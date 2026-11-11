// Native regression test for the libspatialaudio spatializer backend.
#include "spatial/LibSpatialAudioBackend.h"
#include "spatial/SpatializerEngine.h"

#include <cmath>
#include <cstdio>
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

siliconplayer::spatial::SpatializerConfig makeConfig(int renderMethod) {
    siliconplayer::spatial::SpatializerConfig config;
    config.backend = siliconplayer::spatial::SpatializerBackendId::LibSpatialAudio;
    config.enabled = true;
    config.renderMethod = renderMethod;
    return config;
}

void testPanning(int renderMethod, const char* label) {
    siliconplayer::spatial::LibSpatialAudioBackend backend;
    if (!backend.prepare(
                kSampleRate,
                siliconplayer::spatial::BedLayoutId::Surround_5_1,
                kChannels,
                kBlock,
                makeConfig(renderMethod))) {
        printf("FAIL: %s backend prepare\n", label);
        ++g_failures;
        return;
    }

    for (int srcChannel = 0; srcChannel < 3; ++srcChannel) {
        std::vector<float> input(static_cast<size_t>(kBlock) * kChannels, 0.0f);
        std::vector<float> output(static_cast<size_t>(kBlock) * 2, 0.0f);
        for (int f = 0; f < kBlock; ++f) {
            input[static_cast<size_t>(f) * kChannels + srcChannel] =
                    std::sin(2.0 * M_PI * 1000.0 * f / kSampleRate);
        }
        backend.process(input.data(), output.data(), kBlock);

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
            check(left > right * 3.0, "front left pans to the left ear");
        } else if (srcChannel == 1) {
            check(right > left * 3.0, "front right pans to the right ear");
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

} // namespace

int main() {
    testPanning(0, "virtual-speakers");
    testPanning(1, "ambisonics");
    testEngineBlocks();
    if (g_failures == 0) {
        printf("ALL PASS\n");
        return 0;
    }
    printf("FAILURES: %d\n", g_failures);
    return 1;
}
