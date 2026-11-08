package com.flopster101.siliconplayer.export

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidOggOpusPacketSinkTest {

    @Test
    fun opusHeadHasExactVersionChannelsPreSkipRateGainAndMapping() {
        val expected = "OpusHead".toByteArray(Charsets.US_ASCII) + byteArrayOf(
            1,
            2,
            0x38, 0x01,
            0x44, 0xac.toByte(), 0, 0,
            0, 0,
            0
        )

        assertArrayEquals(expected, buildOpusHead(44_100, 312))
    }

    @Test
    fun oggCrcMatchesKnownVectorAndPageRoundTrip() {
        assertEquals(0x89a1897f.toInt(), oggCrc32("123456789".toByteArray(Charsets.US_ASCII)))
        val page = buildOggPage(
            headerType = 2,
            granulePosition = 0x0102030405060708,
            serialNumber = 0x10203040,
            pageSequence = 7,
            packets = listOf(byteArrayOf(9, 8, 7))
        )
        val storedCrc = (0 until 4).fold(0) { value, index ->
            value or ((page[22 + index].toInt() and 0xff) shl (index * 8))
        }
        page.fill(0, 22, 26)

        assertEquals(storedCrc, oggCrc32(page))
    }

    @Test
    fun lacingHandlesPacketSizeBoundaries() {
        listOf(
            254 to byteArrayOf(254.toByte()),
            255 to byteArrayOf(255.toByte(), 0),
            256 to byteArrayOf(255.toByte(), 1)
        ).forEach { (size, expectedLacing) ->
            val packet = ByteArray(size) { it.toByte() }
            val page = buildOggPage(0, 0, 1, 2, listOf(packet))

            assertArrayEquals(expectedLacing, oggPacketLacing(size))
            assertEquals(expectedLacing.size, page[26].toInt() and 0xff)
            assertArrayEquals(expectedLacing, page.copyOfRange(27, 27 + expectedLacing.size))
            assertArrayEquals(packet, page.copyOfRange(27 + expectedLacing.size, page.size))
        }
    }

    @Test
    fun pageBuilderPacksMultiplePacketsIntoOneSegmentTable() {
        val first = ByteArray(254) { 1 }
        val second = ByteArray(256) { 2 }
        val page = buildOggPage(4, 960, 10, 3, listOf(first, second))

        assertEquals(3, page[26].toInt() and 0xff)
        assertArrayEquals(byteArrayOf(254.toByte(), 255.toByte(), 1), page.copyOfRange(27, 30))
        assertArrayEquals(first + second, page.copyOfRange(30, page.size))
        assertEquals(4, page[5].toInt())
        assertEquals(960L, readLittleEndian64(page, 6))
    }

    @Test
    fun durationUsesTocConfigurationAt48kHz() {
        listOf(
            0 to 480,
            3 to 2_880,
            4 to 480,
            7 to 2_880,
            11 to 2_880,
            12 to 480,
            13 to 960,
            14 to 480,
            15 to 960,
            16 to 120,
            17 to 240,
            18 to 480,
            19 to 960,
            20 to 120,
            27 to 960,
            28 to 120,
            31 to 960
        ).forEach { (config, expected) ->
            val packet = byteArrayOf(toc(config, code = 0), 0)
            assertEquals("Opus config $config", expected, opusPacketDurationSamples(packet))
        }
    }

    @Test
    fun durationHandlesAllFrameCountCodesPaddingAndExtendedLengths() {
        assertEquals(120, opusPacketDurationSamples(byteArrayOf(toc(16, 0), 0)))
        assertEquals(240, opusPacketDurationSamples(byteArrayOf(toc(16, 1), 0, 0)))
        assertEquals(240, opusPacketDurationSamples(byteArrayOf(toc(16, 2), 1, 0)))
        assertEquals(360, opusPacketDurationSamples(byteArrayOf(toc(16, 3), 3, 0, 0, 0)))
        assertEquals(
            360,
            opusPacketDurationSamples(byteArrayOf(toc(16, 3), 0x43, 1, 0, 0))
        )
        assertEquals(
            240,
            opusPacketDurationSamples(byteArrayOf(toc(16, 3), 0x82.toByte(), 1, 0, 0, 0))
        )
        assertEquals(
            5_760,
            opusPacketDurationSamples(byteArrayOf(toc(3, 3), 2, 0, 0))
        )

        val extendedLengthPacket = byteArrayOf(toc(16, 2), 252.toByte(), 0) +
            ByteArray(252) + byteArrayOf(1, 2)
        assertEquals(240, opusPacketDurationSamples(extendedLengthPacket))
    }

    @Test
    fun opusTagsContainExactUtf8CommentsInMetadataOrder() {
        val metadata = AudioExportMetadata(
            title = "Tïtle 🎵",
            artist = "Artist",
            album = "Album",
            composer = "Composer",
            genre = "Genre",
            date = "2025-03-08",
            copyright = "Copyright",
            comment = "Nöte"
        )
        val comments = listOf(
            "TITLE=${metadata.title}",
            "ARTIST=${metadata.artist}",
            "ALBUM=${metadata.album}",
            "COMPOSER=${metadata.composer}",
            "GENRE=${metadata.genre}",
            "DATE=${metadata.date}",
            "COPYRIGHT=${metadata.copyright}",
            "COMMENT=${metadata.comment}"
        ).map { it.toByteArray(Charsets.UTF_8) }
        val vendor = "SiliconPlayer".toByteArray(Charsets.UTF_8)
        val expected = ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray(Charsets.US_ASCII))
            writeLittleEndian32(vendor.size)
            write(vendor)
            writeLittleEndian32(comments.size)
            comments.forEach {
                writeLittleEndian32(it.size)
                write(it)
            }
        }.toByteArray()

        assertArrayEquals(expected, buildOpusTags(metadata))
    }

    @Test
    fun opusTagsOmitNullAndBlankComments() {
        val metadata = AudioExportMetadata(
            title = " ",
            artist = "\t",
            album = null,
            composer = "\n",
            comment = "Nöte"
        )

        assertEquals(
            listOf("COMMENT=Nöte"),
            opusVorbisComments(metadata).map { String(it, Charsets.UTF_8) }
        )
        assertEquals(0, opusVorbisComments(null).size)
    }

    @Test
    fun granuleAccountingIncludesPreSkipAndEveryPacketDuration() {
        val packets = listOf(
            byteArrayOf(toc(16, 0), 0),
            byteArrayOf(toc(20, 1), 0, 0),
            byteArrayOf(toc(0, 0), 0),
            byteArrayOf(toc(3, 3), 2, 0, 0)
        )
        var granulePosition = 312L
        val positions = packets.map { packet ->
            granulePosition = advanceOpusGranulePosition(
                granulePosition,
                opusPacketDurationSamples(packet)
            )
            granulePosition
        }

        assertEquals(listOf(432L, 672L, 1_152L, 6_912L), positions)
    }

    @Test
    fun malformedPacketsAreRejected() {
        val malformed = listOf(
            byteArrayOf(),
            byteArrayOf(toc(16, 1), 0),
            byteArrayOf(toc(16, 2)),
            byteArrayOf(toc(16, 2), 2, 0),
            byteArrayOf(toc(16, 3)),
            byteArrayOf(toc(16, 3), 0),
            byteArrayOf(toc(3, 3), 3, 0, 0, 0),
            byteArrayOf(toc(16, 3), 0x82.toByte()),
            byteArrayOf(toc(16, 3), 0x82.toByte(), 2, 0),
            byteArrayOf(toc(16, 3), 0x43, 1, 0),
            byteArrayOf(toc(16, 3), 3, 0),
            ByteArray(1_277) { toc(16, 0) }
        )

        malformed.forEach { packet ->
            assertThrows(IllegalArgumentException::class.java) {
                opusPacketDurationSamples(packet)
            }
        }
    }

    private fun toc(config: Int, code: Int): Byte = ((config shl 3) or code).toByte()

    private fun readLittleEndian64(bytes: ByteArray, offset: Int): Long {
        return (0 until 8).fold(0L) { value, index ->
            value or ((bytes[offset + index].toLong() and 0xff) shl (index * 8))
        }
    }

    private fun ByteArrayOutputStream.writeLittleEndian32(value: Int) {
        repeat(4) { index -> write(value ushr (index * 8)) }
    }
}
