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

    @Test
    fun `appendStoredPlaylistEntries appends new tracks and updates timestamp`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Playlist 1").copy(updatedAtMs = 50L)
            )
        )
        val newTrack = PlaylistTrackEntry(id = "e3", source = "file:///music/track3.mod", title = "Track 3")
        val updated = appendStoredPlaylistEntries(initial, "p1", listOf(newTrack))
        val playlist = updated.playlists.first()
        assertEquals(3, playlist.entries.size)
        assertEquals("e3", playlist.entries.last().id)
        assertTrue(playlist.updatedAtMs > 50L)
    }

    @Test
    fun `sortStoredPlaylists orders by recently updated`() {
        val p1 = samplePlaylist("p1", "B").copy(updatedAtMs = 100L)
        val p2 = samplePlaylist("p2", "A").copy(updatedAtMs = 500L)
        val p3 = samplePlaylist("p3", "C").copy(updatedAtMs = 300L)

        val sorted = sortStoredPlaylists(listOf(p1, p2, p3), PlaylistSortMode.RecentlyUpdated)
        assertEquals(listOf("p2", "p3", "p1"), sorted.map { it.id })
    }

    @Test
    fun `sortStoredPlaylists orders alphabetically ascending and descending`() {
        val p1 = samplePlaylist("p1", "Banana")
        val p2 = samplePlaylist("p2", "apple")
        val p3 = samplePlaylist("p3", "Cherry")

        val asc = sortStoredPlaylists(listOf(p1, p2, p3), PlaylistSortMode.AlphabeticalAsc)
        assertEquals(listOf("p2", "p1", "p3"), asc.map { it.id })

        val desc = sortStoredPlaylists(listOf(p1, p2, p3), PlaylistSortMode.AlphabeticalDesc)
        assertEquals(listOf("p3", "p1", "p2"), desc.map { it.id })
    }

    @Test
    fun `sortStoredPlaylists orders by track count`() {
        val p1 = samplePlaylist("p1", "Few").copy(entries = emptyList())
        val p2 = samplePlaylist("p2", "Many").copy(
            entries = listOf(
                PlaylistTrackEntry(id = "1", source = "s1", title = "T1"),
                PlaylistTrackEntry(id = "2", source = "s2", title = "T2"),
                PlaylistTrackEntry(id = "3", source = "s3", title = "T3")
            )
        )
        val p3 = samplePlaylist("p3", "Medium").copy(
            entries = listOf(PlaylistTrackEntry(id = "1", source = "s1", title = "T1"))
        )

        val sorted = sortStoredPlaylists(listOf(p1, p2, p3), PlaylistSortMode.TrackCount)
        assertEquals(listOf("p2", "p3", "p1"), sorted.map { it.id })
    }

    @Test
    fun `playlistContainsTrack detects existing tracks by path and subtune`() {
        val entries = listOf(
            PlaylistTrackEntry(id = "1", source = "file:///music/song.mod", title = "Song", subtuneIndex = null),
            PlaylistTrackEntry(id = "2", source = "file:///music/multitune.sid", title = "Sid 1", subtuneIndex = 1),
            PlaylistTrackEntry(id = "3", source = "file:///music/multitune.sid", title = "Sid 2", subtuneIndex = 2)
        )

        assertTrue(playlistContainsTrack(entries, "file:///music/song.mod"))
        assertTrue(playlistContainsTrack(entries, "file:///music/multitune.sid", 1))
        assertTrue(playlistContainsTrack(entries, "file:///music/multitune.sid", 2))
        org.junit.Assert.assertFalse(playlistContainsTrack(entries, "file:///music/multitune.sid", 3))
        org.junit.Assert.assertFalse(playlistContainsTrack(entries, "file:///music/other.mod"))
    }
}

