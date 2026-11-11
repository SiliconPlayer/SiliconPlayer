#ifndef SILICONPLAYER_EFFECTS_NODES_GAIN_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_GAIN_EFFECT_H

#include "../AudioEffect.h"

#include <atomic>

namespace siliconplayer::effects {

class GainEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    std::atomic<float> gainDbParam { 0.0f };
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_GAIN_EFFECT_H
