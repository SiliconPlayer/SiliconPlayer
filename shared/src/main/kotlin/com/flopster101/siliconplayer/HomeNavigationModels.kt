package com.flopster101.siliconplayer

import androidx.compose.ui.graphics.vector.ImageVector

internal data class RecentPathEntry(
    val path: String,
    val locationId: String?,
    val title: String? = null,
    val artist: String? = null,
    val decoderName: String? = null,
    val sourceNodeId: Long? = null,
    val artworkThumbnailCacheKey: String? = null,
    val isPlaylist: Boolean = false,
    val playlistSourceHint: String? = null,
    val subtuneIndex: Int? = null
)

internal data class HomePinnedEntry(
    val path: String,
    val isFolder: Boolean,
    val locationId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val decoderName: String? = null,
    val sourceNodeId: Long? = null,
    val artworkThumbnailCacheKey: String? = null,
    val subtuneIndex: Int? = null,
    val pinnedAtEpochMs: Long = System.currentTimeMillis()
) {
    fun asRecentPathEntry(): RecentPathEntry {
        return RecentPathEntry(
            path = path,
            locationId = locationId,
            title = title,
            artist = artist,
            decoderName = decoderName,
            sourceNodeId = sourceNodeId,
            artworkThumbnailCacheKey = artworkThumbnailCacheKey,
            subtuneIndex = subtuneIndex
        )
    }
}

internal data class HomePinInsertPreview(
    val requiresConfirmation: Boolean,
    val evictionCandidate: HomePinnedEntry?
)

internal data class StorageDescriptor(
    val rootPath: String,
    val label: String,
    val icon: ImageVector
)

internal data class StoragePresentation(
    val label: String,
    val icon: ImageVector,
    val qualifier: String? = null
)

internal enum class SourceEntryAction {
    DeleteFromRecents,
    ShareFile,
    CopySource,
    OpenInBrowser
}

internal enum class FolderEntryAction {
    DeleteFromRecents,
    CopyPath,
    OpenInBrowser
}

