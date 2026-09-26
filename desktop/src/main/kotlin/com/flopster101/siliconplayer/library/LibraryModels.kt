package com.flopster101.siliconplayer.library

/**
 * Desktop counterparts of the indexed-library models. The desktop port has no
 * media-library database yet, so these only carry shape for the shared UI and
 * every collection is empty.
 */
data class LibraryTrackEntity(
    val path: String,
    val sourceId: String,
    val dedupKey: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val trackNo: Int,
    val discNo: Int,
    val durationMs: Long,
    val year: Int,
    val format: String,
    val sizeBytes: Long,
    val mtimeMs: Long,
    val addedAtMs: Long
)

data class LibraryAlbum(
    val name: String,
    val artist: String,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int,
    val artworkPath: String?,
    val rawName: String = name
)

data class LibraryArtist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val artworkPath: String?
)

data class LibraryCollections(
    val albums: List<LibraryAlbum>,
    val artists: List<LibraryArtist>,
    val trackCount: Int,
    val tracks: List<LibraryTrackEntity> = emptyList()
) {
    companion object {
        val Empty = LibraryCollections(
            albums = emptyList(),
            artists = emptyList(),
            trackCount = 0
        )
    }
}

data class LibraryAlbumDetail(
    val album: LibraryAlbum,
    val tracks: List<LibraryTrackEntity>
)

data class LibrarySearchResults(
    val query: String,
    val albums: List<LibraryAlbum>,
    val artists: List<LibraryArtist>,
    val tracks: List<LibraryTrackEntity>
) {
    val isEmpty: Boolean
        get() = albums.isEmpty() && artists.isEmpty() && tracks.isEmpty()
}

data class LibrarySyncState(
    val isScanning: Boolean = false,
    val scannedFiles: Int = 0,
    val indexedTracks: Int = 0,
    val currentPath: String? = null,
    val lastSyncedAtMs: Long = 0L
)

data class LibrarySourceStatus(
    val id: String,
    val enabled: Boolean,
    val lastSyncMs: Long,
    val trackCount: Long
)

object LibraryContract {
    const val SOURCE_MEDIASTORE = "mediastore"
    const val SOURCE_SCANNER = "scanner"

    const val UNKNOWN_ALBUM = "Unknown album"
    const val UNKNOWN_ARTIST = "Unknown artist"
    const val VARIOUS_ARTISTS = "Various artists"
}
