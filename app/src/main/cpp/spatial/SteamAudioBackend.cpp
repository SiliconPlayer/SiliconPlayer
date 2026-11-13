#include "SteamAudioBackend.h"

#include "SteamAudioContext.h"
#include "SteamAudioSpatialKernel.h"

#include <pffft.h>

#include <algorithm>
#include <cmath>
#include <cstring>

namespace siliconplayer::spatial {

class SteamAudioHybridConvolver {
public:
    ~SteamAudioHybridConvolver() {
        release();
    }

    bool prepare(int channels, int frames) {
        release();
        if (channels < 2 || frames != kSpatialDirectTaps) {
            return false;
        }
        bedChannels = channels;
        blockFrames = frames;
        fft = pffft_new_setup(kFftSize, PFFFT_REAL);
        storage = static_cast<float*>(pffft_aligned_malloc(kStorageFloats * sizeof(float)));
        if (!fft || !storage) {
            release();
            return false;
        }
        std::fill(storage, storage + kStorageFloats, 0.0f);

        size_t offset = 0;
        auto take = [&](size_t count) {
            float* result = storage + offset;
            offset += count;
            return result;
        };
        timeBlocks = take(kSignalCount * kFftSize);
        inputSpectra = take(kDirectSignalCount * kFftSize);
        directFilters = take(kDirectSignalCount * kFftSize);
        roomFilters = take(2 * kRoomPartitionCount * kFftSize);
        roomHistory = take(kRoomPartitionCount * kFftSize);
        outputSpectra = take(2 * kFftSize);
        outputTime = take(2 * kFftSize);
        overlap = take(2 * kSpatialDirectTaps);
        fftWork = take(kFftSize);

        const float* directTaps[kDirectSignalCount] = {
            kSpatialFrontMid,
            kSpatialFrontSide,
            kSpatialSurroundMid,
            kSpatialSurroundSide,
        };
        for (int signal = 0; signal < kDirectSignalCount; ++signal) {
            std::copy(directTaps[signal], directTaps[signal] + kSpatialDirectTaps, timeBlocks);
            pffft_transform(
                    fft,
                    timeBlocks,
                    directFilters + signal * kFftSize,
                    fftWork,
                    PFFFT_FORWARD);
            std::fill(timeBlocks, timeBlocks + kFftSize, 0.0f);
        }
        for (int partition = 0; partition < kRoomPartitionCount; ++partition) {
            for (int i = 0; i < kSpatialDirectTaps; ++i) {
                const int tap = partition * kSpatialDirectTaps + i - kSpatialRoomDelay;
                if (tap >= 0 && tap < kSpatialRoomTaps) {
                    timeBlocks[i] = kSpatialRoomMid[tap];
                    timeBlocks[kFftSize + i] = kSpatialRoomSide[tap];
                }
            }
            pffft_transform(
                    fft,
                    timeBlocks,
                    roomFilters + (partition * 2) * kFftSize,
                    fftWork,
                    PFFFT_FORWARD);
            pffft_transform(
                    fft,
                    timeBlocks + kFftSize,
                    roomFilters + (partition * 2 + 1) * kFftSize,
                    fftWork,
                    PFFFT_FORWARD);
            std::fill(timeBlocks, timeBlocks + 2 * kFftSize, 0.0f);
        }
        return true;
    }

    void reset() {
        if (!storage) {
            return;
        }
        std::fill(roomHistory, roomHistory + kRoomPartitionCount * kFftSize, 0.0f);
        std::fill(overlap, overlap + 2 * kSpatialDirectTaps, 0.0f);
        roomSlot = 0;
    }

    void process(
            const float* input,
            float* output,
            int frames,
            float roomScale,
            float widthScale) {
        std::fill(timeBlocks, timeBlocks + kSignalCount * kFftSize, 0.0f);
        for (int f = 0; f < frames; ++f) {
            const float* src = input + static_cast<size_t>(f) * bedChannels;
            float fl = src[0];
            float fr = src[1];
            float fc = 0.0f;
            float lfe = 0.0f;
            float sl = 0.0f;
            float sr = 0.0f;
            if (bedChannels == 4) {
                sl = src[2];
                sr = src[3];
            } else if (bedChannels >= 6) {
                fc = src[2];
                lfe = src[3];
                sl = src[4];
                sr = src[5];
                if (bedChannels >= 8) {
                    sl += 0.85f * src[6];
                    sr += 0.85f * src[7];
                }
                if (bedChannels >= 12) {
                    fl += 0.60f * src[8];
                    fr += 0.60f * src[9];
                    sl += 0.35f * src[8] + 0.70f * src[10];
                    sr += 0.35f * src[9] + 0.70f * src[11];
                }
            } else if (bedChannels == 3) {
                fc = src[2];
            }

            const float frontMid = 0.5f * (fl + fr) + 0.4202006f * fc + 0.40f * lfe;
            const float frontSide = 0.5f * (fl - fr);
            const float surrMid = 0.5f * (sl + sr);
            const float surrSide = 0.5f * (sl - sr);
            timeBlocks[f] = frontMid;
            timeBlocks[kFftSize + f] = frontSide;
            timeBlocks[2 * kFftSize + f] = surrMid;
            timeBlocks[3 * kFftSize + f] = surrSide;
            timeBlocks[4 * kFftSize + f] =
                    frontMid + 2.4f * surrMid + 0.30f * frontSide + 0.55f * surrSide;
        }
        for (int signal = 0; signal < kDirectSignalCount; ++signal) {
            pffft_transform(
                    fft,
                    timeBlocks + signal * kFftSize,
                    inputSpectra + signal * kFftSize,
                    fftWork,
                    PFFFT_FORWARD);
        }
        float* currentRoom = roomHistory + roomSlot * kFftSize;
        pffft_transform(fft, timeBlocks + 4 * kFftSize, currentRoom, fftWork, PFFFT_FORWARD);

        std::fill(outputSpectra, outputSpectra + 2 * kFftSize, 0.0f);
        constexpr float scale = 1.0f / static_cast<float>(kFftSize);
        pffft_zconvolve_accumulate(fft, inputSpectra, directFilters, outputSpectra, scale);
        pffft_zconvolve_accumulate(
                fft,
                inputSpectra + 2 * kFftSize,
                directFilters + 2 * kFftSize,
                outputSpectra,
                scale);
        pffft_zconvolve_accumulate(
                fft,
                inputSpectra + kFftSize,
                directFilters + kFftSize,
                outputSpectra + kFftSize,
                scale);
        pffft_zconvolve_accumulate(
                fft,
                inputSpectra + 3 * kFftSize,
                directFilters + 3 * kFftSize,
                outputSpectra + kFftSize,
                scale);
        for (int partition = 0; partition < kRoomPartitionCount; ++partition) {
            int historySlot = roomSlot - partition;
            if (historySlot < 0) {
                historySlot += kRoomPartitionCount;
            }
            const float* history = roomHistory + historySlot * kFftSize;
            const float* filters = roomFilters + (partition * 2) * kFftSize;
            pffft_zconvolve_accumulate(fft, history, filters, outputSpectra, roomScale * scale);
            pffft_zconvolve_accumulate(
                    fft,
                    history,
                    filters + kFftSize,
                    outputSpectra + kFftSize,
                    roomScale * scale);
        }
        roomSlot = (roomSlot + 1) % kRoomPartitionCount;

        pffft_transform(fft, outputSpectra, outputTime, fftWork, PFFFT_BACKWARD);
        pffft_transform(
                fft,
                outputSpectra + kFftSize,
                outputTime + kFftSize,
                fftWork,
                PFFFT_BACKWARD);
        for (int f = 0; f < frames; ++f) {
            const float mid = outputTime[f] + overlap[f];
            const float side = (outputTime[kFftSize + f] + overlap[kSpatialDirectTaps + f]) * widthScale;
            output[static_cast<size_t>(f) * 2] = mid + side;
            output[static_cast<size_t>(f) * 2 + 1] = mid - side;
            overlap[f] = outputTime[kSpatialDirectTaps + f];
            overlap[kSpatialDirectTaps + f] = outputTime[kFftSize + kSpatialDirectTaps + f];
        }
    }

private:
    static constexpr int kDirectSignalCount = 4;
    static constexpr int kSignalCount = 5;
    static constexpr int kFftSize = kSpatialDirectTaps * 2;
    static constexpr int kRoomPartitionCount =
            (kSpatialRoomDelay + kSpatialRoomTaps + kSpatialDirectTaps - 1) / kSpatialDirectTaps;
    static constexpr int kStorageFloats =
            (kSignalCount + kDirectSignalCount * 2 + kRoomPartitionCount * 3 + 4 + 1) * kFftSize +
             2 * kSpatialDirectTaps;

    void release() {
        if (fft) {
            pffft_destroy_setup(fft);
            fft = nullptr;
        }
        if (storage) {
            pffft_aligned_free(storage);
            storage = nullptr;
        }
    }

    PFFFT_Setup* fft = nullptr;
    float* storage = nullptr;
    float* timeBlocks = nullptr;
    float* inputSpectra = nullptr;
    float* directFilters = nullptr;
    float* roomFilters = nullptr;
    float* roomHistory = nullptr;
    float* outputSpectra = nullptr;
    float* outputTime = nullptr;
    float* overlap = nullptr;
    float* fftWork = nullptr;
    int bedChannels = 0;
    int blockFrames = 0;
    int roomSlot = 0;
};

namespace {

// RMS-normalized HRTFs land within ~1 dB of unity, so no makeup is applied.
constexpr float kHrtfGainCompensation = 1.0f;

// Steam Audio uses a right-handed frame where -Z is front, +X is right and
// +Y is up; the bed layout stores azimuth (left positive) and elevation.
IPLVector3 directionForSpeaker(const SpeakerPolarPosition& speaker) {
    if (speaker.isLfe) {
        // Steam Audio skips speakers placed at the origin.
        IPLVector3 lfe{};
        return lfe;
    }
    const double azimuth = static_cast<double>(speaker.azimuthDeg) * M_PI / 180.0;
    const double elevation = static_cast<double>(speaker.elevationDeg) * M_PI / 180.0;
    const double cosElevation = std::cos(elevation);
    IPLVector3 direction{};
    direction.x = static_cast<IPLfloat32>(-std::sin(azimuth) * cosElevation);
    direction.y = static_cast<IPLfloat32>(std::sin(elevation));
    direction.z = static_cast<IPLfloat32>(-std::cos(azimuth) * cosElevation);
    return direction;
}

} // namespace

SteamAudioBackend::SteamAudioBackend() = default;

SteamAudioBackend::~SteamAudioBackend() {
    releaseEffect();
    releaseReflections();
}

void SteamAudioBackend::releaseEffect() {
    if (effect) {
        iplVirtualSurroundEffectRelease(&effect);
        effect = nullptr;
    }
}

void SteamAudioBackend::releaseReflections() {
    if (reflectionLeft) {
        iplReflectionEffectRelease(&reflectionLeft);
        reflectionLeft = nullptr;
    }
    if (reflectionRight) {
        iplReflectionEffectRelease(&reflectionRight);
        reflectionRight = nullptr;
    }
}

void SteamAudioBackend::buildSpeakerLayout(
        BedLayoutId inputLayout,
        int inputChannels,
        bool wideSurround) {
    speakerDirections.clear();
    int speakerCount = 0;
    const SpeakerPolarPosition* positions = speakersForBedLayout(inputLayout, &speakerCount);
    const int count = std::min(inputChannels, speakerCount);
    for (int ch = 0; ch < count; ++ch) {
        SpeakerPolarPosition position = positions[ch];
        if (wideSurround && inputLayout == BedLayoutId::Surround_5_1 && ch >= 4) {
            // Move the side pair toward the lateral HRTF maximum without
            // pushing it fully onto the interaural axis.
            position.azimuthDeg = ch == 4 ? 100.0f : -100.0f;
        }
        speakerDirections.push_back(directionForSpeaker(position));
    }

    speakerLayout = IPLSpeakerLayout{};
    speakerLayout.type = IPL_SPEAKERLAYOUTTYPE_CUSTOM;
    speakerLayout.numSpeakers = static_cast<IPLint32>(speakerDirections.size());
    speakerLayout.speakers = speakerDirections.empty() ? nullptr : speakerDirections.data();
}

bool SteamAudioBackend::prepare(
        int sampleRate,
        BedLayoutId inputLayout,
        int inputChannels,
        int maxBlockFrames,
        const SpatializerConfig& config) {
    if (sampleRate <= 0 || inputChannels <= 0 || maxBlockFrames <= 0) {
        return false;
    }

    blockSize = maxBlockFrames;
    bedChannels = inputChannels;
    activeConfig = config;
    sofaPath = config.customSofaPath;

    buildSpeakerLayout(inputLayout, inputChannels, config.wideSurround);
    if (speakerDirections.empty()) {
        return false;
    }

    context = SteamAudioContext::context();
    if (!context) {
        return false;
    }

    hrtf = SteamAudioContext::hrtf(sampleRate, blockSize, sofaPath);
    if (!hrtf) {
        return false;
    }

    IPLAudioSettings audioSettings{};
    audioSettings.samplingRate = sampleRate;
    audioSettings.frameSize = blockSize;

    IPLVirtualSurroundEffectSettings effectSettings{};
    effectSettings.speakerLayout = speakerLayout;
    effectSettings.hrtf = hrtf;

    releaseEffect();
    releaseReflections();
    if (iplVirtualSurroundEffectCreate(
                context,
                &audioSettings,
                &effectSettings,
                &effect) != IPL_STATUS_SUCCESS) {
        effect = nullptr;
        return false;
    }

    if (config.frontAmbience) {
        IPLReflectionEffectSettings reflectionSettings{};
        reflectionSettings.type = IPL_REFLECTIONEFFECTTYPE_PARAMETRIC;
        reflectionSettings.irSize = 0;
        reflectionSettings.numChannels = 1;
        if (iplReflectionEffectCreate(
                    context,
                    &audioSettings,
                    &reflectionSettings,
                    &reflectionLeft) != IPL_STATUS_SUCCESS ||
            iplReflectionEffectCreate(
                    context,
                    &audioSettings,
                    &reflectionSettings,
                    &reflectionRight) != IPL_STATUS_SUCCESS) {
            releaseReflections();
            releaseEffect();
            return false;
        }
    }

    channelScratch.assign(static_cast<size_t>(bedChannels) * blockSize, 0.0f);
    channelPtrs.assign(static_cast<size_t>(bedChannels), nullptr);
    stereoScratch.assign(static_cast<size_t>(blockSize) * 2, 0.0f);
    stereoPtrs.assign(2, nullptr);
    stereoPtrs[0] = stereoScratch.data();
    stereoPtrs[1] = stereoScratch.data() + blockSize;
    reflectionScratch.assign(static_cast<size_t>(blockSize), 0.0f);
    reflectionPtrs.assign(1, reflectionScratch.data());
    earlyReflectionLeft.assign(
            static_cast<size_t>(blockSize) + static_cast<size_t>(std::ceil(sampleRate * 0.009)), 0.0f);
    earlyReflectionRight.assign(
            static_cast<size_t>(blockSize) + static_cast<size_t>(std::ceil(sampleRate * 0.013)), 0.0f);
    earlyReflectionCross.assign(
            static_cast<size_t>(blockSize) + static_cast<size_t>(std::ceil(sampleRate * 0.017)), 0.0f);
    hybridConvolver.reset();
    if (config.frontDiffuse && config.customSofaPath.empty() && bedChannels >= 2) {
        auto convolver = std::make_unique<SteamAudioHybridConvolver>();
        if (convolver->prepare(bedChannels, blockSize)) {
            hybridConvolver = std::move(convolver);
        }
    }
    reflectionLeftScratch.assign(static_cast<size_t>(blockSize), 0.0f);
    reflectionRightScratch.assign(static_cast<size_t>(blockSize), 0.0f);

    latencyFramesValue = 0;
    return true;
}

float SteamAudioBackend::outputGainCompensation() const {
    return kHrtfGainCompensation;
}

void SteamAudioBackend::reset() {
    if (effect) {
        iplVirtualSurroundEffectReset(effect);
    }
    if (reflectionLeft) {
        iplReflectionEffectReset(reflectionLeft);
    }
    if (reflectionRight) {
        iplReflectionEffectReset(reflectionRight);
    }
    std::fill(earlyReflectionLeft.begin(), earlyReflectionLeft.end(), 0.0f);
    std::fill(earlyReflectionRight.begin(), earlyReflectionRight.end(), 0.0f);
    std::fill(earlyReflectionCross.begin(), earlyReflectionCross.end(), 0.0f);
    if (hybridConvolver) {
        hybridConvolver->reset();
    }
}

void SteamAudioBackend::updateConfig(const SpatializerConfig& config) {
    activeConfig = config;
    sofaPath = config.customSofaPath;
}

void SteamAudioBackend::process(
        const float* inInterleaved,
        float* outStereoInterleaved,
        int frames) {
    if (!inInterleaved || !outStereoInterleaved || frames <= 0) {
        return;
    }
    if (!effect || frames != blockSize) {
        std::memset(outStereoInterleaved, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
        return;
    }

    if (hybridConvolver && activeConfig.frontDiffuse &&
        activeConfig.customSofaPath.empty() && bedChannels >= 2) {
        const float widthScale = std::clamp(activeConfig.virtualizerWidth / 1.5f, 0.25f, 2.0f);
        const float roomScale = activeConfig.frontAmbience ? 1.0f : 0.35f;
        hybridConvolver->process(inInterleaved, outStereoInterleaved, frames, roomScale, widthScale);
        return;
    }

    for (int ch = 0; ch < bedChannels; ++ch) {
        const float* src = inInterleaved + ch;
        float* dst = channelScratch.data() + static_cast<size_t>(ch) * blockSize;
        for (int f = 0; f < blockSize; ++f) {
            dst[f] = src[static_cast<size_t>(f) * bedChannels];
        }
        channelPtrs[static_cast<size_t>(ch)] = dst;
    }
    if (reflectionLeft && reflectionRight && bedChannels >= 3) {
        for (int f = 0; f < blockSize; ++f) {
            float* left = channelScratch.data() + f;
            float* right = channelScratch.data() + static_cast<size_t>(blockSize) + f;
            float* center = channelScratch.data() + static_cast<size_t>(blockSize) * 2 + f;
            const float centerSample = *center;
            const float send = 0.15f * activeConfig.virtualizerWidth;
            *left += centerSample * send;
            *right += centerSample * send;
            const float centerPower = 0.69f * 0.69f + 2.0f * 0.15f * 0.15f;
            const float dryCenter = std::sqrt(std::max(0.0f, centerPower - 2.0f * send * send));
            *center = centerSample * dryCenter;
        }
    }

    IPLAudioBuffer inputBuffer{};
    inputBuffer.numChannels = static_cast<IPLint32>(bedChannels);
    inputBuffer.numSamples = static_cast<IPLint32>(frames);
    inputBuffer.data = channelPtrs.data();

    IPLAudioBuffer outputBuffer{};
    outputBuffer.numChannels = 2;
    outputBuffer.numSamples = static_cast<IPLint32>(frames);
    outputBuffer.data = stereoPtrs.data();

    IPLVirtualSurroundEffectParams params{};
    params.hrtf = hrtf;

    // The returned state only reports whether a convolution tail remains.
    iplVirtualSurroundEffectApply(effect, &params, &inputBuffer, &outputBuffer);

    for (int f = 0; f < frames; ++f) {
        outStereoInterleaved[static_cast<size_t>(f) * 2] = stereoScratch[static_cast<size_t>(f)];
        outStereoInterleaved[static_cast<size_t>(f) * 2 + 1] =
                stereoScratch[static_cast<size_t>(f) + blockSize];
    }

    if (!reflectionLeft || !reflectionRight || bedChannels < 6) {
        return;
    }

    for (int f = 0; f < frames; ++f) {
        const float center = inInterleaved[static_cast<size_t>(f) * bedChannels + 2];
        earlyReflectionLeft[static_cast<size_t>(f)] = center;
        earlyReflectionRight[static_cast<size_t>(f)] = center;
        earlyReflectionCross[static_cast<size_t>(f)] = center;
    }
    std::rotate(
            earlyReflectionLeft.begin(),
            earlyReflectionLeft.end() - frames,
            earlyReflectionLeft.end());
    std::rotate(
            earlyReflectionRight.begin(),
            earlyReflectionRight.end() - frames,
            earlyReflectionRight.end());
    std::rotate(
            earlyReflectionCross.begin(),
            earlyReflectionCross.end() - frames,
            earlyReflectionCross.end());
    const int leftDelay = static_cast<int>(earlyReflectionLeft.size()) - frames;
    const int rightDelay = static_cast<int>(earlyReflectionRight.size()) - frames;
    const int crossDelay = static_cast<int>(earlyReflectionCross.size()) - frames;
    for (int f = 0; f < frames; ++f) {
        const float send = (earlyReflectionLeft[leftDelay + f] * 0.025f +
                            earlyReflectionRight[rightDelay + f] * 0.025f +
                            earlyReflectionCross[crossDelay + f] * 0.020f);
        reflectionScratch[static_cast<size_t>(f)] = send;
    }

    IPLAudioBuffer reflectionInput{};
    reflectionInput.numChannels = 1;
    reflectionInput.numSamples = static_cast<IPLint32>(frames);
    reflectionInput.data = reflectionPtrs.data();
    IPLAudioBuffer reflectionOutput{};
    reflectionOutput.numChannels = 1;
    reflectionOutput.numSamples = static_cast<IPLint32>(frames);
    float* leftPtr = reflectionLeftScratch.data();
    reflectionOutput.data = &leftPtr;
    IPLReflectionEffectParams reflectionParams{};
    reflectionParams.type = IPL_REFLECTIONEFFECTTYPE_PARAMETRIC;
    reflectionParams.reverbTimes[0] = 0.28f;
    reflectionParams.reverbTimes[1] = 0.24f;
    reflectionParams.reverbTimes[2] = 0.16f;
    reflectionParams.numChannels = 1;
    reflectionParams.irSize = 0;
    iplReflectionEffectApply(
            reflectionLeft, &reflectionParams, &reflectionInput, &reflectionOutput, nullptr);
    float* rightPtr = reflectionRightScratch.data();
    reflectionOutput.data = &rightPtr;
    reflectionParams.reverbTimes[0] = 0.31f;
    reflectionParams.reverbTimes[1] = 0.27f;
    reflectionParams.reverbTimes[2] = 0.18f;
    iplReflectionEffectApply(
            reflectionRight, &reflectionParams, &reflectionInput, &reflectionOutput, nullptr);
    for (int f = 0; f < frames; ++f) {
        outStereoInterleaved[static_cast<size_t>(f) * 2] += reflectionLeftScratch[static_cast<size_t>(f)];
        outStereoInterleaved[static_cast<size_t>(f) * 2 + 1] += reflectionRightScratch[static_cast<size_t>(f)];
    }
}

} // namespace siliconplayer::spatial
