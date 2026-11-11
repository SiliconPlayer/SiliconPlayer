#ifndef SILICONPLAYER_EFFECTS_NODES_OPENMPT_BASS_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_OPENMPT_BASS_EFFECT_H

#include "../AudioEffect.h"

#include <atomic>
#include <cstdint>

namespace siliconplayer::effects {

class OpenMptBassEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    std::atomic<int> depthParam { 2 };
    std::atomic<int> rangeParam { 2 };
    int currentSampleRate = 0;
    int configuredSampleRate = 0;
    int32_t bassX1 = 0;
    int32_t bassY1 = 0;
    int32_t bassDcrY1L = 0;
    int32_t bassDcrX1L = 0;
    int32_t bassDcrY1R = 0;
    int32_t bassDcrX1R = 0;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_OPENMPT_BASS_EFFECT_H
