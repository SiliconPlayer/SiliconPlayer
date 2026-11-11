#ifndef SILICONPLAYER_EFFECTS_NODES_OPENMPT_SURROUND_EFFECT_H
#define SILICONPLAYER_EFFECTS_NODES_OPENMPT_SURROUND_EFFECT_H

#include "../AudioEffect.h"

#include <atomic>
#include <cstdint>
#include <vector>

namespace siliconplayer::effects {

class OpenMptSurroundEffect final : public IAudioEffect {
public:
    static const EffectDescriptor& descriptor();

    void prepare(int sampleRate, int channels) override;
    void reset() override;
    void process(float* interleaved, int frames, int channels) override;
    void setParam(int index, float value) override;

private:
    std::atomic<int> depthParam { 8 };
    std::atomic<int> delayMsParam { 20 };
    int currentSampleRate = 0;
    int configuredSampleRate = 0;
    std::vector<int32_t> surroundDelay;
    int surroundWritePos = 0;
    int surroundConfiguredDelayMs = 20;
    int surroundConfiguredDepth = 8;
    int32_t surroundHpA1 = 0;
    int32_t surroundHpB0 = 0;
    int32_t surroundHpB1 = 0;
    int32_t surroundLpA1 = 0;
    int32_t surroundLpB0 = 0;
    int32_t surroundLpB1 = 0;
    int32_t surroundHpX1 = 0;
    int32_t surroundHpY1 = 0;
    int32_t surroundLpY1 = 0;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_NODES_OPENMPT_SURROUND_EFFECT_H
