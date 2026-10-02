#include "DnfamitrackerDecoder.h"

#include <algorithm>
#include <chrono>
#include <cmath>

constexpr float kDnfamitrackerScopeGain = 0.5f;
constexpr float kDnfamitrackerScopeDcFollow = 0.0025f;

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

    refreshTimelineLocked();
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
    scopeDcEstimate.clear();
    title.clear();
    artist.clear();
    copyright.clear();
    comment.clear();
    duration = 0.0;
    durationReliable = false;
    loopStartSeconds = 0.0;
    loopLengthSeconds = 0.0;
    isOpen = false;
}

int DnfamitrackerDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player || !buffer || numFrames <= 0) {
        return 0;
    }

    const int mode = normalizeRepeatMode(repeatMode);
    const double restartSeconds =
            (mode == 2 && loopLengthSeconds > 0.0) ? loopStartSeconds : 0.0;
    if (durationReliable && duration > 0.0) {
        double positionSeconds = player->GetCurrentTimeSeconds();
        if (positionSeconds >= duration) {
            if (mode == 0) {
                return 0;
            }
            seekPlayerLocked(restartSeconds);
            positionSeconds = player->GetCurrentTimeSeconds();
        }
        const double quantumSeconds =
                sampleRate > 0 ? static_cast<double>(numFrames) / sampleRate : 0.0;
        const double remainingSeconds = duration - positionSeconds;
        if (mode != 0 && quantumSeconds > 0.0 && remainingSeconds < quantumSeconds) {
            const int firstFrames =
                    std::max(0, static_cast<int>(remainingSeconds * sampleRate));
            int rendered = 0;
            if (firstFrames > 0) {
                rendered = player->Render(buffer, firstFrames);
            }
            seekPlayerLocked(restartSeconds);
            const int restFrames = numFrames - firstFrames;
            if (restFrames > 0) {
                rendered += player->Render(buffer + rendered * 2, restFrames);
            }
            updateScopeSnapshotLocked();
            return rendered;
        }
    }

    int rendered = player->Render(buffer, numFrames);

    if (rendered < numFrames && mode != 0) {
        seekPlayerLocked(restartSeconds);
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
    double targetSeconds = std::max(0.0, seconds);
    if (durationReliable && duration > 0.0) {
        if (normalizeRepeatMode(repeatMode) == 2 && loopLengthSeconds > 0.0 &&
            targetSeconds > duration) {
            targetSeconds =
                    loopStartSeconds + std::fmod(targetSeconds - duration, loopLengthSeconds);
        } else {
            targetSeconds = std::min(targetSeconds, duration);
        }
    }
    seekPlayerLocked(targetSeconds);
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
        refreshTimelineLocked();
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
    const double positionSeconds = player->GetCurrentTimeSeconds();
    if (durationReliable && duration > 0.0 && positionSeconds >= duration) {
        if (normalizeRepeatMode(repeatMode) == 2 && loopLengthSeconds > 0.0) {
            return loopStartSeconds +
                    std::fmod(positionSeconds - duration, loopLengthSeconds);
        }
        return duration;
    }
    return positionSeconds;
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

int DnfamitrackerDecoder::normalizeRepeatMode(int mode) {
    if (mode < 0 || mode > 3) {
        return 0;
    }
    return mode;
}

void DnfamitrackerDecoder::seekPlayerLocked(double seconds) {
    if (seekExactEnabled) {
        player->Seek(seconds);
    } else {
        player->SeekFast(seconds);
    }
}

void DnfamitrackerDecoder::refreshTimelineLocked() {
    durationReliable = false;
    loopStartSeconds = 0.0;
    loopLengthSeconds = 0.0;
    if (!player || !player->GetDocument()) {
        return;
    }
    const int track = player->GetCurrentSubtune();
    duration = player->GetDuration(track);
    if (duration <= 0.0) {
        duration = 0.0;
        return;
    }
    durationReliable = true;
    const double withLoop = player->GetDocument()->GetStandardLength(track, 1);
    loopLengthSeconds = std::max(0.0, withLoop - duration);
    loopStartSeconds = std::clamp(duration - loopLengthSeconds, 0.0, duration);
}

std::vector<int32_t> DnfamitrackerDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!isOpen || !player) {
        return {};
    }
    const int count = std::min(maxChannels, player->GetChannelCount());
    if (count <= 0) {
        return {};
    }
    std::vector<int32_t> result(static_cast<size_t>(count) * 10, -1);
    for (int i = 0; i < count; ++i) {
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
    if (key == "instrumentNames") {
        return getInstrumentNamesInfoLocked();
    }
    if (key == "sampleNames") {
        return getSampleNamesInfoLocked();
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

std::string DnfamitrackerDecoder::getInstrumentNamesInfoLocked() {
    if (!player || !player->GetDocument()) {
        return "";
    }
    CFTMDocument* doc = player->GetDocument();
    std::string names;
    for (int i = 0; i < CInstrumentManager::MAX_INSTRUMENTS; ++i) {
        if (!names.empty()) {
            names.push_back('\n');
        }
        names.append(std::to_string(i + 1));
        names.append(". ");
        names.append(doc->GetInstrumentName(static_cast<unsigned int>(i)));
    }
    return names;
}

std::string DnfamitrackerDecoder::getSampleNamesInfoLocked() {
    if (!player || !player->GetDocument()) {
        return "";
    }
    CFTMDocument* doc = player->GetDocument();
    std::string names;
    const unsigned int count = doc->GetSampleCount();
    for (unsigned int i = 0; i < count; ++i) {
        if (!names.empty()) {
            names.push_back('\n');
        }
        names.append(std::to_string(i + 1));
        names.append(". ");
        names.append(doc->GetSampleName(i));
    }
    return names;
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
    if (scopeDcEstimate.size() != static_cast<size_t>(count)) {
        scopeDcEstimate.assign(static_cast<size_t>(count), 0.0f);
    }

    for (int ch = 0; ch < count; ++ch) {
        player->GetChannelWaveform(ch, &scopeRawScratch[static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples], ChannelScopeSharedState::kMaxSamples);
        scopeVuScratch[static_cast<size_t>(ch)] = player->GetChannelVU(ch);
    }

    // Taps arrive raw (unipolar, held levels); center them with a persistent
    // one-pole DC blocker like GME's blip high-pass. Per-chunk mean removal
    // would re-offset held steps and hop the baseline between chunks.
    // Chip-level waves peak near full scale; match the other scope feeds.
    for (int ch = 0; ch < count; ++ch) {
        float* samples = &scopeRawScratch[static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples];
        if (!scopeDcBlockEnabled) {
            for (int i = 0; i < ChannelScopeSharedState::kMaxSamples; ++i) {
                samples[i] *= kDnfamitrackerScopeGain;
            }
            continue;
        }
        float& dc = scopeDcEstimate[static_cast<size_t>(ch)];
        for (int i = 0; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            dc += (samples[i] - dc) * kDnfamitrackerScopeDcFollow;
            samples[i] = (samples[i] - dc) * kDnfamitrackerScopeGain;
        }
    }

    channelScopeState->publish(scopeRawScratch, scopeVuScratch, count, ++channelScopeSourceSerial, true);
}
