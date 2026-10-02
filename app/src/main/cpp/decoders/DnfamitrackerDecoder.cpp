#include "DnfamitrackerDecoder.h"

#include <algorithm>
#include <chrono>
#include <cmath>

constexpr float kDnfamitrackerScopeGain = 0.5f;

DnfamitrackerDecoder::DnfamitrackerDecoder()
    : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {
}

DnfamitrackerDecoder::~DnfamitrackerDecoder() {
    close();
}

bool DnfamitrackerDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (isOpen) {
        closeLocked();
    }
    if (!path) {
        return false;
    }

    player = std::make_unique<CFTMPlayer>();
    player->SetupSound(sampleRate);

    if (!player->LoadDocument(path)) {
        player.reset();
        return false;
    }

    CFTMDocument* doc = player->GetDocument();
    if (doc) {
        title = doc->GetSongName() ? doc->GetSongName() : "";
        artist = doc->GetSongArtist() ? doc->GetSongArtist() : "";
        copyright = doc->GetSongCopyright() ? doc->GetSongCopyright() : "";
        comment = doc->GetSongComment();
    }

    duration = player->GetDuration(player->GetCurrentSubtune());
    isOpen = true;
    return true;
}

void DnfamitrackerDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();
}

void DnfamitrackerDecoder::closeLocked() {
    if (channelScopeState) {
        channelScopeState->clear();
    }
    player.reset();
    title.clear();
    artist.clear();
    copyright.clear();
    comment.clear();
    duration = 0.0;
    isOpen = false;
}

int DnfamitrackerDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || !buffer || numFrames <= 0) {
        return 0;
    }

    int rendered = player->Render(buffer, numFrames);

    if (rendered < numFrames && repeatMode == 1) {
        player->Seek(0.0);
        int remaining = numFrames - rendered;
        int secondPass = player->Render(buffer + rendered * 2, remaining);
        rendered += secondPass;
    }

    updateScopeSnapshotLocked();

    return rendered;
}

void DnfamitrackerDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return;
    }
    player->Seek(std::max(0.0, seconds));
}

double DnfamitrackerDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return duration;
}

int DnfamitrackerDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sampleRate;
}

int DnfamitrackerDecoder::getBitDepth() {
    return 32;
}

std::string DnfamitrackerDecoder::getBitDepthLabel() {
    return "32-bit float";
}

int DnfamitrackerDecoder::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return player ? player->GetChannelCount() : 0;
}

int DnfamitrackerDecoder::getChannelCount() {
    return 2;
}

std::string DnfamitrackerDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string DnfamitrackerDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string DnfamitrackerDecoder::getCopyright() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return copyright;
}

std::string DnfamitrackerDecoder::getComment() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return comment;
}

int DnfamitrackerDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return player ? player->GetSubtuneCount() : 1;
}

int DnfamitrackerDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return player ? player->GetCurrentSubtune() : 0;
}

bool DnfamitrackerDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || index < 0 || index >= player->GetSubtuneCount()) {
        return false;
    }
    bool result = player->SelectSubtune(index);
    if (result) {
        duration = player->GetDuration(index);
    }
    return result;
}

std::string DnfamitrackerDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || index < 0 || index >= player->GetSubtuneCount()) {
        return "";
    }
    CFTMDocument* doc = player->GetDocument();
    if (doc) {
        CPatternData* track = doc->GetTrack(static_cast<unsigned int>(index));
        if (track) {
            std::string t = track->GetTitle();
            if (!t.empty()) {
                return t;
            }
        }
    }
    if (player->GetSubtuneCount() <= 1) {
        return title;
    }
    return "Track " + std::to_string(index + 1);
}

double DnfamitrackerDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || index < 0 || index >= player->GetSubtuneCount()) {
        return 0.0;
    }
    return player->GetDuration(index);
}

void DnfamitrackerDecoder::setOutputSampleRate(int rate) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (rate > 0 && rate != sampleRate) {
        sampleRate = rate;
        if (player) {
            player->SetupSound(sampleRate);
        }
    }
}

void DnfamitrackerDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
}

int DnfamitrackerDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

double DnfamitrackerDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return 0.0;
    }
    return player->GetCurrentTimeSeconds();
}

AudioDecoder::TimelineMode DnfamitrackerDecoder::getTimelineMode() const {
    return TimelineMode::Discontinuous;
}

int DnfamitrackerDecoder::getPlaybackCapabilities() const {
    return PLAYBACK_CAP_SEEK |
           PLAYBACK_CAP_RELIABLE_DURATION |
           PLAYBACK_CAP_LIVE_REPEAT_MODE |
           PLAYBACK_CAP_DIRECT_SEEK |
           PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
}

std::vector<int32_t> DnfamitrackerDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return {};
    }
    // The library DPCM branch calls GetInstrument() without a range check,
    // so query every other channel individually and report DPCM as empty.
    const int count = std::min(maxChannels, player->GetChannelCount());
    if (count <= 0) {
        return {};
    }
    std::vector<int32_t> result(static_cast<size_t>(count) * 10, -1);
    for (int i = 0; i < count; ++i) {
        const char* name = player->GetChannelName(i);
        if (name && std::string(name) == "DPCM") {
            continue;
        }
        player->GetChannelDisplayState(i, &result[static_cast<size_t>(i) * 10], 10);
    }
    return result;
}

std::vector<std::string> DnfamitrackerDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return {};
    }
    const int count = player->GetChannelCount();
    std::vector<std::string> names;
    names.reserve(static_cast<size_t>(count));
    for (int i = 0; i < count; ++i) {
        const char* name = player->GetChannelName(i);
        names.push_back((name && name[0] != '\0') ? name : ("Ch " + std::to_string(i + 1)));
    }
    return names;
}

std::vector<uint8_t> DnfamitrackerDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return {};
    }
    return std::vector<uint8_t>(static_cast<size_t>(player->GetChannelCount()), 1u);
}

void DnfamitrackerDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || channelIndex < 0 || channelIndex >= player->GetChannelCount()) {
        return;
    }
    player->SetChannelMuted(channelIndex, enabled);
}

bool DnfamitrackerDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || channelIndex < 0 || channelIndex >= player->GetChannelCount()) {
        return false;
    }
    return player->IsChannelMuted(channelIndex);
}

void DnfamitrackerDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return;
    }
    const int count = player->GetChannelCount();
    for (int i = 0; i < count; ++i) {
        player->SetChannelMuted(i, false);
    }
}

std::string DnfamitrackerDecoder::getCoreStringInfo(const char* name) {
    if (!name) return "";
    std::string key(name);
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (key == "formatName") {
        if (player && player->GetDocument()) {
            return player->GetDocument()->IsDnModule() ? "Dn-FamiTracker Module" : "FamiTracker Module";
        }
        return "FamiTracker Module";
    }
    if (key == "systemName" || key == "hardwareName") {
        if (player && player->GetDocument()) {
            return player->GetDocument()->GetMachine() == NTSC ? "NES / Famicom (NTSC)" : "NES / Famicom (PAL)";
        }
        return "NES / Famicom";
    }
    if (key == "comment") {
        return comment;
    }
    return "";
}

int DnfamitrackerDecoder::getCoreIntInfo(const char* name, int fallback) {
    if (!name) return fallback;
    std::string key(name);
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (key == "currentFrame") {
        return player ? player->GetCurrentFrame() : fallback;
    }
    if (key == "currentRow") {
        return player ? player->GetCurrentRow() : fallback;
    }
    if (key == "songChannelCount") {
        return player ? player->GetChannelCount() : fallback;
    }
    if (key == "fileVersion") {
        if (player && player->GetDocument()) {
            return static_cast<int>(player->GetDocument()->GetFileVersion());
        }
    }
    return fallback;
}

void DnfamitrackerDecoder::updateScopeSnapshotLocked() {
    if (!player || !channelScopeState) {
        return;
    }
    const int count = player->GetChannelCount();
    if (count <= 0) {
        return;
    }

    const auto nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();

    if (!channelScopeState->tryBeginCapture(nowNs, count)) {
        return;
    }

    const size_t rawSize = static_cast<size_t>(count) * ChannelScopeSharedState::kMaxSamples;
    if (scopeRawScratch.size() != rawSize) {
        scopeRawScratch.assign(rawSize, 0.0f);
    }
    if (scopeVuScratch.size() != static_cast<size_t>(count)) {
        scopeVuScratch.assign(static_cast<size_t>(count), 0.0f);
    }

    for (int ch = 0; ch < count; ++ch) {
        player->GetChannelWaveform(ch, &scopeRawScratch[static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples], ChannelScopeSharedState::kMaxSamples);
        scopeVuScratch[static_cast<size_t>(ch)] = player->GetChannelVU(ch);
    }

    // Chip-level waves peak near full scale; match the other scope feeds.
    for (float& sample : scopeRawScratch) {
        sample *= kDnfamitrackerScopeGain;
    }

    channelScopeState->publish(scopeRawScratch, scopeVuScratch, count, ++channelScopeSourceSerial, true);
}
