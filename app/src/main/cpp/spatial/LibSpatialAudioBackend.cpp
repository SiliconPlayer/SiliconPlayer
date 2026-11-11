#include "LibSpatialAudioBackend.h"

#include <algorithm>
#include <cstring>
#include <utility>

#include <spatialaudio/AmbisonicBinauralizer.h>
#include <spatialaudio/AmbisonicCommons.h>
#include <spatialaudio/AmbisonicEncoder.h>
#include <spatialaudio/AmbisonicSpeaker.h>
#include <spatialaudio/BFormat.h>
#include <spatialaudio/SpeakersBinauralizer.h>

namespace siliconplayer::spatial {

namespace {

// The bundled MIT HRTF only carries filters for these rates.
constexpr int kMitHrtfRates[] = { 44100, 48000, 88200, 96000 };

bool isMitHrtfSampleRate(int sampleRate) {
    for (const int rate : kMitHrtfRates) {
        if (rate == sampleRate) {
            return true;
        }
    }
    return false;
}

} // namespace

LibSpatialAudioBackend::LibSpatialAudioBackend() = default;

LibSpatialAudioBackend::~LibSpatialAudioBackend() = default;

void LibSpatialAudioBackend::buildSpeakerPlan(BedLayoutId inputLayout, int inputChannels) {
    speakerPlan.clear();
    int speakerCount = 0;
    const SpeakerPolarPosition* positions = speakersForBedLayout(inputLayout, &speakerCount);
    const int count = std::min(inputChannels, speakerCount);
    for (int ch = 0; ch < count; ++ch) {
        if (positions[ch].isLfe) {
            continue; // The engine extracts and mixes the LFE channel on its own.
        }
        SpeakerPlan plan;
        plan.bedChannel = ch;
        plan.azimuthDeg = positions[ch].azimuthDeg;
        plan.elevationDeg = positions[ch].elevationDeg;
        speakerPlan.push_back(plan);
    }
}

bool LibSpatialAudioBackend::configureVirtualSpeakers(int sampleRate, const SpatializerConfig& config) {
    speakersBinauralizer = std::make_unique<spaudio::SpeakersBinauralizer>();
    virtualSpeakers.clear();
    virtualSpeakers.reserve(speakerPlan.size());

    std::vector<spaudio::AmbisonicSpeaker> speakerArray;
    speakerArray.reserve(speakerPlan.size());
    for (const auto& plan : speakerPlan) {
        auto speaker = std::make_unique<spaudio::AmbisonicSpeaker>();
        if (!speaker->Configure(1, true, 0)) {
            return false;
        }
        spaudio::PolarPosition<float> position;
        position.azimuth = spaudio::DegreesToRadians(plan.azimuthDeg);
        position.elevation = spaudio::DegreesToRadians(plan.elevationDeg);
        position.distance = 1.0f;
        speaker->SetPosition(position);
        speakerArray.push_back(*speaker);
        virtualSpeakers.push_back(std::move(speaker));
    }

    unsigned tailLength = 0;
    if (!speakersBinauralizer->Configure(
                static_cast<unsigned>(sampleRate),
                static_cast<unsigned>(blockSize),
                speakerArray.data(),
                static_cast<unsigned>(speakerArray.size()),
                tailLength,
                config.customSofaPath)) {
        speakersBinauralizer.reset();
        virtualSpeakers.clear();
        return false;
    }
    latencyFramesValue = 0;
    return true;
}

bool LibSpatialAudioBackend::configureAmbisonics(int sampleRate, const SpatializerConfig& config) {
    unsigned order = static_cast<unsigned>(std::clamp(config.ambisonicOrder, 1, 3));
    if (config.customSofaPath.empty()) {
        // The bundled MIT HRTF only supports first-order binauralization.
        order = 1;
    }

    ambisonicBinauralizer = std::make_unique<spaudio::AmbisonicBinauralizer>();
    unsigned tailLength = 0;
    if (!ambisonicBinauralizer->Configure(
                order,
                true,
                static_cast<unsigned>(sampleRate),
                static_cast<unsigned>(blockSize),
                tailLength,
                config.customSofaPath,
                false)) {
        ambisonicBinauralizer.reset();
        return false;
    }

    ambisonicFormat = std::make_unique<spaudio::BFormat>();
    if (!ambisonicFormat->Configure(order, true, static_cast<unsigned>(blockSize))) {
        ambisonicBinauralizer.reset();
        ambisonicFormat.reset();
        return false;
    }

    ambisonicEncoders.clear();
    ambisonicEncoders.reserve(speakerPlan.size());
    for (const auto& plan : speakerPlan) {
        auto encoder = std::make_unique<spaudio::AmbisonicEncoder>();
        if (!encoder->Configure(order, true, static_cast<unsigned>(sampleRate), 0.0f)) {
            ambisonicBinauralizer.reset();
            ambisonicFormat.reset();
            return false;
        }
        spaudio::PolarPosition<float> position;
        position.azimuth = spaudio::DegreesToRadians(plan.azimuthDeg);
        position.elevation = spaudio::DegreesToRadians(plan.elevationDeg);
        position.distance = 1.0f;
        encoder->SetPosition(position);
        ambisonicEncoders.push_back(std::move(encoder));
    }

    latencyFramesValue = 0;
    return true;
}

bool LibSpatialAudioBackend::prepare(
        int sampleRate,
        BedLayoutId inputLayout,
        int inputChannels,
        int maxBlockFrames,
        const SpatializerConfig& config) {
    if (sampleRate <= 0 || inputChannels <= 0 || maxBlockFrames <= 0) {
        return false;
    }
    if (config.customSofaPath.empty() && !isMitHrtfSampleRate(sampleRate)) {
        return false;
    }

    blockSize = maxBlockFrames;
    bedChannels = inputChannels;
    buildSpeakerPlan(inputLayout, inputChannels);
    if (speakerPlan.empty()) {
        return false;
    }

    channelScratch.assign(static_cast<size_t>(bedChannels) * blockSize, 0.0f);
    channelPtrs.assign(static_cast<size_t>(bedChannels), nullptr);
    stereoScratch.assign(static_cast<size_t>(blockSize) * 2, 0.0f);

    useAmbisonics = (config.renderMethod == 1);
    return useAmbisonics
            ? configureAmbisonics(sampleRate, config)
            : configureVirtualSpeakers(sampleRate, config);
}

void LibSpatialAudioBackend::reset() {
    if (speakersBinauralizer) {
        speakersBinauralizer->Reset();
    }
    if (ambisonicBinauralizer) {
        ambisonicBinauralizer->Reset();
    }
    if (ambisonicFormat) {
        ambisonicFormat->Reset();
    }
    for (auto& encoder : ambisonicEncoders) {
        encoder->Reset();
    }
}

void LibSpatialAudioBackend::updateConfig(const SpatializerConfig& config) {
    (void)config;
}

void LibSpatialAudioBackend::process(
        const float* inInterleaved,
        float* outStereoInterleaved,
        int frames) {
    if (!inInterleaved || !outStereoInterleaved || frames <= 0) {
        return;
    }
    if (frames != blockSize) {
        std::memset(outStereoInterleaved, 0, static_cast<size_t>(frames) * 2 * sizeof(float));
        return;
    }

    if (useAmbisonics) {
        for (int ch = 0; ch < bedChannels; ++ch) {
            const float* src = inInterleaved + ch;
            float* dst = channelScratch.data() + static_cast<size_t>(ch) * blockSize;
            for (int f = 0; f < blockSize; ++f) {
                dst[f] = src[static_cast<size_t>(f) * bedChannels];
            }
            channelPtrs[static_cast<size_t>(ch)] = dst;
        }
        ambisonicFormat->Reset();
        for (size_t i = 0; i < ambisonicEncoders.size(); ++i) {
            float* mono = channelPtrs[static_cast<size_t>(speakerPlan[i].bedChannel)];
            if (i == 0) {
                ambisonicEncoders[i]->Process(mono, static_cast<unsigned>(blockSize), ambisonicFormat.get());
            } else {
                ambisonicEncoders[i]->ProcessAccumul(
                        mono,
                        static_cast<unsigned>(blockSize),
                        ambisonicFormat.get()
                );
            }
        }
        float* stereoPtrs[2] = {
            stereoScratch.data(),
            stereoScratch.data() + blockSize
        };
        ambisonicBinauralizer->Process(ambisonicFormat.get(), stereoPtrs, static_cast<unsigned>(blockSize));
    } else {
        for (int ch = 0; ch < bedChannels; ++ch) {
            const float* src = inInterleaved + ch;
            float* dst = channelScratch.data() + static_cast<size_t>(ch) * blockSize;
            for (int f = 0; f < blockSize; ++f) {
                dst[f] = src[static_cast<size_t>(f) * bedChannels];
            }
            channelPtrs[static_cast<size_t>(ch)] = dst;
        }
        std::vector<float*> speakerPtrs;
        speakerPtrs.reserve(speakerPlan.size());
        for (const auto& plan : speakerPlan) {
            speakerPtrs.push_back(channelPtrs[static_cast<size_t>(plan.bedChannel)]);
        }
        float* stereoPtrs[2] = {
            stereoScratch.data(),
            stereoScratch.data() + blockSize
        };
        speakersBinauralizer->Process(speakerPtrs.data(), stereoPtrs);
    }

    for (int f = 0; f < blockSize; ++f) {
        outStereoInterleaved[static_cast<size_t>(f) * 2] = stereoScratch[static_cast<size_t>(f)];
        outStereoInterleaved[static_cast<size_t>(f) * 2 + 1] = stereoScratch[static_cast<size_t>(f) + blockSize];
    }
}

} // namespace siliconplayer::spatial
