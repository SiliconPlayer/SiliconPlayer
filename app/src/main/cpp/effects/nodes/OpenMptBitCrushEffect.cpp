/*
 * Adapted from the OpenMPT project (https://openmpt.org), BSD-3-Clause.
 * See effects/openmpt_dsp/LICENSE.OpenMPT for full terms.
 */

#include "OpenMptBitCrushEffect.h"
#include "../OpenMptDspShared.h"

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace siliconplayer::effects {

namespace {
constexpr ParamDescriptor kBitCrushParams[] = {
        { "bits", "Bit Depth", ParamType::Int, 1.0f, 24.0f, 16.0f, 1.0f, 0 },
};

IAudioEffect* createBitCrushEffect() {
    return new OpenMptBitCrushEffect();
}
} // namespace

const EffectDescriptor& OpenMptBitCrushEffect::descriptor() {
    static const EffectDescriptor desc {
            "openmpt.bitcrush",
            "OpenMPT Bit Crush",
            1,
            kBitCrushParams,
            static_cast<int>(sizeof(kBitCrushParams) / sizeof(kBitCrushParams[0])),
            false,
            &createBitCrushEffect,
    };
    return desc;
}

void OpenMptBitCrushEffect::prepare(int /*sampleRate*/, int /*channels*/) {}

void OpenMptBitCrushEffect::reset() {}

void OpenMptBitCrushEffect::setParam(int index, float value) {
    if (!std::isfinite(value)) return;
    if (index == 0) {
        bitsParam.store(std::clamp(static_cast<int>(std::lrint(value)), 1, 24), std::memory_order_relaxed);
    }
}

void OpenMptBitCrushEffect::process(float* buffer, int frames, int channels) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }

    const int bits = std::clamp(bitsParam.load(std::memory_order_relaxed), 1, 24);
    const int precisionBits = 24;
    const uint32_t mask = ~((uint32_t{1} << (precisionBits - bits)) - 1u);
    const int samples = frames * channels;
    for (int i = 0; i < samples; ++i) {
        const float sample = std::clamp(buffer[i], -1.0f, 1.0f);
        const int32_t fixed = static_cast<int32_t>(std::lrint(sample * static_cast<float>((1 << precisionBits) - 1)));
        const int32_t crushed = static_cast<int32_t>(static_cast<uint32_t>(fixed) & mask);
        buffer[i] = openmpt_detail::clampSample(static_cast<float>(crushed) / static_cast<float>((1 << precisionBits) - 1));
    }
}

} // namespace siliconplayer::effects
