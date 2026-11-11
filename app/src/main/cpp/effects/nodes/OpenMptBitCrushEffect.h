#ifndef SILICONPLAYER_EFFECTS_NODES_OPENMPT_BITCRUSH_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_OPENMPT_BITCRUSH_EFFECT_H

#include "../AudioEffect.h"

#include <atomic>

namespace siliconplayer::effects {

class OpenMptBitCrushEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    std::atomic<int> bitsParam { 16 };
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_OPENMPT_BITCRUSH_EFFECT_H
