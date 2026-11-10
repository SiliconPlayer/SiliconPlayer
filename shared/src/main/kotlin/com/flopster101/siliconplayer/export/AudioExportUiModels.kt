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
    M4a("M4A", "m4a"),
    Adts("ADTS", "aac"),
    Amr("AMR", "amr"),
    WebM("WebM", "webm"),
    ThreeGpp("3GP", "3gp"),
    Mp3("MP3", "mp3"),
    Ogg("Ogg", "ogg"),
    Flac("FLAC", "flac"),
    Wav("WAV", "wav")
}

internal enum class AudioExportCodec(val label: String) {
    Aac("AAC"),
    AmrNb("AMR-NB"),
    Flac("FLAC"),
    Mp3("MP3"),
    Opus("Opus"),
    Pcm24("PCM 24-bit")
}

internal enum class AudioExportLoopType(val label: String) {
    Repeat("Repeat"),
    Duration("Duration")
}

internal enum class AudioExportRepeatMode(val label: String, val storageValue: String) {
    Track("Track", "track"),
    LoopPoint("Loop point", "loop_point")
}

enum class AudioExportFadePlacement(val label: String, val storageValue: String) {
    Tail("Song tail", "tail"),
    NextLoop("Next loop head", "next_loop")
}

internal data class AudioExportDialogSource(
    val sourceId: String?,
    val filePath: String?,
    val title: String,
    val artist: String,
    val decoderName: String?,
    val subtuneIndex: Int,
    val subtuneCount: Int,
    val durationSeconds: Double,
    val durationReliable: Boolean = true
)

internal data class AudioExportUiRequest(
    val source: AudioExportDialogSource,
    val encoder: AudioExportEncoder,
    val container: AudioExportContainer,
    val codec: AudioExportCodec,
    val bitrateBps: Int?,
    val ffmpegMode: AudioExportFfmpegMode,
    val ffmpegArguments: String,
    val externalBinaryLocation: String?,
    val destinationLocation: String,
    val includeMetadata: Boolean,
    val loopType: AudioExportLoopType,
    val repeatMode: AudioExportRepeatMode,
    val repeatCount: Int,
    val loopDurationSeconds: Double?,
    val renderOptions: AudioExportRenderOptions
)

internal data class AudioExportExecutionState(
    val running: Boolean = false,
    val status: String = "",
    val progress: AudioExportProgress? = null,
    val completion: AudioExportCompletion? = null
)

internal interface AudioExportPlatformAdapter {
    val executionState: AudioExportExecutionState
    val encoders: List<AudioExportEncoder>
    val supportedMediaCodecs: Set<AudioExportCodec>
        get() = emptySet()
    val mediaCodecSupportsWebM: Boolean
        get() = false
    val completionActions: Set<AudioExportCompletionAction>
        get() = emptySet()
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
    fun shareCompletion(completion: AudioExportCompletion) = Unit
    fun openCompletionFolder(completion: AudioExportCompletion) = Unit
    fun playCompletion(completion: AudioExportCompletion) = Unit
    fun clearCompletion() = Unit
}

internal val LocalAudioExportPlatformAdapter = staticCompositionLocalOf<AudioExportPlatformAdapter?> { null }

internal fun AudioExportContainer.impliedCodec(): AudioExportCodec = when (this) {
    AudioExportContainer.M4a, AudioExportContainer.Adts -> AudioExportCodec.Aac
    AudioExportContainer.Amr -> AudioExportCodec.AmrNb
    AudioExportContainer.WebM -> AudioExportCodec.Opus
    AudioExportContainer.ThreeGpp -> AudioExportCodec.AmrNb
    AudioExportContainer.Mp3 -> AudioExportCodec.Mp3
    AudioExportContainer.Ogg -> AudioExportCodec.Opus
    AudioExportContainer.Flac -> AudioExportCodec.Flac
    AudioExportContainer.Wav -> AudioExportCodec.Pcm24
}

internal fun AudioExportCodec.impliedContainer(): AudioExportContainer = when (this) {
    AudioExportCodec.Aac -> AudioExportContainer.M4a
    AudioExportCodec.AmrNb -> AudioExportContainer.Amr
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
    bitrateBps: Int?
): String {
    if (container == AudioExportContainer.Flac || codec == AudioExportCodec.Flac) {
        return "Lossless · size varies"
    }
    if (durationSeconds <= 0.0 || !durationSeconds.isFinite()) return "Size depends on duration"
    val bytes = if (container == AudioExportContainer.Wav || codec == AudioExportCodec.Pcm24) {
        durationSeconds * sampleRateHz * 6.0
    } else {
        val bitrate = bitrateBps ?: return "Size depends on quality"
        durationSeconds * bitrate / 8.0
    }
    val channels = if (codec == AudioExportCodec.AmrNb) "mono" else "stereo"
    return "≈ ${formatByteCount(bytes.roundToLong())} · $channels"
}

internal fun audioExportSubtuneLabel(subtuneIndex: Int, subtuneCount: Int): String? {
    if (subtuneCount <= 1) return null
    return "Subtune ${subtuneIndex + 1} of $subtuneCount"
}

internal fun supportedAudioExportContainers(
    codec: AudioExportCodec,
    encoder: AudioExportEncoder
): List<AudioExportContainer> {
    if (encoder == AudioExportEncoder.MediaCodec) {
        return when (codec) {
            AudioExportCodec.Aac -> listOf(
                AudioExportContainer.M4a,
                AudioExportContainer.ThreeGpp,
                AudioExportContainer.Adts
            )
            AudioExportCodec.AmrNb -> listOf(
                AudioExportContainer.Amr,
                AudioExportContainer.ThreeGpp,
                AudioExportContainer.M4a
            )
            AudioExportCodec.Opus -> listOf(
                AudioExportContainer.Ogg,
                AudioExportContainer.WebM,
                AudioExportContainer.M4a
            )
            AudioExportCodec.Flac -> listOf(AudioExportContainer.Flac, AudioExportContainer.M4a)
            AudioExportCodec.Mp3, AudioExportCodec.Pcm24 -> emptyList()
        }
    }
    return when (codec) {
        AudioExportCodec.Aac -> listOf(
            AudioExportContainer.M4a,
            AudioExportContainer.Adts,
            AudioExportContainer.ThreeGpp
        )
        AudioExportCodec.AmrNb -> listOf(
            AudioExportContainer.Amr,
            AudioExportContainer.ThreeGpp,
            AudioExportContainer.M4a
        )
        AudioExportCodec.Flac -> listOf(
            AudioExportContainer.Flac,
            AudioExportContainer.Ogg,
            AudioExportContainer.M4a
        )
        AudioExportCodec.Mp3 -> listOf(AudioExportContainer.Mp3, AudioExportContainer.M4a)
        AudioExportCodec.Opus -> listOf(
            AudioExportContainer.Ogg,
            AudioExportContainer.WebM,
            AudioExportContainer.M4a
        )
        AudioExportCodec.Pcm24 -> listOf(AudioExportContainer.Wav)
    }
}

internal fun AudioExportCodec.mediaCodecContainer(): AudioExportContainer =
    supportedAudioExportContainers(this, AudioExportEncoder.MediaCodec).first()

internal fun audioExportBitrates(codec: AudioExportCodec): List<Int> = when (codec) {
    AudioExportCodec.Aac, AudioExportCodec.Opus ->
        listOf(64_000, 96_000, 128_000, 192_000, 256_000, 320_000, 384_000, 510_000)
    AudioExportCodec.AmrNb -> listOf(4_750, 5_150, 5_900, 6_700, 7_400, 7_950, 10_200, 12_200)
    AudioExportCodec.Mp3 -> listOf(64_000, 96_000, 128_000, 192_000, 256_000, 320_000)
    AudioExportCodec.Flac, AudioExportCodec.Pcm24 -> emptyList()
}

internal fun defaultAudioExportBitrate(codec: AudioExportCodec): Int? = when (codec) {
    AudioExportCodec.AmrNb -> 12_200
    AudioExportCodec.Flac, AudioExportCodec.Pcm24 -> null
    else -> 256_000
}

internal fun formatAudioExportBitrate(bitrateBps: Int): String {
    val kbps = bitrateBps / 1000.0
    val value = String.format(java.util.Locale.ROOT, "%.2f", kbps).trimEnd('0').trimEnd('.')
    return "$value kbps"
}
