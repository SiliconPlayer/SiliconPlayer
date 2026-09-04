#include "AudioEngine.h"

#include <android/log.h>
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <dlfcn.h>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <thread>
#include <unistd.h>
#include <vector>
#include "usb/UacDriver.h"

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {
    pid_t currentThreadId() {
#ifdef SYS_gettid
        return static_cast<pid_t>(syscall(SYS_gettid));
#else
        return getpid();
#endif
    }

    void promoteThreadForAudio(const char* role, int targetNice) {
        const pid_t tid = currentThreadId();
        const int before = getpriority(PRIO_PROCESS, tid);
        errno = 0;
        if (setpriority(PRIO_PROCESS, tid, targetNice) == 0) {
            const int after = getpriority(PRIO_PROCESS, tid);
            LOGD(
                    "Thread priority promoted for %s: tid=%d nice(before=%d after=%d target=%d)",
                    role,
                    static_cast<int>(tid),
                    before,
                    after,
                    targetNice
            );
            return;
        }

        const int err = errno;
        LOGD(
                "Thread priority promotion skipped for %s: tid=%d targetNice=%d errno=%d",
                role,
                static_cast<int>(tid),
                targetNice,
                err
        );
    }

    int miniaudioPeriodFramesForPreset(int bufferPreset) {
        switch (bufferPreset) {
            case 0:
                return 256;
            case 1:
                return 512;
            case 2:
                return 1024;
            case 3:
                return 2048;
            case 4:
                return 4096;
            default:
                return 2048;
        }
    }

    ma_backend toMiniaudioBackend(int backendPreference) {
        switch (backendPreference) {
            case 1: return ma_backend_aaudio;
            case 2: return ma_backend_opensl;
            case 3: return ma_backend_wasapi;
            case 4: return ma_backend_dsound;
            case 5: return ma_backend_winmm;
            case 6: return ma_backend_coreaudio;
            case 7: return ma_backend_alsa;
            case 8: return ma_backend_pulseaudio;
            case 9: return ma_backend_jack;
            case 10: return ma_backend_sndio;
            case 11: return ma_backend_audio4;
            case 12: return ma_backend_oss;
            case 13: return ma_backend_null;
            case 0:
            default:
                return ma_backend_null;
        }
    }
}

bool AudioEngine::createMiniaudioStream() {
    closeMiniaudioStream();

    std::vector<ma_backend> backends;
    if (outputBackendPreference != 0) {
        const ma_backend preferred = toMiniaudioBackend(outputBackendPreference);
        if (preferred != ma_backend_null || outputBackendPreference == 13) {
            backends.push_back(preferred);
        }

        if (outputAllowFallback) {
            const ma_backend allBackends[] = {
                ma_backend_aaudio,
                ma_backend_opensl,
                ma_backend_alsa,
                ma_backend_pulseaudio,
                ma_backend_jack,
                ma_backend_wasapi,
                ma_backend_dsound,
                ma_backend_winmm,
                ma_backend_coreaudio,
                ma_backend_sndio,
                ma_backend_audio4,
                ma_backend_oss,
                ma_backend_null
            };
            for (const auto b : allBackends) {
                if (b != preferred && ma_is_backend_enabled(b)) {
                    backends.push_back(b);
                }
            }
        }
    }

    ma_context_config contextConfig = ma_context_config_init();
    ma_result result = ma_context_init(
            backends.empty() ? nullptr : backends.data(),
            static_cast<ma_uint32>(backends.size()),
            &contextConfig,
            &miniaudioContext
    );

    if (result != MA_SUCCESS) {
        LOGE("Failed to initialize Miniaudio context: result=%d (pref=%d allowFallback=%d)",
             static_cast<int>(result), outputBackendPreference, outputAllowFallback ? 1 : 0);
        miniaudioContextInitialized = false;
        outputStreamReady.store(false, std::memory_order_relaxed);
        activeOutputBackend.store(0, std::memory_order_relaxed);
        return false;
    }
    miniaudioContextInitialized = true;
    activeMiniaudioBackend = miniaudioContext.backend;

    ma_device_config deviceConfig = ma_device_config_init(ma_device_type_playback);
    deviceConfig.playback.format = ma_format_f32;
    deviceConfig.playback.channels = 2;
    deviceConfig.playback.shareMode = bitPerfectModeEnabled ? ma_share_mode_exclusive : ma_share_mode_shared;
    deviceConfig.aaudio.usage = ma_aaudio_usage_media;
    deviceConfig.aaudio.contentType = ma_aaudio_content_type_music;
    auto& uac = siliconplayer::usb::getUacDriverInstance();
    if (uac.isStreaming()) {
        deviceConfig.sampleRate = static_cast<ma_uint32>(uac.currentFormat().sampleRateHz);
        deviceConfig.playback.channels = static_cast<ma_uint32>(uac.currentFormat().channels);
    } else if (bitPerfectModeEnabled && decoderRenderSampleRate > 0) {
        deviceConfig.sampleRate = static_cast<ma_uint32>(decoderRenderSampleRate);
    } else {
        deviceConfig.sampleRate = 0;
    }
    deviceConfig.dataCallback = miniaudioDataCallback;
    deviceConfig.stopCallback = miniaudioStopCallback;
    deviceConfig.pUserData = this;

    if (outputPerformanceMode == 0) {
        deviceConfig.performanceProfile = (outputBufferPreset >= 3)
                ? ma_performance_profile_conservative
                : ma_performance_profile_low_latency;
    } else if (outputPerformanceMode == 1) {
        deviceConfig.performanceProfile = ma_performance_profile_low_latency;
    } else {
        deviceConfig.performanceProfile = ma_performance_profile_conservative;
    }

    const int periodFrames = miniaudioPeriodFramesForPreset(outputBufferPreset);
    deviceConfig.periodSizeInFrames = static_cast<ma_uint32>(periodFrames);
    deviceConfig.periods = 2;

    result = ma_device_init(&miniaudioContext, &deviceConfig, &miniaudioDevice);
    if (result != MA_SUCCESS && (deviceConfig.playback.shareMode == ma_share_mode_exclusive || deviceConfig.sampleRate != 0)) {
        LOGW("Miniaudio init failed (rate=%u, shareMode=%d, result=%d), retrying with shared native mode",
             deviceConfig.sampleRate, static_cast<int>(deviceConfig.playback.shareMode), static_cast<int>(result));
        deviceConfig.playback.shareMode = ma_share_mode_shared;
        deviceConfig.sampleRate = 0;
        result = ma_device_init(&miniaudioContext, &deviceConfig, &miniaudioDevice);
    }
    if (result != MA_SUCCESS) {
        LOGE("Failed to initialize Miniaudio device: result=%d (backend=%s)",
             static_cast<int>(result), ma_get_backend_name(activeMiniaudioBackend));
        ma_context_uninit(&miniaudioContext);
        miniaudioContextInitialized = false;
        miniaudioDeviceInitialized = false;
        outputStreamReady.store(false, std::memory_order_relaxed);
        activeOutputBackend.store(0, std::memory_order_relaxed);
        return false;
    }

    miniaudioDeviceInitialized = true;
    auto& uacInstance = siliconplayer::usb::getUacDriverInstance();
    if (uacInstance.isStreaming()) {
        streamSampleRate = uacInstance.currentFormat().sampleRateHz;
        streamChannelCount = uacInstance.currentFormat().channels;
    } else {
        streamSampleRate = miniaudioDevice.sampleRate > 0 ? static_cast<int>(miniaudioDevice.sampleRate) : 48000;
        streamChannelCount = miniaudioDevice.playback.channels > 0 ? static_cast<int>(miniaudioDevice.playback.channels) : 2;
    }
    miniaudioBufferFrames = miniaudioDevice.playback.internalPeriodSizeInFrames > 0
            ? static_cast<int>(miniaudioDevice.playback.internalPeriodSizeInFrames)
            : periodFrames;
    streamStartupPrerollPending = true;
    outputStreamReady.store(true, std::memory_order_relaxed);
    activeOutputBackend.store(static_cast<int>(miniaudioContext.backend) + 1, std::memory_order_relaxed);

    LOGD("Miniaudio stream opened: backend=%s(%d), sampleRate=%d, channels=%d, bufferFrames=%d, perfProfile=%d, allowFallback=%d",
         ma_get_backend_name(activeMiniaudioBackend),
         static_cast<int>(activeMiniaudioBackend),
         streamSampleRate,
         streamChannelCount,
         miniaudioBufferFrames,
         static_cast<int>(deviceConfig.performanceProfile),
         outputAllowFallback ? 1 : 0);

    return true;
}

void AudioEngine::closeMiniaudioStream() {
    intentionalStreamTeardown.store(true, std::memory_order_release);
    if (miniaudioDeviceInitialized) {
        ma_device_uninit(&miniaudioDevice);
        miniaudioDeviceInitialized = false;
    }
    if (miniaudioContextInitialized) {
        ma_context_uninit(&miniaudioContext);
        miniaudioContextInitialized = false;
    }
    activeMiniaudioBackend = ma_backend_null;
    activeOutputBackend.store(0, std::memory_order_relaxed);
    outputStreamReady.store(false, std::memory_order_relaxed);
    intentionalStreamTeardown.store(false, std::memory_order_release);
}

void AudioEngine::createStream() {
    std::lock_guard<std::mutex> lock(deviceMutex);
    createMiniaudioStream();
}

void AudioEngine::closeStream() {
    std::lock_guard<std::mutex> lock(deviceMutex);
    closeMiniaudioStream();
}

void AudioEngine::syncUacStreamRate(int sampleRateHz, int channels) {
    if (sampleRateHz <= 0) return;
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (streamSampleRate != sampleRateHz || streamChannelCount != channels) {
        clearRenderQueue();
        streamSampleRate = sampleRateHz;
        streamChannelCount = channels > 0 ? channels : 2;
        if (decoder) {
            const bool supportsLiveRateChange =
                    (decoder->getPlaybackCapabilities() & AudioDecoder::PLAYBACK_CAP_LIVE_SAMPLE_RATE_CHANGE) != 0;
            if (supportsLiveRateChange) {
                decoder->setOutputSampleRate(sampleRateHz);
                decoderRenderSampleRate = decoder->getRenderSampleRate();
                resetResamplerStateLocked(true);
            }
        }
    }
}

bool AudioEngine::requestStreamStart() {
    if (!outputStreamReady.load(std::memory_order_relaxed) || !miniaudioDeviceInitialized) {
        return false;
    }

    std::lock_guard<std::mutex> lock(deviceMutex);
    if (!miniaudioDeviceInitialized) {
        return false;
    }
    const ma_result result = ma_device_start(&miniaudioDevice);
    if (result != MA_SUCCESS) {
        LOGE("Failed to start Miniaudio device: result=%d", static_cast<int>(result));
        return false;
    }
    return true;
}

void AudioEngine::requestStreamStop() {
    std::lock_guard<std::mutex> lock(deviceMutex);
    if (!miniaudioDeviceInitialized) {
        return;
    }
    ma_device_stop(&miniaudioDevice);
}

bool AudioEngine::isStreamDisconnectedOrClosed() const {
    if (!outputStreamReady.load(std::memory_order_relaxed) || !miniaudioDeviceInitialized) {
        return true;
    }
    const ma_device_state state = ma_device_get_state(const_cast<ma_device*>(&miniaudioDevice));
    return state == ma_device_state_stopped || state == ma_device_state_uninitialized;
}

int AudioEngine::getStreamBurstFrames() const {
    if (miniaudioDeviceInitialized && miniaudioDevice.playback.internalPeriodSizeInFrames > 0) {
        return static_cast<int>(miniaudioDevice.playback.internalPeriodSizeInFrames);
    }
    return miniaudioBufferFrames;
}

std::string AudioEngine::getAudioBackendLabel() const {
    if (!isPlaying.load(std::memory_order_relaxed)) {
        return "(inactive)";
    }
    if (!miniaudioContextInitialized) {
        return "Unknown";
    }
    if (activeMiniaudioBackend == ma_backend_opensl) {
        return "OpenSL ES";
    }
    const char* name = ma_get_backend_name(activeMiniaudioBackend);
    return name ? name : "Unknown";
}

void AudioEngine::setBitPerfectMode(bool enabled) {
    if (bitPerfectModeEnabled == enabled) return;
    bitPerfectModeEnabled = enabled;
    LOGD("Bit-perfect mode changed: %d", enabled ? 1 : 0);
    if (!enabled) {
        auto& uac = siliconplayer::usb::getUacDriverInstance();
        uac.stop();
        if (isPlaying.load()) {
            std::thread([this]() {
                pthread_setname_np(pthread_self(), "sp_bitperf_off");
                usleep(150000);
                if (isPlaying.load() && !bitPerfectModeEnabled) {
                    reconfigureStream(true);
                }
            }).detach();
            return;
        }
    }
    if (isPlaying.load()) {
        reconfigureStream(true);
    } else {
        streamNeedsRebuild.store(true);
    }
}

void AudioEngine::reconfigureStream(bool resumePlayback) {
    // Serializes device teardown/rebuild against start()/stop()/setUrl(); two
    // concurrent closeStream/createStream cycles corrupt the ma_device.
    std::lock_guard<std::mutex> lifecycleLock(lifecycleMutex);
    const bool shouldResume = resumePlayback && isPlaying.load();
    playbackStreamStarted.store(false, std::memory_order_release);
    requestStreamStop();
    isPlaying.store(false);

    const int previousSampleRate = streamSampleRate;

    closeStream();
    createStream();

    if (streamSampleRate != previousSampleRate) {
        clearRenderQueue();
    }

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getRenderSampleRate();
            resetResamplerStateLocked(true);
        }
    }

    if (!shouldResume) {
        return;
    }

    naturalEndPending.store(false);
    isPlaying.store(true);
    renderWorkerCv.notify_all();

    if (requestStreamStart()) {
        streamStartupPrerollPending = false;
        playbackStreamStarted.store(true, std::memory_order_release);
        renderWorkerCv.notify_all();
        return;
    }

    LOGE("Reconfigure resume start failed");
    isPlaying.store(false);
    playbackStreamStarted.store(false, std::memory_order_release);
}

void AudioEngine::miniaudioStopCallback(ma_device* pDevice) {
    auto* engine = static_cast<AudioEngine*>(pDevice->pUserData);
    if (!engine) return;
    LOGD("Miniaudio device stop callback received (isPlaying=%d)", engine->isPlaying.load() ? 1 : 0);
    if (engine->intentionalStreamTeardown.load(std::memory_order_acquire)) {
        return;
    }
    auto& uac = siliconplayer::usb::getUacDriverInstance();
    if (uac.isStreaming()) {
        return;
    }
    if (engine->isPlaying.load()) {
        engine->streamNeedsRebuild.store(true);
        std::thread([engine]() {
            pthread_setname_np(pthread_self(), "sp_recover");
            usleep(150000);
            if (engine->isPlaying.load() && !engine->intentionalStreamTeardown.load()) {
                engine->reconfigureStream(true);
            }
        }).detach();
    }
}

void AudioEngine::miniaudioDataCallback(
        ma_device* pDevice,
        void* pOutput,
        const void* /*pInput*/,
        ma_uint32 frameCount) {
    static thread_local bool callbackPriorityPromoted = false;
    if (!callbackPriorityPromoted) {
        pthread_setname_np(pthread_self(), "sp_miniaudio");
        promoteThreadForAudio("miniaudio-callback", -16);
        callbackPriorityPromoted = true;
    }
    auto* engine = static_cast<AudioEngine*>(pDevice->pUserData);
    if (!engine || !pOutput || frameCount == 0) return;

    auto& uac = siliconplayer::usb::getUacDriverInstance();
    if (uac.isStreaming()) {
        std::memset(pOutput, 0, frameCount * 2 * sizeof(float));
        return;
    }

    auto* outputData = static_cast<float*>(pOutput);
    const int callbackRate = pDevice->sampleRate > 0
            ? static_cast<int>(pDevice->sampleRate)
            : (engine->streamSampleRate > 0 ? engine->streamSampleRate : 48000);

    engine->renderOutputCallbackFrames(outputData, static_cast<int32_t>(frameCount), callbackRate);
}

int AudioEngine::provideUacDirectFrames(uint8_t* dst, int maxFrames, const siliconplayer::usb::StreamFormat& fmt) {
    if (!dst || maxFrames <= 0) return 0;

    static thread_local std::vector<float> stagingBuf;
    static thread_local size_t stagingReadPos = 0;
    static thread_local size_t stagingAvailableFrames = 0;
    static thread_local int lastSampleRate = 0;

    if (!isPlaying.load(std::memory_order_relaxed) || !playbackStreamStarted.load(std::memory_order_acquire)) {
        std::memset(dst, 0, maxFrames * fmt.channels * fmt.bytesPerSample);
        stagingReadPos = 0;
        stagingAvailableFrames = 0;
        return 0;
    }

    if (lastSampleRate != fmt.sampleRateHz) {
        stagingReadPos = 0;
        stagingAvailableFrames = 0;
        lastSampleRate = fmt.sampleRateHz;
    }

    const int ch = fmt.channels > 0 ? fmt.channels : 2;
    const int subslot = fmt.bytesPerSample > 0 ? fmt.bytesPerSample : (fmt.bitsPerSample / 8);
    auto& uac = siliconplayer::usb::getUacDriverInstance();
    const float volScale = uac.volumeScale();
    const bool applyVol = (volScale < 0.9999f || volScale > 1.0001f);

    int framesFilled = 0;
    uint8_t* outCursor = dst;

    while (framesFilled < maxFrames) {
        if (stagingAvailableFrames == 0) {
            constexpr int kBatchFrames = 512;
            if (stagingBuf.size() < static_cast<size_t>(kBatchFrames * 2)) {
                stagingBuf.resize(kBatchFrames * 2);
            }
            int actualCopied = 0;
            renderOutputCallbackFrames(stagingBuf.data(), kBatchFrames, fmt.sampleRateHz, &actualCopied);
            if (actualCopied <= 0) {
                int missing = maxFrames - framesFilled;
                std::memset(outCursor, 0, missing * ch * subslot);
                return maxFrames;
            }
            stagingReadPos = 0;
            stagingAvailableFrames = static_cast<size_t>(actualCopied);
        }

        int chunk = std::min(maxFrames - framesFilled, static_cast<int>(stagingAvailableFrames));
        const float* src = stagingBuf.data() + (stagingReadPos * 2);

        if (subslot == 2) {
            auto* out16 = reinterpret_cast<int16_t*>(outCursor);
            for (int i = 0; i < chunk * ch; ++i) {
                float s = src[i];
                if (applyVol) s *= volScale;
                s = std::clamp(s, -1.0f, 1.0f);
                out16[i] = static_cast<int16_t>(s * 32767.0f);
            }
        } else if (subslot == 3) {
            for (int i = 0; i < chunk * ch; ++i) {
                float s = src[i];
                if (applyVol) s *= volScale;
                s = std::clamp(s, -1.0f, 1.0f);
                int32_t s24 = static_cast<int32_t>(s * 8388607.0f);
                outCursor[i * 3 + 0] = static_cast<uint8_t>(s24 & 0xFF);
                outCursor[i * 3 + 1] = static_cast<uint8_t>((s24 >> 8) & 0xFF);
                outCursor[i * 3 + 2] = static_cast<uint8_t>((s24 >> 16) & 0xFF);
            }
        } else if (subslot == 4) {
            auto* out32 = reinterpret_cast<int32_t*>(outCursor);
            if (fmt.bitsPerSample == 24) {
                for (int i = 0; i < chunk * ch; ++i) {
                    float s = src[i];
                    if (applyVol) s *= volScale;
                    s = std::clamp(s, -1.0f, 1.0f);
                    out32[i] = static_cast<int32_t>(s * 8388607.0f) << 8;
                }
            } else {
                for (int i = 0; i < chunk * ch; ++i) {
                    float s = src[i];
                    if (applyVol) s *= volScale;
                    s = std::clamp(s, -1.0f, 1.0f);
                    out32[i] = static_cast<int32_t>(s * 2147483647.0f);
                }
            }
        }

        stagingReadPos += static_cast<size_t>(chunk);
        stagingAvailableFrames -= static_cast<size_t>(chunk);
        framesFilled += chunk;
        outCursor += chunk * ch * subslot;
    }

    return maxFrames;
}

void AudioEngine::recoverStreamIfNeeded() {
    if (!streamNeedsRebuild.load()) {
        return;
    }
    // Never block the polling thread behind a lifecycle op; the rebuild is
    // retried on the next poll if one is in flight.
    std::unique_lock<std::mutex> lock(lifecycleMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        return;
    }
    recoverStreamIfNeededLocked();
}

void AudioEngine::recoverStreamIfNeededLocked() {
    if (!streamNeedsRebuild.load()) {
        return;
    }

    const int previousSampleRate = streamSampleRate;

    closeStream();
    createStream();
    streamNeedsRebuild.store(false);

    if (streamSampleRate != previousSampleRate) {
        clearRenderQueue();
    }

    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (decoder) {
            const int desiredRate = resolveOutputSampleRateForCore(decoder->getName());
            decoder->setOutputSampleRate(desiredRate);
            decoderRenderSampleRate = decoder->getRenderSampleRate();
            resetResamplerStateLocked(true);
        }
    }

    if (resumeAfterRebuild.load()) {
        resumeAfterRebuild.store(false);
        if (requestStreamStart()) {
            isPlaying.store(true);
        }
    }
}

bool AudioEngine::renderOutputCallbackFrames(float* outputData, int32_t numFrames, int callbackRate, int* outFramesCopied) {
    if (!outputData || numFrames <= 0) {
        if (outFramesCopied) *outFramesCopied = 0;
        return false;
    }

    if (seekInProgress.load()) {
        std::memset(outputData, 0, static_cast<size_t>(numFrames) * 2u * sizeof(float));
        if (outFramesCopied) *outFramesCopied = 0;
        return false;
    }

    if (pendingResumeFadeOnStart.exchange(false, std::memory_order_relaxed)) {
        beginPauseResumeFadeLocked(
                true,
                callbackRate > 0 ? callbackRate : 48000,
                pendingResumeFadeDurationMs.load(std::memory_order_relaxed),
                pendingResumeFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }
    if (pendingPauseFadeRequest.exchange(false, std::memory_order_relaxed)) {
        beginPauseResumeFadeLocked(
                false,
                callbackRate > 0 ? callbackRate : 48000,
                pendingPauseFadeDurationMs.load(std::memory_order_relaxed),
                pendingPauseFadeAttenuationDb.load(std::memory_order_relaxed)
        );
    }

    const bool activePlayback = isPlaying.load(std::memory_order_relaxed);
    if (activePlayback) {
        renderQueueCallbackCount.fetch_add(1, std::memory_order_relaxed);
    }
    const int framesCopied = popRenderQueue(outputData, numFrames, 2);
    if (outFramesCopied) *outFramesCopied = framesCopied;
    if (activePlayback && framesCopied < numFrames) {
        const uint64_t missingFrames = static_cast<uint64_t>(numFrames - framesCopied);
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        // Hold a higher queue target briefly after underrun to absorb transient CPU spikes
        // during app-switch/system UI animations.
        renderQueueRecoveryBoostUntilNs.store(
                nowNs + 2500000000LL,
                std::memory_order_relaxed
        );
        renderQueueUnderrunCount.fetch_add(1, std::memory_order_relaxed);
        renderQueueUnderrunFrames.fetch_add(missingFrames, std::memory_order_relaxed);
#ifndef NDEBUG
        const int64_t previousLogNs = renderQueueLastUnderrunLogNs.load(std::memory_order_relaxed);
        if (nowNs - previousLogNs > 1000000000LL) {
            const uint64_t underruns = renderQueueUnderrunCount.load(std::memory_order_relaxed);
            const uint64_t underrunFrames = renderQueueUnderrunFrames.load(std::memory_order_relaxed);
            const uint64_t callbacks = renderQueueCallbackCount.load(std::memory_order_relaxed);
            LOGD(
                    "Render queue underrun: missing=%llu callbacks=%llu underruns=%llu totalMissingFrames=%llu bufferedFrames=%d",
                    static_cast<unsigned long long>(missingFrames),
                    static_cast<unsigned long long>(callbacks),
                    static_cast<unsigned long long>(underruns),
                    static_cast<unsigned long long>(underrunFrames),
                    renderQueueFrames()
            );
            renderQueueLastUnderrunLogNs.store(nowNs, std::memory_order_relaxed);
        }
#endif
        std::memset(
                outputData + (static_cast<size_t>(framesCopied) * 2u),
                0,
                static_cast<size_t>(numFrames - framesCopied) * 2u * sizeof(float)
        );
    }

    if (pauseResumeFadeTotalFrames > 0) {
        for (int frame = 0; frame < numFrames; ++frame) {
            const float fadeGain = nextPauseResumeFadeGainLocked();
            if (fadeGain == 1.0f) continue;
            const size_t base = static_cast<size_t>(frame) * 2u;
            outputData[base] *= fadeGain;
            outputData[base + 1u] *= fadeGain;
        }
    }

    const size_t totalSamples = static_cast<size_t>(numFrames) * 2u;
    for (size_t i = 0; i < totalSamples; ++i) {
        outputData[i] = std::clamp(outputData[i], -1.0f, 1.0f);
    }

    uint32_t requestedFeatures = 0u;
    if (shouldUpdateVisualization(&requestedFeatures)) {
        updateVisualizationDataFromOutputCallback(
                outputData,
                numFrames,
                2,
                requestedFeatures
        );
    }

    if (pauseResumeFadeOutStopPending) {
        pauseResumeFadeOutStopPending = false;
        isPlaying.store(false);
        naturalEndPending.store(false);
        clearRenderQueue();
        renderWorkerCv.notify_all();
        return true;
    }

    if (renderTerminalStopPending.load() && renderQueueFrames() <= 0) {
        renderTerminalStopPending.store(false);
        return true;
    }

    const int bufferedFrames = renderQueueFrames();
    const bool backgroundHeadroomActive = backgroundPlaybackMode.load(std::memory_order_relaxed);
    const int configuredChunkFrames = std::max(256, renderWorkerChunkFrames.load(std::memory_order_relaxed));
    const int targetFramesBase = std::max(
            configuredChunkFrames * 2,
            renderWorkerTargetFrames.load(std::memory_order_relaxed)
    );
    int targetFramesHint = backgroundHeadroomActive
            ? std::max(targetFramesBase * 2, std::max(configuredChunkFrames, 1024) * 2)
            : targetFramesBase;
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    if (nowNs < renderQueueRecoveryBoostUntilNs.load(std::memory_order_relaxed)) {
        targetFramesHint = std::max(
                targetFramesHint,
                targetFramesHint * (backgroundHeadroomActive ? 2 : 4)
        );
    }
    if (framesCopied < numFrames || bufferedFrames < targetFramesHint) {
        renderWorkerCv.notify_one();
    }
    return false;
}

int AudioEngine::getAudioSessionId() const {
    if (!miniaudioContextInitialized || !outputStreamReady.load()) {
        return -1;
    }
#if defined(MA_SUPPORT_AAUDIO)
    if (activeMiniaudioBackend == ma_backend_aaudio) {
        void* stream = miniaudioDevice.aaudio.pStreamPlayback;
        if (stream) {
            typedef int32_t (*PFN_AAudioStream_getSessionId)(void* stream);
            static PFN_AAudioStream_getSessionId pfn_getSessionId = nullptr;
            static bool resolved = false;
            if (!resolved) {
                pfn_getSessionId = reinterpret_cast<PFN_AAudioStream_getSessionId>(
                    dlsym(RTLD_DEFAULT, "AAudioStream_getSessionId")
                );
                resolved = true;
            }
            if (pfn_getSessionId) {
                const int32_t sessionId = pfn_getSessionId(stream);
                if (sessionId > 0) {
                    return static_cast<int>(sessionId);
                }
            }
        }
    }
#endif
    return -1;
}
