package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Track/subtune metadata reads must stay correct and never wedge while
 * decoder work (seeks, subtune switches) holds the engine lock: getters
 * serve the last known values instead of blocking.
 */
class DesktopMetadataNonblockingTest {
    private val mp3File = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")
    private val sidFile = File("/home/flopster101/Music/SyncedMusic/Chips/A-F/FamiCommodore.sid")

    @Test
    fun metadataReadsStayCorrectUnderContention() {
        Assume.assumeTrue("Music fixture is available", mp3File.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            assertTrue("Track loads", session.loadFile(mp3File, autoStart = true))
            val title = NativeBridge.getTrackTitle()
            val decoderName = NativeBridge.getCurrentDecoderNameImpl()
            assertTrue("Title reads live (title='$title')", title.isNotBlank())
            assertTrue("Decoder reads live (decoder='$decoderName')", decoderName.isNotBlank())
            assertTrue("Sample rate sane", NativeBridge.getTrackSampleRate() > 0)
            val count = NativeBridge.getSubtuneCount()
            assertTrue("Subtune count sane", count >= 1)
            val failure = AtomicReference<String?>(null)
            val done = CountDownLatch(4)
            repeat(2) {
                Thread {
                    try {
                        repeat(300) {
                            NativeBridge.getTrackTitle()
                            NativeBridge.getTrackArtist()
                            NativeBridge.getTrackAlbum()
                            NativeBridge.getCurrentDecoderNameImpl()
                            NativeBridge.getSubtuneCount()
                            NativeBridge.getSubtuneTitle(0)
                            NativeBridge.getRepeatModeCapabilities()
                            NativeBridge.getPlaybackCapabilities()
                        }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, "reader: $t")
                    } finally {
                        done.countDown()
                    }
                }.also { it.isDaemon = true; it.start() }
            }
            Thread {
                try {
                    var t = 5.0
                    repeat(40) {
                        session.seekTo(t)
                        t = (t + 37.0) % 210.0
                    }
                } catch (t: Throwable) {
                    failure.compareAndSet(null, "seeker: $t")
                } finally {
                    done.countDown()
                }
            }.also { it.isDaemon = true; it.start() }
            Thread {
                try {
                    Thread.sleep(500)
                    session.stop()
                } catch (t: Throwable) {
                    failure.compareAndSet(null, "release: $t")
                } finally {
                    done.countDown()
                }
            }.also { it.isDaemon = true; it.start() }
            assertTrue("Readers, seeker and release all finish", done.await(60, TimeUnit.SECONDS))
            assertTrue("No thread failed (${failure.get()})", failure.get() == null)
            // Same values still read after the churn.
            assertTrue("Engine loads the next track", session.loadFile(mp3File, autoStart = true))
            assertEquals("Title stable after churn", title, NativeBridge.getTrackTitle())
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }

    @Test
    fun sidSubtuneEntriesRead() {
        Assume.assumeTrue("SID fixture is available", sidFile.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            assertTrue("SID loads", session.loadFile(sidFile, autoStart = true))
            val count = NativeBridge.getSubtuneCount()
            assertTrue("SID exposes subtunes (count=$count)", count >= 1)
            repeat(count.coerceAtMost(4)) { index ->
                NativeBridge.getSubtuneTitle(index)
                NativeBridge.getSubtuneArtist(index)
                NativeBridge.getSubtuneDurationSeconds(index)
            }
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }
}
