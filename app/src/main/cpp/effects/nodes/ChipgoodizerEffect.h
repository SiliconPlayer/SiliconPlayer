#ifndef SILICONPLAYER_EFFECTS_NODES_CHIPGOODIZER_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_CHIPGOODIZER_EFFECT_H

#include "../AudioEffect.h"

#include "chipgoodizer/chipgoodizer_c.h"

namespace siliconplayer::effects {

class ChipgoodizerEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    ChipgoodizerEffect();
    ~ChipgoodizerEffect() override;

    ChipgoodizerEffect(const ChipgoodizerEffect&) = delete;
    ChipgoodizerEffect& operator=(const ChipgoodizerEffect&) = delete;

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    cg_effect* handle = nullptr;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_CHIPGOODIZER_EFFECT_H
