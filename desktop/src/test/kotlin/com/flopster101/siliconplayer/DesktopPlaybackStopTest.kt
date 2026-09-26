package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

class DesktopPlaybackStopTest {

    private val testFile = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    @Test
    fun stopClearsTrackAndPlayRestartsFromBeginning() {
        Assume.assumeTrue("Music fixture is available", testFile.exists())
        val session = DesktopPlaybackSession()
        try {
            assertTrue("Track loads and starts", session.loadFile(testFile))
            assertTrue(
                "Engine starts playing",
                awaitCondition(3_000) { session.isPlaying }
            )

            session.seekTo(10.0)
            // seekTo applies the target optimistically; wait until the ticker (which mirrors the
            // native position every 30 ms) confirms playback really sits past 10 s.
            val seekDeadline = System.currentTimeMillis() + 3_000
            while (System.currentTimeMillis() < seekDeadline && session.positionSeconds < 9.5) {
                Thread.sleep(25)
            }
            Thread.sleep(150)
            assertTrue(
                "Playback reaches 10 s (position=${session.positionSeconds})",
                session.positionSeconds >= 9.5
            )

            session.stop()
            assertNull("Stop unloads the track", session.currentFile)
            assertFalse("Stop halts playback", session.isPlaying)
            assertEquals("Stop clears title", "", session.title)
            Thread.sleep(150) // one ticker cycle must not resurrect stale native state
            assertEquals("Stop resets position", 0.0, session.positionSeconds, 0.0)
            assertEquals("Stop clears duration", 0.0, session.durationSeconds, 0.0)

            session.play()
            assertNotNull("Play reloads the stopped source", session.currentFile)
            assertEquals(testFile.absolutePath, session.currentFile?.absolutePath)
            assertTrue(
                "Play starts playback again",
                awaitCondition(3_000) { session.isPlaying }
            )
            assertTrue(
                "Playback restarts near the beginning instead of resuming mid-song " +
                    "(position=${session.positionSeconds})",
                session.positionSeconds < 5.0
            )
        } finally {
            session.dispose()
        }
    }
}
