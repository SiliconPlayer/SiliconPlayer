/*
 * Adapted from the OpenMPT project (https://openmpt.org), BSD-3-Clause.
 * See effects/openmpt_dsp/LICENSE.OpenMPT for full terms.
 */

#ifndef SILICONPLAYER_EFFECTS_OPENMPT_DSP_SHARED_H
#define SILICONPLAYER_EFFECTS_OPENMPT_DSP_SHARED_H

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <limits>

namespace siliconplayer::effects::openmpt_detail {

constexpr float kPi = 3.14159265358979323846f;
constexpr int kDcrAmount = 9;
constexpr float kMixScale = static_cast<float>(1 << 24);

inline float clampSample(float sample) {
    return std::clamp(sample, -1.0f, 1.0f);
}

template <typename T>
inline T saturateRound(double value) {
    const double minV = static_cast<double>(std::numeric_limits<T>::lowest());
    const double maxV = static_cast<double>(std::numeric_limits<T>::max());
    const double clamped = std::clamp(value, minV, maxV);
    return static_cast<T>(std::llround(clamped));
}

inline float sgn(float x) {
    return (x >= 0.0f) ? 1.0f : -1.0f;
}

inline int16_t clamp16(int32_t x) {
    return static_cast<int16_t>(std::clamp(
            x,
            static_cast<int32_t>(std::numeric_limits<int16_t>::min()),
            static_cast<int32_t>(std::numeric_limits<int16_t>::max())));
}

inline void shelfEq(
        int32_t scale,
        int32_t& outA1,
        int32_t& outB0,
        int32_t& outB1,
        int32_t fc,
        int32_t fs,
        float gainDC,
        float gainFT,
        float gainPI) {
    float a1;
    float b0;
    float b1;
    const float wT = kPi * static_cast<float>(fc) / static_cast<float>(fs);
    const float gainPI2 = gainPI * gainPI;
    const float gainFT2 = gainFT * gainFT;
    const float gainDC2 = gainDC * gainDC;
    float quad = gainPI2 + gainDC2 - (gainFT2 * 2.0f);

    float alpha = 0.0f;
    if (quad != 0.0f) {
        const float lambda = (gainPI2 - gainDC2) / quad;
        // Double-wide libm: the float sqrt resolves past Debian-stable glibc.
        alpha = lambda - (sgn(lambda) * std::sqrt(static_cast<double>(std::max((lambda * lambda) - 1.0f, 0.0f))));
    }

    const float beta0 = 0.5f * ((gainDC + gainPI) + (gainDC - gainPI) * alpha);
    const float beta1 = 0.5f * ((gainDC - gainPI) + (gainDC + gainPI) * alpha);
    const float rho = std::sin((wT * 0.5f) - (kPi / 4.0f)) / std::sin((wT * 0.5f) + (kPi / 4.0f));

    quad = 1.0f / (1.0f + rho * alpha);
    b0 = ((beta0 + rho * beta1) * quad);
    b1 = ((beta1 + rho * beta0) * quad);
    a1 = -((rho + alpha) * quad);

    outA1 = saturateRound<int32_t>(a1 * static_cast<float>(scale));
    outB0 = saturateRound<int32_t>(b0 * static_cast<float>(scale));
    outB1 = saturateRound<int32_t>(b1 * static_cast<float>(scale));
}

inline int32_t onePoleLowPassCoef(int32_t scale, double g, double fc, double fs) {
    if (g > 0.999999) return 0;
    g *= g;
    const double scaleOver1Mg = scale / (1.0 - g);
    const double cosw = std::cos((2.0 * kPi) * fc / fs);
    return saturateRound<int32_t>(
            (1.0 - (std::sqrt((g + g) * (1.0 - cosw) - g * g * (1.0 - cosw * cosw)) + g * cosw)) *
            scaleOver1Mg);
}

} // namespace siliconplayer::effects::openmpt_detail

#endif // SILICONPLAYER_EFFECTS_OPENMPT_DSP_SHARED_H
