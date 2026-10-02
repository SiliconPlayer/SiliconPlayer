package com.flopster101.siliconplayer
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource
import com.flopster101.siliconplayer.session.exportCachedFilesToTree
import kotlinx.coroutines.launch

import android.content.Context
import java.io.File

internal suspend fun prepareRemoteExportFile(
    context: Context,
    request: RemoteExportRequest,
    onStatus: suspend (RemoteLoadUiState) -> Unit = {}
): Result<ExportFileItem> {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val existing = findExistingCachedFileForSource(cacheRoot, request.sourceId)
    if (existing != null) {
        existing.setLastModified(System.currentTimeMillis())
        return Result.success(
            ExportFileItem(
                sourceFile = existing,
                displayNameOverride = sanitizeRemoteLeafName(request.preferredFileName)
                    ?: stripRemoteCacheHashPrefix(existing.name)
            )
        )
    }

    val downloaded = when (request) {
        is HttpRemoteExportRequest -> {
            downloadRemoteUrlToCache(
                context = context,
                url = request.sourceId,
                requestUrl = request.requestUrl,
                onStatus = onStatus
            )
        }

        is SmbRemoteExportRequest -> {
            downloadSmbSourceToCache(
                context = context,
                sourceId = request.sourceId,
                spec = request.smbSpec,
                onStatus = onStatus
            )
        }
    }

    val downloadedFile = downloaded.file
        ?: return Result.failure(
            if (downloaded.cancelled) {
                RemoteExportCancelledException(downloaded.errorMessage ?: "Cancelled")
            } else {
                IllegalStateException(downloaded.errorMessage ?: "Failed to cache remote file")
            }
        )
    downloadedFile.setLastModified(System.currentTimeMillis())
    return Result.success(
        ExportFileItem(
            sourceFile = downloadedFile,
            displayNameOverride = sanitizeRemoteLeafName(request.preferredFileName)
                ?: stripRemoteCacheHashPrefix(downloadedFile.name)
        )
    )
}

@androidx.compose.runtime.Composable
internal fun rememberCacheExportDirectoryLauncher(
    context: Context,
    appScope: kotlinx.coroutines.CoroutineScope,
    pendingPathsProvider: () -> List<String>,
    onClearPendingPaths: () -> Unit
): androidx.activity.result.ActivityResultLauncher<android.net.Uri?> {
    return androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        val selectedPaths = pendingPathsProvider()
        onClearPendingPaths()
        if (treeUri == null || selectedPaths.isEmpty()) {
            if (selectedPaths.isNotEmpty()) {
                android.widget.Toast.makeText(context, "Export canceled", android.widget.Toast.LENGTH_SHORT).show()
            }
            return@rememberLauncherForActivityResult
        }
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = exportCachedFilesToTree(
                context = context,
                treeUri = treeUri,
                selectedPaths = selectedPaths
            )
            if (result.invalidDestination) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                    android.widget.Toast.makeText(context, "Export failed: invalid destination", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                android.widget.Toast.makeText(
                    context,
                    "Exported ${result.exportedCount} file(s)" +
                        if (result.failedCount > 0) " (${result.failedCount} failed)" else "",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
