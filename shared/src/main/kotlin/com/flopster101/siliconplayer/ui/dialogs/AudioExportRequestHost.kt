package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.export.AudioExportDialogRequests
import com.flopster101.siliconplayer.export.LocalAudioExportPlatformAdapter

@Composable
internal fun AudioExportRequestHost() {
    val platform = LocalAudioExportPlatformAdapter.current ?: return
    val source = AudioExportDialogRequests.pendingSource ?: return
    AudioExportDialog(
        source = source,
        platform = platform,
        onDismiss = { AudioExportDialogRequests.clear() }
    )
}
