package com.flopster101.siliconplayer.library

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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

data class LibrarySourceStatus(
    val id: String,
    val enabled: Boolean,
    val lastSyncMs: Long,
    val trackCount: Long
)

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

object LibraryRepository {

    private const val SYNC_STALENESS_MS = 15 * 60 * 1000L

    private val syncMutex = Mutex()

    // Scan jobs live on a process scope so they survive leaving the library
    // screen; the UI observes scanState and the notifier posts progress.
    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _scanState = MutableStateFlow(LibrarySyncState())
    val scanState: StateFlow<LibrarySyncState> = _scanState

    suspend fun collections(context: Context): LibraryCollections =
        withContext(Dispatchers.IO) {
            val db = LibraryDatabase.get(context)
            val trackDao = db.trackDao()
            ensureSourceDefaults(db.sourceDao())

            val trackCount = trackDao.enabledTrackCount()
            if (trackCount == 0) {
                LibraryCollections(albums = emptyList(), artists = emptyList(), trackCount = 0)
            } else {
                LibraryCollections(
                    albums = trackDao.albumRows().map { it.toLibraryAlbum() },
                    artists = trackDao.artistRows().map { it.toLibraryArtist() },
                    trackCount = trackCount
                )
            }
        }

    /** Search albums, artists and tracks by a substring of their names. */
    suspend fun search(context: Context, rawQuery: String): LibrarySearchResults =
        withContext(Dispatchers.IO) {
            val query = rawQuery.trim()
            if (query.isEmpty()) {
                return@withContext LibrarySearchResults(query, emptyList(), emptyList(), emptyList())
            }
            val escaped = query
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            val pattern = "%$escaped%"
            val db = LibraryDatabase.get(context)
            LibrarySearchResults(
                query = query,
                albums = db.trackDao().searchAlbumRows(pattern).map { it.toLibraryAlbum() },
                artists = db.trackDao().searchArtistRows(pattern).map { it.toLibraryArtist() },
                tracks = db.trackDao().searchTracks(pattern)
            )
        }

    private fun LibraryAlbumRow.toLibraryAlbum(): LibraryAlbum = LibraryAlbum(
        name = name.ifBlank { LibraryContract.UNKNOWN_ALBUM },
        artist = when {
            distinctArtists > 1 -> LibraryContract.VARIOUS_ARTISTS
            else -> artist.ifBlank { LibraryContract.UNKNOWN_ARTIST }
        },
        trackCount = trackCount,
        durationMs = durationMs,
        year = year,
        artworkPath = artworkPath,
        rawName = name
    )

    private fun LibraryArtistRow.toLibraryArtist(): LibraryArtist = LibraryArtist(
        name = name,
        trackCount = trackCount,
        albumCount = albumCount,
        artworkPath = artworkPath
    )

    suspend fun albumDetail(
        context: Context,
        albumName: String
    ): LibraryAlbumDetail? = withContext(Dispatchers.IO) {
        val db = LibraryDatabase.get(context)
        val tracks = db.trackDao().albumTracks(albumName)
        if (tracks.isEmpty()) return@withContext null
        // Albums group by title only, so a bucket can span artists (the unknown
        // album always does); show the shared artist or a compilation label.
        val artistKeys = tracks.map { it.albumArtist.ifBlank { it.artist } }
            .filter { it.isNotBlank() }
            .distinct()
        LibraryAlbumDetail(
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

    suspend fun artistTracks(context: Context, artist: String): List<LibraryTrackEntity> =
        withContext(Dispatchers.IO) {
            LibraryDatabase.get(context).trackDao().artistTracks(artist)
        }

    suspend fun albumTracks(context: Context, albumName: String): List<LibraryTrackEntity> =
        withContext(Dispatchers.IO) {
            LibraryDatabase.get(context).trackDao().albumTracks(albumName)
        }

    suspend fun artistAlbums(context: Context, artist: String): List<LibraryAlbum> =
        withContext(Dispatchers.IO) {
            val db = LibraryDatabase.get(context)
            db.trackDao().artistAlbumRows(artist).map { row ->
                LibraryAlbum(
                    name = row.name.ifBlank { LibraryContract.UNKNOWN_ALBUM },
                    artist = artist,
                    trackCount = row.trackCount,
                    durationMs = row.durationMs,
                    year = row.year,
                    artworkPath = row.artworkPath,
                    rawName = row.name
                )
            }
        }

    suspend fun syncState(): LibrarySyncState = _scanState.value

    /**
     * Fire-and-forget scan on the process scope; concurrent requests no-op.
     * Progress flows through [scanState] and the notification, so callers
     * never need to own the scan job.
     */
    fun requestScan(context: Context) {
        val appContext = context.applicationContext
        scanScope.launch {
            if (!syncMutex.tryLock()) return@launch
            try {
                val db = LibraryDatabase.get(appContext)
                ensureSourceDefaults(db.sourceDao())
                LibraryScanNotifier.start(appContext)
                _scanState.value = LibrarySyncState(isScanning = true)
                syncAllLocked(appContext, db.trackDao(), db.sourceDao()) { scanned, indexed, path ->
                    _scanState.value = LibrarySyncState(
                        isScanning = true,
                        scannedFiles = scanned,
                        indexedTracks = indexed,
                        currentPath = path,
                        lastSyncedAtMs = _scanState.value.lastSyncedAtMs
                    )
                    LibraryScanNotifier.progress(appContext, _scanState.value)
                }
                _scanState.value = LibrarySyncState(lastSyncedAtMs = System.currentTimeMillis())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                // The next sync attempt retries; reset the UI state below.
            } finally {
                _scanState.value = _scanState.value.copy(isScanning = false)
                LibraryScanNotifier.finish(appContext)
                syncMutex.unlock()
            }
        }
    }

    /** Start a scan if enabled sources look stale and auto-scan is enabled. */
    suspend fun maybeStartAutoScan(context: Context) = withContext(Dispatchers.IO) {
        val db = LibraryDatabase.get(context)
        ensureSourceDefaults(db.sourceDao())
        if (!LibraryScanRootStore.autoScanEnabled(context)) return@withContext
        val nowMs = System.currentTimeMillis()
        val mediaStore = db.sourceDao().source(LibraryContract.SOURCE_MEDIASTORE)
        val scanner = db.sourceDao().source(LibraryContract.SOURCE_SCANNER)
        val mediaStoreStale = (mediaStore?.enabled ?: true) &&
                (mediaStore == null || mediaStore.lastSyncMs <= 0L ||
                        nowMs - mediaStore.lastSyncMs > SYNC_STALENESS_MS)
        val scannerStale = (scanner?.enabled ?: false) &&
                (scanner.lastSyncMs <= 0L ||
                        nowMs - scanner.lastSyncMs > SYNC_STALENESS_MS)
        if (mediaStoreStale || scannerStale) {
            requestScan(context)
        }
    }

    suspend fun sourceEnabled(context: Context, sourceId: String): Boolean {
        val db = LibraryDatabase.get(context)
        return db.sourceDao().source(sourceId)?.enabled ?: (sourceId == LibraryContract.SOURCE_MEDIASTORE)
    }

    suspend fun scanRoots(context: Context): List<LibraryScanRoot> = LibraryScanRootStore.loadRoots(context)

    suspend fun setScanRoots(context: Context, roots: List<LibraryScanRoot>) {
        LibraryScanRootStore.saveRoots(context, roots)
    }

    suspend fun scannerExtensions(context: Context): Set<String> = LibraryScanRootStore.loadExtensions(context)

    suspend fun setScannerExtensions(context: Context, extensions: Set<String>) {
        LibraryScanRootStore.saveExtensions(context, extensions)
    }

    suspend fun autoScanEnabled(context: Context): Boolean = LibraryScanRootStore.autoScanEnabled(context)

    suspend fun setAutoScanEnabled(context: Context, enabled: Boolean) {
        LibraryScanRootStore.setAutoScanEnabled(context, enabled)
    }

    suspend fun sourceStatuses(context: Context): List<LibrarySourceStatus> =
        withContext(Dispatchers.IO) {
            val db = LibraryDatabase.get(context)
            ensureSourceDefaults(db.sourceDao())
            db.sourceDao().allSources().map { source ->
                LibrarySourceStatus(
                    id = source.id,
                    enabled = source.enabled,
                    lastSyncMs = source.lastSyncMs,
                    trackCount = if (source.enabled) {
                        db.trackDao().trackCountForSource(source.id)
                    } else {
                        0L
                    }
                )
            }
        }

    suspend fun setSourceEnabled(context: Context, sourceId: String, enabled: Boolean) {
        val db = LibraryDatabase.get(context)
        db.sourceDao().upsertSource(
            LibrarySourceEntity(id = sourceId, enabled = enabled, lastSyncMs = 0L)
        )
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
