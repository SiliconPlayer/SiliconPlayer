#include "GainEffect.h"

#include <algorithm>
#include <cmath>

namespace siliconplayer::effects {

namespace {
constexpr ParamDescriptor kGainParams[] = {
        { "gainDb", "Gain (dB)", ParamType::Float, -24.0f, 24.0f, 0.0f, 0.0f, 0 },
};

IAudioEffect* createGainEffect() {
    return new GainEffect();
}
} // namespace

const EffectDescriptor& GainEffect::descriptor() {
    static const EffectDescriptor desc {
            "gain",
            "Gain",
            1,
            kGainParams,
            static_cast<int>(sizeof(kGainParams) / sizeof(kGainParams[0])),
            false,
            &createGainEffect,
    };
    return desc;
}

void GainEffect::prepare(int /*sampleRate*/, int /*channels*/) {}

void GainEffect::reset() {}

void GainEffect::setParam(int index, float value) {
    if (!std::isfinite(value)) return;
    if (index == 0) {
        gainDbParam.store(std::clamp(value, -24.0f, 24.0f), std::memory_order_relaxed);
    }
}

void GainEffect::process(float* interleaved, int frames, int channels) {
    if (!interleaved || frames <= 0 || channels <= 0) {
        return;
    }

    const float db = gainDbParam.load(std::memory_order_relaxed);
    if (db == 0.0f) {
        return;
    }

    const float linear = static_cast<float>(std::pow(10.0, static_cast<double>(db) / 20.0));
    const int samples = frames * channels;
    for (int i = 0; i < samples; ++i) {
        interleaved[i] *= linear;
    }
}

} // namespace siliconplayer::effects
