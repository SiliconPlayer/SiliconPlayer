#include "NezplugppDecoder.h"

#define SP_LOG_MODULE "NezplugppDecoder"
#include "../SiliconLog.h"
#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <chrono>
#include <cstdlib>
#include <cstring>
#include <filesystem>

extern "C" {
#include <nezplugpp/nezplug.h>
}
#include <nezplugpp/device/kmsnddev.h>
#include <nezplugpp/format/nezscope.h>

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

constexpr int kNezScopeTextStride = 10;
constexpr float kNezplugppScopeDcFollow = 0.0025f;
constexpr int kNezScopeTextFlagActive = 1 << 0;
constexpr float kNezScopeActivePeak = 0.0015f;

float scopeGainForDev(int devId) {
    if (devId == DEV_2A03_TR) return 1.2e-5f;
    if (devId == DEV_2A03_NOISE) return 3.5e-5f;
    if (devId == DEV_2A03_DPCM) return 5.0e-6f;
    if (devId == DEV_VRC6_SAW) return 1.0e-7f;
    if (devId == DEV_MMC5_DA) return 5.0e-6f;
    if (devId == DEV_ADPCM_CH1) return 2.4e-7f;
    if (devId >= DEV_2A03_SQ1 && devId <= DEV_2A03_SQ2) return 2.0e-5f;
    if (devId >= DEV_VRC6_SQ1 && devId <= DEV_VRC6_SQ2) return 3.0e-7f;
    if (devId >= DEV_MMC5_SQ1 && devId <= DEV_MMC5_SQ2) return 2.0e-5f;
    if (devId >= DEV_N106_CH1 && devId <= DEV_N106_CH8) return 7.5e-7f;
    if (devId >= DEV_AY8910_CH1 && devId <= DEV_AY8910_CH3) return 7.5e-7f;
    if (devId >= DEV_SCC_CH1 && devId <= DEV_SCC_CH5) return 7.0e-7f;
    if (devId >= DEV_DMG_SQ1 && devId <= DEV_DMG_NOISE) return 1.15e-6f;
    if (devId >= DEV_HUC6230_CH1 && devId <= DEV_HUC6230_CH6) return 1.15e-6f;
    if (devId >= DEV_SN76489_SQ1 && devId <= DEV_SN76489_NOISE) return 1.15e-6f;
    if (devId >= DEV_YM2413_CH1 && devId <= DEV_YM2413_CH9) return 3.0e-7f;
    if (devId >= DEV_Y8950_CH1 && devId <= DEV_Y8950_CH9) return 2.4e-7f;
    if (devId >= DEV_VRC7_CH1 && devId <= DEV_VRC7_CH6) return 3.0e-7f;
    if (devId >= DEV_SMSFM_CH1 && devId <= DEV_SMSFM_CH9) return 3.0e-7f;
    return 1.0e-6f;
}

bool parseScopeBoolOption(const char* value, bool fallback) {
    if (!value) return fallback;
    auto equalsIgnoreCase = [](const char* a, const char* b) {
        while (*a && *b) {
            if (std::tolower(static_cast<unsigned char>(*a)) !=
                std::tolower(static_cast<unsigned char>(*b))) return false;
            ++a; ++b;
        }
        return *a == *b;
    };
    if (equalsIgnoreCase(value, "1") || equalsIgnoreCase(value, "true") ||
        equalsIgnoreCase(value, "yes") || equalsIgnoreCase(value, "on")) return true;
    if (equalsIgnoreCase(value, "0") || equalsIgnoreCase(value, "false") ||
        equalsIgnoreCase(value, "no") || equalsIgnoreCase(value, "off")) return false;
    return fallback;
}
}

NezplugppDecoder::NezplugppDecoder() : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

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
    buildToggleChannelsLocked();
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
    // Scene rips tag unknown strings with a literal "<?>" placeholder. Treat it
    // as missing so the app's own Unknown Artist/Title labels apply instead.
    auto clearUnknownPlaceholder = [](std::string& text) {
        std::string trimmed = text;
        while (!trimmed.empty() && trimmed.back() == ' ') trimmed.pop_back();
        if (trimmed == "<?>") text.clear();
    };
    clearUnknownPlaceholder(title);
    clearUnknownPlaceholder(artist);
    clearUnknownPlaceholder(copyrightText);
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
    NEZSetScopeCallback(nullptr, nullptr);
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
        NEZScopeFlush();
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
    if (framesRead > 0 && scopeCaptureEnabled) {
        channelScopeLastReadNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        if (channelScopeState &&
            channelScopeState->tryBeginCapture(channelScopeLastReadNs, scopeVoices)) {
            publishScopeSnapshotLocked();
        }
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
    formatName.clear();
    songVoiceCount = 0;
    songExtDevice = 0;
    sgcSysType = -1;
    if (data == nullptr || size < 8) return;
    if (size >= 0x10 && (std::memcmp(data, "KSCC", 4) == 0 || std::memcmp(data, "KSSX", 4) == 0)) {
        formatName = std::memcmp(data, "KSCC", 4) == 0 ? "KSCC" : "KSSX";
        songExtDevice = data[0x0F];
        const unsigned extDevice = songExtDevice;
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
        songExtDevice = data[0x7B];
        songVoiceCount = countNsfVoices(songExtDevice);
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
        songExtDevice = data[0x0C];
        songVoiceCount = countNsfVoices(songExtDevice);
        return;
    }
    if (size >= 0x30 && std::memcmp(data, "SGC", 3) == 0) {
        formatName = "SGC";
        sgcSysType = data[0x28];
        songVoiceCount = sgcSysType == 0 ? 13 : 4;
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
    if (!name || !value) {
        return;
    }
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (std::strcmp(name, "visualization.channel_scope_active") == 0) {
        const bool enabled = parseScopeBoolOption(value, scopeCaptureEnabled);
        if (scopeCaptureEnabled == enabled) return;
        scopeCaptureEnabled = enabled;
        resetChannelScopeLocked();
        return;
    }
    if (std::strcmp(name, "nezplugpp.scope_dc_block") == 0) {
        scopeDcBlockEnabled = parseScopeBoolOption(value, scopeDcBlockEnabled);
        return;
    }
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

void NezplugppDecoder::buildToggleChannelsLocked() {
    for (int devId : toggleChannelDevIds) {
        const auto muteIt = channelMuteByDev.find(devId);
        if (muteIt == channelMuteByDev.end() || !muteIt->second) chmask[devId] = 1;
    }
    toggleChannelNames.clear();
    toggleChannelDevIds.clear();
    toggleChannelMuted.clear();
    auto addChannel = [&](const char* prefix, int devFirst, int count) {
        for (int i = 0; i < count; ++i) {
            char label[32];
            std::snprintf(label, sizeof(label), "%s %d", prefix, i + 1);
            toggleChannelNames.emplace_back(label);
            toggleChannelDevIds.push_back(devFirst + i);
            const auto muteIt = channelMuteByDev.find(devFirst + i);
            const bool muted = muteIt != channelMuteByDev.end() && muteIt->second;
            toggleChannelMuted.push_back(muted);
            chmask[devFirst + i] = muted ? 0 : 1;
        }
    };
    auto addSingle = [&](const char* label, int devId) {
        toggleChannelNames.emplace_back(label);
        toggleChannelDevIds.push_back(devId);
        const auto muteIt = channelMuteByDev.find(devId);
        const bool muted = muteIt != channelMuteByDev.end() && muteIt->second;
        toggleChannelMuted.push_back(muted);
        chmask[devId] = muted ? 0 : 1;
    };
    if (formatName == "KSCC" || formatName == "KSSX") {
        if ((songExtDevice & 0x02) != 0) {
            addChannel("SN Square", DEV_SN76489_SQ1, 3);
            addSingle("SN Noise", DEV_SN76489_NOISE);
            if ((songExtDevice & 0x01) != 0) addChannel("FM", DEV_SMSFM_CH1, 9);
        } else {
            addChannel("PSG", DEV_AY8910_CH1, 3);
            if ((songExtDevice & 0x80) == 0) addChannel("SCC", DEV_SCC_CH1, 5);
            if ((songExtDevice & 0x01) != 0) addChannel("FM", DEV_YM2413_CH1, 9);
            if ((songExtDevice & 0x08) != 0) {
                addChannel("FMA", DEV_Y8950_CH1, 9);
                addSingle("ADPCM", DEV_ADPCM_CH1);
            }
        }
    } else if (formatName == "NSF" || formatName == "NSD") {
        addChannel("Square", DEV_2A03_SQ1, 2);
        addSingle("Triangle", DEV_2A03_TR);
        addSingle("Noise", DEV_2A03_NOISE);
        addSingle("DPCM", DEV_2A03_DPCM);
        if ((songExtDevice & 0x01) != 0) {
            addChannel("VRC6 Square", DEV_VRC6_SQ1, 2);
            addSingle("VRC6 Saw", DEV_VRC6_SAW);
        }
        if ((songExtDevice & 0x02) != 0) addChannel("VRC7", DEV_VRC7_CH1, 6);
        if ((songExtDevice & 0x04) != 0) addSingle("FDS", DEV_FDS_CH1);
        if ((songExtDevice & 0x08) != 0) {
            addChannel("MMC5 Square", DEV_MMC5_SQ1, 2);
            addSingle("MMC5 PCM", DEV_MMC5_DA);
        }
        if ((songExtDevice & 0x10) != 0) addChannel("N163", DEV_N106_CH1, 8);
        if ((songExtDevice & 0x20) != 0) addChannel("5B", DEV_AY8910_CH1, 3);
    } else if (formatName == "GBS" || formatName == "GBR") {
        addChannel("Square", DEV_DMG_SQ1, 2);
        addSingle("Wave", DEV_DMG_WM);
        addSingle("Noise", DEV_DMG_NOISE);
    } else if (formatName == "HES") {
        addChannel("Wave", DEV_HUC6230_CH1, 6);
    } else if (formatName == "SGC") {
        addChannel("Square", DEV_SN76489_SQ1, 3);
        addSingle("Noise", DEV_SN76489_NOISE);
        if (sgcSysType == 0) addChannel("FM", DEV_SMSFM_CH1, 9);
    } else if (formatName == "AY") {
        addChannel("AY", DEV_AY8910_CH1, 3);
    }
    scopeVoices = static_cast<int>(toggleChannelNames.size());
    scopeDevToIndex.clear();
    for (size_t i = 0; i < toggleChannelDevIds.size(); ++i) {
        scopeDevToIndex.emplace(toggleChannelDevIds[i], static_cast<int>(i));
    }
    resetChannelScopeLocked();
    NEZSetScopeCallback(&NezplugppDecoder::scopeTapCallback, this);
}

void NezplugppDecoder::applyChannelMuteLocked(int channelIndex) {
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelDevIds.size())) return;
    const int devId = toggleChannelDevIds[static_cast<size_t>(channelIndex)];
    const bool muted = toggleChannelMuted[static_cast<size_t>(channelIndex)];
    chmask[devId] = muted ? 0 : 1;
    channelMuteByDev[devId] = muted;
}

std::vector<std::string> NezplugppDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return toggleChannelNames;
}

std::vector<uint8_t> NezplugppDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return std::vector<uint8_t>(toggleChannelNames.size(), 1);
}

void NezplugppDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return;
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyChannelMuteLocked(channelIndex);
}

bool NezplugppDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return false;
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void NezplugppDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    for (size_t i = 0; i < toggleChannelDevIds.size(); ++i) applyChannelMuteLocked(static_cast<int>(i));
}

void NezplugppDecoder::resetChannelScopeLocked() {
    scopeRingRaw.clear();
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    scopeTapChunkFrames = 0;
    scopeTapsThisBlock = 0;
    scopeTapGeneration = 0;
    scopeTapSeen.assign(static_cast<size_t>(std::max(scopeVoices, 0)), 0);
    scopeDcEstimate.assign(static_cast<size_t>(std::max(scopeVoices, 0)), 0.0f);
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void NezplugppDecoder::appendScopeTapLocked(int devId, const float* samples, int frames) {
    if (!scopeCaptureEnabled || scopeVoices <= 0 || !samples || frames <= 0) {
        return;
    }
    const auto indexIt = scopeDevToIndex.find(devId);
    if (indexIt == scopeDevToIndex.end()) return;
    const int voice = indexIt->second;
    if (voice < 0 || voice >= scopeVoices) return;
    if (scopeRingRaw.empty()) {
        scopeRingRaw.assign(
                static_cast<size_t>(scopeVoices) * ChannelScopeSharedState::kMaxSamples, 0.0f);
    }
    // A voice arriving twice starts a new block; publish what landed so far.
    if (scopeTapsThisBlock == 0 || scopeTapSeen[static_cast<size_t>(voice)] == scopeTapGeneration) {
        if (scopeTapsThisBlock > 0 && scopeTapChunkFrames > 0) {
            scopeRingWritePos =
                    (scopeRingWritePos + scopeTapChunkFrames) % ChannelScopeSharedState::kMaxSamples;
            scopeRingSamples =
                    std::min(scopeRingSamples + scopeTapChunkFrames, ChannelScopeSharedState::kMaxSamples);
        }
        ++scopeTapGeneration;
        scopeTapsThisBlock = 0;
        scopeTapChunkFrames = frames;
    }
    scopeTapSeen[static_cast<size_t>(voice)] = scopeTapGeneration;
    ++scopeTapsThisBlock;
    float* ring = scopeRingRaw.data() +
            static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
    const int firstBlock =
            std::min(frames, ChannelScopeSharedState::kMaxSamples - scopeRingWritePos);
    const float gain = scopeGainForDev(devId);
    for (int i = 0; i < firstBlock; ++i) {
        ring[scopeRingWritePos + i] = std::clamp(samples[i] * gain, -1.0f, 1.0f);
    }
    for (int i = firstBlock; i < frames; ++i) {
        ring[i - firstBlock] = std::clamp(samples[i] * gain, -1.0f, 1.0f);
    }
    if (scopeTapsThisBlock >= scopeVoices && scopeTapChunkFrames > 0) {
        scopeRingWritePos =
                (scopeRingWritePos + scopeTapChunkFrames) % ChannelScopeSharedState::kMaxSamples;
        scopeRingSamples =
                std::min(scopeRingSamples + scopeTapChunkFrames, ChannelScopeSharedState::kMaxSamples);
        scopeTapsThisBlock = 0;
        scopeTapChunkFrames = 0;
    }
}

void NezplugppDecoder::publishScopeSnapshotLocked() {
    if (!channelScopeState || scopeRingRaw.empty() || scopeRingSamples <= 0 || scopeVoices <= 0) {
        return;
    }

    scopePublishRaw.assign(
            static_cast<size_t>(scopeVoices) * ChannelScopeSharedState::kMaxSamples, 0.0f);
    scopePublishVu.assign(static_cast<size_t>(scopeVoices), 0.0f);
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
    for (int voice = 0; voice < scopeVoices; ++voice) {
        float* dst = raw.data() + static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
        const float* src = scopeRingRaw.data() +
                static_cast<size_t>(voice) * ChannelScopeSharedState::kMaxSamples;
        std::copy_n(src + historyStart, firstBlock, dst + zeroPrefix);
        std::copy_n(src, filledSamples - firstBlock, dst + zeroPrefix + firstBlock);

        // Taps arrive raw; center them with a persistent one-pole DC blocker.
        // Per-chunk mean removal would re-offset held steps and hop the
        // baseline between chunks.
        float& dc = scopeDcEstimate[static_cast<size_t>(voice)];
        for (int i = 0; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            const float v = dst[i];
            dc += (v - dc) * kNezplugppScopeDcFollow;
            dst[i] = scopeDcBlockEnabled ? (v - dc) : v;
        }

        float peak = 0.0f;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        for (int i = start; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            peak = std::max(peak, std::abs(dst[i]));
        }
        vu[static_cast<size_t>(voice)] = std::clamp(peak, 0.0f, 1.0f);
    }
    channelScopeState->publish(raw, vu, scopeVoices, ++channelScopeSourceSerial, true);
}

std::shared_ptr<ChannelScopeSharedState> NezplugppDecoder::getChannelScopeSharedState() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return channelScopeState;
}

std::vector<int32_t> NezplugppDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (scopeRingRaw.empty() || scopeRingSamples <= 0 || scopeVoices <= 0) {
        return {};
    }

    const int channelsToExport = std::min(scopeVoices, std::clamp(maxChannels, 1, scopeVoices));
    std::vector<int32_t> flat(static_cast<size_t>(channelsToExport * kNezScopeTextStride), -1);
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
        const size_t base = static_cast<size_t>(voice * kNezScopeTextStride);
        int flags = 0;
        if (recentPeak > kNezScopeActivePeak) {
            flags |= kNezScopeTextFlagActive;
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

void NezplugppDecoder::scopeTapCallback(int devId, const float* samples, int frames, void* user) {
    auto* self = static_cast<NezplugppDecoder*>(user);
    if (!self) {
        return;
    }
    // Callbacks fire from inside NEZRender, which read() invokes with
    // decodeMutex held, so the ring is already protected.
    self->appendScopeTapLocked(devId, samples, frames);
}

std::vector<std::string> NezplugppDecoder::getSupportedExtensions() {
    return {"kss", "nsf", "gbs", "hes", "sgc", "nsd", "ay"};
}
