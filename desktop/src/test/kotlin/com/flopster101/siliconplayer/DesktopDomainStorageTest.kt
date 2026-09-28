package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DesktopDomainStorageTest {

    private fun tempDir(): File =
        Files.createTempDirectory("domain-storage-test").toFile()

    private class FakePrefs : AppPreferences {
        val map = mutableMapOf<String, String?>()
        override fun getString(key: String, defValue: String?): String? =
            if (map.containsKey(key)) map[key] else defValue
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun allKeys(): Set<String> = map.keys
        override fun edit(): AppPreferences.Editor = FakeEditor(map)
        override fun addListener(listener: AppPreferences.OnChangeListener) {}
        override fun removeListener(listener: AppPreferences.OnChangeListener) {}

        private class FakeEditor(private val map: MutableMap<String, String?>) : AppPreferences.Editor {
            override fun putString(key: String, value: String?): AppPreferences.Editor {
                if (value == null) map.remove(key) else map[key] = value
                return this
            }
            override fun putInt(key: String, value: Int): AppPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor = this
            override fun putFloat(key: String, value: Float): AppPreferences.Editor = this
            override fun putLong(key: String, value: Long): AppPreferences.Editor = this
            override fun putStringSet(key: String, values: Set<String>?): AppPreferences.Editor = this
            override fun remove(key: String): AppPreferences.Editor {
                map.remove(key)
                return this
            }
            override fun clear(): AppPreferences.Editor {
                map.clear()
                return this
            }
            override fun apply() {}
            override fun commit(): Boolean = true
        }
    }

    private fun track(source: String, title: String) =
        PlaylistTrackEntry(source = source, title = title)

    @Test
    fun atomicWriteKeepsBackupAndFallsBackOnCorruption() {
        val dir = tempDir()
        val file = File(dir, "probe.json")
        writeTextAtomic(file, """{"ok":true}""")
        writeTextAtomic(file, """{"ok":false}""")
        assertTrue(File(dir, "probe.json.bak").isFile)
        assertEquals("""{"ok":false}""", firstParsableJson(readCandidateTexts(file), isObject = true))
        file.writeText("not json{{{")
        // Backup holds the pre-second-write version, which is what fallback restores.
        assertEquals("""{"ok":true}""", firstParsableJson(readCandidateTexts(file), isObject = true))
    }

    @Test
    fun recentsRoundTripThroughFiles() {
        val dir = tempDir()
        val key = AppPreferenceKeys.RECENT_PLAYED_FILES
        val entries = listOf(
            RecentPathEntry(path = "/music/a.mod", locationId = null, title = "A", artist = "Mod"),
            RecentPathEntry(path = "/music/b.sid", locationId = null, title = "B", sourceNodeId = 7L)
        )
        writeRecentEntries(dir, key, entries, 20)
        assertTrue(domainFileForKey(dir, key).isFile)
        val loaded = readRecentEntries(dir, key, 20)
        assertEquals(2, loaded.size)
        assertEquals("/music/a.mod", loaded[0].path)
        assertEquals("Mod", loaded[0].artist)
        assertEquals(7L, loaded[1].sourceNodeId)
    }

    @Test
    fun recentsMigrateFromLegacyPrefsOnce() {
        val dir = tempDir()
        val prefs = FakePrefs()
        val key = AppPreferenceKeys.RECENT_FOLDERS
        writeRecentEntries(prefs, key, listOf(RecentPathEntry(path = "/music", locationId = null, title = "music")), 10)
        val migrated = readRecentEntries(dir, key, 10, prefs)
        assertEquals(listOf("/music"), migrated.map { it.path })
        assertTrue(domainFileForKey(dir, key).isFile)
        assertFalse(prefs.contains(key))
        // Second read comes from the file, not prefs.
        prefs.map[key] = """[{"path":"/stale"}]"""
        assertEquals(listOf("/music"), readRecentEntries(dir, key, 10, prefs).map { it.path })
        assertFalse(prefs.contains(key))
    }

    @Test
    fun playlistLibrarySplitsAcrossFilesWithFavoritesSeparate() {
        val dir = tempDir()
        val playlistA = StoredPlaylist(id = "id-a", title = "A", entries = listOf(track("/a.mod", "A")))
        val playlistB = StoredPlaylist(id = "id-b", title = "B", entries = listOf(track("/b.sid", "B")))
        val state = PlaylistLibraryState(
            favorites = listOf(track("/fav.xm", "Fav")),
            playlists = listOf(playlistA, playlistB),
            folders = emptyList()
        )
        writePlaylistLibraryState(dir, state)
        val libDir = playlistLibraryDir(dir)
        assertTrue(File(libDir, "index.json").isFile)
        assertTrue(File(libDir, "id-a.json").isFile)
        assertTrue(File(libDir, "id-b.json").isFile)
        assertTrue(File(dir, "favorites.json").isFile)

        val reloaded = readPlaylistLibraryState(dir)
        assertEquals(state.favorites.map { it.source }, reloaded.favorites.map { it.source })
        assertEquals(listOf("A", "B"), reloaded.playlists.map { it.title })
        assertEquals(listOf("/a.mod"), reloaded.playlists[0].entries.map { it.source })
    }

    @Test
    fun playlistWritesOnlyTouchChangedFiles() {
        val dir = tempDir()
        val playlistA = StoredPlaylist(id = "id-a", title = "A", entries = listOf(track("/a.mod", "A")))
        val playlistB = StoredPlaylist(id = "id-b", title = "B", entries = listOf(track("/b.sid", "B")))
        val base = PlaylistLibraryState(favorites = emptyList(), playlists = listOf(playlistA, playlistB), folders = emptyList())
        writePlaylistLibraryState(dir, base)
        val libDir = playlistLibraryDir(dir)
        val bytesA = File(libDir, "id-a.json").readBytes()
        val bytesB = File(libDir, "id-b.json").readBytes()
        val bytesIndex = File(libDir, "index.json").readBytes()
        val bytesFav = File(dir, "favorites.json").readBytes()

        // Favorite toggle must not rewrite either playlist file or the index.
        val withFav = base.copy(favorites = listOf(track("/fav.xm", "Fav")))
        writePlaylistLibraryState(dir, withFav)
        assertEquals(bytesA.toList(), File(libDir, "id-a.json").readBytes().toList())
        assertEquals(bytesB.toList(), File(libDir, "id-b.json").readBytes().toList())
        assertEquals(bytesIndex.toList(), File(libDir, "index.json").readBytes().toList())
        assertFalse(bytesFav.toList() == File(dir, "favorites.json").readBytes().toList())

        // Track add touches only that playlist's file.
        val grownA = playlistA.copy(entries = playlistA.entries + track("/a2.mod", "A2"))
        writePlaylistLibraryState(dir, withFav.copy(playlists = listOf(grownA, playlistB)))
        assertFalse(bytesA.toList() == File(libDir, "id-a.json").readBytes().toList())
        assertEquals(bytesB.toList(), File(libDir, "id-b.json").readBytes().toList())
        val reloaded = readPlaylistLibraryState(dir)
        assertEquals(listOf("/a.mod", "/a2.mod"), reloaded.playlists[0].entries.map { it.source })
        assertEquals(1, reloaded.favorites.size)
    }

    @Test
    fun playlistLibraryMigratesFromLegacyBlob() {
        val dir = tempDir()
        val prefs = FakePrefs()
        val legacy = PlaylistLibraryState(
            favorites = listOf(track("/fav.xm", "Fav")),
            playlists = listOf(StoredPlaylist(id = "id-a", title = "A", entries = listOf(track("/a.mod", "A")))),
            folders = emptyList()
        )
        writePlaylistLibraryState(prefs, legacy)
        val migrated = readPlaylistLibraryState(dir, prefs)
        assertEquals(listOf("/fav.xm"), migrated.favorites.map { it.source })
        assertEquals(listOf("A"), migrated.playlists.map { it.title })
        assertTrue(File(playlistLibraryDir(dir), "id-a.json").isFile)
        assertFalse(prefs.contains(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON))
    }

    @Test
    fun networkNodesRoundTripAndMigrate() {
        val dir = tempDir()
        val prefs = FakePrefs()
        val nodes = listOf(
            NetworkNode(id = 1L, parentId = null, type = NetworkNodeType.Folder, title = "NAS"),
            NetworkNode(id = 2L, parentId = 1L, type = NetworkNodeType.RemoteSource, title = "music", source = "smb://nas/music", sourceKind = NetworkSourceKind.Smb, smbHost = "nas", smbShare = "music")
        )
        writeNetworkNodes(prefs, nodes)
        val migrated = readNetworkNodes(dir, prefs)
        assertEquals(2, migrated.size)
        assertEquals("NAS", migrated[0].title)
        assertEquals("nas", migrated[1].smbHost)
        assertTrue(networkNodesFile(dir).isFile)
        assertFalse(prefs.contains(AppPreferenceKeys.NETWORK_SAVED_NODES))
        assertEquals(2, readNetworkNodes(dir).size)
    }

    @Test
    fun credentialStorePersistsToFile() {
        val dir = tempDir()
        val prefs = FakePrefs()
        val previousPrefsProvider = NetworkCredentialStore.preferencesProvider
        val previousDirProvider = NetworkCredentialStore.configDirProvider
        try {
            NetworkCredentialStore.preferencesProvider = { prefs }
            NetworkCredentialStore.configDirProvider = { dir }
            resetCredentialStoreLoaded()
            NetworkCredentialStore.remember(
                parseSmbSourceSpecFromInput("smb://nas/music")!!, username = "user", password = "secret"
            )
            val file = networkCredentialsFile(dir)
            assertTrue(file.isFile)
            assertTrue(file.readText().contains("nas"))

            // Fresh load resolves the stored credential onto a bare spec.
            resetCredentialStoreLoaded()
            val resolved = NetworkCredentialStore.applyTo(parseSmbSourceSpecFromInput("smb://nas/music")!!)
            assertEquals("user", resolved.username)
            assertEquals("secret", resolved.password)
        } finally {
            NetworkCredentialStore.preferencesProvider = previousPrefsProvider
            NetworkCredentialStore.configDirProvider = previousDirProvider
            resetCredentialStoreLoaded()
        }
    }

    @Test
    fun credentialStoreMigratesFromLegacyPrefs() {
        val dir = tempDir()
        val prefs = FakePrefs()
        prefs.map[AppPreferenceKeys.NETWORK_CREDENTIALS_JSON] =
            """{"version":1,"smb":[{"host":"nas","share":"music","username":"user","password":"secret"}],"http":[]}"""
        val previousPrefsProvider = NetworkCredentialStore.preferencesProvider
        val previousDirProvider = NetworkCredentialStore.configDirProvider
        try {
            NetworkCredentialStore.preferencesProvider = { prefs }
            NetworkCredentialStore.configDirProvider = { dir }
            resetCredentialStoreLoaded()
            val resolved = NetworkCredentialStore.applyTo(parseSmbSourceSpecFromInput("smb://nas/music")!!)
            assertEquals("user", resolved.username)
            assertTrue(networkCredentialsFile(dir).isFile)
            assertFalse(prefs.contains(AppPreferenceKeys.NETWORK_CREDENTIALS_JSON))
        } finally {
            NetworkCredentialStore.preferencesProvider = previousPrefsProvider
            NetworkCredentialStore.configDirProvider = previousDirProvider
            resetCredentialStoreLoaded()
        }
    }

    @Test
    fun clearSavedNetworkSourcesWipesSourcesAndCredentials() {
        val dir = tempDir()
        val prefs = FakePrefs()
        val previousPrefsProvider = NetworkCredentialStore.preferencesProvider
        val previousDirProvider = NetworkCredentialStore.configDirProvider
        val previousStoreDir = DomainStoreDirs.configDir
        try {
            NetworkCredentialStore.preferencesProvider = { prefs }
            NetworkCredentialStore.configDirProvider = { dir }
            DomainStoreDirs.configDir = dir
            resetCredentialStoreLoaded()
            writeNetworkNodes(
                dir,
                listOf(NetworkNode(id = 1L, parentId = null, type = NetworkNodeType.Folder, title = "NAS"))
            )
            NetworkCredentialStore.remember(
                parseSmbSourceSpecFromInput("smb://nas/music")!!, username = "user", password = "secret"
            )

            clearSavedNetworkSources(prefs)

            assertTrue(NetworkNodesHolder.current.isEmpty())
            assertTrue(readNetworkNodes(dir, prefs).isEmpty())
            assertFalse(networkNodesFile(dir).exists())
            val credentialsFile = networkCredentialsFile(dir)
            assertFalse(credentialsFile.exists())
            assertFalse(File(dir, "${credentialsFile.name}.bak").exists())
            assertFalse(prefs.contains(AppPreferenceKeys.NETWORK_SAVED_NODES))
            assertFalse(prefs.contains(AppPreferenceKeys.NETWORK_CREDENTIALS_JSON))
            val resolved = NetworkCredentialStore.applyTo(parseSmbSourceSpecFromInput("smb://nas/music")!!)
            assertNull(resolved.username)
            assertNull(resolved.password)
        } finally {
            NetworkCredentialStore.preferencesProvider = previousPrefsProvider
            NetworkCredentialStore.configDirProvider = previousDirProvider
            DomainStoreDirs.configDir = previousStoreDir
            resetCredentialStoreLoaded()
        }
    }

    private fun resetCredentialStoreLoaded() {
        val field = NetworkCredentialStore::class.java.getDeclaredField("loaded")
        field.isAccessible = true
        field.setBoolean(NetworkCredentialStore, false)
    }
}
