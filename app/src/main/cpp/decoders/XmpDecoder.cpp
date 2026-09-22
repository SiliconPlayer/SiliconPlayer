#include "XmpDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <fstream>
#include <sstream>

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

// "N. name" per entry, 1-based, as consumed by the channel scope overlay.
template <typename T>
std::string joinIndexedNames(const T* entries, int count) {
    std::ostringstream out;
    for (int i = 0; i < count; ++i) {
        if (i > 0) out << '\n';
        out << (i + 1) << ". " << std::string(entries[i].name, strnlen(entries[i].name, 32));
    }
    return out.str();
}

// libxmp converts every effect to the FastTracker numbering. FastTracker and
// ProTracker share that layout, but S3M and IT place their effects elsewhere,
// so the codes are mapped back onto the letters those trackers display.
// Formats with yet another dialect (or with no letter at all) are shown as a
// hex code by the UI.
int effectLetterAscii(int effectType, int readEventType) {
    if (readEventType == XMP_READ_EVENT_ST3 || readEventType == XMP_READ_EVENT_IT) {
        switch (effectType) {
        case 0x00: case 0xb4: return 'J';       // arpeggio
        case 0x01: return 'F';                  // portamento up
        case 0x02: return 'E';                  // portamento down
        case 0x03: return 'G';                  // tone portamento
        case 0x04: return 'H';                  // vibrato
        case 0x05: return 'L';                  // tone portamento + volslide
        case 0x06: return 'K';                  // vibrato + volslide
        case 0x07: return 'R';                  // tremolo
        case 0x08: return 'X';                  // set panning
        case 0x09: return 'O';                  // sample offset
        case 0x0a: return 'D';                  // volume slide
        case 0x0b: return 'B';                  // pattern jump
        case 0x0c: case 0x80: return 'M';       // channel volume
        case 0x0d: case 0x8e: return 'C';       // pattern break
        case 0x0e: return 'S';                  // special
        case 0x0f: case 0xa3: return 'A';       // set speed
        case 0x10: return 'V';                  // global volume
        case 0x11: return 'W';                  // global volume slide
        case 0x19: case 0x89: return 'P';       // panning slide
        case 0x1b: return 'Q';                  // retrigger
        case 0x1d: return 'I';                  // tremor
        case 0x81: return 'N';                  // channel volume slide
        case 0x83: case 0x88: case 0x8d: return 'S';
        case 0x84: case 0x85: return 'Z';       // filter
        case 0x87: case 0xab: return 'T';       // set tempo
        case 0x8a: case 0x8b: return 'Y';       // panbrello
        case 0xac: return 'U';                  // fine vibrato
        case 0xbd: case 0xbe: case 0xbf: return 'Z';    // MIDI macros
        default: return 0x100 | (effectType & 0xff);
        }
    }
    if (readEventType != XMP_READ_EVENT_MOD && readEventType != XMP_READ_EVENT_FT2) {
        return 0x100 | (effectType & 0xff);
    }
    if (effectType <= 0x0f) return "0123456789ABCDEF"[effectType];
    if (effectType <= 0x21) return 'G' + (effectType - 0x10);
    return 0x100 | (effectType & 0xff);
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
    moduleInstruments = mi.mod->ins;
    moduleSamples = mi.mod->smp;
    title = mi.mod->name;
    moduleType = mi.mod->type;
    comment = mi.comment != nullptr ? mi.comment : "";
    instrumentNames = joinIndexedNames(mi.mod->xxi, mi.mod->ins);
    sampleNames = joinIndexedNames(mi.mod->xxs, mi.mod->smp);

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

    // Probe the internal Amiga check through the public API: with A500 forced,
    // the reported mixer is non-standard only for Amiga MODs. Most player
    // params reject calls before the playing state, so probe only here.
    isAmigaModule = false;
    const int savedFlags = xmp_get_player(context, XMP_PLAYER_CFLAGS);
    if (savedFlags >= 0) {
        const int probeFlags = (savedFlags | XMP_FLAGS_A500) & ~XMP_FLAGS_A1200;
        xmp_set_player(context, XMP_PLAYER_CFLAGS, probeFlags);
        isAmigaModule = xmp_get_player(context, XMP_PLAYER_MIXER_TYPE) != XMP_MIXER_STANDARD;
        xmp_set_player(context, XMP_PLAYER_CFLAGS, savedFlags);
    }

    xmp_set_player(context, XMP_PLAYER_CHANNEL_SCOPE, 1);
    readEventType = xmp_get_player(context, XMP_PLAYER_READ_EVENT_TYPE);
    applyOptionsLocked();
    ended = false;
    return true;
}

void XmpDecoder::applyOptionsLocked() {
    if (context == nullptr) return;
    xmp_set_player(context, XMP_PLAYER_INTERP, interpolationMode);
    xmp_set_player(context, XMP_PLAYER_MIX,
                   isAmigaModule ? amigaStereoSeparationPercent : stereoSeparationPercent);
    int flags = xmp_get_player(context, XMP_PLAYER_CFLAGS);
    flags &= ~(XMP_FLAGS_A500 | XMP_FLAGS_A1200);
    if (amigaModel == 1) {
        flags |= XMP_FLAGS_A500;
    } else if (amigaModel == 2) {
        flags |= XMP_FLAGS_A1200;
    }
    xmp_set_player(context, XMP_PLAYER_CFLAGS, flags);
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
    instrumentNames.clear();
    sampleNames.clear();
    toggleChannelNames.clear();
    toggleChannelMuted.clear();
    isAmigaModule = false;
    readEventType = XMP_READ_EVENT_MOD;
    channelScopeSourceSerial = 0;
    channelScopeLastReadNs = 0;
    if (channelScopeState) {
        channelScopeState->clear();
    }
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

    channelScopeLastReadNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    if (!channelScopeState ||
        channelScopeState->tryBeginCapture(channelScopeLastReadNs, moduleChannels)) {
        channelScopeSourceSerial++;
        captureChannelScopeSnapshotLocked();
    }
    return numFrames;
}

void XmpDecoder::captureChannelScopeSnapshotLocked() {
    if (!channelScopeState || context == nullptr) return;

    const int totalChannels = std::clamp(moduleChannels, 0, 64);
    if (totalChannels <= 0) return;

    const int maxSamples = ChannelScopeSharedState::kMaxSamples;
    thread_local std::vector<float> scratchRaw;
    thread_local std::vector<float> scratchVu;
    scratchRaw.resize(static_cast<size_t>(totalChannels) * maxSamples);
    scratchVu.resize(static_cast<size_t>(totalChannels));

    for (int ch = 0; ch < totalChannels; ++ch) {
        float* dest = scratchRaw.data() + static_cast<size_t>(ch) * maxSamples;
        if (xmp_get_channel_scope(context, ch, dest, maxSamples) <= 0) {
            std::fill(dest, dest + maxSamples, 0.0f);
            scratchVu[static_cast<size_t>(ch)] = 0.0f;
            continue;
        }
        // VU from the newest ~40ms; the full window is ring history.
        const int vuWindow = std::min(maxSamples, 2048);
        float peak = 0.0f;
        for (int i = maxSamples - vuWindow; i < maxSamples; ++i) {
            peak = std::max(peak, std::fabs(dest[i]));
        }
        scratchVu[static_cast<size_t>(ch)] = std::min(peak, 1.0f);
    }

    channelScopeState->publish(scratchRaw, scratchVu, totalChannels, channelScopeSourceSerial, true);
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
        stereoSeparationPercent = std::clamp(parseIntString(value, 100), -100, 100);
    } else if (key == "xmp.amiga_stereo_separation") {
        amigaStereoSeparationPercent = std::clamp(parseIntString(value, 100), -100, 100);
    } else if (key == "xmp.amiga_model") {
        amigaModel = std::clamp(parseIntString(value, 0), 0, 2);
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
    std::lock_guard<std::mutex> lock(decodeMutex);
    const std::string key(name);
    if (key == "moduleTypeLong" || key == "tracker") return moduleType;
    if (key == "songMessage") return comment;
    if (key == "instrumentNames") return instrumentNames;
    if (key == "sampleNames") return sampleNames;
    return "";
}

std::vector<int32_t> XmpDecoder::getChannelScopeTextState(int maxChannels) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (context == nullptr) return {};
    if (xmp_get_player(context, XMP_PLAYER_STATE) < XMP_STATE_PLAYING) return {};

    const int totalChannels = std::clamp(moduleChannels, 0, 64);
    if (totalChannels <= 0) return {};
    const int channels = std::min(totalChannels, std::clamp(maxChannels, 1, 64));

    struct xmp_frame_info fi;
    xmp_get_frame_info(context, &fi);

    constexpr int kTextStride = 10;
    constexpr int kFlagActive = 1 << 0;
    constexpr int kFlagAmigaLeft = 1 << 1;
    constexpr int kFlagAmigaRight = 1 << 2;

    std::vector<int32_t> flat(static_cast<size_t>(channels) * kTextStride, -1);
    for (int channel = 0; channel < channels; ++channel) {
        const struct xmp_channel_info& ci = fi.channel_info[channel];
        const size_t base = static_cast<size_t>(channel) * kTextStride;

        // libxmp keys are OpenMPT note numbers minus one, and OpenMPT numbering
        // is what the overlay formatter expects.
        const int note = (ci.note >= 1 && ci.note <= 119) ? ci.note + 1 : -1;
        const int volume = std::clamp(static_cast<int>(ci.volume), 0, 64) * 4;
        int effectLetter = 0;
        int effectParam = -1;
        if (ci.event.fxt != 0 || ci.event.fxp != 0) {
            effectLetter = effectLetterAscii(ci.event.fxt, readEventType);
            effectParam = ci.event.fxp;
        }
        // The instrument and sample fields are unsigned, with any byte above
        // the module's count meaning "none".
        const int instrument = static_cast<int>(ci.instrument) < moduleInstruments
                ? static_cast<int>(ci.instrument) + 1 : -1;
        const int sample = static_cast<int>(ci.sample) < moduleSamples
                ? static_cast<int>(ci.sample) + 1 : -1;

        int flags = 0;
        if (note > 0 || volume > 0 || instrument > 0 || sample > 0) {
            flags |= kFlagActive;
        }
        if (isAmigaModule) {
            // ProTracker-style hard panning map per 4-channel group: L R R L.
            const int mod4 = channel & 3;
            if (mod4 == 0 || mod4 == 3) {
                flags |= kFlagAmigaLeft;
            } else {
                flags |= kFlagAmigaRight;
            }
        }

        flat[base + 0] = channel;
        flat[base + 1] = note;
        flat[base + 2] = volume;
        flat[base + 3] = effectLetter;
        flat[base + 4] = effectParam;
        flat[base + 5] = 0;
        flat[base + 6] = -1;
        flat[base + 7] = instrument;
        flat[base + 8] = sample;
        flat[base + 9] = flags;
    }
    return flat;
}
