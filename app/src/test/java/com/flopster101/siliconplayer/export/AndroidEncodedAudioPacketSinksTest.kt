package com.flopster101.siliconplayer.export

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidEncodedAudioPacketSinksTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun adtsHeaderDescribesAacLcFrame() {
        val header = buildAdtsHeader(payloadSize = 100, sampleRateHz = 48_000, channelCount = 2)
        assertArrayEquals(
            byteArrayOf(0xff.toByte(), 0xf1.toByte(), 0x4c, 0x80.toByte(), 0x0d, 0x7f, 0xfc.toByte()),
            header
        )
        val frameSize = ((header[3].toInt() and 3) shl 11) or
            ((header[4].toInt() and 0xff) shl 3) or
            (header[5].toInt() ushr 5)
        assertEquals(107, frameSize)
    }

    @Test(expected = IllegalArgumentException::class)
    fun adtsRejectsUnsupportedSampleRate() {
        buildAdtsHeader(payloadSize = 20, sampleRateHz = 10_000, channelCount = 2)
    }

    @Test
    fun id3UsesUtf8AndSyncSafeSizes() {
        val tag = requireNotNull(buildId3Tag(AudioExportMetadata(title = "Café")))
        assertArrayEquals(
            byteArrayOf(0x49, 0x44, 0x33, 4, 0, 0, 0, 0, 0, 16),
            tag.copyOfRange(0, 10)
        )
        assertArrayEquals("TIT2".toByteArray(), tag.copyOfRange(10, 14))
        assertArrayEquals(byteArrayOf(0, 0, 0, 6, 0, 0), tag.copyOfRange(14, 20))
        assertEquals(3, tag[20].toInt())
        assertEquals("Café", String(tag.copyOfRange(21, tag.size), StandardCharsets.UTF_8))
        assertNull(buildId3Tag(AudioExportMetadata()))
    }

    @Test
    fun id3CommentHasLanguageAndEmptyDescription() {
        val tag = requireNotNull(buildId3Tag(AudioExportMetadata(comment = "Note")))
        assertEquals("COMM", String(tag.copyOfRange(10, 14), StandardCharsets.US_ASCII))
        assertEquals(3, tag[20].toInt())
        assertEquals("und", String(tag.copyOfRange(21, 24), StandardCharsets.US_ASCII))
        assertEquals(0, tag[24].toInt())
        assertEquals("Note", String(tag.copyOfRange(25, tag.size), StandardCharsets.UTF_8))
    }

    @Test
    fun flacDefaultHeaderContainsStreamInfoAndVorbisComment() {
        val header = buildFlacStreamHeader(
            codecData = emptyList(),
            sampleRateHz = 48_000,
            metadata = AudioExportMetadata(title = "Café", artist = "Artist")
        )
        assertArrayEquals("fLaC".toByteArray(), header.copyOfRange(0, 4))
        assertEquals(0, header[4].toInt())
        assertEquals(34, header.readUnsigned24Test(5))
        assertEquals(0x10, header[8].toInt())
        assertEquals(0x00, header[9].toInt())
        assertEquals(0x84.toByte(), header[42])
        val packed = (18 until 26).fold(0L) { value, index ->
            (value shl 8) or (header[index].toInt() and 0xff).toLong()
        }
        assertEquals(48_000, packed ushr 44)
        assertTrue(String(header, StandardCharsets.UTF_8).contains("TITLE=Café"))
        assertTrue(String(header, StandardCharsets.UTF_8).contains("ARTIST=Artist"))
    }

    @Test
    fun flacExistingLastBlockIsContinuedByVorbisComment() {
        val streamInfo = buildFlacStreamHeader(emptyList(), 44_100, null)
        val existing = streamInfo.copyOfRange(0, 42).also { it[4] = 0x80.toByte() }
        val header = buildFlacStreamHeader(listOf(existing), 44_100, AudioExportMetadata())
        assertEquals(0, header[4].toInt())
        assertEquals(0x84.toByte(), header[42])
    }

    @Test
    fun flacAcceptsMp4CodecConfiguration() {
        val stream = buildFlacStreamHeader(emptyList(), 48_000, null)
        val config = "dfLa".toByteArray() + byteArrayOf(0, 0, 0, 0) +
            stream.copyOfRange(4, 42)
        val header = buildFlacStreamHeader(listOf(config), 48_000, AudioExportMetadata(title = "Song"))
        assertArrayEquals(stream.copyOfRange(0, 42), header.copyOfRange(0, 42))
        assertEquals(0x84.toByte(), header[42])
    }

    @Test(expected = IllegalArgumentException::class)
    fun flacRejectsTruncatedCodecMetadata() {
        buildFlacStreamHeader(listOf("fLaC".toByteArray() + byteArrayOf(0, 0, 0, 34)), 48_000, null)
    }

    @Test
    fun flacFinalizationWritesActualTotalSamples() {
        val file = tempFolder.newFile("duration.flac")
        file.writeBytes(buildFlacStreamHeader(emptyList(), 48_000, AudioExportMetadata(title = "Song")))
        updateFlacTotalSamples(file, 123_456_789L)
        val header = file.readBytes().copyOfRange(0, 42)
        val packed = (18 until 26).fold(0L) { value, index ->
            (value shl 8) or (header[index].toInt() and 0xff).toLong()
        }
        assertEquals(123_456_789L, packed and ((1L shl 36) - 1L))
        assertEquals(48_000L, packed ushr 44)
        assertEquals(1L, (packed ushr 41) and 7L)
        assertEquals(15L, (packed ushr 36) and 31L)
    }

    @Test
    fun rawAmrUsesStandardMagicHeader() {
        assertArrayEquals("#!AMR\n".toByteArray(), AMR_FILE_HEADER)
    }

    private fun ByteArray.readUnsigned24Test(offset: Int): Int =
        (this[offset].toInt() and 0xff shl 16) or
            (this[offset + 1].toInt() and 0xff shl 8) or
            (this[offset + 2].toInt() and 0xff)
}
