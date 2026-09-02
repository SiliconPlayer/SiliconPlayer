#pragma once

#include <atomic>
#include <cstdint>
#include <mutex>
#include <string>
#include <thread>
#include <vector>
#include <libusb.h>

namespace siliconplayer::usb {

enum class StartError : int {
    Ok = 0,
    NoDevice,
    NoMatchingAlt,
    ClaimInterfaceFailed,
    SetAltFailed,
    SetSampleRateFailed,
    IsoPumpAllocFailed,
    IsoPumpSubmitFailed
};

struct ClockRateRange {
    uint8_t clockId = 0;
    uint32_t minHz = 0;
    uint32_t maxHz = 0;
    uint32_t resHz = 0;
};

struct StreamFormat {
    int sampleRateHz = 0;
    int bitsPerSample = 0;
    int bytesPerSample = 0;
    int channels = 0;
    uint8_t interfaceNumber = 0;
    uint8_t altSetting = 0;
    uint8_t endpointAddress = 0;
    uint16_t maxPacketSize = 0;
    uint8_t clockSourceId = 0;
    uint8_t controlInterfaceNum = 0;
    bool isHighSpeed = true;
    uint8_t bInterval = 1;
    uint16_t uacVersion = 0x0200;
    uint8_t feedbackEndpointAddress = 0;
    uint16_t feedbackMaxPacketSize = 0;
    uint8_t feedbackInterval = 0;
    std::vector<uint8_t> candidateClockIds;
};

class UacDriver {
public:
    UacDriver();
    ~UacDriver();
    UacDriver(const UacDriver&) = delete;
    UacDriver& operator=(const UacDriver&) = delete;

    bool ensureContext();
    bool open(int fileDescriptor);
    void close();
    bool isOpen() const { return device_ != nullptr; }

    bool start(int sampleRateHz, int bitsPerSample, int channels);
    void stop();
    bool isStreaming() const { return streaming_.load(std::memory_order_acquire); }
    void flushRing();

    bool isStreamingFormat(int sampleRate, int bitsPerSample, int channels) const;
    int writePcm(const uint8_t* data, int frames);
    int writableFrames() const;

    long playedFrames() const { return playedFrames_.load(std::memory_order_acquire); }
    long writtenFrames() const { return writtenFrames_.load(std::memory_order_acquire); }

    const StreamFormat& currentFormat() const { return format_; }
    StartError lastError() const { return lastError_.load(std::memory_order_acquire); }
    std::string lastErrorDetail() const;
    std::vector<ClockRateRange> supportedRates() const;

    void setVolumeScale(float scale) {
        volumeScale_.store(std::clamp(scale, 0.0f, 1.0f), std::memory_order_release);
    }
    float volumeScale() const {
        return volumeScale_.load(std::memory_order_acquire);
    }

private:
    bool selectAltSetting(int sampleRateHz, int bitsPerSample, int channels, StreamFormat* outFmt);
    bool setSampleRate(uint32_t hz);
    void captureRangeForClock(uint8_t clockId);

    bool startIsoPump();
    void stopIsoPump();

    static void LIBUSB_CALL onIsoTrampoline(libusb_transfer* xfr);
    void onIso(libusb_transfer* xfr);

    static void LIBUSB_CALL onFeedbackTrampoline(libusb_transfer* xfr);
    void onFeedback(libusb_transfer* xfr);

    int drainRing(uint8_t* dst, int bytes);

    std::vector<uint8_t> ring_;
    size_t ringMask_ = 0;
    std::atomic<size_t> ringHead_{0};
    std::atomic<size_t> ringTail_{0};

    mutable std::mutex mutex_;
    libusb_context* ctx_ = nullptr;
    libusb_device_handle* device_ = nullptr;
    int fd_ = -1;
    std::atomic<bool> contextReady_{false};

    StreamFormat format_;
    std::atomic<bool> streaming_{false};
    std::atomic<bool> stopRequested_{false};
    bool interfaceClaimed_ = false;

    std::vector<libusb_transfer*> transfers_;
    std::vector<std::vector<uint8_t>> transferBuffers_;
    std::vector<libusb_transfer*> feedbackTransfers_;
    std::vector<std::vector<uint8_t>> feedbackBuffers_;
    std::atomic<int> inflight_{0};

    std::atomic<long> writtenFrames_{0};
    std::atomic<long> playedFrames_{0};
    std::thread eventThread_;

    std::atomic<uint32_t> framesPerUframe_q16_{0};
    uint32_t nominalStep_q16_ = 0;
    int microframesPerSec_ = 8000;
    int maxFramesPerPacket_ = 0;
    uint32_t fracAccumulator_q16_ = 0;

    std::atomic<StartError> lastError_{StartError::Ok};
    mutable std::mutex errorMutex_;
    std::string lastErrorDetail_;
    std::vector<ClockRateRange> supportedRates_;
    std::atomic<float> volumeScale_{1.0f};
};

UacDriver& getUacDriverInstance();

} // namespace siliconplayer::usb
