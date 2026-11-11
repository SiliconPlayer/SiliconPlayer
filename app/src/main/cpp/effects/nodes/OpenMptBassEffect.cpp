/*
 * Adapted from the OpenMPT project (https://openmpt.org), BSD-3-Clause.
 * See effects/openmpt_dsp/LICENSE.OpenMPT for full terms.
 */

#include "OpenMptBassEffect.h"
#include "../OpenMptDspShared.h"

#include <algorithm>
#include <cmath>

namespace siliconplayer::effects {

namespace {
constexpr ParamDescriptor kBassParams[] = {
        { "depth", "Depth", ParamType::Int, 0.0f, 4.0f, 2.0f, 1.0f, 0 },
        { "range", "Range", ParamType::Int, 0.0f, 4.0f, 2.0f, 1.0f, 0 },
};

IAudioEffect* createBassEffect() {
    return new OpenMptBassEffect();
}
} // namespace

const EffectDescriptor& OpenMptBassEffect::descriptor() {
    static const EffectDescriptor desc {
            "openmpt.bass",
            "Bass Expansion",
            1,
            kBassParams,
            static_cast<int>(sizeof(kBassParams) / sizeof(kBassParams[0])),
            false,
            &createBassEffect,
    };
    return desc;
}

void OpenMptBassEffect::prepare(int sampleRate, int /*channels*/) {
    currentSampleRate = sampleRate;
    const int safeRate = std::max(8000, sampleRate);
    if (safeRate == configuredSampleRate) {
        return;
    }
    configuredSampleRate = safeRate;
    bassX1 = 0;
    bassY1 = 0;
    bassDcrY1L = 0;
    bassDcrX1L = 0;
    bassDcrY1R = 0;
    bassDcrX1R = 0;
}

void OpenMptBassEffect::reset() {
    configuredSampleRate = 0;
    bassX1 = 0;
    bassY1 = 0;
    bassDcrY1L = 0;
    bassDcrX1L = 0;
    bassDcrY1R = 0;
    bassDcrX1R = 0;
}

void OpenMptBassEffect::setParam(int index, float value) {
    if (!std::isfinite(value)) return;
    const int iv = static_cast<int>(std::lrint(value));
    if (index == 0) {
        depthParam.store(std::clamp(iv, 0, 4), std::memory_order_relaxed);
    } else if (index == 1) {
        rangeParam.store(std::clamp(iv, 0, 4), std::memory_order_relaxed);
    }
}

void OpenMptBassEffect::process(float* buffer, int frames, int channels) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    if (configuredSampleRate == 0) {
        prepare(currentSampleRate > 0 ? currentSampleRate : 48000, channels);
    }

    using openmpt_detail::kDcrAmount;
    using openmpt_detail::shelfEq;

    const int depthStep = std::clamp(depthParam.load(std::memory_order_relaxed), 0, 4);
    const int rangeStep = std::clamp(rangeParam.load(std::memory_order_relaxed), 0, 4);
    const int nXBassRange = std::clamp((4 - rangeStep) * 5 + 1, 5, 21);
    const int nXBassCutOff = std::clamp(50 + (nXBassRange + 2) * 20, 60, 600);
    int nXBassGain = std::clamp(8 - depthStep, 4, 8);
    int32_t a1 = 0;
    int32_t b0 = 1024;
    int32_t b1 = 0;
    shelfEq(
            1024,
            a1,
            b0,
            b1,
            nXBassCutOff,
            std::max(currentSampleRate, 1),
            1.0f + (1.0f / 16.0f) * static_cast<float>(0x300 >> nXBassGain),
            1.0f,
            0.0000001f);
    if (nXBassGain > 5) {
        b0 >>= (nXBassGain - 5);
        b1 >>= (nXBassGain - 5);
    }

    if (channels >= 2) {
        int32_t y1l = bassDcrY1L;
        int32_t x1l = bassDcrX1L;
        int32_t y1r = bassDcrY1R;
        int32_t x1r = bassDcrX1R;
        for (int frame = 0; frame < frames; ++frame) {
            const int idx = frame * channels;
            int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx] * 0.5f, -1.0f, 1.0f) * 32767.0f));
            int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1] * 0.5f, -1.0f, 1.0f) * 32767.0f));
            const int32_t diffL = x1l - inL;
            const int32_t diffR = x1r - inR;
            x1l = inL;
            x1r = inR;
            inL = diffL / (1 << (kDcrAmount + 1)) - diffL + y1l;
            inR = diffR / (1 << (kDcrAmount + 1)) - diffR + y1r;
            y1l = inL - inL / (1 << kDcrAmount);
            y1r = inR - inR / (1 << kDcrAmount);

            const int32_t x_m = (inL + inR + 0x100) >> 9;
            bassY1 = (b0 * x_m + b1 * bassX1 + a1 * bassY1) >> 2;
            bassX1 = x_m;
            inL += bassY1;
            inR += bassY1;
            bassY1 = (bassY1 + 0x80) >> 8;
            buffer[idx] = (static_cast<float>(inL) / 32767.0f) * 2.0f;
            buffer[idx + 1] = (static_cast<float>(inR) / 32767.0f) * 2.0f;
        }
        bassDcrY1L = y1l;
        bassDcrX1L = x1l;
        bassDcrY1R = y1r;
        bassDcrX1R = x1r;
    } else {
        int32_t y1 = bassDcrY1L;
        int32_t x1 = bassDcrX1L;
        for (int frame = 0; frame < frames; ++frame) {
            int32_t inM = static_cast<int32_t>(std::lrint(std::clamp(buffer[frame] * 0.5f, -1.0f, 1.0f) * 32767.0f));
            const int32_t diff = x1 - inM;
            x1 = inM;
            inM = diff / (1 << (kDcrAmount + 1)) - diff + y1;
            y1 = inM - inM / (1 << kDcrAmount);

            const int32_t x_m = (inM + 0x80) >> 8;
            bassY1 = (b0 * x_m + b1 * bassX1 + a1 * bassY1) >> 2;
            bassX1 = x_m;
            inM += bassY1;
            bassY1 = (bassY1 + 0x40) >> 8;
            buffer[frame] = (static_cast<float>(inM) / 32767.0f) * 2.0f;
        }
        bassDcrY1L = y1;
        bassDcrX1L = x1;
    }
}

} // namespace siliconplayer::effects
