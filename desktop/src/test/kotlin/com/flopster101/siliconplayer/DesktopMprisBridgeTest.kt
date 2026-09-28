package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.mpris.MprisLoopStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopMprisBridgeTest {

    @Test
    fun volumeMapsBetweenMasterGainAndLinearGain() {
        assertEquals(1.0, masterGainDbToMprisVolume(0f, muted = false), 0.0001)
        assertEquals(0.1, masterGainDbToMprisVolume(-20f, muted = false), 0.0001)
        assertEquals(0.0, masterGainDbToMprisVolume(0f, muted = true), 0.0001)
        // MPRIS tops out at 1.0, so positive headroom is reported as full scale.
        assertEquals(1.0, masterGainDbToMprisVolume(20f, muted = false), 0.0001)
    }

    @Test
    fun volumeFromTheBusMapsBackToDecibels() {
        assertEquals(0f, mprisVolumeToMasterGainDb(1.0), 0.01f)
        assertEquals(-6.02f, mprisVolumeToMasterGainDb(0.5), 0.01f)
        assertEquals(-20f, mprisVolumeToMasterGainDb(0.0), 0.01f)
        // Out-of-range requests clamp to the MPRIS 0.0..1.0 window, so 4.0 is full scale.
        assertEquals(0f, mprisVolumeToMasterGainDb(4.0), 0.01f)
    }

    @Test
    fun everyRepeatModeHasALoopStatus() {
        assertEquals(MprisLoopStatus.None, mprisLoopStatusForRepeatMode(RepeatMode.None))
        assertEquals(MprisLoopStatus.Playlist, mprisLoopStatusForRepeatMode(RepeatMode.Playlist))
        assertEquals(MprisLoopStatus.Track, mprisLoopStatusForRepeatMode(RepeatMode.Track))
        assertEquals(MprisLoopStatus.Track, mprisLoopStatusForRepeatMode(RepeatMode.Subtune))
        assertEquals(MprisLoopStatus.Track, mprisLoopStatusForRepeatMode(RepeatMode.LoopPoint))
    }

    @Test
    fun loopStatusFromTheBusMapsBackToARpeatMode() {
        assertEquals(RepeatMode.None, repeatModeForMprisLoopStatus(MprisLoopStatus.None))
        assertEquals(RepeatMode.Track, repeatModeForMprisLoopStatus(MprisLoopStatus.Track))
        assertEquals(RepeatMode.Playlist, repeatModeForMprisLoopStatus(MprisLoopStatus.Playlist))
    }

    @Test
    fun openUriResolvesLocalFiles() {
        val file = File.createTempFile("mpris-open-", ".mp3")
        try {
            assertEquals(file, resolveDesktopMprisOpenUriFile(file.toURI().toString()))
            assertEquals(file, resolveDesktopMprisOpenUriFile(file.absolutePath))
        } finally {
            file.delete()
        }
    }

    @Test
    fun openUriLeavesRemoteAndMissingSourcesToTheSourcePath() {
        assertNull(resolveDesktopMprisOpenUriFile(""))
        assertNull(resolveDesktopMprisOpenUriFile("https://example.com/track.mp3"))
        assertNull(resolveDesktopMprisOpenUriFile("smb://server/share/track.mod"))
        assertNull(resolveDesktopMprisOpenUriFile(File("/nonexistent/mpris/track.mp3").toURI().toString()))
    }
}
