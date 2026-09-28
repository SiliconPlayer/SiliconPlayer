package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopTrackSwitchTest {

    private fun writeSineWav(file: File, seconds: Int) {
        val rate = 22050
        val samples = rate * seconds
        val data = ByteArray(samples * 2)
        for (i in 0 until samples) {
            val value = (kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * i / rate) * 30000).toInt()
            data[i * 2] = (value and 0xFF).toByte()
            data[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        val header = ByteArray(44)
        fun putString(offset: Int, text: String) {
            text.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        }
        fun putInt(offset: Int, value: Int) {
            for (b in 0 until 4) header[offset + b] = ((value shr (8 * b)) and 0xFF).toByte()
        }
        fun putShort(offset: Int, value: Int) {
            header[offset] = (value and 0xFF).toByte()
            header[offset + 1] = ((value shr 8) and 0xFF).toByte()
        }
        putString(0, "RIFF")
        putInt(4, 36 + data.size)
        putString(8, "WAVE")
        putString(12, "fmt ")
        putInt(16, 16)
        putShort(20, 1)
        putShort(22, 1)
        putInt(24, rate)
        putInt(28, rate * 2)
        putShort(32, 2)
        putShort(34, 16)
        putString(36, "data")
        putInt(40, data.size)
        file.writeBytes(header)
        file.appendBytes(data)
    }

    private fun awaitCondition(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(25)
        }
        return condition()
    }

    @Test
    fun switchUnderPlaybackReplacesTheTrackAndKeepsTheEngineRunning() {
        val dir = File(System.getProperty("java.io.tmpdir"), "siliconplayer-switch-${System.nanoTime()}")
        dir.mkdirs()
        val first = File(dir, "first.wav")
        val second = File(dir, "second.wav")
        try {
            writeSineWav(first, 4)
            writeSineWav(second, 6)
            val session = DesktopPlaybackSession()
            try {
                assertTrue("First track loads", session.loadFile(first))
                assertTrue(
                    "Engine starts on the first track",
                    awaitCondition(3_000) { session.isPlaying }
                )

                // The switch tears the output device down before the load; the
                // engine must come back up on the new source, not stay muted.
                assertTrue("Second track loads", session.loadFile(second))
                assertEquals("Switch adopts the new track", second.absolutePath, session.currentFile?.absolutePath)
                assertTrue(
                    "Engine plays the new track after the switch",
                    awaitCondition(3_000) { session.isPlaying }
                )
                assertTrue(
                    "New track has its own duration (was ${session.durationSeconds})",
                    session.durationSeconds > 4.5
                )
            } finally {
                session.dispose()
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
