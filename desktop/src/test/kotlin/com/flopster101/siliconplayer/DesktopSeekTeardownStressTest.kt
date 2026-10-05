package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Rapid seeks keep the seek worker cycling while a concurrent release
 * tears the decoder down. Release must quiesce the worker (abort + serial
 * fencing) instead of deadlocking on it, and the engine must stay usable.
 */
class DesktopSeekTeardownStressTest {
    private val localFile = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")

    @Test
    fun concurrentSeeksAndReleaseDoNotDeadlock() {
        Assume.assumeTrue("Music fixture is available", localFile.exists())
        useSilentTestAudioOutput()
        val session = DesktopPlaybackSession()
        try {
            assertTrue("Track loads", session.loadFile(localFile, autoStart = true))
            val failure = AtomicReference<String?>(null)
            val done = CountDownLatch(3)
            repeat(2) { seeker ->
                Thread {
                    try {
                        var t = 5.0
                        repeat(25) {
                            session.seekTo(t)
                            t = (t + 37.0) % 210.0
                        }
                    } catch (t: Throwable) {
                        failure.compareAndSet(null, "seeker$seeker: $t")
                    } finally {
                        done.countDown()
                    }
                }.also { it.isDaemon = true; it.start() }
            }
            Thread {
                try {
                    Thread.sleep(300)
                    session.stop()
                } catch (t: Throwable) {
                    failure.compareAndSet(null, "release: $t")
                } finally {
                    done.countDown()
                }
            }.also { it.isDaemon = true; it.start() }
            assertTrue(
                "Seeks and release all finish",
                done.await(60, TimeUnit.SECONDS)
            )
            assertTrue(
                "No thread failed (${failure.get()})",
                failure.get() == null
            )
            assertTrue(
                "Engine loads the next track after the stress",
                session.loadFile(localFile, autoStart = false)
            )
        } finally {
            try { session.stop() } catch (_: Throwable) {}
        }
    }
}
