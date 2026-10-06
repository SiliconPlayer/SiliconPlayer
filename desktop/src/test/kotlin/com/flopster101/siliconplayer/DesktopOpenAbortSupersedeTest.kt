package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stopping a stalled network open must kill the open itself, not just
 * return: a follow-up load must not serialize behind the orphaned probe.
 */
class DesktopOpenAbortSupersedeTest {

    private class TarpitServer : AutoCloseable {
        private val server = ServerSocket(0, 50)
        private val clients = Collections.synchronizedList(mutableListOf<Socket>())
        private val running = AtomicBoolean(true)
        private val acceptThread = Thread {
            while (running.get()) {
                try {
                    val client = server.accept()
                    clients.add(client)
                    while (running.get() && !client.isClosed) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }

        val url: String get() = "http://127.0.0.1:${server.localPort}/stalled.mp3"

        override fun close() {
            running.set(false)
            runCatching { server.close() }
            synchronized(clients) { clients.forEach { runCatching { it.close() } } }
            acceptThread.interrupt()
        }
    }

    @Test
    fun loadAfterStopDoesNotWaitOnStalledOpen() {
        val localFile = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")
        Assume.assumeTrue("Music fixture is available", localFile.exists())
        useSilentTestAudioOutput()
        TarpitServer().use { tarpit ->
            val session = DesktopPlaybackSession()
            try {
                val loaderDone = CountDownLatch(1)
                Thread {
                    try {
                        session.loadSource(tarpit.url, autoStart = false)
                    } catch (_: Throwable) {
                    } finally {
                        loaderDone.countDown()
                    }
                }.also { it.isDaemon = true; it.start() }

                Thread.sleep(2_000)
                session.stop()

                // The stopped open must be dead: loading a local file right
                // after must complete instead of queueing behind the tarpit.
                val t0 = System.currentTimeMillis()
                val loaded = session.loadFile(localFile, autoStart = false)
                val loadMs = System.currentTimeMillis() - t0
                assertTrue("Follow-up load completes after stop", loaded)
                assertTrue("Follow-up load is not stuck behind the stalled open (ms=$loadMs)",
                    loadMs < 15_000)
                loaderDone.await(60, TimeUnit.SECONDS)
            } finally {
                try { session.stop() } catch (_: Throwable) {}
            }
        }
    }

    @Test
    fun stopAfterStackedStallsDoesNotGrindTimeouts() {
        useSilentTestAudioOutput()
        TarpitServer().use { tarpit ->
            val session = DesktopPlaybackSession()
            try {
                val loadersDone = CountDownLatch(2)
                repeat(2) {
                    Thread {
                        try {
                            session.loadSource(tarpit.url, autoStart = false)
                        } catch (_: Throwable) {
                        } finally {
                            loadersDone.countDown()
                        }
                    }.also { it.isDaemon = true; it.start() }
                    Thread.sleep(500)
                }
                Thread.sleep(2_000)
                // The second load queued behind the first; stopping now must
                // drop the queued open instead of grinding out its network
                // timeout with the abort flag cleared.
                val t0 = System.currentTimeMillis()
                session.stop()
                val stopMs = System.currentTimeMillis() - t0
                assertTrue("Stop after stacked stalls returns promptly (ms=$stopMs)",
                    stopMs < 12_000)
                loadersDone.await(60, TimeUnit.SECONDS)
            } finally {
                try { session.stop() } catch (_: Throwable) {}
            }
        }
    }

    @Test
    fun supersedingLoadAbortsStalledOpen() {
        val localFile = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")
        Assume.assumeTrue("Music fixture is available", localFile.exists())
        useSilentTestAudioOutput()
        TarpitServer().use { tarpit ->
            val session = DesktopPlaybackSession()
            try {
                val loaderDone = CountDownLatch(1)
                Thread {
                    try {
                        session.loadSource(tarpit.url, autoStart = false)
                    } catch (_: Throwable) {
                    } finally {
                        loaderDone.countDown()
                    }
                }.also { it.isDaemon = true; it.start() }

                Thread.sleep(2_000)
                // No stop: loading the next track directly must abort the
                // stalled open and install the local track, not serialize
                // behind the orphan or drop the request.
                val t0 = System.currentTimeMillis()
                val loaded = session.loadFile(localFile, autoStart = false)
                val loadMs = System.currentTimeMillis() - t0
                assertTrue("Superseding load completes", loaded)
                assertTrue("Superseding load aborts the stalled open (ms=$loadMs)",
                    loadMs < 10_000)
                assertTrue(
                    "Local track is installed, not the stalled URL",
                    NativeBridge.getTrackTitle().isNotBlank()
                )
                loaderDone.await(60, TimeUnit.SECONDS)
            } finally {
                try { session.stop() } catch (_: Throwable) {}
            }
        }
    }
}
