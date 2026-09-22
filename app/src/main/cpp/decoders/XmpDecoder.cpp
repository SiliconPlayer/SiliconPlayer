#include "XmpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cstring>
#include <fstream>

#define LOG_TAG "XmpDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
constexpr float kInt32ToFloat = 1.0f / 2147483648.0f;

int parseIntString(const std::string& value, int fallback) {
    try {
        return std::stoi(value);
    } catch (...) {
        return fallback;
    }
}
}

XmpDecoder::XmpDecoder() = default;

XmpDecoder::~XmpDecoder() {
    close();
}

bool XmpDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", path);
        return false;
    }
    const std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);
    if (size <= 0) {
        LOGE("File is empty: %s", path);
        return false;
    }
    fileBuffer.resize(static_cast<size_t>(size));
    if (!file.read(fileBuffer.data(), size)) {
        LOGE("Failed to read file: %s", path);
        return false;
    }

    context = xmp_create_context();
    if (context == nullptr) {
        LOGE("xmp_create_context failed");
        return false;
    }

    if (xmp_load_module_from_memory(context, fileBuffer.data(), static_cast<long>(size)) != 0) {
        LOGE("xmp_load_module_from_memory failed: %s", path);
        closeLocked();
        return false;
    }

    struct xmp_module_info mi;
    xmp_get_module_info(context, &mi);
    moduleChannels = mi.mod->chn;
    title = mi.mod->name;
    moduleType = mi.mod->type;
    comment = mi.comment != nullptr ? mi.comment : "";

    xmp_scan_module(context);
    xmp_get_module_info(context, &mi);
    if (mi.num_sequences > 0 && mi.seq_data != nullptr && mi.seq_data[0].duration > 0) {
        duration = mi.seq_data[0].duration / 1000.0;
    }

    if (!startPlayerLocked()) {
        closeLocked();
        return false;
    }

    if (duration <= 0.0) {
        struct xmp_frame_info fi;
        xmp_get_frame_info(context, &fi);
        if (fi.total_time > 0) {
            duration = fi.total_time / 1000.0;
        }
    }

    toggleChannelNames.clear();
    toggleChannelMuted.assign(static_cast<size_t>(std::max(0, moduleChannels)), false);
    for (int channel = 0; channel < moduleChannels; ++channel) {
        toggleChannelNames.push_back("Ch " + std::to_string(channel + 1));
    }

    LOGD("Opened module: %s, type: %s, channels: %d, duration: %.2f",
         path, moduleType.c_str(), moduleChannels, duration);
    return true;
}

bool XmpDecoder::startPlayerLocked() {
    if (xmp_start_player(context, renderSampleRate, XMP_FORMAT_32BIT) != 0) {
        LOGE("xmp_start_player failed");
        return false;
    }
    applyOptionsLocked();
    ended = false;
    return true;
}

void XmpDecoder::applyOptionsLocked() {
    if (context == nullptr) return;
    xmp_set_player(context, XMP_PLAYER_INTERP, interpolationMode);
    if (stereoSeparationPercent >= 0) {
        xmp_set_player(context, XMP_PLAYER_MIX, stereoSeparationPercent);
    }
}

void XmpDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();
}

void XmpDecoder::closeLocked() {
    if (context != nullptr) {
        if (xmp_get_player(context, XMP_PLAYER_STATE) == XMP_STATE_PLAYING) {
            xmp_end_player(context);
        }
        xmp_release_module(context);
        xmp_free_context(context);
        context = nullptr;
    }
    fileBuffer.clear();
    fileBuffer.shrink_to_fit();
    duration = 0.0;
    moduleChannels = 0;
    ended = false;
    title.clear();
    moduleType.clear();
    comment.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
}

int XmpDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr || numFrames <= 0) return 0;
    if (ended) return 0;

    const int bytes = numFrames * 2 * static_cast<int>(sizeof(int32_t));
    mixBuffer.resize(static_cast<size_t>(numFrames) * 2);

    const int loopBudget = (repeatMode == 2) ? 0 : 1;
    int ret = xmp_play_buffer(context, mixBuffer.data(), bytes, loopBudget);
    if (ret < 0) {
        if (repeatMode == 2) {
            xmp_restart_module(context);
            xmp_play_buffer(context, nullptr, 0, 0);
            ret = xmp_play_buffer(context, mixBuffer.data(), bytes, loopBudget);
        }
        if (ret < 0) {
            ended = true;
            return 0;
        }
    }

    const int32_t* in = mixBuffer.data();
    const size_t totalSamples = static_cast<size_t>(numFrames) * 2;
    for (size_t i = 0; i < totalSamples; ++i) {
        buffer[i] = static_cast<float>(in[i]) * kInt32ToFloat;
    }
    return numFrames;
}

void XmpDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr) return;
    if (xmp_get_player(context, XMP_PLAYER_STATE) != XMP_STATE_PLAYING) return;
    const int ms = std::max(0, static_cast<int>(seconds * 1000.0));
    if (xmp_seek_time(context, ms) >= 0) {
        // Drop staged pre-seek frame data so it doesn't leak past the target.
        xmp_play_buffer(context, nullptr, 0, 0);
        ended = false;
    }
}

double XmpDecoder::getDuration() {
    return duration;
}

int XmpDecoder::getSampleRate() {
    return renderSampleRate;
}

int XmpDecoder::getBitDepth() {
    return 32;
}

std::string XmpDecoder::getBitDepthLabel() {
    return "32-bit Integer";
}

int XmpDecoder::getChannelCount() {
    return 2;
}

int XmpDecoder::getSourceChannelCount() {
    return moduleChannels > 0 ? moduleChannels : 2;
}

std::string XmpDecoder::getTitle() {
    return title;
}

std::string XmpDecoder::getArtist() {
    return "";
}

std::string XmpDecoder::getComment() {
    return comment;
}

void XmpDecoder::setOutputSampleRate(int sampleRateHz) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    renderSampleRate = sampleRateHz;
}

void XmpDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
    if (mode == 2) {
        ended = false;
    }
}

double XmpDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr) return -1.0;
    if (xmp_get_player(context, XMP_PLAYER_STATE) != XMP_STATE_PLAYING) return -1.0;
    struct xmp_frame_info fi;
    xmp_get_frame_info(context, &fi);
    return fi.time / 1000.0;
}

void XmpDecoder::setOption(const char* name, const char* value) {
    if (name == nullptr || value == nullptr) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string key(name);
    if (key == "xmp.interpolation") {
        const std::string mode(value);
        if (mode == "nearest") {
            interpolationMode = XMP_INTERP_NEAREST;
        } else if (mode == "spline") {
            interpolationMode = XMP_INTERP_SPLINE;
        } else {
            interpolationMode = XMP_INTERP_LINEAR;
        }
    } else if (key == "xmp.stereo_separation") {
        const int percent = std::clamp(parseIntString(value, -1), -100, 100);
        stereoSeparationPercent = percent;
    } else {
        return;
    }
    if (context != nullptr) {
        applyOptionsLocked();
    }
}

std::vector<std::string> XmpDecoder::getToggleChannelNames() {
    return toggleChannelNames;
}

void XmpDecoder::setToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr) return;
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return;
    toggleChannelMuted[static_cast<size_t>(channelIndex)] = enabled;
    xmp_channel_mute(context, channelIndex, enabled ? 1 : 0);
}

bool XmpDecoder::getToggleChannelMuted(int channelIndex) const {
    if (channelIndex < 0 || channelIndex >= static_cast<int>(toggleChannelMuted.size())) return false;
    return toggleChannelMuted[static_cast<size_t>(channelIndex)];
}

void XmpDecoder::clearToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr) return;
    for (size_t channel = 0; channel < toggleChannelMuted.size(); ++channel) {
        toggleChannelMuted[channel] = false;
        xmp_channel_mute(context, static_cast<int>(channel), 0);
    }
}

std::string XmpDecoder::getCoreStringInfo(const char* name) {
    if (name == nullptr) return "";
    const std::string key(name);
    if (key == "moduleTypeLong" || key == "tracker") return moduleType;
    if (key == "songMessage") return comment;
    return "";
}
