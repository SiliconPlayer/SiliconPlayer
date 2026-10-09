#include "NezplugppDecoder.h"

#define SP_LOG_MODULE "NezplugppDecoder"
#include "../SiliconLog.h"
#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
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
    parseSongFormatLocked(songData, songSize);
    std::free(songData);

    NEZSetFrequency(player, kNativeSampleRate);
    NEZSetChannel(player, kNativeChannels);
    NEZSetFilter(player, static_cast<Uint>(std::clamp(filterType, 0, 3)));
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

    {
        const unsigned startSong = NEZGetSongStart(player);
        const unsigned maxSong = NEZGetSongMax(player);
        char info[64];
        std::snprintf(info, sizeof(info), "%u of %u", startSong, maxSong);
        subtuneInfo = info;
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
    formatName.clear();
    songVoiceCount = 0;
    subtuneInfo.clear();
}

int NezplugppDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || !buffer || numFrames <= 0) {
        return 0;
    }

    // KSS has no tagged durations and loops natively, so the fallback
    // duration is the only terminal boundary. Modes 0/1 end there and let
    // the engine stop or advance subtunes; mode 3 restarts the current
    // subtune in place; mode 2 never terminates.
    const int64_t durationFrames = static_cast<int64_t>(std::llround(durationSeconds * sampleRate));
    int framesRead = 0;
    while (framesRead < numFrames) {
        int framesToRender = numFrames - framesRead;
        if (repeatMode != 2 && durationSeconds > 0.0 && durationFrames > 0) {
            const int64_t remaining = durationFrames - renderedFrames;
            if (remaining <= 0) {
                if (repeatMode == 3) {
                    NEZReset(player);
                    renderedFrames = 0;
                    continue;
                }
                break;
            }
            framesToRender = static_cast<int>(std::min<int64_t>(framesToRender, remaining));
        }
        if (framesToRender <= 0) {
            break;
        }

        // NEZRender takes frames; each frame emits one sample per channel.
        const size_t baseSample = static_cast<size_t>(framesRead) * channels;
        const size_t sampleCount = static_cast<size_t>(framesToRender) * channels;
        pcmScratch.resize(sampleCount);
        NEZRender(player, pcmScratch.data(), static_cast<Uint>(framesToRender));
        float gain = 1.0f;
        const auto trimIt = volumeTrimDb.find(formatName);
        if (trimIt != volumeTrimDb.end() && trimIt->second != 0.0f) {
            gain = std::pow(10.0f, trimIt->second / 20.0f);
        }
        for (size_t i = 0; i < sampleCount; ++i) {
            buffer[baseSample + i] = static_cast<float>(pcmScratch[i]) / 32768.0f * gain;
        }
        renderedFrames += framesToRender;
        framesRead += framesToRender;
    }
    return framesRead;
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

int NezplugppDecoder::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return songVoiceCount > 0 ? songVoiceCount : channels;
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
    return "";
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

static int countNsfVoices(unsigned extChips) {
    int voices = 5;
    if ((extChips & 0x01) != 0) voices += 3;
    if ((extChips & 0x02) != 0) voices += 6;
    if ((extChips & 0x04) != 0) voices += 1;
    if ((extChips & 0x08) != 0) voices += 3;
    if ((extChips & 0x10) != 0) voices += 8;
    if ((extChips & 0x20) != 0) voices += 3;
    return voices;
}

void NezplugppDecoder::parseSongFormatLocked(const uint8_t* data, size_t size) {
    if (data == nullptr || size < 8) return;
    if (size >= 0x10 && (std::memcmp(data, "KSCC", 4) == 0 || std::memcmp(data, "KSSX", 4) == 0)) {
        formatName = std::memcmp(data, "KSCC", 4) == 0 ? "KSCC" : "KSSX";
        const unsigned extDevice = data[0x0F];
        if ((extDevice & 0x02) != 0) {
            songVoiceCount = 4 + (((extDevice & 0x01) != 0) ? 9 : 0);
        } else {
            songVoiceCount = 3 + (((extDevice & 0x01) != 0) ? 9 : 0) +
                             (((extDevice & 0x08) != 0) ? 10 : 0) +
                             (((extDevice & 0x80) != 0) ? 0 : 5);
        }
        return;
    }
    if (std::memcmp(data, "HESM", 4) == 0 ||
        (size >= 0x204 && std::memcmp(data + 0x200, "HESM", 4) == 0)) {
        formatName = "HES";
        songVoiceCount = 6;
        return;
    }
    if (size >= 0x80 && std::memcmp(data, "NESM", 4) == 0 && data[4] == 0x1A) {
        formatName = "NSF";
        songVoiceCount = countNsfVoices(data[0x7B]);
        return;
    }
    if (std::memcmp(data, "ZXAYEMUL", 8) == 0) {
        formatName = "AY";
        songVoiceCount = 6;
        return;
    }
    if (std::memcmp(data, "GBRF", 4) == 0) {
        formatName = "GBR";
        songVoiceCount = 4;
        return;
    }
    if (size >= 0x10 && std::memcmp(data, "GBS", 3) == 0) {
        formatName = "GBS";
        songVoiceCount = 4;
        return;
    }
    if (size >= 0x10 && std::memcmp(data, "NESL", 4) == 0 && data[4] == 0x1A) {
        formatName = "NSD";
        songVoiceCount = countNsfVoices(data[0x0C]);
        return;
    }
    if (size >= 0x30 && std::memcmp(data, "SGC", 3) == 0) {
        formatName = "SGC";
        songVoiceCount = data[0x28] == 0 ? 13 : 4;
        return;
    }
}

std::string NezplugppDecoder::getCoreStringInfo(const char* name) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || name == nullptr) return "";
    if (std::strcmp(name, "copyright") == 0) return copyrightText;
    if (std::strcmp(name, "detail") == 0) {
        std::string pretty;
        size_t begin = 0;
        while (begin < comment.size()) {
            size_t end = comment.find('\n', begin);
            if (end == std::string::npos) end = comment.size();
            std::string line = comment.substr(begin, end - begin);
            while (!line.empty() && (line.back() == '\r' || line.back() == ' ')) line.pop_back();
            size_t colon = line.find(':');
            if (colon != std::string::npos) {
                size_t labelEnd = colon;
                while (labelEnd > 0 && line[labelEnd - 1] == ' ') --labelEnd;
                size_t valueStart = colon + 1;
                while (valueStart < line.size() && line[valueStart] == ' ') ++valueStart;
                line = line.substr(0, labelEnd) + ": " + line.substr(valueStart);
            }
            if (!line.empty()) {
                if (!pretty.empty()) pretty += '\n';
                pretty += line;
            }
            begin = end + 1;
        }
        return pretty;
    }
    if (std::strcmp(name, "format") == 0) return formatName;
    if (std::strcmp(name, "songVoices") == 0) {
        return songVoiceCount > 0 ? std::to_string(songVoiceCount) : "";
    }
    if (std::strcmp(name, "subtuneInfo") == 0) return subtuneInfo;
    return "";
}

void NezplugppDecoder::setOption(const char* name, const char* value) {
    if (name == nullptr || value == nullptr) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (std::strcmp(name, "nezplugpp.filter") == 0) {
        filterType = std::clamp(static_cast<int>(std::strtol(value, nullptr, 10)), 0, 3);
        if (isOpen && player) {
            NEZSetFilter(player, static_cast<Uint>(filterType));
        }
        return;
    }
    static const char* kTrimPrefix = "nezplugpp.volume_";
    if (std::strncmp(name, kTrimPrefix, std::strlen(kTrimPrefix)) == 0) {
        std::string key = name + std::strlen(kTrimPrefix);
        for (char& c : key) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
        const auto it = volumeTrimDb.find(key);
        if (it != volumeTrimDb.end()) {
            it->second = std::clamp(static_cast<float>(std::strtof(value, nullptr)), -20.0f, 20.0f);
        }
    }
}

std::vector<std::string> NezplugppDecoder::getSupportedExtensions() {
    return {"kss", "nsf", "gbs", "hes", "sgc", "nsd", "ay"};
}
