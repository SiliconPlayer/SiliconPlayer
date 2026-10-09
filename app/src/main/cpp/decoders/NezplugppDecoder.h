#ifndef SILICONPLAYER_NEZPLUGPPDECODER_H
#define SILICONPLAYER_NEZPLUGPPDECODER_H

#include "AudioDecoder.h"
#include "../ChannelScopeSharedState.h"
#include <cstdint>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

struct NEZPLAY_TAG;

class NezplugppDecoder : public AudioDecoder {
public:
    NezplugppDecoder();
    ~NezplugppDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getChannelCount() override;
    int getDisplayChannelCount() override;
    int getSubtuneCount() const override;
    int getCurrentSubtuneIndex() const override;
    bool selectSubtune(int index) override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getCopyright() override;
    std::string getComment() override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    std::string getCoreStringInfo(const char* name) override;
    void setOption(const char* name, const char* value) override;
    std::vector<std::string> getToggleChannelNames() override;
    std::vector<uint8_t> getToggleChannelAvailability() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override;
    std::vector<int32_t> getChannelScopeTextState(int maxChannels) override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }

    const char* getName() const override { return "NEZplug++"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    NEZPLAY_TAG* player = nullptr;
    std::vector<int16_t> pcmScratch;
    bool isOpen = false;
    int repeatMode = 0;
    int sampleRate = 44100;
    int channels = 2;
    int bitDepth = 16;
    bool durationReliable = false;
    double durationSeconds = 180.0;
    int64_t renderedFrames = 0;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string copyrightText;
    std::string comment;
    std::string formatName;
    int songVoiceCount = 0;
    std::string subtuneInfo;
    int filterType = 0;
    unsigned songExtDevice = 0;
    int sgcSysType = -1;
    std::vector<std::string> toggleChannelNames;
    std::vector<int> toggleChannelDevIds;
    std::vector<bool> toggleChannelMuted;
    std::unordered_map<int, bool> channelMuteByDev;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState;
    std::vector<float> scopeRingRaw;
    std::vector<float> scopePublishRaw;
    std::vector<float> scopePublishVu;
    std::vector<float> scopeDcEstimate;
    std::unordered_map<int, int> scopeDevToIndex;
    std::vector<int> scopeTapSeen;
    int scopeVoices = 0;
    int scopeRingWritePos = 0;
    int scopeRingSamples = 0;
    int scopeTapChunkFrames = 0;
    int scopeTapsThisBlock = 0;
    int scopeTapGeneration = 0;
    int64_t channelScopeLastReadNs = 0;
    bool scopeCaptureEnabled = false;
    // Code toggle for the scope DC blocker. Defaults on.
    void setScopeDcBlockEnabled(bool enabled) { scopeDcBlockEnabled = enabled; }
    bool scopeDcBlockEnabled = true;
    uint64_t channelScopeSourceSerial = 0;
    std::unordered_map<std::string, float> volumeTrimDb = {
        {"KSS", 0.0f}, {"NSF", 8.0f}, {"GBS", 10.0f}, {"GBR", 10.0f},
        {"HES", 0.0f}, {"SGC", 0.0f}, {"NSD", 0.0f}, {"AY", 0.0f},
    };
    void parseSongFormatLocked(const uint8_t* data, size_t size);
    void buildToggleChannelsLocked();
    void resetChannelScopeLocked();
    void appendScopeTapLocked(int devId, const float* samples, int frames);
    void publishScopeSnapshotLocked();
    static void scopeTapCallback(int devId, const float* samples, int frames, void* user);
    void applyChannelMuteLocked(int channelIndex);

    void closeInternalLocked();
    bool selectSongLocked(unsigned int songNo);
};

#endif // SILICONPLAYER_NEZPLUGPPDECODER_H
