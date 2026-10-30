#ifndef SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
#define SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H

#include <atomic>
#include <cstdint>
#include <mutex>
#include <vector>

struct ChannelScopeSharedState {
    // ---- producer side ----
    // Short-held lock: decoders publish here and never wait on consumers.
    mutable std::mutex publishMutex;

    // ---- consumer side ----
    mutable std::mutex mutex;
    // Must exceed worst-case buffered-ahead delay (~9k frames foreground)
    // beyond the largest request (<=8192), or the slider pins and strobes.
    static constexpr int kMaxSamples = 32768;
    // Offline (export) rendering publishes on every decode callback instead of
    // wall-clock-gated captures, so exported frames advance with the audio.
    // Per-instance on purpose: ChannelScopeSharedState.cpp is compiled into
    // every decoder plugin, so a static flag would be duplicated per .so.
    void setCaptureGateBypass(bool enabled) { captureGateBypass_.store(enabled, std::memory_order_relaxed); }
    bool captureGateBypass() const { return captureGateBypass_.load(std::memory_order_relaxed); }

    // Reserved slide room so estimation overshoot leads the trace instead of
    // freezing it at the newest edge.
    static constexpr int kMinSliderHeadroomFrames = 4096;
    // Per-callback bound for the buffered-ahead compensation base; the base
    // decays uncapped across the interval so the window keeps sliding.
    static constexpr int kBufferedAheadEstimateCapFrames = 14000;

    // Absolute published decoder frames (appends only); the scope playhead
    // lag metric pairs this with the output playhead.
    std::atomic<uint64_t> pubTotalAppended_{ 0 };
    // Accepted (ungated) publishes since construction; feeds cadence metrics.
    uint64_t acceptedPublishCount() const { return acceptedPublishCount_.load(std::memory_order_relaxed); }

    bool tryBeginCapture(int64_t nowNs, int channelCountHint = 0);
    // Copies the snapshot into the published slot under the publish lock.
    // Decoders that pre-gated via tryBeginCapture must pass bypassGate, or
    // the shared timestamp makes publish drop every snapshot.
    void publish(
            const std::vector<float>& raw,
            const std::vector<float>& vu,
            int channels,
            uint64_t serial,
            bool bypassGate = false);
    // Rolling append publish: appendedFlat is a planar [channel][count]
    // copy of the decoder's newest samples, prevWritePos is the modular
    // write position in the destination ring BEFORE the append. Only the
    // appended span is written, so decode callbacks can publish at full
    // cadence. vu may be null.
    void publishAppend(
            const float* appendedFlat,
            int channels,
            int prevWritePos,
            int framesAppended,
            const float* vu,
            uint64_t serial);
    std::vector<float> publishedVu();

    // Base capture interval, extended per channel so dense modules pay the
    // gather/publish cost less often; the delay slider keeps motion
    // continuous between captures.
    static constexpr int64_t kCaptureIntervalBaseNs = 24'000'000;
    static constexpr int64_t kCaptureIntervalPerChannelNs = 625'000;
    static int64_t captureIntervalNsForChannels(int channels) {
        return kCaptureIntervalBaseNs +
                std::min(std::max(channels, 0), 64) * kCaptureIntervalPerChannelNs;
    }
    // Absolute count of appended decoder frames since the unit reset;
    // monotonic across rolling appends whenever a rolling window is valid.
    uint64_t totalAppendedFrames() const;
    void getProcessedSamples(int samplesPerChannel, int presentationDelayFrames, std::vector<float>& outFlat);
    std::vector<float> getProcessedSamples(int samplesPerChannel, int presentationDelayFrames = 0);
    void clear();


    // Slew the fetch delay toward target, snapping when the correction is
    // too large to be queue flutter.

private:
    std::vector<float> pubRaw;
    std::vector<float> pubVu;
    int pubChannels = 0;
    uint64_t pubSerial = 0;
    int64_t lastCaptureNs = 0;

    // Rolling model: when set, pubRaw is treated as a modular ring anchored
    // at pubWritePos/pubFilled instead of a linearized window.
    std::vector<float> pubVuAppend;
    bool pubRollingValid = false;
    int pubWritePos = 0;
    int pubFilled = 0;

    // Display fetch delay, slewing toward the estimator target so queue
    // depth flutter cannot shake the window; snaps on large jumps (seek,
    // appends stop, huge corrections).

    std::atomic<uint64_t> acceptedPublishCount_ { 0 };
    std::atomic<bool> captureGateBypass_ { false };

    std::vector<float> localRaw;
    std::vector<float> localVu;
    int localChannels = 0;
    uint64_t localSerial = 0;
    bool localValid = false;
};

#endif // SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
