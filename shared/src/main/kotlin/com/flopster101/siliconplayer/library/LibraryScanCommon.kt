package com.flopster101.siliconplayer.library

import java.io.File
import java.util.Locale

data class LibraryScanRoot(
    val path: String,
    val enabled: Boolean = true
)

// Conventional sampled formats. The indexed library is the normie surface;
// exotic module formats stay playable through the browser, not the index.
val SCANNER_CONVENTIONAL_EXTENSIONS = setOf(
    "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac",
    "wma", "mka", "ape", "wv", "aiff", "aif", "alac"
)

// Album header for a title-bucketed track list; null when empty. Shared so
// both platforms label compilations identically.
internal fun libraryAlbumDetailForTracks(
    albumName: String,
    tracks: List<LibraryTrackEntity>
): LibraryAlbumDetail? {
    if (tracks.isEmpty()) return null
    // Buckets group by title only, so one can span artists (the unknown
    // album always does); show the shared artist or a compilation label.
    val artistKeys = tracks.map { it.albumArtist.ifBlank { it.artist } }
        .filter { it.isNotBlank() }
        .distinct()
    return LibraryAlbumDetail(
        album = LibraryAlbum(
            name = albumName.ifBlank { LibraryContract.UNKNOWN_ALBUM },
            artist = when (artistKeys.size) {
                0 -> LibraryContract.UNKNOWN_ARTIST
                1 -> artistKeys[0]
                else -> LibraryContract.VARIOUS_ARTISTS
            },
            trackCount = tracks.size,
            durationMs = tracks.sumOf { it.durationMs },
            year = tracks.maxOf { it.year },
            artworkPath = tracks.minOfOrNull { it.path }
        ),
        tracks = tracks
    )
}

/** Directory traversal; desktop passes includeHidden=false to skip dot-dirs. */
internal fun interface LibraryLister {
    fun listFiles(root: LibraryScanRoot, includeHidden: Boolean): Sequence<File>
}

/** Display-time identity folding `.`/`..`, storage aliases and case. */
internal fun libraryDedupKeyForPath(path: String): String {
    val absolute = path.startsWith("/")
    val segments = ArrayDeque<String>()
    path.split('/').forEach { segment ->
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> if (segments.isNotEmpty()) segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    var normalized = (if (absolute) "/" else "") + segments.joinToString("/")
    for ((prefix, replacement) in DEDUP_PATH_ALIASES) {
        if (normalized.startsWith(prefix)) {
            normalized = replacement + normalized.removePrefix(prefix)
            break
        }
    }
    return normalized.lowercase(Locale.ROOT)
}

private val DEDUP_PATH_ALIASES = listOf(
    "/sdcard/" to "/storage/emulated/0/",
    "/mnt/sdcard/" to "/storage/emulated/0/",
    "/storage/emulated/legacy/" to "/storage/emulated/0/"
)

internal object DirectPathLister : LibraryLister {

    override fun listFiles(root: LibraryScanRoot, includeHidden: Boolean): Sequence<File> = sequence {
        val start = File(root.path)
        if (!start.isDirectory) return@sequence
        val queue = ArrayDeque<File>()
        queue.addLast(start)
        var visited = 0
        while (queue.isNotEmpty()) {
            val dir = queue.removeFirst()
            val entries = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (entry in entries) {
                if (++visited > 200_000) return@sequence
                if (!includeHidden && entry.name.startsWith(".")) continue
                if (entry.isDirectory) {
                    queue.addLast(entry)
                } else if (entry.isFile) {
                    yield(entry)
                }
            }
        }
    }
}
