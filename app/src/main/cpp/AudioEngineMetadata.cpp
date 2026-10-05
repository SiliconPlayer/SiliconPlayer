#include "AudioEngine.h"
#include "ChannelScopeSharedState.h"

#include <android/log.h>
#include <algorithm>
#include <atomic>
#include <cmath>

#define LOG_TAG "AudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

bool AudioEngine::consumeNaturalEndEvent() {
    return naturalEndPending.exchange(false);
}

bool AudioEngine::consumeTransportErrorEvent() {
    return transportErrorPending.exchange(false);
}

// Caller must hold decoderMutex. Copies the live decoder's metadata into
// the cache served while the mutex is busy.
void AudioEngine::refreshMetadataCacheLocked() {
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    if (!decoder) {
        cachedMetadataTitle.clear();
        cachedMetadataArtist.clear();
        cachedMetadataAlbum.clear();
        cachedMetadataDecoderName.clear();
        cachedMetadataBitDepthLabel = "Unknown";
        cachedSubtuneEntries.clear();
        cachedMetadataSampleRate = 0;
        cachedMetadataChannelCount = 0;
        cachedMetadataSubtuneCount = 0;
        cachedMetadataSubtuneIndex = 0;
        cachedMetadataRepeatCaps = AudioDecoder::REPEAT_CAP_TRACK;
        cachedMetadataPlaybackCaps = AudioDecoder::PLAYBACK_CAP_SEEK |
                                     AudioDecoder::PLAYBACK_CAP_RELIABLE_DURATION |
                                     AudioDecoder::PLAYBACK_CAP_LIVE_REPEAT_MODE;
        cachedMetadataHasNativeSampleRate = false;
        return;
    }
    cachedMetadataTitle = decoder->getTitle();
    cachedMetadataArtist = decoder->getArtist();
    cachedMetadataAlbum = decoder->getAlbum();
    cachedMetadataDecoderName = decoder->getName();
    cachedMetadataBitDepthLabel = decoder->getBitDepthLabel();
    cachedMetadataSampleRate = decoder->getSampleRate();
    cachedMetadataChannelCount = decoder->getDisplayChannelCount();
    cachedMetadataSubtuneCount = decoder->getSubtuneCount();
    cachedMetadataSubtuneIndex = decoder->getCurrentSubtuneIndex();
    cachedMetadataRepeatCaps = decoder->getRepeatModeCapabilities();
    cachedMetadataPlaybackCaps = decoder->getPlaybackCapabilities();
    cachedMetadataHasNativeSampleRate = decoder->hasNativeSampleRate();
    const int count = cachedMetadataSubtuneCount;
    cachedSubtuneEntries.clear();
    cachedSubtuneEntries.reserve(count > 0 ? static_cast<size_t>(count) : 0);
    for (int i = 0; i < count; ++i) {
        CachedSubtuneEntry entry;
        entry.title = decoder->getSubtuneTitle(i);
        entry.artist = decoder->getSubtuneArtist(i);
        entry.durationSeconds = decoder->getSubtuneDurationSeconds(i);
        cachedSubtuneEntries.push_back(std::move(entry));
    }
}

std::string AudioEngine::getTitle() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataTitle;
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataTitle;
}

std::string AudioEngine::getArtist() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataArtist;
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataArtist;
}

std::string AudioEngine::getComposer() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getComposer();
}

std::string AudioEngine::getGenre() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getGenre();
}

std::string AudioEngine::getAlbum() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataAlbum;
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataAlbum;
}

std::string AudioEngine::getYear() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getYear();
}

std::string AudioEngine::getDate() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getDate();
}

std::string AudioEngine::getCopyright() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getCopyright();
}

std::string AudioEngine::getComment() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return "";
    }
    return decoder->getComment();
}

int AudioEngine::getSampleRate() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataSampleRate;
    }
    if (!decoder) {
        return 0;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataSampleRate;
}

bool AudioEngine::hasNativeSampleRate() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataHasNativeSampleRate;
    }
    if (!decoder) {
        return false;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataHasNativeSampleRate;
}

int AudioEngine::getDisplayChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataChannelCount;
    }
    if (!decoder) {
        return 0;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataChannelCount;
}

int AudioEngine::getChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return 0;
    }
    return decoder->getChannelCount();
}

int AudioEngine::getBitDepth() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) {
        return 0;
    }
    return decoder->getBitDepth();
}

std::string AudioEngine::getBitDepthLabel() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataBitDepthLabel;
    }
    if (!decoder) {
        return "Unknown";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataBitDepthLabel;
}

std::string AudioEngine::getCurrentDecoderName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataDecoderName;
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataDecoderName;
}

int AudioEngine::getSubtuneCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataSubtuneCount;
    }
    if (!decoder) {
        return 0;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataSubtuneCount;
}

int AudioEngine::getCurrentSubtuneIndex() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        return cachedMetadataSubtuneIndex;
    }
    if (!decoder) {
        return 0;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    return cachedMetadataSubtuneIndex;
}

bool AudioEngine::selectSubtune(int index) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) {
        return false;
    }
    const bool applied = decoder->selectSubtune(index);
    if (applied) {
        clearTransportStallState();
        refreshMetadataCacheLocked();
    }
    return applied;
}

std::string AudioEngine::getSubtuneTitle(int index) {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        if (index >= 0 &&
            static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
            return cachedSubtuneEntries[static_cast<size_t>(index)].title;
        }
        return "";
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    if (index >= 0 &&
        static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
        return cachedSubtuneEntries[static_cast<size_t>(index)].title;
    }
    return "";
}

std::string AudioEngine::getSubtuneArtist(int index) {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        if (index >= 0 &&
            static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
            return cachedSubtuneEntries[static_cast<size_t>(index)].artist;
        }
        return "";
    }
    if (!decoder) {
        return "";
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    if (index >= 0 &&
        static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
        return cachedSubtuneEntries[static_cast<size_t>(index)].artist;
    }
    return "";
}

double AudioEngine::getSubtuneDurationSeconds(int index) {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock()) {
        std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
        if (index >= 0 &&
            static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
            return cachedSubtuneEntries[static_cast<size_t>(index)].durationSeconds;
        }
        return 0.0;
    }
    if (!decoder) {
        return 0.0;
    }
    refreshMetadataCacheLocked();
    std::lock_guard<std::mutex> cacheLock(metadataCacheMutex);
    if (index >= 0 &&
        static_cast<size_t>(index) < cachedSubtuneEntries.size()) {
        return cachedSubtuneEntries[static_cast<size_t>(index)].durationSeconds;
    }
    return 0.0;
}

int AudioEngine::getDecoderRenderSampleRateHz() const {
    return decoderRenderSampleRate;
}

int AudioEngine::getOutputStreamSampleRateHz() const {
    return streamSampleRate;
}

std::string AudioEngine::getOpenMptModuleTypeLong() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleTypeLong");
}

std::string AudioEngine::getOpenMptTracker() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("tracker");
}

std::string AudioEngine::getOpenMptSongMessage() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("songMessage");
}

int AudioEngine::getOpenMptOrderCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getOpenMptPatternCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("patternCount", 0);
}

int AudioEngine::getOpenMptInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getOpenMptSampleCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("sampleCount", 0);
}

std::string AudioEngine::getOpenMptInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getOpenMptSampleNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::string AudioEngine::getXmpInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getXmpSampleNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::string AudioEngine::getXmpFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleTypeLong");
}

int AudioEngine::getUfmodInfo(const std::string& name) {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo(name.c_str(), 0);
}

std::string AudioEngine::getXmpSongMessage() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("songMessage");
}

std::string AudioEngine::getXmpModuleMd5() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleMd5");
}

std::string AudioEngine::getXmpMixerName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("mixerName");
}

int AudioEngine::getXmpChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("channelCount", 0);
}

int AudioEngine::getXmpOrderCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getXmpPatternCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("patternCount", 0);
}

int AudioEngine::getXmpTrackCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getXmpInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getXmpSampleCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("sampleCount", 0);
}

int AudioEngine::getXmpInitialSpeed() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("initialSpeed", 0);
}

int AudioEngine::getXmpInitialBpm() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("initialBpm", 0);
}

int AudioEngine::getXmpRestartPosition() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("restartPosition", 0);
}

int AudioEngine::getXmpCurrentOrder() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentOrder", 0);
}

int AudioEngine::getXmpCurrentPattern() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentPattern", 0);
}

int AudioEngine::getXmpCurrentRow() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentRow", 0);
}

int AudioEngine::getXmpCurrentTick() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentTick", 0);
}

int AudioEngine::getXmpCurrentSpeed() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentSpeed", 0);
}

int AudioEngine::getXmpCurrentBpm() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentBpm", 0);
}

int AudioEngine::getXmpLoopCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("loopCount", 0);
}

std::string AudioEngine::getAyflyFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getAyflyChipName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("chipName");
}

std::string AudioEngine::getAyflyPlayerName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("playerName");
}

std::string AudioEngine::getAyflyMixerName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("mixerName");
}

int AudioEngine::getAyflyChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("channelCount", 0);
}

int AudioEngine::getAyflyLoopPointMs() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("loopPointMs", 0);
}

int AudioEngine::getAyflySubsongCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("subsongCount", 0);
}

int AudioEngine::getAyflyCurrentSubsong() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentSubsong", 0);
}

int AudioEngine::getAyflyInterruptHz() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("interruptHz", 0);
}

std::vector<float> AudioEngine::getOpenMptChannelVuLevels() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return {};
    return decoder->getCoreFloatVectorInfo("channelVuLevels");
}

std::vector<float> AudioEngine::getChannelScopeSamples(int samplesPerChannel) {
    std::vector<float> flat;
    getChannelScopeSamples(samplesPerChannel, flat);
    return flat;
}

// Plugin-allocated scope state releases through a shared_ptr vtable that is
// unmapped once the plugin is dlclose'd, so the cache must not outlive it.
void AudioEngine::resetScopeStateCacheLocked() {
    std::lock_guard<std::mutex> lock(scopeStateCacheMutex);
    scopeStateCacheValid = false;
    scopeStateCache.reset();
}

void AudioEngine::getChannelScopeSamples(int samplesPerChannel, std::vector<float>& outFlat) {
    // Declare vis demand so the render worker bumps the snapshot serial
    // frequently and keeps `visualizationLastCallbackNs` fresh.
    markVisualizationRequested(kVisualizationFeatureChannelScope);
    std::shared_ptr<ChannelScopeSharedState> state;
    int decoderSampleRate = 0;
    {
        std::lock_guard<std::mutex> lock(decoderMutex);
        if (!decoder) {
            outFlat.clear();
            return;
        }
        state = decoder->getChannelScopeSharedState();
        decoderSampleRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : decoder->getRenderSampleRate();
        {
            std::lock_guard<std::mutex> cacheLock(scopeStateCacheMutex);
            scopeStateCache = state;
            scopeStateCacheDecoderRate = decoderSampleRate;
            scopeStateCacheSerial = decoderSerial.load(std::memory_order_relaxed);
            scopeStateCacheValid = true;
        }
    }
    if (!state) {
        outFlat.clear();
        return;
    }
    const int outputSampleRate = streamSampleRate > 0 ? streamSampleRate : decoderSampleRate;
    const double presentationDelayDecoderFrames =
            scopePresentationDelayDecoderFrames(decoderSampleRate, outputSampleRate, samplesPerChannel);
    state->getProcessedSamples(
            samplesPerChannel,
            static_cast<int>(std::ceil(std::max(0.0, presentationDelayDecoderFrames))),
            outFlat
    );
}

bool AudioEngine::tryGetChannelScopeSamples(int samplesPerChannel, std::vector<float>& outFlat) {
    // Declare vis demand so the render worker bumps the snapshot serial
    // frequently and keeps `visualizationLastCallbackNs` fresh.
    markVisualizationRequested(kVisualizationFeatureChannelScope);
    {
        std::lock_guard<std::mutex> cacheLock(scopeStateCacheMutex);
        if (scopeStateCacheValid &&
            scopeStateCache &&
            scopeStateCacheSerial == decoderSerial.load(std::memory_order_relaxed)) {
            const int decoderSampleRate = scopeStateCacheDecoderRate;
            const int outputSampleRate = streamSampleRate > 0 ? streamSampleRate : decoderSampleRate;
            const double presentationDelayDecoderFrames =
                    scopePresentationDelayDecoderFrames(decoderSampleRate, outputSampleRate, samplesPerChannel);
            scopeStateCache->getProcessedSamples(
                    samplesPerChannel,
                    static_cast<int>(std::ceil(std::max(0.0, presentationDelayDecoderFrames))),
                    outFlat
            );
            return true;
        }
    }
    std::shared_ptr<ChannelScopeSharedState> state;
    int decoderSampleRate = 0;
    {
        std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
        // Decoder busy (long seek, heavy metadata read): leave the previous
        // window in place so the renderer redraws it instead of stalling.
        if (!lock.owns_lock()) {
            static std::atomic<int64_t> lastSkippedPullLogNs { 0 };
            const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                    std::chrono::steady_clock::now().time_since_epoch()).count();
            int64_t previousNs = lastSkippedPullLogNs.load(std::memory_order_relaxed);
            if (nowNs - previousNs > 2000000000LL &&
                lastSkippedPullLogNs.compare_exchange_strong(previousNs, nowNs, std::memory_order_relaxed)) {
                LOGD("Channel scope pull skipped: decoder mutex contended");
            }
            return false;
        }
        if (!decoder) {
            resetScopeStateCacheLocked();
            outFlat.clear();
            return true;
        }
        state = decoder->getChannelScopeSharedState();
        decoderSampleRate = decoderRenderSampleRate > 0 ? decoderRenderSampleRate : decoder->getRenderSampleRate();
        {
            std::lock_guard<std::mutex> cacheLock(scopeStateCacheMutex);
            scopeStateCache = state;
            scopeStateCacheDecoderRate = decoderSampleRate;
            scopeStateCacheSerial = decoderSerial.load(std::memory_order_relaxed);
            scopeStateCacheValid = true;
        }
    }
    if (!state) {
        outFlat.clear();
        return true;
    }
    const int outputSampleRate = streamSampleRate > 0 ? streamSampleRate : decoderSampleRate;
    const double presentationDelayDecoderFrames =
            scopePresentationDelayDecoderFrames(decoderSampleRate, outputSampleRate, samplesPerChannel);
    state->getProcessedSamples(
            samplesPerChannel,
            static_cast<int>(std::ceil(std::max(0.0, presentationDelayDecoderFrames))),
            outFlat
    );
    return true;
}

double AudioEngine::scopePresentationDelayDecoderFrames(
        int decoderSampleRate,
        int outputSampleRate,
        int samplesPerChannel
) const {
    int callbackFrames = 0;
    int64_t callbackNs = 0;
    {
        std::lock_guard<std::mutex> lock(visualizationMutex);
        callbackFrames = std::max(visualizationLastCallbackFrames, 0);
        callbackNs = visualizationLastCallbackNs;
    }

    // Decoder-output -> ear FIFO drained at outputSampleRate so the window
    // slides smoothly between callbacks across all backends.
    const int backendBufferedFrames = std::max(miniaudioBufferFrames, 0);

    double presentationDelayOutputFrames = static_cast<double>(renderQueueFrames());
    if (lookaheadClipperMode.load(std::memory_order_relaxed) > 0) {
        const int lookaheadFrames = std::clamp((outputSampleRate * 5) / 1000, 32, 512);
        presentationDelayOutputFrames += static_cast<double>(lookaheadFrames);
    }
    if (outputSampleRate > 0 && callbackNs > 0) {
        const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
        ).count();
        const int64_t elapsedNs = std::max<int64_t>(0, nowNs - callbackNs);
        const double elapsedFrames =
                (static_cast<double>(elapsedNs) * static_cast<double>(outputSampleRate)) / 1.0e9;
        // Bound the base once per callback, then decay uncapped: per-poll
        // caps flatten the term on large-period transports and pin the window.
        const int64_t previousCompCbNs = visScopeCompBaseCbNs.load(std::memory_order_relaxed);
        if (callbackNs != previousCompCbNs) {
            const double base = static_cast<double>(callbackFrames) +
                    static_cast<double>(backendBufferedFrames);
            visScopeCompBase.store(
                    std::min(base, static_cast<double>(ChannelScopeSharedState::kBufferedAheadEstimateCapFrames)),
                    std::memory_order_relaxed);
            visScopeCompBaseCbNs.store(callbackNs, std::memory_order_relaxed);
        }
        presentationDelayOutputFrames += std::max(
                0.0,
                visScopeCompBase.load(std::memory_order_relaxed) - elapsedFrames);
    } else {
        // No callback timestamp yet (cold start). Fall back to the static
        // backend buffer size so we still report a sane delay on the first
        // poll.
        presentationDelayOutputFrames += static_cast<double>(std::min(
                backendBufferedFrames,
                ChannelScopeSharedState::kBufferedAheadEstimateCapFrames));
    }
    double presentationDelayDecoderFrames = presentationDelayOutputFrames;
    if (decoderSampleRate > 0 && outputSampleRate > 0 && decoderSampleRate != outputSampleRate) {
        presentationDelayDecoderFrames =
                presentationDelayOutputFrames *
                (static_cast<double>(decoderSampleRate) / static_cast<double>(outputSampleRate));
    }
    // Keep the estimate within the history's service range; overshoot leads
    // the trace instead of freezing it at the newest edge.
    const double maxUsableDelayFrames = static_cast<double>(
            std::max(0, ChannelScopeSharedState::kMaxSamples - samplesPerChannel -
                    ChannelScopeSharedState::kMinSliderHeadroomFrames));
    const double finalDelay = std::min(presentationDelayDecoderFrames, maxUsableDelayFrames);

    static std::atomic<int64_t> lastEstimateLogNs { 0 };
    const int64_t nowNs = std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    int64_t previousLogNs = lastEstimateLogNs.load(std::memory_order_relaxed);
    if (nowNs - previousLogNs > 2000000000LL) {
        if (lastEstimateLogNs.compare_exchange_strong(previousLogNs, nowNs, std::memory_order_relaxed)) {
            LOGD(
                    "Scope delay estimate: %.0f frames (queue=%d backend=%d cbFrames=%d fetch=%d decRate=%d outRate=%d)",
                    presentationDelayDecoderFrames,
                    renderQueueFrames(),
                    miniaudioBufferFrames,
                    callbackFrames,
                    samplesPerChannel,
                    decoderSampleRate,
                    outputSampleRate
            );
        }
    }
    return finalDelay;
}

std::vector<int32_t> AudioEngine::getChannelScopeTextState(int maxChannels) {
    // Non-blocking: text is cosmetic and polled at low rate; returning empty
    // on contention lets callers keep their previous states instead of
    // stalling behind long decoder operations.
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return {};
    return decoder->getChannelScopeTextState(maxChannels);
}

std::vector<std::string> AudioEngine::getDecoderToggleChannelNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return {};
    return decoder->getToggleChannelNames();
}

std::vector<uint8_t> AudioEngine::getDecoderToggleChannelAvailability() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return {};
    return decoder->getToggleChannelAvailability();
}

void AudioEngine::setDecoderToggleChannelMuted(int channelIndex, bool enabled) {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return;
    decoder->setToggleChannelMuted(channelIndex, enabled);
}

bool AudioEngine::getDecoderToggleChannelMuted(int channelIndex) {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getToggleChannelMuted(channelIndex);
}

void AudioEngine::clearDecoderToggleChannelMutes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return;
    decoder->clearToggleChannelMutes();
}

std::string AudioEngine::getVgmGameName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getVgmSystemName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getVgmReleaseDate() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("releaseDate");
}

std::string AudioEngine::getVgmEncodedBy() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("encodedBy");
}

std::string AudioEngine::getVgmNotes() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("notes");
}

std::string AudioEngine::getVgmFileVersion() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("fileVersion");
}

int AudioEngine::getVgmDeviceCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("deviceCount", 0);
}

std::string AudioEngine::getVgmUsedChipList() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("usedChipList");
}

bool AudioEngine::getVgmHasLoopPoint() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("hasLoopPoint", 0) != 0;
}

std::string AudioEngine::getFfmpegCodecName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("codecName");
}

std::string AudioEngine::getFfmpegContainerName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("containerName");
}

std::string AudioEngine::getFfmpegSampleFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sampleFormatName");
}

std::string AudioEngine::getFfmpegChannelLayoutName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("channelLayoutName");
}

std::string AudioEngine::getFfmpegEncoderName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("encoderName");
}

std::string AudioEngine::getGmeSystemName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getGmeGameName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getGmeCopyright() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getGmeComment() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("comment");
}

std::string AudioEngine::getGmeDumper() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("dumper");
}

int AudioEngine::getGmeTrackCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getGmeVoiceCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("voiceCount", 0);
}

bool AudioEngine::getGmeHasLoopPoint() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("hasLoopPoint", 0) != 0;
}

int AudioEngine::getGmeLoopStartMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("loopStartMs", -1);
}

int AudioEngine::getGmeLoopLengthMs() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("loopLengthMs", -1);
}

std::string AudioEngine::getLazyUsf2GameName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getLazyUsf2Copyright() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getLazyUsf2Year() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getLazyUsf2UsfBy() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("usfBy");
}

std::string AudioEngine::getLazyUsf2LengthTag() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("lengthTag");
}

std::string AudioEngine::getLazyUsf2FadeTag() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("fadeTag");
}

bool AudioEngine::getLazyUsf2EnableCompare() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("enableCompare", 0) != 0;
}

bool AudioEngine::getLazyUsf2EnableFifoFull() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("enableFifoFull", 0) != 0;
}

std::string AudioEngine::getVio2sfGameName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("gameName");
}

std::string AudioEngine::getVio2sfCopyright() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("copyright");
}

std::string AudioEngine::getVio2sfYear() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getVio2sfComment() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("comment");
}

std::string AudioEngine::getVio2sfLengthTag() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("lengthTag");
}

std::string AudioEngine::getVio2sfFadeTag() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("fadeTag");
}

std::string AudioEngine::getSidFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidFormatName");
}

std::string AudioEngine::getSidClockName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidClockName");
}

std::string AudioEngine::getSidSpeedName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidSpeedName");
}

std::string AudioEngine::getSidCompatibilityName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidCompatibilityName");
}

std::string AudioEngine::getSidBackendName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidBackendName");
}

int AudioEngine::getSidChipCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("sidChipCount", 0);
}

std::string AudioEngine::getSidModelSummary() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidModelSummary");
}

std::string AudioEngine::getSidCurrentModelSummary() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidCurrentModelSummary");
}

std::string AudioEngine::getSidBaseAddressSummary() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidBaseAddressSummary");
}

std::string AudioEngine::getSidCommentSummary() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sidCommentSummary");
}

std::string AudioEngine::getSc68FormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getSc68HardwareName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("hardwareName");
}

std::string AudioEngine::getSc68PlatformName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("platformName");
}

std::string AudioEngine::getSc68ReplayName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("replayName");
}

int AudioEngine::getSc68ReplayRateHz() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("replayRateHz", 0);
}

int AudioEngine::getSc68TrackCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

std::string AudioEngine::getSc68AlbumName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("albumName");
}

std::string AudioEngine::getSc68Year() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("year");
}

std::string AudioEngine::getSc68Ripper() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("ripper");
}

std::string AudioEngine::getSc68Converter() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("converter");
}

std::string AudioEngine::getSc68Timer() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("timer");
}

bool AudioEngine::getSc68CanAsid() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("canAsid", 0) != 0;
}

bool AudioEngine::getSc68UsesYm() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("usesYm", 0) != 0;
}

bool AudioEngine::getSc68UsesSte() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("usesSte", 0) != 0;
}

bool AudioEngine::getSc68UsesAmiga() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("usesAmiga", 0) != 0;
}

std::string AudioEngine::getAdplugDescription() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("description");
}

int AudioEngine::getAdplugPatternCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("patternCount", 0);
}

int AudioEngine::getAdplugCurrentPattern() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentPattern", 0);
}

int AudioEngine::getAdplugOrderCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getAdplugCurrentOrder() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentOrder", 0);
}

int AudioEngine::getAdplugCurrentRow() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentRow", 0);
}

int AudioEngine::getAdplugCurrentSpeed() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentSpeed", 0);
}

int AudioEngine::getAdplugInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

std::string AudioEngine::getAdplugInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getHivelyFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getHivelyFormatVersion() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("formatVersion", 0);
}

int AudioEngine::getHivelyPositionCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("positionCount", 0);
}

int AudioEngine::getHivelyRestartPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("restartPosition", -1);
}

int AudioEngine::getHivelyTrackLengthRows() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackLengthRows", 0);
}

int AudioEngine::getHivelyTrackCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getHivelyInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getHivelySpeedMultiplier() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("speedMultiplier", 0);
}

int AudioEngine::getHivelyCurrentPosition() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentPosition", -1);
}

int AudioEngine::getHivelyCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

int AudioEngine::getHivelyCurrentTempo() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentTempo", 0);
}

int AudioEngine::getHivelyMixGainPercent() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("mixGainPercent", 0);
}

std::string AudioEngine::getHivelyInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getKlystrackFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getKlystrackTrackCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("trackCount", 0);
}

int AudioEngine::getKlystrackInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getKlystrackSongLengthRows() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songLengthRows", 0);
}

int AudioEngine::getKlystrackCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

std::string AudioEngine::getKlystrackInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getDnfamitrackerInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getDnfamitrackerSampleNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::string AudioEngine::getDnfamitrackerFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getDnfamitrackerSystemName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getDnfamitrackerExpansionChips() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("expansionChips");
}

std::string AudioEngine::getDnfamitrackerCurrentSongTitle() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("currentSongTitle");
}

int AudioEngine::getDnfamitrackerSongChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songChannelCount", 0);
}

int AudioEngine::getDnfamitrackerSongCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songCount", 0);
}

int AudioEngine::getDnfamitrackerFrameCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("frameCount", 0);
}

int AudioEngine::getDnfamitrackerRowsPerPattern() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("rowsPerPattern", 0);
}

int AudioEngine::getDnfamitrackerSongSpeed() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songSpeed", 0);
}

int AudioEngine::getDnfamitrackerSongTempo() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songTempo", 0);
}

int AudioEngine::getDnfamitrackerCurrentFrame() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentFrame", -1);
}

int AudioEngine::getDnfamitrackerCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

std::string AudioEngine::getFurnaceInstrumentNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("instrumentNames");
}

std::string AudioEngine::getFurnaceSampleNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("sampleNames");
}

std::string AudioEngine::getFurnaceFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

int AudioEngine::getFurnaceSongVersion() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songVersion", 0);
}

std::string AudioEngine::getFurnaceSystemName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("systemName");
}

std::string AudioEngine::getFurnaceSystemNames() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("systemNames");
}

int AudioEngine::getFurnaceSystemCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("systemCount", 0);
}

int AudioEngine::getFurnaceSongChannelCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("songChannelCount", 0);
}

int AudioEngine::getFurnaceInstrumentCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("instrumentCount", 0);
}

int AudioEngine::getFurnaceWavetableCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("wavetableCount", 0);
}

int AudioEngine::getFurnaceSampleCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("sampleCount", 0);
}

int AudioEngine::getFurnaceOrderCount() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("orderCount", 0);
}

int AudioEngine::getFurnaceRowsPerPattern() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("rowsPerPattern", 0);
}

int AudioEngine::getFurnaceCurrentOrder() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentOrder", -1);
}

int AudioEngine::getFurnaceCurrentRow() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentRow", -1);
}

int AudioEngine::getFurnaceCurrentTick() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return -1;
    return decoder->getCoreIntInfo("currentTick", -1);
}

int AudioEngine::getFurnaceCurrentSpeed() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentSpeed", 0);
}

int AudioEngine::getFurnaceGrooveLength() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("grooveLength", 0);
}

float AudioEngine::getFurnaceCurrentHz() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0.0f;
    return decoder->getCoreFloatInfo("currentHz", 0.0f);
}

std::string AudioEngine::getUadeFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("formatName");
}

std::string AudioEngine::getUadeModuleName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleName");
}

std::string AudioEngine::getUadePlayerName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("playerName");
}

std::string AudioEngine::getUadeModuleFileName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleFileName");
}

std::string AudioEngine::getUadePlayerFileName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("playerFileName");
}

std::string AudioEngine::getUadeModuleMd5() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("moduleMd5");
}

std::string AudioEngine::getUadeDetectionExtension() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("detectionExtension");
}

std::string AudioEngine::getUadeDetectedFormatName() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("detectedFormatName");
}

std::string AudioEngine::getUadeDetectedFormatVersion() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return "";
    return decoder->getCoreStringInfo("detectedFormatVersion");
}

bool AudioEngine::getUadeDetectionByContent() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("detectionByContent", 0) != 0;
}

bool AudioEngine::getUadeDetectionIsCustom() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return false;
    return decoder->getCoreIntInfo("detectionIsCustom", 0) != 0;
}

int AudioEngine::getUadeSubsongMin() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("subsongMin", 0);
}

int AudioEngine::getUadeSubsongMax() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("subsongMax", 0);
}

int AudioEngine::getUadeSubsongDefault() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("subsongDefault", 0);
}

int AudioEngine::getUadeCurrentSubsong() {
    std::unique_lock<std::mutex> lock(decoderMutex, std::try_to_lock);
    if (!lock.owns_lock() || !decoder) return 0;
    return decoder->getCoreIntInfo("currentSubsong", 0);
}

int64_t AudioEngine::getUadeModuleBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("moduleBytes", 0);
}

int64_t AudioEngine::getUadeSongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("songBytes", 0);
}

int64_t AudioEngine::getUadeSubsongBytes() {
    std::lock_guard<std::mutex> lock(decoderMutex);
    if (!decoder) return 0;
    return decoder->getCoreInt64Info("subsongBytes", 0);
}
