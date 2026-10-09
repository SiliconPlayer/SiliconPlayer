#include "NezplugppDecoder.h"

#define SP_LOG_MODULE "NezplugppDecoder"
#include "../SiliconLog.h"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <filesystem>

extern "C" {
#include <nezplugpp/nezplug.h>
}

namespace {
constexpr int kNativeSampleRate = 44100;
constexpr int kNativeChannels = 2;
constexpr int kSeekDrainChunkFrames = 1024;
constexpr double kFallbackDurationSeconds = 180.0;

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
}

NezplugppDecoder::NezplugppDecoder() = default;

NezplugppDecoder::~NezplugppDecoder() {
    close();
}

bool NezplugppDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
    if (!path || path[0] == '\0') {
        return false;
    }
    sourcePath = path;

    uint8_t* songData = nullptr;
    size_t songSize = 0;
    if (!readWholeFile(sourcePath, &songData, &songSize)) {
        LOGE("Failed to read file: %s", path);
        closeInternalLocked();
        return false;
    }

    player = NEZNew();
    if (!player) {
        std::free(songData);
        closeInternalLocked();
        return false;
    }
    if (NEZLoad(player, songData, static_cast<Uint>(songSize)) != 0) {
        LOGE("NEZLoad failed: %s", path);
        std::free(songData);
        closeInternalLocked();
        return false;
    }
    std::free(songData);

    NEZSetFrequency(player, kNativeSampleRate);
    NEZSetChannel(player, kNativeChannels);
    const unsigned int startSong = NEZGetSongStart(player);
    if (startSong > 0) {
        NEZSetSongNo(player, startSong);
    }
    NEZReset(player);

    char* infoTitle = nullptr;
    char* infoArtist = nullptr;
    char* infoCopyright = nullptr;
    char* infoDetail = nullptr;
    NEZGetFileInfo(&infoTitle, &infoArtist, &infoCopyright, &infoDetail);
    if (infoTitle) title = infoTitle;
    if (infoArtist) artist = infoArtist;
    if (infoCopyright) copyrightText = infoCopyright;
    if (infoDetail) comment = infoDetail;
    if (title.empty()) {
        title = std::filesystem::path(path).stem().string();
    }

    durationSeconds = kFallbackDurationSeconds;
    durationReliable = false;
    renderedFrames = 0;
    isOpen = true;
    return true;
}

void NezplugppDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

void NezplugppDecoder::closeInternalLocked() {
    if (player) {
        NEZDelete(player);
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
    copyrightText.clear();
    comment.clear();
}

int NezplugppDecoder::read(float* buffer, int numFrames) {
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

    // NEZRender takes frames; each frame emits one sample per channel.
    const size_t sampleCount = static_cast<size_t>(framesToRender) * channels;
    pcmScratch.resize(sampleCount);
    NEZRender(player, pcmScratch.data(), static_cast<Uint>(framesToRender));
    for (size_t i = 0; i < sampleCount; ++i) {
        buffer[i] = static_cast<float>(pcmScratch[i]) / 32768.0f;
    }
    renderedFrames += framesToRender;
    return framesToRender;
}

void NezplugppDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return;
    }
    double clamped = std::max(0.0, seconds);
    if (durationReliable && durationSeconds > 0.0) {
        clamped = std::min(clamped, durationSeconds);
    }
    NEZReset(player);
    renderedFrames = 0;
    int64_t framesToSkip = static_cast<int64_t>(std::llround(clamped * sampleRate));
    if (framesToSkip <= 0) {
        return;
    }
    std::vector<int16_t> discard(static_cast<size_t>(kSeekDrainChunkFrames) * channels);
    while (framesToSkip > 0) {
        const int chunk = static_cast<int>(std::min<int64_t>(framesToSkip, kSeekDrainChunkFrames));
        NEZRender(player, discard.data(), static_cast<Uint>(chunk));
        framesToSkip -= chunk;
        renderedFrames += chunk;
    }
}

double NezplugppDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int NezplugppDecoder::getSampleRate() {
    return sampleRate;
}

int NezplugppDecoder::getBitDepth() {
    return bitDepth;
}

std::string NezplugppDecoder::getBitDepthLabel() {
    return "16-bit PCM";
}

int NezplugppDecoder::getChannelCount() {
    return channels;
}

int NezplugppDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return 1;
    }
    return std::max(1, static_cast<int>(NEZGetSongMax(player)));
}

int NezplugppDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return 0;
    }
    const int songNo = static_cast<int>(NEZGetSongNo(player));
    return std::max(0, songNo - 1);
}

bool NezplugppDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || index < 0) {
        return false;
    }
    return selectSongLocked(static_cast<unsigned int>(index + 1));
}

bool NezplugppDecoder::selectSongLocked(unsigned int songNo) {
    if (!player || songNo == 0) {
        return false;
    }
    NEZSetSongNo(player, songNo);
    NEZReset(player);
    renderedFrames = 0;
    return true;
}

std::string NezplugppDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string NezplugppDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string NezplugppDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyrightText;
}

std::string NezplugppDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

void NezplugppDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = (mode >= 0 && mode <= 3) ? mode : 0;
}

int NezplugppDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int NezplugppDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int caps = PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_FIXED_SAMPLE_RATE;
    if (durationReliable) {
        caps |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    return caps;
}

int NezplugppDecoder::getFixedSampleRateHz() const {
    return sampleRate;
}

double NezplugppDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (sampleRate <= 0) return 0.0;
    return static_cast<double>(renderedFrames) / sampleRate;
}

std::vector<std::string> NezplugppDecoder::getSupportedExtensions() {
    return {"kss"};
}
