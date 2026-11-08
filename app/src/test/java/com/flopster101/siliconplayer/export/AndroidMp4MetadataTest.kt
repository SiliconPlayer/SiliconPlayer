package com.flopster101.siliconplayer.export

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidMp4MetadataTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun writesEveryMetadataValueAndUpdatesFinalMoovSize() {
        val originalMoov = atom("moov", atom("mvhd", ByteArray(4)), atom("trak", atom("tkhd", ByteArray(4))))
        val prefix = atom("ftyp", byteArrayOf(1, 2, 3, 4)) + atom("mdat", byteArrayOf(5, 6, 7))
        val file = mp4File("all-tags.m4a", prefix + originalMoov)
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

        writeAndroidMp4Metadata(file, metadata)

        val tags = listOf(
            "©nam" to metadata.title,
            "©ART" to metadata.artist,
            "©alb" to metadata.album,
            "©wri" to metadata.composer,
            "©gen" to metadata.genre,
            "©day" to metadata.date,
            "cprt" to metadata.copyright,
            "©cmt" to metadata.comment
        )
        val ilst = atom(
            "ilst",
            *tags.map { (type, value) -> atom(type, atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0), value!!.toByteArray())) }.toTypedArray()
        )
        val handler = atom("hdlr", ByteArray(8), "mdir".toByteArray(Charsets.ISO_8859_1), ByteArray(13))
        val udta = atom("udta", atom("meta", ByteArray(4), handler, ilst))
        val expected = prefix + atom("moov", originalMoov.copyOfRange(8, originalMoov.size), udta)
        val actual = file.readBytes()
        assertArrayEquals(expected, actual)
        assertEquals(originalMoov.size + udta.size, readInt(actual, prefix.size))
        assertArrayEquals(prefix, actual.copyOfRange(0, prefix.size))
    }

    @Test
    fun omitsBlankAndNullTags() {
        val file = mp4File("selected-tags.mp4", syntheticMp4())

        writeAndroidMp4Metadata(
            file,
            AudioExportMetadata(title = "Song", artist = " ", album = null, comment = "Note")
        )

        val bytes = file.readBytes()
        assertContains(bytes, atom("©nam", atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0), "Song".toByteArray())))
        assertContains(bytes, atom("©cmt", atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0), "Note".toByteArray())))
        assertFalse(bytes.containsSequence("©ART".toByteArray(Charsets.ISO_8859_1)))
        assertFalse(bytes.containsSequence("©alb".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun allBlankMetadataIsANoOp() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val file = mp4File("empty.3gp", original)

        writeAndroidMp4Metadata(file, AudioExportMetadata(title = " ", artist = "\n", comment = null))

        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun rejectsMalformedAtomSizesWithoutChangingFile() {
        val malformedFiles = listOf(
            byteArrayOf(0, 0, 0, 7) + "moov".toByteArray(),
            byteArrayOf(0, 0, 0, 20) + "moov".toByteArray(),
            byteArrayOf(0, 0, 0),
            atom("ftyp", ByteArray(4)) + byteArrayOf(0, 0, 0, 8, 1)
        )

        malformedFiles.forEachIndexed { index, original ->
            val file = mp4File("malformed-$index.m4a", original)
            assertThrows(IllegalArgumentException::class.java) {
                writeAndroidMp4Metadata(file, AudioExportMetadata(title = "Song"))
            }
            assertArrayEquals(original, file.readBytes())
        }
        assertEquals(malformedFiles.size, tempFolder.root.listFiles()?.size)
    }

    @Test
    fun rejects64BitAtomsWithoutChangingFile() {
        val original = byteArrayOf(0, 0, 0, 1) + "moov".toByteArray() + ByteArray(8)
        val file = mp4File("64-bit.m4a", original)

        assertThrows(IllegalArgumentException::class.java) {
            writeAndroidMp4Metadata(file, AudioExportMetadata(title = "Song"))
        }

        assertArrayEquals(original, file.readBytes())
    }

    @Test
    fun rejectsMissingOrNonFinalMoovWithoutChangingFile() {
        val originals = listOf(
            atom("ftyp", ByteArray(4)),
            atom("moov", atom("mvhd", ByteArray(4))) + atom("free", ByteArray(0))
        )

        originals.forEachIndexed { index, original ->
            val file = mp4File("layout-$index.m4a", original)
            assertThrows(IllegalArgumentException::class.java) {
                writeAndroidMp4Metadata(file, AudioExportMetadata(title = "Song"))
            }
            assertArrayEquals(original, file.readBytes())
        }
    }

    @Test
    fun rejectsMalformedMoovChildrenWithoutChangingFile() {
        val original = atom("ftyp", ByteArray(4)) + atom("moov", byteArrayOf(0, 0, 0, 9) + "mvhd".toByteArray())
        val file = mp4File("bad-moov.m4a", original)

        assertThrows(IllegalArgumentException::class.java) {
            writeAndroidMp4Metadata(file, AudioExportMetadata(title = "Song"))
        }

        assertArrayEquals(original, file.readBytes())
    }

    private fun mp4File(name: String, bytes: ByteArray): File {
        return tempFolder.newFile(name).apply { writeBytes(bytes) }
    }

    private fun syntheticMp4(): ByteArray {
        return atom("ftyp", ByteArray(4)) +
            atom("mdat", byteArrayOf(9, 8, 7)) +
            atom("moov", atom("mvhd", ByteArray(4)))
    }

    private fun atom(type: String, vararg payloads: ByteArray): ByteArray {
        val size = 8 + payloads.sumOf { it.size }
        val bytes = ByteArrayOutputStream(size)
        val output = DataOutputStream(bytes)
        output.writeInt(size)
        output.write(type.toByteArray(Charsets.ISO_8859_1))
        payloads.forEach(output::write)
        return bytes.toByteArray()
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xff shl 24) or
            (bytes[offset + 1].toInt() and 0xff shl 16) or
            (bytes[offset + 2].toInt() and 0xff shl 8) or
            (bytes[offset + 3].toInt() and 0xff)
    }

    private fun assertContains(bytes: ByteArray, expected: ByteArray) {
        assertEquals(true, bytes.containsSequence(expected))
    }

    private fun ByteArray.containsSequence(sequence: ByteArray): Boolean {
        return indices.any { start ->
            start + sequence.size <= size && sequence.indices.all { index -> this[start + index] == sequence[index] }
        }
    }
}
