package com.flopster101.siliconplayer.export

import androidx.compose.runtime.staticCompositionLocalOf
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.formatByteCount
import kotlin.math.roundToLong

internal enum class AudioExportEncoder(val label: String) {
    MediaCodec("MediaCodec"),
    SystemFfmpeg("System FFmpeg"),
    ExternalFfmpeg("External FFmpeg")
}

internal enum class AudioExportFfmpegMode(val label: String) {
    Easy("Easy"),
    Advanced("Advanced")
}

internal enum class AudioExportContainer(val label: String, val extension: String) {
    M4a("AAC · M4A", "m4a"),
    Mp3("MP3", "mp3"),
    Ogg("Opus · Ogg", "ogg"),
    Flac("FLAC", "flac"),
    Wav("WAV", "wav")
}

internal enum class AudioExportCodec(val label: String) {
    Aac("AAC"),
    Flac("FLAC"),
    Mp3("MP3"),
    Opus("Opus"),
    Pcm24("PCM 24-bit")
}

internal enum class AudioExportRepeatChoice(val label: String) {
    None("None"),
    TrackTwice("Track ×2"),
    LoopPointTwice("Loop points ×2")
}

internal data class AudioExportDialogSource(
    val sourceId: String?,
    val filePath: String?,
    val title: String,
    val artist: String,
    val decoderName: String?,
    val subtuneIndex: Int,
    val subtuneCount: Int,
    val durationSeconds: Double
)

internal data class AudioExportUiRequest(
    val source: AudioExportDialogSource,
    val encoder: AudioExportEncoder,
    val container: AudioExportContainer,
    val codec: AudioExportCodec,
    val bitrateKbps: Int?,
    val ffmpegMode: AudioExportFfmpegMode,
    val ffmpegArguments: String,
    val externalBinaryLocation: String?,
    val destinationLocation: String,
    val includeMetadata: Boolean,
    val repeatChoice: AudioExportRepeatChoice,
    val renderOptions: AudioExportRenderOptions
)

internal data class AudioExportExecutionState(
    val running: Boolean = false,
    val status: String = "",
    val progress: AudioExportProgress? = null
)

internal interface AudioExportPlatformAdapter {
    val executionState: AudioExportExecutionState
    val encoders: List<AudioExportEncoder>
    val externalBinaryLocation: String?
    val externalBinaryLabel: String?
    val externalBinaryActionLabel: String
    val destinationLocation: String?
    val destinationLabel: String?
    val destinationDescription: String?
    fun chooseExternalBinary()
    fun chooseDestination(suggestedFileName: String)
    fun resetDestination(suggestedFileName: String)
    fun startExport(request: AudioExportUiRequest)
    fun cancelExport()
}

internal val LocalAudioExportPlatformAdapter = staticCompositionLocalOf<AudioExportPlatformAdapter?> { null }

internal fun AudioExportContainer.impliedCodec(): AudioExportCodec = when (this) {
    AudioExportContainer.M4a -> AudioExportCodec.Aac
    AudioExportContainer.Mp3 -> AudioExportCodec.Mp3
    AudioExportContainer.Ogg -> AudioExportCodec.Opus
    AudioExportContainer.Flac -> AudioExportCodec.Flac
    AudioExportContainer.Wav -> AudioExportCodec.Pcm24
}

internal fun AudioExportCodec.impliedContainer(): AudioExportContainer = when (this) {
    AudioExportCodec.Aac -> AudioExportContainer.M4a
    AudioExportCodec.Flac -> AudioExportContainer.Flac
    AudioExportCodec.Mp3 -> AudioExportContainer.Mp3
    AudioExportCodec.Opus -> AudioExportContainer.Ogg
    AudioExportCodec.Pcm24 -> AudioExportContainer.Wav
}

internal fun suggestedAudioExportFileName(title: String, container: AudioExportContainer): String {
    val base = title.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .ifBlank { "audio-export" }
    return "$base.${container.extension}"
}

internal fun formatAudioExportSizeEstimate(
    durationSeconds: Double,
    container: AudioExportContainer,
    codec: AudioExportCodec,
    sampleRateHz: Int,
    bitrateKbps: Int?
): String {
    if (container == AudioExportContainer.Flac || codec == AudioExportCodec.Flac) {
        return "Lossless · size varies"
    }
    if (durationSeconds <= 0.0 || !durationSeconds.isFinite()) return "Size depends on duration"
    val bytes = if (container == AudioExportContainer.Wav || codec == AudioExportCodec.Pcm24) {
        durationSeconds * sampleRateHz * 6.0
    } else {
        val bitrate = bitrateKbps ?: return "Size depends on quality"
        durationSeconds * bitrate * 125.0
    }
    return "≈ ${formatByteCount(bytes.roundToLong())} · stereo"
}

internal fun audioExportSubtuneLabel(subtuneIndex: Int, subtuneCount: Int): String? {
    if (subtuneCount <= 1) return null
    return "Subtune ${subtuneIndex + 1} of $subtuneCount"
}
