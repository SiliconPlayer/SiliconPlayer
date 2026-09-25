package com.flopster101.siliconplayer

import java.io.File
import java.util.Locale

internal enum class RemoteLoadPhase {
    Connecting,
    Downloading,
    Opening
}

internal data class RemoteLoadUiState(
    val sourceId: String,
    val phase: RemoteLoadPhase,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val percent: Int? = null,
    val indeterminate: Boolean = true
)

internal data class RemoteDownloadResult(
    val file: File?,
    val errorMessage: String? = null,
    val cancelled: Boolean = false
)

internal data class SubtuneEntry(
    val index: Int,
    val title: String,
    val artist: String,
    val durationSeconds: Double
)

internal fun formatByteCount(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

internal fun formatShortDuration(seconds: Double): String {
    if (seconds <= 0.0 || !seconds.isFinite()) return "--:--"
    val totalSeconds = seconds.toInt().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds)
}
