#ifndef SILICONPLAYER_AYFLYDECODER_H
#define SILICONPLAYER_AYFLYDECODER_H

#include "AudioDecoder.h"
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

class AyflyDecoder : public AudioDecoder {
public:
    AyflyDecoder();
    ~AyflyDecoder() override;

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
    int getSubtuneCount() const override;
    int getCurrentSubtuneIndex() const override;
    bool selectSubtune(int index) override;
    std::string getSubtuneTitle(int index) override;
    std::string getSubtuneArtist(int index) override;
    double getSubtuneDurationSeconds(int index) override;
    std::string getTitle() override;
    std::string getArtist() override;
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

    std::string getCoreStringInfo(const char* name) override;
    int getCoreIntInfo(const char* name, int fallback) override;
    void setOption(const char* name, const char* value) override;

    const char* getName() const override { return "ayfly"; }

private:
    // ayfly.h stays in the .cpp: UNICODE/_UNICODE decides its types and must
    // match the library build, so only that translation unit includes it.
    void* song = nullptr;
    mutable std::mutex decodeMutex;

    std::vector<char> fileBuffer;
    std::vector<unsigned char> mixBuffer;

    double duration = 0.0;
    int requestedSampleRateHz = 48000;
    int renderSampleRate = 48000;
    int sourceChannels = 0;
    int repeatMode = 0;
    bool ended = false;
    std::string title;
    std::string artist;
    std::string formatName;
    int subsongCount = 1;
    int currentSubsong = 0;
    std::vector<std::string> subsongTitles;
    std::vector<double> subsongDurations;
    // Core options; -1 (chip/mix) and 0 (int freq) mean auto: keep the
    // song/library value. tickRate converts tick-based times to seconds.
    int oversample = 1;
    int chipType = -1;
    int mixType = -1;
    int intFreqHz = 0;
    double tickRate = 50.0;

    void closeLocked();
    bool createSongLocked(const char* path);
    void applyOptionsLocked();
    void refreshTickRateLocked();
    static bool onSongElapsed(void* arg);
};

#endif //SILICONPLAYER_AYFLYDECODER_H
