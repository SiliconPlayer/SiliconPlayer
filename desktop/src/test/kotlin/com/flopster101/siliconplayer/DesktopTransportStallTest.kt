package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.math.abs

/**
 * A stream that dies mid-track is not EOF: playback must stall with the
 * position frozen instead of firing a fake natural end (restart/stop).
 */
class DesktopTransportStallTest {

    // Serves a valid prefix then resets the connection, promising a full
    // length so the truncation reads as an error rather than clean EOF.
    private class DyingServer(private val prefix: ByteArray, private val fullLength: Long) : AutoCloseable {
        private val server = ServerSocket(0, 50)
        private val clients = Collections.synchronizedList(mutableListOf<Socket>())
        val url: String get() = "http://127.0.0.1:${server.localPort}/dying.mp3"
        private val acceptThread = Thread {
            try {
                val client = server.accept()
                clients.add(client)
                client.soTimeout = 10_000
                val input = client.getInputStream()
                val header = ByteArray(1024)
                // Consume the HTTP request head.
                var total = 0
                while (total < header.size) {
                    val read = try { input.read(header, total, header.size - total) } catch (_: Exception) { -1 }
                    if (read <= 0) break
                    total += read
                    if (total >= 4 && String(header, 0, total).contains("\r\n\r\n")) break
                }
                val out = client.getOutputStream()
                val responseHead =
                    "HTTP/1.1 200 OK\r\nContent-Length: $fullLength\r\nContent-Type: audio/mpeg\r\nConnection: close\r\n\r\n"
                out.write(responseHead.toByteArray())
                out.write(prefix)
                out.flush()
                // Reset so reads fail instead of looking like clean EOF.
                client.setSoLinger(true, 0)
            } catch (_: Exception) {
            }
        }.also { it.isDaemon = true; it.start() }

        override fun close() {
            runCatching { server.close() }
            synchronized(clients) { clients.forEach { runCatching { it.close() } } }
            acceptThread.interrupt()
        }
    }

    @Test
    fun midStreamDeathStallsInsteadOfEnding() {
        val fixture = File("/home/flopster101/Music/SyncedMusic/RushJet1 - FDX.mp3")
        Assume.assumeTrue("Music fixture is available", fixture.exists())
        useSilentTestAudioOutput()
        val bytes = fixture.readBytes()
        // ~2s of audio at typical bitrates: plays out, then reads die.
        val prefix = bytes.copyOf(minOf(bytes.size, 32 * 1024))
        DyingServer(prefix, bytes.size.toLong()).use { server ->
            val session = DesktopPlaybackSession()
            try {
                assertTrue("Stream starts", session.loadSource(server.url, autoStart = true))
                // Let the served prefix play out and reads start failing.
                Thread.sleep(7_000)
                assertTrue(
                    "Mid-track death stalls instead of ending",
                    session.isPlaying
                )
                val first = NativeBridge.getPosition()
                Thread.sleep(1_500)
                val second = NativeBridge.getPosition()
                assertTrue(
                    "Stalled position is frozen (first=$first second=$second)",
                    abs(first - second) < 0.05
                )
                assertTrue(
                    "Stall is mid-track, not at the end (pos=$second)",
                    second + 2.0 < NativeBridge.getDuration()
                )
            } finally {
                try { session.stop() } catch (_: Throwable) {}
            }
        }
    }
}
