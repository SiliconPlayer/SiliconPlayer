#include "ChannelScopeSharedState.h"

#include <algorithm>
#include <chrono>

void ChannelScopeSharedState::clear() {
    {
        std::lock_guard<std::mutex> lock(publishMutex);
        pubRaw.clear();
        pubVu.clear();
        pubVuAppend.clear();
        pubChannels = 0;
        pubSerial = 0;
        lastCaptureNs = 0;
        pubRollingValid = false;
        pubWritePos = 0;
        pubFilled = 0;
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
    if (captureGateBypass()) return true;
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
    if (!captureGateBypass() &&
        !bypassGate &&
        nowNs - lastCaptureNs < captureIntervalNsForChannels(channels)) {
        return;
    }
    lastCaptureNs = nowNs;
    acceptedPublishCount_.fetch_add(1, std::memory_order_relaxed);
    pubRollingValid = false;
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

void ChannelScopeSharedState::publishAppend(
        const float* appendedFlat,
        int channels,
        int prevWritePos,
        int framesAppended,
        const float* vu,
        uint64_t serial) {
    if (channels <= 0 || !appendedFlat || framesAppended <= 0) return;
    std::lock_guard<std::mutex> lock(publishMutex);
    if (pubRollingValid && pubChannels != channels) {
        // Channel layout changed: take over the fresh span only; the old
        // content is stale across the layout switch anyway.
        pubRaw.assign(
                static_cast<size_t>(channels) * static_cast<size_t>(kMaxSamples), 0.0f);
        pubRollingValid = true;
        pubWritePos = prevWritePos;
        pubFilled = 0;
    } else if (!pubRollingValid) {
        pubRollingValid = true;
        pubWritePos = prevWritePos;
        pubFilled = 0;
        pubRaw.assign(
                static_cast<size_t>(channels) * static_cast<size_t>(kMaxSamples), 0.0f);
    }
    pubChannels = channels;
    pubSerial = serial;
    prevWritePos = ((prevWritePos % kMaxSamples) + kMaxSamples) % kMaxSamples;
    framesAppended = std::min(framesAppended, kMaxSamples);
    for (int channel = 0; channel < channels; ++channel) {
        const float* src = appendedFlat + static_cast<size_t>(channel) * static_cast<size_t>(framesAppended);
        float* dst = pubRaw.data() + static_cast<size_t>(channel) * static_cast<size_t>(kMaxSamples);
        const int first = std::min(framesAppended, kMaxSamples - prevWritePos);
        std::copy_n(src, first, dst + prevWritePos);
        if (framesAppended > first) {
            std::copy_n(src + first, framesAppended - first, dst);
        }
    }
    pubWritePos = (prevWritePos + framesAppended) % kMaxSamples;
    pubFilled = std::min(pubFilled + framesAppended, kMaxSamples);
    if (vu) {
        pubVuAppend.assign(vu, vu + channels);
    }
    acceptedPublishCount_.fetch_add(1, std::memory_order_relaxed);
}

std::vector<float> ChannelScopeSharedState::publishedVu() {
    std::lock_guard<std::mutex> lock(publishMutex);
    return pubRollingValid ? pubVuAppend : pubVu;
}

void ChannelScopeSharedState::getProcessedSamples(
        int samplesPerChannel,
        int presentationDelayFrames,
        std::vector<float>& outFlat
) {
    // Rolling publisher: copy the requested window out of the modular ring
    // directly; cost scales with the window, not the ring.
    if (pubRollingValid) {
        std::lock_guard<std::mutex> pullLock(publishMutex);
        const int requestedSamples = std::clamp(samplesPerChannel, 16, kMaxSamples);
        const int capacity = std::min(pubFilled, kMaxSamples);
        const int availableChannels = std::max(pubChannels, 0);
        if (availableChannels <= 0 || capacity <= 0) {
            outFlat.clear();
            return;
        }
        const int windowSamples = std::min(requestedSamples, capacity);
        const int maxDelay = std::max(0,
                std::min(pubFilled, kMaxSamples) - windowSamples);
        const int clampedDelay = std::clamp(presentationDelayFrames, 0, maxDelay);
        // Anchor the window end on the modular write position, not the filled
        // count: once the ring saturates, capacity stops advancing while
        // pubWritePos keeps sliding with decodes. In the warm-up phase they
        // are equal, so one anchor covers both.
        const int span = std::min(windowSamples,
                std::max(0, capacity - clampedDelay));
        const int copyStart = windowSamples - span;
        const int endPos = ((pubWritePos - clampedDelay) % kMaxSamples + kMaxSamples) % kMaxSamples;
        const int idx0 = (endPos - span + kMaxSamples) % kMaxSamples;
        outFlat.assign(
                static_cast<size_t>(availableChannels) * static_cast<size_t>(windowSamples), 0.0f);
        for (int channel = 0; channel < availableChannels; ++channel) {
            const float* src = pubRaw.data() + static_cast<size_t>(channel) * static_cast<size_t>(kMaxSamples);
            float* dst = outFlat.data() + static_cast<size_t>(channel) * static_cast<size_t>(windowSamples);
            // Zeros prefix while history is shorter than the window.
            const int first = std::min(span, kMaxSamples - idx0);
            if (span > 0) {
                std::copy_n(src + idx0, first, dst + copyStart);
                if (span > first) {
                    std::copy_n(src, span - first, dst + copyStart + first);
                }
            }
        }
        return;
    }

    // clear() and fellow consumers mutate local* under this same mutex;
    // locks nest consumer -> publish only, never in reverse.
    std::lock_guard<std::mutex> consumerLock(mutex);
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
