#ifndef SILICONPLAYER_SPATIAL_STEAM_AUDIO_BACKEND_H
#define SILICONPLAYER_SPATIAL_STEAM_AUDIO_BACKEND_H

#include "ISpatializer.h"

#include <phonon/phonon.h>

#include <string>
#include <vector>

namespace siliconplayer::spatial {

class SteamAudioBackend : public ISpatializer {
public:
    SteamAudioBackend();
    ~SteamAudioBackend() override;

    SpatializerBackendId backendId() const override { return SpatializerBackendId::SteamAudio; }

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
    float outputGainCompensation() const override;

private:
    void buildSpeakerLayout(
            BedLayoutId inputLayout,
            int inputChannels,
            bool wideSurround);
    void releaseEffect();
    void releaseReflections();

    IPLContext context = nullptr;
    IPLHRTF hrtf = nullptr;
    IPLVirtualSurroundEffect effect = nullptr;
    IPLReflectionEffect reflectionLeft = nullptr;
    IPLReflectionEffect reflectionRight = nullptr;

    IPLSpeakerLayout speakerLayout{};
    std::vector<IPLVector3> speakerDirections;

    std::vector<float> channelScratch;
    std::vector<float*> channelPtrs;
    std::vector<float> stereoScratch;
    std::vector<float*> stereoPtrs;
    std::vector<float> reflectionScratch;
    std::vector<float*> reflectionPtrs;
    std::vector<float> earlyReflectionLeft;
    std::vector<float> earlyReflectionRight;
    std::vector<float> earlyReflectionCross;
    std::vector<float> mimoFrontMid;
    std::vector<float> mimoFrontSide;
    std::vector<float> mimoSurrMid;
    std::vector<float> mimoSurrSide;
    std::vector<float> mimoRoomExciter;
    int diffuseCursor = 0;
    int roomCursor = 0;
    std::vector<float> reflectionLeftScratch;
    std::vector<float> reflectionRightScratch;

    int blockSize = 0;
    int bedChannels = 0;
    int latencyFramesValue = 0;
    std::string sofaPath;
    SpatializerConfig activeConfig;
};

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_STEAM_AUDIO_BACKEND_H
