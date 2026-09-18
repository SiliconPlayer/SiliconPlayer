package com.flopster101.siliconplayer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

private const val PLAYLIST_LIBRARY_FAVORITES_KEY = "favorites"
private const val PLAYLIST_LIBRARY_PLAYLISTS_KEY = "playlists"
private const val PLAYLIST_ENTRY_ID_KEY = "id"
private const val PLAYLIST_ENTRY_SOURCE_KEY = "source"
private const val PLAYLIST_ENTRY_REQUEST_URL_HINT_KEY = "request_url_hint"
private const val PLAYLIST_ENTRY_TITLE_KEY = "title"
private const val PLAYLIST_ENTRY_CUSTOM_TITLE_KEY = "custom_title"
private const val PLAYLIST_ENTRY_ARTIST_KEY = "artist"
private const val PLAYLIST_ENTRY_ALBUM_KEY = "album"
private const val PLAYLIST_ENTRY_ARTWORK_CACHE_KEY = "artworkThumbnailCacheKey"
private const val PLAYLIST_ENTRY_SUBTUNE_KEY = "subtune_index"
private const val PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY = "duration_seconds_override"
private const val PLAYLIST_ENTRY_ADDED_AT_KEY = "added_at_ms"
private const val STORED_PLAYLIST_ID_KEY = "id"
private const val STORED_PLAYLIST_TITLE_KEY = "title"
private const val STORED_PLAYLIST_FORMAT_KEY = "format"
private const val STORED_PLAYLIST_SOURCE_HINT_KEY = "source_id_hint"
private const val STORED_PLAYLIST_UPDATED_AT_KEY = "updated_at_ms"
private const val STORED_PLAYLIST_IS_PINNED_KEY = "is_pinned"
private const val STORED_PLAYLIST_FOLDER_ID_KEY = "folder_id"
private const val STORED_PLAYLIST_ENTRIES_KEY = "entries"

private const val PLAYLIST_LIBRARY_FOLDERS_KEY = "folders"
private const val FOLDER_ID_KEY = "id"
private const val FOLDER_TITLE_KEY = "title"
private const val FOLDER_PARENT_ID_KEY = "parent_folder_id"
private const val FOLDER_CREATED_AT_KEY = "created_at_ms"
private const val FOLDER_IS_PINNED_KEY = "is_pinned"

internal fun readPlaylistLibraryState(prefs: SharedPreferences): PlaylistLibraryState {
    val raw = prefs.getString(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON, null)
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: return emptyPlaylistLibraryState()
    return runCatching {
        val root = JSONObject(raw)
        val favorites = root.optJSONArray(PLAYLIST_LIBRARY_FAVORITES_KEY)
            ?.let(::readPlaylistTrackEntries)
            .orEmpty()
        val playlists = root.optJSONArray(PLAYLIST_LIBRARY_PLAYLISTS_KEY)
            ?.let(::readStoredPlaylists)
            .orEmpty()
        val folders = root.optJSONArray(PLAYLIST_LIBRARY_FOLDERS_KEY)
            ?.let(::readPlaylistFolders)
            .orEmpty()
        PlaylistLibraryState(
            favorites = favorites,
            playlists = playlists,
            folders = folders
        )
    }.getOrElse {
        emptyPlaylistLibraryState()
    }
}

internal fun writePlaylistLibraryState(
    prefs: SharedPreferences,
    state: PlaylistLibraryState
) {
    val root = JSONObject()
        .put(
            PLAYLIST_LIBRARY_FAVORITES_KEY,
            JSONArray().apply {
                state.favorites.forEach { put(writePlaylistTrackEntry(it)) }
            }
        )
        .put(
            PLAYLIST_LIBRARY_PLAYLISTS_KEY,
            JSONArray().apply {
                state.playlists.forEach { put(writeStoredPlaylist(it)) }
            }
        )
        .put(
            PLAYLIST_LIBRARY_FOLDERS_KEY,
            JSONArray().apply {
                state.folders.forEach { put(writePlaylistFolder(it)) }
            }
        )
    prefs.edit()
        .putString(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON, root.toString())
        .commit()
}

internal fun upsertStoredPlaylist(
    state: PlaylistLibraryState,
    playlist: StoredPlaylist
): PlaylistLibraryState {
    val existingIndex = state.playlists.indexOfFirst { existing ->
        existing.id == playlist.id
    }.takeUnless { it < 0 } ?: state.playlists.indexOfFirst { existing ->
        existing.sourceIdHint != null &&
            playlist.sourceIdHint != null &&
            samePath(existing.sourceIdHint, playlist.sourceIdHint)
    }
    val updatedPlaylist = if (existingIndex >= 0) {
        playlist.copy(
            id = state.playlists[existingIndex].id,
            updatedAtMs = System.currentTimeMillis()
        )
    } else {
        playlist.copy(updatedAtMs = System.currentTimeMillis())
    }
    val withoutExisting = if (existingIndex >= 0) {
        state.playlists.toMutableList().apply { removeAt(existingIndex) }
    } else {
        state.playlists.toMutableList()
    }
    withoutExisting.add(0, updatedPlaylist)
    return state.copy(playlists = withoutExisting)
}

internal fun removeStoredPlaylist(
    state: PlaylistLibraryState,
    playlistId: String
): PlaylistLibraryState {
    return state.copy(
        playlists = state.playlists.filterNot { it.id == playlistId }
    )
}

internal fun renameStoredPlaylist(
    state: PlaylistLibraryState,
    playlistId: String,
    newTitle: String
): PlaylistLibraryState {
    val trimmed = newTitle.trim()
    if (trimmed.isEmpty()) return state
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(
                    title = trimmed,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    )
}

internal fun setStoredPlaylistPinned(
    state: PlaylistLibraryState,
    playlistId: String,
    isPinned: Boolean
): PlaylistLibraryState {
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(
                    isPinned = isPinned,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    )
}

internal fun removeStoredPlaylistEntry(
    state: PlaylistLibraryState,
    playlistId: String,
    entryId: String
): PlaylistLibraryState {
    return removeStoredPlaylistEntries(state, playlistId, setOf(entryId))
}

internal fun removeStoredPlaylistEntries(
    state: PlaylistLibraryState,
    playlistId: String,
    entryIds: Set<String>
): PlaylistLibraryState {
    if (entryIds.isEmpty()) return state
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(
                    entries = playlist.entries.filterNot { it.id in entryIds },
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    )
}

internal fun appendStoredPlaylistEntries(
    state: PlaylistLibraryState,
    playlistId: String,
    newEntries: List<PlaylistTrackEntry>
): PlaylistLibraryState {
    if (newEntries.isEmpty()) return state
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                playlist.copy(
                    entries = playlist.entries + newEntries,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    )
}

internal fun updateStoredPlaylistEntries(
    state: PlaylistLibraryState,
    playlistId: String,
    transform: (PlaylistTrackEntry) -> PlaylistTrackEntry
): PlaylistLibraryState {
    var changed = false
    val updatedPlaylists = state.playlists.map { playlist ->
        if (playlist.id != playlistId) {
            playlist
        } else {
            val newEntries = playlist.entries.map { entry ->
                val updated = transform(entry)
                if (updated != entry) changed = true
                updated
            }
            if (changed) playlist.copy(entries = newEntries, updatedAtMs = System.currentTimeMillis()) else playlist
        }
    }
    return if (changed) state.copy(playlists = updatedPlaylists) else state
}

internal fun updateStoredPlaylistEntry(
    state: PlaylistLibraryState,
    playlistId: String,
    updatedEntry: PlaylistTrackEntry
): PlaylistLibraryState {
    return updateStoredPlaylistEntries(state, playlistId) { entry ->
        if (entry.id == updatedEntry.id) updatedEntry else entry
    }
}

internal fun moveStoredPlaylistEntry(
    state: PlaylistLibraryState,
    playlistId: String,
    entryId: String,
    offset: Int
): PlaylistLibraryState {
    if (offset == 0) return state
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId) {
                playlist
            } else {
                val currentIndex = playlist.entries.indexOfFirst { it.id == entryId }
                if (currentIndex < 0 || playlist.entries.size < 2) {
                    playlist
                } else {
                    val targetIndex = (currentIndex + offset).coerceIn(0, playlist.entries.lastIndex)
                    if (targetIndex == currentIndex) {
                        playlist
                    } else {
                        val reordered = playlist.entries.toMutableList().apply {
                            val entry = removeAt(currentIndex)
                            add(targetIndex, entry)
                        }
                        playlist.copy(
                            entries = reordered,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    )
}

internal fun clearStoredPlaylistEntries(
    state: PlaylistLibraryState,
    playlistId: String
): PlaylistLibraryState {
    return state.copy(
        playlists = state.playlists.map { playlist ->
            if (playlist.id != playlistId || playlist.entries.isEmpty()) {
                playlist
            } else {
                playlist.copy(
                    entries = emptyList(),
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    )
}

internal fun upsertFavoriteTrack(
    state: PlaylistLibraryState,
    track: PlaylistTrackEntry
): PlaylistLibraryState {
    val withoutExisting = state.favorites.filterNot { existing ->
        samePath(existing.source, track.source) &&
            (existing.subtuneIndex ?: -1) == (track.subtuneIndex ?: -1)
    }
    return state.copy(favorites = listOf(track) + withoutExisting)
}

/** Batch favorite insert that preserves the given track order at the top. */
internal fun upsertFavoriteTracks(
    state: PlaylistLibraryState,
    tracks: List<PlaylistTrackEntry>
): PlaylistLibraryState {
    var updated = state
    for (track in tracks.asReversed()) {
        updated = upsertFavoriteTrack(updated, track)
    }
    return updated
}

internal fun removeFavoriteTrack(
    state: PlaylistLibraryState,
    favoriteId: String
): PlaylistLibraryState {
    return removeFavoriteTracks(state, setOf(favoriteId))
}

internal fun removeFavoriteTracks(
    state: PlaylistLibraryState,
    favoriteIds: Set<String>
): PlaylistLibraryState {
    if (favoriteIds.isEmpty()) return state
    return state.copy(
        favorites = state.favorites.filterNot { it.id in favoriteIds }
    )
}

internal fun moveFavoriteTrack(
    state: PlaylistLibraryState,
    favoriteId: String,
    offset: Int
): PlaylistLibraryState {
    if (offset == 0 || state.favorites.size < 2) return state
    val currentIndex = state.favorites.indexOfFirst { it.id == favoriteId }
    if (currentIndex < 0) return state
    val targetIndex = (currentIndex + offset).coerceIn(0, state.favorites.lastIndex)
    if (targetIndex == currentIndex) return state
    val reorderedFavorites = state.favorites.toMutableList().apply {
        val entry = removeAt(currentIndex)
        add(targetIndex, entry)
    }
    return state.copy(favorites = reorderedFavorites)
}

internal fun updateFavoriteTracks(
    state: PlaylistLibraryState,
    transform: (PlaylistTrackEntry) -> PlaylistTrackEntry
): PlaylistLibraryState {
    var changed = false
    val newFavorites = state.favorites.map { fav ->
        val updated = transform(fav)
        if (updated != fav) changed = true
        updated
    }
    return if (changed) state.copy(favorites = newFavorites) else state
}

internal fun updateFavoriteTrack(
    state: PlaylistLibraryState,
    updatedTrack: PlaylistTrackEntry
): PlaylistLibraryState {
    return updateFavoriteTracks(state) { fav ->
        if (fav.id == updatedTrack.id) updatedTrack else fav
    }
}

internal fun mergeTrackPlaybackMetadata(
    state: PlaylistLibraryState,
    activeSourceId: String?,
    currentSubtuneIndex: Int,
    title: String,
    artist: String?,
    album: String?,
    artworkThumbnailCacheKey: String?,
    durationSecondsOverride: Double?,
    clearDurationIfUnreliable: Boolean = false,
    requestUrlHint: String?
): PlaylistLibraryState {
    if (activeSourceId.isNullOrBlank()) return state
    val normalizedTitle = title.trim()
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedAlbum = album?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtworkKey = artworkThumbnailCacheKey?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedDurationOverride = durationSecondsOverride?.takeIf { it.isFinite() && it > 0.0 }
    var changed = false

    fun updateEntry(entry: PlaylistTrackEntry): PlaylistTrackEntry {
        if (!playlistEntryMatchesPlayback(entry, activeSourceId, currentSubtuneIndex)) return entry
        val resolvedDurationOverride = if (clearDurationIfUnreliable) {
            normalizedDurationOverride
        } else {
            normalizedDurationOverride ?: entry.durationSecondsOverride
        }
        val updatedEntry = entry.copy(
            title = if (entry.customTitle == null && normalizedTitle.isNotBlank()) normalizedTitle else entry.title,
            artist = normalizedArtist ?: entry.artist,
            album = normalizedAlbum ?: entry.album,
            artworkThumbnailCacheKey = normalizedArtworkKey ?: entry.artworkThumbnailCacheKey,
            durationSecondsOverride = resolvedDurationOverride,
            requestUrlHint = sanitizePlaylistTrackRequestUrlHint(
                source = entry.source,
                requestUrlHint = requestUrlHint
            ) ?: entry.requestUrlHint
        )
        if (updatedEntry != entry) {
            changed = true
        }
        return updatedEntry
    }

    val updatedFavorites = state.favorites.map(::updateEntry)
    val updatedPlaylists = state.playlists.map { playlist ->
        var playlistChanged = false
        val newEntries = playlist.entries.map { entry ->
            val updated = updateEntry(entry)
            if (updated != entry) playlistChanged = true
            updated
        }
        if (playlistChanged) playlist.copy(entries = newEntries, updatedAtMs = System.currentTimeMillis()) else playlist
    }

    return if (changed) {
        state.copy(favorites = updatedFavorites, playlists = updatedPlaylists)
    } else {
        state
    }
}

internal fun readStoredPlaylistFromJson(raw: String?): StoredPlaylist? {
    val normalized = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    return runCatching {
        readStoredPlaylist(JSONObject(normalized))
    }.getOrNull()
}

internal fun writeStoredPlaylistToJson(playlist: StoredPlaylist): String {
    return writeStoredPlaylist(playlist).toString()
}

private fun readStoredPlaylists(array: JSONArray): List<StoredPlaylist> {
    val playlists = mutableListOf<StoredPlaylist>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        readStoredPlaylist(item)?.let { playlists += it }
    }
    return playlists
}

private fun readStoredPlaylist(item: JSONObject): StoredPlaylist? {
    val title = item.optString(STORED_PLAYLIST_TITLE_KEY).trim()
    if (title.isBlank()) return null
    val entriesArray = item.optJSONArray(STORED_PLAYLIST_ENTRIES_KEY)
    val entries = if (entriesArray != null) readPlaylistTrackEntries(entriesArray) else emptyList()
    val isPinned = item.optBoolean(STORED_PLAYLIST_IS_PINNED_KEY, false)
    val folderId = item.optString(STORED_PLAYLIST_FOLDER_ID_KEY).trim().ifBlank { null }
    return StoredPlaylist(
        id = item.optString(STORED_PLAYLIST_ID_KEY).trim().ifBlank { java.util.UUID.randomUUID().toString() },
        title = title,
        format = PlaylistStoredFormat.fromStorage(item.optString(STORED_PLAYLIST_FORMAT_KEY)),
        sourceIdHint = item.optString(STORED_PLAYLIST_SOURCE_HINT_KEY).trim().ifBlank { null },
        entries = entries,
        updatedAtMs = item.optLong(STORED_PLAYLIST_UPDATED_AT_KEY).takeIf { it > 0L }
            ?: System.currentTimeMillis(),
        isPinned = isPinned,
        folderId = folderId
    )
}

private fun readPlaylistFolders(array: JSONArray): List<PlaylistFolder> {
    val folders = mutableListOf<PlaylistFolder>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        readPlaylistFolder(item)?.let { folders += it }
    }
    return folders
}

private fun readPlaylistFolder(item: JSONObject): PlaylistFolder? {
    val title = item.optString(FOLDER_TITLE_KEY).trim()
    if (title.isBlank()) return null
    return PlaylistFolder(
        id = item.optString(FOLDER_ID_KEY).trim().ifBlank { java.util.UUID.randomUUID().toString() },
        title = title,
        parentFolderId = item.optString(FOLDER_PARENT_ID_KEY).trim().ifBlank { null },
        createdAtMs = item.optLong(FOLDER_CREATED_AT_KEY).takeIf { it > 0L } ?: System.currentTimeMillis(),
        isPinned = item.optBoolean(FOLDER_IS_PINNED_KEY, false)
    )
}

private fun writePlaylistFolder(folder: PlaylistFolder): JSONObject {
    return JSONObject()
        .put(FOLDER_ID_KEY, folder.id)
        .put(FOLDER_TITLE_KEY, folder.title)
        .put(FOLDER_PARENT_ID_KEY, folder.parentFolderId ?: "")
        .put(FOLDER_CREATED_AT_KEY, folder.createdAtMs)
        .put(FOLDER_IS_PINNED_KEY, folder.isPinned)
}

private fun readPlaylistTrackEntries(array: JSONArray): List<PlaylistTrackEntry> {
    val entries = mutableListOf<PlaylistTrackEntry>()
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val source = item.optString(PLAYLIST_ENTRY_SOURCE_KEY).trim()
        val title = item.optString(PLAYLIST_ENTRY_TITLE_KEY).trim()
        if (source.isBlank() || title.isBlank()) continue
        val fallbackAddedAt = (array.length() - index).toLong()
        entries += PlaylistTrackEntry(
            id = item.optString(PLAYLIST_ENTRY_ID_KEY).trim().ifBlank { java.util.UUID.randomUUID().toString() },
            source = source,
            requestUrlHint = item.optString(PLAYLIST_ENTRY_REQUEST_URL_HINT_KEY).trim().ifBlank { null },
            title = title,
            customTitle = item.optString(PLAYLIST_ENTRY_CUSTOM_TITLE_KEY).trim().ifBlank { null },
            artist = item.optString(PLAYLIST_ENTRY_ARTIST_KEY).trim().ifBlank { null },
            album = item.optString(PLAYLIST_ENTRY_ALBUM_KEY).trim().ifBlank { null },
            artworkThumbnailCacheKey = item.optString(PLAYLIST_ENTRY_ARTWORK_CACHE_KEY).trim().ifBlank { null },
            subtuneIndex = item.optInt(PLAYLIST_ENTRY_SUBTUNE_KEY, Int.MIN_VALUE)
                .takeUnless { it == Int.MIN_VALUE || it < 0 },
            durationSecondsOverride = item.optDouble(PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY, Double.NaN)
                .takeIf { it.isFinite() && it > 0.0 },
            addedAtMs = item.optLong(PLAYLIST_ENTRY_ADDED_AT_KEY)
                .takeIf { it > 0L }
                ?: fallbackAddedAt
        )
    }
    return entries
}

private fun writeStoredPlaylist(playlist: StoredPlaylist): JSONObject {
    return JSONObject()
        .put(STORED_PLAYLIST_ID_KEY, playlist.id)
        .put(STORED_PLAYLIST_TITLE_KEY, playlist.title)
        .put(STORED_PLAYLIST_FORMAT_KEY, playlist.format.storageValue)
        .put(STORED_PLAYLIST_SOURCE_HINT_KEY, playlist.sourceIdHint ?: "")
        .put(STORED_PLAYLIST_UPDATED_AT_KEY, playlist.updatedAtMs)
        .put(STORED_PLAYLIST_IS_PINNED_KEY, playlist.isPinned)
        .apply {
            if (!playlist.folderId.isNullOrBlank()) {
                put(STORED_PLAYLIST_FOLDER_ID_KEY, playlist.folderId)
            }
        }
        .put(
            STORED_PLAYLIST_ENTRIES_KEY,
            JSONArray().apply {
                playlist.entries.forEach { put(writePlaylistTrackEntry(it)) }
            }
        )
}

private fun writePlaylistTrackEntry(entry: PlaylistTrackEntry): JSONObject {
    return JSONObject()
        .put(PLAYLIST_ENTRY_ID_KEY, entry.id)
        .put(PLAYLIST_ENTRY_SOURCE_KEY, entry.source)
        .put(PLAYLIST_ENTRY_REQUEST_URL_HINT_KEY, entry.requestUrlHint ?: "")
        .put(PLAYLIST_ENTRY_TITLE_KEY, entry.title)
        .apply {
            if (!entry.customTitle.isNullOrBlank()) {
                put(PLAYLIST_ENTRY_CUSTOM_TITLE_KEY, entry.customTitle)
            }
        }
        .put(PLAYLIST_ENTRY_ARTIST_KEY, entry.artist ?: "")
        .put(PLAYLIST_ENTRY_ALBUM_KEY, entry.album ?: "")
        .put(PLAYLIST_ENTRY_ARTWORK_CACHE_KEY, entry.artworkThumbnailCacheKey ?: "")
        .put(PLAYLIST_ENTRY_SUBTUNE_KEY, entry.subtuneIndex ?: -1)
        .put(PLAYLIST_ENTRY_ADDED_AT_KEY, entry.addedAtMs)
        .apply {
            entry.durationSecondsOverride
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { put(PLAYLIST_ENTRY_DURATION_OVERRIDE_KEY, it) }
        }
}

internal fun createPlaylistFolder(
    state: PlaylistLibraryState,
    title: String,
    parentFolderId: String? = null
): Pair<PlaylistLibraryState, PlaylistFolder> {
    val normalizedTitle = title.trim().ifBlank { "New Folder" }
    val newFolder = PlaylistFolder(
        id = java.util.UUID.randomUUID().toString(),
        title = normalizedTitle,
        parentFolderId = parentFolderId?.trim()?.ifBlank { null }
    )
    val updatedState = state.copy(folders = state.folders + newFolder)
    return updatedState to newFolder
}

internal fun renamePlaylistFolder(
    state: PlaylistLibraryState,
    folderId: String,
    newTitle: String
): PlaylistLibraryState {
    val trimmed = newTitle.trim()
    if (trimmed.isBlank()) return state
    val updatedFolders = state.folders.map { folder ->
        if (folder.id == folderId) folder.copy(title = trimmed) else folder
    }
    return state.copy(folders = updatedFolders)
}

internal fun togglePinPlaylistFolder(
    state: PlaylistLibraryState,
    folderId: String
): PlaylistLibraryState {
    val updatedFolders = state.folders.map { folder ->
        if (folder.id == folderId) folder.copy(isPinned = !folder.isPinned) else folder
    }
    return state.copy(folders = updatedFolders)
}

internal fun movePlaylistFolder(
    state: PlaylistLibraryState,
    folderId: String,
    targetParentFolderId: String?
): PlaylistLibraryState {
    val normalizedTarget = targetParentFolderId?.trim()?.ifBlank { null }
    if (folderId == normalizedTarget) return state
    val descendantIds = getDescendantFolderIds(state.folders, folderId)
    if (normalizedTarget != null && normalizedTarget in descendantIds) {
        return state
    }
    val updatedFolders = state.folders.map { folder ->
        if (folder.id == folderId) folder.copy(parentFolderId = normalizedTarget) else folder
    }
    return state.copy(folders = updatedFolders)
}

internal fun movePlaylistToFolder(
    state: PlaylistLibraryState,
    playlistId: String,
    targetFolderId: String?
): PlaylistLibraryState {
    val normalizedTarget = targetFolderId?.trim()?.ifBlank { null }
    val updatedPlaylists = state.playlists.map { playlist ->
        if (playlist.id == playlistId) {
            playlist.copy(
                folderId = normalizedTarget,
                updatedAtMs = System.currentTimeMillis()
            )
        } else {
            playlist
        }
    }
    return state.copy(playlists = updatedPlaylists)
}

internal fun deletePlaylistFolder(
    state: PlaylistLibraryState,
    folderId: String,
    deletePlaylists: Boolean
): PlaylistLibraryState {
    val targetFolder = state.folders.firstOrNull { it.id == folderId } ?: return state
    val allDeletedFolderIds = setOf(folderId) + getDescendantFolderIds(state.folders, folderId)
    val remainingFolders = state.folders.filterNot { it.id in allDeletedFolderIds }

    val updatedPlaylists = if (deletePlaylists) {
        state.playlists.filterNot { it.folderId in allDeletedFolderIds }
    } else {
        val fallbackParentId = targetFolder.parentFolderId?.takeUnless { it in allDeletedFolderIds }
        state.playlists.map { playlist ->
            if (playlist.folderId in allDeletedFolderIds) {
                playlist.copy(
                    folderId = fallbackParentId,
                    updatedAtMs = System.currentTimeMillis()
                )
            } else {
                playlist
            }
        }
    }
    return state.copy(folders = remainingFolders, playlists = updatedPlaylists)
}
