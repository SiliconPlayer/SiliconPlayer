package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbDisplayResolutionTest {

    @Test
    fun resolveSmbDisplayHost_usesDiscoveredHostNameWhenAvailable() {
        val nodes = listOf(
            NetworkNode(
                id = 1L,
                parentId = null,
                type = NetworkNodeType.RemoteSource,
                title = "Music NAS",
                source = "smb://192.168.1.50/music",
                sourceKind = NetworkSourceKind.Smb,
                smbHost = "192.168.1.50",
                smbShare = "music",
                smbDiscoveredHostName = "DISKSTATION"
            )
        )
        val displayHost = resolveSmbDisplayHost("192.168.1.50", nodes)
        assertEquals("DISKSTATION", displayHost)
    }

    @Test
    fun resolveSmbDisplayHost_fallsBackToTitleWhenNoDiscoveredHostName() {
        val nodes = listOf(
            NetworkNode(
                id = 2L,
                parentId = null,
                type = NetworkNodeType.RemoteSource,
                title = "MyServer",
                source = "smb://192.168.1.60/share",
                sourceKind = NetworkSourceKind.Smb,
                smbHost = "192.168.1.60",
                smbShare = "share",
                smbDiscoveredHostName = null
            )
        )
        val displayHost = resolveSmbDisplayHost("192.168.1.60", nodes)
        assertEquals("MyServer", displayHost)
    }

    @Test
    fun resolveSmbDisplayHost_preservesHostWhenNoNodeMatches() {
        val displayHost = resolveSmbDisplayHost("192.168.1.99", emptyList())
        assertEquals("192.168.1.99", displayHost)
    }

    @Test
    fun buildSmbDisplayUri_formatsResolvedHostAndPath() {
        val nodes = listOf(
            NetworkNode(
                id = 1L,
                parentId = null,
                type = NetworkNodeType.RemoteSource,
                title = "HomeServer",
                source = "smb://192.168.1.50/media",
                sourceKind = NetworkSourceKind.Smb,
                smbHost = "192.168.1.50",
                smbShare = "media",
                smbDiscoveredHostName = "HOMESERVER"
            )
        )
        val spec = SmbSourceSpec(
            host = "192.168.1.50",
            share = "media",
            path = "Music/Chiptunes/song.mod",
            username = "user",
            password = "secret_password"
        )
        val displayUri = buildSmbDisplayUri(spec, nodes)
        assertEquals("smb://HOMESERVER/media/Music/Chiptunes/song.mod", displayUri)
    }

    @Test
    fun formatSourceIdForDisplay_formatsSmbAndHttpSources() {
        val nodes = listOf(
            NetworkNode(
                id = 1L,
                parentId = null,
                type = NetworkNodeType.RemoteSource,
                title = "NAS",
                source = "smb://10.0.0.2/music",
                sourceKind = NetworkSourceKind.Smb,
                smbHost = "10.0.0.2",
                smbShare = "music",
                smbDiscoveredHostName = "SYNOLOGY"
            )
        )
        val formattedSmb = formatSourceIdForDisplay("smb://user:pass@10.0.0.2/music/track.s3m", nodes)
        assertEquals("smb://SYNOLOGY/music/track.s3m", formattedSmb)

        val formattedHttp = formatSourceIdForDisplay("http://user:pass@example.com/audio/song.mp3", nodes)
        assertEquals("http://example.com/audio/song.mp3", formattedHttp)

        val local = formatSourceIdForDisplay("/sdcard/Music/test.xm", nodes)
        assertEquals("/sdcard/Music/test.xm", local)
    }
}
