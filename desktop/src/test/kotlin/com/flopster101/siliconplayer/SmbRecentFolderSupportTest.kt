package com.flopster101.siliconplayer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbRecentFolderSupportTest {

    @Test
    fun isRemoteQueuePlaybackSourceIdentifiesRemoteSchemes() {
        assertTrue(isRemoteQueuePlaybackSource("smb://nas/music/song.mp3"))
        assertTrue(isRemoteQueuePlaybackSource("smb://user:pass@192.168.1.5/share/dir/track.flac"))
        assertTrue(isRemoteQueuePlaybackSource("http://example.com/audio/test.ogg"))
        assertTrue(isRemoteQueuePlaybackSource("https://example.com/audio/test.ogg"))

        assertFalse(isRemoteQueuePlaybackSource("/home/user/Music/track.mp3"))
        assertFalse(isRemoteQueuePlaybackSource("file:///home/user/Music/track.mp3"))
        assertFalse(isRemoteQueuePlaybackSource("archive:///path/to/archive.zip#song.mod"))
        assertFalse(isRemoteQueuePlaybackSource("playlist://my_playlist"))
        assertFalse(isRemoteQueuePlaybackSource(null))
        assertFalse(isRemoteQueuePlaybackSource(""))
        assertFalse(isRemoteQueuePlaybackSource("   "))
    }

    @Test
    fun buildSmbFolderPlayableSourceIdsFiltersAndFormatsUris() {
        val rootSpec = SmbSourceSpec(
            host = "nas-server",
            share = "music",
            path = "artists/queen/bohemian.mp3",
            username = "alice",
            password = "secret"
        )
        val entries = listOf(
            SmbBrowserEntry(name = "..", isDirectory = true, sizeBytes = 0L),
            SmbBrowserEntry(name = "bonus", isDirectory = true, sizeBytes = 0L),
            SmbBrowserEntry(name = ".hidden_track.mp3", isDirectory = false, sizeBytes = 100L, isHidden = true),
            SmbBrowserEntry(name = "cover.jpg", isDirectory = false, sizeBytes = 50000L),
            SmbBrowserEntry(name = "notes.txt", isDirectory = false, sizeBytes = 1200L),
            SmbBrowserEntry(name = "01 - Bohemian Rhapsody.mp3", isDirectory = false, sizeBytes = 5000000L),
            SmbBrowserEntry(name = "02 - Killer Queen.flac", isDirectory = false, sizeBytes = 15000000L),
            SmbBrowserEntry(name = "album.zip", isDirectory = false, sizeBytes = 25000000L)
        )

        val supported = setOf("mp3", "flac")
        val playable = buildSmbFolderPlayableSourceIds(
            entries = entries,
            rootSpec = rootSpec,
            parentPath = "artists/queen",
            supportedExtensions = supported,
            showHiddenFilesAndFolders = false
        )

        assertEquals(2, playable.size)
        assertEquals("smb://alice:secret@nas-server/music/artists/queen/01%20-%20Bohemian%20Rhapsody.mp3", playable[0])
        assertEquals("smb://alice:secret@nas-server/music/artists/queen/02%20-%20Killer%20Queen.flac", playable[1])
    }

    @Test
    fun buildSmbFolderPlayableSourceIdsAtShareRoot() {
        val rootSpec = SmbSourceSpec(
            host = "nas-server",
            share = "audio",
            path = "test.mp3"
        )
        val entries = listOf(
            SmbBrowserEntry(name = "test.mp3", isDirectory = false, sizeBytes = 1000L),
            SmbBrowserEntry(name = "other.ogg", isDirectory = false, sizeBytes = 2000L)
        )

        val playable = buildSmbFolderPlayableSourceIds(
            entries = entries,
            rootSpec = rootSpec,
            parentPath = null,
            supportedExtensions = setOf("mp3", "ogg"),
            showHiddenFilesAndFolders = false
        )

        assertEquals(2, playable.size)
        assertEquals("smb://nas-server/audio/test.mp3", playable[0])
        assertEquals("smb://nas-server/audio/other.ogg", playable[1])
    }

    @Test
    fun buildSmbFolderPlayableSourceIdsIncludesHiddenWhenRequested() {
        val rootSpec = SmbSourceSpec(
            host = "nas-server",
            share = "audio",
            path = "track.mp3"
        )
        val entries = listOf(
            SmbBrowserEntry(name = ".hidden.mp3", isDirectory = false, sizeBytes = 1000L, isHidden = true),
            SmbBrowserEntry(name = "normal.mp3", isDirectory = false, sizeBytes = 1000L, isHidden = false)
        )

        val playable = buildSmbFolderPlayableSourceIds(
            entries = entries,
            rootSpec = rootSpec,
            parentPath = null,
            supportedExtensions = setOf("mp3"),
            showHiddenFilesAndFolders = true
        )

        assertEquals(2, playable.size)
        assertEquals("smb://nas-server/audio/.hidden.mp3", playable[0])
        assertEquals("smb://nas-server/audio/normal.mp3", playable[1])
    }

    @Test
    fun loadSmbRecentFolderContextIgnoresNonSmb() {
        val testScope = CoroutineScope(Job())

        val localEntry = RecentPathEntry(
            path = "/home/user/music/song.mp3",
            locationId = null
        )
        val job = loadSmbRecentFolderContext(
            scope = testScope,
            entry = localEntry
        )
        assertNull(job)
    }

    @Test
    fun loadSmbRecentFolderContextSeedsInitialTrackImmediately() {
        val testScope = CoroutineScope(Job())

        RemotePlayableSourceIdsHolder.current = emptyList()

        val smbEntry = RecentPathEntry(
            path = "smb://nas-server/music/rock/queen.mp3",
            locationId = null
        )

        val job = loadSmbRecentFolderContext(
            scope = testScope,
            entry = smbEntry
        )
        assertNotNull(job)
        job?.cancel()

        assertEquals(1, RemotePlayableSourceIdsHolder.current.size)
        assertTrue(samePath("smb://nas-server/music/rock/queen.mp3", RemotePlayableSourceIdsHolder.current[0]))
    }

    @Test
    fun loadSmbRecentFolderContextPreservesExistingMatchingQueue() {
        val testScope = CoroutineScope(Job())

        val existingQueue = listOf(
            "smb://nas-server/music/rock/song1.mp3",
            "smb://nas-server/music/rock/queen.mp3",
            "smb://nas-server/music/rock/song3.mp3"
        )
        RemotePlayableSourceIdsHolder.current = existingQueue

        val smbEntry = RecentPathEntry(
            path = "smb://nas-server/music/rock/queen.mp3",
            locationId = null
        )

        val job = loadSmbRecentFolderContext(
            scope = testScope,
            entry = smbEntry
        )
        assertNotNull(job)
        job?.cancel()

        assertEquals(3, RemotePlayableSourceIdsHolder.current.size)
        assertEquals(existingQueue, RemotePlayableSourceIdsHolder.current)
    }

    @Test
    fun remotePlayableSourceIdsHolderMatchesWithOrWithoutCredentials() {
        val queue = listOf(
            "smb://user:pass@nas-server/music/rock/song1.mp3",
            "smb://user:pass@nas-server/music/rock/song2.mp3"
        )
        RemotePlayableSourceIdsHolder.current = queue

        val resolved = RemotePlayableSourceIdsHolder.resolvedCurrentOrLastForSource("smb://nas-server/music/rock/song1.mp3")
        assertEquals(2, resolved.size)
        assertEquals(queue, resolved)
    }
}
