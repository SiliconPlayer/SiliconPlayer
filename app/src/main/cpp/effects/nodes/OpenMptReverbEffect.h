#ifndef SILICONPLAYER_EFFECTS_NODES_OPENMPT_REVERB_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_OPENMPT_REVERB_EFFECT_H

#include "../AudioEffect.h"

#include <array>
#include <atomic>
#include <cstdint>
#include <vector>

namespace siliconplayer::effects {

class OpenMptReverbEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    void configureReverb(int sampleRate, int preset);
    void processReverbPreDelay(const int32_t* in, int32_t frames);
    void processReverbReflections(int32_t* out, int32_t frames);
    void processReverbLate(int32_t* out, int32_t frames);
    void processReverbPost(const int32_t* wet, int32_t* dry, int32_t frames);
    void applyReverbDryMix(int32_t* dry, const int32_t* wet, int32_t dryVol, int32_t frames);

    std::atomic<int> depthParam { 8 };
    std::atomic<int> presetParam { 0 };
    int currentSampleRate = 0;
    int configuredSampleRate = 0;
    int reverbConfiguredPreset = -1;
    int reverbConfiguredDepth = -1;
    int32_t reverbInputY1L = 0;
    int32_t reverbInputY1R = 0;
    int32_t reverbDcrX1L = 0;
    int32_t reverbDcrX1R = 0;
    int32_t reverbDcrY1L = 0;
    int32_t reverbDcrY1R = 0;

    int32_t reverbRefMasterGain = 0;
    int32_t reverbLateMasterGain = 0;
    int32_t reverbReflectionsGain = 0;
    uint32_t reverbPreDifPos = 0;
    uint32_t reverbDelayPos = 0;
    uint32_t reverbRefOutPos = 0;
    uint32_t reverbLateDelay = 0;
    uint32_t reverbLateDelayPos = 0;
    int16_t reverbRoomLpCoeffL = 0;
    int16_t reverbRoomLpCoeffR = 0;
    int16_t reverbRoomHistoryL = 0;
    int16_t reverbRoomHistoryR = 0;
    int16_t reverbPreDifCoeffL = 0;
    int16_t reverbPreDifCoeffR = 0;
    int16_t reverbDifCoeffL = 0;
    int16_t reverbDifCoeffR = 0;
    int16_t reverbDecayDcL = 0;
    int16_t reverbDecayDcR = 0;
    int16_t reverbDecayLpL = 0;
    int16_t reverbDecayLpR = 0;
    int16_t reverbLpHist0L = 0;
    int16_t reverbLpHist0R = 0;
    int16_t reverbLpHist1L = 0;
    int16_t reverbLpHist1R = 0;
    int16_t reverbDif2InGain0L = 0x7000;
    int16_t reverbDif2InGain0R = 0x1000;
    int16_t reverbDif2InGain1L = 0x1000;
    int16_t reverbDif2InGain1R = 0x7000;
    int16_t reverbOutGain0L = 0;
    int16_t reverbOutGain0R = 0;
    int16_t reverbOutGain1L = 0;
    int16_t reverbOutGain1R = 0;

    struct ReverbReflectionState {
        uint32_t delay = 0;
        int16_t gainLL = 0;
        int16_t gainRL = 0;
        int16_t gainLR = 0;
        int16_t gainRR = 0;
    };
    std::array<ReverbReflectionState, 8> reverbReflections {};

    std::vector<int16_t> reverbRefDelayBuffer;
    std::vector<int16_t> reverbPreDifBuffer;
    std::vector<int16_t> reverbRefOutBuffer;
    std::vector<int16_t> reverbDiffusion1;
    std::vector<int16_t> reverbDiffusion2;
    std::vector<int16_t> reverbDelay1;
    std::vector<int16_t> reverbDelay2;
    std::vector<int32_t> reverbWetWork;
    std::vector<int32_t> reverbDryWork;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_OPENMPT_REVERB_EFFECT_H
