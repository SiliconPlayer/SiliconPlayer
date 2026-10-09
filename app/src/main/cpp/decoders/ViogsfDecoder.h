#ifndef SILICONPLAYER_VIOGSFDECODER_H
#define SILICONPLAYER_VIOGSFDECODER_H

#include "AudioDecoder.h"
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

struct viogsf_s;

class ViogsfDecoder : public AudioDecoder {
public:
    ViogsfDecoder();
    ~ViogsfDecoder() override;

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
    void setRepeatMode(int mode) override;
    int getRepeatModeCapabilities() const override;
    int getPlaybackCapabilities() const override;
    int getFixedSampleRateHz() const override;
    double getPlaybackPositionSeconds() override;
    TimelineMode getTimelineMode() const override { return TimelineMode::ContinuousLinear; }
    std::string getCoreStringInfo(const char* name) override;

    const char* getName() const override { return "viogsf"; }
    static std::vector<std::string> getSupportedExtensions();

    struct MetadataState {
        std::string title;
        std::string artist;
        std::string composer;
        std::string genre;
        std::string game;
        std::string copyright;
        std::string year;
        std::string comment;
        std::string lengthTag;
        std::string fadeTag;
        bool hasLength = false;
        bool hasFade = false;
        unsigned long lengthMs = 0;
        unsigned long fadeMs = 0;
    };

private:
    mutable std::mutex decodeMutex;
    viogsf_s* player = nullptr;
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
    std::string composer;
    std::string genre;
    std::string gameName;
    std::string copyrightText;
    std::string year;
    std::string comment;
    std::string lengthTag;
    std::string fadeTag;

    void closeInternalLocked();
};

#endif // SILICONPLAYER_VIOGSFDECODER_H
