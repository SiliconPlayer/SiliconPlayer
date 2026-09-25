package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.availableRepeatModesForFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

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

    var canSeek by mutableStateOf(true)
        private set

    var hasReliableDuration by mutableStateOf(true)
        private set

    private var tickerJob: Job? = null
    private var isUserSeeking = false

    init {
        startTicker()
    }

    fun loadFile(file: File): Boolean {
        if (!file.exists() || !file.isFile) return false

        NativeBridge.stopEngineNative()
        NativeBridge.loadAudio(file.absolutePath)

        currentFile = file
        refreshMetadata()

        NativeBridge.startEngineNative()
        isPlaying = NativeBridge.isEnginePlaying()
        return true
    }

    fun play() {
        if (currentFile == null) return
        NativeBridge.startEngineWithPauseResumeFadeNative()
        isPlaying = true
    }

    fun pause() {
        NativeBridge.stopEngineWithPauseResumeFadeNative()
        isPlaying = false
    }

    fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun stop() {
        NativeBridge.stopEngineNative()
        isPlaying = false
        positionSeconds = 0.0
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
        bitDepthLabel = NativeBridge.getTrackBitDepthLabel()
        durationSeconds = NativeBridge.getDuration()
        subtuneIndex = NativeBridge.getCurrentSubtuneIndex()
        subtuneCount = NativeBridge.getSubtuneCount()
        canSeek = true
        hasReliableDuration = durationSeconds > 0.0
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
        NativeBridge.releaseCurrentDecoder()
    }
}
