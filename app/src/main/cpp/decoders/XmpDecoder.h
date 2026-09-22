#ifndef SILICONPLAYER_XMPDECODER_H
#define SILICONPLAYER_XMPDECODER_H

#include "AudioDecoder.h"
#include "../ChannelScopeSharedState.h"
#include <xmp.h>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
#include <cstdint>

class XmpDecoder : public AudioDecoder {
public:
    XmpDecoder();
    ~XmpDecoder() override;

    bool open(const char* path) override;
    void close() override;
    int read(float* buffer, int numFrames) override;
    void seek(double seconds) override;
    double getDuration() override;
    int getSampleRate() override;
    int getBitDepth() override;
    std::string getBitDepthLabel() override;
    int getChannelCount() override;
    int getSourceChannelCount() override;
    std::string getTitle() override;
    std::string getArtist() override;
    std::string getComment() override;
    void setOutputSampleRate(int sampleRate) override;
    int getPlaybackCapabilities() const override {
        return PLAYBACK_CAP_SEEK |
               PLAYBACK_CAP_RELIABLE_DURATION |
               PLAYBACK_CAP_LIVE_REPEAT_MODE |
               PLAYBACK_CAP_CUSTOM_SAMPLE_RATE |
               PLAYBACK_CAP_DIRECT_SEEK;
    }
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override {
        return REPEAT_CAP_TRACK | REPEAT_CAP_LOOP_POINT;
    }
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::Discontinuous; }
    void setOption(const char* name, const char* value) override;
    std::vector<std::string> getToggleChannelNames() override;
    void setToggleChannelMuted(int channelIndex, bool enabled) override;
    bool getToggleChannelMuted(int channelIndex) const override;
    void clearToggleChannelMutes() override;
    std::string getCoreStringInfo(const char* name) override;
    std::shared_ptr<ChannelScopeSharedState> getChannelScopeSharedState() const override {
        return channelScopeState;
    }
    std::vector<int32_t> getChannelScopeTextState(int maxChannels) override;

    const char* getName() const override { return "libxmp"; }

private:
    xmp_context context = nullptr;
    mutable std::mutex decodeMutex;

    std::vector<char> fileBuffer;
    std::vector<int32_t> mixBuffer;

    double duration = 0.0;
    int renderSampleRate = 48000;
    int moduleChannels = 0;
    int moduleInstruments = 0;
    int moduleSamples = 0;
    int repeatMode = 0;
    bool ended = false;
    int interpolationMode = XMP_INTERP_LINEAR;
    int stereoSeparationPercent = 100;
    int amigaStereoSeparationPercent = 100;
    bool amigaMixingEnabled = false;
    bool isAmigaModule = false;
    int readEventType = XMP_READ_EVENT_MOD;
    std::string title;
    std::string moduleType;
    std::string comment;
    std::string instrumentNames;
    std::string sampleNames;

    std::vector<std::string> toggleChannelNames;
    std::vector<bool> toggleChannelMuted;

    std::shared_ptr<ChannelScopeSharedState> channelScopeState =
            std::make_shared<ChannelScopeSharedState>();
    uint64_t channelScopeSourceSerial = 0;
    int64_t channelScopeLastReadNs = 0;

    void closeLocked();
    bool startPlayerLocked();
    void applyOptionsLocked();
    void captureChannelScopeSnapshotLocked();
};

#endif //SILICONPLAYER_XMPDECODER_H
