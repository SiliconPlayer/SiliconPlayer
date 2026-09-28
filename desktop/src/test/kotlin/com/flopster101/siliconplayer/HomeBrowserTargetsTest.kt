package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeBrowserTargetsTest {

    private fun recentEntry(path: String, sourceNodeId: Long? = null): RecentPathEntry {
        return RecentPathEntry(path = path, locationId = null, sourceNodeId = sourceNodeId)
    }

    @Test
    fun smbFileResolvesToParentDirectory() {
        val target = resolveBrowserFolderForRecentSource(
            recentEntry("smb://nas-test/music/rock/track.flac"),
            emptyList()
        )!!
        assertEquals("smb://nas-test/music/rock", target.directoryPath)
        assertNull(target.smbSourceNodeId)
        assertNull(target.httpSourceNodeId)
    }

    @Test
    fun smbFileAtShareRootResolvesToShare() {
        val target = resolveBrowserFolderForRecentSource(
            recentEntry("smb://nas-test/music/track.flac"),
            emptyList()
        )!!
        assertEquals("smb://nas-test/music", target.directoryPath)
    }

    @Test
    fun smbFileReattachesPreferredNodeCredentials() {
        val node = NetworkNode(
            id = 7L,
            parentId = null,
            type = NetworkNodeType.RemoteSource,
            title = "NAS",
            sourceKind = NetworkSourceKind.Smb,
            smbHost = "nas-test",
            smbShare = "music",
            smbUsername = "user",
            smbPassword = "pass"
        )
        val target = resolveBrowserFolderForRecentSource(
            recentEntry("smb://nas-test/music/rock/track.flac", sourceNodeId = 7L),
            listOf(node)
        )!!
        assertEquals(7L, target.smbSourceNodeId)
        assertEquals("smb://user:pass@nas-test/music/rock", target.directoryPath)
    }

    @Test
    fun httpFileResolvesToParentDirectory() {
        val target = resolveBrowserFolderForRecentSource(
            recentEntry("https://example.invalid/music/rock/track.flac"),
            emptyList()
        )!!
        assertEquals("https://example.invalid/music/rock/", target.directoryPath)
        assertNull(target.httpSourceNodeId)
    }

    @Test
    fun localFileResolvesToParentDirectory() {
        val dir = createTempDir("browser-target-test")
        try {
            val file = File(dir, "track.flac").apply { writeBytes(byteArrayOf(1)) }
            val target = resolveBrowserFolderForRecentSource(
                recentEntry(file.absolutePath),
                emptyList()
            )!!
            assertEquals(dir.absolutePath, target.directoryPath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankAndBareSharePathsResolveToNull() {
        assertNull(resolveBrowserFolderForRecentSource(recentEntry("   "), emptyList()))
        assertNull(resolveBrowserFolderForRecentSource(recentEntry("smb://nas-test"), emptyList()))
    }

    @Test
    fun smbFolderResolvesToItsParent() {
        val target = resolveBrowserParentForRecentFolder(
            recentEntry("smb://nas-test/music/rock"),
            emptyList()
        )!!
        assertEquals("smb://nas-test/music", target.directoryPath)
    }

    @Test
    fun localFolderResolvesToItsParent() {
        val dir = createTempDir("browser-target-test")
        try {
            val sub = File(dir, "sub").apply { mkdir() }
            assertTrue(sub.isDirectory)
            val target = resolveBrowserParentForRecentFolder(
                recentEntry(sub.absolutePath),
                emptyList()
            )!!
            assertEquals(dir.absolutePath, target.directoryPath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun folderOpenRequestKeepsLocalFolder() {
        val request = resolveFolderOpenRequest(recentEntry("/music/rock"), emptyList())
        assertEquals("/music/rock", request.launchState.directoryPath)
        assertNull(request.launchState.smbSourceNodeId)
        assertNull(request.launchState.httpSourceNodeId)
    }

    @Test
    fun folderOpenRequestKeepsSmbFolder() {
        val request = resolveFolderOpenRequest(
            recentEntry("smb://nas-test/music/rock"),
            emptyList()
        )
        assertEquals("smb://nas-test/music/rock", request.launchState.directoryPath)
        assertNull(request.launchState.smbSourceNodeId)
    }
}
