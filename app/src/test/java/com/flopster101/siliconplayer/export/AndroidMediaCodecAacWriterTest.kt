package com.flopster101.siliconplayer.export

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
        val output = ByteArray(samples.size * 2)

        convertFloatsToPcm16Le(samples, 0, samples.size, output)

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
            output
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
}
