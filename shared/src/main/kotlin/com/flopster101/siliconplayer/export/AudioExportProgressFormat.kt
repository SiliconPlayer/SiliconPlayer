package com.flopster101.siliconplayer.export

import java.util.Locale

internal fun audioExportEstimatedRemainingSeconds(progress: AudioExportProgress): Double? {
    if (progress.renderedFrames <= 0L || progress.totalFrames <= 0L) return null
    val elapsed = progress.elapsedSeconds.takeIf { it.isFinite() && it > 0.0 } ?: return null
    return elapsed * (progress.totalFrames - progress.renderedFrames).toDouble() / progress.renderedFrames
}

internal fun formatAudioExportByteRate(bytes: Long, elapsedSeconds: Double): String {
    if (bytes <= 0L || !elapsedSeconds.isFinite() || elapsedSeconds <= 0.0) return "—"
    val bytesPerSecond = bytes / elapsedSeconds
    return when {
        bytesPerSecond >= 1024.0 * 1024.0 ->
            String.format(Locale.ROOT, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024.0 ->
            String.format(Locale.ROOT, "%.1f KB/s", bytesPerSecond / 1024.0)
        else -> String.format(Locale.ROOT, "%.0f B/s", bytesPerSecond)
    }
}
