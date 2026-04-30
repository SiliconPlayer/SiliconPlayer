package com.flopster101.siliconplayer

private val supportedPlaylistExtensions = setOf("m3u", "m3u8")

expect fun generatePlaylistId(): String

expect fun currentPlaylistTimeMillis(): Long

enum class PlaylistStoredFormat(
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

data class PlaylistTrackEntry(
    val id: String = generatePlaylistId(),
    val source: String,
    val requestUrlHint: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkThumbnailCacheKey: String? = null,
    val subtuneIndex: Int? = null,
    val durationSecondsOverride: Double? = null,
    val addedAtMs: Long = currentPlaylistTimeMillis()
)

data class StoredPlaylist(
    val id: String = generatePlaylistId(),
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>,
    val updatedAtMs: Long = currentPlaylistTimeMillis()
)

data class PlaylistLibraryState(
    val favorites: List<PlaylistTrackEntry>,
    val playlists: List<StoredPlaylist>
)

data class ParsedPlaylistDocument(
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>
)

fun emptyPlaylistLibraryState(): PlaylistLibraryState {
    return PlaylistLibraryState(
        favorites = emptyList(),
        playlists = emptyList()
    )
}

fun isSupportedPlaylistFileName(name: String): Boolean {
    return inferredPrimaryExtensionForName(name)?.lowercase() in supportedPlaylistExtensions
}

fun buildInternalPlaylistCopy(
    title: String,
    entries: List<PlaylistTrackEntry>
): StoredPlaylist {
    val normalizedTitle = title.trim().ifBlank { "Playlist" }
    return StoredPlaylist(
        title = normalizedTitle,
        format = PlaylistStoredFormat.Internal,
        entries = entries
    )
}

fun buildImportedPlaylist(
    document: ParsedPlaylistDocument
): StoredPlaylist {
    return StoredPlaylist(
        title = document.title,
        format = document.format,
        sourceIdHint = document.sourceIdHint,
        entries = document.entries
    )
}
