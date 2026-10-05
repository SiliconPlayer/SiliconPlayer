package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Engine start can rebuild the output device synchronously, so play()
 * dispatches it: it must return at once, still start playback, and a
 * stop/pause that lands first must suppress the stale start.
 */
class DesktopAsyncPlayTest {
    private val mp3File = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")

    @Test
    fun playReturnsFastAndStarts() {
        Assume.assumeTrue("Music fixture is available", mp3File.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            assertTrue("Track loads", session.loadFile(mp3File, autoStart = true))
            session.stop()
            val t0 = System.currentTimeMillis()
            session.play()
            val playMs = System.currentTimeMillis() - t0
            assertTrue("play() returns at once (ms=$playMs)", playMs < 2_000)
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline && !NativeBridge.isEnginePlayingImpl()) {
                Thread.sleep(100)
            }
            assertTrue("Engine starts after async play", NativeBridge.isEnginePlayingImpl())
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }

    @Test
    fun stopSuppressesStalePlay() {
        Assume.assumeTrue("Music fixture is available", mp3File.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            assertTrue("Track loads", session.loadFile(mp3File, autoStart = true))
            session.play()
            session.stop()
            Thread.sleep(1_500)
            assertFalse(
                "Stale play does not resurrect playback",
                NativeBridge.isEnginePlayingImpl()
            )
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }
}
