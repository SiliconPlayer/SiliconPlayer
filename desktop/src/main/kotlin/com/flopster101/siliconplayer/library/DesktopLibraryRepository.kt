package com.flopster101.siliconplayer.library

import com.flopster101.siliconplayer.platform.LibraryRepositorySupport
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val SYNC_STALENESS_MS = 15 * 60 * 1000L

// Directory-scanner library persisted as JSON; probes run isolated.
class DesktopLibraryRepository(private val configDir: File) : LibraryRepositorySupport {

    override val isAvailable: Boolean = true

    // Scan jobs live on a process scope so they survive leaving the library
    // screen; the UI observes scanState for progress.
    private val scanScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val scanMutex = Mutex()
    private val _scanState = MutableStateFlow(LibrarySyncState())
    override val scanState: StateFlow<LibrarySyncState> = _scanState.asStateFlow()

    override suspend fun collections(): LibraryCollections = withContext(Dispatchers.IO) {
        libraryCollections(readLibraryTracks(configDir))
    }

    override suspend fun search(rawQuery: String): LibrarySearchResults = withContext(Dispatchers.IO) {
        librarySearch(readLibraryTracks(configDir), rawQuery)
    }

    override suspend fun albumTracks(albumName: String): List<LibraryTrackEntity> =
        withContext(Dispatchers.IO) {
            libraryAlbumTracks(readLibraryTracks(configDir), albumName)
        }

    override suspend fun artistTracks(artist: String): List<LibraryTrackEntity> =
        withContext(Dispatchers.IO) {
            libraryArtistTracks(readLibraryTracks(configDir), artist)
        }

    suspend fun artistAlbums(artist: String): List<LibraryAlbum> = withContext(Dispatchers.IO) {
        libraryArtistAlbums(readLibraryTracks(configDir), artist)
    }

    /**
     * Fire-and-forget scan on the process scope; concurrent requests no-op.
     * Progress flows through [scanState], so callers never own the scan job.
     */
    override fun requestScan() {
        scanScope.launch {
            if (!scanMutex.tryLock()) return@launch
            try {
                _scanState.value = LibrarySyncState(isScanning = true)
                syncLocked { scanned, indexed, path ->
                    _scanState.value = LibrarySyncState(
                        isScanning = true,
                        scannedFiles = scanned,
                        indexedTracks = indexed,
                        currentPath = path,
                        lastSyncedAtMs = _scanState.value.lastSyncedAtMs
                    )
                }
                _scanState.value = LibrarySyncState(lastSyncedAtMs = System.currentTimeMillis())
            } catch (_: Exception) {
                // The next sync attempt retries; reset the UI state below.
            } finally {
                _scanState.value = _scanState.value.copy(isScanning = false)
                scanMutex.unlock()
            }
        }
    }

    /** Start a scan if the library looks stale and auto-scan is enabled. */
    suspend fun maybeStartAutoScan() = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        if (!config.autoScanEnabled) return@withContext
        val nowMs = System.currentTimeMillis()
        if (config.lastSyncMs <= 0L || nowMs - config.lastSyncMs > SYNC_STALENESS_MS) {
            requestScan()
        }
    }

    suspend fun scanRoots(): List<LibraryScanRoot> = withContext(Dispatchers.IO) {
        readLibraryScanConfig(configDir).roots
    }

    suspend fun setScanRoots(roots: List<LibraryScanRoot>) = withContext(Dispatchers.IO) {
        val config = readLibraryScanConfig(configDir)
        writeLibraryScanConfig(configDir, config.copy(roots = roots))
    }

    private suspend fun syncLocked(
        onProgress: (scanned: Int, indexed: Int, currentPath: String?) -> Unit
    ) {
        val config = readLibraryScanConfig(configDir)
        val extensions = config.extensions ?: defaultScanExtensions()
        val syncedAtMs = System.currentTimeMillis()
        scanRootsIntoStore(config.roots, extensions, onProgress)
        // The scan rewrote the track store (pruning vanished files); stamp
        // the sync time on the config row.
        writeLibraryScanConfig(configDir, config.copy(lastSyncMs = syncedAtMs))
    }

    private data class ProbeCandidate(
        val path: String,
        val nameWithoutExtension: String,
        val extension: String,
        val mtimeMs: Long,
        val sizeBytes: Long,
        val stale: LibraryTrackEntity?
    )

    // Walks enabled roots, probes changed files in isolated child JVMs (a
    // native decoder crash must never take the app down) and rewrites the
    // track store.
    private suspend fun scanRootsIntoStore(
        roots: List<LibraryScanRoot>,
        extensions: Set<String>,
        onProgress: (scanned: Int, indexed: Int, currentPath: String?) -> Unit
    ) = withContext(Dispatchers.IO) {
        val existing = readLibraryTracks(configDir).associateBy { it.path }.toMutableMap()
        val seenPaths = HashSet<String>()
        val candidates = ArrayList<ProbeCandidate>()
        var scanned = 0
        for (root in roots) {
            if (!root.enabled) continue
            for (file in DirectPathLister.listFiles(root, includeHidden = false)) {
                scanned++
                if (scanned % 64 == 0) {
                    onProgress(scanned, 0, file.absolutePath)
                }
                val extension = file.extension.lowercase()
                if (extension !in extensions) continue
                val path = file.absolutePath
                seenPaths.add(path)
                val stale = existing[path]
                val mtimeMs = file.lastModified()
                val sizeBytes = file.length()
                if (stale != null && stale.mtimeMs == mtimeMs && stale.sizeBytes == sizeBytes) continue
                candidates.add(
                    ProbeCandidate(
                        path = path,
                        nameWithoutExtension = file.nameWithoutExtension,
                        extension = extension,
                        mtimeMs = mtimeMs,
                        sizeBytes = sizeBytes,
                        stale = stale
                    )
                )
            }
        }
        val addedAtMs = System.currentTimeMillis()
        var indexed = 0
        var dirty = false
        if (candidates.isNotEmpty()) {
            val results = IsolatedLibraryProber().probeAll(candidates.map { it.path }) { probed, total ->
                if (probed % 16 == 0 || probed == total) {
                    onProgress(scanned, indexed + probed, candidates.getOrNull(probed)?.path)
                }
            }
            val byPath = candidates.associateBy { it.path }
            results.forEach { (path, probe) ->
                val candidate = byPath[path] ?: return@forEach
                val durationMs = probe?.durationSeconds
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { (it * 1000.0).roundToLong() } ?: 0L
                // Skip durationless, artist-less phantom opens; keep them out of the library.
                if (probe == null ||
                    (durationMs <= 0L && probe.artist.isNullOrBlank() && probe.album.isNullOrBlank())
                ) {
                    return@forEach
                }
                existing[path] = LibraryTrackEntity(
                    path = path,
                    sourceId = LibraryContract.SOURCE_SCANNER,
                    dedupKey = libraryDedupKeyForPath(path),
                    title = probe.title ?: candidate.nameWithoutExtension,
                    artist = probe.artist ?: "",
                    albumArtist = probe.artist ?: "",
                    album = probe.album ?: "",
                    trackNo = 0,
                    discNo = 0,
                    durationMs = durationMs,
                    year = 0,
                    format = candidate.extension.uppercase(),
                    sizeBytes = candidate.sizeBytes,
                    mtimeMs = candidate.mtimeMs,
                    addedAtMs = candidate.stale?.addedAtMs ?: addedAtMs
                )
                indexed++
            }
            dirty = true
            onProgress(scanned, indexed, null)
        }
        val removed = existing.keys - seenPaths
        if (removed.isNotEmpty()) {
            removed.forEach { existing.remove(it) }
            dirty = true
        }
        if (dirty) {
            writeLibraryTracks(configDir, existing.values.toList())
        }
    }
}
