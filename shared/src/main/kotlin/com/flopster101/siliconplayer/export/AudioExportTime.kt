package com.flopster101.siliconplayer.export

internal fun parseAudioExportTime(raw: String): Double? {
    val parts = raw.trim().split(':')
    if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() }) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    val minutes = parts.getOrNull(parts.size - 2)?.toLongOrNull() ?: 0L
    val hours = parts.getOrNull(parts.size - 3)?.toLongOrNull() ?: 0L
    if (seconds < 0.0 || seconds >= 60.0 && parts.size > 1 || minutes < 0L || hours < 0L) return null
    if (parts.size > 1 && minutes >= 60L) return null
    val total = hours.toDouble() * 3_600.0 + minutes.toDouble() * 60.0 + seconds
    return total.takeIf { it.isFinite() }
}
