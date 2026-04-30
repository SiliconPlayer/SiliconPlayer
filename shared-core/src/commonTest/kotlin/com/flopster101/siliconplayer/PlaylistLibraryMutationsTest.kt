package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistLibraryMutationsTest {

    @Test
    fun upsertStoredPlaylistReplacesMatchingSourceHintAtFront() {
        val existing = StoredPlaylist(
            id = "existing",
            title = "Old",
            format = PlaylistStoredFormat.M3u,
            sourceIdHint = "/music/list.m3u",
            entries = listOf(track("old")),
            updatedAtMs = 1L
        )
        val other = StoredPlaylist(
            id = "other",
            title = "Other",
            format = PlaylistStoredFormat.Internal,
            entries = listOf(track("other")),
            updatedAtMs = 2L
        )
        val imported = StoredPlaylist(
            id = "imported",
            title = "New",
            format = PlaylistStoredFormat.M3u,
            sourceIdHint = "/music/list.m3u",
            entries = listOf(track("new")),
            updatedAtMs = 3L
        )

        val result = upsertStoredPlaylist(
            state = PlaylistLibraryState(favorites = emptyList(), playlists = listOf(other, existing)),
            playlist = imported,
            sameSource = { left, right -> left == right },
            updatedAtMs = 4L
        )

        assertEquals(listOf("existing", "other"), result.playlists.map { it.id })
        assertEquals("New", result.playlists.first().title)
        assertEquals(4L, result.playlists.first().updatedAtMs)
    }

    @Test
    fun upsertFavoriteTrackReplacesSameSourceAndSubtune() {
        val oldFavorite = track("old", source = "/music/song.vgm", subtuneIndex = 1)
        val otherSubtune = track("other", source = "/music/song.vgm", subtuneIndex = 2)
        val newFavorite = track("new", source = "/music/song.vgm", subtuneIndex = 1)

        val result = upsertFavoriteTrack(
            state = PlaylistLibraryState(favorites = listOf(oldFavorite, otherSubtune), playlists = emptyList()),
            track = newFavorite,
            sameSource = { left, right -> left == right }
        )

        assertEquals(listOf("new", "other"), result.favorites.map { it.id })
    }

    @Test
    fun moveFavoriteTrackClampsToListBounds() {
        val state = PlaylistLibraryState(
            favorites = listOf(track("a"), track("b"), track("c")),
            playlists = emptyList()
        )

        val result = moveFavoriteTrack(state, favoriteId = "b", offset = 99)

        assertEquals(listOf("a", "c", "b"), result.favorites.map { it.id })
    }

    private fun track(
        id: String,
        source: String = "/music/$id.vgm",
        subtuneIndex: Int? = null
    ): PlaylistTrackEntry {
        return PlaylistTrackEntry(
            id = id,
            source = source,
            title = id,
            subtuneIndex = subtuneIndex,
            addedAtMs = 1L
        )
    }
}
