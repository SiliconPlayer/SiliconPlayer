package com.flopster101.siliconplayer.library

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LibrarySyncState(
    val isScanning: Boolean = false,
    val scannedFiles: Int = 0,
    val indexedTracks: Int = 0,
    val currentPath: String? = null,
    val lastSyncedAtMs: Long = 0L
)

object LibraryRepository {

    private const val SYNC_STALENESS_MS = 15 * 60 * 1000L

    private val syncMutex = Mutex()
    private var cachedState = LibrarySyncState()
    private var cachedCollections: LibraryCollections? = null

    suspend fun collections(context: Context, forceSync: Boolean = false): LibraryCollections =
        withContext(Dispatchers.IO) {
            val db = LibraryDatabase.get(context)
            val trackDao = db.trackDao()
            val sourceDao = db.sourceDao()

            val didSync = syncMutex.withLock {
                ensureSourceDefaults(sourceDao)
                val nowMs = System.currentTimeMillis()
                val syncNeeded = forceSync || run {
                    val mediaStore = sourceDao.source(LibraryContract.SOURCE_MEDIASTORE)
                    val scanner = sourceDao.source(LibraryContract.SOURCE_SCANNER)
                    val mediaStoreStale = (mediaStore?.enabled ?: true) &&
                            (mediaStore == null || mediaStore.lastSyncMs <= 0L ||
                                    nowMs - mediaStore.lastSyncMs > SYNC_STALENESS_MS)
                    val scannerStale = (scanner?.enabled ?: false) &&
                            (scanner.lastSyncMs <= 0L ||
                                    nowMs - scanner.lastSyncMs > SYNC_STALENESS_MS)
                    mediaStoreStale || scannerStale
                }
                if ((syncNeeded && LibraryScanRootStore.autoScanEnabled(context)) || forceSync) {
                    syncAllLocked(context, trackDao, sourceDao)
                    true
                } else {
                    false
                }
            }

            val trackCount = trackDao.trackCount()
            val collections = if (trackCount == 0) {
                LibraryCollections(albums = emptyList(), artists = emptyList(), trackCount = 0, isSyncing = didSync)
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
            cachedCollections = collections
            collections
        }

    suspend fun syncState(): LibrarySyncState = cachedState

    suspend fun runManualScan(
        context: Context,
        onStateChange: (LibrarySyncState) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val db = LibraryDatabase.get(context)
            syncMutex.withLock {
                ensureSourceDefaults(db.sourceDao())
                onStateChange(cachedState.copy(isScanning = true, scannedFiles = 0, indexedTracks = 0, currentPath = null))
                syncAllLocked(context, db.trackDao(), db.sourceDao()) { scanned, indexed, path ->
                    cachedState = LibrarySyncState(
                        isScanning = true,
                        scannedFiles = scanned,
                        indexedTracks = indexed,
                        currentPath = path,
                        lastSyncedAtMs = cachedState.lastSyncedAtMs
                    )
                    onStateChange(cachedState)
                }
                cachedState = cachedState.copy(
                    isScanning = false,
                    lastSyncedAtMs = System.currentTimeMillis()
                )
                onStateChange(cachedState)
            }
        }
    }

    suspend fun sourceEnabled(context: Context, sourceId: String): Boolean {
        val db = LibraryDatabase.get(context)
        return db.sourceDao().source(sourceId)?.enabled ?: (sourceId == LibraryContract.SOURCE_MEDIASTORE)
    }

    suspend fun setSourceEnabled(context: Context, sourceId: String, enabled: Boolean) {
        val db = LibraryDatabase.get(context)
        db.sourceDao().upsertSource(
            LibrarySourceEntity(id = sourceId, enabled = enabled, lastSyncMs = 0L)
        )
        cachedCollections = null
    }

    private suspend fun syncAllLocked(
        context: Context,
        trackDao: LibraryTrackDao,
        sourceDao: LibrarySourceDao,
        onScannerProgress: (scanned: Int, indexed: Int, currentPath: String?) -> Unit = { _, _, _ -> }
    ) {
        val syncedAtMs = System.currentTimeMillis()
        if (sourceDao.source(LibraryContract.SOURCE_MEDIASTORE)?.enabled ?: true) {
            MediaStoreLibrarySource.sync(context, trackDao)
            sourceDao.setSourceSynced(LibraryContract.SOURCE_MEDIASTORE, syncedAtMs)
        }
        val scannerSource = sourceDao.source(LibraryContract.SOURCE_SCANNER)
        if (scannerSource?.enabled == true) {
            val roots = LibraryScanRootStore.loadRoots(context)
            val extensions = LibraryScanRootStore.loadExtensions(context)
            if (roots.isNotEmpty()) {
                ScannerLibrarySource.sync(context, trackDao, roots, extensions) { progress ->
                    onScannerProgress(progress.scannedFiles, progress.indexedTracks, progress.currentPath)
                }
            }
            sourceDao.setSourceSynced(LibraryContract.SOURCE_SCANNER, syncedAtMs)
        }
    }

    private suspend fun ensureSourceDefaults(sourceDao: LibrarySourceDao) {
        if (sourceDao.source(LibraryContract.SOURCE_MEDIASTORE) == null) {
            sourceDao.upsertSource(
                LibrarySourceEntity(id = LibraryContract.SOURCE_MEDIASTORE, enabled = true, lastSyncMs = 0L)
            )
        }
        if (sourceDao.source(LibraryContract.SOURCE_SCANNER) == null) {
            sourceDao.upsertSource(
                LibrarySourceEntity(id = LibraryContract.SOURCE_SCANNER, enabled = false, lastSyncMs = 0L)
            )
        }
    }
}
