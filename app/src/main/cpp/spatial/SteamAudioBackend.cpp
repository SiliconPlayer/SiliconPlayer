#include "SteamAudioBackend.h"

#include "SteamAudioContext.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace siliconplayer::spatial {

namespace {

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
}

void SteamAudioBackend::releaseEffect() {
    if (effect) {
        iplVirtualSurroundEffectRelease(&effect);
        effect = nullptr;
    }
}

void SteamAudioBackend::buildSpeakerLayout(BedLayoutId inputLayout, int inputChannels) {
    speakerDirections.clear();
    int speakerCount = 0;
    const SpeakerPolarPosition* positions = speakersForBedLayout(inputLayout, &speakerCount);
    const int count = std::min(inputChannels, speakerCount);
    for (int ch = 0; ch < count; ++ch) {
        speakerDirections.push_back(directionForSpeaker(positions[ch]));
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
    sofaPath = config.customSofaPath;

    buildSpeakerLayout(inputLayout, inputChannels);
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
    if (iplVirtualSurroundEffectCreate(
                context,
                &audioSettings,
                &effectSettings,
                &effect) != IPL_STATUS_SUCCESS) {
        effect = nullptr;
        return false;
    }

    channelScratch.assign(static_cast<size_t>(bedChannels) * blockSize, 0.0f);
    channelPtrs.assign(static_cast<size_t>(bedChannels), nullptr);
    stereoScratch.assign(static_cast<size_t>(blockSize) * 2, 0.0f);
    stereoPtrs.assign(2, nullptr);
    stereoPtrs[0] = stereoScratch.data();
    stereoPtrs[1] = stereoScratch.data() + blockSize;

    latencyFramesValue = 0;
    return true;
}

void SteamAudioBackend::reset() {
    if (effect) {
        iplVirtualSurroundEffectReset(effect);
    }
}

void SteamAudioBackend::updateConfig(const SpatializerConfig& config) {
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

    for (int ch = 0; ch < bedChannels; ++ch) {
        const float* src = inInterleaved + ch;
        float* dst = channelScratch.data() + static_cast<size_t>(ch) * blockSize;
        for (int f = 0; f < blockSize; ++f) {
            dst[f] = src[static_cast<size_t>(f) * bedChannels];
        }
        channelPtrs[static_cast<size_t>(ch)] = dst;
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
}

} // namespace siliconplayer::spatial
