package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.CrsidOptionKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.GmeOptionKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.SidPlayFpOptionKeys
import com.flopster101.siliconplayer.SubtuneEntry
import com.flopster101.siliconplayer.UadeOptionKeys
import com.flopster101.siliconplayer.availableRepeatModesForFlags
import com.flopster101.siliconplayer.canSeekPlayback
import com.flopster101.siliconplayer.hasReliableDuration
import com.flopster101.siliconplayer.platform.AppPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DesktopPlaybackSession(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    var currentFile by mutableStateOf<File?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var positionSeconds by mutableDoubleStateOf(0.0)
        private set

    var durationSeconds by mutableDoubleStateOf(0.0)
        private set

    var title by mutableStateOf("")
        private set

    var artist by mutableStateOf("")
        private set

    var album by mutableStateOf("")
        private set

    var decoderName by mutableStateOf<String?>(null)
        private set

    var sampleRateHz by mutableIntStateOf(0)
        private set

    var channelCount by mutableIntStateOf(0)
        private set

    var bitDepthLabel by mutableStateOf("")
        private set

    var repeatMode by mutableStateOf(RepeatMode.None)
        private set

    var subtuneIndex by mutableIntStateOf(0)
        private set

    var subtuneCount by mutableIntStateOf(0)
        private set

    var subtuneEntries by mutableStateOf<List<SubtuneEntry>>(emptyList())
        private set

    var artwork by mutableStateOf<ImageBitmap?>(null)
        private set

    var playbackCapabilitiesFlags by mutableIntStateOf(0)
        private set

    var repeatModeCapabilitiesFlags by mutableIntStateOf(0)
        private set

    var canSeek by mutableStateOf(true)
        private set

    var hasReliableDuration by mutableStateOf(true)
        private set

    private var tickerJob: Job? = null
    private var isUserSeeking = false
    private var currentSource: String? = null
    private var stoppedSource: String? = null
    // Live prefs source for track-open native pushes; attached once host prefs exist.
    var trackOptionsPrefs: AppPreferences? = null

    init {
        startTicker()
    }

    fun loadFile(file: File, autoStart: Boolean = true): Boolean {
        if (!file.exists() || !file.isFile) return false

        NativeBridge.stopEngineNative()
        val forced = NativeBridge.consumeForcedDecoderOneShot()
        if (forced != null) {
            NativeBridge.loadAudioWithDecoder(file.absolutePath, forced)
        } else {
            NativeBridge.loadAudio(file.absolutePath)
        }
        trackOptionsPrefs?.let { pushStoredTrackOptionsToNative(it) }

        currentFile = file
        currentSource = file.absolutePath
        stoppedSource = null
        refreshMetadata()
        artwork = null
        scope.launch(Dispatchers.IO) {
            artwork = DesktopArtworkSupport.loadArtworkForFile(file)
        }

        // loadAudio without start leaves a loaded-but-paused track (mirrors autoStart=false).
        if (autoStart) {
            NativeBridge.startEngineNative()
            isPlaying = NativeBridge.isEnginePlaying()
        } else {
            isPlaying = false
        }
        return true
    }

    fun loadSource(source: String, titleHint: String? = null, artistHint: String? = null, autoStart: Boolean = true): Boolean {
        val file = File(source)
        if (file.exists() && file.isFile) {
            return loadFile(file, autoStart)
        }
        NativeBridge.stopEngineNative()
        val forced = NativeBridge.consumeForcedDecoderOneShot()
        if (forced != null) {
            NativeBridge.loadAudioWithDecoder(source, forced)
        } else {
            NativeBridge.loadAudio(source)
        }
        trackOptionsPrefs?.let { pushStoredTrackOptionsToNative(it) }
        currentFile = File(source)
        currentSource = source
        stoppedSource = null
        refreshMetadata()
        artwork = null
        scope.launch(Dispatchers.IO) {
            val f = currentFile
            if (f != null && f.exists() && f.isFile) {
                artwork = DesktopArtworkSupport.loadArtworkForFile(f)
            }
        }
        if (title.isBlank() && !titleHint.isNullOrBlank()) {
            title = titleHint
        }
        if (artist.isBlank() && !artistHint.isNullOrBlank()) {
            artist = artistHint
        }
        // loadAudio without start leaves a loaded-but-paused track (mirrors autoStart=false).
        if (autoStart) {
            NativeBridge.startEngineNative()
            isPlaying = NativeBridge.isEnginePlaying()
        } else {
            isPlaying = false
        }
        return true
    }

    // Mirrors Android: skip the fade natives when off or near track start.
    var fadePauseResume: Boolean = true

    fun play() {
        val sourceToResume = stoppedSource
        if (sourceToResume != null) {
            stoppedSource = null
            loadSource(sourceToResume)
            return
        }
        if (currentFile == null) return
        if (fadePauseResume && positionSeconds > 0.05) {
            NativeBridge.startEngineWithPauseResumeFadeNative()
        } else {
            NativeBridge.startEngineNative()
        }
        isPlaying = true
    }

    fun pause() {
        if (fadePauseResume && positionSeconds > 0.05) {
            NativeBridge.stopEngineWithPauseResumeFadeNative()
        } else {
            NativeBridge.stopEngineNative()
        }
        isPlaying = false
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun canResume(): Boolean = currentFile != null || stoppedSource != null

    fun stop() {
        NativeBridge.stopEngineNative()
        NativeBridge.releaseCurrentDecoder()
        if (currentSource != null) {
            stoppedSource = currentSource
        }
        isPlaying = false
        clearTrackState()
    }

    /**
     * Mirrors Android's stop-and-clear: the decoder is unloaded and the track state cleared while
     * the source is remembered so Play restarts it from the beginning.
     */
    private fun clearTrackState() {
        currentFile = null
        currentSource = null
        title = ""
        artist = ""
        album = ""
        decoderName = null
        sampleRateHz = 0
        channelCount = 0
        bitDepthLabel = ""
        durationSeconds = 0.0
        positionSeconds = 0.0
        subtuneIndex = 0
        subtuneCount = 0
        subtuneEntries = emptyList()
        artwork = null
        playbackCapabilitiesFlags = 0
        repeatModeCapabilitiesFlags = 0
        canSeek = false
        hasReliableDuration = false
    }

    fun seekTo(seconds: Double) {
        val target = seconds.coerceIn(0.0, if (durationSeconds > 0) durationSeconds else 0.0)
        positionSeconds = target
        isUserSeeking = true
        NativeBridge.seekTo(target)
        isUserSeeking = false
    }

    fun cycleRepeatMode() {
        val caps = NativeBridge.getRepeatModeCapabilities()
        val available = availableRepeatModesForFlags(caps)
        val currentIndex = available.indexOf(repeatMode)
        val nextMode = if (currentIndex in available.indices && currentIndex + 1 < available.size) {
            available[currentIndex + 1]
        } else {
            available.firstOrNull() ?: RepeatMode.None
        }
        repeatMode = nextMode
        val nativeMode = when (nextMode) {
            RepeatMode.None -> 0
            RepeatMode.Track -> 1
            RepeatMode.Subtune -> 2
            RepeatMode.Playlist -> 3
            RepeatMode.LoopPoint -> 4
        }
        NativeBridge.setRepeatMode(nativeMode)
    }

    fun nextSubtune() {
        if (subtuneCount > 1 && subtuneIndex + 1 < subtuneCount) {
            selectSubtune(subtuneIndex + 1)
        }
    }

    fun previousSubtune() {
        if (subtuneCount > 1 && subtuneIndex > 0) {
            selectSubtune(subtuneIndex - 1)
        }
    }

    fun selectSubtune(index: Int) {
        if (NativeBridge.selectSubtune(index)) {
            refreshMetadata()
        }
    }

    private fun refreshMetadata() {
        val currentTitle = NativeBridge.getTrackTitle().trim()
        val currentArtist = NativeBridge.getTrackArtist().trim()
        val currentAlbum = NativeBridge.getTrackAlbum().trim()
        val fileLeaf = currentFile?.nameWithoutExtension ?: "Unknown"

        title = if (currentTitle.isNotBlank()) currentTitle else fileLeaf
        artist = if (currentArtist.isNotBlank()) currentArtist else "Unknown Artist"
        album = if (currentAlbum.isNotBlank()) currentAlbum else ""
        decoderName = NativeBridge.getCurrentDecoderName()
        sampleRateHz = NativeBridge.getTrackSampleRate()
        channelCount = NativeBridge.getTrackChannelCount()
        bitDepthLabel = resolveTrackBitDepthLabel()
        durationSeconds = NativeBridge.getDuration()
        subtuneIndex = NativeBridge.getCurrentSubtuneIndex()
        subtuneCount = NativeBridge.getSubtuneCount()
        subtuneEntries = if (subtuneCount > 1) {
            (0 until subtuneCount).map { idx ->
                val subTitle = NativeBridge.getSubtuneTitle(idx).trim()
                val subArtist = NativeBridge.getSubtuneArtist(idx).trim()
                val subDuration = NativeBridge.getSubtuneDurationSeconds(idx)
                SubtuneEntry(
                    index = idx,
                    title = subTitle.ifBlank { "Subtune ${idx + 1}" },
                    artist = subArtist,
                    durationSeconds = subDuration
                )
            }
        } else {
            emptyList()
        }
        playbackCapabilitiesFlags = NativeBridge.getPlaybackCapabilities()
        repeatModeCapabilitiesFlags = NativeBridge.getRepeatModeCapabilities()
        canSeek = canSeekPlayback(playbackCapabilitiesFlags)
        hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags)
    }

    private fun resolveTrackBitDepthLabel(): String {
        val rawLabel = NativeBridge.getTrackBitDepthLabel().trim()
        val rawInt = NativeBridge.getTrackBitDepth()
        return when {
            rawLabel.isNotBlank() && rawLabel != "-bit" && !rawLabel.equals("Unknown", ignoreCase = true) -> {
                if (rawLabel.matches(Regex("^\\d+$"))) "${rawLabel}-bit" else rawLabel
            }
            rawInt > 0 -> "${rawInt}-bit"
            currentFile?.extension.equals("flac", ignoreCase = true) -> {
                currentFile?.let { DesktopArtworkSupport.extractFlacBitDepth(it) }?.let { "${it}-bit" } ?: "Unknown"
            }
            else -> rawLabel.takeIf { it.isNotBlank() && it != "-bit" } ?: "Unknown"
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                if (!isUserSeeking) {
                    val pos = NativeBridge.getPosition()
                    val dur = NativeBridge.getDuration()
                    val playing = NativeBridge.isEnginePlaying()

                    positionSeconds = pos
                    if (dur > 0.0) {
                        durationSeconds = dur
                    }
                    isPlaying = playing

                    if (bitDepthLabel.isBlank() || bitDepthLabel == "-bit" || bitDepthLabel == "Unknown") {
                        val resolved = resolveTrackBitDepthLabel()
                        if (resolved != "Unknown" && resolved != "-bit") {
                            bitDepthLabel = resolved
                        }
                    }

                    if (NativeBridge.consumeNaturalEndEvent()) {
                        when (repeatMode) {
                            RepeatMode.Track, RepeatMode.Subtune, RepeatMode.LoopPoint -> {
                                seekTo(0.0)
                                play()
                            }
                            else -> {
                                stop()
                            }
                        }
                    }
                }
                delay(30)
            }
        }
    }

    fun dispose() {
        tickerJob?.cancel()
        tickerJob = null
        stop()
    }
}

// Mirrors Android AppNavigationCoreEffects unknown-duration: live setCoreOption on all four cores.
internal fun pushUnknownTrackDurationToNative(seconds: Int) {
    val value = seconds.coerceIn(1, 86400).toString()
    runCatching { NativeBridge.setCoreOption(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.UNKNOWN_DURATION_SECONDS, value) }
    runCatching { NativeBridge.setCoreOption(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.UNKNOWN_DURATION_SECONDS, value) }
    runCatching { NativeBridge.setCoreOption(DecoderNames.C_RSID, CrsidOptionKeys.UNKNOWN_DURATION_SECONDS, value) }
    runCatching { NativeBridge.setCoreOption(DecoderNames.UADE, UadeOptionKeys.UNKNOWN_DURATION_SECONDS, value) }
}

// Engine-level end-fade (same shared C++ AudioEngine; native clamps duration/curve).
internal fun pushEndFadeToNative(applyToAll: Boolean, durationMs: Int, curve: EndFadeCurve) {
    runCatching { NativeBridge.setEndFadeApplyToAllTracks(applyToAll) }
    runCatching { NativeBridge.setEndFadeDurationMs(durationMs) }
    runCatching { NativeBridge.setEndFadeCurve(curve.nativeValue) }
}

// Stored player prefs pushed at track-open/startup; fallbacks match desktop settings reads.
internal fun pushStoredTrackOptionsToNative(prefs: AppPreferences) {
    pushUnknownTrackDurationToNative(prefs.getInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, GmeDefaults.unknownDurationSeconds))
    pushEndFadeToNative(
        prefs.getBoolean(AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS, AppDefaults.Player.endFadeApplyToAllTracks),
        prefs.getInt(AppPreferenceKeys.END_FADE_DURATION_MS, AppDefaults.Player.endFadeDurationMs),
        EndFadeCurve.fromStorage(prefs.getString(AppPreferenceKeys.END_FADE_CURVE, AppDefaults.Player.endFadeCurve.storageValue))
    )
}
