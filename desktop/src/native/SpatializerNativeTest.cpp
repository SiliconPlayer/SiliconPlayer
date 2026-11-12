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
    // Isolate the HRTF makeup from the user-facing loudness boost.
    auto config = makeConfig(0, backendId);
    config.outputGainDb = 0.0f;
    config.frontAmbience = true;
    engine.setConfig(config);

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
    off.outputGainDb = 0.0f;
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

void testOutputGain(
        siliconplayer::spatial::SpatializerBackendId backendId,
        const char* label) {
    auto renderRms = [&](float gainDb) {
        siliconplayer::spatial::SpatializerEngine engine;
        auto config = makeConfig(0, backendId);
        config.outputGainDb = gainDb;
        engine.setConfig(config);

        std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
        double energy = 0.0;
        int measured = 0;
        for (int b = 0; b < 32; ++b) {
            for (int f = 0; f < kBlock; ++f) {
                const int t = b * kBlock + f;
                const float s = std::sin(2.0 * M_PI * 1000.0 * t / kSampleRate);
                bed[static_cast<size_t>(f) * kChannels] = s;
                bed[static_cast<size_t>(f) * kChannels + 1] = s;
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
            if (b < 8) continue;
            for (const float sample : out) {
                energy += static_cast<double>(sample) * sample;
            }
            measured += kBlock * 2;
        }
        return std::sqrt(energy / std::max(1, measured));
    };

    const double unity = renderRms(0.0f);
    const double boosted = renderRms(8.5f);
    const double ratio = boosted / std::max(1e-9, unity);
    const double expected = std::pow(10.0, 8.5 / 20.0);
    printf("  %s boost: 0 dB rms=%.4f, +8.5 dB rms=%.4f (x%.3f, expected x%.3f)\n",
           label, unity, boosted, ratio, expected);
    check(std::abs(ratio - expected) < 0.05, "loudness boost tracks the configured dB");
}

void testResetDropsBufferedOutput(
        siliconplayer::spatial::SpatializerBackendId backendId,
        const char* label) {
    siliconplayer::spatial::SpatializerEngine engine;
    auto config = makeConfig(0, backendId);
    config.outputGainDb = 0.0f;
    if (backendId == siliconplayer::spatial::SpatializerBackendId::SteamAudio) {
        config.frontAmbience = true;
        config.frontDiffuse = true;
        config.virtualizerWidth = 1.5f;
        config.compressorMode = 2;
    }
    engine.setConfig(config);

    std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
    std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
    for (int b = 0; b < 3; ++b) {
        for (int f = 0; f < kBlock; ++f) {
            const float sample = std::sin(2.0 * M_PI * 1000.0 * (b * kBlock + f) / kSampleRate);
            bed[static_cast<size_t>(f) * kChannels] = sample;
            bed[static_cast<size_t>(f) * kChannels + 1] = sample;
            bed[static_cast<size_t>(f) * kChannels + 4] = sample;
        }
        engine.processOrDownmix(
                bed.data(), out.data(), kBlock, kChannels,
                siliconplayer::spatial::BedLayoutId::Surround_5_1,
                2, kSampleRate);
    }

    engine.reset();
    std::fill(bed.begin(), bed.end(), 0.0f);
    std::fill(out.begin(), out.end(), 1.0f);
    engine.processOrDownmix(
            bed.data(), out.data(), kBlock, kChannels,
            siliconplayer::spatial::BedLayoutId::Surround_5_1,
            2, kSampleRate);

    double energy = 0.0;
    for (const float sample : out) {
        energy += static_cast<double>(sample) * sample;
    }
    printf("  %s post-reset energy=%.9f\n", label, energy);
    check(energy < 1e-10, "reset never replays buffered audio");
}

void testToneAndDynamics() {
    auto renderRms = [](float frequency, float bassDb, float trebleDb, int compressorMode, float amplitude = 0.1f) {
        siliconplayer::spatial::SpatializerEngine engine;
        auto config = makeConfig(0, siliconplayer::spatial::SpatializerBackendId::SteamAudio);
        config.outputGainDb = 0.0f;
        config.bassGainDb = bassDb;
        config.trebleGainDb = trebleDb;
        config.compressorMode = compressorMode;
        engine.setConfig(config);
        std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
        double energy = 0.0;
        for (int b = 0; b < 40; ++b) {
            for (int f = 0; f < kBlock; ++f) {
                const float sample = amplitude * std::sin(
                        2.0 * M_PI * frequency * (b * kBlock + f) / kSampleRate);
                bed[static_cast<size_t>(f) * kChannels + 2] = sample;
            }
            engine.processOrDownmix(
                    bed.data(), out.data(), kBlock, kChannels,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    2, kSampleRate);
            if (b >= 20) {
                for (const float sample : out) {
                    energy += static_cast<double>(sample) * sample;
                }
            }
        }
        return std::sqrt(energy / (20.0 * kBlock * 2));
    };

    const double bassReference = renderRms(80.0f, 0.0f, 0.0f, 0);
    const double bassBoosted = renderRms(80.0f, 1.5f, 0.0f, 0);
    const double bassDb = 20.0 * std::log10(bassBoosted / bassReference);
    printf("  bass shelf at 80 Hz: %+.2f dB\n", bassDb);
    check(std::abs(bassDb - 1.5) < 0.25, "soft-V bass shelf tracks its gain");

    const double trebleReference = renderRms(10000.0f, 0.0f, 0.0f, 0);
    const double trebleBoosted = renderRms(10000.0f, 0.0f, 1.5f, 0);
    const double trebleDb = 20.0 * std::log10(trebleBoosted / trebleReference);
    printf("  treble shelf at 10 kHz: %+.2f dB\n", trebleDb);
    check(std::abs(trebleDb - 1.5) < 0.25, "soft-V treble shelf tracks its gain");

    const double dynamicsOff = renderRms(1000.0f, 0.0f, 0.0f, 0);
    const double dynamicsOn = renderRms(1000.0f, 0.0f, 0.0f, 1);
    const double makeupDb = 20.0 * std::log10(dynamicsOn / dynamicsOff);
    printf("  gentle dynamics on a quiet tone: %+.2f dB\n", makeupDb);
    check(makeupDb > 1.5f && makeupDb < 2.5f, "gentle dynamics lifts quiet content");

    const double detailQuiet = renderRms(4000.0f, 0.0f, 0.0f, 2, 0.005f);
    const double directQuiet = renderRms(4000.0f, 0.0f, 0.0f, 0, 0.005f);
    const double detailLift = 20.0 * std::log10(detailQuiet / directQuiet);
    printf("  detail dynamics on a steady high-band tone: %+.2f dB\n", detailLift);
    check(std::abs(detailLift) < 1.0f, "detail dynamics does not over-lift steady content");

    auto renderPlucks = [](int compressorMode) {
        siliconplayer::spatial::SpatializerEngine engine;
        auto config = makeConfig(0, siliconplayer::spatial::SpatializerBackendId::SteamAudio);
        config.outputGainDb = 0.0f;
        config.compressorMode = compressorMode;
        engine.setConfig(config);
        std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
        double energy = 0.0;
        int measured = 0;
        for (int b = 0; b < 64; ++b) {
            std::fill(bed.begin(), bed.end(), 0.0f);
            for (int f = 0; f < kBlock; ++f) {
                const int t = b * kBlock + f;
                if (t % 4800 < 480) {
                    const float envelope = std::exp(-static_cast<float>(t % 4800) / 96.0f);
                    const float sample = 0.008f * envelope * std::sin(2.0 * M_PI * 4000.0 * t / kSampleRate);
                    bed[static_cast<size_t>(f) * kChannels + 4] = sample;
                    bed[static_cast<size_t>(f) * kChannels + 5] = sample;
                }
            }
            engine.processOrDownmix(
                    bed.data(), out.data(), kBlock, kChannels,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    2, kSampleRate);
            if (b >= 16) {
                for (const float sample : out) {
                    energy += static_cast<double>(sample) * sample;
                }
                measured += kBlock * 2;
            }
        }
        return std::sqrt(energy / std::max(1, measured));
    };
    const double detailPlucks = renderPlucks(2);
    const double directPlucks = renderPlucks(0);
    const double pluckLift = 20.0 * std::log10(detailPlucks / directPlucks);
    printf("  detail dynamics on quiet plucks: %+.2f dB\n", pluckLift);
    check(pluckLift > 0.5f && pluckLift < 4.0f, "detail dynamics lifts quiet plucks without constant maximum boost");
}

void testFrontAmbience() {
    auto measure = [](bool enabled, double& sideEnergy, double& tailEnergy) {
        siliconplayer::spatial::SpatializerEngine engine;
        auto config = makeConfig(0, siliconplayer::spatial::SpatializerBackendId::SteamAudio);
        config.outputGainDb = 0.0f;
        config.frontAmbience = enabled;
        engine.setConfig(config);
        std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
        for (int b = 0; b < 40; ++b) {
            for (int f = 0; f < kBlock; ++f) {
                const float sample = b < 24
                        ? 0.2f * std::sin(2.0 * M_PI * 1000.0 * (b * kBlock + f) / kSampleRate)
                        : 0.0f;
                bed[static_cast<size_t>(f) * kChannels + 2] = sample;
            }
            engine.processOrDownmix(
                    bed.data(), out.data(), kBlock, kChannels,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    2, kSampleRate);
            for (int f = 0; f < kBlock; ++f) {
                const float left = out[static_cast<size_t>(f) * 2];
                const float right = out[static_cast<size_t>(f) * 2 + 1];
                if (b >= 16 && b < 24) {
                    const float side = (left - right) * 0.5f;
                    sideEnergy += static_cast<double>(side) * side;
                } else if (b >= 32) {
                    tailEnergy += static_cast<double>(left) * left;
                    tailEnergy += static_cast<double>(right) * right;
                }
            }
        }
    };

    double directSide = 0.0;
    double directTail = 0.0;
    double ambientSide = 0.0;
    double ambientTail = 0.0;
    measure(false, directSide, directTail);
    measure(true, ambientSide, ambientTail);
    printf("  front ambience: side %.6f -> %.6f, tail %.6f -> %.6f\n",
           directSide, ambientSide, directTail, ambientTail);
    check(std::abs(ambientSide - directSide) > 1e-4,
          "front ambience changes center-channel spatialization");
    check(ambientTail > directTail * 2.0, "front ambience adds a decaying tail");
}

void testFrontDiffusion() {
    auto render = [](bool enabled) {
        siliconplayer::spatial::SpatializerEngine engine;
        auto config = makeConfig(0, siliconplayer::spatial::SpatializerBackendId::SteamAudio);
        config.outputGainDb = 0.0f;
        config.frontDiffuse = enabled;
        engine.setConfig(config);
        std::vector<float> bed(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> out(static_cast<size_t>(kBlock) * 2, 0.0f);
        std::vector<float> timeline;
        for (int b = 0; b < 8; ++b) {
            std::fill(bed.begin(), bed.end(), 0.0f);
            if (b == 0) {
                bed[0] = 0.5f;
                bed[1] = 0.5f;
            }
            engine.processOrDownmix(
                    bed.data(), out.data(), kBlock, kChannels,
                    siliconplayer::spatial::BedLayoutId::Surround_5_1,
                    2, kSampleRate);
            timeline.insert(timeline.end(), out.begin(), out.end());
        }
        return timeline;
    };
    const auto direct = render(false);
    const auto diffuse = render(true);
    double earlyDifference = 0.0;
    double lateEnergy = 0.0;
    for (int f = 0; f < static_cast<int>(direct.size()) / 2; ++f) {
        const double left = diffuse[static_cast<size_t>(f) * 2] - direct[static_cast<size_t>(f) * 2];
        const double right = diffuse[static_cast<size_t>(f) * 2 + 1] - direct[static_cast<size_t>(f) * 2 + 1];
        if (f < 2 * kBlock) {
            earlyDifference += left * left + right * right;
        }
        if (f >= 3 * kBlock) {
            lateEnergy += left * left + right * right;
        }
    }
    printf("  front diffusion: early difference=%.9f late energy=%.9f\n", earlyDifference, lateEnergy);
    check(earlyDifference > 1e-5, "front diffusion changes frontal impulse phase");
    check(lateEnergy > 1e-8, "front diffusion disperses frontal transients over time");
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
    testOutputGain(
            siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio,
            "libspatialaudio");
    testOutputGain(
            siliconplayer::spatial::SpatializerBackendId::SteamAudio,
            "steam-audio");
    testResetDropsBufferedOutput(
            siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio,
            "libspatialaudio");
    testResetDropsBufferedOutput(
            siliconplayer::spatial::SpatializerBackendId::SteamAudio,
            "steam-audio");
    testToneAndDynamics();
    testFrontAmbience();
    testFrontDiffusion();
    if (g_failures == 0) {
        printf("ALL PASS\n");
        return 0;
    }
    printf("FAILURES: %d\n", g_failures);
    return 1;
}
