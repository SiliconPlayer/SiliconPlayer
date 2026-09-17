package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistStoreTest {

    private fun samplePlaylist(id: String, title: String): StoredPlaylist {
        return StoredPlaylist(
            id = id,
            title = title,
            format = PlaylistStoredFormat.Internal,
            sourceIdHint = null,
            entries = listOf(
                PlaylistTrackEntry(
                    id = "e1",
                    source = "file:///music/track1.mod",
                    title = "Track 1"
                ),
                PlaylistTrackEntry(
                    id = "e2",
                    source = "file:///music/track2.mod",
                    title = "Track 2"
                )
            ),
            updatedAtMs = 1000L
        )
    }

    @Test
    fun `removeStoredPlaylist removes target playlist by id`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Playlist 1"),
                samplePlaylist("p2", "Playlist 2")
            )
        )

        val updated = removeStoredPlaylist(initial, "p1")
        assertEquals(1, updated.playlists.size)
        assertEquals("p2", updated.playlists.first().id)
    }

    @Test
    fun `renameStoredPlaylist updates title and bumps timestamp`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Old Title")
            )
        )

        val updated = renameStoredPlaylist(initial, "p1", "  New Title  ")
        assertEquals("New Title", updated.playlists.first().title)
        assertTrue(updated.playlists.first().updatedAtMs >= 1000L)
    }

    @Test
    fun `renameStoredPlaylist ignores blank title`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Initial Title")
            )
        )

        val updated = renameStoredPlaylist(initial, "p1", "   ")
        assertEquals("Initial Title", updated.playlists.first().title)
    }

    @Test
    fun `removeStoredPlaylistEntry removes specific entry`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Playlist 1")
            )
        )

        val updated = removeStoredPlaylistEntry(initial, "p1", "e1")
        val playlist = updated.playlists.first()
        assertEquals(1, playlist.entries.size)
        assertEquals("e2", playlist.entries.first().id)
    }
}
