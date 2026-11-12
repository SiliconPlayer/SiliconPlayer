// Native regression test for the spatializer backends.
#include "spatial/LibSpatialAudioBackend.h"
#include "spatial/SpatializerEngine.h"
#include "spatial/SteamAudioBackend.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <memory>
#include <vector>

namespace {

int g_failures = 0;

void check(bool ok, const char* what) {
    printf("%s: %s\n", ok ? "PASS" : "FAIL", what);
    if (!ok) {
        ++g_failures;
    }
}

constexpr int kSampleRate = 48000;
constexpr int kChannels = 6;
constexpr int kBlock = 256;

siliconplayer::spatial::SpatializerConfig makeConfig(
        int renderMethod,
        siliconplayer::spatial::SpatializerBackendId backend =
                siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio) {
    siliconplayer::spatial::SpatializerConfig config;
    config.backend = backend;
    config.enabled = true;
    config.renderMethod = renderMethod;
    return config;
}

void testPanning(
        int renderMethod,
        const char* label,
        siliconplayer::spatial::SpatializerBackendId backendId =
                siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio) {
    for (int srcChannel = 0; srcChannel < 3; ++srcChannel) {
        // A fresh instance per channel keeps the convolution tail of the
        // previous channel out of the measurement.
        std::unique_ptr<siliconplayer::spatial::ISpatializer> backend;
        if (backendId == siliconplayer::spatial::SpatializerBackendId::SteamAudio) {
            backend = std::make_unique<siliconplayer::spatial::SteamAudioBackend>();
        } else {
            backend = std::make_unique<siliconplayer::spatial::LibSpatialAudioBackend>();
        }
        if (!backend->prepare(
                    kSampleRate,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    kChannels,
                    kBlock,
                    makeConfig(renderMethod, backendId))) {
            printf("FAIL: %s backend prepare\n", label);
            ++g_failures;
            return;
        }

        std::vector<float> input(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> output(static_cast<size_t>(kBlock) * 2, 0.0f);
        for (int f = 0; f < kBlock; ++f) {
            input[static_cast<size_t>(f) * kChannels + srcChannel] =
                    std::sin(2.0 * M_PI * 1000.0 * f / kSampleRate);
        }
        backend->process(input.data(), output.data(), kBlock);

        double left = 0.0;
        double right = 0.0;
        bool finite = true;
        for (int f = 0; f < kBlock; ++f) {
            const float l = output[static_cast<size_t>(f) * 2];
            const float r = output[static_cast<size_t>(f) * 2 + 1];
            if (!std::isfinite(l) || !std::isfinite(r)) {
                finite = false;
            }
            left += static_cast<double>(l) * l;
            right += static_cast<double>(r) * r;
        }
        printf("  %s src=%d left=%.4f right=%.4f\n", label, srcChannel, left, right);
        check(finite, "output stays finite");
        if (srcChannel == 0) {
            check(left > right * 2.0, "front left pans to the left ear");
        } else if (srcChannel == 1) {
            check(right > left * 2.0, "front right pans to the right ear");
        } else {
            check(left > 0.0 && right > 0.0, "front center feeds both ears");
        }
    }
}

void testEngineBlocks() {
    siliconplayer::spatial::SpatializerEngine engine;
    engine.setConfig(makeConfig(0));
    check(engine.isActiveForMultichannel(), "engine reports the spatializer active");

    std::vector<float> bed(static_cast<size_t>(kBlock) * 4 * kChannels, 0.0f);
    std::vector<float> out(static_cast<size_t>(kBlock) * 4 * 2, 0.0f);
    for (int f = 0; f < static_cast<int>(bed.size()) / kChannels; ++f) {
        bed[static_cast<size_t>(f) * kChannels] =
                std::sin(2.0 * M_PI * 440.0 * f / kSampleRate);
        bed[static_cast<size_t>(f) * kChannels + 3] =
                std::sin(2.0 * M_PI * 60.0 * f / kSampleRate);
    }

    engine.processOrDownmix(
            bed.data(),
            out.data(),
            static_cast<int>(out.size()) / 2,
            kChannels,
            siliconplayer::spatial::BedLayoutId::Surround_5_1,
            2,
            kSampleRate
    );

    double energy = 0.0;
    bool finite = true;
    for (const float sample : out) {
        if (!std::isfinite(sample)) {
            finite = false;
        }
        energy += static_cast<double>(sample) * sample;
    }
    check(finite, "engine output stays finite");
    check(energy > 1.0, "engine output carries the bed signal");

    const int flushed = engine.flush(out.data(), static_cast<int>(out.size()) / 2);
    check(flushed > 0, "flush renders the pending convolution tail");
}

void testLevelAndBypass(
        siliconplayer::spatial::SpatializerBackendId backendId,
        const char* label) {
    siliconplayer::spatial::SpatializerEngine engine;
    engine.setConfig(makeConfig(0, backendId));

    std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
    std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);

    auto runBlocks = [&](int blocks) {
        double energy = 0.0;
        int measured = 0;
        for (int b = 0; b < blocks; ++b) {
            for (int f = 0; f < kBlock; ++f) {
                const int t = b * kBlock + f;
                // Front pair only: the HRTF path under test, no LFE.
                bed[static_cast<size_t>(f) * kChannels] =
                        std::sin(2.0 * M_PI * 1000.0 * t / kSampleRate);
                bed[static_cast<size_t>(f) * kChannels + 1] =
                        std::sin(2.0 * M_PI * 1000.0 * t / kSampleRate);
            }
            engine.processOrDownmix(
                    bed.data(),
                    out.data(),
                    kBlock,
                    kChannels,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    2,
                    kSampleRate
            );
            // Skip the convolution warm-up.
            if (b < 8) continue;
            for (const float sample : out) {
                energy += static_cast<double>(sample) * sample;
            }
            measured += kBlock * 2;
        }
        return std::sqrt(energy / std::max(1, measured));
    };

    const double outRms = runBlocks(32);
    // Two front speakers of a 1 kHz sine sum to ~1.0 RMS; the compensation
    // must keep the rendered level within a few dB of that.
    const double db = 20.0 * std::log10(outRms / 0.70710678);
    printf("  %s level: rms=%.4f (%+.2f dB)\n", label, outRms, db);
    check(outRms > 0.3 && outRms < 2.0, "spatializer keeps the level near unity");

    // Turning the stage off must restore the plain downmix immediately.
    siliconplayer::spatial::SpatializerConfig off = makeConfig(0, backendId);
    off.backend = siliconplayer::spatial::SpatializerBackendId::None;
    engine.setConfig(off);
    check(!engine.isActiveForMultichannel(), "off disables the spatializer stage");

    for (int f = 0; f < kBlock; ++f) {
        bed[static_cast<size_t>(f) * kChannels] = 0.5f;
        bed[static_cast<size_t>(f) * kChannels + 1] = 0.5f;
    }
    engine.processOrDownmix(
            bed.data(),
            out.data(),
            kBlock,
            kChannels,
            siliconplayer::spatial::BedLayoutId::Surround_5_1,
            2,
            kSampleRate
    );
    bool centered = true;
    for (int f = 0; f < kBlock; ++f) {
        if (std::abs(out[static_cast<size_t>(f) * 2] -
                     out[static_cast<size_t>(f) * 2 + 1]) > 1e-5f) {
            centered = false;
        }
    }
    check(centered, "off renders a symmetric plain downmix");
}

} // namespace

int main() {
    testPanning(0, "virtual-speakers");
    testPanning(1, "ambisonics");
    testPanning(0, "steam-audio", siliconplayer::spatial::SpatializerBackendId::SteamAudio);
    testEngineBlocks();
    testLevelAndBypass(
            siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio,
            "libspatialaudio");
    testLevelAndBypass(
            siliconplayer::spatial::SpatializerBackendId::SteamAudio,
            "steam-audio");
    if (g_failures == 0) {
        printf("ALL PASS\n");
        return 0;
    }
    printf("FAILURES: %d\n", g_failures);
    return 1;
}
