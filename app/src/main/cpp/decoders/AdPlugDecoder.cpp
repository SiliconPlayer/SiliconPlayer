#include "AdPlugDecoder.h"

#include <adplug/adplug.h>
#include <adplug/emuopl.h>
#include <adplug/kemuopl.h>
#include <adplug/nemuopl.h>
#include <adplug/player.h>
#include <adplug/wemuopl.h>

#include <algorithm>
#define SP_LOG_MODULE "AdPlugDecoder"
#include "../SiliconLog.h"
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <filesystem>

#include "../ChannelScopeSharedState.h"

namespace {
constexpr float kAdPlugScopeGain = 1.5f;
class TrackingOplProxy final : public Copl {
public:
    enum class Engine : int {
        DosBox = 0,
        KenSilverman = 1,
        Mame = 2,
        Nuked = 3
    };

    static std::unique_ptr<TrackingOplProxy> create(Engine engine, int sampleRateHz) {
        std::unique_ptr<Copl> backend;
        bool promoteDualOnSecondChip = false;
        bool mirrorMonoWhenSingleChip = false;

        switch (engine) {
            case Engine::DosBox:
                backend = std::make_unique<CWemuopl>(sampleRateHz, true, true);
                break;
            case Engine::KenSilverman:
                backend = std::make_unique<CKemuopl>(sampleRateHz, true, true);
                mirrorMonoWhenSingleChip = true;
                break;
            case Engine::Nuked:
                backend = std::make_unique<CNemuopl>(sampleRateHz);
                break;
            case Engine::Mame:
            default:
                backend = std::make_unique<CEmuopl>(sampleRateHz, true, true);
                // CEmuopl defaults to dual mode, which sends single-chip tunes
                // to one side. Promote to dual only once chip 1 is actually used.
                promoteDualOnSecondChip = true;
                break;
        }

        return std::unique_ptr<TrackingOplProxy>(
                new TrackingOplProxy(std::move(backend), promoteDualOnSecondChip, mirrorMonoWhenSingleChip, engine));
    }

    void write(int reg, int val) override {
        if (!backend_) {
            return;
        }
        const int chip = backend_->getchip();
        const auto mapping = mapTotalLevelRegister(reg);
        if (mapping.valid && chip >= 0 && chip < kChipCount) {
            rawTotalLevel_[chip][mapping.channel][mapping.slot] = val;
            rawTotalLevelValid_[chip][mapping.channel][mapping.slot] = true;
            const bool muted = channelMuted_[chip][mapping.channel];
            const int effective = muted ? forceMuteTotalLevel(val) : val;
            backend_->write(reg, effective);
            return;
        }
        backend_->write(reg, val);
    }

    void setchip(int n) override {
        if (!backend_) {
            return;
        }
        backend_->setchip(n);
        currChip = backend_->getchip();
        if (n == 1) {
            dualChipActive_ = true;
            if (promoteDualOnSecondChip_) {
                if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
                    emuOpl->settype(TYPE_DUAL_OPL2);
                    currType = TYPE_DUAL_OPL2;
                }
            }
        }
    }

    int getchip() override {
        if (!backend_) {
            return currChip;
        }
        return backend_->getchip();
    }

    void update(short* buf, int samples) override {
        update_and_scope(buf, samples, nullptr);
    }

    void update_and_scope(short* buf, int samples, short* scope_buf) override {
        if (!backend_ || !buf || samples <= 0) {
            return;
        }
        backend_->update_and_scope(buf, samples, scope_buf);
        if (mirrorMonoWhenSingleChip_ && !dualChipActive_) {
            for (int i = 0; i < samples; ++i) {
                buf[(i * 2) + 1] = buf[i * 2];
            }
        }
    }

    void init() override {
        if (!backend_) {
            return;
        }
        backend_->init();
        currChip = backend_->getchip();
        currType = backend_->gettype();
        dualChipActive_ = false;
        std::memset(rawTotalLevel_, 0, sizeof(rawTotalLevel_));
        std::memset(rawTotalLevelValid_, 0, sizeof(rawTotalLevelValid_));
        std::memset(channelMuted_, 0, sizeof(channelMuted_));
        if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
            emuOpl->settype(TYPE_OPL2);
            currType = TYPE_OPL2;
        }
    }

    int getVoiceCount() const {
        return dualChipActive_ ? 18 : 9;
    }

    bool isScopeInterleaved() const {
        return engine_ == Engine::Nuked;
    }

    int getScopeStridePerFrame() const {
        return engine_ == Engine::Nuked ? 18 : 0;
    }

    bool hasAnyMutedVoice() const {
        for (int c = 0; c < kChipCount; ++c) {
            for (int ch = 0; ch < kChannelCountPerChip; ++ch) {
                if (channelMuted_[c][ch]) return true;
            }
        }
        return false;
    }

    void setVoiceMuted(int voiceIndex, bool muted) {
        if (voiceIndex < 0 || voiceIndex >= 18) {
            return;
        }
        const int chip = voiceIndex / 9;
        const int channel = voiceIndex % 9;
        if (channelMuted_[chip][channel] == muted) {
            return;
        }
        channelMuted_[chip][channel] = muted;
        applyChannelTotalLevel(chip, channel);
    }

    bool getVoiceMuted(int voiceIndex) const {
        if (voiceIndex < 0 || voiceIndex >= 18) {
            return false;
        }
        const int chip = voiceIndex / 9;
        const int channel = voiceIndex % 9;
        return channelMuted_[chip][channel];
    }

private:
    static constexpr int kChipCount = 2;
    static constexpr int kChannelCountPerChip = 9;
    static constexpr int kOpTable[kChannelCountPerChip] = { 0, 1, 2, 8, 9, 10, 16, 17, 18 };

    struct RegMapping {
        bool valid = false;
        int channel = 0;
        int slot = 0;
    };

    static RegMapping mapTotalLevelRegister(int reg) {
        for (int channel = 0; channel < kChannelCountPerChip; ++channel) {
            const int op = kOpTable[channel];
            if (reg == (0x40 + op)) {
                return { true, channel, 0 };
            }
            if (reg == (0x43 + op)) {
                return { true, channel, 1 };
            }
        }
        return {};
    }

    static int forceMuteTotalLevel(int rawValue) {
        return (rawValue & 0xC0) | 0x3F;
    }

    void applyChannelTotalLevel(int chip, int channel) {
        if (!backend_ ||
            chip < 0 || chip >= kChipCount ||
            channel < 0 || channel >= kChannelCountPerChip) {
            return;
        }
        const int previousChip = getchip();
        setchip(chip);

        const int op = kOpTable[channel];
        for (int slot = 0; slot < 2; ++slot) {
            if (!rawTotalLevelValid_[chip][channel][slot]) {
                continue;
            }
            const int raw = rawTotalLevel_[chip][channel][slot];
            const int reg = (slot == 0) ? (0x40 + op) : (0x43 + op);
            const int effective = channelMuted_[chip][channel] ? forceMuteTotalLevel(raw) : raw;
            backend_->write(reg, effective);
        }

        setchip(previousChip);
    }

    explicit TrackingOplProxy(
            std::unique_ptr<Copl> backend,
            bool promoteDualOnSecondChip,
            bool mirrorMonoWhenSingleChip,
            Engine engine)
        : backend_(std::move(backend)),
          promoteDualOnSecondChip_(promoteDualOnSecondChip),
          mirrorMonoWhenSingleChip_(mirrorMonoWhenSingleChip),
          engine_(engine) {
        if (backend_) {
            currChip = backend_->getchip();
            currType = backend_->gettype();
            if (auto* emuOpl = dynamic_cast<CEmuopl*>(backend_.get())) {
                emuOpl->settype(TYPE_OPL2);
                currType = TYPE_OPL2;
            }
        }
    }

    std::unique_ptr<Copl> backend_;
    Engine engine_ = Engine::Mame;
    bool promoteDualOnSecondChip_ = false;
    bool mirrorMonoWhenSingleChip_ = false;
    bool dualChipActive_ = false;
    int rawTotalLevel_[kChipCount][kChannelCountPerChip][2] {};
    bool rawTotalLevelValid_[kChipCount][kChannelCountPerChip][2] {};
    bool channelMuted_[kChipCount][kChannelCountPerChip] {};
};

int normalizeAdlibCore(int value) {
    if (value < 0 || value > 3) {
        return 0;
    }
    return value;
}

std::string safeString(const std::string& value) {
    return value;
}
}

AdPlugDecoder::AdPlugDecoder()
    : channelScopeState(std::make_shared<ChannelScopeSharedState>()) {}

AdPlugDecoder::~AdPlugDecoder() {
    close();
}

bool AdPlugDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();

    if (!path) {
        return false;
    }

    sourcePath = path;
    const auto engine = static_cast<TrackingOplProxy::Engine>(normalizeAdlibCore(adlibCore));
    opl = TrackingOplProxy::create(engine, sampleRateHz);
    player.reset(CAdPlug::factory(sourcePath, opl.get()));
    if (!player) {
        LOGE("CAdPlug::factory failed for file: %s", sourcePath.c_str());
        closeInternalLocked();
        return false;
    }
    player->setEndlessLoopMode(repeatMode.load() == 2);

    title = safeString(player->gettitle());
    if (title.empty()) {
        title = std::filesystem::path(sourcePath).stem().string();
    }
    artist = safeString(player->getauthor());
    composer = artist;
    genre = safeString(player->gettype());

    subtuneCount = std::max(1u, player->getsubsongs());
    currentSubtuneIndex = std::clamp(static_cast<int>(player->getsubsong()), 0, subtuneCount - 1);

    const bool endlessLoop = repeatMode.load() == 2;
    // songlength() replays each track; loop mode would run every subsong
    // to the 10-minute cap.
    player->setEndlessLoopMode(false);
    subtuneDurations.assign(static_cast<size_t>(subtuneCount), 0.0);
    for (int i = 0; i < subtuneCount; ++i) {
        const unsigned long durationMs = player->songlength(i);
        subtuneDurations[static_cast<size_t>(i)] =
                durationMs > 0 ? static_cast<double>(durationMs) / 1000.0 : 0.0;
    }
    player->setEndlessLoopMode(endlessLoop);
    player->rewind(currentSubtuneIndex);
    durationReliable = subtuneDurations[currentSubtuneIndex] > 0.0;
    durationSeconds = subtuneDurations[currentSubtuneIndex];

    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;
    pcmScratch.clear();
    syncToggleChannelsLocked();
    applyToggleMutesLocked();
    return true;
}

void AdPlugDecoder::closeInternalLocked() {
    player.reset();
    opl.reset();
    pcmScratch.clear();
    sourcePath.clear();
    title.clear();
    artist.clear();
    composer.clear();
    genre.clear();
    subtuneCount = 1;
    currentSubtuneIndex = 0;
    remainingTickFrames = 0;
    durationReliable = false;
    durationSeconds = 0.0;
    subtuneDurations.clear();
    playbackPositionSeconds = 0.0;
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    scopeRingRaw.clear();
    scopeRingChannels = 0;
    scopeRingWritePos = 0;
    scopeRingSamples = 0;
    scopePublishRaw.clear();
    scopePublishVu.clear();
    channelScopeLastReadNs = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
}

void AdPlugDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeInternalLocked();
}

int AdPlugDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);

    if (!player || !opl || !buffer || numFrames <= 0) {
        return 0;
    }
    syncToggleChannelsLocked();

    const int mode = repeatMode.load();
    const bool hasReliableDuration = durationSeconds >= 1.0;
    const bool gateVirtualEof =
            (mode == 0) ||
            (mode == 3) ||
            (mode == 1 && std::max(1, subtuneCount) > 1);

    if (hasReliableDuration && mode != 2 && playbackPositionSeconds >= durationSeconds) {
        if (mode == 1 && std::max(1, subtuneCount) == 1) {
            player->rewind(currentSubtuneIndex);
            playbackPositionSeconds = 0.0;
            remainingTickFrames = 0;
            reachedEnd = false;
        } else if (gateVirtualEof) {
            return 0;
        }
    }

    auto refreshToTickFrames = [this]() {
        const float refreshHz = player->getrefresh();
        const double safeRefreshHz =
                (std::isfinite(refreshHz) && refreshHz > 0.0f) ? static_cast<double>(refreshHz) : 70.0;
        return std::max(
                1,
                static_cast<int>(std::lround(static_cast<double>(sampleRateHz) / safeRefreshHz))
        );
    };

    int framesWritten = 0;
    int loopRecoveries = 0;
    constexpr int kMaxLoopRecoveriesPerRead = 512;

    while (framesWritten < numFrames) {
        if (remainingTickFrames <= 0) {
            const bool hasNextTick = player->update();
            if (!hasNextTick) {
                if (mode == 2) {
                    bool recovered = false;
                    for (int attempt = 0; attempt < 2 && !recovered; ++attempt) {
                        if (attempt == 0) {
                            player->rewind(currentSubtuneIndex);
                        } else {
                            player->rewind();
                        }
                        remainingTickFrames = 0;
                        reachedEnd = false;

                        if (player->update()) {
                            remainingTickFrames = refreshToTickFrames();
                            recovered = true;
                            loopRecoveries = 0;
                        }
                    }

                    if (recovered) {
                        continue;
                    }

                    if (loopRecoveries < kMaxLoopRecoveriesPerRead) {
                        ++loopRecoveries;
                        continue;
                    }
                }
                reachedEnd = true;
                break;
            }
            loopRecoveries = 0;
            remainingTickFrames = refreshToTickFrames();
        }

        const int framesLeft = numFrames - framesWritten;
        const int chunkFrames = std::min(framesLeft, remainingTickFrames);
        const int chunkSamples = chunkFrames * channels;

        if (static_cast<int>(pcmScratch.size()) < chunkSamples) {
            pcmScratch.resize(chunkSamples);
        }

        if (channelScopeState && scopeScratch.size() < chunkFrames * 18) {
            scopeScratch.resize(chunkFrames * 18);
        }

        if (channelScopeState) {
            std::fill(scopeScratch.begin(), scopeScratch.end(), 0);
        }

        opl->update_and_scope(pcmScratch.data(), chunkFrames, channelScopeState ? scopeScratch.data() : nullptr);
        if (channelScopeState) {
            appendScopeChunkLocked(chunkFrames);
        }
        for (int sample = 0; sample < chunkSamples; ++sample) {
            buffer[(framesWritten * channels) + sample] =
                    static_cast<float>(pcmScratch[sample]) / 32768.0f;
        }

        remainingTickFrames -= chunkFrames;
        framesWritten += chunkFrames;
        playbackPositionSeconds += static_cast<double>(chunkFrames) / static_cast<double>(sampleRateHz);
    }

    if (framesWritten > 0 && channelScopeState) {
        channelScopeLastReadNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
    }

    return framesWritten;
}

void AdPlugDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return;
    }

    double targetSeconds = std::max(0.0, seconds);
    if (durationSeconds > 0.0) {
        targetSeconds = std::min(targetSeconds, durationSeconds);
    }
    const auto targetMs = static_cast<unsigned long>(std::llround(targetSeconds * 1000.0));
    player->seek(targetMs);
    playbackPositionSeconds = targetSeconds;
    remainingTickFrames = 0;
    reachedEnd = false;
}

double AdPlugDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return durationSeconds;
}

int AdPlugDecoder::getSampleRate() {
    return sampleRateHz;
}

int AdPlugDecoder::getBitDepth() {
    return bitDepth;
}

std::string AdPlugDecoder::getBitDepthLabel() {
    return "16 bit";
}

int AdPlugDecoder::getDisplayChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    auto* trackingOpl = dynamic_cast<TrackingOplProxy*>(opl.get());
    if (trackingOpl) {
        const int voiceCount = trackingOpl->getVoiceCount();
        if (voiceCount > 0) return voiceCount;
    }
    return !toggleChannelNames.empty() ? static_cast<int>(toggleChannelNames.size()) : channels;
}

int AdPlugDecoder::getChannelCount() {
    return channels;
}

int AdPlugDecoder::getSubtuneCount() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return subtuneCount;
}

int AdPlugDecoder::getCurrentSubtuneIndex() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return currentSubtuneIndex;
}

bool AdPlugDecoder::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return false;
    }
    player->rewind(index);
    currentSubtuneIndex = index;
    remainingTickFrames = 0;
    playbackPositionSeconds = 0.0;
    reachedEnd = false;

    const double duration = index < static_cast<int>(subtuneDurations.size())
                                    ? subtuneDurations[static_cast<size_t>(index)]
                                    : 0.0;
    durationReliable = duration > 0.0;
    durationSeconds = duration;
    return true;
}

std::string AdPlugDecoder::getSubtuneTitle(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return title;
}

std::string AdPlugDecoder::getSubtuneArtist(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (index < 0 || index >= subtuneCount) {
        return "";
    }
    return artist;
}

double AdPlugDecoder::getSubtuneDurationSeconds(int index) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player || index < 0 || index >= subtuneCount) {
        return 0.0;
    }
    return index < static_cast<int>(subtuneDurations.size())
                   ? subtuneDurations[static_cast<size_t>(index)]
                   : 0.0;
}

std::string AdPlugDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string AdPlugDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

std::string AdPlugDecoder::getComposer() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return composer;
}

std::string AdPlugDecoder::getGenre() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return genre;
}

std::string AdPlugDecoder::getDescription() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    return safeString(player->getdesc());
}

int AdPlugDecoder::getPatternCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpatterns());
}

int AdPlugDecoder::getCurrentPatternInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getpattern());
}

int AdPlugDecoder::getOrderCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorders());
}

int AdPlugDecoder::getCurrentOrderInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getorder());
}

int AdPlugDecoder::getCurrentRowInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getrow());
}

int AdPlugDecoder::getCurrentSpeedInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getspeed());
}

int AdPlugDecoder::getInstrumentCountInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return 0;
    }
    return static_cast<int>(player->getinstruments());
}

std::string AdPlugDecoder::getInstrumentNamesInfo() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!player) {
        return "";
    }
    const int instrumentCount = static_cast<int>(player->getinstruments());
    if (instrumentCount <= 0) {
        return "";
    }

    std::string names;
    names.reserve(static_cast<size_t>(instrumentCount) * 10);
    for (int i = 0; i < instrumentCount; ++i) {
        const std::string instrumentName = safeString(player->getinstrument(static_cast<unsigned int>(i)));
        if (instrumentName.empty()) {
            continue;
        }
        if (!names.empty()) {
            names += "\n";
        }
        names += std::to_string(i + 1);
        names += ". ";
        names += instrumentName;
    }
    return names;
}

std::vector<std::string> AdPlugDecoder::getToggleChannelNames() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return toggleChannelNames;
}

std::vector<uint8_t> AdPlugDecoder::getToggleChannelAvailability() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    return std::vector<uint8_t>(toggleChannelNames.size(), 1u);
}

void AdPlugDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    syncToggleChannelsLocked();
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return;
    }
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    applyToggleMutesLocked();
}

bool AdPlugDecoder::getToggleChannelMuted(int channelIndex) const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) {
        return false;
    }
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void AdPlugDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (toggleChannelMuted.empty()) {
        return;
    }
    std::fill(toggleChannelMuted.begin(), toggleChannelMuted.end(), false);
    applyToggleMutesLocked();
}

void AdPlugDecoder::syncToggleChannelsLocked() {
    auto* trackingOpl = dynamic_cast<TrackingOplProxy*>(opl.get());
    const int voiceCount = trackingOpl ? trackingOpl->getVoiceCount() : 9;

    if (voiceCount == static_cast<int>(toggleChannelNames.size()) &&
        voiceCount == static_cast<int>(toggleChannelMuted.size())) {
        return;
    }

    std::vector<bool> previousMuted = toggleChannelMuted;
    toggleChannelNames.clear();
    toggleChannelNames.reserve(static_cast<size_t>(voiceCount));
    toggleChannelMuted.assign(static_cast<size_t>(voiceCount), false);

    for (int i = 0; i < voiceCount; ++i) {
        if (voiceCount > 9) {
            const int chipIndex = i / 9;
            const int chipChannel = (i % 9) + 1;
            const char chipLabel = static_cast<char>('A' + chipIndex);
            toggleChannelNames.push_back(
                    "Chip " + std::string(1, chipLabel) + " Ch " + std::to_string(chipChannel)
            );
        } else {
            toggleChannelNames.push_back("OPL Ch " + std::to_string(i + 1));
        }
        if (i < static_cast<int>(previousMuted.size())) {
            toggleChannelMuted[static_cast<size_t>(i)] = previousMuted[static_cast<size_t>(i)];
        }
    }

    applyToggleMutesLocked();
}

void AdPlugDecoder::applyToggleMutesLocked() {
    auto* trackingOpl = dynamic_cast<TrackingOplProxy*>(opl.get());
    if (!trackingOpl) {
        return;
    }
    const bool hasAnyMuted = std::any_of(
            toggleChannelMuted.begin(),
            toggleChannelMuted.end(),
            [](bool m) { return m; }
    );
    if (!hasAnyMuted && !trackingOpl->hasAnyMutedVoice()) {
        return;
    }
    for (int i = 0; i < static_cast<int>(toggleChannelMuted.size()); ++i) {
        trackingOpl->setVoiceMuted(i, toggleChannelMuted[static_cast<size_t>(i)]);
    }
}

void AdPlugDecoder::setRepeatMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 3) ? mode : 0;
    repeatMode.store(normalized);
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (player) {
        player->setEndlessLoopMode(normalized == 2);
    }
}

int AdPlugDecoder::getRepeatModeCapabilities() const {
    return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
}

int AdPlugDecoder::getPlaybackCapabilities() const {
    std::lock_guard<std::mutex> lock(decodeMutex);
    int capabilities = PLAYBACK_CAP_SEEK | PLAYBACK_CAP_LIVE_REPEAT_MODE | PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    if (durationReliable) {
        capabilities |= PLAYBACK_CAP_RELIABLE_DURATION;
    }
    // Seek is supported but is decode-forward (not direct/random access).
    return capabilities;
}

void AdPlugDecoder::setOutputSampleRate(int sampleRate) {
    const int normalized = (sampleRate > 0) ? std::clamp(sampleRate, 8000, 192000) : 44100;
    std::lock_guard<std::mutex> lock(decodeMutex);
    sampleRateHz = normalized;
}

void AdPlugDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) {
        return;
    }
    if (std::strcmp(name, "adplug.opl_engine") != 0) {
        return;
    }

    const int parsed = std::atoi(value);
    std::lock_guard<std::mutex> lock(decodeMutex);
    adlibCore = normalizeAdlibCore(parsed);
}

int AdPlugDecoder::getOptionApplyPolicy(const char* name) const {
    if (!name) {
        return OPTION_APPLY_LIVE;
    }
    if (std::strcmp(name, "adplug.opl_engine") == 0) {
        return OPTION_APPLY_REQUIRES_PLAYBACK_RESTART;
    }
    return OPTION_APPLY_LIVE;
}

int AdPlugDecoder::getFixedSampleRateHz() const {
    return 0;
}

double AdPlugDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (durationSeconds > 0.0 && repeatMode.load() != 2) {
        return std::min(playbackPositionSeconds, durationSeconds);
    }
    return playbackPositionSeconds;
}

AudioDecoder::TimelineMode AdPlugDecoder::getTimelineMode() const {
    return TimelineMode::ContinuousLinear;
}

std::string AdPlugDecoder::getCoreStringInfo(const char* name) {
    if (name == nullptr) return "";
    if (std::strcmp(name, "description") == 0) return getDescription();
    if (std::strcmp(name, "instrumentNames") == 0) return getInstrumentNamesInfo();
    return "";
}

int AdPlugDecoder::getCoreIntInfo(const char* name, int fallback) {
    if (name == nullptr) return fallback;
    if (std::strcmp(name, "patternCount") == 0) return getPatternCountInfo();
    if (std::strcmp(name, "currentPattern") == 0) return getCurrentPatternInfo();
    if (std::strcmp(name, "orderCount") == 0) return getOrderCountInfo();
    if (std::strcmp(name, "currentOrder") == 0) return getCurrentOrderInfo();
    if (std::strcmp(name, "currentRow") == 0) return getCurrentRowInfo();
    if (std::strcmp(name, "currentSpeed") == 0) return getCurrentSpeedInfo();
    if (std::strcmp(name, "instrumentCount") == 0) return getInstrumentCountInfo();
    return fallback;
}

std::vector<std::string> AdPlugDecoder::getSupportedExtensions() {
    // Do not touch CAdPlug::players during process startup:
    // decoder registration runs in global init, and AdPlug's own global
    // player list can still be in static-initialization-order limbo there.
    // Keep this list aligned with external/adplug/src/adplug.cpp (allplayers).
    static const std::vector<std::string> kExtensions = {
            "hsc", "sng", "imf", "wlf", "adlib", "a2m", "a2t", "xms",
            "bam", "cmf", "adl", "d00", "dfm", "hsp", "ksm", "mad",
            "mus", "mdy", "ims", "mdi", "mid", "sci", "laa", "mkj",
            "cff", "dmo", "s3m", "dtm", "mtk", "mtr", "rad", "rac",
            "raw", "sat", "sa2", "xad", "lds", "plx", "m", "rol",
            "xsm", "dro", "pis", "msc", "rix", "mkf", "jbm", "got",
            "vgm", "vgz", "sop", "hsq", "sqx", "sdb", "agd", "ha2"
    };
    return kExtensions;
}

std::shared_ptr<ChannelScopeSharedState> AdPlugDecoder::getChannelScopeSharedState() const {
    return channelScopeState;
}

std::vector<int32_t> AdPlugDecoder::getChannelScopeTextState(int /*maxChannels*/) {
    return {};
}

void AdPlugDecoder::appendScopeChunkLocked(int numFrames) {
    if (!channelScopeState || !opl || numFrames <= 0) {
        return;
    }
    auto* trackingProxy = dynamic_cast<TrackingOplProxy*>(opl.get());
    const int activeChannels = trackingProxy ? trackingProxy->getVoiceCount() : 18;
    const int prevRingWritePos = scopeRingWritePos;

    if (scopeRingChannels != activeChannels || scopeRingRaw.size() != static_cast<size_t>(activeChannels * ChannelScopeSharedState::kMaxSamples)) {
        scopeRingRaw.assign(static_cast<size_t>(activeChannels * ChannelScopeSharedState::kMaxSamples), 0.0f);
        scopeRingChannels = activeChannels;
        scopeRingWritePos = 0;
        scopeRingSamples = 0;
    }

    const bool isInterleaved = trackingProxy && trackingProxy->isScopeInterleaved();

    if (isInterleaved) {
        // Nuked OPL: interleaved format with 18 values per frame
        for (int frame = 0; frame < numFrames; ++frame) {
            for (int ch = 0; ch < activeChannels; ++ch) {
                scopeRingRaw[
                    static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(scopeRingWritePos)
                ] = std::clamp((static_cast<float>(scopeScratch[frame * 18 + ch]) / 32768.0f) * kAdPlugScopeGain, -1.0f, 1.0f);
            }
            scopeRingWritePos = (scopeRingWritePos + 1) % ChannelScopeSharedState::kMaxSamples;
            scopeRingSamples = std::min(scopeRingSamples + 1, ChannelScopeSharedState::kMaxSamples);
        }
    } else {
        // Planar engines (Mame, DosBox, KenSilverman): channels in sequence
        for (int frame = 0; frame < numFrames; ++frame) {
            const int dstIndex = (scopeRingWritePos + frame) % ChannelScopeSharedState::kMaxSamples;
            for (int ch = 0; ch < activeChannels; ++ch) {
                const int srcIndex = ch * numFrames + frame;
                scopeRingRaw[
                    static_cast<size_t>(ch) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(dstIndex)
                ] = std::clamp((static_cast<float>(scopeScratch[srcIndex]) / 32768.0f) * kAdPlugScopeGain, -1.0f, 1.0f);
            }
        }
        scopeRingWritePos = (scopeRingWritePos + numFrames) % ChannelScopeSharedState::kMaxSamples;
        scopeRingSamples = std::min(scopeRingSamples + numFrames, ChannelScopeSharedState::kMaxSamples);
    }

    publishScopeAppend(prevRingWritePos, numFrames);
}

// Rolling per-decode publish: only the appended span and a trailing VU
// window are touched, so decode cadence is never rate-limited.
void AdPlugDecoder::publishScopeAppend(int prevWritePos, int framesAppended) {
    if (!channelScopeState || scopeRingChannels <= 0 || scopeRingRaw.empty() ||
        framesAppended <= 0) {
        return;
    }
    if (static_cast<int>(scopeAppendFlat.size()) !=
        scopeRingChannels * framesAppended) {
        scopeAppendFlat.assign(
                static_cast<size_t>(scopeRingChannels) *
                        static_cast<size_t>(framesAppended), 0.0f);
    }
    std::vector<float>& vu = scopePublishVu;
    if (static_cast<int>(vu.size()) != scopeRingChannels) {
        vu.assign(static_cast<size_t>(scopeRingChannels), 0.0f);
    }
    const int trailing = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);
    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        const float* ringChannel =
                scopeRingRaw.data() + static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        float peak = 0.0f;
        for (int i = 1; i <= trailing && i <= ChannelScopeSharedState::kMaxSamples; ++i) {
            const int idx = (scopeRingWritePos - i + ChannelScopeSharedState::kMaxSamples) %
                            ChannelScopeSharedState::kMaxSamples;
            peak = std::max(peak, std::abs(ringChannel[idx]));
        }
        vu[static_cast<size_t>(channel)] = std::clamp(peak, 0.0f, 1.0f);
    }
    // Extract the appended span planar-compact from the modular ring.
    int spanWrite = ((prevWritePos % ChannelScopeSharedState::kMaxSamples) +
                     ChannelScopeSharedState::kMaxSamples) % ChannelScopeSharedState::kMaxSamples;
    for (int k = 0; k < framesAppended; ++k) {
        const int ringIdx = (spanWrite + k) % ChannelScopeSharedState::kMaxSamples;
        for (int channel = 0; channel < scopeRingChannels; ++channel) {
            scopeAppendFlat[static_cast<size_t>(channel) * static_cast<size_t>(framesAppended) +
                            static_cast<size_t>(k)] =
                    scopeRingRaw[
                            static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                            static_cast<size_t>(ringIdx)];
        }
    }
    channelScopeState->publishAppend(
            scopeAppendFlat.data(), scopeRingChannels, prevWritePos, framesAppended, vu.data(),
            ++channelScopeSourceSerial);
}

void AdPlugDecoder::publishScopeSnapshotLocked() {
    if (!channelScopeState || scopeRingChannels <= 0 || scopeRingRaw.empty() || scopeRingSamples <= 0) {
        if (channelScopeState) {
            channelScopeState->clear();
        }
        return;
    }

    const size_t totalSlots = static_cast<size_t>(scopeRingChannels) * ChannelScopeSharedState::kMaxSamples;
    if (scopePublishRaw.size() != totalSlots) {
        scopePublishRaw.assign(totalSlots, 0.0f);
    }
    if (scopePublishVu.size() != static_cast<size_t>(scopeRingChannels)) {
        scopePublishVu.assign(static_cast<size_t>(scopeRingChannels), 0.0f);
    }

    std::vector<float>& raw = scopePublishRaw;
    std::vector<float>& vu = scopePublishVu;
    const int filledSamples = std::clamp(scopeRingSamples, 0, ChannelScopeSharedState::kMaxSamples);
    const int zeroPrefix = ChannelScopeSharedState::kMaxSamples - filledSamples;
    const int trailingSamples = std::clamp(sampleRateHz > 0 ? sampleRateHz / 50 : 64, 64, 1024);

    for (int channel = 0; channel < scopeRingChannels; ++channel) {
        float* dst = raw.data() + static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples;
        for (int i = 0; i < filledSamples; ++i) {
            const int ringIndex =
                    (scopeRingWritePos - filledSamples + i + ChannelScopeSharedState::kMaxSamples) %
                    ChannelScopeSharedState::kMaxSamples;
            dst[zeroPrefix + i] = scopeRingRaw[
                    static_cast<size_t>(channel) * ChannelScopeSharedState::kMaxSamples +
                    static_cast<size_t>(ringIndex)
            ];
        }

        float peak = 0.0f;
        const int start = std::max(0, ChannelScopeSharedState::kMaxSamples - trailingSamples);
        for (int i = start; i < ChannelScopeSharedState::kMaxSamples; ++i) {
            peak = std::max(peak, std::abs(dst[i]));
        }
        vu[static_cast<size_t>(channel)] = std::clamp(peak, 0.0f, 1.0f);
    }

    channelScopeState->publish(raw, vu, scopeRingChannels, ++channelScopeSourceSerial, true);
}
