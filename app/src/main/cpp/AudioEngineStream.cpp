#include "AudioEngine.h"

#include <android/log.h>
#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cstring>
#include <pthread.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <vector>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
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
    deviceConfig.sampleRate = 0;
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
    streamSampleRate = miniaudioDevice.sampleRate > 0 ? static_cast<int>(miniaudioDevice.sampleRate) : 48000;
    streamChannelCount = miniaudioDevice.playback.channels > 0 ? static_cast<int>(miniaudioDevice.playback.channels) : 2;
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
}

void AudioEngine::createStream() {
    createMiniaudioStream();
}

void AudioEngine::closeStream() {
    closeMiniaudioStream();
}

bool AudioEngine::requestStreamStart() {
    if (!outputStreamReady.load(std::memory_order_relaxed) || !miniaudioDeviceInitialized) {
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

void AudioEngine::reconfigureStream(bool resumePlayback) {
    const bool shouldResume = resumePlayback && isPlaying.load();
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
        renderWorkerCv.notify_all();
        return;
    }

    LOGE("Reconfigure resume start failed");
    isPlaying.store(false);
}

void AudioEngine::miniaudioStopCallback(ma_device* pDevice) {
    auto* engine = static_cast<AudioEngine*>(pDevice->pUserData);
    if (!engine) return;
    LOGD("Miniaudio device stop callback received");
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

    auto* outputData = static_cast<float*>(pOutput);
    const int callbackRate = pDevice->sampleRate > 0
            ? static_cast<int>(pDevice->sampleRate)
            : (engine->streamSampleRate > 0 ? engine->streamSampleRate : 48000);

    engine->renderOutputCallbackFrames(outputData, static_cast<int32_t>(frameCount), callbackRate);
}

void AudioEngine::recoverStreamIfNeeded() {
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

bool AudioEngine::renderOutputCallbackFrames(float* outputData, int32_t numFrames, int callbackRate) {
    if (!outputData || numFrames <= 0) {
        return false;
    }

    if (seekInProgress.load()) {
        std::memset(outputData, 0, static_cast<size_t>(numFrames) * 2u * sizeof(float));
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

    renderQueueCallbackCount.fetch_add(1, std::memory_order_relaxed);
    const int framesCopied = popRenderQueue(outputData, numFrames, 2);
    if (framesCopied < numFrames) {
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
