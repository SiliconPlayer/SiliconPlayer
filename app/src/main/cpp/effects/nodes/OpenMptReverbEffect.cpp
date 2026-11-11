/*
 * Adapted from the OpenMPT project (https://openmpt.org), BSD-3-Clause.
 * See effects/openmpt_dsp/LICENSE.OpenMPT for full terms.
 */

#include "OpenMptReverbEffect.h"
#include "../OpenMptDspShared.h"

#include <algorithm>
#include <array>
#include <cmath>
#include <cstdint>

namespace {

constexpr int kNumReverbPresets = 29;
constexpr int kReflectionsCount = 8;
constexpr int kSndmixReflectionsDelayMask = 0x1fff;
constexpr int kSndmixPrediffusionDelayMask = 0x7f;
constexpr int kSndmixReverbDelayMask = 0xfff;
constexpr int kRvbDif1LLen = 149 * 2;
constexpr int kRvbDif1RLen = 223 * 2;
constexpr int kRvbDif2LLen = 421 * 2;
constexpr int kRvbDif2RLen = 647 * 2;
constexpr int kRvbDly1LLen = 683 * 2;
constexpr int kRvbDly1RLen = 811 * 2;
constexpr int kRvbDly2LLen = 773 * 2;
constexpr int kRvbDly2RLen = 1013 * 2;
constexpr int kRvbDlyMask = 2047;
constexpr int kRvbMinRefDelay = 96;
constexpr int kRvbMaxRefDelay = 7500;
constexpr int kRvbMinRvbDelay = 128;
constexpr int kRvbMaxRvbDelay = 3800;
constexpr int kTankLengthAvg =
        (kRvbDif1LLen + kRvbDif1RLen + kRvbDif2LLen + kRvbDif2RLen +
         kRvbDly1LLen + kRvbDly1RLen + kRvbDly2LLen + kRvbDly2RLen) / 2;
constexpr float kGlobalReverbSendGain = 1.0f;

struct ReverbPresetProperties {
    int32_t room;
    int32_t roomHF;
    float decayTime;
    float decayHFRatio;
    int32_t reflections;
    float reflectionsDelay;
    int32_t reverb;
    float reverbDelay;
    float diffusion;
    float density;
};

struct ReflectionPreset {
    int32_t delayFactor;
    int16_t gainLL;
    int16_t gainRR;
    int16_t gainLR;
    int16_t gainRL;
};

struct EnvironmentReflection {
    int16_t gainLL;
    int16_t gainRR;
    int16_t gainLR;
    int16_t gainRL;
    uint32_t delay;
};

struct EnvironmentReverb {
    int32_t reverbLevel;
    int32_t reflectionsLevel;
    int32_t roomHF;
    uint32_t reverbDecay;
    int32_t preDiffusion;
    int32_t tankDiffusion;
    uint32_t reverbDelay;
    float reverbDamping;
    int32_t reverbDecaySamples;
    std::array<EnvironmentReflection, 8> reflections {};
};

constexpr std::array<ReverbPresetProperties, kNumReverbPresets> kReverbPresets = {{
        { -1000, -200, 1.30f, 0.90f, 0, 0.002f, 0, 0.010f, 100.0f, 75.0f },
        { -1000, -600, 1.10f, 0.83f, -400, 0.005f, 500, 0.010f, 100.0f, 100.0f },
        { -1000, -600, 1.30f, 0.83f, -1000, 0.010f, -200, 0.020f, 100.0f, 100.0f },
        { -1000, -600, 1.50f, 0.83f, -1600, 0.020f, -1000, 0.040f, 100.0f, 100.0f },
        { -1000, -600, 1.80f, 0.70f, -1300, 0.015f, -800, 0.030f, 100.0f, 100.0f },
        { -1000, -600, 1.80f, 0.70f, -2000, 0.030f, -1400, 0.060f, 100.0f, 100.0f },
        { -1000, -100, 1.49f, 0.83f, -2602, 0.007f, 200, 0.011f, 100.0f, 100.0f },
        { -1000, -6000, 0.17f, 0.10f, -1204, 0.001f, 207, 0.002f, 100.0f, 100.0f },
        { -1000, -454, 0.40f, 0.83f, -1646, 0.002f, 53, 0.003f, 100.0f, 100.0f },
        { -1000, -1200, 1.49f, 0.54f, -370, 0.007f, 1030, 0.011f, 100.0f, 60.0f },
        { -1000, -6000, 0.50f, 0.10f, -1376, 0.003f, -1104, 0.004f, 100.0f, 100.0f },
        { -1000, -300, 2.31f, 0.64f, -711, 0.012f, 83, 0.017f, 100.0f, 100.0f },
        { -1000, -476, 4.32f, 0.59f, -789, 0.020f, -289, 0.030f, 100.0f, 100.0f },
        { -1000, -500, 3.92f, 0.70f, -1230, 0.020f, -2, 0.029f, 100.0f, 100.0f },
        { -1000, 0, 2.91f, 1.30f, -602, 0.015f, -302, 0.022f, 100.0f, 100.0f },
        { -1000, -698, 7.24f, 0.33f, -1166, 0.020f, 16, 0.030f, 100.0f, 100.0f },
        { -1000, -1000, 10.05f, 0.23f, -602, 0.020f, 198, 0.030f, 100.0f, 100.0f },
        { -1000, -4000, 0.30f, 0.10f, -1831, 0.002f, -1630, 0.030f, 100.0f, 100.0f },
        { -1000, -300, 1.49f, 0.59f, -1219, 0.007f, 441, 0.011f, 100.0f, 100.0f },
        { -1000, -237, 2.70f, 0.79f, -1214, 0.013f, 395, 0.020f, 100.0f, 100.0f },
        { -1000, -270, 1.49f, 0.86f, -1204, 0.007f, -4, 0.011f, 100.0f, 100.0f },
        { -1000, -3300, 1.49f, 0.54f, -2560, 0.162f, -613, 0.088f, 79.0f, 100.0f },
        { -1000, -800, 1.49f, 0.67f, -2273, 0.007f, -2217, 0.011f, 50.0f, 100.0f },
        { -1000, -2500, 1.49f, 0.21f, -2780, 0.300f, -2014, 0.100f, 27.0f, 100.0f },
        { -1000, -1000, 1.49f, 0.83f, -10000, 0.061f, 500, 0.025f, 100.0f, 100.0f },
        { -1000, -2000, 1.49f, 0.50f, -2466, 0.179f, -2514, 0.100f, 21.0f, 100.0f },
        { -1000, 0, 1.65f, 1.50f, -1363, 0.008f, -1153, 0.012f, 100.0f, 100.0f },
        { -1000, -1000, 2.81f, 0.14f, 429, 0.014f, 648, 0.021f, 80.0f, 60.0f },
        { -1000, -4000, 1.49f, 0.10f, -449, 0.007f, 1700, 0.011f, 100.0f, 100.0f },
}};

constexpr std::array<ReflectionPreset, kReflectionsCount> kReflectionPresets = {{
        {0, 9830, 6554, 0, 0},
        {10, 6554, 13107, 0, 0},
        {24, -9830, 13107, 0, 0},
        {36, 13107, -6554, 0, 0},
        {54, 16384, 16384, -1638, -1638},
        {61, -13107, 8192, -328, -328},
        {73, -11468, -11468, -3277, 3277},
        {87, 13107, -9830, 4916, -4916},
}};

double mBToLinear64(int32_t valueMb) {
    if (!valueMb) return 1.0;
    if (valueMb <= -100000) return 0.0;
    const double val = valueMb * 3.321928094887362304 / (100.0 * 20.0);
    return std::pow(2.0, val - static_cast<int32_t>(0.5 + val));
}

void i3dl2ToGeneric(const ReverbPresetProperties& preset, EnvironmentReverb& rvb, float outputFreq) {
    rvb.reverbLevel = preset.reverb;
    rvb.reflectionsLevel = preset.reflections;
    rvb.roomHF = preset.roomHF;

    int32_t maxLevel = (rvb.reverbLevel > rvb.reflectionsLevel) ? rvb.reverbLevel : rvb.reflectionsLevel;
    if (maxLevel < -600) {
        maxLevel += 600;
        rvb.reverbLevel -= maxLevel;
        rvb.reflectionsLevel -= maxLevel;
    }

    const int32_t density = 8192 + static_cast<int32_t>(79.31f * preset.density);
    rvb.preDiffusion = density;
    int32_t tailDiffusion = static_cast<int32_t>((0.15f + preset.diffusion * (0.36f * 0.01f)) * 32767.0f);
    if (tailDiffusion > 0x7f00) tailDiffusion = 0x7f00;
    rvb.tankDiffusion = tailDiffusion;

    float reflectionsDelay = preset.reflectionsDelay;
    if (reflectionsDelay > 0.100f) reflectionsDelay = 0.100f;
    int32_t reverbDelay = static_cast<int32_t>(preset.reverbDelay * outputFreq);
    int32_t reflectionsDelaySamples = static_cast<int32_t>(reflectionsDelay * outputFreq);
    int32_t reverbDecayTime = static_cast<int32_t>(preset.decayTime * outputFreq);
    if (reflectionsDelaySamples < kRvbMinRefDelay) {
        reverbDelay -= (kRvbMinRefDelay - reflectionsDelaySamples);
        reflectionsDelaySamples = kRvbMinRefDelay;
    }
    if (reflectionsDelaySamples > kRvbMaxRefDelay) {
        reverbDelay += (reflectionsDelaySamples - kRvbMaxRefDelay);
        reflectionsDelaySamples = kRvbMaxRefDelay;
    }
    if (reverbDelay < kRvbMinRvbDelay) {
        reverbDecayTime -= (kRvbMinRvbDelay - reverbDelay);
        reverbDelay = kRvbMinRvbDelay;
    }
    if (reverbDelay > kRvbMaxRvbDelay) {
        reverbDecayTime += (reverbDelay - kRvbMaxRvbDelay);
        reverbDelay = kRvbMaxRvbDelay;
    }
    rvb.reverbDelay = static_cast<uint32_t>(reverbDelay);
    rvb.reverbDecaySamples = reverbDecayTime;

    for (int i = 0; i < kReflectionsCount; ++i) {
        rvb.reflections[static_cast<size_t>(i)].delay = static_cast<uint32_t>(
                reflectionsDelaySamples + ((kReflectionPresets[static_cast<size_t>(i)].delayFactor * reverbDelay + 50) / 100));
        rvb.reflections[static_cast<size_t>(i)].gainLL = kReflectionPresets[static_cast<size_t>(i)].gainLL;
        rvb.reflections[static_cast<size_t>(i)].gainRL = kReflectionPresets[static_cast<size_t>(i)].gainRL;
        rvb.reflections[static_cast<size_t>(i)].gainLR = kReflectionPresets[static_cast<size_t>(i)].gainLR;
        rvb.reflections[static_cast<size_t>(i)].gainRR = kReflectionPresets[static_cast<size_t>(i)].gainRR;
    }

    if (kTankLengthAvg < 10) return;
    const float delayFactor = (reverbDecayTime <= kTankLengthAvg)
            ? 1.0f
            : (static_cast<float>(kTankLengthAvg) / static_cast<float>(reverbDecayTime));
    rvb.reverbDecay = static_cast<uint32_t>(std::pow(0.001f, delayFactor) * 32768.0f);

    const float decayTimeHF = static_cast<float>(reverbDecayTime) * preset.decayHFRatio;
    const float delayFactorHF = (decayTimeHF <= static_cast<float>(kTankLengthAvg))
            ? 1.0f
            : (static_cast<float>(kTankLengthAvg) / decayTimeHF);
    rvb.reverbDamping = std::pow(0.001f, delayFactorHF);
}

int16_t readStereo(const std::vector<int16_t>& buffer, int pos, bool right) {
    return buffer[static_cast<size_t>(pos) * 2 + (right ? 1 : 0)];
}

void writeStereo(std::vector<int16_t>& buffer, int pos, int16_t l, int16_t r) {
    const size_t idx = static_cast<size_t>(pos) * 2;
    buffer[idx] = l;
    buffer[idx + 1] = r;
}

} // namespace

namespace siliconplayer::effects {

namespace {
constexpr ParamDescriptor kReverbParams[] = {
        { "depth", "Depth", ParamType::Int, 1.0f, 16.0f, 8.0f, 1.0f, 0 },
        { "preset", "Preset", ParamType::Enum, 0.0f, 28.0f, 0.0f, 1.0f, kNumReverbPresets },
};

IAudioEffect* createReverbEffect() {
    return new OpenMptReverbEffect();
}
} // namespace

const EffectDescriptor& OpenMptReverbEffect::descriptor() {
    static const EffectDescriptor desc {
            "openmpt.reverb",
            "Reverb",
            1,
            kReverbParams,
            static_cast<int>(sizeof(kReverbParams) / sizeof(kReverbParams[0])),
            false,
            &createReverbEffect,
    };
    return desc;
}

void OpenMptReverbEffect::prepare(int sampleRate, int /*channels*/) {
    currentSampleRate = sampleRate;
    const int safeRate = std::max(8000, sampleRate);
    if (safeRate == configuredSampleRate) {
        return;
    }
    configuredSampleRate = safeRate;
    reverbConfiguredPreset = -1;
    reverbConfiguredDepth = -1;
    reverbInputY1L = 0;
    reverbInputY1R = 0;
    reverbDcrX1L = 0;
    reverbDcrX1R = 0;
    reverbDcrY1L = 0;
    reverbDcrY1R = 0;
    reverbPreDifPos = 0;
    reverbDelayPos = 0;
    reverbRefOutPos = 0;
    reverbLateDelayPos = 0;
    reverbRoomHistoryL = 0;
    reverbRoomHistoryR = 0;
    reverbLpHist0L = 0;
    reverbLpHist0R = 0;
    reverbLpHist1L = 0;
    reverbLpHist1R = 0;
    reverbRefDelayBuffer.assign(static_cast<size_t>(kSndmixReflectionsDelayMask + 1) * 2, 0);
    reverbPreDifBuffer.assign(static_cast<size_t>(kSndmixPrediffusionDelayMask + 1) * 2, 0);
    reverbRefOutBuffer.assign(static_cast<size_t>(kSndmixReverbDelayMask + 1) * 2, 0);
    reverbDiffusion1.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDiffusion2.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDelay1.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
    reverbDelay2.assign(static_cast<size_t>(kRvbDlyMask + 1) * 2, 0);
}

void OpenMptReverbEffect::reset() {
    configuredSampleRate = 0;
    reverbConfiguredPreset = -1;
    reverbConfiguredDepth = -1;
    reverbInputY1L = 0;
    reverbInputY1R = 0;
    reverbDcrX1L = 0;
    reverbDcrX1R = 0;
    reverbDcrY1L = 0;
    reverbDcrY1R = 0;
    reverbRefMasterGain = 0;
    reverbLateMasterGain = 0;
    reverbReflectionsGain = 0;
    reverbPreDifPos = 0;
    reverbDelayPos = 0;
    reverbRefOutPos = 0;
    reverbLateDelay = 0;
    reverbLateDelayPos = 0;
    reverbRoomLpCoeffL = 0;
    reverbRoomLpCoeffR = 0;
    reverbRoomHistoryL = 0;
    reverbRoomHistoryR = 0;
    reverbPreDifCoeffL = 0;
    reverbPreDifCoeffR = 0;
    reverbDifCoeffL = 0;
    reverbDifCoeffR = 0;
    reverbDecayDcL = 0;
    reverbDecayDcR = 0;
    reverbDecayLpL = 0;
    reverbDecayLpR = 0;
    reverbLpHist0L = 0;
    reverbLpHist0R = 0;
    reverbLpHist1L = 0;
    reverbLpHist1R = 0;
    reverbOutGain0L = 0;
    reverbOutGain0R = 0;
    reverbOutGain1L = 0;
    reverbOutGain1R = 0;
    for (auto& ref : reverbReflections) {
        ref = {};
    }
    reverbRefDelayBuffer.clear();
    reverbPreDifBuffer.clear();
    reverbRefOutBuffer.clear();
    reverbDiffusion1.clear();
    reverbDiffusion2.clear();
    reverbDelay1.clear();
    reverbDelay2.clear();
    reverbWetWork.clear();
    reverbDryWork.clear();
}

void OpenMptReverbEffect::setParam(int index, float value) {
    if (!std::isfinite(value)) return;
    const int iv = static_cast<int>(std::lrint(value));
    if (index == 0) {
        depthParam.store(std::clamp(iv, 1, 16), std::memory_order_relaxed);
    } else if (index == 1) {
        presetParam.store(std::clamp(iv, 0, kNumReverbPresets - 1), std::memory_order_relaxed);
    }
}

void OpenMptReverbEffect::process(float* buffer, int frames, int channels) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    if (configuredSampleRate == 0) {
        prepare(currentSampleRate > 0 ? currentSampleRate : 48000, channels);
    }
    if (channels < 2 || reverbRefDelayBuffer.empty() || reverbDelay2.empty()) {
        return;
    }

    using openmpt_detail::kMixScale;

    configureReverb(currentSampleRate, presetParam.load(std::memory_order_relaxed));

    const int depth = std::clamp(depthParam.load(std::memory_order_relaxed), 1, 16);
    if (depth != reverbConfiguredDepth) {
        int32_t masterGain = (reverbRefMasterGain * depth) >> 4;
        if (masterGain > 0x7fff) masterGain = 0x7fff;
        reverbReflectionsGain = masterGain;

        masterGain = (reverbLateMasterGain * depth) >> 4;
        if (masterGain > 0x10000) masterGain = 0x10000;
        reverbOutGain0L = static_cast<int16_t>((masterGain + 0x7f) >> 3);
        reverbOutGain0R = static_cast<int16_t>((masterGain + 0xff) >> 4);
        reverbOutGain1L = static_cast<int16_t>((masterGain + 0xff) >> 4);
        reverbOutGain1R = static_cast<int16_t>((masterGain + 0x7f) >> 3);
        reverbConfiguredDepth = depth;
    }

    int32_t maxRvbGain = (reverbRefMasterGain > reverbLateMasterGain) ? reverbRefMasterGain : reverbLateMasterGain;
    if (maxRvbGain > 32768) maxRvbGain = 32768;
    int32_t dryVol = (36 - depth) >> 1;
    if (dryVol < 8) dryVol = 8;
    if (dryVol > 16) dryVol = 16;
    dryVol = 16 - (((16 - dryVol) * maxRvbGain) >> 15);

    constexpr int kMaxChunkFrames = 64;
    reverbWetWork.resize(static_cast<size_t>(kMaxChunkFrames) * 2);
    reverbDryWork.resize(static_cast<size_t>(kMaxChunkFrames) * 2);

    int framesRemaining = frames;
    int frameOffset = 0;

    while (framesRemaining > 0) {
        const int chunk = std::min(framesRemaining, kMaxChunkFrames);

        for (int frame = 0; frame < chunk; ++frame) {
            const int idx = (frameOffset + frame) * channels;
            const int32_t inL = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx] * 0.5f, -1.0f, 1.0f) * kMixScale));
            const int32_t inR = static_cast<int32_t>(std::lrint(std::clamp(buffer[idx + 1] * 0.5f, -1.0f, 1.0f) * kMixScale));
            reverbWetWork[static_cast<size_t>(frame) * 2] = static_cast<int32_t>(std::lrint(static_cast<float>(inL) * kGlobalReverbSendGain));
            reverbWetWork[static_cast<size_t>(frame) * 2 + 1] = static_cast<int32_t>(std::lrint(static_cast<float>(inR) * kGlobalReverbSendGain));
            reverbDryWork[static_cast<size_t>(frame) * 2] = 0;
            reverbDryWork[static_cast<size_t>(frame) * 2 + 1] = 0;
        }

        applyReverbDryMix(reverbDryWork.data(), reverbWetWork.data(), dryVol, chunk);

        for (int i = 0; i < chunk; ++i) {
            const int pos = i * 2;
            const int32_t xL = reverbWetWork[static_cast<size_t>(pos)] >> 12;
            const int32_t xR = reverbWetWork[static_cast<size_t>(pos + 1)] >> 12;
            reverbInputY1L = xL + (((xL - reverbInputY1L) * reverbRoomLpCoeffL) >> 15);
            reverbInputY1R = xR + (((xR - reverbInputY1R) * reverbRoomLpCoeffR) >> 15);
            reverbWetWork[static_cast<size_t>(pos)] = reverbInputY1L;
            reverbWetWork[static_cast<size_t>(pos + 1)] = reverbInputY1R;
        }

        processReverbPreDelay(reverbWetWork.data(), chunk);
        processReverbReflections(reverbWetWork.data(), chunk);
        processReverbLate(reverbWetWork.data(), chunk);
        processReverbPost(reverbWetWork.data(), reverbDryWork.data(), chunk);

        for (int frame = 0; frame < chunk; ++frame) {
            const int idx = (frameOffset + frame) * channels;
            buffer[idx] = static_cast<float>(reverbDryWork[static_cast<size_t>(frame) * 2] / kMixScale) * 2.0f;
            buffer[idx + 1] = static_cast<float>(reverbDryWork[static_cast<size_t>(frame) * 2 + 1] / kMixScale) * 2.0f;
        }

        frameOffset += chunk;
        framesRemaining -= chunk;
    }
}

void OpenMptReverbEffect::configureReverb(int sampleRate, int preset) {
    const int clampedPreset = std::clamp(preset, 0, kNumReverbPresets - 1);
    if (reverbConfiguredPreset == clampedPreset) {
        return;
    }

    using openmpt_detail::clamp16;
    using openmpt_detail::onePoleLowPassCoef;
    using openmpt_detail::saturateRound;

    EnvironmentReverb rvb {};
    i3dl2ToGeneric(kReverbPresets[static_cast<size_t>(clampedPreset)], rvb, static_cast<float>(std::max(8000, sampleRate)));

    const int32_t roomLp = onePoleLowPassCoef(32768, mBToLinear64(rvb.roomHF), 5000.0, static_cast<double>(std::max(8000, sampleRate)));
    reverbRoomLpCoeffL = clamp16(roomLp);
    reverbRoomLpCoeffR = clamp16(roomLp);
    reverbPreDifCoeffL = clamp16(rvb.preDiffusion * 2);
    reverbPreDifCoeffR = clamp16(rvb.preDiffusion * 2);

    for (int i = 0; i < kReflectionsCount; ++i) {
        reverbReflections[static_cast<size_t>(i)].delay = rvb.reflections[static_cast<size_t>(i)].delay;
        reverbReflections[static_cast<size_t>(i)].gainLL = rvb.reflections[static_cast<size_t>(i)].gainLL;
        reverbReflections[static_cast<size_t>(i)].gainRL = rvb.reflections[static_cast<size_t>(i)].gainRL;
        reverbReflections[static_cast<size_t>(i)].gainLR = rvb.reflections[static_cast<size_t>(i)].gainLR;
        reverbReflections[static_cast<size_t>(i)].gainRR = rvb.reflections[static_cast<size_t>(i)].gainRR;
    }

    reverbLateDelay = rvb.reverbDelay;
    reverbRefMasterGain = (rvb.reflectionsLevel > -9000) ? saturateRound<int32_t>(mBToLinear64(rvb.reflectionsLevel) * 32768.0) : 0;
    reverbLateMasterGain = (rvb.reverbLevel > -9000) ? saturateRound<int32_t>(mBToLinear64(rvb.reverbLevel) * 32768.0) : 0;

    int32_t tailDiffusion = rvb.tankDiffusion;
    if (tailDiffusion > 0x7f00) tailDiffusion = 0x7f00;
    reverbDifCoeffL = clamp16(tailDiffusion);
    reverbDifCoeffR = clamp16(tailDiffusion);

    int32_t decay = static_cast<int32_t>(rvb.reverbDecay);
    decay = std::clamp(decay, 0, 0x7ff0);
    reverbDecayDcL = clamp16(decay);
    reverbDecayDcR = clamp16(decay);

    const float damping = rvb.reverbDamping * rvb.reverbDamping;
    int32_t dampingLp = onePoleLowPassCoef(32768, static_cast<double>(damping), 5000.0, static_cast<double>(std::max(8000, sampleRate)));
    dampingLp = std::clamp(dampingLp, 0x100, 0x7f00);
    reverbDecayLpL = clamp16(dampingLp);
    reverbDecayLpR = clamp16(dampingLp);

    reverbConfiguredPreset = clampedPreset;
    reverbConfiguredDepth = -1;
}

void OpenMptReverbEffect::processReverbPreDelay(const int32_t* in, int32_t frames) {
    using openmpt_detail::clamp16;

    uint32_t preDifPos = reverbPreDifPos;
    uint32_t delayPos = reverbDelayPos - 1;
    int16_t historyL = reverbRoomHistoryL;
    int16_t historyR = reverbRoomHistoryR;

    for (int32_t i = 0; i < frames; ++i) {
        const int32_t inL = clamp16(in[static_cast<size_t>(i) * 2]);
        const int32_t inR = clamp16(in[static_cast<size_t>(i) * 2 + 1]);
        const int32_t lpL = (clamp16(static_cast<int32_t>(historyL) - inL) * reverbRoomLpCoeffL) / 65536;
        const int32_t lpR = (clamp16(static_cast<int32_t>(historyR) - inR) * reverbRoomLpCoeffR) / 65536;
        historyL = clamp16(clamp16(lpL + lpL) + inL);
        historyR = clamp16(clamp16(lpR + lpR) + inR);

        const int32_t preDifL = readStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), false);
        const int32_t preDifR = readStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), true);
        preDifPos = (preDifPos + 1) & kSndmixPrediffusionDelayMask;
        delayPos = (delayPos + 1) & kSndmixReflectionsDelayMask;

        const int16_t preDif2L = clamp16(static_cast<int32_t>(historyL) - (preDifL * reverbPreDifCoeffL) / 65536);
        const int16_t preDif2R = clamp16(static_cast<int32_t>(historyR) - (preDifR * reverbPreDifCoeffR) / 65536);
        writeStereo(reverbPreDifBuffer, static_cast<int>(preDifPos), preDif2L, preDif2R);
        writeStereo(
                reverbRefDelayBuffer,
                static_cast<int>(delayPos),
                clamp16((reverbPreDifCoeffL * preDif2L) / 65536 + preDifL),
                clamp16((reverbPreDifCoeffR * preDif2R) / 65536 + preDifR));
    }

    reverbPreDifPos = preDifPos;
    reverbRoomHistoryL = historyL;
    reverbRoomHistoryR = historyR;
}

void OpenMptReverbEffect::processReverbReflections(int32_t* out, int32_t frames) {
    using openmpt_detail::clamp16;

    int pos[7];
    for (int i = 0; i < 7; ++i) {
        pos[i] = static_cast<int>(reverbDelayPos) - static_cast<int>(reverbReflections[static_cast<size_t>(i)].delay) - 1;
    }
    const int16_t refGain = static_cast<int16_t>(reverbReflectionsGain >> 3);
    const uint32_t refOutStart = reverbRefOutPos;

    for (int32_t frame = 0; frame < frames; ++frame) {
        int32_t refOutL = 0;
        int32_t refOutR = 0;
        for (int i = 0; i < 4; ++i) {
            pos[i] = (pos[i] + 1) & kSndmixReflectionsDelayMask;
            const int16_t refL = readStereo(reverbRefDelayBuffer, pos[i], false);
            const int16_t refR = readStereo(reverbRefDelayBuffer, pos[i], true);
            const auto& refl = reverbReflections[static_cast<size_t>(i)];
            refOutL += refL * refl.gainLL + refR * refl.gainRL;
            refOutR += refL * refl.gainLR + refR * refl.gainRR;
        }
        const int16_t stage1L = clamp16(refOutL / (1 << 15));
        const int16_t stage1R = clamp16(refOutR / (1 << 15));

        refOutL = 0;
        refOutR = 0;
        for (int i = 4; i < 7; ++i) {
            pos[i] = (pos[i] + 1) & kSndmixReflectionsDelayMask;
            const int16_t refL = readStereo(reverbRefDelayBuffer, pos[i], false);
            const int16_t refR = readStereo(reverbRefDelayBuffer, pos[i], true);
            const auto& refl = reverbReflections[static_cast<size_t>(i)];
            refOutL += refL * refl.gainLL + refR * refl.gainRL;
            refOutR += refL * refl.gainLR + refR * refl.gainRR;
        }

        const int16_t refMixL = clamp16(stage1L + refOutL / (1 << 15));
        const int16_t refMixR = clamp16(stage1R + refOutR / (1 << 15));
        const int refOutPos = static_cast<int>((refOutStart + static_cast<uint32_t>(frame)) & kSndmixReverbDelayMask);
        writeStereo(reverbRefOutBuffer, refOutPos, refMixL, refMixR);
        out[static_cast<size_t>(frame) * 2] = refMixL * refGain;
        out[static_cast<size_t>(frame) * 2 + 1] = refMixR * refGain;
    }

    reverbRefOutPos = (reverbRefOutPos + static_cast<uint32_t>(frames)) & kSndmixReverbDelayMask;
    reverbDelayPos = (reverbDelayPos + static_cast<uint32_t>(frames)) & kSndmixReflectionsDelayMask;
}

void OpenMptReverbEffect::processReverbLate(int32_t* out, int32_t frames) {
    using openmpt_detail::clamp16;

    int delayPos = static_cast<int>(reverbLateDelayPos) & kRvbDlyMask;
    const uint32_t refStart = (reverbRefOutPos - static_cast<uint32_t>(frames)) & kSndmixReverbDelayMask;
    const uint32_t refReadStart = (refStart - reverbLateDelay) & kSndmixReverbDelayMask;

    for (int32_t frame = 0; frame < frames; ++frame) {
        const int refPos = static_cast<int>((refReadStart + static_cast<uint32_t>(frame)) & kSndmixReverbDelayMask);
        const int16_t refInL = readStereo(reverbRefOutBuffer, refPos, false);
        const int16_t refInR = readStereo(reverbRefOutBuffer, refPos, true);

        const int delay2LPos = (delayPos - kRvbDly2LLen) & kRvbDlyMask;
        const int delay2RPos = (delayPos - kRvbDly2RLen) & kRvbDlyMask;
        const int32_t delay2LL = readStereo(reverbDelay2, delay2LPos, false);
        const int32_t delay2LR = readStereo(reverbDelay2, delay2LPos, true);
        const int32_t delay2RL = readStereo(reverbDelay2, delay2RPos, false);
        const int32_t delay2RR = readStereo(reverbDelay2, delay2RPos, true);

        const int32_t diff1L = readStereo(reverbDiffusion1, (delayPos - kRvbDif1LLen) & kRvbDlyMask, false);
        const int32_t diff1R = readStereo(reverbDiffusion1, (delayPos - kRvbDif1RLen) & kRvbDlyMask, true);
        const int32_t diff2L = readStereo(reverbDiffusion2, (delayPos - kRvbDif2LLen) & kRvbDlyMask, false);
        const int32_t diff2R = readStereo(reverbDiffusion2, (delayPos - kRvbDif2RLen) & kRvbDlyMask, true);

        const int32_t lpDecayLL = clamp16(static_cast<int32_t>(reverbLpHist0L) - delay2LL) * reverbDecayLpL / 65536;
        const int32_t lpDecayLR = clamp16(static_cast<int32_t>(reverbLpHist0R) - delay2LR) * reverbDecayLpR / 65536;
        const int32_t lpDecayRL = clamp16(static_cast<int32_t>(reverbLpHist1L) - delay2RL) * reverbDecayLpL / 65536;
        const int32_t lpDecayRR = clamp16(static_cast<int32_t>(reverbLpHist1R) - delay2RR) * reverbDecayLpR / 65536;
        reverbLpHist0L = clamp16(clamp16(lpDecayLL + lpDecayLL) + delay2LL);
        reverbLpHist0R = clamp16(clamp16(lpDecayLR + lpDecayLR) + delay2LR);
        reverbLpHist1L = clamp16(clamp16(lpDecayRL + lpDecayRL) + delay2RL);
        reverbLpHist1R = clamp16(clamp16(lpDecayRR + lpDecayRR) + delay2RR);

        const int32_t histDecayL = clamp16((reverbDecayDcL * reverbLpHist0L) / (1 << 15));
        const int32_t histDecayR = clamp16((reverbDecayDcR * reverbLpHist1R) / (1 << 15));
        const int32_t histDecayInL = clamp16(histDecayL + refInL / 4);
        const int32_t histDecayInR = clamp16(histDecayR + refInR / 4);
        const int32_t histDecayInDiffL = clamp16(histDecayInL - (diff1L * reverbDifCoeffL) / 65536);
        const int32_t histDecayInDiffR = clamp16(histDecayInR - (diff1R * reverbDifCoeffR) / 65536);
        writeStereo(reverbDiffusion1, delayPos, static_cast<int16_t>(histDecayInDiffL), static_cast<int16_t>(histDecayInDiffR));

        const int32_t delay1L = clamp16((reverbDifCoeffL * histDecayInDiffL) / 65536 + diff1L);
        const int32_t delay1R = clamp16((reverbDifCoeffR * histDecayInDiffR) / 65536 + diff1R);
        writeStereo(reverbDelay1, delayPos, static_cast<int16_t>(delay1L), static_cast<int16_t>(delay1R));
        const int32_t histDecayInDelayL = clamp16(histDecayInL + delay1L);
        const int32_t histDecayInDelayR = clamp16(histDecayInR + delay1R);

        const int delay1LPos = (delayPos - kRvbDly1LLen) & kRvbDlyMask;
        const int delay1RPos = (delayPos - kRvbDly1RLen) & kRvbDlyMask;
        const int32_t delay1LL = readStereo(reverbDelay1, delay1LPos, false);
        const int32_t delay1LR = readStereo(reverbDelay1, delay1LPos, true);
        const int32_t delay1RL = readStereo(reverbDelay1, delay1RPos, false);
        const int32_t delay1RR = readStereo(reverbDelay1, delay1RPos, true);

        const int32_t delay1GainsL = clamp16((delay1LL * reverbDif2InGain0L + delay1LR * reverbDif2InGain0R) / (1 << 15));
        const int32_t delay1GainsR = clamp16((delay1RL * reverbDif2InGain1L + delay1RR * reverbDif2InGain1R) / (1 << 15));
        const int32_t histDelay1LL = clamp16(clamp16(histDecayInDelayL + delay1LL) - delay1GainsL);
        const int32_t histDelay1LR = clamp16(clamp16(histDecayInDelayR + delay1LR) - delay1GainsR);
        const int32_t histDelay1RL = clamp16(clamp16(histDecayInDelayL + delay1RL) - delay1GainsL);
        const int32_t histDelay1RR = clamp16(clamp16(histDecayInDelayR + delay1RR) - delay1GainsR);
        const int32_t diff2outL = clamp16(delay1GainsL - (diff2L * reverbDifCoeffL) / 65536);
        const int32_t diff2outR = clamp16(delay1GainsR - (diff2R * reverbDifCoeffR) / 65536);
        const int32_t diff2outCoeffsL = (reverbDifCoeffL * diff2outL) / 65536;
        const int32_t diff2outCoeffsR = (reverbDifCoeffR * diff2outR) / 65536;
        writeStereo(reverbDiffusion2, delayPos, static_cast<int16_t>(diff2outL), static_cast<int16_t>(diff2outR));

        const int32_t delay2outL = clamp16(diff2outCoeffsL + diff2L);
        const int32_t delay2outR = clamp16(diff2outCoeffsR + diff2R);
        writeStereo(reverbDelay2, delayPos, static_cast<int16_t>(delay2outL), static_cast<int16_t>(delay2outR));
        delayPos = (delayPos + 1) & kRvbDlyMask;

        out[static_cast<size_t>(frame) * 2] += clamp16(histDelay1LL + delay2outL) * reverbOutGain0L
                                             + clamp16(histDelay1LR + delay2outR) * reverbOutGain0R;
        out[static_cast<size_t>(frame) * 2 + 1] += clamp16(histDelay1RL + clamp16(diff2outCoeffsL)) * reverbOutGain1L
                                                 + clamp16(histDelay1RR + clamp16(diff2outCoeffsR)) * reverbOutGain1R;
    }
    reverbLateDelayPos = static_cast<uint32_t>(delayPos);
}

void OpenMptReverbEffect::processReverbPost(const int32_t* wet, int32_t* dry, int32_t frames) {
    using openmpt_detail::kDcrAmount;

    int32_t x1L = reverbDcrX1L;
    int32_t x1R = reverbDcrX1R;
    int32_t y1L = reverbDcrY1L;
    int32_t y1R = reverbDcrY1R;
    int32_t inL = 0;
    int32_t inR = 0;
    for (int32_t i = 0; i < frames; ++i) {
        inL = wet[static_cast<size_t>(i) * 2];
        inR = wet[static_cast<size_t>(i) * 2 + 1];
        int32_t outL = dry[static_cast<size_t>(i) * 2];
        int32_t outR = dry[static_cast<size_t>(i) * 2 + 1];

        x1L -= inL;
        x1R -= inR;
        x1L = x1L / (1 << (kDcrAmount + 1)) - x1L;
        x1R = x1R / (1 << (kDcrAmount + 1)) - x1R;
        y1L += x1L;
        y1R += x1R;
        outL += y1L;
        outR += y1R;
        y1L -= y1L / (1 << kDcrAmount);
        y1R -= y1R / (1 << kDcrAmount);
        x1L = inL;
        x1R = inR;

        dry[static_cast<size_t>(i) * 2] = outL;
        dry[static_cast<size_t>(i) * 2 + 1] = outR;
    }
    reverbDcrX1L = inL;
    reverbDcrX1R = inR;
    reverbDcrY1L = y1L;
    reverbDcrY1R = y1R;
}

void OpenMptReverbEffect::applyReverbDryMix(int32_t* dry, const int32_t* wet, int32_t dryVol, int32_t frames) {
    for (int32_t i = 0; i < frames; ++i) {
        dry[static_cast<size_t>(i) * 2] += (wet[static_cast<size_t>(i) * 2] >> 4) * dryVol;
        dry[static_cast<size_t>(i) * 2 + 1] += (wet[static_cast<size_t>(i) * 2 + 1] >> 4) * dryVol;
    }
}

} // namespace siliconplayer::effects
