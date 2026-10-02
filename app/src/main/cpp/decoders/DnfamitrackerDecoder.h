#ifndef SILICONPLAYER_DNFAMITRACKERDECODER_H
#define SILICONPLAYER_DNFAMITRACKERDECODER_H

#include "AudioDecoder.h"
#include "../ChannelScopeSharedState.h"

#include <FTMPlayer.h>

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

class DnfamitrackerDecoder : public AudioDecoder {
public:
    DnfamitrackerDecoder();
    ~DnfamitrackerDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getDisplayChannelCount() override;
    int getChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getCopyright() override;
    std::string getComment() override;
    int getSubtuneCount() const override;
    int getCurrentSubtuneIndex() const override;
    bool selectSubtune(int index) override;
    std::string getSubtuneTitle(int index) override;
    double getSubtuneDurationSeconds(int index) override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override;
    int getPlaybackCapabilities() const override;

    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override { return channelScopeState; }
    std::vector<int32_t> getChannelScopeTextState(int maxChannels) override;
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;

    std::string getCoreStringInfo(const char* name) override;
    int getCoreIntInfo(const char* name, int fallback = 0) override;

    const char* getName() const override { return "libdnfamitracker"; }

private:
    void closeLocked();
    void updateScopeSnapshotLocked();

    std::unique_ptr<CFTMPlayer> player;
    mutable std::mutex decodeMutex;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState;
    std::vector<float> scopeRawScratch;
    std::vector<float> scopeVuScratch;
    uint32_t channelScopeSourceSerial = 0;
    int sampleRate = 48000;
    int repeatMode = 0;
    double duration = 0.0;
    std::string title;
    std::string artist;
    std::string copyright;
    std::string comment;
    bool isOpen = false;
};

#endif // SILICONPLAYER_DNFAMITRACKERDECODER_H
