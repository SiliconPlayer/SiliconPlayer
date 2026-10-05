package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end SMB streaming check covering the FFmpeg custom AVIO path.
 *
 * Tests are skipped unless `SILICONPLAYER_SMB_TEST_URI` points at a reachable `smb://` file URI.
 */
class DesktopSmbStreamingTest {

    private fun testSmbUri(): String? =
        System.getenv("SILICONPLAYER_SMB_TEST_URI")?.takeIf { it.isNotBlank() }

    private fun ensureCredentialStoreReady() {
        if (NetworkCredentialStore.preferencesProvider == null) {
            NetworkCredentialStore.preferencesProvider = { DesktopAppPreferences("smb_streaming_test") }
        }
    }

    @Test
    fun testSmbAvioHandleRandomReads() {
        val uri = testSmbUri() ?: return
        ensureCredentialStoreReady()

        val handleId = NativeBridge.openSmbAvioHandle(uri)
        assertTrue("SMB AVIO handle should open", handleId > 0L)
        try {
            val sizeBytes = NativeBridge.getSmbAvioHandleSize(handleId)
            assertTrue("SMB file size should be positive", sizeBytes > 0L)

            val head = ByteArray(4096)
            val headRead = NativeBridge.readSmbAvioHandle(handleId, 0L, head, head.size)
            assertTrue("SMB head read should return bytes", headRead > 0)

            val tailOffset = (sizeBytes - head.size).coerceAtLeast(0L)
            val tail = ByteArray(head.size)
            val tailRead = NativeBridge.readSmbAvioHandle(handleId, tailOffset, tail, tail.size)
            assertTrue("SMB seek read should return bytes", tailRead > 0)

            val secondHead = ByteArray(head.size)
            val secondRead = NativeBridge.readSmbAvioHandle(handleId, 0L, secondHead, secondHead.size)
            assertEquals("Repeated reads should return the same length", headRead, secondRead)
            assertTrue(
                "Repeated reads should return the same bytes",
                head.copyOf(headRead) contentEquals secondHead.copyOf(secondRead)
            )
        } finally {
            NativeBridge.closeSmbAvioHandle(handleId)
        }
    }

    @Test
    fun testSmbStreamingPlaysThroughFfmpeg() {
        val uri = testSmbUri() ?: return
        ensureCredentialStoreReady()
        useSilentTestAudioOutput()

        try {
            NativeBridge.loadAudioWithDecoder(uri, "FFmpeg")
            assertEquals("FFmpeg should be the active decoder", "FFmpeg", NativeBridge.getCurrentDecoderName())
            val duration = NativeBridge.getDuration()
            assertTrue("Duration should be finite and > 0.0", !duration.isNaN() && duration > 0.0)

            NativeBridge.startEngineNative()
            val deadline = System.currentTimeMillis() + 10_000
            while (!NativeBridge.isEnginePlaying() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            assertTrue("Engine should report playing over SMB", NativeBridge.isEnginePlaying())
            NativeBridge.stopEngineNative()
        } finally {
            NativeBridge.releaseCurrentDecoder()
        }
    }
}
