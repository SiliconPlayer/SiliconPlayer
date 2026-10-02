package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.screens.BrowserArchiveCapability
import com.flopster101.siliconplayer.ui.screens.browserArchiveCapabilityForName
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private var activeSmbRecentFolderContextJob: Job? = null

internal fun buildSmbFolderPlayableSourceIds(
    entries: List<SmbBrowserEntry>,
    rootSpec: SmbSourceSpec,
    parentPath: String?,
    supportedExtensions: Set<String> = NativeBridge.getSupportedExtensions()
        .asSequence()
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet(),
    showHiddenFilesAndFolders: Boolean = false
): List<String> {
    val share = rootSpec.share.trim()
    if (share.isBlank()) return emptyList()
    return entries
        .asSequence()
        .filter { browserEntry ->
            !browserEntry.isDirectory &&
                (showHiddenFilesAndFolders || (!browserEntry.isHidden && !browserEntry.name.startsWith("."))) &&
                browserArchiveCapabilityForName(browserEntry.name) == BrowserArchiveCapability.None &&
                fileMatchesSupportedExtensions(File(browserEntry.name), supportedExtensions)
        }
        .map { browserEntry ->
            val targetPath = joinSmbRelativePath(parentPath.orEmpty(), browserEntry.name)
            val targetSpec = buildSmbEntrySourceSpec(
                rootSpec.copy(share = share),
                targetPath
            )
            buildSmbRequestUri(targetSpec)
        }
        .toList()
}

internal fun loadSmbRecentFolderContext(
    scope: CoroutineScope,
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode> = emptyList(),
    showHiddenFilesAndFolders: Boolean = false,
    onPlayableSourceIdsLoaded: (List<String>) -> Unit = { playableIds ->
        RemotePlayableSourceIdsHolder.current = playableIds
    }
): Job? {
    val rawPath = entry.path.trim()
    val smbSpec = parseSmbSourceSpecFromInput(rawPath) ?: return null
    val targetResolution = resolveSmbRecentOpenTarget(
        targetSpec = smbSpec,
        networkNodes = networkNodes,
        preferredSourceNodeId = entry.sourceNodeId
    )
    val resolvedSpec = parseSmbSourceSpecFromInput(targetResolution.requestUri) ?: smbSpec
    val share = resolvedSpec.share.trim()
    if (share.isBlank()) return null
    val normalizedPath = normalizeSmbPathForShare(resolvedSpec.path).orEmpty()
    val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim().ifBlank { null }
    val initialTargetUri = targetResolution.requestUri

    val currentQueue = RemotePlayableSourceIdsHolder.current
    if (!currentQueue.any { samePath(it, initialTargetUri) }) {
        RemotePlayableSourceIdsHolder.current = listOf(initialTargetUri)
    }

    activeSmbRecentFolderContextJob?.cancel()
    val job = scope.launch {
        val playableSourceIds = withContext(Dispatchers.IO) {
            val result = listSmbDirectoryEntries(resolvedSpec, parentPath)
            val entries = result.getOrNull() ?: return@withContext emptyList()
            buildSmbFolderPlayableSourceIds(
                entries = entries,
                rootSpec = resolvedSpec,
                parentPath = parentPath,
                showHiddenFilesAndFolders = showHiddenFilesAndFolders
            )
        }

        if (isActive && playableSourceIds.isNotEmpty()) {
            val activeQueue = RemotePlayableSourceIdsHolder.current
            if (activeQueue.any { samePath(it, initialTargetUri) }) {
                onPlayableSourceIdsLoaded(playableSourceIds)
            }
        }
    }
    activeSmbRecentFolderContextJob = job
    return job
}
