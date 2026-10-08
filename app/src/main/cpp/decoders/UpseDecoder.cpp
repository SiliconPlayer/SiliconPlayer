#include "UpseDecoder.h"

#include <android/log.h>
#include <algorithm>
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

UpseDecoder::UpseDecoder() = default;

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
    }
}

int UpseDecoder::getOptionApplyPolicy(const char* name) const {
    if (name && equalsIgnoreCaseAscii(name, "libupse.reverb")) {
        return OPTION_APPLY_LIVE;
    }
    return OPTION_APPLY_LIVE;
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
