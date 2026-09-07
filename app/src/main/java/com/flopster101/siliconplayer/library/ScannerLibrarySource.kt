package com.flopster101.siliconplayer.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import com.flopster101.siliconplayer.MediaMetadataRetrieverGlobalLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.File
import kotlin.math.roundToLong

internal object ScannerLibrarySource {

    data class Progress(
        val scannedFiles: Int,
        val indexedTracks: Int,
        val currentPath: String?
    )

    suspend fun sync(
        context: Context,
        dao: LibraryTrackDao,
        roots: List<LibraryScanRoot>,
        extensions: Set<String>,
        onProgress: (Progress) -> Unit = {}
    ): Int {
        if (roots.isEmpty()) return 0
        val addedAtMs = System.currentTimeMillis()
        val seenPaths = HashSet<String>()
        var indexed = 0
        var scanned = 0

        val existing = dao.tracksForSource(LibraryContract.SOURCE_SCANNER).associateBy { it.path }
        val ownedByMediaStore = dao.trackSourcePairs()
            .filter { it.sourceId == LibraryContract.SOURCE_MEDIASTORE }
            .map { it.path }
            .toHashSet()

        var pendingBatch = ArrayList<LibraryTrackEntity>(64)

        suspend fun flushBatch() {
            if (pendingBatch.isNotEmpty()) {
                dao.upsertTracks(pendingBatch)
                pendingBatch = ArrayList(64)
            }
        }

        for (root in roots) {
            if (!root.enabled) continue
            for (file in DirectPathLister.listFiles(root)) {
                if (!currentCoroutineContext().isActive) {
                    flushBatch()
                    throw kotlinx.coroutines.CancellationException("Library scan cancelled")
                }
                scanned++
                if (scanned % 64 == 0) {
                    onProgress(Progress(scanned, indexed, file.absolutePath))
                }
                val extension = file.extension.lowercase()
                if (extension !in extensions) continue
                val path = file.absolutePath
                seenPaths.add(path)
                val stale = existing[path]
                val mtimeMs = file.lastModified()
                val sizeBytes = file.length()
                if (stale != null && stale.mtimeMs == mtimeMs && stale.sizeBytes == sizeBytes) continue
                if (path in ownedByMediaStore) continue

                val metadata = probeMetadata(file, extension)
                pendingBatch.add(
                    LibraryTrackEntity(
                        path = path,
                        sourceId = LibraryContract.SOURCE_SCANNER,
                        title = metadata.first ?: file.nameWithoutExtension,
                        artist = metadata.second ?: "",
                        albumArtist = "",
                        album = "",
                        trackNo = 0,
                        discNo = 0,
                        durationMs = metadata.third ?: 0L,
                        year = 0,
                        format = extension.uppercase(),
                        sizeBytes = sizeBytes,
                        mtimeMs = mtimeMs,
                        addedAtMs = if (stale != null) stale.addedAtMs else addedAtMs
                    )
                )
                indexed++
                if (pendingBatch.size >= 64) flushBatch()
            }
        }
        flushBatch()

        val removed = existing.keys - seenPaths
        if (removed.isNotEmpty()) dao.deleteTracksByPath(removed.toList())
        return indexed
    }

    /** Title/artist/duration probe; filename fallback keeps every file indexed. */
    private fun probeMetadata(file: File, extension: String): Triple<String?, String?, Long?> {
        if (extension in MEDIA_RETRIEVER_EXTENSIONS) {
            synchronized(MediaMetadataRetrieverGlobalLock) {
                val retriever = MediaMetadataRetriever()
                return try {
                    retriever.setDataSource(file.absolutePath)
                    val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.trim()?.takeIf { it.isNotBlank() }
                    val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?.trim()?.takeIf { it.isNotBlank() }
                    val durationMs = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.trim()?.toLongOrNull()
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.trim()?.toDoubleOrNull()?.roundToLong()
                    Triple(title, artist, durationMs?.takeIf { it > 0 })
                } catch (_: Exception) {
                    Triple(null, null, null)
                } finally {
                    runCatching { retriever.release() }
                }
            }
        }
        return Triple(null, null, null)
    }

    private val MEDIA_RETRIEVER_EXTENSIONS = setOf(
        "mp3", "flac", "wav", "ogg", "oga", "opus", "m4a", "aac", "wma", "mka", "aiff", "aif"
    )
}
