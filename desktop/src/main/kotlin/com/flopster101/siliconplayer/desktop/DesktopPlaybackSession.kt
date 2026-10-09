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
import com.flopster101.siliconplayer.resolveSubtuneNowPlayingText
import com.flopster101.siliconplayer.SidPlayFpOptionKeys
import com.flopster101.siliconplayer.SubtuneEntry
import com.flopster101.siliconplayer.UadeOptionKeys
import com.flopster101.siliconplayer.canSeekPlayback
import com.flopster101.siliconplayer.cycleRepeatModeValue
import com.flopster101.siliconplayer.hasReliableDuration
import com.flopster101.siliconplayer.resolveActiveRepeatMode
import com.flopster101.siliconplayer.resolveCachedRemoteSourceId
import com.flopster101.siliconplayer.resolveManualSourceInput
import com.flopster101.siliconplayer.supportsLiveRepeatMode
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.hasCoreDspOverrides
import com.flopster101.siliconplayer.audio.readCoreDspSettings
import com.flopster101.siliconplayer.audio.readCoreIgnoreGlobalDsp
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.audio.resolveEffectiveDspSettings
import com.flopster101.siliconplayer.platform.AppPreferences
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DesktopPlaybackSession(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    // Guards an async play against a stop/pause that lands first: only the
    // latest request may start the engine.
    private val playGeneration = AtomicLong(0L)

    companion object {
        private const val STALE_PAUSE_ECHO_NS = 2_000_000_000L
    }

    // AVRCP senders poll our state slowly; a stale PAUSE echo landing just
    // after our resume must not re-pause. UI and MPRIS pauses stay immediate.
    private var lastResumeRequestNs = 0L
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

    var lastUsedCoreName by mutableStateOf<String?>(null)
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

    // Remote-aware playback identity, mirroring Android's currentPlaybackSourceId.
    var currentSourceId by mutableStateOf<String?>(null)
        private set
    var currentRequestUrl by mutableStateOf<String?>(null)
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
    private var flacBitDepthProbePath: String? = null
    private var flacBitDepthProbeResult = "Unknown"
    private var currentSource: String? = null
    private var stoppedSource: String? = null
    private var stoppedSubtuneIndex: Int? = null
    // Live prefs source for track-open native pushes; attached once host prefs exist.
    var trackOptionsPrefs: AppPreferences? = null
    // Persisted preferred mode (host-owned); resolved per track into repeatMode.
    var preferredRepeatMode: RepeatMode = RepeatMode.None
    // Queue advance for track-end Playlist/None; returns false when the queue is exhausted.
    var onAdvanceQueue: ((wrap: Boolean) -> Boolean)? = null

    init {
        startTicker()
    }

    fun loadFile(file: File, autoStart: Boolean = true, initialSubtuneIndex: Int? = null): Boolean {
        if (!file.exists() || !file.isFile) return false

        armLoadCrashGuard(file.absolutePath)
        try {
            loadFileGuarded(file, autoStart, initialSubtuneIndex)
            return true
        } finally {
            clearLoadCrashGuard()
        }
    }

    private fun loadFileGuarded(file: File, autoStart: Boolean, initialSubtuneIndex: Int?) {
        // Every track load destroys the device first: anything already popped
        // into PulseAudio's buffers would otherwise keep playing the old song
        // after the click, over the new one's head (cork retains buffers, only
        // destroy flushes). start() rebuilds it through the prefill path.
        NativeBridge.teardownOutputStream()
        // No pre-stop when auto-starting: setUrl swaps the decoder under the
        // running stream (Android parity). stopEngineNative runs on a detached
        // thread and could otherwise clear the new track's rendered head,
        // truncating the song start intermittently.
        // Synchronous: the detached stop could otherwise land after the
        // install below and wipe the new track.
        if (!autoStart) {
            NativeBridge.stopEngineSyncNative()
        }
        val forced = NativeBridge.consumeForcedDecoderOneShot()
        if (forced != null) {
            NativeBridge.loadAudioWithDecoder(file.absolutePath, forced)
        } else {
            NativeBridge.loadAudio(file.absolutePath)
        }
        trackOptionsPrefs?.let { pushStoredTrackOptionsToNative(it) }

        val remoteSourceId = runCatching { resolveCachedRemoteSourceId(file.absolutePath) }.getOrNull()
        currentFile = file
        currentSource = file.absolutePath
        currentSourceId = remoteSourceId ?: file.absolutePath
        currentRequestUrl = remoteSourceId
        stoppedSource = null
        stoppedSubtuneIndex = null
        refreshMetadata()
        if (initialSubtuneIndex != null &&
            initialSubtuneIndex in 0 until subtuneCount &&
            NativeBridge.selectSubtune(initialSubtuneIndex)
        ) {
            refreshMetadata()
        }
        refreshRepeatMode()
        // Push the effective per-core DSP synchronously (Android parity):
        // the Main LaunchedEffect only runs after start, so a deferred push
        // would switch the reverb on audibly mid-song.
        trackOptionsPrefs?.let { prefs ->
            applyDspSettingsToNative(
                resolveEffectiveDspSettings(
                    coreName = decoderName,
                    global = readGlobalDspSettings(prefs),
                    core = readCoreDspSettings(prefs, decoderName),
                    coreHasOverrides = hasCoreDspOverrides(prefs, decoderName),
                    ignoreGlobalForCore = readCoreIgnoreGlobalDsp(prefs, decoderName)
                )
            )
        }
        artwork = null
        scope.launch(Dispatchers.IO) {
            artwork = DesktopArtworkSupport.loadArtworkForSource(
                displayFile = file,
                sourceId = remoteSourceId ?: file.absolutePath,
                requestUrl = remoteSourceId
            )
        }

        // loadAudio without start leaves a loaded-but-paused track (mirrors autoStart=false).
        if (autoStart) {
            NativeBridge.startEngineNative()
            isPlaying = NativeBridge.isEnginePlaying()
        } else {
            isPlaying = false
        }
    }

    fun loadSource(
        source: String,
        titleHint: String? = null,
        artistHint: String? = null,
        autoStart: Boolean = true,
        initialSubtuneIndex: Int? = null
    ): Boolean {
        val file = File(source)
        if (file.exists() && file.isFile) {
            return loadFile(file, autoStart, initialSubtuneIndex)
        }
        armLoadCrashGuard(source)
        try {
            loadSourceGuarded(source, titleHint, artistHint, autoStart, initialSubtuneIndex)
            return true
        } finally {
            clearLoadCrashGuard()
        }
    }

    // Crash guard: armed around every decoder load so a mid-load process death
    // is skipped once at the next session restore instead of crash-looping.
    // Mirrors Android NativeBridge.replaceCurrentAudio.
    private fun armLoadCrashGuard(path: String) {
        runCatching {
            trackOptionsPrefs?.edit()
                ?.putString(AppPreferenceKeys.SESSION_LOAD_CRASH_GUARD_PATH, path)
                ?.apply()
        }
    }

    private fun clearLoadCrashGuard() {
        runCatching {
            trackOptionsPrefs?.edit()
                ?.remove(AppPreferenceKeys.SESSION_LOAD_CRASH_GUARD_PATH)
                ?.apply()
        }
    }

    private fun loadSourceGuarded(
        source: String,
        titleHint: String?,
        artistHint: String?,
        autoStart: Boolean,
        initialSubtuneIndex: Int?
    ) {
        // Same teardown rule as loadFileGuarded.
        NativeBridge.teardownOutputStream()
        // Same no-pre-stop rule as loadFileGuarded: the detached stop could
        // wipe the new source's rendered head before start() prefills.
        // Synchronous: the detached stop could otherwise land after the
        // install below and wipe the new track.
        if (!autoStart) {
            NativeBridge.stopEngineSyncNative()
        }
        val forced = NativeBridge.consumeForcedDecoderOneShot()
        if (forced != null) {
            NativeBridge.loadAudioWithDecoder(source, forced)
        } else {
            NativeBridge.loadAudio(source)
        }
        trackOptionsPrefs?.let { pushStoredTrackOptionsToNative(it) }
        val resolved = resolveManualSourceInput(source)
        currentFile = resolved?.displayFile ?: File(source)
        currentSource = source
        currentSourceId = resolved?.sourceId ?: source
        currentRequestUrl = resolved?.requestUrl ?: source
        stoppedSource = null
        stoppedSubtuneIndex = null
        refreshMetadata()
        if (initialSubtuneIndex != null &&
            initialSubtuneIndex in 0 until subtuneCount &&
            NativeBridge.selectSubtune(initialSubtuneIndex)
        ) {
            refreshMetadata()
        }
        refreshRepeatMode()
        // Same synchronous per-core DSP push as loadFileGuarded.
        trackOptionsPrefs?.let { prefs ->
            applyDspSettingsToNative(
                resolveEffectiveDspSettings(
                    coreName = decoderName,
                    global = readGlobalDspSettings(prefs),
                    core = readCoreDspSettings(prefs, decoderName),
                    coreHasOverrides = hasCoreDspOverrides(prefs, decoderName),
                    ignoreGlobalForCore = readCoreIgnoreGlobalDsp(prefs, decoderName)
                )
            )
        }
        artwork = null
        scope.launch(Dispatchers.IO) {
            val displayFile = currentFile
            val sourceId = currentSourceId
            if (displayFile != null) {
                artwork = DesktopArtworkSupport.loadArtworkForSource(
                    displayFile = displayFile,
                    sourceId = sourceId,
                    requestUrl = resolved?.requestUrl ?: sourceId
                )
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
    }

    // Mirrors Android: skip the fade natives when off or near track start.
    var fadePauseResume: Boolean = true

    fun play() {
        val sourceToResume = stoppedSource
        if (sourceToResume != null) {
            stoppedSource = null
            val subtuneToResume = stoppedSubtuneIndex
            stoppedSubtuneIndex = null
            loadSource(sourceToResume, initialSubtuneIndex = subtuneToResume)
            lastResumeRequestNs = System.nanoTime()
            return
        }
        if (currentFile == null) return
        // Engine start can rebuild the output device synchronously; never
        // block the caller (UI or MPRIS) on it.
        val generation = playGeneration.incrementAndGet()
        val useFade = fadePauseResume && positionSeconds > 0.05
        scope.launch(Dispatchers.IO) {
            if (generation != playGeneration.get()) return@launch
            if (useFade) {
                NativeBridge.startEngineWithPauseResumeFadeNative()
            } else {
                NativeBridge.startEngineNative()
            }
        }
        isPlaying = true
        lastResumeRequestNs = System.nanoTime()
    }

    fun pauseFromHardwareKey() {
        if (System.nanoTime() - lastResumeRequestNs < STALE_PAUSE_ECHO_NS) return
        pause()
    }

    fun pause() {
        playGeneration.incrementAndGet()
        // The paused track keeps its SMB handle: cancelling here replays on
        // resume as buffered audio plus a fake natural-end restart at 0.
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
        playGeneration.incrementAndGet()
        NativeBridge.cancelActiveSmbAvioHandles()
        NativeBridge.stopEngineNative()
        NativeBridge.releaseCurrentDecoder()
        if (currentSource != null) {
            stoppedSource = currentSource
            stoppedSubtuneIndex = subtuneIndex.takeIf { subtuneCount > 1 }
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
        currentSourceId = null
        currentRequestUrl = null
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

    // Preferred mode resolved against live decoder caps (subtune repeat only
    // for multi-subtune tracks); mirrors Android refreshRepeatModeForTrack.
    fun refreshRepeatMode() {
        val resolved = resolveActiveRepeatMode(
            preferredRepeatMode,
            repeatModeCapabilitiesFlags,
            includeSubtuneRepeat = subtuneCount > 1
        )
        repeatMode = resolved
        NativeBridge.setRepeatMode(resolved.nativeValue)
    }

    // Cycle button: gated on live-repeat support like Android; the host
    // persists the returned mode as the new preferred repeat mode.
    fun cycleRepeatMode(): RepeatMode? {
        if (!supportsLiveRepeatMode(playbackCapabilitiesFlags)) return null
        val next = cycleRepeatModeValue(
            repeatMode,
            repeatModeCapabilitiesFlags,
            includeSubtuneRepeat = subtuneCount > 1
        ) ?: return null
        repeatMode = next
        preferredRepeatMode = next
        NativeBridge.setRepeatMode(next.nativeValue)
        return next
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

    // Subsong switches can re-probe the decoder; keep taps off the mutex.
    fun selectSubtuneAsync(index: Int) {
        scope.launch(Dispatchers.IO) {
            selectSubtune(index)
        }
    }

    private fun refreshMetadata() {
        val fileTitle = NativeBridge.getTrackTitle().trim()
        val currentArtist = NativeBridge.getTrackArtist().trim()
        val fileAlbum = NativeBridge.getTrackAlbum().trim()
        val fileLeaf = currentFile?.nameWithoutExtension ?: "Unknown"

        subtuneCount = NativeBridge.getSubtuneCount()
        subtuneIndex = NativeBridge.getCurrentSubtuneIndex().coerceIn(0, (subtuneCount - 1).coerceAtLeast(0))
        val subtuneTitle = if (subtuneCount > 1) NativeBridge.getSubtuneTitle(subtuneIndex) else ""
        val resolvedText = resolveSubtuneNowPlayingText(fileTitle, fileAlbum, subtuneCount, subtuneTitle)
        title = if (resolvedText.title.isNotBlank()) resolvedText.title else fileLeaf
        artist = if (currentArtist.isNotBlank()) currentArtist else "Unknown Artist"
        album = resolvedText.album
        val currentDecoderName = NativeBridge.getCurrentDecoderName()
        decoderName = currentDecoderName
        currentDecoderName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            lastUsedCoreName = it
        }
        sampleRateHz = NativeBridge.getTrackSampleRate()
        channelCount = NativeBridge.getTrackChannelCount()
        bitDepthLabel = resolveTrackBitDepthLabel()
        durationSeconds = NativeBridge.getDuration()
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
                // Probe once per file: the ticker reasks while unresolved.
                val flacFile = currentFile
                if (flacFile != null) {
                    if (flacBitDepthProbePath != flacFile.absolutePath) {
                        flacBitDepthProbePath = flacFile.absolutePath
                        flacBitDepthProbeResult =
                            DesktopArtworkSupport.extractFlacBitDepth(flacFile)?.let { "${it}-bit" }
                            ?: "Unknown"
                    }
                    flacBitDepthProbeResult
                } else {
                    "Unknown"
                }
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

                    // The engine can move between subtunes on its own (natural
                    // end, seek across a boundary); resync without it the
                    // session keeps showing the previous subtune.
                    if (subtuneCount > 1 &&
                        NativeBridge.getCurrentSubtuneIndex() != subtuneIndex
                    ) {
                        refreshMetadata()
                    }

                    if (bitDepthLabel.isBlank() || bitDepthLabel == "-bit" || bitDepthLabel == "Unknown") {
                        val resolved = resolveTrackBitDepthLabel()
                        if (resolved != "Unknown" && resolved != "-bit") {
                            bitDepthLabel = resolved
                        }
                    }

                    if (NativeBridge.consumeTransportErrorEvent()) {
                        println("SiliconPlayer: stream stalled, playback stopped")
                    }
                    if (NativeBridge.consumeNaturalEndEvent()) {
                        when (repeatMode) {
                            RepeatMode.Track, RepeatMode.Subtune -> {
                                seekTo(0.0)
                                play()
                            }
                            // LoopPoint is engine-handled; Playlist/None advance the queue.
                            RepeatMode.LoopPoint -> Unit
                            else -> {
                                val advanced = onAdvanceQueue?.invoke(repeatMode == RepeatMode.Playlist) ?: false
                                if (!advanced) {
                                    stop()
                                }
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
