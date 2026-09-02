#include "UacDriver.h"

#include <android/log.h>
#include <algorithm>
#include <cstring>

#define TAG "UacDriver"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
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

    LOGI("selectAltSetting: %u interfaces in config, requested %dHz %d-bit %dch", config->bNumInterfaces, sampleRateHz, bitsPerSample, channels);

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
        LOGI("no AudioControl interface found; defaulting controlIface=0, UAC 1.0");
        controlIface = 0;
        uacVersion = 0x0100;
    } else {
        LOGI("AudioControl iface=%u, UAC version=0x%04x, clock entities=%zu, terminals=%zu",
             controlIface, uacVersion, clockEntities.size(), terminals.size());
    }

    struct Candidate {
        const libusb_interface_descriptor* alt = nullptr;
        int channels = 2;
        int bits = 16;
        int subslot = 2;
        int selectedRate = 48000;
        uint8_t terminalLink = 0;
        const libusb_endpoint_descriptor* iso = nullptr;
        const libusb_endpoint_descriptor* feedback = nullptr;
        int score = 0;
    };
    std::vector<Candidate> candidates;

    bool isHs = libusb_get_device_speed(dev) >= LIBUSB_SPEED_HIGH;
    int microframesPerSecond = isHs ? 8000 : 1000;

    for (uint8_t i = 0; i < config->bNumInterfaces; ++i) {
        const libusb_interface& iface = config->interface[i];
        for (int a = 0; a < iface.num_altsetting; ++a) {
            const libusb_interface_descriptor& alt = iface.altsetting[a];
            if (alt.bInterfaceClass != USB_CLASS_AUDIO) continue;
            if (alt.bInterfaceSubClass != SUBCLASS_AUDIOSTREAM && alt.bInterfaceSubClass != 0 && alt.bInterfaceSubClass != 0x03) continue;
            if (alt.bAlternateSetting == 0 && iface.num_altsetting > 1) continue;

            int altChannels = 0, altBits = 0, altSubslot = 0;
            uint8_t altTerminalLink = 0;
            bool rateSupported = (uacVersion >= 0x0200);

            std::vector<uint32_t> discreteRates;
            uint32_t contLo = 0, contHi = 0;
            bool isContinuous = false;

            walkExtra(alt.extra, alt.extra_length, [&](const uint8_t* p, int len) {
                if (isClassDescriptor(p, len, CS_INTERFACE, AS_GENERAL)) {
                    if (uacVersion >= 0x0200) {
                        if (len >= 4) altTerminalLink = p[3];
                        if (len >= 11) altChannels = p[10];
                    }
                } else if (isClassDescriptor(p, len, CS_INTERFACE, AS_FORMAT_TYPE) && len >= 4 && p[3] == FORMAT_TYPE_I) {
                    if (uacVersion >= 0x0200) {
                        if (len >= 5) altSubslot = p[4];
                        if (len >= 6) altBits = p[5];
                    } else if (len >= 7) {
                        altChannels = p[4];
                        altSubslot = p[5];
                        altBits = p[6];
                        if (len >= 8) {
                            int kind = p[7];
                            if (kind == 0 && len >= 14) {
                                isContinuous = true;
                                auto rd24 = [](const uint8_t* q) {
                                    return static_cast<uint32_t>(q[0]) | (static_cast<uint32_t>(q[1]) << 8) | (static_cast<uint32_t>(q[2]) << 16);
                                };
                                contLo = rd24(p + 8);
                                contHi = rd24(p + 11);
                                std::lock_guard<std::mutex> elock(errorMutex_);
                                bool dup = false;
                                for (const auto& e : supportedRates_) {
                                    if (e.minHz == contLo && e.maxHz == contHi) { dup = true; break; }
                                }
                                if (!dup) supportedRates_.push_back({0, contLo, contHi, 0});
                            } else if (kind > 0) {
                                std::lock_guard<std::mutex> elock(errorMutex_);
                                for (int k = 0; k < kind; ++k) {
                                    int off = 8 + k * 3;
                                    if (off + 3 > len) break;
                                    uint32_t hz = static_cast<uint32_t>(p[off]) | (static_cast<uint32_t>(p[off + 1]) << 8) | (static_cast<uint32_t>(p[off + 2]) << 16);
                                    discreteRates.push_back(hz);
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

            int selectedRate = sampleRateHz;
            int rateScore = 1000;
            if (uacVersion < 0x0200) {
                if (isContinuous) {
                    if (static_cast<uint32_t>(sampleRateHz) < contLo) {
                        selectedRate = static_cast<int>(contLo);
                        rateScore = 500;
                    } else if (static_cast<uint32_t>(sampleRateHz) > contHi) {
                        selectedRate = static_cast<int>(contHi);
                        rateScore = 500;
                    } else {
                        selectedRate = sampleRateHz;
                        rateScore = 1000;
                    }
                } else if (!discreteRates.empty()) {
                    auto it = std::find(discreteRates.begin(), discreteRates.end(), static_cast<uint32_t>(sampleRateHz));
                    if (it != discreteRates.end()) {
                        selectedRate = sampleRateHz;
                        rateScore = 1000;
                    } else {
                        uint32_t closest = discreteRates.front();
                        int minDiff = std::abs(static_cast<int>(closest) - sampleRateHz);
                        for (uint32_t r : discreteRates) {
                            int diff = std::abs(static_cast<int>(r) - sampleRateHz);
                            if (diff < minDiff) {
                                minDiff = diff;
                                closest = r;
                            }
                        }
                        selectedRate = static_cast<int>(closest);
                        rateScore = 500;
                    }
                }
            }

            const libusb_endpoint_descriptor* iso = nullptr;
            const libusb_endpoint_descriptor* feedback = nullptr;
            for (int e = 0; e < alt.bNumEndpoints; ++e) {
                const libusb_endpoint_descriptor& ep = alt.endpoint[e];
                if ((ep.bmAttributes & 0x03) != LIBUSB_TRANSFER_TYPE_ISOCHRONOUS) continue;
                bool isIn = (ep.bEndpointAddress & 0x80) != 0;
                if (!isIn && !iso) iso = &ep;
                else if (isIn && !feedback) feedback = &ep;
            }
            if (!iso) continue;

            if (altChannels <= 0) altChannels = 2;
            if (altBits <= 0) altBits = altSubslot ? altSubslot * 8 : 16;
            if (altSubslot <= 0) altSubslot = altBits / 8;

            int microframesPerInterval = isHs ? (1 << (iso->bInterval > 0 ? iso->bInterval - 1 : 0)) : iso->bInterval;
            int packetsPerSec = microframesPerInterval > 0 ? microframesPerSecond / microframesPerInterval : microframesPerSecond;
            int frameStride = altSubslot * altChannels;
            int framesPerPacket = (selectedRate + packetsPerSec - 1) / packetsPerSec;
            int reqBytesPerPacket = framesPerPacket * frameStride;
            int actualMps = iso->wMaxPacketSize & 0x07FF;
            int extraTransactions = ((iso->wMaxPacketSize >> 11) & 0x3) + 1;
            int realMps = actualMps * extraTransactions;
            if (realMps > 0 && reqBytesPerPacket > realMps) {
                LOGW("alt %u (mps=%d) too small for %d bytes/pkt (%d frames * %d stride) — skipping",
                     alt.bAlternateSetting, realMps, reqBytesPerPacket, framesPerPacket, frameStride);
                continue;
            }

            int score = rateScore;
            if (altChannels == channels) score += 1000;
            else if (altChannels == 2) score += 500;

            if (altBits == bitsPerSample) score += 1000;
            else if (altBits == 24 && bitsPerSample == 16) score += 850;
            else if (altBits == 32 && bitsPerSample == 16) score += 750;
            else if (altBits == 32 && bitsPerSample == 24) score += 850;
            else if (altBits >= bitsPerSample) score += 500;
            else score += 100;

            if (feedback != nullptr) score += 50;

            LOGI("candidate alt %u on iface %u: %dch %d-bit (subslot %d) rate=%d (req %d) score=%d ep=0x%02x mps=%d (real %d)",
                 alt.bAlternateSetting, alt.bInterfaceNumber, altChannels, altBits, altSubslot, selectedRate, sampleRateHz, score, iso->bEndpointAddress, actualMps, realMps);

            candidates.push_back({&alt, altChannels, altBits, altSubslot, selectedRate, altTerminalLink, iso, feedback, score});
        }
    }

    if (candidates.empty()) {
        LOGW("no candidate alt-settings found on device");
        libusb_free_config_descriptor(config);
        return false;
    }

    std::sort(candidates.begin(), candidates.end(), [](const Candidate& a, const Candidate& b) {
        return a.score > b.score;
    });

    const Candidate& best = candidates.front();
    outFmt->sampleRateHz = best.selectedRate;
    outFmt->bitsPerSample = best.bits;
    outFmt->bytesPerSample = best.subslot;
    outFmt->channels = best.channels;
    outFmt->interfaceNumber = best.alt->bInterfaceNumber;
    outFmt->altSetting = best.alt->bAlternateSetting;
    outFmt->endpointAddress = best.iso->bEndpointAddress;
    outFmt->maxPacketSize = best.iso->wMaxPacketSize;

    uint8_t resolvedClock = 0;
    for (const auto& tc : terminals) {
        if (tc.termId == best.terminalLink) { resolvedClock = tc.clockId; break; }
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
    outFmt->bInterval = best.iso->bInterval;
    outFmt->uacVersion = uacVersion;
    if (best.feedback) {
        outFmt->feedbackEndpointAddress = best.feedback->bEndpointAddress;
        outFmt->feedbackMaxPacketSize = best.feedback->wMaxPacketSize;
        outFmt->feedbackInterval = best.feedback->bInterval;
    }

    LOGI("matched alt %u on iface %u: %dch %d-bit (subslot %d), data ep 0x%02x mps=%d bInterval=%u clockId=%u%s",
         outFmt->altSetting, outFmt->interfaceNumber, outFmt->channels, outFmt->bitsPerSample, outFmt->bytesPerSample,
         outFmt->endpointAddress, outFmt->maxPacketSize, outFmt->bInterval, outFmt->clockSourceId,
         outFmt->feedbackEndpointAddress != 0 ? " + feedback" : "");

    libusb_free_config_descriptor(config);
    return true;
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
        if (rc != 4) {
            LOGW("UAC1 set endpoint rate to %uHz returned %d (endpoint may be fixed-rate)", hz, rc);
        }
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

    format_ = fmt;

    int rc = libusb_set_interface_alt_setting(device_, format_.interfaceNumber, format_.altSetting);
    if (rc != LIBUSB_SUCCESS) {
        err(StartError::SetAltFailed, "libusb_set_interface_alt_setting failed");
        return false;
    }
    setSampleRate(static_cast<uint32_t>(format_.sampleRateHz));

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
    LOGI("UAC stream started: %dHz %d-bit %dch (alt %u on iface %u, ep 0x%02x, clock %u, highSpeed=%d)",
         format_.sampleRateHz, format_.bitsPerSample, format_.channels,
         format_.altSetting, format_.interfaceNumber, format_.endpointAddress,
         format_.clockSourceId, format_.isHighSpeed);
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
    if (!was && transfers_.empty() && !interfaceClaimed_) return;

    std::lock_guard<std::mutex> lock(mutex_);
    stopIsoPump();
    if (device_ && interfaceClaimed_) {
        if (format_.altSetting != 0) {
            libusb_set_interface_alt_setting(device_, format_.interfaceNumber, 0);
        }
        libusb_release_interface(device_, format_.interfaceNumber);
        libusb_attach_kernel_driver(device_, format_.interfaceNumber);
        interfaceClaimed_ = false;
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
    nominalStep_q16_ = seed_q16;
    framesPerUframe_q16_.store(seed_q16, std::memory_order_relaxed);
    fracAccumulator_q16_ = 0;
    maxFramesPerPacket_ = baseFrames + (rateRemainder > 0 ? 1 : 0);

    int frameStride = format_.channels * format_.bytesPerSample;
    int maxBytesPerPacket = maxFramesPerPacket_ * frameStride;
    int mps = format_.maxPacketSize & 0x07FF;
    if (mps > 0 && maxBytesPerPacket > mps) {
        maxBytesPerPacket = mps;
        maxFramesPerPacket_ = maxBytesPerPacket / frameStride;
    }

    auto setErr = [this](StartError c, const std::string& d) {
        lastError_.store(c, std::memory_order_release);
        std::lock_guard<std::mutex> lock(errorMutex_);
        lastErrorDetail_ = d;
    };

    transfers_.reserve(kNumTransfers);
    transferBuffers_.reserve(kNumTransfers);

    for (int i = 0; i < kNumTransfers; ++i) {
        libusb_transfer* xfr = libusb_alloc_transfer(kPacketsPerTransfer);
        if (!xfr) {
            setErr(StartError::IsoPumpAllocFailed, "libusb_alloc_transfer returned null");
            stopIsoPump();
            return false;
        }
        std::vector<uint8_t> buf(maxBytesPerPacket * kPacketsPerTransfer, 0);
        libusb_fill_iso_transfer(
            xfr, device_, format_.endpointAddress, buf.data(),
            static_cast<int>(buf.size()), kPacketsPerTransfer,
            &UacDriver::onIsoTrampoline, this, 0);
        libusb_set_iso_packet_lengths(xfr, baseFrames * frameStride);
        transferBuffers_.push_back(std::move(buf));
        transfers_.push_back(xfr);
    }

    if (format_.feedbackEndpointAddress != 0) {
        feedbackTransfers_.reserve(2);
        feedbackBuffers_.reserve(2);
        for (int i = 0; i < 2; ++i) {
            libusb_transfer* fb = libusb_alloc_transfer(1);
            if (fb) {
                int fbLen = format_.isHighSpeed ? 4 : 3;
                std::vector<uint8_t> fbuf(fbLen, 0);
                libusb_fill_iso_transfer(
                    fb, device_, format_.feedbackEndpointAddress, fbuf.data(),
                    static_cast<int>(fbuf.size()), 1,
                    &UacDriver::onFeedbackTrampoline, this, 0);
                libusb_set_iso_packet_lengths(fb, fbLen);
                feedbackBuffers_.push_back(std::move(fbuf));
                feedbackTransfers_.push_back(fb);
            }
        }
    }

    stopRequested_.store(false, std::memory_order_release);
    eventThread_ = std::thread([this]() {
        pthread_setname_np(pthread_self(), "sp_uac_events");
        while (!stopRequested_.load(std::memory_order_acquire) || inflight_.load(std::memory_order_acquire) > 0) {
            timeval tv{0, 50000};
            if (ctx_) {
                libusb_handle_events_timeout_completed(ctx_, &tv, nullptr);
            } else {
                break;
            }
        }
    });

    for (libusb_transfer* fb : feedbackTransfers_) {
        inflight_.fetch_add(1, std::memory_order_relaxed);
        if (libusb_submit_transfer(fb) != LIBUSB_SUCCESS) {
            inflight_.fetch_sub(1, std::memory_order_relaxed);
        }
    }

    for (size_t i = 0; i < transfers_.size(); ++i) {
        libusb_transfer* xfr = transfers_[i];
        inflight_.fetch_add(1, std::memory_order_relaxed);
        int rc = libusb_submit_transfer(xfr);
        if (rc != LIBUSB_SUCCESS) {
            LOGW("libusb_submit_transfer failed with rc=%d (%s)", rc, libusb_error_name(rc));
            inflight_.fetch_sub(1, std::memory_order_relaxed);
            setErr(StartError::IsoPumpSubmitFailed, std::string("libusb_submit_transfer failed: ") + libusb_error_name(rc));
            stopIsoPump();
            return false;
        }
    }

    LOGI("Iso pump successfully started: %zu data transfers, %zu feedback transfers, %d pkts/xfr, %d bytes/pkt",
         transfers_.size(), feedbackTransfers_.size(), kPacketsPerTransfer, maxBytesPerPacket);
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
    if (ctx_) {
        libusb_interrupt_event_handler(ctx_);
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

    if (format_.feedbackEndpointAddress == 0 && nominalStep_q16_ > 0) {
        size_t head = ringHead_.load(std::memory_order_relaxed);
        size_t tail = ringTail_.load(std::memory_order_relaxed);
        size_t currentFillBytes = ringSize(head, tail);

        constexpr size_t targetWatermark = kRingBytes / 2;
        int64_t fillErrorBytes = static_cast<int64_t>(currentFillBytes) - static_cast<int64_t>(targetWatermark);

        int64_t maxAdj_q16 = (static_cast<int64_t>(nominalStep_q16_) * 5) / 10000;
        if (maxAdj_q16 < 1) maxAdj_q16 = 1;

        constexpr int64_t rangeBytes = static_cast<int64_t>(kRingBytes / 4);
        int64_t adj_q16 = (fillErrorBytes * maxAdj_q16) / rangeBytes;
        if (adj_q16 > maxAdj_q16) adj_q16 = maxAdj_q16;
        if (adj_q16 < -maxAdj_q16) adj_q16 = -maxAdj_q16;

        uint32_t adjustedStep = static_cast<uint32_t>(static_cast<int64_t>(nominalStep_q16_) + adj_q16);
        framesPerUframe_q16_.store(adjustedStep, std::memory_order_relaxed);
    }

    uint8_t* cursor = xfr->buffer;
    int frameStride = format_.channels * format_.bytesPerSample;
    uint32_t step_q16 = framesPerUframe_q16_.load(std::memory_order_relaxed);
    long totalFramesThisTransfer = 0;

    for (int p = 0; p < xfr->num_iso_packets; ++p) {
        fracAccumulator_q16_ += step_q16;
        int frames = static_cast<int>(fracAccumulator_q16_ >> 16);
        fracAccumulator_q16_ &= 0xFFFF;
        if (frames > maxFramesPerPacket_) frames = maxFramesPerPacket_;
        int packetBytes = frames * frameStride;

        xfr->iso_packet_desc[p].length = packetBytes;
        if (packetBytes > 0) {
            if (audioProvider_) {
                int provided = audioProvider_(cursor, frames, format_);
                if (provided < frames) {
                    std::memset(cursor + (provided * frameStride), 0, (frames - provided) * frameStride);
                }
            } else {
                drainRing(cursor, packetBytes);
            }
        }
        cursor += packetBytes;
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

UacDriver& getUacDriverInstance() {
    static UacDriver instance;
    return instance;
}

} // namespace siliconplayer::usb
