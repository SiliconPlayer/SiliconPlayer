#include <algorithm>
#include "usb/UacDriver.h"

namespace siliconplayer::usb {

UacDriver::UacDriver() = default;
UacDriver::~UacDriver() = default;

bool UacDriver::ensureContext() {
    return false;
}

bool UacDriver::open(int) {
    return false;
}

void UacDriver::close() {
}

bool UacDriver::start(int, int, int) {
    return false;
}

void UacDriver::stop() {
}

void UacDriver::flushRing() {
}

bool UacDriver::isStreamingFormat(int, int, int) const {
    return false;
}

int UacDriver::writePcm(const uint8_t*, int) {
    return 0;
}

int UacDriver::writableFrames() const {
    return 0;
}

std::string UacDriver::lastErrorDetail() const {
    return "";
}

std::vector<ClockRateRange> UacDriver::supportedRates() const {
    return {};
}

UacDriver& getUacDriverInstance() {
    static UacDriver instance;
    return instance;
}

} // namespace siliconplayer::usb
