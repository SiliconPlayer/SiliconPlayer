#ifndef SILICONPLAYER_SPATIAL_LIB_SPATIAL_AUDIO_BACKEND_H
#define SILICONPLAYER_SPATIAL_LIB_SPATIAL_AUDIO_BACKEND_H

#include "ISpatializer.h"

#include <memory>
#include <vector>

namespace spaudio {
class AmbisonicBinauralizer;
class AmbisonicEncoder;
class AmbisonicSpeaker;
class BFormat;
class SpeakersBinauralizer;
} // namespace spaudio

namespace siliconplayer::spatial {

class LibSpatialAudioBackend : public ISpatializer {
public:
    LibSpatialAudioBackend();
    ~LibSpatialAudioBackend() override;

    SpatializerBackendId backendId() const override { return SpatializerBackendId::LibSpatialAudio; }

    bool prepare(
            int sampleRate,
            BedLayoutId inputLayout,
            int inputChannels,
            int maxBlockFrames,
            const SpatializerConfig& config) override;
    void reset() override;
    void updateConfig(const SpatializerConfig& config) override;
    void process(
            const float* inInterleaved,
            float* outStereoInterleaved,
            int frames) override;
    int latencyFrames() const override { return latencyFramesValue; }

private:
    struct SpeakerPlan {
        int bedChannel = 0;
        float azimuthDeg = 0.0f;
        float elevationDeg = 0.0f;
    };

    bool configureVirtualSpeakers(int sampleRate, const SpatializerConfig& config);
    bool configureAmbisonics(int sampleRate, const SpatializerConfig& config);
    void buildSpeakerPlan(BedLayoutId inputLayout, int inputChannels);

    std::unique_ptr<spaudio::SpeakersBinauralizer> speakersBinauralizer;
    std::unique_ptr<spaudio::AmbisonicBinauralizer> ambisonicBinauralizer;
    std::unique_ptr<spaudio::BFormat> ambisonicFormat;
    std::vector<std::unique_ptr<spaudio::AmbisonicSpeaker>> virtualSpeakers;
    std::vector<std::unique_ptr<spaudio::AmbisonicEncoder>> ambisonicEncoders;

    std::vector<SpeakerPlan> speakerPlan;
    std::vector<float> channelScratch;
    std::vector<float*> channelPtrs;
    std::vector<float> stereoScratch;

    int blockSize = 0;
    int bedChannels = 0;
    int latencyFramesValue = 0;
    bool useAmbisonics = false;
};

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_LIB_SPATIAL_AUDIO_BACKEND_H
