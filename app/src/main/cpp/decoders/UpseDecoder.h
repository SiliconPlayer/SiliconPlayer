#ifndef SILICONPLAYER_UPSEDECODER_H
#define SILICONPLAYER_UPSEDECODER_H

#include "AudioDecoder.h"
#include "../ChannelScopeSharedState.h"
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

extern "C" {
#include <upse/upse.h>
#include <upse/Neill/spu.h>
#include <upse/upse-scope-tap.h>
}

class UpseDecoder : public AudioDecoder {
public:
    UpseDecoder();
    ~UpseDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComposer() override;
    std::string getGenre() override;
    std::string getCopyright() override;
    std::string getYear() override;
    std::string getComment() override;
    std::string getGameName();
    std::string getLengthTag();
    std::string getFadeTag();
    void setOutputSampleRate(int sampleRate) override;
    void setOption(const char* name, const char* value) override;
    int getOptionApplyPolicy(const char* name) const override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }
    std::string getCoreStringInfo(const char* name) override;
    std::vector<std::string> getToggleChannelNames() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override { return channelScopeState; }
    std::vector<int32_t> getChannelScopeTextState(int maxChannels) override;

    const char* getName() const override { return "libupse"; }
    static std::vector<std::string> getSupportedExtensions();

private:
    mutable std::mutex decodeMutex;
    upse_module_t* module = nullptr;
    std::vector<int16_t> surplusSamples;
    bool isOpen = false;
    int repeatMode = 0;
    bool reverbEnabled = true;
    static constexpr int kScopeVoices = 24;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState;
    std::vector<float> scopeRingRaw;
    std::vector<float> scopePublishRaw;
    std::vector<float> scopePublishVu;
    int64_t channelScopeLastReadNs = 0;
    int scopeRingWritePos = 0;
    int scopeRingSamples = 0;
    bool scopeCaptureEnabled = false;
    uint64_t channelScopeSourceSerial = 0;
    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;
    int scopeTapChunkFrames = 0;
    int sampleRate = 44100;
    int channels = 2;
    int bitDepth = 16;
    bool durationReliable = false;
    double durationSeconds = 180.0;
    int64_t renderedFrames = 0;
    std::string sourcePath;
    std::string title;
    std::string artist;
    std::string composer;
    std::string genre;
    std::string gameName;
    std::string copyrightText;
    std::string year;
    std::string comment;
    std::string lengthTag;
    std::string fadeTag;

    void closeInternalLocked();
    bool openInternalLocked(const char* path);
    void applyReverbLocked();
    void applyScopeTapLocked();
    void applyVoiceMutesLocked();
    void resetChannelScopeLocked();
    void appendScopeTapLocked(int voice, const int16_t* samples, int frames);
    void publishScopeSnapshotLocked();
    static void scopeTapCallback(int voice, const short* samples, int frames, void* user);
};

#endif // SILICONPLAYER_UPSEDECODER_H
