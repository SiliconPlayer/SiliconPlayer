#ifndef SILICONPLAYER_SPATIAL_SPATIALIZER_ENGINE_H
#define SILICONPLAYER_SPATIAL_SPATIALIZER_ENGINE_H

#include "ISpatializer.h"

#include <atomic>
#include <memory>
#include <vector>

namespace siliconplayer::spatial {

class SpatializerEngine {
public:
    // Convolution block; the engine keeps the bed and the stereo result in
    // block-sized FIFOs so the HRTF overlap-add stays frame aligned.
    static constexpr int kBlockSize = 256;

    SpatializerEngine() = default;

    // Caller must hold decoderMutex when mutating config or calling processOrDownmix.
    void setConfig(const SpatializerConfig& config);
    const SpatializerConfig& config() const { return currentConfig; }

    bool isActiveForMultichannel() const {
        return activeFlag.load(std::memory_order_relaxed);
    }

    bool isRenderingMultichannel() const {
        return renderingActiveFlag.load(std::memory_order_relaxed);
    }

    double latencyFrames() const {
        return cachedLatencyFrames.load(std::memory_order_relaxed);
    }

    void reset();

    // Renders the speaker bed into stereo. Writes exactly `frames` stereo
    // frames to `outStereoInterleaved`; the input buffer is left untouched.
    void processOrDownmix(
            const float* bedInterleaved,
            float* outStereoInterleaved,
            int frames,
            int decodeChannels,
            BedLayoutId bedLayout,
            int outChannels,
            int sampleRate);

    // Renders the pending convolution tail; returns the stereo frames written.
    int flush(float* outStereoInterleaved, int maxFrames);

    static void downmixBedToStereo(
            const float* inInterleaved,
            int decodeChannels,
            BedLayoutId bedLayout,
            float* outStereo,
            int frames,
            float centerGainLinear = 1.0f,
            float surroundGainLinear = 1.0f,
            float heightGainLinear = 1.0f,
            float lfeGainLinear = 0.70710678f);

private:
    struct BiquadState {
        float b0 = 1.0f;
        float b1 = 0.0f;
        float b2 = 0.0f;
        float a1 = 0.0f;
        float a2 = 0.0f;
        float z1 = 0.0f;
        float z2 = 0.0f;

        void reset() {
            z1 = 0.0f;
            z2 = 0.0f;
        }

        float processSample(float x) {
            const float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    };

    void updateLfeLowPassCoeffs(int sampleRate);
    void applyBedChannelTrims(float* bedBlock, int decodeChannels, BedLayoutId bedLayout);
    void refreshAtomicFlagsLocked();
    void processBlocks(int sampleRate);
    int drainOutput(float* outStereoInterleaved, int maxFrames);
    void ensureBedCapacity(size_t frames);
    void resetFifos();

    SpatializerConfig currentConfig;
    std::unique_ptr<ISpatializer> activeBackend;

    int preparedSampleRate = 0;
    int preparedInputChannels = 0;
    BedLayoutId preparedLayout = BedLayoutId::Stereo_2_0;
    int bedChannels = 0;
    bool backendReady = false;

    // 4th-order Linkwitz-Riley 120 Hz LFE low-pass (two cascaded 2nd-order sections).
    BiquadState lfeBiquad1;
    BiquadState lfeBiquad2;
    int lfeFilterSampleRate = 0;

    std::vector<float> bedFifo;
    size_t bedFifoFrames = 0;
    size_t bedFifoStart = 0;
    size_t bedFifoCapacity = 0;
    std::vector<float> outFifo;
    size_t outFifoFrames = 0;
    size_t outFifoRead = 0;

    std::vector<float> bedBlock;
    std::vector<float> dryBlock;
    std::vector<float> wetBlock;
    std::vector<float> lfeMonoScratch;

    std::atomic<bool> activeFlag { false };
    std::atomic<bool> renderingActiveFlag { false };
    std::atomic<double> cachedLatencyFrames { 0.0 };
};

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_SPATIALIZER_ENGINE_H
