package com.flopster101.siliconplayer.export

import android.os.Build
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeAndroidWebmMetadata(file: File, metadata: AudioExportMetadata) {
    val items = listOf(
        "TITLE" to metadata.title,
        "ARTIST" to metadata.artist,
        "ALBUM" to metadata.album,
        "COMPOSER" to metadata.composer,
        "GENRE" to metadata.genre,
        "DATE" to metadata.date,
        "COPYRIGHT" to metadata.copyright,
        "COMMENT" to metadata.comment
    ).mapNotNull { (name, value) ->
        value?.takeUnless { it.isBlank() }?.let {
            webmElement(
                WEBM_TAG,
                webmElement(
                    WEBM_SIMPLE_TAG,
                    webmMetadataString(WEBM_TAG_NAME, name),
                    webmMetadataString(WEBM_TAG_STRING, it)
                )
            )
        }
    }
    if (items.isEmpty()) return

    require(file.isFile) { "WebM file does not exist: $file" }
    val segment = RandomAccessFile(file, "r").use(::readWebmSegment)
    val tags = webmElement(WEBM_TAGS, *items.toTypedArray())
    val newSize = if (segment.unknownSize) {
        null
    } else {
        require(segment.contentSize < webmUnknownSize(8) - tags.size) {
            "WebM metadata is too large."
        }
        encodeWebmSize(segment.contentSize + tags.size, segment.sizeLength)
    }

    val directory = file.absoluteFile.parentFile ?: throw IOException("WebM file has no parent directory.")
    val temporary = File.createTempFile(".${file.name}.", ".tmp", directory)
    var moved = false
    try {
        RandomAccessFile(file, "r").use { input ->
            require(input.length() == segment.fileSize) { "WebM file changed while writing metadata." }
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                if (segment.unknownSize) {
                    copyWebmRange(input, output, 0L, segment.fileSize, buffer)
                } else {
                    copyWebmRange(input, output, 0L, segment.sizeOffset, buffer)
                    output.write(newSize)
                    copyWebmRange(input, output, segment.contentOffset, segment.contentSize, buffer)
                }
                output.write(tags)
                if (!segment.unknownSize) {
                    copyWebmRange(
                        input,
                        output,
                        segment.endOffset,
                        segment.fileSize - segment.endOffset,
                        buffer
                    )
                }
            }
        }
        replaceWebmFile(temporary, file)
        moved = true
    } finally {
        if (!moved) temporary.delete()
    }
}

private data class WebmSegment(
    val sizeOffset: Long,
    val sizeLength: Int,
    val contentOffset: Long,
    val contentSize: Long,
    val endOffset: Long,
    val fileSize: Long,
    val unknownSize: Boolean
)

private data class WebmId(val value: Long, val length: Int)
private data class WebmSize(val value: Long, val length: Int, val unknown: Boolean)

private fun readWebmSegment(input: RandomAccessFile): WebmSegment {
    val fileSize = input.length()
    var offset = 0L
    var segment: WebmSegment? = null
    while (offset < fileSize) {
        val id = readWebmId(input, offset, fileSize)
        val sizeOffset = offset + id.length
        val size = readWebmSize(input, sizeOffset, fileSize)
        val contentOffset = sizeOffset + size.length
        if (size.unknown) {
            require(id.value == WEBM_SEGMENT_ID) {
                "Unknown-size EBML element found outside the WebM Segment."
            }
        }
        val contentSize = if (size.unknown) fileSize - contentOffset else size.value
        require(contentSize <= fileSize - contentOffset) { "Truncated EBML element content." }
        val endOffset = contentOffset + contentSize
        if (id.value == WEBM_SEGMENT_ID) {
            require(segment == null) { "WebM file contains multiple Segments." }
            segment = WebmSegment(
                sizeOffset = sizeOffset,
                sizeLength = size.length,
                contentOffset = contentOffset,
                contentSize = contentSize,
                endOffset = endOffset,
                fileSize = fileSize,
                unknownSize = size.unknown
            )
        }
        offset = endOffset
    }
    return requireNotNull(segment) { "WebM file has no Segment." }
}

private fun readWebmId(input: RandomAccessFile, offset: Long, fileSize: Long): WebmId {
    input.seek(offset)
    val first = input.readUnsignedByte()
    val length = webmVintLength(first)
    require(length in 1..4) { "Invalid EBML element ID." }
    require(length <= fileSize - offset) { "Truncated EBML element ID." }
    var value = first.toLong()
    repeat(length - 1) {
        value = (value shl 8) or input.readUnsignedByte().toLong()
    }
    return WebmId(value, length)
}

private fun readWebmSize(input: RandomAccessFile, offset: Long, fileSize: Long): WebmSize {
    require(offset < fileSize) { "Truncated EBML element size." }
    input.seek(offset)
    val first = input.readUnsignedByte()
    val length = webmVintLength(first)
    require(length > 0) { "Invalid EBML element size." }
    require(length <= fileSize - offset) { "Truncated EBML element size." }
    var value = (first and (0xff ushr length)).toLong()
    repeat(length - 1) {
        value = (value shl 8) or input.readUnsignedByte().toLong()
    }
    return WebmSize(value, length, value == webmUnknownSize(length))
}

private fun webmVintLength(first: Int): Int = when {
    first and 0x80 != 0 -> 1
    first and 0x40 != 0 -> 2
    first and 0x20 != 0 -> 3
    first and 0x10 != 0 -> 4
    first and 0x08 != 0 -> 5
    first and 0x04 != 0 -> 6
    first and 0x02 != 0 -> 7
    first and 0x01 != 0 -> 8
    else -> 0
}

private fun webmUnknownSize(length: Int): Long = (1L shl (length * 7)) - 1L

private fun encodeWebmSize(value: Long, minimumLength: Int = 1): ByteArray {
    require(value >= 0L) { "EBML element size cannot be negative." }
    for (length in minimumLength..8) {
        if (value >= webmUnknownSize(length)) continue
        val bytes = ByteArray(length)
        var remaining = value
        for (index in length - 1 downTo 0) {
            bytes[index] = (remaining and 0xffL).toByte()
            remaining = remaining ushr 8
        }
        bytes[0] = (bytes[0].toInt() or (0x80 ushr (length - 1))).toByte()
        return bytes
    }
    throw IllegalArgumentException("EBML element is too large.")
}

private fun webmMetadataString(id: ByteArray, value: String): ByteArray =
    webmElement(id, value.toByteArray(Charsets.UTF_8))

private fun webmElement(id: ByteArray, vararg payloads: ByteArray): ByteArray {
    val payloadSize = payloads.sumOf { it.size.toLong() }
    require(payloadSize < webmUnknownSize(8)) { "EBML element is too large." }
    val bytes = java.io.ByteArrayOutputStream(id.size + 8 + payloadSize.toInt())
    bytes.write(id)
    bytes.write(encodeWebmSize(payloadSize))
    payloads.forEach(bytes::write)
    return bytes.toByteArray()
}

private fun copyWebmRange(
    input: RandomAccessFile,
    output: DataOutputStream,
    offset: Long,
    length: Long,
    buffer: ByteArray
) {
    input.seek(offset)
    var remaining = length
    while (remaining > 0L) {
        val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        if (count < 0) throw IOException("WebM file changed while writing metadata.")
        output.write(buffer, 0, count)
        remaining -= count
    }
}

private fun replaceWebmFile(temporary: File, file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        if (!temporary.renameTo(file)) throw IOException("Could not replace WebM file: $file")
        return
    }
    try {
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private val WEBM_TAGS = byteArrayOf(0x12, 0x54, 0xc3.toByte(), 0x67)
private val WEBM_TAG = byteArrayOf(0x73, 0x73)
private val WEBM_SIMPLE_TAG = byteArrayOf(0x67, 0xc8.toByte())
private val WEBM_TAG_NAME = byteArrayOf(0x45, 0xa3.toByte())
private val WEBM_TAG_STRING = byteArrayOf(0x44, 0x87.toByte())
private const val WEBM_SEGMENT_ID = 0x18538067L
private const val COPY_BUFFER_SIZE = 64 * 1024
