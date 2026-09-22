#include "AyflyDecoder.h"

#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <filesystem>
#include <fstream>
#include <type_traits>

#include <ayfly/ayfly.h>

#define LOG_TAG "AyflyDecoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
constexpr int kBytesPerFrame = 4; // 16-bit stereo
constexpr double kTicksPerSecond = 50.0;

static_assert(std::is_same<AY_CHAR, wchar_t>::value,
              "ayfly must be compiled with UNICODE/_UNICODE like the library");

std::string ayTextToUtf8(const wchar_t* text) {
    // bionic wide strings are UTF-32, so code points can be encoded directly.
    std::string out;
    if (text == nullptr) return out;
    for (const wchar_t* p = text; *p != L'\0'; ++p) {
        const unsigned int cp = static_cast<unsigned int>(*p);
        if (cp >= 0xD800 && cp <= 0xDFFF) continue;
        if (cp > 0x10FFFF) continue;
        if (cp < 0x80) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    return out;
}

int clampRenderRate(int sampleRateHz) {
    return std::clamp(sampleRateHz, 8000, 192000);
}
}

AyflyDecoder::AyflyDecoder() = default;

AyflyDecoder::~AyflyDecoder() {
    close();
}

// Called from inside ay_rendersongbuffer: returning true stops the pull loop
// at the end of the song, false loops back to the song's loop point.
bool AyflyDecoder::onSongElapsed(void* arg) {
    AyflyDecoder* self = static_cast<AyflyDecoder*>(arg);
    const bool loopForever = self->repeatMode == 2 || self->duration <= 0.0;
    return !loopForever;
}

bool AyflyDecoder::open(const char* path) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();

    if (path == nullptr || path[0] == '\0') return false;

    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open file: %s", path);
        return false;
    }
    const std::streamsize size = file.tellg();
    if (size <= 0) {
        LOGE("File is empty: %s", path);
        return false;
    }
    file.seekg(0, std::ios::beg);
    fileBuffer.resize(static_cast<size_t>(size));
    if (!file.read(fileBuffer.data(), size)) {
        LOGE("Failed to read file: %s", path);
        return false;
    }

    if (!createSongLocked(path)) {
        LOGE("Failed to detect song: %s", path);
        return false;
    }
    if (title.empty()) {
        title = std::filesystem::path(path).stem().string();
    }

    LOGD("Opened song: %s, duration: %.2f, channels: %d", path, duration, sourceChannels);
    return true;
}

bool AyflyDecoder::createSongLocked(const char* path) {
    renderSampleRate = clampRenderRate(requestedSampleRateHz);

    // In-memory loads carry no path, so detection needs the extension hint
    // that path loads fall back on when a content detector rejects a file.
    std::wstring typeHint;
    const std::string ext = std::filesystem::path(path).extension().string();
    if (!ext.empty() &&
        std::all_of(ext.begin(), ext.end(), [](unsigned char c) { return c < 0x80; })) {
        typeHint.assign(ext.begin(), ext.end());
    }

    song = ay_initsongindirect(
            reinterpret_cast<unsigned char*>(fileBuffer.data()),
            static_cast<unsigned long>(renderSampleRate),
            static_cast<unsigned long>(fileBuffer.size()),
            nullptr,
            typeHint.empty() ? nullptr : typeHint.c_str());
    if (song == nullptr) return false;

    title = ayTextToUtf8(ay_getsongname(song));
    artist = ayTextToUtf8(ay_getsongauthor(song));
    const unsigned long lengthTicks = ay_getsonglength(song);
    duration = lengthTicks > 0 ? static_cast<double>(lengthTicks) / kTicksPerSecond : 0.0;
    sourceChannels = ay_ists(song) ? 6 : 3;
    ended = false;
    ay_setelapsedcallback(song, &AyflyDecoder::onSongElapsed, this);
    return true;
}

void AyflyDecoder::close() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    closeLocked();
}

void AyflyDecoder::closeLocked() {
    if (song != nullptr) {
        ay_closesong(&song);
    }
    fileBuffer.clear();
    fileBuffer.shrink_to_fit();
    mixBuffer.clear();
    mixBuffer.shrink_to_fit();
    duration = 0.0;
    sourceChannels = 0;
    ended = false;
    title.clear();
    artist.clear();
}

int AyflyDecoder::read(float* buffer, int numFrames) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (song == nullptr || buffer == nullptr || numFrames <= 0) return 0;
    if (ended) return 0;

    const size_t totalBytes = static_cast<size_t>(numFrames) * kBytesPerFrame;
    if (mixBuffer.size() < totalBytes) {
        mixBuffer.resize(totalBytes);
    }

    // The renderer stops on a short buffer only when the song ran out, so a
    // zero return is the terminal event; anything else is decimation rounding.
    int framesFilled = 0;
    bool resumedAfterStop = false;
    while (framesFilled < numFrames) {
        const size_t offset = static_cast<size_t>(framesFilled) * kBytesPerFrame;
        const unsigned long written = ay_rendersongbuffer(
                song, mixBuffer.data() + offset,
                static_cast<unsigned long>(totalBytes - offset));
        if (written == 0) {
            // The stop flag clears only on seek, so entering loop-point repeat
            // after the song already stopped resumes from the loop point.
            if (repeatMode == 2 && !resumedAfterStop) {
                resumedAfterStop = true;
                ay_seeksong(song, static_cast<long>(ay_getsongloop(song)));
                continue;
            }
            ended = true;
            break;
        }
        framesFilled += static_cast<int>(written / kBytesPerFrame);
    }

    const int16_t* in = reinterpret_cast<const int16_t*>(mixBuffer.data());
    const int samples = framesFilled * 2;
    for (int i = 0; i < samples; ++i) {
        buffer[i] = static_cast<float>(in[i]) / 32768.0f;
    }
    return framesFilled;
}

void AyflyDecoder::seek(double seconds) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (song == nullptr) return;
    double target = std::max(0.0, seconds);
    if (duration > 0.0) {
        target = std::min(target, duration);
    }
    // Positions are 1/50s ticks; the library re-executes ticks to the target.
    ay_seeksong(song, static_cast<long>(std::llround(target * kTicksPerSecond)));
    ended = false;
}

double AyflyDecoder::getDuration() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return duration;
}

int AyflyDecoder::getSampleRate() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return renderSampleRate;
}

int AyflyDecoder::getBitDepth() {
    return 16;
}

std::string AyflyDecoder::getBitDepthLabel() {
    return "16-bit";
}

int AyflyDecoder::getChannelCount() {
    return 2;
}

int AyflyDecoder::getSourceChannelCount() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return sourceChannels > 0 ? sourceChannels : 3;
}

std::string AyflyDecoder::getTitle() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return title;
}

std::string AyflyDecoder::getArtist() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    return artist;
}

void AyflyDecoder::setOutputSampleRate(int sampleRate) {
    if (sampleRate <= 0) return;
    std::lock_guard<std::mutex> lock(decodeMutex);
    // Applied when the next song opens; the engine resamples to the output rate.
    requestedSampleRateHz = clampRenderRate(sampleRate);
}

void AyflyDecoder::setRepeatMode(int mode) {
    std::lock_guard<std::mutex> lock(decodeMutex);
    repeatMode = mode;
    if (mode == 2) {
        ended = false;
    }
}

double AyflyDecoder::getPlaybackPositionSeconds() {
    std::lock_guard<std::mutex> lock(decodeMutex);
    if (song == nullptr) return -1.0;
    return static_cast<double>(ay_getelapsedtime(song)) / kTicksPerSecond;
}
