package com.flopster101.siliconplayer.platform

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.CacheExportResult
import com.flopster101.siliconplayer.ExportConflictDecision
import com.flopster101.siliconplayer.ExportFileItem
import com.flopster101.siliconplayer.ExportNameConflict
import com.flopster101.siliconplayer.RemoteExportRequest
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.session.exportFilesToTree
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Composable
internal fun rememberAndroidRemoteSourceExportSupport(): RemoteSourceExportSupport {
    val context = LocalContext.current
    var pendingDestination by remember { mutableStateOf<CancellableContinuation<Uri?>?>(null) }
    val directoryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val continuation = pendingDestination
        pendingDestination = null
        if (continuation != null && continuation.isActive) {
            continuation.resume(treeUri)
        }
    }
    return remember(context, directoryLauncher) {
        object : RemoteSourceExportSupport {
            override suspend fun prepareRemoteExportFile(
                request: RemoteExportRequest,
                onStatus: suspend (RemoteLoadUiState) -> Unit
            ): Result<ExportFileItem> {
                return com.flopster101.siliconplayer.prepareRemoteExportFile(
                    context = context,
                    request = request,
                    onStatus = onStatus
                )
            }

            override suspend fun exportFiles(
                exportItems: List<ExportFileItem>,
                onNameConflict: (suspend (ExportNameConflict) -> ExportConflictDecision)?
            ): CacheExportResult {
                if (exportItems.isEmpty()) {
                    return CacheExportResult(exportedCount = 0, failedCount = 0, cancelled = true)
                }
                val treeUri = withContext(Dispatchers.Main.immediate) {
                    suspendCancellableCoroutine<Uri?> { continuation ->
                        pendingDestination = continuation
                        continuation.invokeOnCancellation { pendingDestination = null }
                        directoryLauncher.launch(null)
                    }
                } ?: return CacheExportResult(exportedCount = 0, failedCount = 0, cancelled = true)
                return exportFilesToTree(
                    context = context,
                    treeUri = treeUri,
                    exportItems = exportItems,
                    onNameConflict = onNameConflict
                )
            }
        }
    }
}
