package com.flopster101.siliconplayer.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object LibraryRepository {

    private const val SYNC_STALENESS_MS = 15 * 60 * 1000L

    private val syncMutex = Mutex()

    suspend fun collections(context: Context): LibraryCollections = withContext(Dispatchers.IO) {
        val db = LibraryDatabase.get(context)
        val trackDao = db.trackDao()
        val sourceDao = db.sourceDao()

        val didSync = syncMutex.withLock {
            ensureSourceDefaults(sourceDao)
            val source = sourceDao.source(LibraryContract.SOURCE_MEDIASTORE)
            val enabled = source?.enabled ?: true
            val nowMs = System.currentTimeMillis()
            val needsSync = enabled &&
                    (source == null || source.lastSyncMs <= 0L || nowMs - source.lastSyncMs > SYNC_STALENESS_MS)
            if (needsSync) {
                MediaStoreLibrarySource.sync(context, trackDao)
                sourceDao.setSourceSynced(LibraryContract.SOURCE_MEDIASTORE, System.currentTimeMillis())
            }
            needsSync
        }

        val trackCount = trackDao.trackCount()
        if (trackCount == 0) {
            LibraryCollections(
                albums = emptyList(),
                artists = emptyList(),
                trackCount = 0,
                isSyncing = didSync
            )
        } else {
            LibraryCollections(
                albums = trackDao.albumRows().map { row ->
                    LibraryAlbum(
                        name = row.name.ifBlank { LibraryContract.UNKNOWN_ALBUM },
                        artist = row.artist.ifBlank { LibraryContract.UNKNOWN_ARTIST },
                        trackCount = row.trackCount,
                        durationMs = row.durationMs,
                        year = row.year,
                        artworkPath = row.artworkPath
                    )
                },
                artists = trackDao.artistRows().map { row ->
                    LibraryArtist(
                        name = row.name,
                        trackCount = row.trackCount,
                        albumCount = row.albumCount,
                        artworkPath = row.artworkPath
                    )
                },
                trackCount = trackCount,
                isSyncing = didSync
            )
        }
    }

    private suspend fun ensureSourceDefaults(sourceDao: LibrarySourceDao) {
        if (sourceDao.source(LibraryContract.SOURCE_MEDIASTORE) == null) {
            sourceDao.upsertSource(
                LibrarySourceEntity(id = LibraryContract.SOURCE_MEDIASTORE, enabled = true)
            )
        }
    }
}
