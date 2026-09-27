package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.findExistingCachedFileForSource
import com.flopster101.siliconplayer.platform.ArtworkCacheSupport
import com.flopster101.siliconplayer.platform.PlaylistRefreshNotifier
import com.flopster101.siliconplayer.platform.TrackProbeSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

internal enum class PlaylistMetadataRefreshStatus {
    Idle,
    Running,
    Success,
    PartialSuccess,
    Failed
}

internal data class PlaylistMetadataRefreshState(
    val status: PlaylistMetadataRefreshStatus = PlaylistMetadataRefreshStatus.Idle,
    val current: Int = 0,
    val total: Int = 0,
    val succeededCount: Int = 0,
    val failedCount: Int = 0
)

internal object PlaylistMetadataRefresher {

    private val _state = MutableStateFlow(PlaylistMetadataRefreshState())
    val state: StateFlow<PlaylistMetadataRefreshState> = _state.asStateFlow()

    fun resetState() {
        _state.value = PlaylistMetadataRefreshState()
    }

    private fun resolveTrackProbePath(cacheDir: File, entry: PlaylistTrackEntry): String? {
        val localFile = resolvePlaylistEntryLocalFile(entry.source)
        if (localFile != null && localFile.exists()) {
            return localFile.absolutePath
        }
        val cacheRoot = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
        val cached = findExistingCachedFileForSource(cacheRoot, entry.source)
            ?: entry.requestUrlHint?.let { findExistingCachedFileForSource(cacheRoot, it) }
        if (cached != null && cached.exists()) {
            return cached.absolutePath
        }
        return entry.source
    }

    suspend fun probeTrack(
        cacheDir: File,
        artworkCache: ArtworkCacheSupport,
        trackProbe: TrackProbeSupport,
        entry: PlaylistTrackEntry
    ): PlaylistTrackEntry? = withContext(Dispatchers.IO) {
        val candidatePath = resolveTrackProbePath(cacheDir, entry) ?: return@withContext null
        val probeResult = trackProbe.probeFile(candidatePath, entry.subtuneIndex ?: -1)
            ?: return@withContext null

        val probedTitle = probeResult.title?.trim()?.takeIf { it.isNotBlank() }
        val probedArtist = probeResult.artist?.trim()?.takeIf { it.isNotBlank() }
        val probedAlbum = probeResult.album?.trim()?.takeIf { it.isNotBlank() }
        val probedDuration = probeResult.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }

        val newTitle = if (entry.customTitle.isNullOrBlank() && probedTitle != null) probedTitle else entry.title
        val newArtist = probedArtist ?: entry.artist
        val newAlbum = probedAlbum ?: entry.album
        val newDuration = probedDuration
        val artworkKey = entry.artworkThumbnailCacheKey ?: artworkCache.ensureThumbnailCached(
            sourceId = entry.source,
            requestUrlHint = entry.requestUrlHint
        )

        entry.copy(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            artworkThumbnailCacheKey = artworkKey,
            durationSecondsOverride = newDuration
        )
    }

    suspend fun refreshSingleTrack(
        cacheDir: File,
        artworkCache: ArtworkCacheSupport,
        trackProbe: TrackProbeSupport,
        entry: PlaylistTrackEntry,
        playlistLibraryState: PlaylistLibraryState,
        onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
    ): Boolean {
        val updated = probeTrack(cacheDir, artworkCache, trackProbe, entry) ?: return false
        var changed = false
        val newFavorites = playlistLibraryState.favorites.map { fav ->
            if (fav.id == entry.id) {
                changed = true
                updated
            } else fav
        }
        val newPlaylists = playlistLibraryState.playlists.map { playlist ->
            var plChanged = false
            val entries = playlist.entries.map { e ->
                if (e.id == entry.id) {
                    plChanged = true
                    updated
                } else e
            }
            if (plChanged) {
                changed = true
                playlist.copy(entries = entries, updatedAtMs = System.currentTimeMillis())
            } else playlist
        }
        if (changed) {
            onPlaylistLibraryStateChanged(
                playlistLibraryState.copy(favorites = newFavorites, playlists = newPlaylists)
            )
        }
        return true
    }

    suspend fun refreshPlaylistTracks(
        cacheDir: File,
        artworkCache: ArtworkCacheSupport,
        trackProbe: TrackProbeSupport,
        playlistId: String,
        targetEntryIds: Set<String>?,
        playlistLibraryStateProvider: () -> PlaylistLibraryState,
        onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val currentState = playlistLibraryStateProvider()
        val isFavorites = playlistId == FAVORITES_PLAYLIST_ID
        val tracksToRefresh = if (isFavorites) {
            if (targetEntryIds == null) currentState.favorites else currentState.favorites.filter { it.id in targetEntryIds }
        } else {
            val playlist = currentState.playlists.firstOrNull { it.id == playlistId } ?: return@withContext Pair(0, 0)
            if (targetEntryIds == null) playlist.entries else playlist.entries.filter { it.id in targetEntryIds }
        }

        if (tracksToRefresh.isEmpty()) return@withContext Pair(0, 0)

        var succeeded = 0
        var state = playlistLibraryStateProvider()

        for (track in tracksToRefresh) {
            val probed = probeTrack(cacheDir, artworkCache, trackProbe, track)
            if (probed != null) {
                succeeded++
                state = if (isFavorites) {
                    updateFavoriteTrack(state, probed)
                } else {
                    updateStoredPlaylistEntry(state, playlistId, probed)
                }
                onPlaylistLibraryStateChanged(state)
            }
        }
        Pair(succeeded, tracksToRefresh.size)
    }

    suspend fun refreshAllPlaylists(
        cacheDir: File,
        artworkCache: ArtworkCacheSupport,
        trackProbe: TrackProbeSupport,
        notifier: PlaylistRefreshNotifier,
        localOnly: Boolean,
        onStopPlayback: () -> Unit,
        playlistLibraryStateProvider: () -> PlaylistLibraryState,
        onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (_state.value.status == PlaylistMetadataRefreshStatus.Running) return@withContext
        onStopPlayback()

        val initialState = playlistLibraryStateProvider()
        val allEntries = mutableListOf<PlaylistTrackEntry>()
        allEntries.addAll(initialState.favorites)
        initialState.playlists.forEach { pl ->
            allEntries.addAll(pl.entries)
        }

        val distinctEntries = allEntries.distinctBy { Pair(it.source, it.subtuneIndex ?: -1) }
        val filtered = if (localOnly) {
            distinctEntries.filterNot { isRemotePlaylistSource(it.source) }
        } else {
            distinctEntries.sortedBy { isRemotePlaylistSource(it.source) }
        }

        val total = filtered.size
        if (total == 0) {
            _state.value = PlaylistMetadataRefreshState(
                status = PlaylistMetadataRefreshStatus.Success,
                current = 0,
                total = 0,
                succeededCount = 0,
                failedCount = 0
            )
            return@withContext
        }

        _state.value = PlaylistMetadataRefreshState(
            status = PlaylistMetadataRefreshStatus.Running,
            current = 0,
            total = total,
            succeededCount = 0,
            failedCount = 0
        )
        notifier.update(0, total, null)

        var current = 0
        var succeeded = 0
        var failed = 0
        var state = playlistLibraryStateProvider()

        for (track in filtered) {
            current++
            notifier.update(current, total, track.title)
            val probed = probeTrack(cacheDir, artworkCache, trackProbe, track)
            if (probed != null) {
                succeeded++
                state = mergeTrackPlaybackMetadata(
                    state = state,
                    activeSourceId = track.source,
                    currentSubtuneIndex = track.subtuneIndex ?: -1,
                    title = probed.title,
                    artist = probed.artist,
                    album = probed.album,
                    artworkThumbnailCacheKey = probed.artworkThumbnailCacheKey,
                    durationSecondsOverride = probed.durationSecondsOverride,
                    clearDurationIfUnreliable = (probed.durationSecondsOverride == null),
                    requestUrlHint = track.requestUrlHint
                )
                onPlaylistLibraryStateChanged(state)
            } else {
                failed++
            }

            _state.value = PlaylistMetadataRefreshState(
                status = PlaylistMetadataRefreshStatus.Running,
                current = current,
                total = total,
                succeededCount = succeeded,
                failedCount = failed
            )
        }

        val finalStatus = when {
            succeeded > 0 && failed == 0 -> PlaylistMetadataRefreshStatus.Success
            succeeded > 0 && failed > 0 -> PlaylistMetadataRefreshStatus.PartialSuccess
            else -> PlaylistMetadataRefreshStatus.Failed
        }

        _state.value = PlaylistMetadataRefreshState(
            status = finalStatus,
            current = total,
            total = total,
            succeededCount = succeeded,
            failedCount = failed
        )
    }
}
