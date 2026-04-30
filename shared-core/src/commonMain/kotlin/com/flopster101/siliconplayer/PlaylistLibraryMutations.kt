package com.flopster101.siliconplayer

fun upsertStoredPlaylist(
    state: PlaylistLibraryState,
    playlist: StoredPlaylist,
    sameSource: (String?, String?) -> Boolean,
    updatedAtMs: Long = currentPlaylistTimeMillis()
): PlaylistLibraryState {
    val existingIndex = state.playlists.indexOfFirst { existing ->
        existing.sourceIdHint != null &&
            playlist.sourceIdHint != null &&
            sameSource(existing.sourceIdHint, playlist.sourceIdHint)
    }
    val updatedPlaylist = if (existingIndex >= 0) {
        playlist.copy(
            id = state.playlists[existingIndex].id,
            updatedAtMs = updatedAtMs
        )
    } else {
        playlist.copy(updatedAtMs = updatedAtMs)
    }
    val withoutExisting = if (existingIndex >= 0) {
        state.playlists.toMutableList().apply { removeAt(existingIndex) }
    } else {
        state.playlists.toMutableList()
    }
    withoutExisting.add(0, updatedPlaylist)
    return state.copy(playlists = withoutExisting)
}

fun removeStoredPlaylist(
    state: PlaylistLibraryState,
    playlistId: String
): PlaylistLibraryState {
    return state.copy(
        playlists = state.playlists.filterNot { it.id == playlistId }
    )
}

fun upsertFavoriteTrack(
    state: PlaylistLibraryState,
    track: PlaylistTrackEntry,
    sameSource: (String, String) -> Boolean
): PlaylistLibraryState {
    val withoutExisting = state.favorites.filterNot { existing ->
        sameSource(existing.source, track.source) &&
            (existing.subtuneIndex ?: -1) == (track.subtuneIndex ?: -1)
    }
    return state.copy(favorites = listOf(track) + withoutExisting)
}

fun removeFavoriteTrack(
    state: PlaylistLibraryState,
    favoriteId: String
): PlaylistLibraryState {
    return state.copy(
        favorites = state.favorites.filterNot { it.id == favoriteId }
    )
}

fun moveFavoriteTrack(
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
