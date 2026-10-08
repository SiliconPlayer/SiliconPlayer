#include "UpseDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>
#include <mutex>

extern "C" {
#include <upse/upse.h>
}

#define LOG_TAG "UpseDecoder"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kNativeSampleRate = 44100;
constexpr int kNativeChannels = 2;
constexpr double kFallbackDurationSeconds = 180.0;
constexpr int kMaxRenderCallsPerRead = 64;
constexpr int kSeekDrainChunkFrames = 1024;

thread_local const std::string* tlsBaseDir = nullptr;

std::string normalizeUpsePath(const char* path) {
    std::string normalized(path ? path : "");
    std::replace(normalized.begin(), normalized.end(), '\\', '/');
    constexpr const char* kFileScheme = "file://";
    if (normalized.rfind(kFileScheme, 0) == 0) {
        normalized.erase(0, std::strlen(kFileScheme));
    }
    return normalized;
}

bool isAbsoluteUpsePath(const std::string& path) {
    return !path.empty() && (path[0] == '/' || (path.size() > 2 && path[1] == ':'));
}

void* upseFopen(const char* path, const char* mode) {
    (void)mode;
    if (!path || path[0] == '\0') return nullptr;
    const std::string candidate = normalizeUpsePath(path);
    if (FILE* handle = std::fopen(candidate.c_str(), "rb")) {
        return handle;
    }
    if (tlsBaseDir != nullptr && !tlsBaseDir->empty() && !isAbsoluteUpsePath(candidate)) {
        const std::filesystem::path baseDir(*tlsBaseDir);
        const std::filesystem::path alongside = baseDir / candidate;
        if (std::filesystem::exists(alongside)) {
            if (FILE* handle = std::fopen(alongside.string().c_str(), "rb")) {
                return handle;
            }
        }
        const std::filesystem::path filenameOnly =
                baseDir / std::filesystem::path(candidate).filename();
        if (filenameOnly != alongside && std::filesystem::exists(filenameOnly)) {
            return std::fopen(filenameOnly.string().c_str(), "rb");
        }
    }
    return nullptr;
}

size_t upseFread(void* ptr, size_t size, size_t nmemb, void* file) {
    if (!ptr || !file) return 0;
    return std::fread(ptr, size, nmemb, static_cast<FILE*>(file));
}

int upseFseek(void* file, long offset, int whence) {
    if (!file) return -1;
    return std::fseek(static_cast<FILE*>(file), offset, whence);
}

int upseFclose(void* file) {
    if (!file) return -1;
    return std::fclose(static_cast<FILE*>(file));
}

long upseFtell(void* file) {
    if (!file) return -1L;
    return std::ftell(static_cast<FILE*>(file));
}

void ensureUpseInit() {
    static std::once_flag initFlag;
    std::call_once(initFlag, []() { upse_module_init(); });
}

std::string nonEmptyOr(std::string value, const std::string& fallback) {
    return value.empty() ? fallback : value;
}

bool equalsIgnoreCaseAscii(const char* a, const char* b) {
    if (!a || !b) return false;
    while (*a && *b) {
        char ca = *a++;
        char cb = *b++;
        if (ca >= 'A' && ca <= 'Z') ca = static_cast<char>(ca + ('a' - 'A'));
        if (cb >= 'A' && cb <= 'Z') cb = static_cast<char>(cb + ('a' - 'A'));
        if (ca != cb) return false;
    }
    return *a == *b;
}

constexpr int kUpseScopeTextStride = 10;
constexpr int kUpseScopeTextFlagActive = 1 << 0;
constexpr float kUpseScopeActivePeak = 0.0015f;
// SPU ensemble voices sit far below full scale; lift them into the scope
// rows the way the SID cores lift theirs. Sized so the loudest passages
// stay under the clamp below.
constexpr float kUpseScopeTapGain = 3.0f;

bool parseBoolOption(const char* value, bool fallback) {
    if (!value) return fallback;
    if (equalsIgnoreCaseAscii(value, "1") || equalsIgnoreCaseAscii(value, "true") ||
        equalsIgnoreCaseAscii(value, "yes") || equalsIgnoreCaseAscii(value, "on")) {
        return true;
    }
    if (equalsIgnoreCaseAscii(value, "0") || equalsIgnoreCaseAscii(value, "false") ||
        equalsIgnoreCaseAscii(value, "no") || equalsIgnoreCaseAscii(value, "off")) {
        return false;
    }
    return fallback;
}
}

UpseDecoder::UpseDecoder() : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

UpseDecoder::~UpseDecoder() {
    close();
}

bool UpseDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
    return openInternalLocked(path);
}

bool UpseDecoder::openInternalLocked(const char* path) {
    if (!path || path[0] == '\0') {
        return false;
    }
    ensureUpseInit();
    sourcePath = normalizeUpsePath(path);

    const upse_iofuncs_t iofuncs = {
            upseFopen,
            upseFread,
            upseFseek,
            upseFclose,
            upseFtell,
    };
    const std::string baseDir = std::filesystem::path(sourcePath).parent_path().string();
    tlsBaseDir = &baseDir;
    upse_module_t* opened = upse_module_open(sourcePath.c_str(), &iofuncs);
    tlsBaseDir = nullptr;
    if (!opened) {
        LOGE("upse_module_open failed: %s", sourcePath.c_str());
        return false;
    }
    module = opened;
    applyReverbLocked();
    applyScopeTapLocked();
    if (toggleChannelNames.empty()) {
        toggleChannelNames.reserve(kScopeVoices);
        toggleChannelMuted.assign(kScopeVoices, false);
        for (int voice = 0; voice < kScopeVoices; ++voice) {
            toggleChannelNames.push_back("Voice " + std::to_string(voice + 1));
        }
    }
    applyVoiceMutesLocked();
    resetChannelScopeLocked();

    if (module->metadata != nullptr) {
        const upse_psf_t* meta = module->metadata;
        if (meta->title) title = meta->title;
        if (meta->artist) artist = meta->artist;
        if (meta->game) gameName = meta->game;
        if (meta->year) year = meta->year;
        if (meta->genre) genre = meta->genre;
        if (meta->copyright) copyrightText = meta->copyright;
        if (meta->comment) comment = meta->comment;
        if (meta->xsf != nullptr) {
            if (meta->xsf->inf_length[0] != '\0') lengthTag = meta->xsf->inf_length;
            if (meta->xsf->inf_fade[0] != '\0') fadeTag = meta->xsf->inf_fade;
        }
        const uint32_t totalMs = meta->stop + meta->fade;
        if (meta->stop > 0 && totalMs > 0) {
            durationSeconds = static_cast<double>(totalMs) / 1000.0;
            durationReliable = true;
        }
    }
    if (artist.empty()) {
        artist = gameName;
    }
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }

    renderedFrames = 0;
    isOpen = true;
    return true;
}

void UpseDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void UpseDecoder::closeInternalLocked() {
    if (module) {
        upse_module_close(module);
        module = nullptr;
    }
    surplusSamples.clear();
    resetChannelScopeLocked();
    isOpen = false;
    repeatMode = 0;
    durationSeconds = kFallbackDurationSeconds;
    durationReliable = false;
    renderedFrames = 0;
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    gameName.clear();
    copyrightText.clear();
    year.clear();
    comment.clear();
    lengthTag.clear();
    fadeTag.clear();
}

int UpseDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !module || !buffer || numFrames <= 0) {
        return 0;
    }

    int framesToRender = numFrames;
    if (repeatMode != 2 && durationSeconds > 0.0) {
        const int64_t durationFrames = static_cast<int64_t>(std::llround(durationSeconds * sampleRate));
        const int64_t remaining = durationFrames - renderedFrames;
        if (remaining <= 0) {
            return 0;
        }
        framesToRender = static_cast<int>(std::min<int64_t>(framesToRender, remaining));
    }
    if (framesToRender <= 0) {
        return 0;
    }

    int framesOut = 0;
    auto emitS16 = [&](const int16_t* samples, int frames) {
        float* out = buffer + static_cast<size_t>(framesOut) * channels;
        for (int i = 0; i < frames * channels; ++i) {
            out[i] = static_cast<float>(samples[i]) / 32768.0f;
        }
        framesOut += frames;
    };
    if (!surplusSamples.empty()) {
        const int available = static_cast<int>(surplusSamples.size() / channels);
        const int take = std::min(available, framesToRender);
        emitS16(surplusSamples.data(), take);
        surplusSamples.erase(
                surplusSamples.begin(),
                surplusSamples.begin() + static_cast<size_t>(take) * channels);
    }
    for (int calls = 0; calls < kMaxRenderCallsPerRead && framesOut < framesToRender; ++calls) {
        int16_t* samples = nullptr;
        const int got = upse_eventloop_render(module, &samples);
        if (got <= 0 || !samples) {
            if (got < 0 || (got == 0 && samples != nullptr)) {
                break;
            }
            continue;
        }
        const int need = framesToRender - framesOut;
        const int take = std::min(got, need);
        emitS16(samples, take);
        if (got > take) {
            surplusSamples.assign(
                    samples + static_cast<size_t>(take) * channels,
                    samples + static_cast<size_t>(got) * channels);
        }
    }
    renderedFrames += framesOut;
    if (framesOut > 0 && scopeCaptureEnabled) {
        channelScopeLastReadNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        if (channelScopeState &&
            channelScopeState->tryBeginCapture(channelScopeLastReadNs, kScopeVoices)) {
            publishScopeSnapshotLocked();
        }
    }
    return framesOut;
}

void UpseDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen) {
        return;
    }
    double clamped = std::max(0.0, seconds);
    if (durationReliable && durationSeconds > 0.0) {
        clamped = std::min(clamped, durationSeconds);
    }
    const std::string path = sourcePath;
    closeInternalLocked();
    if (!openInternalLocked(path.c_str())) {
        return;
    }
    int64_t framesToSkip = static_cast<int64_t>(std::llround(clamped * sampleRate));
    if (framesToSkip <= 0) {
        return;
    }
    std::vector<int16_t> discard(static_cast<size_t>(kSeekDrainChunkFrames) * channels);
    while (framesToSkip > 0 && module) {
        const int chunk = static_cast<int>(std::min<int64_t>(framesToSkip, kSeekDrainChunkFrames));
        int16_t* samples = nullptr;
        const int got = upse_eventloop_render(module, &samples);
        if (got <= 0 || !samples) {
            break;
        }
        const int take = std::min<int64_t>(got, framesToSkip);
        framesToSkip -= take;
        renderedFrames += take;
    }
}

double UpseDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int UpseDecoder::getSampleRate() {
    return sampleRate;
}

int UpseDecoder::getBitDepth() {
    return bitDepth;
}

std::string UpseDecoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int UpseDecoder::getChannelCount() {
    return channels;
}

std::string UpseDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return nonEmptyOr(title, sourcePath);
}

std::string UpseDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string UpseDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string UpseDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string UpseDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string UpseDecoder::getYear() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return year;
}

std::string UpseDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

std::string UpseDecoder::getGameName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return gameName;
}

std::string UpseDecoder::getLengthTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return lengthTag;
}

std::string UpseDecoder::getFadeTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return fadeTag;
}

void UpseDecoder::setOutputSampleRate(int sampleRateHz) {
    (void)sampleRateHz;
}

void UpseDecoder::applyReverbLocked() {
    if (!module || !module->instance.spu) {
        return;
    }
    // Mirrors the head of libupse's upse_spu_state_t: the Neill core state
    // pointer is its first field. Keeps the glib min/max macros and the
    // C-only internal headers out of this translation unit.
    struct UpseSpuHead {
        void* pCore;
    };
    auto* spu = static_cast<UpseSpuHead*>(module->instance.spu);
    spu_enable_reverb(spu->pCore, reverbEnabled ? 1 : 0);
}

void UpseDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (equalsIgnoreCaseAscii(name, "libupse.reverb")) {
        reverbEnabled = parseBoolOption(value, reverbEnabled);
        applyReverbLocked();
        return;
    }
    if (equalsIgnoreCaseAscii(name, "visualization.channel_scope_active")) {
        const bool enabled = parseBoolOption(value, scopeCaptureEnabled);
        if (scopeCaptureEnabled == enabled) {
            return;
        }
        scopeCaptureEnabled = enabled;
        resetChannelScopeLocked();
    }
}

int UpseDecoder::getOptionApplyPolicy(const char* name) const {
    if (name && equalsIgnoreCaseAscii(name, "libupse.reverb")) {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
}

void UpseDecoder::applyScopeTapLocked() {
    if (!module) {
        return;
    }
    upse_ps1_spu_set_scope_callback(
            module->instance.spu, &UpseDecoder::scopeTapCallback, this);
}

void UpseDecoder::applyVoiceMutesLocked() {
    if (!module) {
        return;
    }
    upse_ps1_spu_clear_voice_mutes(module->instance.spu);
    for (int voice = 0; voice < kScopeVoices; ++voice) {
        if (voice < static_cast<int>(toggleChannelMuted.size()) && toggleChannelMuted[static_cast<size_t>(voice)]) {
            upse_ps1_spu_set_voice_mute(module->instance.spu, voice, 1);
        }
    }
}

void UpseDecoder::resetChannelScopeLocked() {
    scopeRingRaw.clear();
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    scopeTapChunkFrames = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void UpseDecoder::appendScopeTapLocked(int voice, const int16_t* samples, int frames) {
    if (!scopeCaptureEnabled || voice < 0 || voice >= kScopeVoices || !samples || frames <= 0) {
        return;
    }
    if (scopeRingRaw.empty()) {
        scopeRingRaw.assign(
                static_cast<size_t>(kScopeVoices) * ChannelScopeSharedState::kMaxSamples, 0.0f);
    }
    float* ring = scopeRingRaw.data() +
            static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
    const int firstBlock =
            std::min(frames, ChannelScopeSharedState::kMaxSamples - scopeRingWritePos);
    for (int i = 0; i < firstBlock; ++i) {
        ring[scopeRingWritePos + i] = std::clamp(
                static_cast<float>(samples[i]) / 32768.0f * kUpseScopeTapGain, -1.0f, 1.0f);
    }
    for (int i = firstBlock; i < frames; ++i) {
        ring[i - firstBlock] = std::clamp(
                static_cast<float>(samples[i]) / 32768.0f * kUpseScopeTapGain, -1.0f, 1.0f);
    }
    // The SPU reports every voice once per render chunk, in order, so the
    // shared write position advances when the last voice lands. Voices stay
    // mutually locked without any padding against the mix clock.
    if (voice == 0) {
        scopeTapChunkFrames = frames;
    }
    if (voice == kScopeVoices - 1 && scopeTapChunkFrames > 0) {
        scopeRingWritePos =
                (scopeRingWritePos + scopeTapChunkFrames) % ChannelScopeSharedState::kMaxSamples;
        scopeRingSamples =
                std::min(scopeRingSamples + scopeTapChunkFrames, ChannelScopeSharedState::kMaxSamples);
        scopeTapChunkFrames = 0;
    }
}

void UpseDecoder::publishScopeSnapshotLocked() {
    if (!channelScopeState || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        return;
    }

    scopePublishRaw.assign(
            static_cast<size_t>(kScopeVoices) * ChannelScopeSharedState::kMaxSamples, 0.0f);
    scopePublishVu.assign(static_cast<size_t>(kScopeVoices), 0.0f);
    std::vector<float>& raw = scopePublishRaw;
    std::vector<float>& vu = scopePublishVu;
    const int filledSamples = std::clamp(scopeRingSamples, 0, ChannelScopeSharedState::kMaxSamples);
    const int zeroPrefix = ChannelScopeSharedState::kMaxSamples - filledSamples;
    const int historyStart =
            (scopeRingWritePos - filledSamples + ChannelScopeSharedState::kMaxSamples) %
            ChannelScopeSharedState::kMaxSamples;
    const int firstBlock =
            std::min(filledSamples, ChannelScopeSharedState::kMaxSamples - historyStart);
    const int trailingSamples = 1024;
    for (int voice = 0; voice < kScopeVoices; ++voice) {
        float* dst = raw.data() + static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
        const float* src = scopeRingRaw.data() +
                static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
        std::copy_n(src + historyStart, firstBlock, dst + zeroPrefix);
        std::copy_n(src, filledSamples - firstBlock, dst + zeroPrefix + firstBlock);

        float peak = 0.0f;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        for (int i = start; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            peak = std::max(peak, std::abs(dst[i]));
        }
        vu[static_cast<size_t>(voice)] = std::clamp(peak, 0.0f, 1.0f);
    }
    channelScopeState->publish(raw, vu, kScopeVoices, ++channelScopeSourceSerial, true);
}

void UpseDecoder::scopeTapCallback(int voice, const short* samples, int frames, void* user) {
    auto* self = static_cast<UpseDecoder*>(user);
    if (!self) {
        return;
    }
    self->appendScopeTapLocked(
            voice, reinterpret_cast<const int16_t*>(samples), frames);
}

std::vector<std::string> UpseDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

void UpseDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= kScopeVoices ||
        channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyVoiceMutesLocked();
    resetChannelScopeLocked();
}

bool UpseDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void UpseDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyVoiceMutesLocked();
    resetChannelScopeLocked();
}

std::vector<int32_t> UpseDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (scopeRingRaw.empty() || scopeRingSamples <= 0) {
        return {};
    }

    const int channelsToExport = std::min(kScopeVoices, std::clamp(maxChannels, 1, kScopeVoices));
    std::vector<int32_t> flat(static_cast<size_t>(channelsToExport * kUpseScopeTextStride), -1);
    const int trailingSamples = 1024;
    const int recentSamples = std::min(scopeRingSamples, trailingSamples);
    const int recentStart =
            (scopeRingWritePos - recentSamples + ChannelScopeSharedState::kMaxSamples) %
            ChannelScopeSharedState::kMaxSamples;
    const int recentFirstBlock =
            std::min(recentSamples, ChannelScopeSharedState::kMaxSamples - recentStart);
    for (int voice = 0; voice < channelsToExport; ++voice) {
        float recentPeak = 0.0f;
        const float* src = scopeRingRaw.data() +
                static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
        for (int i = 0; i < recentFirstBlock; ++i) {
            recentPeak = std::max(recentPeak, std::abs(src[recentStart + i]));
        }
        for (int i = recentFirstBlock; i < recentSamples; ++i) {
            recentPeak = std::max(recentPeak, std::abs(src[i - recentFirstBlock]));
        }

        const size_t base = static_cast<size_t>(voice * kUpseScopeTextStride);
        int flags = 0;
        if (recentPeak > kUpseScopeActivePeak) {
            flags |= kUpseScopeTextFlagActive;
        }

        flat[base + 0] = voice;
        flat[base + 1] = -1;
        flat[base + 2] = std::clamp(static_cast<int>(std::lround(recentPeak * 64.0f)), 0, 64);
        flat[base + 3] = 0;
        flat[base + 4] = -1;
        flat[base + 5] = 0;
        flat[base + 6] = -1;
        flat[base + 7] = -1;
        flat[base + 8] = -1;
        flat[base + 9] = flags;
    }
    return flat;
}

void UpseDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = (mode >= 0 && mode <= 3) ? mode : 0;
}

int UpseDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int UpseDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int UpseDecoder::getFixedSampleRateHz() const {
    return sampleRate;
}

double UpseDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate <= 0) return 0.0;
    return static_cast<double>(renderedFrames) / sampleRate;
}

std::string UpseDecoder::getCoreStringInfo(const char* name) {
    if (name == nullptr) return "";
    if (std::strcmp(name, "gameName") == 0) return getGameName();
    if (std::strcmp(name, "copyright") == 0) return getCopyright();
    if (std::strcmp(name, "year") == 0) return getYear();
    if (std::strcmp(name, "comment") == 0) return getComment();
    if (std::strcmp(name, "lengthTag") == 0) return getLengthTag();
    if (std::strcmp(name, "fadeTag") == 0) return getFadeTag();
    return "";
}

std::vector<std::string> UpseDecoder::getSupportedExtensions() {
    return {"psf", "minipsf"};
}
