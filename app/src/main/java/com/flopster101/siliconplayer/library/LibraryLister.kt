package com.flopster101.siliconplayer.library

import java.io.File

/** Abstraction over directory traversal so a SAF backend can slot in later. */
internal fun interface LibraryLister {
    fun listFiles(root: LibraryScanRoot): Sequence<File>
}

internal object DirectPathLister : LibraryLister {

    override fun listFiles(root: LibraryScanRoot): Sequence<File> = sequence {
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
                if (entry.isDirectory) {
                    queue.addLast(entry)
                } else if (entry.isFile) {
                    yield(entry)
                }
            }
        }
    }
}
