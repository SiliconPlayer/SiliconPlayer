package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.WavExportWriter
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WavExportWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun pcm16WritesPlaceholderThenPatchesHeaderAndPayload() {
        val file = tempFolder.newFile("pcm16.wav")
        val writer = WavExportWriter(file, 48_000, AudioExportSampleFormat.Pcm16)

        val placeholder = file.readBytes()
        assertEquals(44, placeholder.size)
        assertEquals(36L, littleEndianUint32(placeholder, 4))
        assertEquals(0L, littleEndianUint32(placeholder, 40))

        writer.write(floatArrayOf(-1.0f, 1.0f, 0.0f, 0.5f), 2)
        assertEquals(2L, writer.frameCount)
        writer.finish()
        writer.finish()
        val wav = file.readBytes()
        writer.close()
        writer.close()

        assertArrayEquals(wav, file.readBytes())
        assertEquals(52, wav.size)
        assertEquals(44L, littleEndianUint32(wav, 4))
        assertFormatHeader(wav, 1, 192_000, 4, 16, 36)
        assertEquals(8L, littleEndianUint32(wav, 40))
        assertArrayEquals(
            bytes(0x00, 0x80, 0xFF, 0x7F, 0x00, 0x00, 0x00, 0x40),
            wav.copyOfRange(44, wav.size)
        )
    }

    @Test
    fun pcm24WritesLittleEndianSymmetricFullScaleSamples() {
        val file = tempFolder.newFile("pcm24.wav")
        WavExportWriter(file, 48_000, AudioExportSampleFormat.Pcm24).use { writer ->
            writer.write(floatArrayOf(-1.0f, 1.0f, -0.25f, 0.25f), 2)
            writer.finish()
        }

        val wav = file.readBytes()
        assertEquals(56, wav.size)
        assertEquals(48L, littleEndianUint32(wav, 4))
        assertFormatHeader(wav, 1, 288_000, 6, 24, 36)
        assertEquals(12L, littleEndianUint32(wav, 40))
        assertArrayEquals(
            bytes(
                0x00, 0x00, 0x80,
                0xFF, 0xFF, 0x7F,
                0x00, 0x00, 0xE0,
                0x00, 0x00, 0x20
            ),
            wav.copyOfRange(44, wav.size)
        )
    }

    @Test
    fun float32WritesFactChunkAndRawLittleEndianDataOnClose() {
        val file = tempFolder.newFile("float32.wav")
        val samples = floatArrayOf(0.0f, -0.5f, 1.0f, -1.0f)
        WavExportWriter(file, 48_000, AudioExportSampleFormat.Float32).use { writer ->
            writer.write(samples, 2)
            assertEquals(2L, writer.frameCount)
        }

        val wav = file.readBytes()
        assertEquals(72, wav.size)
        assertEquals(64L, littleEndianUint32(wav, 4))
        assertFormatHeader(wav, 3, 384_000, 8, 32, 48)
        assertAscii("fact", wav, 36)
        assertEquals(4L, littleEndianUint32(wav, 40))
        assertEquals(2L, littleEndianUint32(wav, 44))
        assertEquals(16L, littleEndianUint32(wav, 52))
        samples.forEachIndexed { index, sample ->
            assertEquals(sample.toRawBits(), littleEndianUint32(wav, 56 + index * 4).toInt())
        }
    }

    @Test
    fun validatesSampleRateAndStereoInput() {
        assertThrows(IllegalArgumentException::class.java) {
            WavExportWriter(tempFolder.newFile("zero-rate.wav"), 0, AudioExportSampleFormat.Pcm16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WavExportWriter(tempFolder.newFile("negative-rate.wav"), -1, AudioExportSampleFormat.Pcm16)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WavExportWriter(tempFolder.newFile("rate-overflow.wav"), Int.MAX_VALUE, AudioExportSampleFormat.Float32)
        }

        val writer = WavExportWriter(
            tempFolder.newFile("input-validation.wav"),
            48_000,
            AudioExportSampleFormat.Pcm16
        )
        assertThrows(IllegalArgumentException::class.java) { writer.write(FloatArray(0), -1) }
        assertThrows(IllegalArgumentException::class.java) { writer.write(FloatArray(3), 2) }
        assertEquals(0L, writer.frameCount)

        writer.write(floatArrayOf(0.25f, -0.25f, 1.0f, 1.0f), 1)
        assertEquals(1L, writer.frameCount)
        writer.finish()
        assertThrows(IllegalStateException::class.java) {
            writer.write(FloatArray(2), 1)
        }
        writer.close()
    }

    @Test
    fun rejectsRiffSizeOverflowWithoutLargeAllocations() {
        val writer = WavExportWriter(
            tempFolder.newFile("size-overflow.wav"),
            48_000,
            AudioExportSampleFormat.Pcm16
        )
        val maxFrames = (0xFFFF_FFFFL - 36L) / 4L
        try {
            setFrameCount(writer, maxFrames)
            assertThrows(IllegalArgumentException::class.java) {
                writer.write(FloatArray(2), 1)
            }
            assertEquals(maxFrames, writer.frameCount)

            setFrameCount(writer, Long.MAX_VALUE)
            assertThrows(IllegalArgumentException::class.java) {
                writer.write(FloatArray(2), 1)
            }
        } finally {
            setFrameCount(writer, 0L)
            writer.close()
        }
    }

    private fun assertFormatHeader(
        wav: ByteArray,
        formatTag: Int,
        byteRate: Int,
        blockAlign: Int,
        bitsPerSample: Int,
        dataOffset: Int
    ) {
        assertAscii("RIFF", wav, 0)
        assertAscii("WAVE", wav, 8)
        assertAscii("fmt ", wav, 12)
        assertEquals(16L, littleEndianUint32(wav, 16))
        assertEquals(formatTag, littleEndianUint16(wav, 20))
        assertEquals(2, littleEndianUint16(wav, 22))
        assertEquals(48_000L, littleEndianUint32(wav, 24))
        assertEquals(byteRate.toLong(), littleEndianUint32(wav, 28))
        assertEquals(blockAlign, littleEndianUint16(wav, 32))
        assertEquals(bitsPerSample, littleEndianUint16(wav, 34))
        assertAscii("data", wav, dataOffset)
    }

    private fun assertAscii(expected: String, bytes: ByteArray, offset: Int) {
        assertEquals(expected, String(bytes, offset, expected.length, Charsets.US_ASCII))
    }

    private fun littleEndianUint16(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun littleEndianUint32(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 4) {
            value = value or ((bytes[offset + index].toLong() and 0xFF) shl (index * 8))
        }
        return value
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun setFrameCount(writer: WavExportWriter, value: Long) {
        val field = WavExportWriter::class.java.getDeclaredField("frameCount")
        field.isAccessible = true
        field.setLong(writer, value)
    }
}
