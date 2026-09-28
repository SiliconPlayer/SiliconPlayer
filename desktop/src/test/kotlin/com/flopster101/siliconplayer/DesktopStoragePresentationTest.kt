package com.flopster101.siliconplayer

import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Public
import com.flopster101.siliconplayer.desktop.DesktopArtworkSupport
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopStoragePresentationTest {

    @Test
    fun nullKeyReturnsNull() {
        assertNull(DesktopArtworkSupport.peekMemoryArtwork(null))
    }

    @Test
    fun localPathReturnsLocal() {
        val sp = storagePresentationForPath("/home/user/track.flac")
        assertEquals("Local", sp.label)
        assertNotNull(sp.icon)
        assertNull(sp.qualifier)
    }

    @Test
    fun fileSchemeReturnsLocal() {
        assertEquals("Local", storagePresentationForPath("file:///home/user/track.flac").label)
    }

    @Test
    fun smbUrlReturnsSmb() {
        val sp = storagePresentationForPath("smb://host/share/track.flac")
        assertEquals("SMB (host)", sp.label)
        assertNotNull(sp.icon)
        assertNull(sp.qualifier)
    }

    @Test
    fun httpUrlReturnsHttp() {
        val sp = storagePresentationForPath("http://host/track.mp3")
        assertEquals("HTTP (host)", sp.label)
        assertNotNull(sp.icon)
        assertNull(sp.qualifier)
    }

    @Test
    fun httpsUrlReturnsHttps() {
        val sp = storagePresentationForPath("https://host/track.mp3")
        assertEquals("HTTPS (host)", sp.label)
        assertNotNull(sp.icon)
        assertNull(sp.qualifier)
    }

    @Test
    fun playlistSchemeReturnsPlaylist() {
        val sp = storagePresentationForPath("playlist://myplaylist")
        assertEquals("Playlist", sp.label)
        assertNotNull(sp.icon)
    }
}
