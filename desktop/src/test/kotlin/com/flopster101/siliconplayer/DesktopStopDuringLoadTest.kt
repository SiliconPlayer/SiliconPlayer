package com.flopster101.siliconplayer

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A stalled HTTP server stands in for a slow remote open (SMB song that has
 * not finished loading, UADE probing a foreign file): the decoder open blocks
 * indefinitely, and tapping stop must still return immediately.
 */
class DesktopStopDuringLoadTest {

    private class TarpitServer : AutoCloseable {
        private val server = ServerSocket(0, 50)
        private val clients = Collections.synchronizedList(mutableListOf<Socket>())
        private val running = AtomicBoolean(true)
        private val acceptThread = Thread {
            while (running.get()) {
                try {
                    val client = server.accept()
                    clients.add(client)
                    // Hold the connection open without sending a single byte.
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
    fun stopReturnsWhileDecoderOpenIsStalled() {
        useSilentTestAudioOutput()
        TarpitServer().use { tarpit ->
            val loadDone = CountDownLatch(1)
            val loader = Thread {
                try {
                    NativeBridge.loadAudio(tarpit.url)
                } catch (_: Throwable) {
                } finally {
                    loadDone.countDown()
                }
            }.also { it.isDaemon = true; it.start() }

            // Give the loader time to reach the stalled network open.
            Thread.sleep(2_000)

            val stopDone = CountDownLatch(1)
            val stopThread = Thread {
                try {
                    NativeBridge.stopEngineWithPauseResumeFadeNative()
                } catch (_: Throwable) {
                } finally {
                    stopDone.countDown()
                }
            }.also { it.isDaemon = true; it.start() }

            val stopReturned = stopDone.await(10, TimeUnit.SECONDS)
            // Release the stalled open so the loader thread can always exit.
            tarpit.close()
            loadDone.await(60, TimeUnit.SECONDS)
            try { NativeBridge.releaseCurrentDecoderNative() } catch (_: Throwable) {}

            assertTrue("stop must return while a decoder open is stalled", stopReturned)
        }
    }
}
