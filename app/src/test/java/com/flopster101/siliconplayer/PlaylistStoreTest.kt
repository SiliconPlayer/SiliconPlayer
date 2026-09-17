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

