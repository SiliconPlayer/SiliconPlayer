#ifndef SILICONPLAYER_UFMODDECODER_H
#define SILICONPLAYER_UFMODDECODER_H

#include "AudioDecoder.h"
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
    int getRepeatModeCapabilities() const override { return REPEAT_CAP_TRACK; }
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::Discontinuous; }
    void setOption(const char* name, const char* value) override;
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
    int repeatMode = 0;
    bool ended = false;
};

#endif // SILICONPLAYER_UFMODDECODER_H
