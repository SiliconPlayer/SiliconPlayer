package com.flopster101.siliconplayer

import java.util.UUID

internal enum class PlaylistStoredFormat(
    val storageValue: String,
    val label: String
) {
    Internal("internal", "Internal"),
    M3u("m3u", "M3U"),
    M3u8("m3u8", "M3U8");

    companion object {
        fun fromStorage(value: String?): PlaylistStoredFormat {
            return entries.firstOrNull { it.storageValue == value } ?: Internal
        }
    }
}

internal enum class PlaylistEntrySortMode(
    val storageValue: String,
    val label: String
) {
    Custom("custom", "Custom"),
    Title("title", "Title"),
    Artist("artist", "Artist"),
    Album("album", "Album"),
    RecentlyAdded("recently_added", "Recently added");

    companion object {
        fun fromStorage(value: String?): PlaylistEntrySortMode {
            return entries.firstOrNull { it.storageValue == value || it.name.equals(value, ignoreCase = true) }
                ?: Custom
        }
    }
}

internal data class PlaylistTrackEntry(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val requestUrlHint: String? = null,
    val title: String,
    val customTitle: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artworkThumbnailCacheKey: String? = null,
    val subtuneIndex: Int? = null,
    val durationSecondsOverride: Double? = null,
    val addedAtMs: Long = System.currentTimeMillis()
)

internal val PlaylistTrackEntry.effectiveTitle: String
    get() = customTitle?.trim()?.takeIf { it.isNotEmpty() } ?: title

internal data class StoredPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val format: PlaylistStoredFormat = PlaylistStoredFormat.Internal,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry> = emptyList(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val folderId: String? = null,
    val customArtworkUri: String? = null,
    val iconTintArgb: Long? = null,
    val autoGenerateCover: Boolean = true
)
