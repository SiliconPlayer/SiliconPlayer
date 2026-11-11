#include "SpatializerEngine.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace siliconplayer::spatial {

namespace {

constexpr float kInvSqrt2 = 0.70710678f;

float dbToLinearClamped(float db, float minDb, float maxDb) {
    if (!std::isfinite(db)) {
        return 1.0f;
    }
    const float clamped = std::clamp(db, minDb, maxDb);
    return std::pow(10.0f, clamped / 20.0f);
}

std::unique_ptr<ISpatializer> createBackendInstance(SpatializerBackendId backendId) {
    (void)backendId;
    return nullptr;
}

} // namespace

void SpatializerEngine::setConfig(const SpatializerConfig& config) {
    const bool backendChanged = (config.backend != currentConfig.backend);
    currentConfig = config;
    if (backendChanged || currentConfig.backend == SpatializerBackendId::None) {
        activeBackend = createBackendInstance(currentConfig.backend);
        preparedSampleRate = 0;
        preparedInputChannels = 0;
        preparedMaxFrames = 0;
    } else if (activeBackend) {
        activeBackend->updateConfig(currentConfig);
    }
    refreshAtomicFlagsLocked();
}

void SpatializerEngine::refreshAtomicFlagsLocked() {
    const bool active = currentConfig.enabled &&
                        currentConfig.backend != SpatializerBackendId::None &&
                        activeBackend != nullptr;
    activeFlag.store(active, std::memory_order_relaxed);
    spatializeStereoFlag.store(currentConfig.spatializeStereo, std::memory_order_relaxed);
    const double lat = (active && activeBackend)
            ? static_cast<double>(std::max(0, activeBackend->latencyFrames()))
            : 0.0;
    cachedLatencyFrames.store(lat, std::memory_order_relaxed);
}

void SpatializerEngine::reset() {
    lfeBiquad1.reset();
    lfeBiquad2.reset();
    if (activeBackend) {
        activeBackend->reset();
    }
}

void SpatializerEngine::updateLfeLowPassCoeffs(int sampleRate) {
    if (sampleRate <= 0 || sampleRate == lfeFilterSampleRate) {
        return;
    }
    lfeFilterSampleRate = sampleRate;
    // 2nd-order Butterworth low-pass at 120 Hz (Q = 1 / sqrt(2)); two cascaded = 4th-order Linkwitz-Riley.
    constexpr double kCutoffHz = 120.0;
    const double nyquist = std::max(240.0, static_cast<double>(sampleRate) * 0.5);
    const double fc = std::clamp(kCutoffHz, 20.0, nyquist * 0.45);
    const double omega = 2.0 * M_PI * fc / static_cast<double>(sampleRate);
    const double sn = std::sin(omega);
    const double cs = std::cos(omega);
    const double alpha = sn / (2.0 * M_SQRT1_2);
    const double a0 = 1.0 + alpha;
    const float b0 = static_cast<float>(((1.0 - cs) * 0.5) / a0);
    const float b1 = static_cast<float>((1.0 - cs) / a0);
    const float b2 = b0;
    const float a1 = static_cast<float>((-2.0 * cs) / a0);
    const float a2 = static_cast<float>((1.0 - alpha) / a0);

    lfeBiquad1.b0 = b0;
    lfeBiquad1.b1 = b1;
    lfeBiquad1.b2 = b2;
    lfeBiquad1.a1 = a1;
    lfeBiquad1.a2 = a2;
    lfeBiquad1.reset();

    lfeBiquad2.b0 = b0;
    lfeBiquad2.b1 = b1;
    lfeBiquad2.b2 = b2;
    lfeBiquad2.a1 = a1;
    lfeBiquad2.a2 = a2;
    lfeBiquad2.reset();
}

void SpatializerEngine::applyBedChannelTrimsInPlace(
        float* bedInterleaved,
        int frames,
        int decodeChannels,
        BedLayoutId bedLayout) {
    if (!bedInterleaved || frames <= 0 || decodeChannels <= 2) {
        return;
    }
    const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
    const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
    const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);

    if (std::abs(centerGain - 1.0f) < 1e-4f &&
        std::abs(surroundGain - 1.0f) < 1e-4f &&
        std::abs(heightGain - 1.0f) < 1e-4f) {
        return;
    }

    for (int f = 0; f < frames; ++f) {
        float* frame = bedInterleaved + static_cast<size_t>(f) * decodeChannels;
        if (bedLayout == BedLayoutId::Quad_4_0 && decodeChannels >= 4) {
            frame[2] *= surroundGain;
            frame[3] *= surroundGain;
        } else if (decodeChannels >= 6) {
            frame[2] *= centerGain; // FC
            for (int ch = 4; ch < std::min(decodeChannels, 8); ++ch) {
                frame[ch] *= surroundGain; // BL, BR, SL, SR
            }
            for (int ch = 8; ch < std::min(decodeChannels, 12); ++ch) {
                frame[ch] *= heightGain; // TFL, TFR, TBL, TBR
            }
        }
    }
}

void SpatializerEngine::downmixBedToStereo(
        const float* inInterleaved,
        int decodeChannels,
        BedLayoutId bedLayout,
        float* outStereo,
        int frames,
        float centerGainLinear,
        float surroundGainLinear,
        float heightGainLinear,
        float lfeGainLinear) {
    if (!inInterleaved || !outStereo || frames <= 0 || decodeChannels <= 0) {
        return;
    }

    if (decodeChannels == 1) {
        // Descending order so in-place mono -> stereo expansion never clobbers unread frames.
        for (int f = frames - 1; f >= 0; --f) {
            const float s = inInterleaved[f];
            outStereo[f * 2] = s;
            outStereo[f * 2 + 1] = s;
        }
        return;
    }

    if (decodeChannels == 2) {
        if (inInterleaved != outStereo) {
            std::memcpy(outStereo, inInterleaved, static_cast<size_t>(frames) * 2 * sizeof(float));
        }
        return;
    }

    const float cCoeff = kInvSqrt2 * centerGainLinear;
    const float sCoeff = kInvSqrt2 * surroundGainLinear;
    const float hCoeff = 0.5f * heightGainLinear;
    const float lfeCoeff = kInvSqrt2 * lfeGainLinear;

    if (bedLayout == BedLayoutId::Quad_4_0 && decodeChannels == 4) {
        const float norm = 1.0f / std::max(1.0f, 1.0f + sCoeff);
        for (int f = 0; f < frames; ++f) {
            const float* src = inInterleaved + static_cast<size_t>(f) * 4;
            const float fl = src[0];
            const float fr = src[1];
            const float bl = src[2];
            const float br = src[3];
            outStereo[f * 2] = (fl + sCoeff * bl) * norm;
            outStereo[f * 2 + 1] = (fr + sCoeff * br) * norm;
        }
        return;
    }

    if (decodeChannels >= 6) {
        const bool has71 = decodeChannels >= 8;
        const bool hasHeight = decodeChannels >= 12;
        float maxColSum = 1.0f + cCoeff + lfeCoeff + sCoeff;
        if (has71) {
            maxColSum += sCoeff;
        }
        if (hasHeight) {
            maxColSum += 2.0f * hCoeff;
        }
        const float norm = 1.0f / std::max(1.0f, maxColSum);

        for (int f = 0; f < frames; ++f) {
            const float* src = inInterleaved + static_cast<size_t>(f) * decodeChannels;
            const float fl = src[0];
            const float fr = src[1];
            const float fc = src[2] * cCoeff;
            const float lfe = src[3] * lfeCoeff;
            float left = fl + fc + lfe + src[4] * sCoeff;
            float right = fr + fc + lfe + src[5] * sCoeff;
            if (has71) {
                left += src[6] * sCoeff;
                right += src[7] * sCoeff;
            }
            if (hasHeight) {
                left += (src[8] + src[10]) * hCoeff;
                right += (src[9] + src[11]) * hCoeff;
            }
            outStereo[f * 2] = left * norm;
            outStereo[f * 2 + 1] = right * norm;
        }
        return;
    }

    // Generic fallback for unusual channel counts (e.g. 3ch FL/FR/FC or 5ch FL/FR/FC/BL/BR).
    for (int f = 0; f < frames; ++f) {
        const float* src = inInterleaved + static_cast<size_t>(f) * decodeChannels;
        const float fl = src[0];
        const float fr = decodeChannels > 1 ? src[1] : src[0];
        const float fc = decodeChannels > 2 ? src[2] * cCoeff : 0.0f;
        float surL = 0.0f;
        float surR = 0.0f;
        if (decodeChannels == 5) {
            surL = src[3] * sCoeff;
            surR = src[4] * sCoeff;
        }
        const float norm = 1.0f / std::max(1.0f, 1.0f + (decodeChannels > 2 ? cCoeff : 0.0f) + (decodeChannels == 5 ? sCoeff : 0.0f));
        outStereo[f * 2] = (fl + fc + surL) * norm;
        outStereo[f * 2 + 1] = (fr + fc + surR) * norm;
    }
}

void SpatializerEngine::processOrDownmix(
        float* buffer,
        int frames,
        int decodeChannels,
        BedLayoutId bedLayout,
        int outChannels,
        int sampleRate) {
    if (!buffer || frames <= 0 || decodeChannels <= 0 || outChannels <= 0) {
        return;
    }

    const bool wantSpatial = currentConfig.enabled &&
                             currentConfig.backend != SpatializerBackendId::None &&
                             activeBackend != nullptr &&
                             outChannels == 2 &&
                             (decodeChannels > 2 || (decodeChannels == 2 && currentConfig.spatializeStereo));

    if (wantSpatial) {
        if (sampleRate != preparedSampleRate ||
            decodeChannels != preparedInputChannels ||
            bedLayout != preparedLayout ||
            frames > preparedMaxFrames) {
            const int targetMaxFrames = std::max({ frames, preparedMaxFrames, 4096 });
            if (activeBackend->prepare(sampleRate, bedLayout, decodeChannels, targetMaxFrames, currentConfig)) {
                preparedSampleRate = sampleRate;
                preparedInputChannels = decodeChannels;
                preparedLayout = bedLayout;
                preparedMaxFrames = targetMaxFrames;
                refreshAtomicFlagsLocked();
            }
        }

        if (preparedSampleRate == sampleRate && preparedInputChannels == decodeChannels) {
            const float wet = std::clamp(currentConfig.wetMix, 0.0f, 1.0f);
            const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
            const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
            const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);
            const float lfeGain = dbToLinearClamped(currentConfig.lfeGainDb, -48.0f, 12.0f);

            if (wet < 0.999f) {
                dryStereoScratch.resize(static_cast<size_t>(frames) * 2);
                downmixBedToStereo(
                        buffer,
                        decodeChannels,
                        bedLayout,
                        dryStereoScratch.data(),
                        frames,
                        centerGain,
                        surroundGain,
                        heightGain,
                        lfeGain
                );
            }

            // Extract LFE channel (ch=3 in 5.1/7.1/7.1.4) before backend processing.
            const bool hasLfeChannel =
                    (bedLayout == BedLayoutId::Surround_5_1 ||
                     bedLayout == BedLayoutId::Surround_7_1 ||
                     bedLayout == BedLayoutId::Atmos_7_1_4) &&
                    decodeChannels >= 4 &&
                    currentConfig.lfeGainDb > -47.5f;
            if (hasLfeChannel) {
                updateLfeLowPassCoeffs(sampleRate);
                lfeMonoScratch.resize(static_cast<size_t>(frames));
                const float scaledLfeGain = lfeGain * kInvSqrt2;
                for (int f = 0; f < frames; ++f) {
                    float lfeSample = buffer[static_cast<size_t>(f) * decodeChannels + 3];
                    buffer[static_cast<size_t>(f) * decodeChannels + 3] = 0.0f;
                    if (currentConfig.lfeLowPassEnabled) {
                        lfeSample = lfeBiquad2.processSample(lfeBiquad1.processSample(lfeSample));
                    }
                    lfeMonoScratch[static_cast<size_t>(f)] = lfeSample * scaledLfeGain;
                }
            }

            applyBedChannelTrimsInPlace(buffer, frames, decodeChannels, bedLayout);

            wetStereoScratch.resize(static_cast<size_t>(frames) * 2);
            activeBackend->process(buffer, wetStereoScratch.data(), frames);

            for (int f = 0; f < frames; ++f) {
                const float lfeAdd = hasLfeChannel ? lfeMonoScratch[static_cast<size_t>(f)] : 0.0f;
                float outL = wetStereoScratch[f * 2] + lfeAdd;
                float outR = wetStereoScratch[f * 2 + 1] + lfeAdd;
                if (wet < 0.999f) {
                    const float dryWeight = 1.0f - wet;
                    outL = dryWeight * dryStereoScratch[f * 2] + wet * outL;
                    outR = dryWeight * dryStereoScratch[f * 2 + 1] + wet * outR;
                }
                buffer[f * 2] = outL;
                buffer[f * 2 + 1] = outR;
            }
            return;
        }
    }

    if (decodeChannels == outChannels) {
        return;
    }

    if (outChannels == 2) {
        const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
        const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
        const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);
        const float lfeGain = dbToLinearClamped(currentConfig.lfeGainDb, -48.0f, 12.0f);
        downmixBedToStereo(
                buffer,
                decodeChannels,
                bedLayout,
                buffer,
                frames,
                centerGain,
                surroundGain,
                heightGain,
                lfeGain
        );
        return;
    }

    if (decodeChannels > outChannels) {
        for (int frame = 0; frame < frames; ++frame) {
            const float* srcFrame = buffer + static_cast<size_t>(frame) * decodeChannels;
            float* dstFrame = buffer + static_cast<size_t>(frame) * outChannels;
            for (int outCh = 0; outCh < outChannels; ++outCh) {
                dstFrame[outCh] = srcFrame[outCh];
            }
        }
    } else {
        for (int frame = frames - 1; frame >= 0; --frame) {
            const float* srcFrame = buffer + static_cast<size_t>(frame) * decodeChannels;
            float* dstFrame = buffer + static_cast<size_t>(frame) * outChannels;
            for (int outCh = 0; outCh < outChannels; ++outCh) {
                dstFrame[outCh] = outCh < decodeChannels ? srcFrame[outCh] : 0.0f;
            }
        }
    }
}

} // namespace siliconplayer::spatial
