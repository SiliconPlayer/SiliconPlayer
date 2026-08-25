#ifndef SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
#define SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H

#include <cstdint>
#include <mutex>
#include <vector>

struct ChannelScopeSharedState {
    // ---- producer side ----
    // Short-held lock: decoders publish here and never wait on consumers.
    mutable std::mutex publishMutex;
    static constexpr int64_t kMinCaptureIntervalNs = 24'000'000;

    // ---- consumer side ----
    mutable std::mutex mutex;
    // Must exceed worst-case buffered-ahead delay (~9k frames foreground)
    // beyond the largest request (<=8192), or the slider pins and strobes.
    static constexpr int kMaxSamples = 32768;
    // Slack below the current window so delay growth between updates never
    // compares against unprocessed history.
    static constexpr int kProcessSlackFrames = 2048;
    // Reserved slide room so estimation overshoot leads the trace instead of
    // freezing it at the newest edge.
    static constexpr int kMinSliderHeadroomFrames = 4096;
    // Per-callback bound for the buffered-ahead compensation base; the base
    // decays uncapped across the interval so the window keeps sliding.
    static constexpr int kBufferedAheadEstimateCapFrames = 14000;

    bool tryBeginCapture(int64_t nowNs);
    // Copies the snapshot into the published slot under the publish lock.
    // Decoders that pre-gated via tryBeginCapture must pass bypassGate, or
    // the shared timestamp makes publish drop every snapshot.
    void publish(
            const std::vector<float>& raw,
            const std::vector<float>& vu,
            int channels,
            uint64_t serial,
            bool bypassGate = false);
    std::vector<float> publishedVu();
    void getProcessedSamples(int samplesPerChannel, int presentationDelayFrames, std::vector<float>& outFlat);
    std::vector<float> getProcessedSamples(int samplesPerChannel, int presentationDelayFrames = 0);
    void clear();

private:
    std::vector<float> pubRaw;
    std::vector<float> pubVu;
    int pubChannels = 0;
    uint64_t pubSerial = 0;
    int64_t lastCaptureNs = 0;

    std::vector<float> localRaw;
    std::vector<float> localVu;
    int localChannels = 0;
    uint64_t localSerial = 0;
    bool localValid = false;
    std::vector<float> prevSnapshot;
    std::vector<float> processedFrame;
    std::vector<std::uint8_t> frozenFrameCount;
    int lastChannels = 0;
    uint64_t consumedSerial = 0;
    bool processedValid = false;
};

#endif // SILICONPLAYER_CHANNEL_SCOPE_SHARED_STATE_H
