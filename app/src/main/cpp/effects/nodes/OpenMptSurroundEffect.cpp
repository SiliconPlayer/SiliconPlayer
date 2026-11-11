/*
 * Adapted from the OpenMPT project (https://openmpt.org), BSD-3-Clause.
 * See effects/openmpt_dsp/LICENSE.OpenMPT for full terms.
 */

#include "OpenMptSurroundEffect.h"
#include "../OpenMptDspShared.h"

#include <algorithm>
#include <cmath>

namespace siliconplayer::effects {

namespace {
constexpr ParamDescriptor kSurroundParams[] = {
        { "depth", "Depth", ParamType::Int, 1.0f, 16.0f, 8.0f, 1.0f, 0 },
        { "delayMs", "Delay (ms)", ParamType::Int, 5.0f, 45.0f, 20.0f, 5.0f, 0 },
};

IAudioEffect* createSurroundEffect() {
    return new OpenMptSurroundEffect();
}
} // namespace

const EffectDescriptor& OpenMptSurroundEffect::descriptor() {
    static const EffectDescriptor desc {
            "openmpt.surround",
            "OpenMPT Surround",
            1,
            kSurroundParams,
            static_cast<int>(sizeof(kSurroundParams) / sizeof(kSurroundParams[0])),
            false,
            &createSurroundEffect,
    };
    return desc;
}

void OpenMptSurroundEffect::prepare(int sampleRate, int /*channels*/) {
    currentSampleRate = sampleRate;
    const int safeRate = std::max(8000, sampleRate);
    if (safeRate == configuredSampleRate) {
        return;
    }
    configuredSampleRate = safeRate;
    const int maxSurroundDelayFrames = std::max(16, (safeRate * 45) / 1000);
    surroundDelay.assign(static_cast<size_t>(maxSurroundDelayFrames), 0);
    surroundWritePos = 0;
    surroundConfiguredDelayMs = 20;
    surroundConfiguredDepth = 8;
    surroundHpX1 = 0;
    surroundHpY1 = 0;
    surroundLpY1 = 0;
    openmpt_detail::shelfEq(1024, surroundHpA1, surroundHpB0, surroundHpB1, 200, safeRate, 0.0f, 0.5f, 1.0f);
    openmpt_detail::shelfEq(1024, surroundLpA1, surroundLpB0, surroundLpB1, 7000, safeRate, 1.0f, 0.75f, 0.0f);
    surroundHpB0 = (surroundHpB0 * surroundConfiguredDepth) >> 5;
    surroundHpB1 = (surroundHpB1 * surroundConfiguredDepth) >> 5;
    surroundLpB0 *= 2;
    surroundLpB1 *= 2;
}

void OpenMptSurroundEffect::reset() {
    configuredSampleRate = 0;
    surroundDelay.clear();
    surroundWritePos = 0;
}

void OpenMptSurroundEffect::setParam(int index, float value) {
    if (!std::isfinite(value)) return;
    const int iv = static_cast<int>(std::lrint(value));
    if (index == 0) {
        depthParam.store(std::clamp(iv, 1, 16), std::memory_order_relaxed);
    } else if (index == 1) {
        const int clamped = std::clamp(iv, 5, 45);
        const int step = ((clamped - 5) + 2) / 5;
        delayMsParam.store(5 + (step * 5), std::memory_order_relaxed);
    }
}

void OpenMptSurroundEffect::process(float* buffer, int frames, int channels) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    if (configuredSampleRate == 0) {
        prepare(currentSampleRate > 0 ? currentSampleRate : 48000, channels);
    }
    if (channels < 2 || surroundDelay.empty()) {
        return;
    }

    using openmpt_detail::kMixScale;
    using openmpt_detail::shelfEq;

    const int delayClamped = std::clamp(delayMsParam.load(std::memory_order_relaxed), 5, 45);
    const int delayMs = 5 + ((((delayClamped - 5) + 2) / 5) * 5);
    const int depth = std::clamp(depthParam.load(std::memory_order_relaxed), 1, 16);
    if (delayMs != surroundConfiguredDelayMs || depth != surroundConfiguredDepth) {
        const int safeRate = std::max(currentSampleRate, 8000);
        std::fill(surroundDelay.begin(), surroundDelay.end(), 0);
        surroundWritePos = 0;
        surroundConfiguredDelayMs = delayMs;
        surroundConfiguredDepth = depth;
        surroundHpX1 = 0;
        surroundHpY1 = 0;
        surroundLpY1 = 0;
        shelfEq(1024, surroundHpA1, surroundHpB0, surroundHpB1, 200, safeRate, 0.0f, 0.5f, 1.0f);
        shelfEq(1024, surroundLpA1, surroundLpB0, surroundLpB1, 7000, safeRate, 1.0f, 0.75f, 0.0f);
        surroundHpB0 = (surroundHpB0 * depth) >> 5;
        surroundHpB1 = (surroundHpB1 * depth) >> 5;
        surroundLpB0 *= 2;
        surroundLpB1 *= 2;
    }

    for (int frame = 0; frame < frames; ++frame) {
        const int idx = frame * channels;
        const int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx] * 0.5f, -1.0f, 1.0f) * kMixScale));
        const int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1] * 0.5f, -1.0f, 1.0f) * kMixScale));

        const int32_t secho = surroundDelay[static_cast<size_t>(surroundWritePos)];
        surroundDelay[static_cast<size_t>(surroundWritePos)] = (inL + inR + 256) >> 9;

        const int64_t hpAcc = static_cast<int64_t>(surroundHpB0) * secho
                              + static_cast<int64_t>(surroundHpB1) * surroundHpX1
                              + static_cast<int64_t>(surroundHpA1) * surroundHpY1;
        int32_t v0 = static_cast<int32_t>(hpAcc >> 10);
        surroundHpX1 = secho;
        const int64_t lpAcc = static_cast<int64_t>(surroundLpB0) * v0
                              + static_cast<int64_t>(surroundLpB1) * surroundHpY1
                              + static_cast<int64_t>(surroundLpA1) * surroundLpY1;
        int32_t v = static_cast<int32_t>(lpAcc >> 2);
        surroundHpY1 = v0;
        surroundLpY1 = v >> 8;

        const int32_t outL = inL + v;
        const int32_t outR = inR - v;
        buffer[idx] = (static_cast<float>(outL) / kMixScale) * 2.0f;
        buffer[idx + 1] = (static_cast<float>(outR) / kMixScale) * 2.0f;

        surroundWritePos++;
        if (surroundWritePos >= std::clamp((currentSampleRate * delayMs) / 1000, 1, static_cast<int>(surroundDelay.size()) - 1)) {
            surroundWritePos = 0;
        }
    }
}

} // namespace siliconplayer::effects
