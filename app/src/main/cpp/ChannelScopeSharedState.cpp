#include "ChannelScopeSharedState.h"

#include <algorithm>
#include <chrono>

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
}

bool ChannelScopeSharedState::tryBeginCapture(int64_t nowNs, int channelCountHint) {
    std::lock_guard<std::mutex> lock(publishMutex);
    if (lastCaptureNs != 0 &&
        nowNs - lastCaptureNs < captureIntervalNsForChannels(channelCountHint)) {
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
    if (!bypassGate &&
        nowNs - lastCaptureNs < captureIntervalNsForChannels(channels)) {
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
    // Steal the newest published buffers under the short publish lock.
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

    // Cores capture every channel every mixed sample and flush idle channels
    // with silence, so captures are served verbatim: any filtering here would
    // misclassify quiet or held-note traces as stale.
    const int clampedSamples = std::clamp(samplesPerChannel, 16, kMaxSamples);
    const int maxPresentationDelay = std::max(0, kMaxSamples - clampedSamples);
    const int clampedDelay = std::clamp(presentationDelayFrames, 0, maxPresentationDelay);
    const int windowStart = maxPresentationDelay - clampedDelay;

    outFlat.resize(static_cast<size_t>(totalChannels) * static_cast<size_t>(clampedSamples));
    for (int channel = 0; channel < totalChannels; ++channel) {
        const size_t sourceOffset =
                static_cast<size_t>(channel) * static_cast<size_t>(kMaxSamples) +
                static_cast<size_t>(windowStart);
        const size_t destinationOffset = static_cast<size_t>(channel) * static_cast<size_t>(clampedSamples);
        std::copy(
                localRaw.data() + sourceOffset,
                localRaw.data() + sourceOffset + clampedSamples,
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
