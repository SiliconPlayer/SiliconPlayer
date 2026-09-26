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
