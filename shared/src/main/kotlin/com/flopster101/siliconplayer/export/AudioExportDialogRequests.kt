package com.flopster101.siliconplayer.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.GmeDefaults

/**
 * Entry points outside the player (browser rows, home entries, playlists) hand a
 * source here; the platform host renders the shared export dialog for it.
 */
internal object AudioExportDialogRequests {
    var pendingSource: AudioExportDialogSource? by mutableStateOf(null)
        private set

    fun request(source: AudioExportDialogSource) {
        pendingSource = source
    }

    fun clear() {
        pendingSource = null
    }
}

internal fun audioExportSourceForTrack(
    path: String?,
    title: String,
    artist: String? = null,
    decoderName: String? = null,
    subtuneIndex: Int? = null,
    durationSeconds: Double? = null
): AudioExportDialogSource {
    val resolvedDuration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
    return AudioExportDialogSource(
        sourceId = path,
        filePath = path,
        title = title,
        artist = artist.orEmpty(),
        decoderName = decoderName,
        subtuneIndex = subtuneIndex ?: 0,
        subtuneCount = 1,
        durationSeconds = resolvedDuration ?: GmeDefaults.unknownDurationSeconds.toDouble(),
        durationReliable = resolvedDuration != null
    )
}
