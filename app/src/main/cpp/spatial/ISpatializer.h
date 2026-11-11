#ifndef SILICONPLAYER_SPATIAL_ISPATIALIZER_H
#define SILICONPLAYER_SPATIAL_ISPATIALIZER_H

#include "SpeakerLayout.h"

#include <string>

namespace siliconplayer::spatial {

enum class SpatializerBackendId : int {
    None = 0,
    LibSpatialAudio = 1,
    SteamAudio = 2
};

struct SpatializerConfig {
    SpatializerBackendId backend = SpatializerBackendId::None;
    bool enabled = true;
    bool spatializeStereo = false;
    float centerGainDb = 0.0f;
    float surroundGainDb = 0.0f;
    float heightGainDb = 0.0f;
    float lfeGainDb = -3.0f;
    bool lfeLowPassEnabled = true;
    float wetMix = 1.0f;
    int ambisonicOrder = 3;
    int renderMethod = 0; // 0 = Virtual Speakers (Direct HRTF), 1 = Ambisonics (HOA)
    float yawDegrees = 0.0f;
    std::string customSofaPath;
};

class ISpatializer {
public:
    virtual ~ISpatializer() = default;

    virtual SpatializerBackendId backendId() const = 0;
    // `maxBlockFrames` is the largest block the engine will hand to process().
    virtual bool prepare(
            int sampleRate,
            BedLayoutId inputLayout,
            int inputChannels,
            int maxBlockFrames,
            const SpatializerConfig& config) = 0;
    virtual void reset() = 0;
    virtual void updateConfig(const SpatializerConfig& config) = 0;
    // Converts `frames` interleaved bed frames into interleaved stereo frames.
    // `frames` always equals the block size negotiated during prepare().
    virtual void process(
            const float* inInterleaved,
            float* outStereoInterleaved,
            int frames) = 0;
    virtual int latencyFrames() const { return 0; }
};

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_ISPATIALIZER_H
