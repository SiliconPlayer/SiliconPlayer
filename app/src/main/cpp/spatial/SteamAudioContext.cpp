#include "SteamAudioContext.h"

#include <mutex>
#include <vector>

namespace siliconplayer::spatial {

namespace {

struct HrtfEntry {
    int sampleRate = 0;
    int frameSize = 0;
    std::string sofaPath;
    IPLHRTF hrtf = nullptr;
};

std::mutex g_mutex;
IPLContext g_context = nullptr;
std::vector<HrtfEntry> g_hrtfs;

IPLContext contextLocked() {
    if (g_context) {
        return g_context;
    }
    IPLContextSettings settings{};
    settings.version = STEAMAUDIO_VERSION;
    // The highest level is capped by the CPU's supported set at runtime.
    settings.simdLevel = IPL_SIMDLEVEL_AVX512;
    if (iplContextCreate(&settings, &g_context) != IPL_STATUS_SUCCESS) {
        g_context = nullptr;
    }
    return g_context;
}

} // namespace

IPLContext SteamAudioContext::context() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return contextLocked();
}

IPLHRTF SteamAudioContext::hrtf(int sampleRate, int frameSize, const std::string& sofaPath) {
    std::lock_guard<std::mutex> lock(g_mutex);
    for (const auto& entry : g_hrtfs) {
        if (entry.sampleRate == sampleRate &&
            entry.frameSize == frameSize &&
            entry.sofaPath == sofaPath) {
            return entry.hrtf;
        }
    }

    IPLContext ctx = contextLocked();
    if (!ctx) {
        return nullptr;
    }

    IPLAudioSettings audioSettings{};
    audioSettings.samplingRate = sampleRate;
    audioSettings.frameSize = frameSize;

    IPLHRTFSettings hrtfSettings{};
    hrtfSettings.type = sofaPath.empty() ? IPL_HRTFTYPE_DEFAULT : IPL_HRTFTYPE_SOFA;
    hrtfSettings.sofaFileName = sofaPath.empty() ? nullptr : sofaPath.c_str();
    hrtfSettings.volume = 1.0f;
    hrtfSettings.normType = IPL_HRTFNORMTYPE_RMS;

    IPLHRTF hrtf = nullptr;
    if (iplHRTFCreate(ctx, &audioSettings, &hrtfSettings, &hrtf) != IPL_STATUS_SUCCESS) {
        return nullptr;
    }

    g_hrtfs.push_back(HrtfEntry{ sampleRate, frameSize, sofaPath, hrtf });
    return hrtf;
}

void SteamAudioContext::release() {
    std::lock_guard<std::mutex> lock(g_mutex);
    for (auto& entry : g_hrtfs) {
        if (entry.hrtf) {
            iplHRTFRelease(&entry.hrtf);
        }
    }
    g_hrtfs.clear();
    if (g_context) {
        iplContextRelease(&g_context);
        g_context = nullptr;
    }
}

} // namespace siliconplayer::spatial
