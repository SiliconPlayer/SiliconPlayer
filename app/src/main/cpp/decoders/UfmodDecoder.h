#ifndef SILICONPLAYER_UFMODDECODER_H
#define SILICONPLAYER_UFMODDECODER_H

#include "AudioDecoder.h"
#include "../ChannelScopeSharedState.h"
#include <ufmod.h>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

class UfmodDecoder : public AudioDecoder {
public:
    UfmodDecoder() = default;
    ~UfmodDecoder() override;

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
    int getSourceChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    void setOutputSampleRate(int sampleRate) override;
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override {
        return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
    }
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::Discontinuous; }
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK | PLAYBACK_CAP_CUSTOM_SAMPLE_RATE;
    }
    void setOption(const char* name, const char* value) override;
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override { return channelScopeState; }
    std::string getCoreStringInfo(const char* name) override;
    int getCoreIntInfo(const char* name, int fallback = 0) override;

    const char* getName() const override { return "uFMOD"; }

private:
    ufmod_t* context = nullptr;
    mutable std::mutex decodeMutex;
    std::vector<char> fileData;
    std::vector<int16_t> pcmBuffer;
    std::string title;
    int sampleRate = 48000;
    int moduleChannels = 0;
    int moduleOrders = 0;
    int moduleBpm = 0;
    int moduleSpeed = 0;
    double estimatedDuration = 0.0;
    double timelineBaseSeconds = 0.0;
    double timelineAudioBaseSeconds = 0.0;
    int lastOrder = -1;
    int lastRow = -1;
    int repeatMode = 0;
    unsigned int quirkFlags = 0;
    std::shared_ptr<ChannelScopeSharedState> channelScopeState = std::make_shared<ChannelScopeSharedState>();
    std::vector<float> scopeRingRaw;
    std::vector<float> scopeScratch;
    std::vector<float> scopePublishRaw;
    std::vector<float> scopePublishVu;
    int scopeRingChannels = 0;
    int scopeRingWritePos = 0;
    int scopeRingSamples = 0;
    int64_t channelScopeLastReadNs = 0;
    bool ended = false;
    bool timelineAnchored = false;

    void updateTimelinePositionLocked();
};

#endif // SILICONPLAYER_UFMODDECODER_H
