package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import java.io.File

internal enum class PreviousTrackAction {
    RestartCurrent,
    PlayPreviousTrack,
    NoAction
}

internal sealed class ResumeTarget {
    data class LocalFile(val file: File) : ResumeTarget()
    data class SourceId(val sourceId: String) : ResumeTarget()
}

internal data class SubtuneState(
    val count: Int,
    val currentIndex: Int
)

internal data class SubtuneEntry(
    val index: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Double
)

internal data class NativeSubtuneCursor(
    val count: Int,
    val index: Int
)

internal data class DeferredPlaybackSeek(
    val sourceId: String,
    val positionSeconds: Double
)

internal data class SnapshotApplicationResult(
    val decoderName: String?,
    val pluginVolumeDb: Float?,
    val title: String,
    val artist: String,
    val album: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitDepthLabel: String,
    val subtuneCount: Int,
    val currentSubtuneIndex: Int,
    val repeatModeCapabilitiesFlags: Int,
    val playbackCapabilitiesFlags: Int,
    val durationSeconds: Double
)

internal data class SubtuneSelectionResult(
    val success: Boolean,
    val snapshot: NativeTrackSnapshot? = null,
    val durationSeconds: Double = 0.0,
    val isPlaying: Boolean = false,
    val sourceId: String? = null
)

internal data class LoadedTrackSelectionState(
    val snapshot: NativeTrackSnapshot,
    val initialSubtuneApplied: Boolean
)

internal fun readSubtuneState(selectedFile: File?): SubtuneState {
    if (selectedFile == null) {
        return SubtuneState(count = 0, currentIndex = 0)
    }
    val count = NativeBridge.getSubtuneCount().coerceAtLeast(0)
    val currentIndex = if (count <= 0) {
        0
    } else {
        NativeBridge.getCurrentSubtuneIndex().coerceIn(0, count - 1)
    }
    return SubtuneState(count = count, currentIndex = currentIndex)
}

internal fun readNativeSubtuneCursor(): NativeSubtuneCursor {
    val count = NativeBridge.getSubtuneCount().coerceAtLeast(1)
    val index = NativeBridge.getCurrentSubtuneIndex().coerceIn(0, count - 1)
    return NativeSubtuneCursor(count = count, index = index)
}

internal fun hasNativeSubtuneCursorChanged(
    nativeCursor: NativeSubtuneCursor,
    currentSubtuneCount: Int,
    currentSubtuneIndex: Int
): Boolean {
    return nativeCursor.count != currentSubtuneCount || nativeCursor.index != currentSubtuneIndex
}

internal fun readSubtuneEntries(subtuneCount: Int): List<SubtuneEntry> {
    if (subtuneCount <= 0) return emptyList()
    return (0 until subtuneCount).map { index ->
        val title = NativeBridge.getSubtuneTitle(index).trim()
        val artist = NativeBridge.getSubtuneArtist(index).trim()
        val durationSeconds = NativeBridge.getSubtuneDurationSeconds(index)
        SubtuneEntry(
            index = index,
            title = title.ifBlank { "Subtune ${index + 1}" },
            artist = artist,
            durationSeconds = durationSeconds
        )
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveTrackRepeatMode(
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    preferredRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int
): RepeatMode {
    val allowTrackRepeat = true
    val allowSubtuneRepeat = subtuneCount > 1
    return resolveActiveRepeatMode(
        preferredRepeatMode = preferredRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        includeSubtuneRepeat = allowSubtuneRepeat,
        includeTrackRepeat = allowTrackRepeat
    )
}

internal fun shouldApplyRepeatModeNow(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    seekInProgress: Boolean
): Boolean {
    val hasActiveSource = selectedFile != null || !currentPlaybackSourceId.isNullOrBlank()
    return hasActiveSource && !seekInProgress
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveNextRepeatMode(
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    selectedFile: File?,
    durationSeconds: Double,
    subtuneCount: Int,
    activeRepeatMode: RepeatMode,
    repeatModeCapabilitiesFlags: Int
): RepeatMode? {
    if (!supportsLiveRepeatMode(playbackCapabilitiesFlags)) return null
    if (seekInProgress) return null
    val allowTrackRepeat = true
    val allowSubtuneRepeat = subtuneCount > 1
    return cycleRepeatModeValue(
        activeRepeatMode = activeRepeatMode,
        repeatModeCapabilitiesFlags = repeatModeCapabilitiesFlags,
        includeSubtuneRepeat = allowSubtuneRepeat,
        includeTrackRepeat = allowTrackRepeat
    )
}

internal fun snapshotAppearsValid(snapshot: NativeTrackSnapshot): Boolean {
    return snapshot.sampleRateHz > 0 ||
        snapshot.durationSeconds > 0.0 ||
        snapshot.title.isNotBlank() ||
        snapshot.artist.isNotBlank()
}

internal fun shouldPollTrackMetadata(
    metadataPollElapsedMs: Long,
    metadataTitle: String,
    metadataArtist: String
): Boolean {
    return metadataPollElapsedMs >= 540L || metadataTitle.isBlank() || metadataArtist.isBlank()
}

internal fun resolvePlaybackServiceSourceId(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    cacheRoot: File
): String? {
    return currentPlaybackSourceId
        ?: selectedFile
            ?.takeIf { it.absolutePath.startsWith(cacheRoot.absolutePath) }
            ?.let { sourceIdForCachedFileName(cacheRoot, it.name) }
}

internal fun currentTrackListIndex(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>
): Int {
    val localIndex = currentTrackIndexForList(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles
    )
    if (localIndex >= 0) return localIndex
    val activeSourceId = currentPlaybackSourceId ?: selectedFile?.absolutePath ?: return -1
    return visiblePlayableSourceIds.indexOfFirst { sourceId ->
        samePath(sourceId, activeSourceId)
    }
}

internal fun canNavigateToPreviousTrack(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>,
    playlistWrapNavigation: Boolean
): Boolean {
    val activePlaylist = if (visiblePlayableFiles.isNotEmpty()) {
        visiblePlayableFiles
    } else {
        visiblePlayableSourceIds
    }
    val currentIndex = currentTrackListIndex(
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        visiblePlayableFiles = visiblePlayableFiles,
        visiblePlayableSourceIds = visiblePlayableSourceIds
    )
    return if (playlistWrapNavigation) {
        currentIndex >= 0
    } else {
        currentIndex > 0 && activePlaylist.isNotEmpty()
    }
}

internal fun canNavigateToNextTrack(
    selectedFile: File?,
    currentPlaybackSourceId: String?,
    visiblePlayableFiles: List<File>,
    visiblePlayableSourceIds: List<String>,
    playlistWrapNavigation: Boolean
): Boolean {
    val activePlaylistSize = if (visiblePlayableFiles.isNotEmpty()) {
        visiblePlayableFiles.size
    } else {
        visiblePlayableSourceIds.size
    }
    val currentIndex = currentTrackListIndex(
        selectedFile = selectedFile,
        currentPlaybackSourceId = currentPlaybackSourceId,
        visiblePlayableFiles = visiblePlayableFiles,
        visiblePlayableSourceIds = visiblePlayableSourceIds
    )
    return if (playlistWrapNavigation) {
        currentIndex >= 0
    } else {
        currentIndex in 0 until (activePlaylistSize - 1)
    }
}

internal fun tryPlayAdjacentTrack(
    selectedFile: File?,
    visiblePlayableFiles: List<File>,
    offset: Int,
    onPlay: (File) -> Unit
): Boolean {
    val target = adjacentTrackForOffset(
        selectedFile = selectedFile,
        visiblePlayableFiles = visiblePlayableFiles,
        offset = offset
    ) ?: return false
    onPlay(target)
    return true
}

internal fun resolvePreviousTrackAction(
    previousRestartsAfterThreshold: Boolean,
    hasTrackLoaded: Boolean,
    positionSeconds: Double,
    hasPreviousTrack: Boolean
): PreviousTrackAction {
    val shouldRestartCurrent = shouldRestartCurrentTrackOnPrevious(
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        hasTrackLoaded = hasTrackLoaded,
        positionSeconds = positionSeconds
    )
    if (shouldRestartCurrent) return PreviousTrackAction.RestartCurrent
    if (hasPreviousTrack) return PreviousTrackAction.PlayPreviousTrack
    if (hasTrackLoaded) return PreviousTrackAction.RestartCurrent
    return PreviousTrackAction.NoAction
}

internal fun resolveResumeTarget(
    lastStoppedFile: File?,
    lastStoppedSourceId: String?
): ResumeTarget? {
    val resumableFile = lastStoppedFile?.takeIf { it.exists() && it.isFile }
    if (resumableFile != null) return ResumeTarget.LocalFile(resumableFile)
    val sourceId = lastStoppedSourceId?.takeIf { it.isNotBlank() } ?: return null
    return ResumeTarget.SourceId(sourceId)
}

internal fun buildSnapshotApplicationResult(
    snapshot: NativeTrackSnapshot,
    prefs: SharedPreferences
): SnapshotApplicationResult {
    val decoderName = snapshot.decoderName
    val pluginVolumeDb = decoderName?.let { readPluginVolumeForDecoder(prefs, it) }
    return SnapshotApplicationResult(
        decoderName = decoderName,
        pluginVolumeDb = pluginVolumeDb,
        title = snapshot.title,
        artist = snapshot.artist,
        album = snapshot.album,
        sampleRateHz = snapshot.sampleRateHz,
        channelCount = snapshot.channelCount,
        bitDepthLabel = snapshot.bitDepthLabel,
        subtuneCount = snapshot.subtuneCount,
        currentSubtuneIndex = snapshot.currentSubtuneIndex,
        repeatModeCapabilitiesFlags = snapshot.repeatModeCapabilitiesFlags,
        playbackCapabilitiesFlags = snapshot.playbackCapabilitiesFlags,
        durationSeconds = snapshot.durationSeconds
    )
}

internal fun loadTrackSnapshotForSelection(
    path: String,
    initialSubtuneIndex: Int?
): LoadedTrackSelectionState {
    NativeBridge.replaceCurrentAudio(path)
    val subtuneCount = NativeBridge.getSubtuneCount().coerceAtLeast(0)
    val initialSubtuneApplied = initialSubtuneIndex
        ?.takeIf { subtuneCount > 0 && it in 0 until subtuneCount }
        ?.let { targetIndex -> NativeBridge.selectSubtune(targetIndex) }
        ?: false
    val snapshot = readNativeTrackSnapshot()
    val decoderName = snapshot.decoderName?.trim()?.takeIf { it.isNotEmpty() } ?: readCurrentDecoderName()
    if (decoderName != null) {
        val context = NativeBridge.requireAppContext()
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        applyEffectiveDspSettingsForCoreAction(prefs, decoderName)
    }

    return LoadedTrackSelectionState(
        snapshot = snapshot,
        initialSubtuneApplied = initialSubtuneApplied
    )
}

internal fun selectSubtuneAndReadState(
    index: Int,
    selectedFile: File?,
    currentPlaybackSourceId: String?
): SubtuneSelectionResult {
    if (selectedFile == null) return SubtuneSelectionResult(success = false)
    if (!NativeBridge.selectSubtune(index)) return SubtuneSelectionResult(success = false)
    val snapshot = readNativeTrackSnapshot()
    val decoderName = snapshot.decoderName?.trim()?.takeIf { it.isNotEmpty() } ?: readCurrentDecoderName()
    if (decoderName != null) {
        val context = NativeBridge.requireAppContext()
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        applyEffectiveDspSettingsForCoreAction(prefs, decoderName)
    }

    return SubtuneSelectionResult(
        success = true,
        snapshot = snapshot,
        durationSeconds = NativeBridge.getDuration(),
        isPlaying = NativeBridge.isEnginePlaying(),
        sourceId = currentPlaybackSourceId ?: selectedFile.absolutePath
    )
}

private data class ImmediateDspSettings(
    val bassEnabled: Boolean,
    val bassDepth: Int,
    val bassRange: Int,
    val surroundEnabled: Boolean,
    val surroundDepth: Int,
    val surroundDelayMs: Int,
    val reverbEnabled: Boolean,
    val reverbDepth: Int,
    val reverbPreset: Int,
    val bitCrushEnabled: Boolean,
    val bitCrushBits: Int
)

private fun normalizeBassDepthPref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (8 - value.coerceIn(4, 8)).coerceIn(0, 4)
    }
}

private fun normalizeBassRangePref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (4 - ((value.coerceIn(5, 21) - 1) / 5)).coerceIn(0, 4)
    }
}

private fun normalizeSurroundDelayMsPref(value: Int): Int {
    if (value in 5..45 && value % 5 == 0) return value
    val clamped = value.coerceIn(5, 45)
    val step = ((clamped - 5) + 2) / 5
    return 5 + (step * 5)
}

private fun defaultImmediateDspSettings(): ImmediateDspSettings {
    return ImmediateDspSettings(
        bassEnabled = AppDefaults.AudioProcessing.Dsp.bassEnabled,
        bassDepth = AppDefaults.AudioProcessing.Dsp.bassDepth,
        bassRange = AppDefaults.AudioProcessing.Dsp.bassRange,
        surroundEnabled = AppDefaults.AudioProcessing.Dsp.surroundEnabled,
        surroundDepth = AppDefaults.AudioProcessing.Dsp.surroundDepth,
        surroundDelayMs = AppDefaults.AudioProcessing.Dsp.surroundDelayMs,
        reverbEnabled = AppDefaults.AudioProcessing.Dsp.reverbEnabled,
        reverbDepth = AppDefaults.AudioProcessing.Dsp.reverbDepth,
        reverbPreset = AppDefaults.AudioProcessing.Dsp.reverbPreset,
        bitCrushEnabled = AppDefaults.AudioProcessing.Dsp.bitCrushEnabled,
        bitCrushBits = AppDefaults.AudioProcessing.Dsp.bitCrushBits
    )
}

private fun readGlobalImmediateDspSettings(prefs: SharedPreferences): ImmediateDspSettings {
    val defaults = defaultImmediateDspSettings()
    return ImmediateDspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, defaults.bitCrushBits).coerceIn(1, 24)
    )
}

private fun readCoreImmediateDspSettings(prefs: SharedPreferences, coreName: String): ImmediateDspSettings {
    val defaults = defaultImmediateDspSettings()
    return ImmediateDspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey(coreName), defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassDepthKey(coreName), defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassRangeKey(coreName), defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(coreName), defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDepthKey(coreName), defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(coreName), defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreReverbEnabledKey(coreName), defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbDepthKey(coreName), defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbPresetKey(coreName), defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(coreName), defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(coreName), defaults.bitCrushBits).coerceIn(1, 24)
    )
}

private fun hasCoreImmediateDspOverrides(prefs: SharedPreferences, coreName: String): Boolean {
    return prefs.contains(AppPreferenceKeys.audioDspCoreBassEnabledKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassDepthKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassRangeKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDepthKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbEnabledKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbDepthKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbPresetKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(coreName)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(coreName))
}

private fun applyImmediateDspSettingsToNative(settings: ImmediateDspSettings) {
    NativeBridge.setDspBassEnabled(settings.bassEnabled)
    NativeBridge.setDspBassDepth(settings.bassDepth)
    NativeBridge.setDspBassRange(settings.bassRange)
    NativeBridge.setDspSurroundEnabled(settings.surroundEnabled)
    NativeBridge.setDspSurroundDepth(settings.surroundDepth)
    NativeBridge.setDspSurroundDelayMs(settings.surroundDelayMs)
    NativeBridge.setDspReverbEnabled(settings.reverbEnabled)
    NativeBridge.setDspReverbDepth(settings.reverbDepth)
    NativeBridge.setDspReverbPreset(settings.reverbPreset)
    NativeBridge.setDspBitCrushEnabled(settings.bitCrushEnabled)
    NativeBridge.setDspBitCrushBits(settings.bitCrushBits)
}

internal fun applyEffectiveDspSettingsForCoreAction(
    prefs: SharedPreferences,
    coreName: String
) {
    val globalSettings = readGlobalImmediateDspSettings(prefs)
    val coreSettings = readCoreImmediateDspSettings(prefs, coreName)
    val coreHasOverrides = hasCoreImmediateDspOverrides(prefs, coreName)
    val ignoreGlobalForCore = prefs.getBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(coreName), false)
    val effectiveSettings = if (ignoreGlobalForCore || coreHasOverrides) {
        coreSettings
    } else {
        globalSettings
    }
    applyImmediateDspSettingsToNative(effectiveSettings)
}
