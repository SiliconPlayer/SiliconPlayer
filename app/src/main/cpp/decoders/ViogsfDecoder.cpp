#include "ViogsfDecoder.h"

#define SP_LOG_MODULE "ViogsfDecoder"
#include "../SiliconLog.h"
#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <filesystem>

extern "C" {
#include <psflib.h>
#include <viogsf/gsf_loader.h>
}

namespace {
constexpr int kNativeSampleRate = 44100;
constexpr int kNativeChannels = 2;
constexpr int kSeekDrainChunkFrames = 1024;
constexpr double kFallbackDurationSeconds = 180.0;
constexpr unsigned long kInvalidPsfTime = 0xC0CAC01A;

struct PsfOpenContext {
    std::string sourcePath;
};

static void* stdioFopen(void* context, const char* path) {
    if (!path) return nullptr;
    std::string normalized(path);
    std::replace(normalized.begin(), normalized.end(), '\\', '/');
    constexpr const char* kFileScheme = "file://";
    if (normalized.rfind(kFileScheme, 0) == 0) {
        normalized.erase(0, std::strlen(kFileScheme));
    }

    std::string candidatePath = normalized;
    const auto* openContext = static_cast<const PsfOpenContext*>(context);
    if (openContext != nullptr && !openContext->sourcePath.empty() &&
        !std::filesystem::path(candidatePath).is_absolute()) {
        const std::filesystem::path baseDir =
                std::filesystem::path(openContext->sourcePath).parent_path();
        if (std::filesystem::exists(baseDir / candidatePath)) {
            candidatePath = (baseDir / candidatePath).string();
        } else if (std::filesystem::exists(baseDir / std::filesystem::path(candidatePath).filename())) {
            candidatePath = (baseDir / std::filesystem::path(candidatePath).filename()).string();
        }
    }
    return std::fopen(candidatePath.c_str(), "rb");
}

static size_t stdioFread(void* buffer, size_t size, size_t count, void* handle) {
    if (!buffer || !handle) return 0;
    return std::fread(buffer, size, count, static_cast<FILE*>(handle));
}

static int stdioFseek(void* handle, int64_t offset, int whence) {
    if (!handle) return -1;
    return std::fseek(static_cast<FILE*>(handle), static_cast<long>(offset), whence);
}

static int stdioFclose(void* handle) {
    if (!handle) return -1;
    return std::fclose(static_cast<FILE*>(handle));
}

static long stdioFtell(void* handle) {
    if (!handle) return -1;
    return std::ftell(static_cast<FILE*>(handle));
}

static bool equalsIgnoreCase(const char* lhs, const char* rhs) {
    if (!lhs || !rhs) return false;
    while (*lhs && *rhs) {
        const char a = static_cast<char>(std::tolower(static_cast<unsigned char>(*lhs)));
        const char b = static_cast<char>(std::tolower(static_cast<unsigned char>(*rhs)));
        if (a != b) return false;
        ++lhs;
        ++rhs;
    }
    return *lhs == '\0' && *rhs == '\0';
}

static unsigned long parsePsfTimeMs(const std::string& input, bool& ok) {
    ok = false;
    if (input.empty()) {
        return 0;
    }
    unsigned long value = 0;
    unsigned long multiplier = 1000;
    const char* ptr = input.c_str();
    unsigned long colonCount = 0;

    while (*ptr && ((*ptr >= '0' && *ptr <= '9') || *ptr == ':')) {
        colonCount += (*ptr == ':') ? 1u : 0u;
        ++ptr;
    }
    if (colonCount > 2) return 0;
    if (*ptr && *ptr != '.' && *ptr != ',') return 0;
    if (*ptr) ++ptr;
    while (*ptr && *ptr >= '0' && *ptr <= '9') ++ptr;
    if (*ptr) return 0;

    ptr = std::strrchr(input.c_str(), ':');
    if (!ptr) {
        ptr = input.c_str();
    }

    for (;;) {
        char* end = nullptr;
        if (ptr != input.c_str()) ++ptr;
        if (multiplier == 1000) {
            const double temp = std::strtod(ptr, &end);
            if (temp >= 60.0) return 0;
            value = static_cast<unsigned long>(temp * 1000.0);
        } else {
            const unsigned long temp = std::strtoul(ptr, &end, 10);
            if (temp >= 60 && multiplier < 3600000) return 0;
            value += temp * multiplier;
        }
        if (ptr == input.c_str()) break;
        ptr -= 2;
        while (ptr > input.c_str() && *ptr != ':') --ptr;
        multiplier *= 60;
    }

    if (value == kInvalidPsfTime) {
        return 0;
    }
    ok = true;
    return value;
}

static int gsfInfoMetadata(void* context, const char* name, const char* value) {
    auto* metadata = static_cast<ViogsfDecoder::MetadataState*>(context);
    if (!metadata || !name || !value) {
        return 0;
    }

    if (equalsIgnoreCase(name, "length")) {
        bool ok = false;
        const unsigned long parsed = parsePsfTimeMs(value, ok);
        if (ok) {
            metadata->hasLength = true;
            metadata->lengthMs = parsed;
            metadata->lengthTag = value;
        }
    } else if (equalsIgnoreCase(name, "fade")) {
        bool ok = false;
        const unsigned long parsed = parsePsfTimeMs(value, ok);
        if (ok) {
            metadata->hasFade = true;
            metadata->fadeMs = parsed;
            metadata->fadeTag = value;
        }
    } else if (equalsIgnoreCase(name, "title")) {
        metadata->title = value;
    } else if (equalsIgnoreCase(name, "artist")) {
        metadata->artist = value;
    } else if (equalsIgnoreCase(name, "composer")) {
        metadata->composer = value;
    } else if (equalsIgnoreCase(name, "genre")) {
        metadata->genre = value;
    } else if (equalsIgnoreCase(name, "game")) {
        metadata->game = value;
    } else if (equalsIgnoreCase(name, "copyright")) {
        metadata->copyright = value;
    } else if (equalsIgnoreCase(name, "year")) {
        metadata->year = value;
    } else if (equalsIgnoreCase(name, "comment")) {
        metadata->comment = value;
    }
    return 0;
}

struct ViogsfLibContext {
    std::string baseDir;
};

static bool readWholeFile(const std::string& path, uint8_t** outData, size_t* outSize) {
    FILE* handle = std::fopen(path.c_str(), "rb");
    if (!handle) {
        return false;
    }
    std::fseek(handle, 0, SEEK_END);
    const long size = std::ftell(handle);
    std::fseek(handle, 0, SEEK_SET);
    if (size <= 0) {
        std::fclose(handle);
        return false;
    }
    uint8_t* data = static_cast<uint8_t*>(std::malloc(static_cast<size_t>(size)));
    if (!data) {
        std::fclose(handle);
        return false;
    }
    const size_t got = std::fread(data, 1, static_cast<size_t>(size), handle);
    std::fclose(handle);
    if (got != static_cast<size_t>(size)) {
        std::free(data);
        return false;
    }
    *outData = data;
    *outSize = static_cast<size_t>(size);
    return true;
}

static int viogsfReadLib(const char* name, uint8_t** outData, size_t* outSize, void* ctx) {
    if (!name || !outData || !outSize) {
        return -1;
    }
    std::string normalized(name);
    std::replace(normalized.begin(), normalized.end(), '\\', '/');
    const auto* libContext = static_cast<const ViogsfLibContext*>(ctx);
    if (!std::filesystem::path(normalized).is_absolute() && libContext != nullptr &&
        !libContext->baseDir.empty()) {
        const std::filesystem::path baseDir(libContext->baseDir);
        if (readWholeFile((baseDir / normalized).string(), outData, outSize)) {
            return 0;
        }
        if (readWholeFile((baseDir / std::filesystem::path(normalized).filename()).string(),
                          outData, outSize)) {
            return 0;
        }
        return -1;
    }
    return readWholeFile(normalized, outData, outSize) ? 0 : -1;
}
}

ViogsfDecoder::ViogsfDecoder() = default;

ViogsfDecoder::~ViogsfDecoder() {
    close();
}

bool ViogsfDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
    if (!path || path[0] == '\0') {
        return false;
    }
    sourcePath = path;
    const PsfOpenContext openContext {
        sourcePath
    };
    const psf_file_callbacks ioCallbacks = {
            "\\/:",
            const_cast<PsfOpenContext*>(&openContext),
            stdioFopen,
            stdioFread,
            stdioFseek,
            stdioFclose,
            stdioFtell
    };

    MetadataState metadata;
    const int metadataLoadResult = psf_load(
            path,
            &ioCallbacks,
            0x22,
            nullptr,
            nullptr,
            gsfInfoMetadata,
            &metadata,
            0,
            nullptr,
            nullptr
    );
    if (metadataLoadResult <= 0) {
        LOGW("psf_load(metadata) failed for GSF (continuing): %s", path);
    }

    uint8_t* songData = nullptr;
    size_t songSize = 0;
    if (!readWholeFile(sourcePath, &songData, &songSize)) {
        LOGE("Failed to read GSF file: %s", path);
        closeInternalLocked();
        return false;
    }

    ViogsfLibContext libContext {
        std::filesystem::path(sourcePath).parent_path().string()
    };
    const viogsf_reader_t reader { viogsfReadLib, nullptr, &libContext };
    player = viogsf_open(songData, songSize, &reader, kNativeSampleRate);
    std::free(songData);
    if (!player) {
        LOGE("viogsf_open failed: %s", path);
        closeInternalLocked();
        return false;
    }

    title = metadata.title;
    artist = metadata.artist;
    composer = metadata.composer;
    genre = metadata.genre;
    gameName = metadata.game;
    copyrightText = metadata.copyright;
    year = metadata.year;
    comment = metadata.comment;
    lengthTag = metadata.lengthTag;
    fadeTag = metadata.fadeTag;
    if (artist.empty()) {
        artist = metadata.game;
    }
    if (genre.empty()) {
        genre = "GSF";
    }
    if (title.empty()) {
        title = std::filesystem::path(path).stem().string();
    }

    if (metadata.hasLength) {
        const unsigned long totalMs = metadata.lengthMs + (metadata.hasFade ? metadata.fadeMs : 0u);
        durationSeconds = static_cast<double>(totalMs) / 1000.0;
        durationReliable = durationSeconds > 0.0;
    } else {
        durationSeconds = kFallbackDurationSeconds;
        durationReliable = false;
    }

    renderedFrames = 0;
    isOpen = true;
    return true;
}

void ViogsfDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void ViogsfDecoder::closeInternalLocked() {
    if (player) {
        viogsf_close(player);
        player = nullptr;
    }
    pcmScratch.clear();
    isOpen = false;
    repeatMode = 0;
    renderedFrames = 0;
    durationSeconds = kFallbackDurationSeconds;
    durationReliable = false;
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

int ViogsfDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || !buffer || numFrames <= 0) {
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

    const size_t sampleCount = static_cast<size_t>(framesToRender) * channels;
    pcmScratch.resize(sampleCount);
    const int got = viogsf_render(player, pcmScratch.data(), framesToRender);
    if (got <= 0) {
        return 0;
    }
    for (int i = 0; i < got * channels; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[static_cast<size_t>(i)]) / 32768.0f;
    }
    renderedFrames += got;
    return got;
}

void ViogsfDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return;
    }
    double clamped = std::max(0.0, seconds);
    if (durationReliable && durationSeconds > 0.0) {
        clamped = std::min(clamped, durationSeconds);
    }
    viogsf_reset(player);
    renderedFrames = 0;
    int64_t framesToSkip = static_cast<int64_t>(std::llround(clamped * sampleRate));
    if (framesToSkip <= 0) {
        return;
    }
    std::vector<int16_t> discard(static_cast<size_t>(kSeekDrainChunkFrames) * channels);
    while (framesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(framesToSkip, kSeekDrainChunkFrames));
        const int got = viogsf_render(player, discard.data(), chunk);
        if (got <= 0) {
            break;
        }
        framesToSkip -= got;
        renderedFrames += got;
    }
}

double ViogsfDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int ViogsfDecoder::getSampleRate() {
    return sampleRate;
}

int ViogsfDecoder::getBitDepth() {
    return bitDepth;
}

std::string ViogsfDecoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int ViogsfDecoder::getChannelCount() {
    return channels;
}

std::string ViogsfDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string ViogsfDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string ViogsfDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string ViogsfDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string ViogsfDecoder::getGameName() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return gameName;
}

std::string ViogsfDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string ViogsfDecoder::getYear() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return year;
}

std::string ViogsfDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

std::string ViogsfDecoder::getLengthTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return lengthTag;
}

std::string ViogsfDecoder::getFadeTag() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return fadeTag;
}

void ViogsfDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = (mode >= 0 && mode <= 3) ? mode : 0;
}

int ViogsfDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int ViogsfDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int ViogsfDecoder::getFixedSampleRateHz() const {
    return sampleRate;
}

double ViogsfDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate <= 0) return 0.0;
    return static_cast<double>(renderedFrames) / sampleRate;
}

std::string ViogsfDecoder::getCoreStringInfo(const char* name) {
    if (name == nullptr) return "";
    if (std::strcmp(name, "gameName") == 0) return getGameName();
    if (std::strcmp(name, "copyright") == 0) return getCopyright();
    if (std::strcmp(name, "year") == 0) return getYear();
    if (std::strcmp(name, "comment") == 0) return getComment();
    if (std::strcmp(name, "lengthTag") == 0) return getLengthTag();
    if (std::strcmp(name, "fadeTag") == 0) return getFadeTag();
    return "";
}

std::vector<std::string> ViogsfDecoder::getSupportedExtensions() {
    return {"gsf", "minigsf"};
}
