package com.flopster101.siliconplayer.export

import com.flopster101.siliconplayer.EndFadeCurve

enum class AudioExportSampleFormat(
    val storageValue: String,
    val bytesPerSample: Int
) {
    Pcm16("pcm16", 2),
    Pcm24("pcm24", 3),
    Float32("float32", 4);

    companion object {
        fun fromStorage(value: String?): AudioExportSampleFormat? {
            return entries.firstOrNull { it.storageValue == value }
        }
    }
}

data class AudioExportRenderOptions(
    val sampleRateHz: Int = 48_000,
    val sampleFormat: AudioExportSampleFormat = AudioExportSampleFormat.Pcm24,
    val durationCapSeconds: Double? = null,
    val unknownDurationSeconds: Int? = null,
    val endFadeDurationMs: Int? = null,
    val endFadeCurve: EndFadeCurve? = null,
    val endFadeApplyToAllTracks: Boolean? = null,
    val endFadePlacement: AudioExportFadePlacement = AudioExportFadePlacement.Tail,
    val useActiveGain: Boolean = true,
    val useActiveEffects: Boolean = true
) {
    init {
        require(sampleRateHz in 8_000..192_000) { "Sample rate must be between 8000 and 192000 Hz." }
        require(durationCapSeconds == null || (durationCapSeconds.isFinite() && durationCapSeconds > 0.0)) {
            "Duration cap must be a positive number of seconds."
        }
        require(unknownDurationSeconds == null || unknownDurationSeconds in 1..86_400) {
            "Unknown duration must be between 1 and 86400 seconds."
        }
        require(endFadeDurationMs == null || endFadeDurationMs == 0 || endFadeDurationMs in 100..120_000) {
            "Fade duration must be 0 or between 100 and 120000 ms."
        }
    }
}

data class AudioExportMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val composer: String? = null,
    val genre: String? = null,
    val date: String? = null,
    val copyright: String? = null,
    val comment: String? = null
)

data class AudioExportProgress(
    val renderedFrames: Long,
    val totalFrames: Long,
    val sampleRateHz: Int = 48_000
) {
    val renderedSeconds: Double
        get() = renderedFrames.toDouble() / sampleRateHz.toDouble()

    val totalSeconds: Double
        get() = totalFrames.toDouble() / sampleRateHz.toDouble()

    val fraction: Double
        get() = if (totalFrames > 0L) {
            (renderedFrames.toDouble() / totalFrames.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
}
