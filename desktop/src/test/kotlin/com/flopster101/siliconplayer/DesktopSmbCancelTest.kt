package com.flopster101.siliconplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The stop paths cancel in-flight remote reads; a stalled transport must
 * therefore unblock readers promptly instead of hanging until network
 * timeout. Uses a fake stalling transport (no network required).
 */
class DesktopSmbCancelTest {

    private class StallingTransport : ProgressiveRandomAccessTransport {
        override val sourceId: String = "cancel-test-stall"
        override val sizeBytes: Long = 1024L * 1024L
        private val cancelled = AtomicBoolean(false)

        override fun readAt(offset: Long, buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            while (!cancelled.get()) {
                Thread.sleep(50)
            }
            throw IOException("transport cancelled")
        }

        override fun cancel() {
            cancelled.set(true)
        }

        override fun close() {
            cancelled.set(true)
        }
    }

    @Test
    fun cancelUnblocksStalledRead() {
        val cacheDir = Files.createTempDirectory("smb-cancel-test").toFile()
        try {
            val cache = ProgressiveRandomAccessCache(
                cacheDir = cacheDir,
                transport = StallingTransport(),
                chunkSizeBytes = 64 * 1024,
                prefetchTransportFactory = null
            )
            val readerDone = CountDownLatch(1)
            val readerFailed = AtomicBoolean(false)
            val reader = Thread {
                try {
                    cache.readAt(0L, ByteArray(4096), 4096)
                } catch (_: Throwable) {
                    readerFailed.set(true)
                } finally {
                    readerDone.countDown()
                }
            }.also { it.isDaemon = true; it.start() }

            Thread.sleep(500)
            assertFalse("reader must still be stalled before cancel", readerDone.await(100, TimeUnit.MILLISECONDS))
            cache.cancel()
            assertTrue("cancel must unblock the stalled reader", readerDone.await(10, TimeUnit.SECONDS))
            assertTrue("stalled read must fail after cancel", readerFailed.get())
            runCatching { cache.close() }
        } finally {
            runCatching { cacheDir.deleteRecursively() }
        }
    }
}
