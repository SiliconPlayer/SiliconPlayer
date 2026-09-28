package com.flopster101.siliconplayer
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.data.resolveArchiveContainerParentLocation
import java.io.File
import java.util.Locale

internal fun playRecentFileEntryAction(
    cacheRoot: File,
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>,
    openPlayerOnTrackSelect: Boolean,
    onApplyTrackSelection: (File, Boolean, Boolean?, String?, String?, Boolean) -> Unit,
    onApplyManualInputSelection: (String) -> Unit,
    onOpenPlaylistFile: (File, String?) -> Unit
) {
    val playbackInput = resolveRecentPlaybackInput(entry, networkNodes)
    val normalized = normalizeSourceIdentity(playbackInput)
    val uri = normalized?.let { Uri.parse(it) }
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
    if (entry.isPlaylist && !normalized.isNullOrBlank() && (scheme == null || scheme == "file")) {
        val localPlaylist = when (scheme) {
            "file" -> uri?.path?.let(::File)
            else -> File(normalized)
        }?.takeIf { it.exists() && it.isFile && isSupportedPlaylistFile(it) }
        if (localPlaylist != null) {
            onOpenPlaylistFile(localPlaylist, normalized)
            return
        }
    }
    val isRemote = scheme == "http" || scheme == "https" || scheme == "smb"
    if (isRemote && !normalized.isNullOrBlank()) {
        val cached = findExistingCachedFileForSource(cacheRoot, normalized)
        if (cached != null) {
            onApplyTrackSelection(
                cached,
                true,
                openPlayerOnTrackSelect,
                normalized,
                null,
                false
            )
        } else {
            onApplyManualInputSelection(playbackInput)
        }
    } else {
        onApplyManualInputSelection(playbackInput)
    }
}

private fun resolveRecentPlaybackInput(
    entry: RecentPathEntry,
    networkNodes: List<NetworkNode>
): String {
    val rawPath = entry.path.trim()
    if (rawPath.isBlank()) return entry.path

    parseArchiveSourceId(rawPath)?.let { archiveSource ->
        parseSmbSourceSpecFromInput(archiveSource.archivePath)?.let { archiveSmbSpec ->
            val smbTarget = resolveSmbRecentOpenTarget(
                targetSpec = archiveSmbSpec,
                networkNodes = networkNodes,
                preferredSourceNodeId = entry.sourceNodeId
            )
            return buildArchiveSourceId(smbTarget.requestUri, archiveSource.entryPath)
        }
        parseHttpSourceSpecFromInput(archiveSource.archivePath)?.let { archiveHttpSpec ->
            val httpTarget = resolveHttpRecentOpenTarget(
                targetSpec = archiveHttpSpec,
                networkNodes = networkNodes,
                preferredSourceNodeId = entry.sourceNodeId
            )
            return buildArchiveSourceId(httpTarget.requestUri, archiveSource.entryPath)
        }
        return rawPath
    }

    parseSmbSourceSpecFromInput(rawPath)?.let { smbSpec ->
        return resolveSmbRecentOpenTarget(
            targetSpec = smbSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        ).requestUri
    }

    parseHttpSourceSpecFromInput(rawPath)?.let { httpSpec ->
        return resolveHttpRecentOpenTarget(
            targetSpec = httpSpec,
            networkNodes = networkNodes,
            preferredSourceNodeId = entry.sourceNodeId
        ).requestUri
    }

    return rawPath
}

internal fun applyRecentFolderAction(
    context: Context,
    prefs: android.content.SharedPreferences,
    entry: RecentPathEntry,
    action: FolderEntryAction,
    recentFolders: List<RecentPathEntry>,
    recentFoldersLimit: Int,
    networkNodes: List<NetworkNode>,
    onRecentFoldersChanged: (List<RecentPathEntry>) -> Unit,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit
) {
    when (action) {
        FolderEntryAction.OpenInBrowser -> {
            val target = resolveBrowserParentForRecentFolder(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "Unable to open folder in browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        FolderEntryAction.DeleteFromRecents -> {
            val updated = recentFolders.filterNot { samePath(it.path, entry.path) }
            onRecentFoldersChanged(updated)
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_FOLDERS,
                updated,
                recentFoldersLimit
            )
            Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
        }

        FolderEntryAction.CopyPath -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Path", entry.path))
            Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun applyRecentSourceAction(
    context: Context,
    prefs: android.content.SharedPreferences,
    entry: RecentPathEntry,
    action: SourceEntryAction,
    recentPlayedFiles: List<RecentPathEntry>,
    recentFilesLimit: Int,
    networkNodes: List<NetworkNode>,
    onRecentPlayedFilesChanged: (List<RecentPathEntry>) -> Unit,
    resolveShareableFileForRecent: (RecentPathEntry) -> File?,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit
) {
    when (action) {
        SourceEntryAction.OpenInBrowser -> {
            val target = resolveBrowserFolderForRecentSource(entry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "This source cannot be opened in file browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        SourceEntryAction.DeleteFromRecents -> {
            val updated = recentPlayedFiles.filterNot { samePath(it.path, entry.path) }
            onRecentPlayedFilesChanged(updated)
            writeRecentEntries(
                prefs,
                AppPreferenceKeys.RECENT_PLAYED_FILES,
                updated,
                recentFilesLimit
            )
            Toast.makeText(context, "Removed from recents", Toast.LENGTH_SHORT).show()
        }

        SourceEntryAction.ShareFile -> {
            val shareFile = resolveShareableFileForRecent(entry)
            if (shareFile == null) {
                Toast.makeText(
                    context,
                    "Share is only available for local or cached files",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = guessMimeTypeFromFilename(shareFile.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share file"))
            } catch (_: Throwable) {
                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }

        SourceEntryAction.CopySource -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("URL or path", entry.path))
            Toast.makeText(context, "Copied URL/path", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun applyPinnedFolderAction(
    context: Context,
    entry: HomePinnedEntry,
    action: FolderEntryAction,
    pinnedEntries: List<HomePinnedEntry>,
    onPinnedEntriesChanged: (List<HomePinnedEntry>) -> Unit,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit,
    networkNodes: List<NetworkNode>
) {
    val recentEntry = entry.asRecentPathEntry()
    when (action) {
        FolderEntryAction.OpenInBrowser -> {
            val target = resolveBrowserParentForRecentFolder(recentEntry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "Unable to open folder in browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        FolderEntryAction.DeleteFromRecents -> {
            val updated = pinnedEntries.filterNot { pinned -> samePath(pinned.path, entry.path) }
            onPinnedEntriesChanged(updated)
            val isPlaylist = entry.path.startsWith("playlist://")
            Toast.makeText(context, if (isPlaylist) "Playlist unpinned" else "Folder unpinned", Toast.LENGTH_SHORT).show()
        }

        FolderEntryAction.CopyPath -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Path", entry.path))
            Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT).show()
        }
    }
}

internal fun applyPinnedSourceAction(
    context: Context,
    entry: HomePinnedEntry,
    action: SourceEntryAction,
    pinnedEntries: List<HomePinnedEntry>,
    onPinnedEntriesChanged: (List<HomePinnedEntry>) -> Unit,
    resolveShareableFileForRecent: (HomePinnedEntry) -> File?,
    onOpenInBrowser: (locationId: String?, directoryPath: String, smbSourceNodeId: Long?, httpSourceNodeId: Long?) -> Unit,
    networkNodes: List<NetworkNode>
) {
    val recentEntry = entry.asRecentPathEntry()
    when (action) {
        SourceEntryAction.OpenInBrowser -> {
            val target = resolveBrowserFolderForRecentSource(recentEntry, networkNodes)
            if (target == null) {
                Toast.makeText(context, "This source cannot be opened in file browser", Toast.LENGTH_SHORT).show()
            } else {
                onOpenInBrowser(
                    target.locationId,
                    target.directoryPath,
                    target.smbSourceNodeId,
                    target.httpSourceNodeId
                )
            }
        }

        SourceEntryAction.DeleteFromRecents -> {
            val updated = pinnedEntries.filterNot { pinned -> samePath(pinned.path, entry.path) }
            onPinnedEntriesChanged(updated)
            Toast.makeText(context, "File unpinned", Toast.LENGTH_SHORT).show()
        }

        SourceEntryAction.ShareFile -> {
            val shareFile = resolveShareableFileForRecent(entry)
            if (shareFile == null) {
                Toast.makeText(
                    context,
                    "Share is only available for local or cached files",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = guessMimeTypeFromFilename(shareFile.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share file"))
            } catch (_: Throwable) {
                Toast.makeText(context, "Unable to share file", Toast.LENGTH_SHORT).show()
            }
        }

        SourceEntryAction.CopySource -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("URL or path", entry.path))
            Toast.makeText(context, "Copied URL/path", Toast.LENGTH_SHORT).show()
        }
    }
}

