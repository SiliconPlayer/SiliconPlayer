package com.flopster101.siliconplayer.export

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidMediaCodecAacWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun convertFloatsToPcm16Le_clampsScalesAndWritesLittleEndian() {
        val samples = floatArrayOf(-2.0f, -1.0f, -0.5f, 0.0f, 0.5f, 1.0f, 2.0f)
        val buffer = pcmBuffer(samples.size * 2)

        writeFloatsToPcm16Le(buffer, samples, 0, samples.size)

        assertArrayEquals(
            byteArrayOf(
                0, -128,
                0, -128,
                0, -64,
                0, 0,
                0, 64,
                -1, 127,
                -1, 127
            ),
            buffer.array()
        )
    }

    @Test
    fun defaultMusicFileNameDoesNotOverwriteExistingFile() {
        val directory = tempFolder.newFolder("Music")
        val existing = directory.resolve("Song.m4a")
        existing.writeBytes(byteArrayOf(1))

        val result = uniqueAndroidExportFile(directory, "Song.m4a")

        assertEquals("Song (2).m4a", result.name)
        assertEquals(1, existing.readBytes().size)
    }

    @Test
    fun amrNbConversionDownmixesStereoToMono() {
        val samples = floatArrayOf(-1.0f, 1.0f, 0.5f, 0.5f, -0.5f, 0.0f)
        val buffer = pcmBuffer(6)

        writeFloatStereoToMonoPcm16Le(buffer, samples, 0, 3)

        assertArrayEquals(byteArrayOf(0, 0, 0, 64, 0, -32), buffer.array())
    }

    private fun pcmBuffer(size: Int): ByteBuffer =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
}
