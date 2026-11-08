package com.flopster101.siliconplayer.export

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidWebmMetadataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun writesEveryMetadataValueWithExactEbmlBytesAndUpdatesFiniteSegmentSize() {
        val prefix = element(EBML, element(EBML_VERSION, byteArrayOf(1)))
        val payload = element(INFO, element(TIMECODE_SCALE, byteArrayOf(0x0f, 0x42, 0x40)))
        val suffix = element(CRC32, byteArrayOf(1, 2, 3, 4))
        val file = webmFile("all-tags.webm", prefix + segment(payload, sizeLength = 8) + suffix)
        val metadata = AudioExportMetadata(
            title = "Tïtle",
            artist = "Artist",
            album = "Album",
            composer = "Composer",
            genre = "Genre",
            date = "2025-03-08",
            copyright = "Copyright",
            comment = "Comment"
        )

        writeAndroidWebmMetadata(file, metadata)

        val expectedTags = tags(
            "TITLE" to metadata.title!!,
            "ARTIST" to metadata.artist!!,
            "ALBUM" to metadata.album!!,
            "COMPOSER" to metadata.composer!!,
            "GENRE" to metadata.genre!!,
            "DATE" to metadata.date!!,
            "COPYRIGHT" to metadata.copyright!!,
            "COMMENT" to metadata.comment!!
        )
        val expected = prefix + segment(payload + expectedTags, sizeLength = 8) + suffix
        assertArrayEquals(expected, file.readBytes())
    }

    @Test
    fun preservesUnknownSegmentSizeAndOmitsBlankAndNullTags() {
        val prefix = element(EBML, element(EBML_VERSION, byteArrayOf(1)))
        val payload = element(INFO, byteArrayOf(9, 8, 7))
        val original = prefix + unknownSegment(payload, sizeLength = 8)
        val file = webmFile("unknown-size.webm", original)

        writeAndroidWebmMetadata(
            file,
            AudioExportMetadata(title = "Song", artist = " ", album = null, comment = "Nöte")
        )

        val expectedTags = tags("TITLE" to "Song", "COMMENT" to "Nöte")
        assertArrayEquals(original + expectedTags, file.readBytes())
    }

    @Test
    fun expandsFiniteSegmentSizeWhenOriginalVintIsTooSmall() {
        val payload = ByteArray(100) { it.toByte() }
        val suffix = element(CRC32, byteArrayOf(4, 3, 2, 1))
        val file = webmFile("growing-size.webm", segment(payload) + suffix)
        val title = "X".repeat(100)

        writeAndroidWebmMetadata(file, AudioExportMetadata(title = title))

        assertArrayEquals(segment(payload + tags("TITLE" to title)) + suffix, file.readBytes())
    }

    @Test
    fun allBlankMetadataIsANoOpEvenForMalformedInput() {
        val original = byteArrayOf(0, 0x18, 0x53)
        val file = webmFile("empty.webm", original)

        writeAndroidWebmMetadata(file, AudioExportMetadata(title = " ", artist = "\n", comment = null))

        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun rejectsMalformedOrTruncatedEbmlWithoutChangingFile() {
        val malformedFiles = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            byteArrayOf(0x1a, 0x45),
            SEGMENT,
            SEGMENT + byteArrayOf(0),
            SEGMENT + byteArrayOf(0x40),
            SEGMENT + byteArrayOf(0x85.toByte(), 1, 2),
            element(EBML, byteArrayOf(1)),
            segment(ByteArray(0)) + byteArrayOf(0x1a),
            EBML + byteArrayOf(0xff.toByte()),
            segment(ByteArray(0)) + segment(ByteArray(0))
        )

        malformedFiles.forEachIndexed { index, original ->
            val file = webmFile("malformed-$index.webm", original)
            assertThrows(IllegalArgumentException::class.java) {
                writeAndroidWebmMetadata(file, AudioExportMetadata(title = "Song"))
            }
            assertArrayEquals(original, file.readBytes())
        }
        assertEquals(malformedFiles.size, tempFolder.root.listFiles()?.size)
    }

    private fun webmFile(name: String, bytes: ByteArray): File {
        return tempFolder.newFile(name).apply { writeBytes(bytes) }
    }

    private fun tags(vararg values: Pair<String, String>): ByteArray {
        return element(
            TAGS,
            *values.map { (name, value) ->
                element(
                    TAG,
                    element(
                        SIMPLE_TAG,
                        element(TAG_NAME, name.toByteArray(Charsets.UTF_8)),
                        element(TAG_STRING, value.toByteArray(Charsets.UTF_8))
                    )
                )
            }.toTypedArray()
        )
    }

    private fun segment(payload: ByteArray, sizeLength: Int = 1): ByteArray =
        sizedElement(SEGMENT, payload, sizeLength)

    private fun unknownSegment(payload: ByteArray, sizeLength: Int): ByteArray =
        SEGMENT + unknownSize(sizeLength) + payload

    private fun element(id: ByteArray, vararg payloads: ByteArray): ByteArray {
        val payload = ByteArrayOutputStream()
        payloads.forEach(payload::write)
        return sizedElement(id, payload.toByteArray(), 1)
    }

    private fun sizedElement(id: ByteArray, payload: ByteArray, minimumSizeLength: Int): ByteArray {
        return id + encodeSize(payload.size.toLong(), minimumSizeLength) + payload
    }

    private fun encodeSize(value: Long, minimumLength: Int): ByteArray {
        for (length in minimumLength..8) {
            val unknown = (1L shl (length * 7)) - 1L
            if (value >= unknown) continue
            val bytes = ByteArray(length)
            var remaining = value
            for (index in length - 1 downTo 0) {
                bytes[index] = (remaining and 0xffL).toByte()
                remaining = remaining ushr 8
            }
            bytes[0] = (bytes[0].toInt() or (0x80 ushr (length - 1))).toByte()
            return bytes
        }
        throw IllegalArgumentException("Test element is too large.")
    }

    private fun unknownSize(length: Int): ByteArray {
        val bytes = ByteArray(length) { 0xff.toByte() }
        bytes[0] = ((0xff ushr length) or (0x80 ushr (length - 1))).toByte()
        return bytes
    }

    private companion object {
        val EBML = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        val EBML_VERSION = byteArrayOf(0x42, 0x86.toByte())
        val SEGMENT = byteArrayOf(0x18, 0x53, 0x80.toByte(), 0x67)
        val INFO = byteArrayOf(0x15, 0x49, 0xa9.toByte(), 0x66)
        val TIMECODE_SCALE = byteArrayOf(0x2a, 0xd7.toByte(), 0xb1.toByte())
        val CRC32 = byteArrayOf(0x1c, 0x53, 0xbb.toByte(), 0x6b)
        val TAGS = byteArrayOf(0x12, 0x54, 0xc3.toByte(), 0x67)
        val TAG = byteArrayOf(0x73, 0x73)
        val SIMPLE_TAG = byteArrayOf(0x67, 0xc8.toByte())
        val TAG_NAME = byteArrayOf(0x45, 0xa3.toByte())
        val TAG_STRING = byteArrayOf(0x44, 0x87.toByte())
    }
}
