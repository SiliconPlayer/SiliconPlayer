#include "ChannelScopeSharedState.h"

#include <algorithm>
#include <chrono>
#include <cmath>

void ChannelScopeSharedState::clear() {
    {
        std::lock_guard<std::mutex> lock(publishMutex);
        pubRaw.clear();
        pubVu.clear();
        pubChannels = 0;
        pubSerial = 0;
        lastCaptureNs = 0;
    }
    std::lock_guard<std::mutex> lock(mutex);
    localRaw.clear();
    localVu.clear();
    localChannels = 0;
    localSerial = 0;
    localValid = false;
    prevSnapshot.clear();
    processedFrame.clear();
    frozenFrameCount.clear();
    lastChannels = 0;
    consumedSerial = 0;
    processedValid = false;
}

bool ChannelScopeSharedState::tryBeginCapture(int64_t nowNs) {
    std::lock_guard<std::mutex> lock(publishMutex);
    if (lastCaptureNs != 0 && nowNs - lastCaptureNs < kMinCaptureIntervalNs) {
        return false;
    }
    lastCaptureNs = nowNs;
    return true;
}

void ChannelScopeSharedState::publish(
        const std::vector<float>& raw,
        const std::vector<float>& vu,
        int channels,
        uint64_t serial,
        bool bypassGate) {
    if (channels <= 0 || raw.empty()) return;
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    std::lock_guard<std::mutex> lock(publishMutex);
    // Backstop gate so decoders without an explicit pre-gather check still
    // publish at the shared cadence.
    if (!bypassGate && lastCaptureNs != 0 && nowNs - lastCaptureNs < kMinCaptureIntervalNs) {
        return;
    }
    lastCaptureNs = nowNs;
    if (pubRaw.size() != raw.size()) {
        pubRaw.resize(raw.size());
    }
    std::copy(raw.begin(), raw.end(), pubRaw.begin());
    if (pubVu.size() != vu.size()) {
        pubVu.resize(vu.size());
    }
    std::copy(vu.begin(), vu.end(), pubVu.begin());
    pubChannels = channels;
    pubSerial = serial;
}

std::vector<float> ChannelScopeSharedState::publishedVu() {
    std::lock_guard<std::mutex> lock(publishMutex);
    return pubVu;
}

void ChannelScopeSharedState::getProcessedSamples(
        int samplesPerChannel,
        int presentationDelayFrames,
        std::vector<float>& outFlat
) {
    // Steal the newest published buffers under the short publish lock; all
    // heavy processing runs against consumer-private state.
    {
        std::lock_guard<std::mutex> pullLock(publishMutex);
        if (pubChannels > 0 && !pubRaw.empty()) {
            if (!localValid || localChannels != pubChannels || localRaw.size() != pubRaw.size()) {
                localRaw = pubRaw;
                localVu = pubVu;
                localChannels = pubChannels;
                localSerial = pubSerial;
                localValid = true;
            } else if (pubSerial != localSerial) {
                localRaw.swap(pubRaw);
                localVu.swap(pubVu);
                localSerial = pubSerial;
            }
        }
    }
    if (!localValid || localChannels <= 0 || localRaw.empty() || localVu.size() != static_cast<size_t>(localChannels)) {
        outFlat.clear();
        return;
    }
    const int totalChannels = localChannels;
    const uint64_t snapshotSerialValue = localSerial;

    const int clampedSamples = std::clamp(samplesPerChannel, 16, kMaxSamples);
    const size_t flattenedSize = static_cast<size_t>(totalChannels) * static_cast<size_t>(clampedSamples);

    if (lastChannels != totalChannels ||
        prevSnapshot.size() != localRaw.size() ||
        processedFrame.size() != localRaw.size() ||
        frozenFrameCount.size() != static_cast<size_t>(totalChannels)) {
        prevSnapshot.assign(localRaw.size(), 0.0f);
        processedFrame.assign(localRaw.size(), 0.0f);
        frozenFrameCount.assign(static_cast<size_t>(totalChannels), 0);
        lastChannels = totalChannels;
        processedValid = false;
        consumedSerial = snapshotSerialValue;
    }

    const int maxPresentationDelay = std::max(0, kMaxSamples - clampedSamples);
    const int clampedDelay = std::clamp(presentationDelayFrames, 0, maxPresentationDelay);
    const int windowStart = maxPresentationDelay - clampedDelay;
    const int spanStart = std::max(0, windowStart - kProcessSlackFrames);
    const int spanLength = kMaxSamples - spanStart;

    if (snapshotSerialValue != consumedSerial || !processedValid) {
        for (int channel = 0; channel < totalChannels; ++channel) {
            const size_t channelOffset = static_cast<size_t>(channel) * kMaxSamples;
            const float* source = localRaw.data() + channelOffset + spanStart;
            float* processedDestination = processedFrame.data() + channelOffset + spanStart;

            const float* previous = prevSnapshot.data() + channelOffset + spanStart;
            bool sameAsPrevious = true;
            float peak = 0.0f;
            float prevPeak = 0.0f;
            float deltaSum = 0.0f;
            float rmsAcc = 0.0f;
            for (int i = 0; i < spanLength; ++i) {
                const float value = source[i];
                const float prevValue = previous[i];
                if (prevValue != value) sameAsPrevious = false;
                deltaSum += std::abs(value - prevValue);
                rmsAcc += value * value;
                peak = std::max(peak, std::abs(value));
                prevPeak = std::max(prevPeak, std::abs(prevValue));
            }

            auto& frozen = frozenFrameCount[static_cast<size_t>(channel)];
            const float channelVu = localVu[static_cast<size_t>(channel)];
            const float meanDelta = deltaSum / static_cast<float>(spanLength);
            const float rms = std::sqrt(rmsAcc / static_cast<float>(spanLength));
            const bool frameNearlyFrozen = meanDelta < 0.0005f;
            const bool looksSilentNow = (channelVu < 0.00035f) && (rms < 0.0045f);
            const bool abruptTailFreeze =
                    looksSilentNow &&
                    (peak > 0.018f || prevPeak > 0.018f) &&
                    (sameAsPrevious || frameNearlyFrozen);
            const bool likelyFreshSignal =
                    (peak > 0.001f) &&
                    (!frameNearlyFrozen || channelVu > 0.03f);
            bool suppressStaleScope = false;
            if (abruptTailFreeze) {
                frozen = 3;
                suppressStaleScope = true;
            } else if (likelyFreshSignal) {
                frozen = 0;
            } else {
                if (frozen < 255) {
                    frozen++;
                }
                if (frameNearlyFrozen &&
                    frozen >= 6 &&
                    channelVu < 0.03f &&
                    (peak > 0.012f || prevPeak > 0.012f)) {
                    suppressStaleScope = true;
                }
                if (frozen >= 3) {
                    suppressStaleScope = true;
                }
            }

            if (suppressStaleScope) {
                std::fill(processedDestination, processedDestination + spanLength, 0.0f);
            } else {
                std::copy(source, source + spanLength, processedDestination);
            }
            std::copy(source, source + spanLength, prevSnapshot.data() + channelOffset + spanStart);
        }
        processedValid = true;
        consumedSerial = snapshotSerialValue;
    }

    outFlat.resize(flattenedSize);
    for (int channel = 0; channel < totalChannels; ++channel) {
        const size_t sourceOffset =
                static_cast<size_t>(channel) * static_cast<size_t>(kMaxSamples) +
                static_cast<size_t>(windowStart);
        const size_t destinationOffset = static_cast<size_t>(channel) * static_cast<size_t>(clampedSamples);
        std::copy(
                processedFrame.data() + sourceOffset,
                processedFrame.data() + sourceOffset + clampedSamples,
                outFlat.data() + destinationOffset
        );
    }
}

std::vector<float> ChannelScopeSharedState::getProcessedSamples(
        int samplesPerChannel,
        int presentationDelayFrames
) {
    std::vector<float> flattened;
    getProcessedSamples(samplesPerChannel, presentationDelayFrames, flattened);
    return flattened;
}
