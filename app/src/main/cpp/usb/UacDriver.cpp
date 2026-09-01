#include "UacDriver.h"

#include <android/log.h>
#include <algorithm>
#include <cstring>

#define TAG "UacDriver"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace siliconplayer::usb {

namespace {

constexpr uint8_t USB_CLASS_AUDIO       = 0x01;
constexpr uint8_t SUBCLASS_AUDIOCONTROL = 0x01;
constexpr uint8_t SUBCLASS_AUDIOSTREAM  = 0x02;

constexpr uint8_t CS_INTERFACE          = 0x24;
constexpr uint8_t AC_HEADER             = 0x01;
constexpr uint8_t AC_INPUT_TERMINAL     = 0x02;
constexpr uint8_t AC_OUTPUT_TERMINAL    = 0x03;
constexpr uint8_t AC_CLOCK_SOURCE       = 0x0A;
constexpr uint8_t AC_CLOCK_SELECTOR     = 0x0B;
constexpr uint8_t AC_CLOCK_MULTIPLIER   = 0x0C;
constexpr uint8_t AS_GENERAL            = 0x01;
constexpr uint8_t AS_FORMAT_TYPE        = 0x02;
constexpr uint8_t FORMAT_TYPE_I         = 0x01;

constexpr uint8_t REQ_SET_CUR              = 0x01;
constexpr uint16_t CS_SAM_FREQ_CONTROL_SEL = 0x01;

constexpr int kNumTransfers = 4;
constexpr int kPacketsPerTransfer = 8;
constexpr size_t kRingBytes = 1u << 20;

inline size_t ringSize(size_t head, size_t tail) {
    return head - tail;
}

bool isClassDescriptor(const uint8_t* p, size_t remaining, uint8_t descType, uint8_t subtype) {
    if (remaining < 3) return false;
    return p[1] == descType && p[2] == subtype;
}

template<typename Cb>
void walkExtra(const uint8_t* extra, int extraLen, Cb&& cb) {
    int i = 0;
    while (i + 2 <= extraLen) {
        int len = extra[i];
        if (len < 2 || i + len > extraLen) break;
        if (cb(extra + i, len)) return;
        i += len;
    }
}

struct ErrorSink {
    std::atomic<StartError>* code;
    std::mutex* m;
    std::string* detail;
    void operator()(StartError c, std::string text) const {
        code->store(c, std::memory_order_release);
        std::lock_guard<std::mutex> lock(*m);
        *detail = std::move(text);
        LOGW("start error %d: %s", static_cast<int>(c), detail->c_str());
    }
};

} // namespace

UacDriver::UacDriver() {
    ring_.resize(kRingBytes);
    ringMask_ = kRingBytes - 1;
}

UacDriver::~UacDriver() {
    stop();
    close();
    std::lock_guard<std::mutex> lock(mutex_);
    if (ctx_) {
        libusb_exit(ctx_);
        ctx_ = nullptr;
    }
}

std::string UacDriver::lastErrorDetail() const {
    std::lock_guard<std::mutex> lock(errorMutex_);
    return lastErrorDetail_;
}

std::vector<ClockRateRange> UacDriver::supportedRates() const {
    std::lock_guard<std::mutex> lock(errorMutex_);
    return supportedRates_;
}

bool UacDriver::ensureContext() {
    if (contextReady_.load(std::memory_order_acquire)) return true;
    std::lock_guard<std::mutex> lock(mutex_);
    if (contextReady_.load(std::memory_order_relaxed)) return true;

    libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, nullptr);
    int rc = libusb_init(&ctx_);
    if (rc != LIBUSB_SUCCESS) {
        LOGE("libusb_init failed: %d", rc);
        ctx_ = nullptr;
        return false;
    }
    contextReady_.store(true, std::memory_order_release);
    return true;
}

bool UacDriver::open(int fileDescriptor) {
    if (!ensureContext()) return false;
    std::lock_guard<std::mutex> lock(mutex_);
    if (device_ != nullptr) {
        if (fd_ == fileDescriptor) return true;
        libusb_close(device_);
        device_ = nullptr;
        fd_ = -1;
    }
    libusb_device_handle* handle = nullptr;
    int rc = libusb_wrap_sys_device(ctx_, static_cast<intptr_t>(fileDescriptor), &handle);
    if (rc != LIBUSB_SUCCESS || handle == nullptr) {
        LOGE("libusb_wrap_sys_device(fd=%d) -> %d", fileDescriptor, rc);
        return false;
    }
    device_ = handle;
    fd_ = fileDescriptor;
    libusb_set_auto_detach_kernel_driver(device_, 1);
    return true;
}

void UacDriver::close() {
    stop();
    std::lock_guard<std::mutex> lock(mutex_);
    if (device_ != nullptr) {
        libusb_close(device_);
        device_ = nullptr;
        fd_ = -1;
        format_ = {};
    }
}

bool UacDriver::selectAltSetting(int sampleRateHz, int bitsPerSample, int channels, StreamFormat* outFmt) {
    libusb_device* dev = libusb_get_device(device_);
    libusb_config_descriptor* config = nullptr;
    int rc = libusb_get_active_config_descriptor(dev, &config);
    if (rc != LIBUSB_SUCCESS) {
        rc = libusb_get_config_descriptor(dev, 0, &config);
    }
    if (rc != LIBUSB_SUCCESS || !config) return false;

    uint8_t controlIface = 0xFF;
    uint16_t uacVersion = 0x0200;
    bool foundControl = false;

    struct TermClock { uint8_t termId; uint8_t clockId; };
    std::vector<TermClock> terminals;
    struct ClockEntity { uint8_t id; uint8_t subtype; uint8_t baseId; };
    std::vector<ClockEntity> clockEntities;

    for (uint8_t i = 0; i < config->bNumInterfaces; ++i) {
        const libusb_interface& iface = config->interface[i];
        for (int a = 0; a < iface.num_altsetting; ++a) {
            const libusb_interface_descriptor& alt = iface.altsetting[a];
            if (alt.bInterfaceClass != USB_CLASS_AUDIO || alt.bInterfaceSubClass != SUBCLASS_AUDIOCONTROL) continue;
            controlIface = alt.bInterfaceNumber;
            walkExtra(alt.extra, alt.extra_length, [&](const uint8_t* p, int len) {
                if (isClassDescriptor(p, len, CS_INTERFACE, AC_HEADER) && len >= 5) {
                    uacVersion = static_cast<uint16_t>(p[3]) | (static_cast<uint16_t>(p[4]) << 8);
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AC_CLOCK_SOURCE) && len >= 4) {
                    clockEntities.push_back({p[3], AC_CLOCK_SOURCE, 0});
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AC_CLOCK_SELECTOR) && len >= 6) {
                    clockEntities.push_back({p[3], AC_CLOCK_SELECTOR, p[5]});
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AC_CLOCK_MULTIPLIER) && len >= 5) {
                    clockEntities.push_back({p[3], AC_CLOCK_MULTIPLIER, p[4]});
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AC_INPUT_TERMINAL) && len >= 8) {
                    terminals.push_back({p[3], p[7]});
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AC_OUTPUT_TERMINAL) && len >= 9) {
                    terminals.push_back({p[3], p[8]});
                }
                return false;
            });
            foundControl = true;
            break;
        }
        if (foundControl) break;
    }
    if (!foundControl) {
        libusb_free_config_descriptor(config);
        return false;
    }

    bool found = false;
    for (uint8_t i = 0; i < config->bNumInterfaces && !found; ++i) {
        const libusb_interface& iface = config->interface[i];
        for (int a = 0; a < iface.num_altsetting; ++a) {
            const libusb_interface_descriptor& alt = iface.altsetting[a];
            if (alt.bInterfaceClass != USB_CLASS_AUDIO || alt.bInterfaceSubClass != SUBCLASS_AUDIOSTREAM) continue;
            if (alt.bAlternateSetting == 0) continue;

            int altChannels = 0, altBits = 0, altSubslot = 0;
            uint8_t altTerminalLink = 0;
            bool rateSupported = (uacVersion >= 0x0200);

            walkExtra(alt.extra, alt.extra_length, [&](const uint8_t* p, int len) {
                if (isClassDescriptor(p, len, CS_INTERFACE, AS_GENERAL)) {
                    if (uacVersion >= 0x0200 && len >= 16) {
                        altTerminalLink = p[3];
                        altChannels = p[10];
                    }
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AS_FORMAT_TYPE) && len >= 4 && p[3] == FORMAT_TYPE_I) {
                    if (uacVersion >= 0x0200) {
                        if (len >= 6) {
                            altSubslot = p[4];
                            altBits = p[5];
                        }
                    } else if (len >= 7) {
                        altChannels = p[4];
                        altSubslot = p[5];
                        altBits = p[6];
                        if (len >= 8 && altChannels == channels && altBits == bitsPerSample) {
                            int kind = p[7];
                            if (kind == 0 && len >= 14) {
                                auto rd24 = [](const uint8_t* q) {
                                    return static_cast<uint32_t>(q[0]) | (static_cast<uint32_t>(q[1]) << 8) | (static_cast<uint32_t>(q[2]) << 16);
                                };
                                uint32_t lo = rd24(p + 8);
                                uint32_t hi = rd24(p + 11);
                                rateSupported = (static_cast<uint32_t>(sampleRateHz) >= lo && static_cast<uint32_t>(sampleRateHz) <= hi);
                                std::lock_guard<std::mutex> elock(errorMutex_);
                                bool dup = false;
                                for (const auto& e : supportedRates_) {
                                    if (e.minHz == lo && e.maxHz == hi) { dup = true; break; }
                                }
                                if (!dup) supportedRates_.push_back({0, lo, hi, 0});
                            } else if (kind > 0) {
                                rateSupported = false;
                                std::lock_guard<std::mutex> elock(errorMutex_);
                                for (int k = 0; k < kind; ++k) {
                                    int off = 8 + k * 3;
                                    if (off + 3 > len) break;
                                    uint32_t hz = static_cast<uint32_t>(p[off]) | (static_cast<uint32_t>(p[off + 1]) << 8) | (static_cast<uint32_t>(p[off + 2]) << 16);
                                    if (static_cast<int>(hz) == sampleRateHz) rateSupported = true;
                                    bool dup = false;
                                    for (const auto& e : supportedRates_) {
                                        if (e.minHz == hz && e.maxHz == hz) { dup = true; break; }
                                    }
                                    if (!dup) supportedRates_.push_back({0, hz, hz, 0});
                                }
                            }
                        }
                    }
                }
                return false;
            });

            if (altChannels != channels || altBits != bitsPerSample || !rateSupported) continue;

            const libusb_endpoint_descriptor* iso = nullptr;
            const libusb_endpoint_descriptor* feedback = nullptr;
            for (int e = 0; e < alt.bNumEndpoints; ++e) {
                const libusb_endpoint_descriptor& ep = alt.endpoint[e];
                if ((ep.bmAttributes & 0x03) != LIBUSB_TRANSFER_TYPE_ISOCHRONOUS) continue;
                bool isIn = (ep.bEndpointAddress & 0x80) != 0;
                uint8_t usage = (ep.bmAttributes >> 4) & 0x03;
                if (!isIn && usage == 0x00 && !iso) iso = &ep;
                else if (isIn && usage == 0x01 && !feedback) feedback = &ep;
            }
            if (!iso) continue;

            bool isHs = libusb_get_device_speed(dev) >= LIBUSB_SPEED_HIGH;
            int microframesPerInterval = isHs ? (1 << (iso->bInterval > 0 ? iso->bInterval - 1 : 0)) : iso->bInterval;
            int microframesPerSecond = isHs ? 8000 : 1000;
            int packetsPerSec = microframesPerInterval > 0 ? microframesPerSecond / microframesPerInterval : microframesPerSecond;
            int frameStride = (altSubslot ? altSubslot : bitsPerSample / 8) * channels;
            int reqBytesPerPacket = ((sampleRateHz + packetsPerSec - 1) / packetsPerSec + 1) * frameStride;
            int actualMps = iso->wMaxPacketSize & 0x07FF;
            int extraTransactions = ((iso->wMaxPacketSize >> 11) & 0x3) + 1;
            int realMps = actualMps * extraTransactions;
            if (reqBytesPerPacket > realMps) continue;

            outFmt->sampleRateHz = sampleRateHz;
            outFmt->bitsPerSample = bitsPerSample;
            outFmt->bytesPerSample = altSubslot ? altSubslot : bitsPerSample / 8;
            outFmt->channels = channels;
            outFmt->interfaceNumber = alt.bInterfaceNumber;
            outFmt->altSetting = alt.bAlternateSetting;
            outFmt->endpointAddress = iso->bEndpointAddress;
            outFmt->maxPacketSize = iso->wMaxPacketSize;

            uint8_t resolvedClock = 0;
            for (const auto& tc : terminals) {
                if (tc.termId == altTerminalLink) { resolvedClock = tc.clockId; break; }
            }
            for (int hop = 0; hop < 4 && resolvedClock != 0; ++hop) {
                const ClockEntity* ent = nullptr;
                for (const auto& ce : clockEntities) {
                    if (ce.id == resolvedClock) { ent = &ce; break; }
                }
                if (!ent) break;
                if (ent->subtype == AC_CLOCK_SOURCE) break;
                if (ent->baseId == 0) break;
                resolvedClock = ent->baseId;
            }
            if (resolvedClock == 0) {
                for (const auto& ce : clockEntities) {
                    if (ce.subtype == AC_CLOCK_SOURCE) { resolvedClock = ce.id; break; }
                }
            }
            if (resolvedClock == 0 && !clockEntities.empty()) {
                resolvedClock = clockEntities.front().id;
            }

            outFmt->clockSourceId = resolvedClock;
            outFmt->controlInterfaceNum = controlIface;
            outFmt->candidateClockIds.clear();
            for (const auto& ce : clockEntities) {
                if (ce.subtype == AC_CLOCK_SOURCE) outFmt->candidateClockIds.push_back(ce.id);
            }
            for (const auto& ce : clockEntities) {
                if (ce.subtype != AC_CLOCK_SOURCE) outFmt->candidateClockIds.push_back(ce.id);
            }

            outFmt->isHighSpeed = isHs;
            outFmt->bInterval = iso->bInterval;
            outFmt->uacVersion = uacVersion;
            if (feedback) {
                outFmt->feedbackEndpointAddress = feedback->bEndpointAddress;
                outFmt->feedbackMaxPacketSize = feedback->wMaxPacketSize;
                outFmt->feedbackInterval = feedback->bInterval;
            }
            found = true;
            break;
        }
    }

    libusb_free_config_descriptor(config);
    return found;
}

void UacDriver::captureRangeForClock(uint8_t clockId) {
    if (clockId == 0 || !device_) return;
    uint8_t rng[256] = {0};
    int gr = libusb_control_transfer(
        device_, 0xA1, 0x02,
        static_cast<uint16_t>(CS_SAM_FREQ_CONTROL_SEL << 8),
        static_cast<uint16_t>((clockId << 8) | format_.controlInterfaceNum),
        rng, sizeof(rng), 1000);
    if (gr < 2) return;
    int n = rng[0] | (rng[1] << 8);
    std::lock_guard<std::mutex> elock(errorMutex_);
    for (int i = 0; i < n && (2 + (i + 1) * 12) <= gr; ++i) {
        const uint8_t* t = rng + 2 + i * 12;
        uint32_t mn = uint32_t(t[0]) | (uint32_t(t[1]) << 8) | (uint32_t(t[2]) << 16) | (uint32_t(t[3]) << 24);
        uint32_t mx = uint32_t(t[4]) | (uint32_t(t[5]) << 8) | (uint32_t(t[6]) << 16) | (uint32_t(t[7]) << 24);
        uint32_t res = uint32_t(t[8]) | (uint32_t(t[9]) << 8) | (uint32_t(t[10]) << 16) | (uint32_t(t[11]) << 24);
        bool dup = false;
        for (const auto& e : supportedRates_) {
            if (e.minHz == mn && e.maxHz == mx && e.resHz == res) { dup = true; break; }
        }
        if (!dup) supportedRates_.push_back({clockId, mn, mx, res});
    }
}

bool UacDriver::setSampleRate(uint32_t hz) {
    if (format_.uacVersion >= 0x0200) {
        std::vector<uint8_t> tryIds;
        if (format_.clockSourceId != 0) tryIds.push_back(format_.clockSourceId);
        for (uint8_t id : format_.candidateClockIds) {
            if (id != 0 && id != format_.clockSourceId) tryIds.push_back(id);
        }
        if (tryIds.empty()) return true;

        uint8_t data[4] = {
            static_cast<uint8_t>(hz & 0xFF),
            static_cast<uint8_t>((hz >> 8) & 0xFF),
            static_cast<uint8_t>((hz >> 16) & 0xFF),
            static_cast<uint8_t>((hz >> 24) & 0xFF),
        };
        int rc = -1;
        uint8_t winningId = 0;
        for (uint8_t id : tryIds) {
            int r = libusb_control_transfer(
                device_, 0x21, REQ_SET_CUR,
                static_cast<uint16_t>(CS_SAM_FREQ_CONTROL_SEL << 8),
                static_cast<uint16_t>((id << 8) | format_.controlInterfaceNum),
                data, 4, 1000);
            if (r == 4) { rc = r; winningId = id; break; }
        }
        if (rc == 4) {
            if (winningId != format_.clockSourceId) format_.clockSourceId = winningId;
            captureRangeForClock(winningId);
            return true;
        }

        for (uint8_t id : tryIds) {
            uint8_t cur[4] = {0};
            int gc = libusb_control_transfer(
                device_, 0xA1, REQ_SET_CUR,
                static_cast<uint16_t>(CS_SAM_FREQ_CONTROL_SEL << 8),
                static_cast<uint16_t>((id << 8) | format_.controlInterfaceNum),
                cur, 4, 1000);
            uint32_t hzGot = (gc == 4) ? (uint32_t(cur[0]) | (uint32_t(cur[1]) << 8) | (uint32_t(cur[2]) << 16) | (uint32_t(cur[3]) << 24)) : 0;
            if (gc == 4 && hzGot == hz) {
                format_.clockSourceId = id;
                return true;
            }
        }
        return false;
    }

    uint8_t data[3] = {
        static_cast<uint8_t>(hz & 0xFF),
        static_cast<uint8_t>((hz >> 8) & 0xFF),
        static_cast<uint8_t>((hz >> 16) & 0xFF),
    };
    int rc = libusb_control_transfer(
        device_, 0x22, REQ_SET_CUR,
        static_cast<uint16_t>(CS_SAM_FREQ_CONTROL_SEL << 8),
        static_cast<uint16_t>(format_.endpointAddress),
        data, 3, 1000);
    if (rc != 3) {
        uint8_t data4[4] = { data[0], data[1], data[2], 0 };
        rc = libusb_control_transfer(
            device_, 0x22, REQ_SET_CUR,
            static_cast<uint16_t>(CS_SAM_FREQ_CONTROL_SEL << 8),
            static_cast<uint16_t>(format_.endpointAddress),
            data4, 4, 1000);
        if (rc != 4) return false;
    }
    return true;
}

bool UacDriver::start(int sampleRateHz, int bitsPerSample, int channels) {
    std::lock_guard<std::mutex> lock(mutex_);
    ErrorSink err{&lastError_, &errorMutex_, &lastErrorDetail_};
    lastError_.store(StartError::Ok, std::memory_order_release);
    {
        std::lock_guard<std::mutex> elock(errorMutex_);
        lastErrorDetail_.clear();
        supportedRates_.clear();
    }
    if (!device_) {
        err(StartError::NoDevice, "start called before open");
        return false;
    }
    if (streaming_.load(std::memory_order_acquire)) {
        stopIsoPump();
        streaming_.store(false, std::memory_order_release);
    }

    StreamFormat fmt{};
    if (!selectAltSetting(sampleRateHz, bitsPerSample, channels, &fmt)) {
        err(StartError::NoMatchingAlt, "no alternate setting matches requested format");
        return false;
    }

    bool needClaim = !interfaceClaimed_ || format_.interfaceNumber != fmt.interfaceNumber;
    if (needClaim) {
        if (interfaceClaimed_) {
            libusb_release_interface(device_, format_.interfaceNumber);
            interfaceClaimed_ = false;
        }
        int rc = libusb_claim_interface(device_, fmt.interfaceNumber);
        if (rc != LIBUSB_SUCCESS) {
            err(StartError::ClaimInterfaceFailed, "libusb_claim_interface failed");
            return false;
        }
        interfaceClaimed_ = true;
    }

    if (fmt.controlInterfaceNum != 0xFF && (!controlInterfaceClaimed_ || claimedControlIface_ != fmt.controlInterfaceNum)) {
        if (controlInterfaceClaimed_) {
            libusb_release_interface(device_, claimedControlIface_);
            controlInterfaceClaimed_ = false;
        }
        int rc = libusb_claim_interface(device_, fmt.controlInterfaceNum);
        if (rc == LIBUSB_SUCCESS) {
            controlInterfaceClaimed_ = true;
            claimedControlIface_ = fmt.controlInterfaceNum;
        }
    }
    format_ = fmt;

    int rc = libusb_set_interface_alt_setting(device_, format_.interfaceNumber, format_.altSetting);
    if (rc != LIBUSB_SUCCESS) {
        err(StartError::SetAltFailed, "libusb_set_interface_alt_setting failed");
        return false;
    }
    if (!setSampleRate(static_cast<uint32_t>(sampleRateHz))) {
        err(StartError::SetSampleRateFailed, "failed to configure sample rate on clock entity");
        return false;
    }

    ringHead_.store(0, std::memory_order_relaxed);
    ringTail_.store(0, std::memory_order_relaxed);
    writtenFrames_.store(0, std::memory_order_relaxed);
    playedFrames_.store(0, std::memory_order_relaxed);
    stopRequested_.store(false, std::memory_order_relaxed);

    if (!startIsoPump()) {
        if (lastError_.load(std::memory_order_relaxed) == StartError::Ok) {
            err(StartError::IsoPumpAllocFailed, "failed to start isochronous pump");
        }
        return false;
    }
    streaming_.store(true, std::memory_order_release);
    return true;
}

void UacDriver::flushRing() {
    ringTail_.store(0, std::memory_order_release);
    ringHead_.store(0, std::memory_order_release);
    writtenFrames_.store(0, std::memory_order_release);
    playedFrames_.store(0, std::memory_order_release);
}

bool UacDriver::isStreamingFormat(int sampleRate, int bitsPerSample, int channels) const {
    if (!streaming_.load(std::memory_order_acquire)) return false;
    return format_.sampleRateHz == sampleRate && format_.bitsPerSample == bitsPerSample && format_.channels == channels;
}

void UacDriver::stop() {
    bool was = streaming_.exchange(false, std::memory_order_acq_rel);
    if (!was && transfers_.empty() && !interfaceClaimed_ && !controlInterfaceClaimed_) return;

    std::lock_guard<std::mutex> lock(mutex_);
    stopIsoPump();
    if (device_ && interfaceClaimed_) {
        if (format_.altSetting != 0) {
            libusb_set_interface_alt_setting(device_, format_.interfaceNumber, 0);
        }
        libusb_release_interface(device_, format_.interfaceNumber);
        interfaceClaimed_ = false;
    }
    if (device_ && controlInterfaceClaimed_) {
        libusb_release_interface(device_, claimedControlIface_);
        controlInterfaceClaimed_ = false;
    }
}

bool UacDriver::startIsoPump() {
    int hostPeriodHz = format_.isHighSpeed ? 8000 : 1000;
    int packetIntervalUframes = format_.isHighSpeed ? (1 << (format_.bInterval > 0 ? format_.bInterval - 1 : 0)) : format_.bInterval;
    if (packetIntervalUframes < 1) packetIntervalUframes = 1;
    microframesPerSec_ = hostPeriodHz / packetIntervalUframes;
    int baseFrames = format_.sampleRateHz / microframesPerSec_;
    int rateRemainder = format_.sampleRateHz % microframesPerSec_;

    uint32_t seed_q16 = (static_cast<uint32_t>(baseFrames) << 16) +
        static_cast<uint32_t>((static_cast<uint64_t>(rateRemainder) << 16) / static_cast<uint32_t>(microframesPerSec_));
    framesPerUframe_q16_.store(seed_q16, std::memory_order_relaxed);
    fracAccumulator_q16_ = 0;
    maxFramesPerPacket_ = baseFrames + (rateRemainder > 0 ? 1 : 0) + 1;

    int frameStride = format_.channels * format_.bytesPerSample;
    int maxBytesPerPacket = maxFramesPerPacket_ * frameStride;
    int maxPacket = libusb_get_max_iso_packet_size(libusb_get_device(device_), format_.endpointAddress);
    if (maxPacket > 0 && maxBytesPerPacket > maxPacket) return false;

    transfers_.reserve(kNumTransfers);
    transferBuffers_.reserve(kNumTransfers);

    auto setErr = [this](StartError c, const std::string& d) {
        lastError_.store(c, std::memory_order_release);
        std::lock_guard<std::mutex> lock(errorMutex_);
        lastErrorDetail_ = d;
    };

    for (int i = 0; i < kNumTransfers; ++i) {
        libusb_transfer* xfr = libusb_alloc_transfer(kPacketsPerTransfer);
        if (!xfr) {
            setErr(StartError::IsoPumpAllocFailed, "libusb_alloc_transfer returned null");
            stopIsoPump();
            return false;
        }
        std::vector<uint8_t> buf(maxBytesPerPacket * kPacketsPerTransfer, 0);
        transferBuffers_.push_back(std::move(buf));
        transfers_.push_back(xfr);
    }

    if (format_.feedbackEndpointAddress != 0) {
        feedbackTransfers_.reserve(2);
        feedbackBuffers_.reserve(2);
        for (int i = 0; i < 2; ++i) {
            libusb_transfer* fb = libusb_alloc_transfer(1);
            if (fb) {
                std::vector<uint8_t> fbuf(8, 0);
                feedbackBuffers_.push_back(std::move(fbuf));
                feedbackTransfers_.push_back(fb);
            }
        }
    }

    stopRequested_.store(false, std::memory_order_release);
    eventThread_ = std::thread([this]() {
        while (!stopRequested_.load(std::memory_order_acquire)) {
            timeval tv{0, 100000};
            libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
        }
    });

    for (size_t i = 0; i < transfers_.size(); ++i) {
        libusb_transfer* xfr = transfers_[i];
        uint8_t* buf = transferBuffers_[i].data();
        libusb_fill_iso_transfer(
            xfr, device_, format_.endpointAddress, buf,
            maxBytesPerPacket * kPacketsPerTransfer, kPacketsPerTransfer,
            &UacDriver::onIsoTrampoline, this, 1000);
        for (int p = 0; p < kPacketsPerTransfer; ++p) {
            xfr->iso_packet_desc[p].length = maxBytesPerPacket;
        }
        inflight_.fetch_add(1, std::memory_order_relaxed);
        int rc = libusb_submit_transfer(xfr);
        if (rc != LIBUSB_SUCCESS) {
            inflight_.fetch_sub(1, std::memory_order_relaxed);
            setErr(StartError::IsoPumpSubmitFailed, "libusb_submit_transfer failed");
            stopIsoPump();
            return false;
        }
    }

    for (size_t i = 0; i < feedbackTransfers_.size(); ++i) {
        libusb_transfer* fb = feedbackTransfers_[i];
        uint8_t* fbuf = feedbackBuffers_[i].data();
        int fbLen = format_.isHighSpeed ? 4 : 3;
        libusb_fill_iso_transfer(
            fb, device_, format_.feedbackEndpointAddress, fbuf,
            fbLen, 1, &UacDriver::onFeedbackTrampoline, this, 1000);
        fb->iso_packet_desc[0].length = fbLen;
        inflight_.fetch_add(1, std::memory_order_relaxed);
        if (libusb_submit_transfer(fb) != LIBUSB_SUCCESS) {
            inflight_.fetch_sub(1, std::memory_order_relaxed);
        }
    }
    return true;
}

void UacDriver::stopIsoPump() {
    stopRequested_.store(true, std::memory_order_release);
    for (libusb_transfer* xfr : transfers_) {
        if (xfr) libusb_cancel_transfer(xfr);
    }
    for (libusb_transfer* fb : feedbackTransfers_) {
        if (fb) libusb_cancel_transfer(fb);
    }

    if (eventThread_.joinable()) {
        eventThread_.join();
    }

    for (libusb_transfer* xfr : transfers_) {
        if (xfr) libusb_free_transfer(xfr);
    }
    transfers_.clear();
    transferBuffers_.clear();

    for (libusb_transfer* fb : feedbackTransfers_) {
        if (fb) libusb_free_transfer(fb);
    }
    feedbackTransfers_.clear();
    feedbackBuffers_.clear();
    inflight_.store(0, std::memory_order_release);
}

void LIBUSB_CALL UacDriver::onIsoTrampoline(libusb_transfer* xfr) {
    static_cast<UacDriver*>(xfr->user_data)->onIso(xfr);
}

void UacDriver::onIso(libusb_transfer* xfr) {
    if (stopRequested_.load(std::memory_order_acquire) || xfr->status == LIBUSB_TRANSFER_CANCELLED || xfr->status == LIBUSB_TRANSFER_NO_DEVICE) {
        inflight_.fetch_sub(1, std::memory_order_relaxed);
        return;
    }

    uint8_t* ptr = xfr->buffer;
    int frameStride = format_.channels * format_.bytesPerSample;
    uint32_t step_q16 = framesPerUframe_q16_.load(std::memory_order_relaxed);
    long totalFramesThisTransfer = 0;

    for (int p = 0; p < xfr->num_iso_packets; ++p) {
        fracAccumulator_q16_ += step_q16;
        int frames = static_cast<int>(fracAccumulator_q16_ >> 16);
        fracAccumulator_q16_ &= 0xFFFF;
        int packetBytes = frames * frameStride;

        xfr->iso_packet_desc[p].length = packetBytes;
        drainRing(ptr, packetBytes);
        ptr += packetBytes;
        totalFramesThisTransfer += frames;
    }
    playedFrames_.fetch_add(totalFramesThisTransfer, std::memory_order_relaxed);

    if (libusb_submit_transfer(xfr) != LIBUSB_SUCCESS) {
        inflight_.fetch_sub(1, std::memory_order_relaxed);
    }
}

void LIBUSB_CALL UacDriver::onFeedbackTrampoline(libusb_transfer* xfr) {
    static_cast<UacDriver*>(xfr->user_data)->onFeedback(xfr);
}

void UacDriver::onFeedback(libusb_transfer* xfr) {
    if (stopRequested_.load(std::memory_order_acquire) || xfr->status == LIBUSB_TRANSFER_CANCELLED || xfr->status == LIBUSB_TRANSFER_NO_DEVICE) {
        inflight_.fetch_sub(1, std::memory_order_relaxed);
        return;
    }

    if (xfr->status == LIBUSB_TRANSFER_COMPLETED && xfr->iso_packet_desc[0].actual_length >= 3) {
        const uint8_t* p = xfr->buffer;
        uint32_t val_q16 = 0;
        if (format_.isHighSpeed && xfr->iso_packet_desc[0].actual_length >= 4) {
            uint32_t raw_16_16 = uint32_t(p[0]) | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16) | (uint32_t(p[3]) << 24);
            int shift = (format_.feedbackInterval > 1) ? (format_.feedbackInterval - 1) : 0;
            val_q16 = raw_16_16 >> (shift + 3);
        } else {
            uint32_t raw_10_14 = uint32_t(p[0]) | (uint32_t(p[1]) << 8) | (uint32_t(p[2]) << 16);
            val_q16 = raw_10_14 << 2;
        }
        if (val_q16 > 0) {
            framesPerUframe_q16_.store(val_q16, std::memory_order_relaxed);
        }
    }

    int fbLen = format_.isHighSpeed ? 4 : 3;
    xfr->iso_packet_desc[0].length = fbLen;
    if (libusb_submit_transfer(xfr) != LIBUSB_SUCCESS) {
        inflight_.fetch_sub(1, std::memory_order_relaxed);
    }
}

int UacDriver::drainRing(uint8_t* dst, int bytes) {
    size_t tail = ringTail_.load(std::memory_order_relaxed);
    size_t head = ringHead_.load(std::memory_order_acquire);
    size_t available = ringSize(head, tail);
    size_t toRead = std::min(static_cast<size_t>(bytes), available);

    size_t start = tail & ringMask_;
    size_t end = (tail + toRead) & ringMask_;
    if (start <= end) {
        std::memcpy(dst, ring_.data() + start, toRead);
    } else {
        size_t firstPart = kRingBytes - start;
        std::memcpy(dst, ring_.data() + start, firstPart);
        std::memcpy(dst + firstPart, ring_.data(), toRead - firstPart);
    }

    if (toRead < static_cast<size_t>(bytes)) {
        std::memset(dst + toRead, 0, bytes - toRead);
    }
    ringTail_.store(tail + toRead, std::memory_order_release);
    return static_cast<int>(toRead);
}

int UacDriver::writePcm(const uint8_t* data, int frames) {
    if (!data || frames <= 0) return 0;
    int frameStride = format_.channels * format_.bytesPerSample;
    size_t bytes = static_cast<size_t>(frames * frameStride);

    size_t head = ringHead_.load(std::memory_order_relaxed);
    size_t tail = ringTail_.load(std::memory_order_acquire);
    size_t space = kRingBytes - ringSize(head, tail);
    size_t toWrite = std::min(bytes, space);
    if (toWrite == 0) return 0;

    size_t start = head & ringMask_;
    size_t end = (head + toWrite) & ringMask_;
    if (start <= end) {
        std::memcpy(ring_.data() + start, data, toWrite);
    } else {
        size_t firstPart = kRingBytes - start;
        std::memcpy(ring_.data() + start, data, firstPart);
        std::memcpy(ring_.data(), data + firstPart, toWrite - firstPart);
    }

    ringHead_.store(head + toWrite, std::memory_order_release);
    int writtenFrames = static_cast<int>(toWrite / frameStride);
    writtenFrames_.fetch_add(writtenFrames, std::memory_order_relaxed);
    return writtenFrames;
}

int UacDriver::writableFrames() const {
    int frameStride = format_.channels * format_.bytesPerSample;
    if (frameStride <= 0) return 0;
    size_t head = ringHead_.load(std::memory_order_relaxed);
    size_t tail = ringTail_.load(std::memory_order_acquire);
    size_t space = kRingBytes - ringSize(head, tail);
    return static_cast<int>(space / frameStride);
}

} // namespace siliconplayer::usb
