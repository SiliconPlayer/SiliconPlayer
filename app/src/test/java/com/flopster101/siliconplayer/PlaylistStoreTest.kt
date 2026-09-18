package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun `readStoredPlaylistFromJson preserves empty playlist`() {
        val playlist = StoredPlaylist(
            id = "empty-p1",
            title = "Empty Playlist",
            format = PlaylistStoredFormat.Internal,
            sourceIdHint = null,
            entries = emptyList(),
            updatedAtMs = 12345L
        )

        val json = writeStoredPlaylistToJson(playlist)
        val restored = readStoredPlaylistFromJson(json)

        org.junit.Assert.assertNotNull(restored)
        assertEquals("empty-p1", restored?.id)
        assertEquals("Empty Playlist", restored?.title)
        assertTrue(restored?.entries?.isEmpty() == true)
        assertEquals(12345L, restored?.updatedAtMs)
    }

    @Test
    fun `readStoredPlaylistFromJson rejects playlist with blank title`() {
        val json = """{"id":"p1","title":"   ","format":"internal","entries":[]}"""
        val restored = readStoredPlaylistFromJson(json)
        org.junit.Assert.assertNull(restored)
    }

    @Test
    fun `writePlaylistLibraryState and readPlaylistLibraryState preserves empty playlists`() {
        val prefs = FakeSharedPreferences()
        val originalState = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                StoredPlaylist(
                    id = "empty-p1",
                    title = "My Test Playlist",
                    format = PlaylistStoredFormat.Internal,
                    sourceIdHint = null,
                    entries = emptyList(),
                    updatedAtMs = 5000L
                )
            )
        )

        writePlaylistLibraryState(prefs, originalState)
        val loadedState = readPlaylistLibraryState(prefs)

        assertEquals(1, loadedState.playlists.size)
        val loadedPlaylist = loadedState.playlists.first()
        assertEquals("empty-p1", loadedPlaylist.id)
        assertEquals("My Test Playlist", loadedPlaylist.title)
        assertTrue(loadedPlaylist.entries.isEmpty())
        assertEquals(5000L, loadedPlaylist.updatedAtMs)
    }

    @Test
    fun `writePlaylistLibraryState and readPlaylistLibraryState round-trips favorites and playlists with tracks`() {
        val prefs = FakeSharedPreferences()
        val originalState = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(
                    id = "fav-1",
                    source = "file:///music/fav.mod",
                    title = "Favorite Song",
                    artist = "Artist",
                    album = "Album",
                    subtuneIndex = 1
                )
            ),
            playlists = listOf(
                samplePlaylist("p1", "Playlist With Tracks"),
                StoredPlaylist(
                    id = "empty-p2",
                    title = "Empty Playlist 2",
                    format = PlaylistStoredFormat.Internal,
                    sourceIdHint = null,
                    entries = emptyList(),
                    updatedAtMs = 2000L
                )
            )
        )

        writePlaylistLibraryState(prefs, originalState)
        val loadedState = readPlaylistLibraryState(prefs)

        assertEquals(1, loadedState.favorites.size)
        assertEquals("fav-1", loadedState.favorites.first().id)
        assertEquals("Favorite Song", loadedState.favorites.first().title)
        assertEquals(2, loadedState.playlists.size)
        assertEquals("p1", loadedState.playlists[0].id)
        assertEquals(2, loadedState.playlists[0].entries.size)
        assertEquals("empty-p2", loadedState.playlists[1].id)
        assertTrue(loadedState.playlists[1].entries.isEmpty())
    }

    @Test
    fun `serializePlaylistToM3u exports valid M3U format with EXTINF and subtunes`() {
        val playlist = StoredPlaylist(
            id = "test-export",
            title = "My Synth Hits",
            format = PlaylistStoredFormat.Internal,
            entries = listOf(
                PlaylistTrackEntry(
                    id = "e1",
                    source = "/storage/emulated/0/Music/track1.mp3",
                    title = "Around The World",
                    artist = "Daft Punk",
                    durationSecondsOverride = 185.4
                ),
                PlaylistTrackEntry(
                    id = "e2",
                    source = "https://example.com/audio/chip.sid",
                    title = "Commando",
                    artist = "Rob Hubbard",
                    subtuneIndex = 2,
                    durationSecondsOverride = null
                ),
                PlaylistTrackEntry(
                    id = "e3",
                    source = "content://com.android.providers.media.documents/document/audio%3A123",
                    title = "Untitled Track",
                    artist = null,
                    durationSecondsOverride = 60.0
                )
            )
        )

        val exported = serializePlaylistToM3u(playlist)
        val lines = exported.lines().filter { it.isNotBlank() }

        assertEquals("#EXTM3U", lines[0])
        assertEquals("#EXTINF:185,Daft Punk - Around The World", lines[1])
        assertEquals("/storage/emulated/0/Music/track1.mp3", lines[2])
        assertEquals("#EXTINF:-1,Rob Hubbard - Commando", lines[3])
        assertEquals("https://example.com/audio/chip.sid#subtune=3", lines[4])
        assertEquals("#EXTINF:60,Untitled Track", lines[5])
        assertEquals("content://com.android.providers.media.documents/document/audio%3A123", lines[6])
    }

    @Test
    fun `serializePlaylistToM3u handles empty playlist`() {
        val playlist = StoredPlaylist(
            id = "empty",
            title = "Empty",
            format = PlaylistStoredFormat.Internal,
            entries = emptyList()
        )
        val exported = serializePlaylistToM3u(playlist)
        assertEquals("#EXTM3U\n", exported)
    }

    @Test
    fun `suggestedPlaylistExportFileName sanitizes invalid characters`() {
        val playlist = StoredPlaylist(
            id = "p1",
            title = "Cool: Hits / Tracks? *Yes*",
            format = PlaylistStoredFormat.Internal,
            entries = emptyList()
        )
        val fileName = suggestedPlaylistExportFileName(playlist, PlaylistExportFormat.M3U8)
        assertEquals("Cool_ Hits _ Tracks_ _Yes_.m3u8", fileName)
    }

    @Test
    fun `PlaylistExportRegistry resolves exporters correctly`() {
        val m3u8Exporter = PlaylistExportRegistry.exporterFor(PlaylistExportFormat.M3U8)
        assertEquals(PlaylistExportFormat.M3U8, m3u8Exporter.format)
        val m3uExporter = PlaylistExportRegistry.exporterFor(PlaylistExportFormat.M3U)
        assertEquals(PlaylistExportFormat.M3U, m3uExporter.format)
        assertEquals(m3u8Exporter, PlaylistExportRegistry.defaultExporter())
    }

    @Test
    fun `parseM3uPlaylistLines parses metadata subtunes and content URIs`() {
        val lines = listOf(
            "#EXTM3U",
            "#EXTINF:120,Artist One - Song One",
            "content://media/external/audio/media/42#subtune=3",
            "#EXTINF:60,Song Two",
            "http://example.com/music/tune.mod"
        )
        val doc = parseM3uPlaylistLines(
            lines = lines,
            title = "Test Import",
            allowUnresolvedFiles = true
        )
        assertNotNull(doc)
        assertEquals("Test Import", doc!!.title)
        assertEquals(2, doc.entries.size)

        val first = doc.entries[0]
        assertEquals("content://media/external/audio/media/42", first.source)
        assertEquals("Artist One", first.artist)
        assertEquals("Song One", first.title)
        assertEquals(2, first.subtuneIndex)
        assertEquals(120.0, first.durationSecondsOverride)

        val second = doc.entries[1]
        assertEquals("http://example.com/music/tune.mod", second.source)
        assertEquals(null, second.artist)
        assertEquals("Song Two", second.title)
        assertEquals(null, second.subtuneIndex)
        assertEquals(60.0, second.durationSecondsOverride)
    }

    @Test
    fun `parseM3uPlaylistLines with allowUnresolvedFiles preserves non-existent files`() {
        val lines = listOf(
            "#EXTM3U",
            "#EXTINF:-1,Nonexistent Song",
            "/some/unmounted/storage/path/song.xm"
        )
        val doc = parseM3uPlaylistLines(
            lines = lines,
            title = "Import With Unresolved",
            allowUnresolvedFiles = true
        )
        assertNotNull(doc)
        assertEquals(1, doc!!.entries.size)
        assertEquals("/some/unmounted/storage/path/song.xm", doc.entries[0].source)
        assertEquals("Nonexistent Song", doc.entries[0].title)
    }

    @Test
    fun `parseM3uPlaylistLines with baseFile resolves relative entries to absolute paths matching base directory`() {
        val lines = listOf(
            "#EXTM3U",
            "001 Grand Opening.mini2sf",
            "sub\\002 Track.mini2sf"
        )
        val baseFile = java.io.File("/storage/emulated/0/Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/!playlist.m3u")
        val doc = parseM3uPlaylistLines(
            lines = lines,
            title = "Kirby",
            baseFile = baseFile,
            allowUnresolvedFiles = true
        )
        assertNotNull(doc)
        assertEquals(2, doc!!.entries.size)
        assertEquals(
            "/storage/emulated/0/Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/001 Grand Opening.mini2sf",
            doc.entries[0].source
        )
        assertEquals("001 Grand Opening", doc.entries[0].title)
        assertEquals(
            "/storage/emulated/0/Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/sub/002 Track.mini2sf",
            doc.entries[1].source
        )
    }

    @Test
    fun `resolveExternalStorageDocId resolves primary volume document id correctly`() {
        val docId = "primary:Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/!playlist.m3u"
        val resolved = resolveExternalStorageDocId(null, docId)
        assertNotNull(resolved)
        assertTrue(resolved!!.endsWith("Music/SyncedMusic/Chips/VGM/DS/Kirby Super Star Ultra (EMU).zophar/!playlist.m3u"))
    }

    @Test
    fun `duplicateStoredPlaylist creates new playlist with fresh ids and copy title`() {
        val original = samplePlaylist("orig-id", "Chiptunes")
        val duplicated = duplicateStoredPlaylist(original, "Chiptunes (Copy)")

        assertTrue(duplicated.id != original.id)
        assertEquals("Chiptunes (Copy)", duplicated.title)
        assertEquals(PlaylistStoredFormat.Internal, duplicated.format)
        assertEquals(original.entries.size, duplicated.entries.size)

        for (i in original.entries.indices) {
            val origEntry = original.entries[i]
            val dupEntry = duplicated.entries[i]
            assertTrue(dupEntry.id != origEntry.id)
            assertEquals(origEntry.source, dupEntry.source)
            assertEquals(origEntry.title, dupEntry.title)
        }
    }

    @Test
    fun `duplicateStoredPlaylist falls back to default copy title when blank`() {
        val original = samplePlaylist("orig-id", "Soundtracks")
        val duplicated = duplicateStoredPlaylist(original, "   ")

        assertEquals("Soundtracks (Copy)", duplicated.title)
    }

    @Test
    fun `setStoredPlaylistPinned toggles isPinned and updates timestamp`() {
        val initial = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Playlist 1").copy(isPinned = false, updatedAtMs = 100L)
            )
        )
        val pinnedState = setStoredPlaylistPinned(initial, "p1", true)
        val pinned = pinnedState.playlists.first()
        assertTrue(pinned.isPinned)
        assertTrue(pinned.updatedAtMs > 100L)

        val unpinnedState = setStoredPlaylistPinned(pinnedState, "p1", false)
        val unpinned = unpinnedState.playlists.first()
        assertTrue(!unpinned.isPinned)
    }

    @Test
    fun `stored playlist json serialization preserves isPinned`() {
        val playlist = samplePlaylist("p1", "Favorite Tunes").copy(isPinned = true)
        val json = writeStoredPlaylistToJson(playlist)
        val restored = readStoredPlaylistFromJson(json)

        assertNotNull(restored)
        assertTrue(restored!!.isPinned)
        assertEquals("Favorite Tunes", restored.title)
    }

    @Test
    fun `readStoredPlaylistFromJson defaults isPinned to false when absent`() {
        val rawJson = """
            {
                "id": "legacy-id",
                "title": "Legacy Playlist",
                "format": "internal",
                "updated_at_ms": 500,
                "entries": []
            }
        """.trimIndent()
        val restored = readStoredPlaylistFromJson(rawJson)

        assertNotNull(restored)
        assertTrue(!restored!!.isPinned)
    }

    @Test
    fun `removeStoredPlaylistEntries removes matching entries and updates timestamp`() {
        val original = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                StoredPlaylist(
                    id = "p1",
                    title = "Test",
                    format = PlaylistStoredFormat.Internal,
                    sourceIdHint = null,
                    entries = listOf(
                        PlaylistTrackEntry(id = "e1", source = "s1", title = "Track 1"),
                        PlaylistTrackEntry(id = "e2", source = "s2", title = "Track 2"),
                        PlaylistTrackEntry(id = "e3", source = "s3", title = "Track 3")
                    ),
                    updatedAtMs = 100L
                )
            )
        )

        val updated = removeStoredPlaylistEntries(original, "p1", setOf("e1", "e3"))
        val remaining = updated.playlists.first().entries
        assertEquals(1, remaining.size)
        assertEquals("e2", remaining.first().id)
        assertTrue(updated.playlists.first().updatedAtMs >= 100L)
    }

    @Test
    fun `removeFavoriteTracks removes multiple favorite entries`() {
        val original = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(id = "f1", source = "s1", title = "Fav 1"),
                PlaylistTrackEntry(id = "f2", source = "s2", title = "Fav 2"),
                PlaylistTrackEntry(id = "f3", source = "s3", title = "Fav 3")
            ),
            playlists = emptyList()
        )

        val updated = removeFavoriteTracks(original, setOf("f1", "f2"))
        assertEquals(1, updated.favorites.size)
        assertEquals("f3", updated.favorites.first().id)
    }

    @Test
    fun `updateStoredPlaylistEntries and updateStoredPlaylistEntry update target entries`() {
        val original = PlaylistLibraryState(
            favorites = emptyList(),
            playlists = listOf(
                samplePlaylist("p1", "Playlist 1")
            )
        )

        val updated = updateStoredPlaylistEntry(
            original,
            "p1",
            original.playlists.first().entries.first().copy(title = "Updated Title", durationSecondsOverride = 123.4)
        )
        val firstEntry = updated.playlists.first().entries.first()
        assertEquals("Updated Title", firstEntry.title)
        assertEquals(123.4, firstEntry.durationSecondsOverride ?: 0.0, 0.001)
    }

    @Test
    fun `updateFavoriteTracks and updateFavoriteTrack update target favorites`() {
        val original = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(id = "f1", source = "s1", title = "Fav 1")
            ),
            playlists = emptyList()
        )

        val updated = updateFavoriteTrack(
            original,
            original.favorites.first().copy(title = "Updated Fav", artist = "Artist 1", durationSecondsOverride = 45.0)
        )
        val firstFav = updated.favorites.first()
        assertEquals("Updated Fav", firstFav.title)
        assertEquals("Artist 1", firstFav.artist)
        assertEquals(45.0, firstFav.durationSecondsOverride ?: 0.0, 0.001)
    }

    @Test
    fun `mergeTrackPlaybackMetadata updates matching entries and preserves customTitle`() {
        val original = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(
                    id = "f1",
                    source = "/music/song.mp3",
                    title = "Old Title",
                    customTitle = "My Custom Title"
                ),
                PlaylistTrackEntry(
                    id = "f2",
                    source = "/music/other.mp3",
                    title = "Other Song"
                )
            ),
            playlists = listOf(
                StoredPlaylist(
                    id = "p1",
                    title = "My Playlist",
                    format = PlaylistStoredFormat.Internal,
                    entries = listOf(
                        PlaylistTrackEntry(
                            id = "e1",
                            source = "/music/song.mp3",
                            title = "Raw Filename"
                        )
                    )
                )
            )
        )

        val merged = mergeTrackPlaybackMetadata(
            state = original,
            activeSourceId = "/music/song.mp3",
            currentSubtuneIndex = 0,
            title = "Probed Song Title",
            artist = "Probed Artist",
            album = "Probed Album",
            artworkThumbnailCacheKey = "thumb_123",
            durationSecondsOverride = 210.5,
            requestUrlHint = null
        )

        val fav1 = merged.favorites.first { it.id == "f1" }
        assertEquals("Old Title", fav1.title)
        assertEquals("My Custom Title", fav1.customTitle)
        assertEquals("My Custom Title", fav1.effectiveTitle)
        assertEquals("Probed Artist", fav1.artist)
        assertEquals("Probed Album", fav1.album)
        assertEquals(210.5, fav1.durationSecondsOverride ?: 0.0, 0.001)

        val playlistEntry = merged.playlists.first().entries.first { it.id == "e1" }
        assertEquals("Probed Song Title", playlistEntry.title)
        assertEquals("Probed Artist", playlistEntry.artist)
        assertEquals(210.5, playlistEntry.durationSecondsOverride ?: 0.0, 0.001)

        val otherFav = merged.favorites.first { it.id == "f2" }
        assertEquals("Other Song", otherFav.title)
        assertEquals(null, otherFav.durationSecondsOverride)
    }

    @Test
    fun `mergeTrackPlaybackMetadata clears duration override when clearDurationIfUnreliable is true`() {
        val original = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(
                    id = "f1",
                    source = "/music/song.nsf",
                    title = "NSF Track",
                    durationSecondsOverride = 180.0
                )
            ),
            playlists = listOf(
                StoredPlaylist(
                    id = "p1",
                    title = "My Playlist",
                    format = PlaylistStoredFormat.Internal,
                    entries = listOf(
                        PlaylistTrackEntry(
                            id = "e1",
                            source = "/music/song.nsf",
                            title = "NSF Track",
                            durationSecondsOverride = 180.0
                        )
                    )
                )
            )
        )

        val merged = mergeTrackPlaybackMetadata(
            state = original,
            activeSourceId = "/music/song.nsf",
            currentSubtuneIndex = 0,
            title = "NSF Track",
            artist = "Composer",
            album = "Game",
            artworkThumbnailCacheKey = null,
            durationSecondsOverride = null,
            clearDurationIfUnreliable = true,
            requestUrlHint = null
        )

        val fav1 = merged.favorites.first { it.id == "f1" }
        assertEquals(null, fav1.durationSecondsOverride)

        val playlistEntry = merged.playlists.first().entries.first { it.id == "e1" }
        assertEquals(null, playlistEntry.durationSecondsOverride)
    }

    @Test
    fun `customTitle roundtrips through JSON serialization`() {
        val original = PlaylistLibraryState(
            favorites = listOf(
                PlaylistTrackEntry(
                    id = "f1",
                    source = "/music/song.mp3",
                    title = "Real Title",
                    customTitle = "User Custom Name",
                    durationSecondsOverride = 99.5
                )
            ),
            playlists = emptyList()
        )
        val prefs = FakeSharedPreferences()
        writePlaylistLibraryState(prefs, original)

        val restored = readPlaylistLibraryState(prefs)
        val restoredFav = restored.favorites.first()
        assertEquals("Real Title", restoredFav.title)
        assertEquals("User Custom Name", restoredFav.customTitle)
        assertEquals("User Custom Name", restoredFav.effectiveTitle)
        assertEquals(99.5, restoredFav.durationSecondsOverride ?: 0.0, 0.001)
    }

    @Test
    fun `createPlaylistFolder adds folder with correct parent`() {
        val initial = PlaylistLibraryState()
        val (updated, folder) = createPlaylistFolder(initial, "Chiptunes", null)
        assertEquals(1, updated.folders.size)
        assertEquals("Chiptunes", folder.title)
        org.junit.Assert.assertNull(folder.parentFolderId)

        val (subState, subFolder) = createPlaylistFolder(updated, "Amiga", folder.id)
        assertEquals(2, subState.folders.size)
        assertEquals(folder.id, subFolder.parentFolderId)
    }

    @Test
    fun `renamePlaylistFolder updates title`() {
        val initial = PlaylistLibraryState(
            folders = listOf(PlaylistFolder(id = "f1", title = "Old", parentFolderId = null))
        )
        val updated = renamePlaylistFolder(initial, "f1", "New Title")
        assertEquals("New Title", updated.folders.first().title)
    }

    @Test
    fun `togglePinPlaylistFolder toggles pinned status`() {
        val initial = PlaylistLibraryState(
            folders = listOf(PlaylistFolder(id = "f1", title = "Folder", parentFolderId = null, isPinned = false))
        )
        val pinned = togglePinPlaylistFolder(initial, "f1")
        assertTrue(pinned.folders.first().isPinned)

        val unpinned = togglePinPlaylistFolder(pinned, "f1")
        org.junit.Assert.assertFalse(unpinned.folders.first().isPinned)
    }

    @Test
    fun `movePlaylistFolder updates parent and prevents circular move`() {
        val f1 = PlaylistFolder(id = "f1", title = "Parent", parentFolderId = null)
        val f2 = PlaylistFolder(id = "f2", title = "Child", parentFolderId = "f1")
        val f3 = PlaylistFolder(id = "f3", title = "Grandchild", parentFolderId = "f2")
        val state = PlaylistLibraryState(folders = listOf(f1, f2, f3))

        // Moving f3 to root
        val moved = movePlaylistFolder(state, "f3", null)
        org.junit.Assert.assertNull(moved.folders.first { it.id == "f3" }.parentFolderId)

        // Attempting circular move: move f1 into its descendant f2 should be ignored
        val circular = movePlaylistFolder(state, "f1", "f2")
        assertEquals(null, circular.folders.first { it.id == "f1" }.parentFolderId)

        // Attempting self move: move f1 into f1 should be ignored
        val self = movePlaylistFolder(state, "f1", "f1")
        assertEquals(null, self.folders.first { it.id == "f1" }.parentFolderId)
    }

    @Test
    fun `movePlaylistToFolder updates folderId on playlist`() {
        val p1 = samplePlaylist("p1", "Playlist 1")
        val state = PlaylistLibraryState(
            playlists = listOf(p1),
            folders = listOf(PlaylistFolder(id = "f1", title = "Folder", parentFolderId = null))
        )
        val moved = movePlaylistToFolder(state, "p1", "f1")
        assertEquals("f1", moved.playlists.first().folderId)

        val movedToRoot = movePlaylistToFolder(moved, "p1", null)
        org.junit.Assert.assertNull(movedToRoot.playlists.first().folderId)
    }

    @Test
    fun `deletePlaylistFolder without deleting playlists moves them to parent`() {
        val f1 = PlaylistFolder(id = "f1", title = "Parent", parentFolderId = null)
        val f2 = PlaylistFolder(id = "f2", title = "Child", parentFolderId = "f1")
        val p1 = samplePlaylist("p1", "P1").copy(folderId = "f2")
        val state = PlaylistLibraryState(folders = listOf(f1, f2), playlists = listOf(p1))

        val updated = deletePlaylistFolder(state, "f2", deletePlaylists = false)
        assertEquals(1, updated.folders.size)
        assertEquals("f1", updated.folders.first().id)
        // Playlist in f2 moved to f2's parent which is f1
        assertEquals(1, updated.playlists.size)
        assertEquals("f1", updated.playlists.first().folderId)
    }

    @Test
    fun `deletePlaylistFolder with deletePlaylists deletes folder and its contents`() {
        val f1 = PlaylistFolder(id = "f1", title = "Parent", parentFolderId = null)
        val f2 = PlaylistFolder(id = "f2", title = "Child", parentFolderId = "f1")
        val p1 = samplePlaylist("p1", "P1").copy(folderId = "f2")
        val state = PlaylistLibraryState(folders = listOf(f1, f2), playlists = listOf(p1))

        val updated = deletePlaylistFolder(state, "f1", deletePlaylists = true)
        assertTrue(updated.folders.isEmpty())
        assertTrue(updated.playlists.isEmpty())
    }

    @Test
    fun `writePlaylistLibraryState and readPlaylistLibraryState preserves folders and playlist folderId`() {
        val prefs = FakeSharedPreferences()
        val original = PlaylistLibraryState(
            folders = listOf(
                PlaylistFolder(id = "f1", title = "VGM", parentFolderId = null, isPinned = true),
                PlaylistFolder(id = "f2", title = "NES", parentFolderId = "f1", isPinned = false)
            ),
            playlists = listOf(
                samplePlaylist("p1", "Megaman").copy(folderId = "f2")
            )
        )
        writePlaylistLibraryState(prefs, original)

        val restored = readPlaylistLibraryState(prefs)
        assertEquals(2, restored.folders.size)
        val rF1 = restored.folders.first { it.id == "f1" }
        assertEquals("VGM", rF1.title)
        assertTrue(rF1.isPinned)
        org.junit.Assert.assertNull(rF1.parentFolderId)

        val rF2 = restored.folders.first { it.id == "f2" }
        assertEquals("NES", rF2.title)
        assertEquals("f1", rF2.parentFolderId)

        assertEquals("f2", restored.playlists.first().folderId)
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = map[key] as? MutableSet<String> ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val prefs: FakeSharedPreferences) : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val removed = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = values } }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = apply { key?.let { temp[it] = value } }
            override fun remove(key: String?): android.content.SharedPreferences.Editor = apply { key?.let { removed.add(it) } }
            override fun clear(): android.content.SharedPreferences.Editor = apply { clear = true }
            override fun commit(): Boolean {
                if (clear) prefs.map.clear()
                removed.forEach { prefs.map.remove(it) }
                prefs.map.putAll(temp)
                return true
            }
            override fun apply() { commit() }
        }
    }
}

