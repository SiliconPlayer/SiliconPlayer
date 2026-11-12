#include "SteamAudioBackend.h"

#include "SteamAudioContext.h"
#include "SteamAudioSpatialKernel.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace siliconplayer::spatial {

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
    mimoFrontMid.assign(static_cast<size_t>(kSpatialDirectTaps) * 2, 0.0f);
    mimoFrontSide.assign(static_cast<size_t>(kSpatialDirectTaps) * 2, 0.0f);
    mimoSurrMid.assign(static_cast<size_t>(kSpatialDirectTaps) * 2, 0.0f);
    mimoSurrSide.assign(static_cast<size_t>(kSpatialDirectTaps) * 2, 0.0f);
    mimoRoomExciter.assign(static_cast<size_t>(kSpatialRoomHistory) * 2, 0.0f);
    diffuseCursor = 0;
    roomCursor = 0;
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
    std::fill(mimoFrontMid.begin(), mimoFrontMid.end(), 0.0f);
    std::fill(mimoFrontSide.begin(), mimoFrontSide.end(), 0.0f);
    std::fill(mimoSurrMid.begin(), mimoSurrMid.end(), 0.0f);
    std::fill(mimoSurrSide.begin(), mimoSurrSide.end(), 0.0f);
    std::fill(mimoRoomExciter.begin(), mimoRoomExciter.end(), 0.0f);
    diffuseCursor = 0;
    roomCursor = 0;
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

    if (activeConfig.frontDiffuse && activeConfig.customSofaPath.empty() && bedChannels >= 2) {
        const float widthScale = std::clamp(activeConfig.virtualizerWidth / 1.5f, 0.25f, 2.0f);
        for (int f = 0; f < frames; ++f) {
            const float* src = inInterleaved + static_cast<size_t>(f) * bedChannels;
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
            const float roomExciter = frontMid + 2.4f * surrMid + 0.30f * frontSide + 0.55f * surrSide;

            diffuseCursor = (diffuseCursor - 1) & (kSpatialDirectTaps - 1);
            const size_t cur = static_cast<size_t>(diffuseCursor);
            const size_t mirror = cur + static_cast<size_t>(kSpatialDirectTaps);
            mimoFrontMid[cur] = frontMid;
            mimoFrontMid[mirror] = frontMid;
            mimoFrontSide[cur] = frontSide;
            mimoFrontSide[mirror] = frontSide;
            mimoSurrMid[cur] = surrMid;
            mimoSurrMid[mirror] = surrMid;
            mimoSurrSide[cur] = surrSide;
            mimoSurrSide[mirror] = surrSide;

            roomCursor = (roomCursor == 0) ? (kSpatialRoomHistory - 1) : (roomCursor - 1);
            const size_t rCur = static_cast<size_t>(roomCursor);
            mimoRoomExciter[rCur] = roomExciter;
            mimoRoomExciter[rCur + static_cast<size_t>(kSpatialRoomHistory)] = roomExciter;

            const float* __restrict fmHist = mimoFrontMid.data() + cur;
            const float* __restrict fsHist = mimoFrontSide.data() + cur;
            const float* __restrict smHist = mimoSurrMid.data() + cur;
            const float* __restrict ssHist = mimoSurrSide.data() + cur;
            float midOut = 0.0f;
            float sideOut = 0.0f;
            for (int k = 0; k < kSpatialDirectTaps; ++k) {
                midOut += fmHist[k] * kSpatialFrontMid[k] + smHist[k] * kSpatialSurroundMid[k];
                sideOut += fsHist[k] * kSpatialFrontSide[k] + ssHist[k] * kSpatialSurroundSide[k];
            }

            const float* __restrict rmHist = mimoRoomExciter.data() + rCur + static_cast<size_t>(kSpatialRoomDelay);
            float roomMid = 0.0f;
            float roomSide = 0.0f;
            for (int k = 0; k < kSpatialRoomTaps; ++k) {
                const float r = rmHist[k];
                roomMid += r * kSpatialRoomMid[k];
                roomSide += r * kSpatialRoomSide[k];
            }
            const float roomScale = activeConfig.frontAmbience ? 1.0f : 0.35f;
            midOut += roomMid * roomScale;
            sideOut = (sideOut + roomSide * roomScale) * widthScale;
            outStereoInterleaved[static_cast<size_t>(f) * 2] = midOut + sideOut;
            outStereoInterleaved[static_cast<size_t>(f) * 2 + 1] = midOut - sideOut;
        }
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
