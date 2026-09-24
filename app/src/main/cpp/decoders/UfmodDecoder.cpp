#include "UfmodDecoder.h"
#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <fstream>

#define LOG_TAG "UfmodDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

UfmodDecoder::~UfmodDecoder() {
    close();
}

bool UfmodDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    close();

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return false;
    const std::streamsize size = file.tellg();
    if (size < 60) return false;
    file.seekg(0, std::ios::beg);
    fileData.resize(static_cast<size_t>(size));
    if (!file.read(fileData.data(), size)) return false;

    context = ufmod_load(fileData.data(), fileData.size(), static_cast<unsigned int>(sampleRate));
    if (!context) {
        LOGE("uFMOD rejected %s", path ? path : "<null>");
        fileData.clear();
        return false;
    }

    title = ufmod_get_title(context);
    ufmod_set_quirks(context, quirkFlags);
    moduleChannels = static_cast<int>(ufmod_get_channel_count(context));
    unsigned int orders = 0;
    unsigned int bpm = 0;
    unsigned int speed = 0;
    ufmod_get_info(context, nullptr, &orders, &bpm, &speed);
    moduleOrders = static_cast<int>(orders);
    moduleBpm = static_cast<int>(bpm);
    moduleSpeed = static_cast<int>(speed);
    if (moduleOrders > 0 && moduleBpm > 0 && moduleSpeed > 0) {
        estimatedDuration = static_cast<double>(moduleOrders) * 64.0 * moduleSpeed * 2.5 / moduleBpm;
    } else {
        estimatedDuration = 0.0;
    }
    sampleRate = static_cast<int>(ufmod_get_sample_rate(context));
    pcmBuffer.assign(static_cast<size_t>(std::max(1, 4096)) * 2, 0);
    timelineBaseSeconds = 0.0;
    timelineAudioBaseSeconds = 0.0;
    lastOrder = -1;
    lastRow = -1;
    timelineAnchored = false;
    ended = false;
    ufmod_set_noloop(context, repeatMode == 0 ? 1 : 0);
    return true;
}

void UfmodDecoder::close() {
    if (context) {
        ufmod_free(context);
        context = nullptr;
    }
    fileData.clear();
    pcmBuffer.clear();
    title.clear();
    moduleChannels = 0;
    moduleOrders = 0;
    moduleBpm = 0;
    moduleSpeed = 0;
    estimatedDuration = 0.0;
    timelineBaseSeconds = 0.0;
    timelineAudioBaseSeconds = 0.0;
    lastOrder = -1;
    lastRow = -1;
    timelineAnchored = false;
    ended = false;
}

int UfmodDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!context || !buffer || numFrames <= 0 || ended) return 0;
    if (static_cast<int>(pcmBuffer.size()) < numFrames * 2) {
        pcmBuffer.resize(static_cast<size_t>(numFrames) * 2);
    }

    const size_t rendered = ufmod_render(context, pcmBuffer.data(), static_cast<size_t>(numFrames));
    const int frames = static_cast<int>(rendered);
    for (int i = 0; i < frames * 2; ++i) {
        buffer[i] = static_cast<float>(pcmBuffer[i]) / 32768.0f;
    }
    updateTimelinePositionLocked();
    if (frames < numFrames) ended = true;
    return frames;
}

void UfmodDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!context) return;
    ufmod_restart(context);
    if (seconds > 0.0 && estimatedDuration > 0.0 && moduleOrders > 0) {
        const double orderPosition = seconds / estimatedDuration * moduleOrders;
        const int targetOrder = std::clamp(static_cast<int>(orderPosition), 0, moduleOrders - 1);
        if (targetOrder > 0) ufmod_jump_order(context, targetOrder);
    }
    ufmod_set_noloop(context, repeatMode == 0 ? 1 : 0);
    lastOrder = -1;
    lastRow = -1;
    timelineAnchored = false;
    ended = false;
}

double UfmodDecoder::getDuration() { return estimatedDuration; }
int UfmodDecoder::getSampleRate() { return sampleRate; }
int UfmodDecoder::getBitDepth() { return 16; }
std::string UfmodDecoder::getBitDepthLabel() { return "16-bit mixer output"; }
int UfmodDecoder::getChannelCount() { return 2; }
int UfmodDecoder::getDisplayChannelCount() {
    return moduleChannels > 0 ? moduleChannels : 2;
}
int UfmodDecoder::getSourceChannelCount() { return moduleChannels; }
std::string UfmodDecoder::getTitle() { return title; }
std::string UfmodDecoder::getArtist() { return {}; }
void UfmodDecoder::setOutputSampleRate(int rate) { if (!context && rate > 0) sampleRate = rate; }
void UfmodDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
    if (context) ufmod_set_noloop(context, mode == 0 ? 1 : 0);
}
void UfmodDecoder::updateTimelinePositionLocked() {
    if (!context) return;

    unsigned int order = 0;
    unsigned int row = 0;
    ufmod_get_row_order(context, &row, &order);
    const double audioSeconds = static_cast<double>(ufmod_get_time(context)) / 1000.0;
    const bool backwardJump = lastOrder >= 0 &&
            (static_cast<int>(order) < lastOrder ||
             (static_cast<int>(order) == lastOrder && static_cast<int>(row) < lastRow));

    if (!timelineAnchored) {
        timelineBaseSeconds = 0.0;
        timelineAudioBaseSeconds = audioSeconds;
        timelineAnchored = true;
    } else if (backwardJump) {
        const double rowDuration = moduleSpeed > 0 && moduleBpm > 0
                ? 2.5 * moduleSpeed / moduleBpm
                : 0.0;
        timelineBaseSeconds = (static_cast<double>(order) * 64.0 + row) * rowDuration;
        timelineAudioBaseSeconds = audioSeconds;
    }

    lastOrder = static_cast<int>(order);
    lastRow = static_cast<int>(row);
}

double UfmodDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (!context) return 0.0;
    updateTimelinePositionLocked();
    const double audioSeconds = static_cast<double>(ufmod_get_time(context)) / 1000.0;
    const double position = timelineBaseSeconds + (audioSeconds - timelineAudioBaseSeconds);
    return std::max(0.0, std::min(position, estimatedDuration > 0.0 ? estimatedDuration : position));
}

void UfmodDecoder::setOption(const char* name, const char* value) {
    if (!name || !value) return;
    if (std::strcmp(name, "ufmod.quirks") == 0) {
        quirkFlags = static_cast<unsigned int>(std::strtoul(value, nullptr, 10));
        if (context) ufmod_set_quirks(context, quirkFlags);
    }
}

std::string UfmodDecoder::getCoreStringInfo(const char* name) {
    if (name && std::strcmp(name, "version") == 0) return "uFMOD-C";
    return {};
}

int UfmodDecoder::getCoreIntInfo(const char* name, int fallback) {
    if (!name) return fallback;
    if (std::strcmp(name, "channels") == 0) return moduleChannels;
    if (std::strcmp(name, "orders") == 0) return moduleOrders;
    if (std::strcmp(name, "bpm") == 0) return moduleBpm;
    if (std::strcmp(name, "speed") == 0) return moduleSpeed;
    if (std::strcmp(name, "quirks") == 0 && context) return static_cast<int>(ufmod_get_quirks(context));
    if (context && (std::strcmp(name, "current_order") == 0 || std::strcmp(name, "current_row") == 0)) {
        unsigned int row = 0;
        unsigned int order = 0;
        ufmod_get_row_order(context, &row, &order);
        return std::strcmp(name, "current_order") == 0 ? static_cast<int>(order) : static_cast<int>(row);
    }
    if (std::strcmp(name, "loop_count") == 0 && context) return ufmod_get_loop_count(context);
    return fallback;
}
