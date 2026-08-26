#include "AudioEngine.h"

#include <algorithm>
#include <chrono>
#include <cmath>
#include <limits>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace {
    constexpr int kVisualizationWaveformSize = 256;
    constexpr int kVisualizationFftSize = 2048;
    constexpr int kVisualizationSpectrumBins = 256;
    constexpr float kVisualizationMinDisplayHz = 35.0f;

    int computeVisualizationMinBin(int sampleRateHz) {
        const int fftHalf = kVisualizationFftSize / 2;
        const float sampleRate = static_cast<float>(std::max(sampleRateHz, 1));
        const float rawBin = (kVisualizationMinDisplayHz / sampleRate) * static_cast<float>(kVisualizationFftSize);
        return std::clamp(static_cast<int>(std::ceil(rawBin)), 1, fftHalf - 2);
    }

    float computeVisualizationTiltCompensation(float freqNorm) {
        const float clamped = std::clamp(freqNorm, 0.0f, 1.0f);
        const float shaped = std::pow(clamped, 0.85f);
        return 0.24f + (1.76f * shaped);
    }

    struct VisualizationLookupTables {
        std::array<float, kVisualizationFftSize> hannWindow {};
        std::array<int, kVisualizationFftSize> bitReverse {};
        std::vector<std::vector<std::pair<float, float>>> twiddles;

        int cachedSampleRate = 0;
        struct BandInfo {
            int startBin;
            int endBin;
            float invCount;
            float weight;
        };
        std::array<BandInfo, kVisualizationSpectrumBins> bands {};

        VisualizationLookupTables() {
            constexpr int kBitCount = 11; // log2(2048)
            const float invSizeMinusOne = 1.0f / static_cast<float>(kVisualizationFftSize - 1);
            for (int i = 0; i < kVisualizationFftSize; ++i) {
                int x = i;
                int reversed = 0;
                for (int bit = 0; bit < kBitCount; ++bit) {
                    reversed = (reversed << 1) | (x & 1);
                    x >>= 1;
                }
                bitReverse[i] = reversed;

                const float phase = static_cast<float>(i) * invSizeMinusOne;
                hannWindow[i] = 0.5f - (0.5f * std::cos(2.0f * static_cast<float>(M_PI) * phase));
            }

            for (int len = 2; len <= kVisualizationFftSize; len <<= 1) {
                const int halfLen = len >> 1;
                std::vector<std::pair<float, float>> stageTwiddles(halfLen);
                for (int j = 0; j < halfLen; ++j) {
                    const float angle = -2.0f * static_cast<float>(M_PI) * static_cast<float>(j) / static_cast<float>(len);
                    stageTwiddles[j] = { std::cos(angle), std::sin(angle) };
                }
                twiddles.push_back(std::move(stageTwiddles));
            }
        }

        void updateBandsForSampleRate(int sampleRateHz) {
            if (sampleRateHz == cachedSampleRate && cachedSampleRate > 0) {
                return;
            }
            cachedSampleRate = sampleRateHz;
            const int fftHalf = kVisualizationFftSize / 2;
            const float sampleRateF = static_cast<float>(std::max(sampleRateHz, 1));
            const int minBin = computeVisualizationMinBin(sampleRateHz);
            const int maxBin = fftHalf - 1;
            const float minFrequencyHz =
                    (static_cast<float>(minBin) * sampleRateF) / static_cast<float>(kVisualizationFftSize);
            const float maxFrequencyHz =
                    (static_cast<float>(maxBin) * sampleRateF) / static_cast<float>(kVisualizationFftSize);
            const float frequencyRatio = std::max(maxFrequencyHz / std::max(minFrequencyHz, 1.0f), 1.001f);

            for (int band = 0; band < kVisualizationSpectrumBins; ++band) {
                const float t0 = static_cast<float>(band) / static_cast<float>(kVisualizationSpectrumBins);
                const float t1 = static_cast<float>(band + 1) / static_cast<float>(kVisualizationSpectrumBins);
                const float startFrequencyHz = minFrequencyHz * std::pow(frequencyRatio, t0);
                const float endFrequencyHz = minFrequencyHz * std::pow(frequencyRatio, t1);
                const int startBin = static_cast<int>(std::floor(
                        (startFrequencyHz / sampleRateF) * static_cast<float>(kVisualizationFftSize)));
                const int endBin = static_cast<int>(std::ceil(
                        (endFrequencyHz / sampleRateF) * static_cast<float>(kVisualizationFftSize))) - 1;
                const int clampedStart = std::clamp(startBin, minBin, maxBin);
                const int clampedEnd = std::clamp(std::max(endBin, clampedStart), clampedStart, maxBin);
                const int count = clampedEnd - clampedStart + 1;
                const float tiltCompensation = computeVisualizationTiltCompensation(t0);
                bands[band] = {
                    clampedStart,
                    clampedEnd,
                    1.0f / static_cast<float>(std::max(count, 1)),
                    (68.0f * tiltCompensation) / static_cast<float>(kVisualizationFftSize)
                };
            }
        }
    };

    static VisualizationLookupTables gVisTables;

    void fftInPlace(std::array<float, kVisualizationFftSize>& real,
                    std::array<float, kVisualizationFftSize>& imag) {
        for (int i = 0; i < kVisualizationFftSize; ++i) {
            const int j = gVisTables.bitReverse[i];
            if (j > i) {
                std::swap(real[i], real[j]);
                std::swap(imag[i], imag[j]);
            }
        }

        size_t stageIdx = 0;
        for (int len = 2; len <= kVisualizationFftSize; len <<= 1, ++stageIdx) {
            const int halfLen = len >> 1;
            const auto& stageTwiddles = gVisTables.twiddles[stageIdx];
            for (int i = 0; i < kVisualizationFftSize; i += len) {
                for (int j = 0; j < halfLen; ++j) {
                    const int even = i + j;
                    const int odd = even + halfLen;
                    const float twiddleReal = stageTwiddles[j].first;
                    const float twiddleImag = stageTwiddles[j].second;
                    const float oddReal = real[odd];
                    const float oddImag = imag[odd];
                    const float tReal = (twiddleReal * oddReal) - (twiddleImag * oddImag);
                    const float tImag = (twiddleReal * oddImag) + (twiddleImag * oddReal);
                    const float evenReal = real[even];
                    const float evenImag = imag[even];

                    real[odd] = evenReal - tReal;
                    imag[odd] = evenImag - tImag;
                    real[even] = evenReal + tReal;
                    imag[even] = evenImag + tImag;
                }
            }
        }
    }

    void computeSingleFftBinnedBars(
            const float* pcm,
            int sampleRate,
            std::array<float, kVisualizationSpectrumBins>& outBars
    ) {
        gVisTables.updateBandsForSampleRate(sampleRate);

        std::array<float, kVisualizationFftSize> fftReal {};
        std::array<float, kVisualizationFftSize> fftImag {};

        // Fast DC removal and precomputed Hann window
        float sum = 0.0f;
        for (int n = 0; n < kVisualizationFftSize; ++n) {
            sum += pcm[n];
        }
        const float mean = sum * (1.0f / static_cast<float>(kVisualizationFftSize));
        for (int n = 0; n < kVisualizationFftSize; ++n) {
            fftReal[n] = (pcm[n] - mean) * gVisTables.hannWindow[n];
            fftImag[n] = 0.0f;
        }

        fftInPlace(fftReal, fftImag);

        for (int band = 0; band < kVisualizationSpectrumBins; ++band) {
            const auto& b = gVisTables.bands[band];
            float powerSum = 0.0f;
            for (int bin = b.startBin; bin <= b.endBin; ++bin) {
                const float re = fftReal[bin];
                const float im = fftImag[bin];
                powerSum += (re * re) + (im * im);
            }
            const float magnitude = std::sqrt(powerSum * b.invCount);
            const float weighted = magnitude * b.weight;
            const float barValue = std::clamp(weighted / (1.0f + weighted), 0.0f, 1.0f);
            outBars[band] = std::max(outBars[band], barValue);
        }
    }
}

// Gain control implementation
void AudioEngine::setMasterGain(float gainDb) {
    masterGainDb.store(gainDb);
}

void AudioEngine::setPluginGain(float gainDb) {
    pluginGainDb.store(gainDb);
}

void AudioEngine::setSongGain(float gainDb) {
    songGainDb.store(gainDb);
}

void AudioEngine::setForceMono(bool enabled) {
    forceMono.store(enabled);
}

void AudioEngine::setOutputLimiterEnabled(bool enabled) {
    outputLimiterEnabled.store(enabled);
}

void AudioEngine::setLookaheadClipperMode(int mode) {
    const int normalized = (mode >= 0 && mode <= 2) ? mode : 1;
    const int previous = lookaheadClipperMode.exchange(normalized);
    if (previous != normalized) {
        std::lock_guard<std::mutex> lock(decoderMutex);
        resetLookaheadClipperStateLocked();
        lookaheadClipperLastMode = normalized;
    }
}

void AudioEngine::setDspBassEnabled(bool enabled) {
    dspBassEnabled.store(enabled);
}

void AudioEngine::setDspBassDepth(int depth) {
    dspBassDepth.store(std::clamp(depth, 0, 4));
}

void AudioEngine::setDspBassRange(int range) {
    dspBassRange.store(std::clamp(range, 0, 4));
}

void AudioEngine::setDspSurroundEnabled(bool enabled) {
    dspSurroundEnabled.store(enabled);
}

void AudioEngine::setDspSurroundDepth(int depth) {
    dspSurroundDepth.store(std::clamp(depth, 1, 16));
}

void AudioEngine::setDspSurroundDelayMs(int delayMs) {
    const int clamped = std::clamp(delayMs, 5, 45);
    const int step = ((clamped - 5) + 2) / 5;
    dspSurroundDelayMs.store(5 + (step * 5));
}

void AudioEngine::setDspReverbEnabled(bool enabled) {
    dspReverbEnabled.store(enabled);
}

void AudioEngine::setDspReverbDepth(int depth) {
    dspReverbDepth.store(std::clamp(depth, 1, 16));
}

void AudioEngine::setDspReverbPreset(int preset) {
    dspReverbPreset.store(std::clamp(preset, 0, 28));
}

void AudioEngine::setDspBitCrushEnabled(bool enabled) {
    dspBitCrushEnabled.store(enabled);
}

void AudioEngine::setDspBitCrushBits(int bits) {
    dspBitCrushBits.store(std::clamp(bits, 1, 24));
}

void AudioEngine::setMasterChannelMute(int channelIndex, bool enabled) {
    if (channelIndex == 0) {
        masterMuteLeft.store(enabled);
    } else if (channelIndex == 1) {
        masterMuteRight.store(enabled);
    }
}

void AudioEngine::setMasterChannelSolo(int channelIndex, bool enabled) {
    if (channelIndex == 0) {
        masterSoloLeft.store(enabled);
    } else if (channelIndex == 1) {
        masterSoloRight.store(enabled);
    }
}

void AudioEngine::setEndFadeApplyToAllTracks(bool enabled) {
    endFadeApplyToAllTracks.store(enabled);
}

void AudioEngine::setEndFadeDurationMs(int durationMs) {
    const int normalized = std::clamp(durationMs, 100, 120000);
    endFadeDurationMs.store(normalized);
}

void AudioEngine::setEndFadeCurve(int curve) {
    const int normalized = (curve >= 0 && curve <= 2) ? curve : 0;
    endFadeCurve.store(normalized);
}

float AudioEngine::getMasterGain() const {
    return masterGainDb.load();
}

float AudioEngine::getPluginGain() const {
    return pluginGainDb.load();
}

float AudioEngine::getSongGain() const {
    return songGainDb.load();
}

bool AudioEngine::getForceMono() const {
    return forceMono.load();
}

bool AudioEngine::getMasterChannelMute(int channelIndex) const {
    if (channelIndex == 0) return masterMuteLeft.load();
    if (channelIndex == 1) return masterMuteRight.load();
    return false;
}

bool AudioEngine::getMasterChannelSolo(int channelIndex) const {
    if (channelIndex == 0) return masterSoloLeft.load();
    if (channelIndex == 1) return masterSoloRight.load();
    return false;
}

bool AudioEngine::getDspBassEnabled() const {
    return dspBassEnabled.load();
}

int AudioEngine::getDspBassDepth() const {
    return dspBassDepth.load();
}

int AudioEngine::getDspBassRange() const {
    return dspBassRange.load();
}

bool AudioEngine::getDspSurroundEnabled() const {
    return dspSurroundEnabled.load();
}

int AudioEngine::getDspSurroundDepth() const {
    return dspSurroundDepth.load();
}

int AudioEngine::getDspSurroundDelayMs() const {
    return dspSurroundDelayMs.load();
}

bool AudioEngine::getDspReverbEnabled() const {
    return dspReverbEnabled.load();
}

int AudioEngine::getDspReverbDepth() const {
    return dspReverbDepth.load();
}

int AudioEngine::getDspReverbPreset() const {
    return dspReverbPreset.load();
}

bool AudioEngine::getDspBitCrushEnabled() const {
    return dspBitCrushEnabled.load();
}

int AudioEngine::getDspBitCrushBits() const {
    return dspBitCrushBits.load();
}

// Convert dB to linear gain
float AudioEngine::dbToGain(float db) {
    return std::pow(10.0f, db / 20.0f);
}

void AudioEngine::beginPauseResumeFadeLocked(bool fadeIn, int streamRate, int durationMs, float attenuationDb) {
    const int safeRate = std::max(1, streamRate);
    const int safeDurationMs = std::clamp(durationMs, 1, 5000);
    const float safeAttenuationDb = std::clamp(attenuationDb, 0.0f, 60.0f);
    const int totalFrames = std::max(
            1,
            static_cast<int>((static_cast<int64_t>(safeRate) * safeDurationMs) / 1000)
    );
    const float floorGain = std::clamp(dbToGain(-safeAttenuationDb), 0.0f, 1.0f);

    pauseResumeFadeTotalFrames = totalFrames;
    pauseResumeFadeProcessedFrames = 0;
    pauseResumeFadeFromGain = fadeIn ? floorGain : 1.0f;
    pauseResumeFadeToGain = fadeIn ? 1.0f : floorGain;
    pauseResumeFadeOutStopPending = false;
}

float AudioEngine::nextPauseResumeFadeGainLocked() {
    if (pauseResumeFadeTotalFrames <= 0) {
        return 1.0f;
    }

    if (pauseResumeFadeProcessedFrames < pauseResumeFadeTotalFrames) {
        pauseResumeFadeProcessedFrames++;
    }
    const float t = std::clamp(
            static_cast<float>(pauseResumeFadeProcessedFrames) /
            static_cast<float>(pauseResumeFadeTotalFrames),
            0.0f,
            1.0f
    );
    const float curveT =
            0.5f - 0.5f * std::cos(static_cast<float>(M_PI) * t);
    const float gain = pauseResumeFadeFromGain + (pauseResumeFadeToGain - pauseResumeFadeFromGain) * curveT;

    if (pauseResumeFadeProcessedFrames >= pauseResumeFadeTotalFrames) {
        if (pauseResumeFadeToGain < 1.0f) {
            // Fade-out reached floor: hold floor gain for remaining frames in this chunk
            // until render loop flips the stream into terminal stop.
            pauseResumeFadeOutStopPending = true;
            pauseResumeFadeProcessedFrames = pauseResumeFadeTotalFrames;
        } else {
            pauseResumeFadeTotalFrames = 0;
            pauseResumeFadeProcessedFrames = 0;
            pauseResumeFadeFromGain = 1.0f;
            pauseResumeFadeToGain = 1.0f;
        }
    }
    return std::clamp(gain, 0.0f, 1.0f);
}

float AudioEngine::computeEndFadeGainLocked(double playbackPositionSeconds) const {
    if (!decoder) return 1.0f;

    const int mode = repeatMode.load();
    if (mode == 2) {
        return 1.0f; // Repeat at loop point bypasses end fade.
    }
    if (mode != 0 && mode != 1 && mode != 3) {
        return 1.0f;
    }

    const int fadeMs = endFadeDurationMs.load();
    if (fadeMs <= 0) {
        return 1.0f;
    }
    const double fadeSeconds = static_cast<double>(fadeMs) / 1000.0;
    if (!(fadeSeconds > 0.0)) {
        return 1.0f;
    }

    const bool applyToAll = endFadeApplyToAllTracks.load();
    const bool reliableDuration =
            (decoder->getPlaybackCapabilities() & AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION) != 0;
    if (reliableDuration && !applyToAll) {
        return 1.0f;
    }

    const double durationNow = decoder->getDuration();
    if (!(durationNow > 0.0) || !std::isfinite(durationNow)) {
        return 1.0f;
    }

    const double fadeStart = std::max(0.0, durationNow - fadeSeconds);
    if (playbackPositionSeconds <= fadeStart) {
        return 1.0f;
    }
    if (playbackPositionSeconds >= durationNow) {
        return 0.0f;
    }

    const double progress = std::clamp(
            (playbackPositionSeconds - fadeStart) / std::max(0.001, fadeSeconds),
            0.0,
            1.0
    );
    float gain = static_cast<float>(1.0 - progress);
    const int curve = endFadeCurve.load();
    if (curve == 1) {
        // Ease-in fade: softer attenuation at fade start, stronger near end.
        gain = static_cast<float>(1.0 - (progress * progress));
    } else if (curve == 2) {
        // Ease-out fade: stronger attenuation near fade start.
        gain = gain * gain;
    }
    return std::clamp(gain, 0.0f, 1.0f);
}

// Apply two-stage gain pipeline: Master → (Plugin or Song)
void AudioEngine::applyGain(float* buffer, int numFrames, int channels, float extraGain) {
    const float masterDb = masterGainDb.load();
    const float pluginDb = pluginGainDb.load();
    const float songDb = songGainDb.load();

    // Calculate total gain
    const float masterGain = dbToGain(masterDb);
    // Song volume overrides plugin volume when not at neutral (0dB)
    const float secondaryGain = (songDb != 0.0f) ? dbToGain(songDb) : dbToGain(pluginDb);
    const float baseGain = masterGain * secondaryGain * std::clamp(extraGain, 0.0f, 1.0f);

    if (baseGain == 1.0f) {
        return;
    }
    for (int frame = 0; frame < numFrames; ++frame) {
        const int baseIndex = frame * channels;
        for (int channel = 0; channel < channels; ++channel) {
            buffer[baseIndex + channel] *= baseGain;
        }
    }
}

void AudioEngine::applyMasterChannelRouting(float* buffer, int numFrames, int channels) {
    if (!buffer || numFrames <= 0 || channels < 2) {
        return;
    }

    const bool muteLeft = masterMuteLeft.load();
    const bool muteRight = masterMuteRight.load();
    const bool soloLeft = masterSoloLeft.load();
    const bool soloRight = masterSoloRight.load();
    const bool anySolo = soloLeft || soloRight;

    const bool leftEnabled = anySolo ? soloLeft : !muteLeft;
    const bool rightEnabled = anySolo ? soloRight : !muteRight;

    if (leftEnabled && rightEnabled) {
        return;
    }

    for (int i = 0; i < numFrames; ++i) {
        const int base = i * channels;
        if (!leftEnabled) {
            buffer[base] = 0.0f;
        }
        if (!rightEnabled) {
            buffer[base + 1] = 0.0f;
        }
    }
}

// Downmix stereo to mono
void AudioEngine::applyMonoDownmix(float* buffer, int numFrames, int channels) {
    if (!forceMono.load() || channels != 2) {
        return;
    }

    // Average left and right channels
    for (int i = 0; i < numFrames; i++) {
        const float mono = (buffer[i * 2] + buffer[i * 2 + 1]) * 0.5f;
        buffer[i * 2] = mono;
        buffer[i * 2 + 1] = mono;
    }
}

void AudioEngine::applyOpenMptDspEffects(float* buffer, int numFrames, int channels, int sampleRate) {
    siliconplayer::effects::OpenMptDspParams params;
    params.bassEnabled = dspBassEnabled.load(std::memory_order_relaxed);
    params.bassDepth = dspBassDepth.load(std::memory_order_relaxed);
    params.bassRange = dspBassRange.load(std::memory_order_relaxed);
    params.surroundEnabled = dspSurroundEnabled.load(std::memory_order_relaxed);
    params.surroundDepth = dspSurroundDepth.load(std::memory_order_relaxed);
    params.surroundDelayMs = dspSurroundDelayMs.load(std::memory_order_relaxed);
    params.reverbEnabled = dspReverbEnabled.load(std::memory_order_relaxed);
    params.reverbDepth = dspReverbDepth.load(std::memory_order_relaxed);
    params.reverbPreset = dspReverbPreset.load(std::memory_order_relaxed);
    params.bitCrushEnabled = dspBitCrushEnabled.load(std::memory_order_relaxed);
    params.bitCrushBits = dspBitCrushBits.load(std::memory_order_relaxed);

    const bool anyDspEnabled =
            params.bassEnabled || params.surroundEnabled || params.reverbEnabled || params.bitCrushEnabled;
    if (!anyDspEnabled || !buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    constexpr float kDspBusPreGain = 0.5623413f; // -5.0 dB
    constexpr float kDspBusMakeupGain = 1.5848932f; // +4.0 dB (net: -1.0 dB)
    const int totalSamples = numFrames * channels;

    constexpr float kSoftClipThreshold = 0.90f;
    constexpr float kneeWidth = 1.0f - kSoftClipThreshold;

    for (int i = 0; i < totalSamples; ++i) {
        float sample = buffer[i] * kDspBusPreGain;
        const float absSample = std::abs(sample);
        if (absSample > kSoftClipThreshold) {
            const float sign = sample < 0.0f ? -1.0f : 1.0f;
            const float over = (absSample - kSoftClipThreshold) / kneeWidth;
            sample = sign * (kSoftClipThreshold + (kneeWidth * (1.0f - std::exp(-over))));
        }
        buffer[i] = std::clamp(sample, -1.0f, 1.0f);
    }

    openMptDspEffects.process(buffer, numFrames, channels, sampleRate, params);
    for (int i = 0; i < totalSamples; ++i) {
        buffer[i] *= kDspBusMakeupGain;
    }
}

void AudioEngine::applyOutputLimiter(float* buffer, int numFrames, int channels) {
    if (!buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    const bool limiterEnabledNow = outputLimiterEnabled.load(std::memory_order_relaxed);
    if (!limiterEnabledNow) {
        outputLimiterGain = 1.0f;
        return;
    }

    const int totalSamples = numFrames * channels;
    float limiterGain = 1.0f;
    float peak = 0.0f;
    for (int i = 0; i < totalSamples; ++i) {
        peak = std::max(peak, std::abs(buffer[i]));
    }
    const float targetGain = (peak > 1.0f) ? (1.0f / peak) : 1.0f;
    const float attack = 0.45f;
    const float release = 0.04f;
    const float coeff = (targetGain < outputLimiterGain) ? attack : release;
    outputLimiterGain += (targetGain - outputLimiterGain) * coeff;
    outputLimiterGain = std::clamp(outputLimiterGain, 0.1f, 1.0f);
    limiterGain = outputLimiterGain;

    constexpr float kSoftClipStart = 0.92f;
    constexpr float kSoftClipDrive = 1.45f;
    const float tanhNorm = std::tanh(kSoftClipDrive);
    for (int i = 0; i < totalSamples; ++i) {
        float sample = buffer[i] * limiterGain;
        const float absSample = std::abs(sample);
        if (absSample > kSoftClipStart) {
            sample = std::tanh(sample * kSoftClipDrive) / tanhNorm;
        }
        buffer[i] = std::clamp(sample, -1.0f, 1.0f);
    }
}

void AudioEngine::resetLookaheadClipperStateLocked() {
    std::fill(lookaheadClipperDelayLine.begin(), lookaheadClipperDelayLine.end(), 0.0f);
    lookaheadClipperWriteIndex = 0;
    lookaheadClipperSampleRate = 0;
    lookaheadClipperChannels = 0;
}

void AudioEngine::applyLookaheadClipper(float* buffer, int numFrames, int channels, int sampleRate) {
    if (!buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    const int mode = lookaheadClipperMode.load(std::memory_order_relaxed);
    if (mode <= 0) {
        resetLookaheadClipperStateLocked();
        lookaheadClipperLastMode = mode;
        return;
    }

    const int safeRate = std::max(sampleRate, 8000);
    const int safeChannels = std::clamp(channels, 1, 2);
    const int lookaheadFrames = std::clamp((safeRate * 5) / 1000, 32, 512);
    const size_t delaySamples = static_cast<size_t>(lookaheadFrames) * static_cast<size_t>(safeChannels);
    if (delaySamples == 0u) {
        return;
    }

    if (lookaheadClipperDelayLine.size() != delaySamples ||
        lookaheadClipperSampleRate != safeRate ||
        lookaheadClipperChannels != safeChannels ||
        lookaheadClipperLastMode != mode) {
        lookaheadClipperDelayLine.assign(delaySamples, 0.0f);
        lookaheadClipperWriteIndex = 0;
        lookaheadClipperSampleRate = safeRate;
        lookaheadClipperChannels = safeChannels;
        lookaheadClipperLastMode = mode;
    }

    constexpr float kSoftClipThreshold = 0.86f;
    const float kneeWidth = 1.0f - kSoftClipThreshold;

    const int totalSamples = numFrames * safeChannels;
    for (int i = 0; i < totalSamples; ++i) {
        const float incoming = buffer[i];
        float delayed = lookaheadClipperDelayLine[lookaheadClipperWriteIndex];
        lookaheadClipperDelayLine[lookaheadClipperWriteIndex] = incoming;
        lookaheadClipperWriteIndex = (lookaheadClipperWriteIndex + 1u) % delaySamples;

        if (mode == 2) {
            buffer[i] = std::clamp(delayed, -1.0f, 1.0f);
            continue;
        }

        float sample = delayed;
        const float absSample = std::abs(sample);
        if (absSample > kSoftClipThreshold) {
            const float sign = sample < 0.0f ? -1.0f : 1.0f;
            const float over = (absSample - kSoftClipThreshold) / kneeWidth;
            sample = sign * (kSoftClipThreshold + (kneeWidth * (1.0f - std::exp(-over))));
        }
        buffer[i] = std::clamp(sample, -1.0f, 1.0f);
    }
}

void AudioEngine::updateVisualizationDataFromOutputCallback(
        const float* buffer,
        int numFrames,
        int channels,
        uint32_t requestedFeatures
) {
    if (!buffer || numFrames <= 0 || channels <= 0) {
        return;
    }

    const bool needsWaveform = (requestedFeatures & kVisualizationFeatureWaveform) != 0u;
    const bool needsBars = (requestedFeatures & kVisualizationFeatureBars) != 0u;
    const bool needsVu = (requestedFeatures & kVisualizationFeatureVu) != 0u;
    const bool needsChannelCount = (requestedFeatures & kVisualizationFeatureChannelCount) != 0u;
    // Let channel-scope-only demand through so the per-chunk timestamp
    // refresh below stays alive for `getChannelScopeSamples`.
    const bool needsChannelScope = (requestedFeatures & kVisualizationFeatureChannelScope) != 0u;
    if (!needsWaveform && !needsBars && !needsVu && !needsChannelCount && !needsChannelScope) {
        return;
    }

    const int safeChannels = std::clamp(channels, 1, 2);
    std::array<float, 256> waveL {};
    std::array<float, 256> waveR {};
    double sumSqL = 0.0;
    double sumSqR = 0.0;
    if (needsWaveform) {
        for (int n = 0; n < kVisualizationWaveformSize; ++n) {
            const int srcFrame = (n * numFrames) / kVisualizationWaveformSize;
            const int frameIndex = std::min(srcFrame, numFrames - 1);
            const int base = frameIndex * channels;
            const float left = buffer[base];
            const float right = channels > 1 ? buffer[base + 1] : left;
            waveL[n] = std::clamp(left, -1.0f, 1.0f);
            waveR[n] = std::clamp(right, -1.0f, 1.0f);
        }
    }
    if (needsVu) {
        for (int frame = 0; frame < numFrames; ++frame) {
            const int base = frame * channels;
            const float left = buffer[base];
            const float right = channels > 1 ? buffer[base + 1] : left;
            sumSqL += static_cast<double>(left) * left;
            sumSqR += static_cast<double>(right) * right;
        }
    }
    std::array<float, 2> vu {};
    if (needsVu) {
        const double invFrames = 1.0 / static_cast<double>(numFrames);
        vu[0] = static_cast<float>(std::clamp(std::sqrt(sumSqL * invFrames), 0.0, 1.0));
        vu[1] = static_cast<float>(std::clamp(std::sqrt(sumSqR * invFrames), 0.0, 1.0));
    }    constexpr int kHistoryMask = 16384 - 1;
    const int64_t callbackNowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();

    std::lock_guard<std::mutex> visLock(visualizationMutex);
    if (needsWaveform || needsVu) {
        int wIndex = visualizationScopeWriteIndex;
        for (int frame = 0; frame < numFrames; ++frame) {
            const int base = frame * safeChannels;
            const float left = buffer[base];
            const float right = safeChannels > 1 ? buffer[base + 1] : left;
            visualizationScopeHistoryLeft[wIndex] = std::clamp(left, -1.0f, 1.0f);
            visualizationScopeHistoryRight[wIndex] = std::clamp(right, -1.0f, 1.0f);
            wIndex = (wIndex + 1) & kHistoryMask;
        }
        visualizationScopeWriteIndex = wIndex;
    }
    if (needsBars) {
        int mIndex = visualizationMonoWriteIndex;
        for (int frame = 0; frame < numFrames; ++frame) {
            const int base = frame * safeChannels;
            const float left = buffer[base];
            const float right = safeChannels > 1 ? buffer[base + 1] : left;
            const float mono = 0.5f * (left + right);
            visualizationMonoHistory[mIndex] = std::clamp(mono, -1.0f, 1.0f);
            mIndex = (mIndex + 1) & kHistoryMask;
        }
        visualizationMonoWriteIndex = mIndex;
    }
    if (needsWaveform) {
        visualizationWaveformLeft = waveL;
        visualizationWaveformRight = waveR;
    }
    if (needsVu) {
        visualizationVuLevels = vu;
    }
    if (needsChannelCount) {
        visualizationChannelCount.store(safeChannels, std::memory_order_relaxed);
    }
    visualizationLastCallbackFrames = numFrames;
    visualizationLastCallbackNs = callbackNowNs;
}

void AudioEngine::markVisualizationRequested(uint32_t features) const {
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    visualizationRequestedFeatures.fetch_or(features, std::memory_order_relaxed);
    visualizationLastRequestNs.store(nowNs, std::memory_order_relaxed);
}

bool AudioEngine::shouldUpdateVisualization(uint32_t* outFeatures) const {
    const int64_t lastRequestNs = visualizationLastRequestNs.load(std::memory_order_relaxed);
    if (lastRequestNs <= 0) {
        if (outFeatures) *outFeatures = 0u;
        return false;
    }
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    constexpr int64_t kVisualizationDemandWindowNs = 750'000'000; // 750 ms
    const bool active = (nowNs - lastRequestNs) <= kVisualizationDemandWindowNs;
    if (!active) {
        visualizationRequestedFeatures.store(0u, std::memory_order_relaxed);
        if (outFeatures) *outFeatures = 0u;
        return false;
    }
    const uint32_t features = visualizationRequestedFeatures.load(std::memory_order_relaxed);
    if (outFeatures) *outFeatures = features;
    return features != 0u;
}

bool AudioEngine::getNewPcmMono(int32_t maxFrames, std::vector<float>& out) {
    markVisualizationRequested(kVisualizationFeatureWaveform);
    out.clear();
    if (maxFrames <= 0) return true;

    std::lock_guard<std::mutex> lock(visualizationMutex);
    constexpr int kHistoryMask = 16384 - 1;
    const int available = (visualizationScopeWriteIndex - visualizationPcmFeedReadIndex + 16384) & kHistoryMask;
    if (available <= 0) return true;

    const int readFrames = std::min(available, static_cast<int>(maxFrames));
    visualizationPcmFeedReadIndex = (visualizationScopeWriteIndex - readFrames + 16384) & kHistoryMask;
    out.resize(static_cast<size_t>(readFrames));
    for (int i = 0; i < readFrames; ++i) {
        const int idx = (visualizationPcmFeedReadIndex + i) & kHistoryMask;
        out[static_cast<size_t>(i)] = 0.5f * (visualizationScopeHistoryLeft[idx] + visualizationScopeHistoryRight[idx]);
    }
    visualizationPcmFeedReadIndex = (visualizationPcmFeedReadIndex + readFrames) & kHistoryMask;
    return true;
}

std::vector<float> AudioEngine::getVisualizationWaveformScope(
        int channelIndex,
        int windowMs,
        int triggerMode
) const {
    markVisualizationRequested(kVisualizationFeatureWaveform);
    constexpr int kOutputSize = 1024;
    const int sampleRate = std::max(streamSampleRate, 8000);
    const int clampedWindowMs = std::clamp(windowMs, 5, 100);
    int windowFrames = (sampleRate * clampedWindowMs) / 1000;
    windowFrames = std::clamp(windowFrames, 128, 4096);
    const int extractFrames = windowFrames * 2 + 4;

    std::vector<float> localWindow(extractFrames, 0.0f);
    {
        std::lock_guard<std::mutex> lock(visualizationMutex);
        const int callbackFrames = std::max(visualizationLastCallbackFrames, 1);
        const int64_t callbackNs = visualizationLastCallbackNs;
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t elapsedNs = std::max<int64_t>(0, nowNs - callbackNs);
        const double elapsedFrames = (static_cast<double>(elapsedNs) * static_cast<double>(sampleRate)) / 1.0e9;
        const int playOffset = std::clamp(
                static_cast<int>(std::floor(elapsedFrames)),
                0,
                callbackFrames
        );

        const auto& history = channelIndex == 1 ? visualizationScopeHistoryRight : visualizationScopeHistoryLeft;
        constexpr int kHistoryMask = 16384 - 1;
        const int callbackStart = (visualizationScopeWriteIndex - callbackFrames + 16384) & kHistoryMask;
        const int currentPlayHead = (callbackStart + playOffset) & kHistoryMask;
        const int rawStart = (currentPlayHead - extractFrames + 16384) & kHistoryMask;
        for (int i = 0; i < extractFrames; ++i) {
            localWindow[i] = history[(rawStart + i) & kHistoryMask];
        }
    }

    int startOffset = windowFrames;
    if (triggerMode == 1 || triggerMode == 2) {
        const bool rising = triggerMode == 1;
        const int searchLen = windowFrames;
        const int anchorOffset = searchLen / 2;
        const float invSearchLen = 1.0f / static_cast<float>(searchLen);

        int bestTriggerOffset = -1;
        float bestScore = -1.0e9f;
        for (int offset = 2; offset < searchLen - 2; ++offset) {
            const float prev = localWindow[offset - 1];
            const float curr = localWindow[offset];
            const bool crossed = rising ? (prev < 0.0f && curr >= 0.0f) : (prev > 0.0f && curr <= 0.0f);
            if (!crossed) continue;

            const float left = localWindow[offset - 2];
            const float right = localWindow[offset + 1];
            const float slope = std::abs(curr - prev);
            const float edgeEnergy = 0.5f * (std::abs(curr) + std::abs(prev));
            const float curvature = std::abs((right - curr) - (curr - left));
            const float anchorPenalty = static_cast<float>(std::abs(offset - anchorOffset)) * invSearchLen;

            const float score = (slope * 2.8f) + (edgeEnergy * 0.9f) + (curvature * 0.35f) - (anchorPenalty * 1.6f);
            if (score > bestScore) {
                bestScore = score;
                bestTriggerOffset = offset;
            }
        }

        if (bestTriggerOffset < 0) {
            float bestAbs = std::numeric_limits<float>::max();
            for (int offset = 0; offset < searchLen; ++offset) {
                const float sample = std::abs(localWindow[offset]);
                const float anchorPenalty = static_cast<float>(std::abs(offset - anchorOffset)) * invSearchLen;
                const float ranking = sample + (anchorPenalty * 0.10f);
                if (ranking < bestAbs) {
                    bestAbs = ranking;
                    bestTriggerOffset = offset;
                }
            }
        }

        if (bestTriggerOffset >= 0) {
            startOffset = bestTriggerOffset;
        }
    }

    std::vector<float> output(kOutputSize, 0.0f);
    const double scale = static_cast<double>(windowFrames - 1) / static_cast<double>(kOutputSize - 1);
    for (int i = 0; i < kOutputSize; ++i) {
        const double frameOffset = static_cast<double>(i) * scale;
        const int frameFloor = static_cast<int>(std::floor(frameOffset));
        const float frac = static_cast<float>(frameOffset - static_cast<double>(frameFloor));
        const int idx0 = std::min(startOffset + frameFloor, extractFrames - 2);
        const int idx1 = idx0 + 1;
        const float sample0 = localWindow[idx0];
        const float sample1 = localWindow[idx1];
        const float sample = sample0 + ((sample1 - sample0) * frac);
        output[i] = std::clamp(sample, -1.0f, 1.0f);
    }
    return output;
}

std::vector<float> AudioEngine::getVisualizationBars() const {
    markVisualizationRequested(kVisualizationFeatureBars);
    std::array<float, kVisualizationFftSize> localMono {};
    int sampleRate = 48000;
    {
        std::lock_guard<std::mutex> lock(visualizationMutex);
        sampleRate = std::max(streamSampleRate, 8000);
        const int callbackFrames = std::max(visualizationLastCallbackFrames, 1);
        const int64_t callbackNs = visualizationLastCallbackNs;
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t elapsedNs = std::max<int64_t>(0, nowNs - callbackNs);
        const double elapsedFrames = (static_cast<double>(elapsedNs) * static_cast<double>(sampleRate)) / 1.0e9;
        const int playOffset = std::clamp(
                static_cast<int>(std::floor(elapsedFrames)),
                0,
                callbackFrames
        );

        constexpr int kHistoryMask = 16384 - 1;
        const int callbackStart = (visualizationMonoWriteIndex - callbackFrames + 16384) & kHistoryMask;
        const int currentPlayHead = (callbackStart + playOffset) & kHistoryMask;
        const int startIdx = (currentPlayHead - kVisualizationFftSize + 16384) & kHistoryMask;
        for (int i = 0; i < kVisualizationFftSize; ++i) {
            localMono[i] = visualizationMonoHistory[(startIdx + i) & kHistoryMask];
        }
    }

    std::array<float, kVisualizationSpectrumBins> bars {};
    computeSingleFftBinnedBars(localMono.data(), sampleRate, bars);

    return std::vector<float>(bars.begin(), bars.end());
}

std::vector<float> AudioEngine::getVisualizationVuLevels() const {
    markVisualizationRequested(kVisualizationFeatureVu);
    std::array<float, 512> localL {};
    std::array<float, 512> localR {};
    int sampleRate = 48000;
    {
        std::lock_guard<std::mutex> lock(visualizationMutex);
        sampleRate = std::max(streamSampleRate, 8000);
        const int callbackFrames = std::max(visualizationLastCallbackFrames, 1);
        const int64_t callbackNs = visualizationLastCallbackNs;
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t elapsedNs = std::max<int64_t>(0, nowNs - callbackNs);
        const double elapsedFrames = (static_cast<double>(elapsedNs) * static_cast<double>(sampleRate)) / 1.0e9;
        const int playOffset = std::clamp(
                static_cast<int>(std::floor(elapsedFrames)),
                0,
                callbackFrames
        );

        constexpr int kHistoryMask = 16384 - 1;
        constexpr int kVuWindowSize = 512;
        const int callbackStart = (visualizationScopeWriteIndex - callbackFrames + 16384) & kHistoryMask;
        const int currentPlayHead = (callbackStart + playOffset) & kHistoryMask;
        const int startIdx = (currentPlayHead - kVuWindowSize + 16384) & kHistoryMask;
        for (int i = 0; i < kVuWindowSize; ++i) {
            const int idx = (startIdx + i) & kHistoryMask;
            localL[i] = visualizationScopeHistoryLeft[idx];
            localR[i] = visualizationScopeHistoryRight[idx];
        }
    }

    float sumSqL = 0.0f;
    float sumSqR = 0.0f;
    for (int i = 0; i < 512; ++i) {
        sumSqL += localL[i] * localL[i];
        sumSqR += localR[i] * localR[i];
    }
    constexpr float invSize = 1.0f / 512.0f;
    const float rmsL = std::clamp(std::sqrt(sumSqL * invSize), 0.0f, 1.0f);
    const float rmsR = std::clamp(std::sqrt(sumSqR * invSize), 0.0f, 1.0f);

    return { rmsL, rmsR };
}

int AudioEngine::getVisualizationChannelCount() const {
    markVisualizationRequested(kVisualizationFeatureChannelCount);
    return visualizationChannelCount.load();
}

// Bitrate information
int64_t AudioEngine::getTrackBitrate() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return 0;
    }
    return decoder->getCoreIntInfo("bitrate", 0);
}

bool AudioEngine::isTrackVBR() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return false;
    }
    return decoder->getCoreIntInfo("isVbr", 0) != 0;
}
