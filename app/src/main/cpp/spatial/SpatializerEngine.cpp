#include "SpatializerEngine.h"

#include "LibSpatialAudioBackend.h"

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
    switch (backendId) {
        case SpatializerBackendId::LibSpatialAudio:
            return std::make_unique<LibSpatialAudioBackend>();
        case SpatializerBackendId::None:
        case SpatializerBackendId::SteamAudio:
            break;
    }
    return nullptr;
}

} // namespace

void SpatializerEngine::setConfig(const SpatializerConfig& config) {
    const bool backendChanged = (config.backend != currentConfig.backend);
    const bool enabledChanged = (config.enabled != currentConfig.enabled);
    currentConfig = config;
    if (backendChanged || enabledChanged || currentConfig.backend == SpatializerBackendId::None) {
        activeBackend = createBackendInstance(currentConfig.backend);
        preparedSampleRate = 0;
        preparedInputChannels = 0;
        backendReady = false;
        resetFifos();
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
    const double lat = active
            ? static_cast<double>(kBlockSize) +
              static_cast<double>(std::max(0, activeBackend->latencyFrames()))
            : 0.0;
    cachedLatencyFrames.store(lat, std::memory_order_relaxed);
}

void SpatializerEngine::reset() {
    resetFifos();
    lfeBiquad1.reset();
    lfeBiquad2.reset();
    if (activeBackend) {
        activeBackend->reset();
    }
}

void SpatializerEngine::resetFifos() {
    bedFifoFrames = 0;
    bedFifoStart = 0;
    outFifoFrames = 0;
    outFifoRead = 0;
    // Prime the bed with one silent block so the first real block already has
    // a full convolution window; this keeps the output FIFO gap-free.
    if (bedChannels > 0 && bedFifoCapacity >= static_cast<size_t>(kBlockSize)) {
        std::memset(bedFifo.data(), 0, static_cast<size_t>(kBlockSize) * bedChannels * sizeof(float));
        bedFifoFrames = static_cast<size_t>(kBlockSize);
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

void SpatializerEngine::applyBedChannelTrims(
        float* bedBlock,
        int decodeChannels,
        BedLayoutId bedLayout) {
    if (!bedBlock || decodeChannels <= 2) {
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

    for (int f = 0; f < kBlockSize; ++f) {
        float* frame = bedBlock + static_cast<size_t>(f) * decodeChannels;
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
            outStereo[f * 2] = (src[0] + sCoeff * src[2]) * norm;
            outStereo[f * 2 + 1] = (src[1] + sCoeff * src[3]) * norm;
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
            const float fc = src[2] * cCoeff;
            const float lfe = src[3] * lfeCoeff;
            float left = src[0] + fc + lfe + src[4] * sCoeff;
            float right = src[1] + fc + lfe + src[5] * sCoeff;
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
        const float surL = decodeChannels == 5 ? src[3] * sCoeff : 0.0f;
        const float surR = decodeChannels == 5 ? src[4] * sCoeff : 0.0f;
        const float norm = 1.0f / std::max(
                1.0f,
                1.0f + (decodeChannels > 2 ? cCoeff : 0.0f) + (decodeChannels == 5 ? sCoeff : 0.0f)
        );
        outStereo[f * 2] = (fl + fc + surL) * norm;
        outStereo[f * 2 + 1] = (fr + fc + surR) * norm;
    }
}

void SpatializerEngine::ensureBedCapacity(size_t frames) {
    const size_t channels = static_cast<size_t>(std::max(bedChannels, 1));
    const size_t needed = (bedFifoStart + frames + static_cast<size_t>(kBlockSize)) * channels;
    if (bedFifoCapacity >= needed) {
        return;
    }
    size_t capacity = std::max<size_t>(bedFifoCapacity, 1);
    while (capacity < needed) {
        capacity *= 2;
    }
    std::vector<float> grown(capacity * channels, 0.0f);
    if (bedFifoCapacity > 0 && bedFifoFrames > 0) {
        std::memcpy(
                grown.data(),
                bedFifo.data() + bedFifoStart * channels,
                bedFifoFrames * channels * sizeof(float)
        );
    }
    bedFifo.swap(grown);
    bedFifoCapacity = capacity;
    bedFifoStart = 0;
}

void SpatializerEngine::processBlocks(int sampleRate) {
    const float wet = std::clamp(currentConfig.wetMix, 0.0f, 1.0f);
    const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
    const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
    const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);
    const float lfeGain = dbToLinearClamped(currentConfig.lfeGainDb, -48.0f, 12.0f);
    const bool hasLfeChannel =
            (preparedLayout == BedLayoutId::Surround_5_1 ||
            preparedLayout == BedLayoutId::Surround_7_1 ||
            preparedLayout == BedLayoutId::Atmos_7_1_4) &&
            bedChannels >= 4 &&
            currentConfig.lfeGainDb > -47.5f;

    while (bedFifoFrames >= static_cast<size_t>(kBlockSize)) {
        float* block = bedFifo.data() + bedFifoStart * bedChannels;

        if (wet < 0.999f) {
            downmixBedToStereo(
                    block,
                    bedChannels,
                    preparedLayout,
                    dryBlock.data(),
                    kBlockSize,
                    centerGain,
                    surroundGain,
                    heightGain,
                    lfeGain
            );
        }

        if (hasLfeChannel) {
            updateLfeLowPassCoeffs(sampleRate);
            lfeMonoScratch.resize(static_cast<size_t>(kBlockSize));
            const float scaledLfeGain = lfeGain * kInvSqrt2;
            for (int f = 0; f < kBlockSize; ++f) {
                float lfeSample = block[static_cast<size_t>(f) * bedChannels + 3];
                block[static_cast<size_t>(f) * bedChannels + 3] = 0.0f;
                if (currentConfig.lfeLowPassEnabled) {
                    lfeSample = lfeBiquad2.processSample(lfeBiquad1.processSample(lfeSample));
                }
                lfeMonoScratch[static_cast<size_t>(f)] = lfeSample * scaledLfeGain;
            }
        }

        applyBedChannelTrims(block, bedChannels, preparedLayout);

        activeBackend->process(block, wetBlock.data(), kBlockSize);

        for (int f = 0; f < kBlockSize; ++f) {
            const float lfeAdd = hasLfeChannel ? lfeMonoScratch[static_cast<size_t>(f)] : 0.0f;
            float outL = wetBlock[static_cast<size_t>(f) * 2] + lfeAdd;
            float outR = wetBlock[static_cast<size_t>(f) * 2 + 1] + lfeAdd;
            if (wet < 0.999f) {
                const float dryWeight = 1.0f - wet;
                outL = dryWeight * dryBlock[static_cast<size_t>(f) * 2] + wet * outL;
                outR = dryWeight * dryBlock[static_cast<size_t>(f) * 2 + 1] + wet * outR;
            }
            outFifo.push_back(outL);
            outFifo.push_back(outR);
        }
        outFifoFrames += static_cast<size_t>(kBlockSize);

        bedFifoStart += static_cast<size_t>(kBlockSize);
        bedFifoFrames -= static_cast<size_t>(kBlockSize);
    }

    if (bedFifoStart > 0 && (bedFifoStart + bedFifoFrames) * static_cast<size_t>(bedChannels) > bedFifoCapacity / 2) {
        const size_t channels = static_cast<size_t>(std::max(bedChannels, 1));
        std::memmove(
                bedFifo.data(),
                bedFifo.data() + bedFifoStart * channels,
                bedFifoFrames * channels * sizeof(float)
        );
        bedFifoStart = 0;
    }
}

int SpatializerEngine::drainOutput(float* outStereoInterleaved, int maxFrames) {
    if (!outStereoInterleaved || maxFrames <= 0) {
        return 0;
    }
    int written = 0;
    while (written < maxFrames && outFifoFrames > 0) {
        const float* src = outFifo.data() + outFifoRead * 2;
        outStereoInterleaved[static_cast<size_t>(written) * 2] = src[0];
        outStereoInterleaved[static_cast<size_t>(written) * 2 + 1] = src[1];
        ++outFifoRead;
        --outFifoFrames;
        ++written;
    }
    if (outFifoRead > 0 && (outFifoFrames == 0 || outFifoRead * 2 > outFifo.size() / 2)) {
        outFifo.erase(outFifo.begin(), outFifo.begin() + static_cast<long>(outFifoRead * 2));
        outFifoRead = 0;
    }
    return written;
}

void SpatializerEngine::processOrDownmix(
        const float* bedInterleaved,
        float* outStereoInterleaved,
        int frames,
        int decodeChannels,
        BedLayoutId bedLayout,
        int outChannels,
        int sampleRate) {
    if (!outStereoInterleaved || frames <= 0 || decodeChannels <= 0 || outChannels <= 0) {
        return;
    }

    const bool wantSpatial = currentConfig.enabled &&
                             currentConfig.backend != SpatializerBackendId::None &&
                             activeBackend != nullptr &&
                             outChannels == 2 &&
                             (decodeChannels > 2 || (decodeChannels == 2 && currentConfig.spatializeStereo));

    if (!wantSpatial) {
        if (decodeChannels == outChannels) {
            if (bedInterleaved != outStereoInterleaved) {
                std::memcpy(
                        outStereoInterleaved,
                        bedInterleaved,
                        static_cast<size_t>(frames) * outChannels * sizeof(float)
                );
            }
            return;
        }
        const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
        const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
        const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);
        const float lfeGain = dbToLinearClamped(currentConfig.lfeGainDb, -48.0f, 12.0f);
        if (outChannels == 2) {
            downmixBedToStereo(
                    bedInterleaved,
                    decodeChannels,
                    bedLayout,
                    outStereoInterleaved,
                    frames,
                    centerGain,
                    surroundGain,
                    heightGain,
                    lfeGain
            );
            return;
        }
        for (int f = 0; f < frames; ++f) {
            const float* src = bedInterleaved + static_cast<size_t>(f) * decodeChannels;
            float* dst = outStereoInterleaved + static_cast<size_t>(f) * outChannels;
            for (int ch = 0; ch < outChannels; ++ch) {
                dst[ch] = ch < decodeChannels ? src[ch] : 0.0f;
            }
        }
        return;
    }

    const bool layoutChanged =
            !backendReady ||
            sampleRate != preparedSampleRate ||
            decodeChannels != preparedInputChannels ||
            bedLayout != preparedLayout;
    if (layoutChanged) {
        bedChannels = decodeChannels;
        preparedSampleRate = sampleRate;
        preparedInputChannels = decodeChannels;
        preparedLayout = bedLayout;
        bedBlock.assign(static_cast<size_t>(kBlockSize) * bedChannels, 0.0f);
        dryBlock.assign(static_cast<size_t>(kBlockSize) * 2, 0.0f);
        wetBlock.assign(static_cast<size_t>(kBlockSize) * 2, 0.0f);
        bedFifo.clear();
        bedFifoCapacity = 0;
        bedFifoStart = 0;
        bedFifoFrames = 0;
        ensureBedCapacity(static_cast<size_t>(kBlockSize));
        backendReady = activeBackend->prepare(
                sampleRate,
                bedLayout,
                decodeChannels,
                kBlockSize,
                currentConfig
        );
        resetFifos();
        refreshAtomicFlagsLocked();
        if (!backendReady) {
            // The backend cannot render this layout; fall back to a plain downmix.
            const float centerGain = dbToLinearClamped(currentConfig.centerGainDb, -24.0f, 12.0f);
            const float surroundGain = dbToLinearClamped(currentConfig.surroundGainDb, -24.0f, 12.0f);
            const float heightGain = dbToLinearClamped(currentConfig.heightGainDb, -24.0f, 12.0f);
            const float lfeGain = dbToLinearClamped(currentConfig.lfeGainDb, -48.0f, 12.0f);
            downmixBedToStereo(
                    bedInterleaved,
                    decodeChannels,
                    bedLayout,
                    outStereoInterleaved,
                    frames,
                    centerGain,
                    surroundGain,
                    heightGain,
                    lfeGain
            );
            return;
        }
    }

    ensureBedCapacity(static_cast<size_t>(frames));

    const size_t channels = static_cast<size_t>(std::max(bedChannels, 1));
    float* bedDst = bedFifo.data() + (bedFifoStart + bedFifoFrames) * channels;
    for (int f = 0; f < frames; ++f) {
        std::memcpy(
                bedDst + static_cast<size_t>(f) * channels,
                bedInterleaved + static_cast<size_t>(f) * decodeChannels,
                static_cast<size_t>(decodeChannels) * sizeof(float)
        );
    }
    bedFifoFrames += static_cast<size_t>(frames);

    processBlocks(sampleRate);

    const int written = drainOutput(outStereoInterleaved, frames);
    if (written < frames) {
        std::memset(
                outStereoInterleaved + static_cast<size_t>(written) * 2,
                0,
                static_cast<size_t>(frames - written) * 2 * sizeof(float)
        );
    }
}

int SpatializerEngine::flush(float* outStereoInterleaved, int maxFrames) {
    if (!outStereoInterleaved || maxFrames <= 0) {
        return 0;
    }
    if (bedFifoFrames > 0 && activeBackend && currentConfig.enabled) {
        // Complete the pending block with silence so the convolution tail of
        // the last real samples is rendered instead of being dropped.
        const size_t channels = static_cast<size_t>(std::max(bedChannels, 1));
        float* block = bedFifo.data() + (bedFifoStart + bedFifoFrames) * channels;
        std::memset(block, 0, (static_cast<size_t>(kBlockSize) - bedFifoFrames) * channels * sizeof(float));
        bedFifoFrames = static_cast<size_t>(kBlockSize);
        processBlocks(preparedSampleRate);
    }
    return drainOutput(outStereoInterleaved, maxFrames);
}

} // namespace siliconplayer::spatial
