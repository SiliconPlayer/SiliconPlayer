package com.flopster101.siliconplayer.export

import android.os.Build
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun writeAndroidMp4Metadata(file: File, metadata: AudioExportMetadata) {
    val items = listOf(
        "©nam" to metadata.title,
        "©ART" to metadata.artist,
        "©alb" to metadata.album,
        "©wri" to metadata.composer,
        "©gen" to metadata.genre,
        "©day" to metadata.date,
        "cprt" to metadata.copyright,
        "©cmt" to metadata.comment
    ).mapNotNull { (type, value) ->
        value?.takeUnless { it.isBlank() }?.let { mp4Atom(type, mp4MetadataDataAtom(it)) }
    }
    if (items.isEmpty()) return

    require(file.isFile) { "MP4 file does not exist: $file" }
    val moov = RandomAccessFile(file, "r").use(::readFinalMoovAtom)
    val ilst = mp4Atom("ilst", *items.toTypedArray())
    val meta = mp4Atom("meta", ByteArray(4), mp4MetadataHandlerAtom(), ilst)
    val udta = mp4Atom("udta", meta)
    val newMoovSize = moov.bytes.size.toLong() + udta.size
    require(newMoovSize <= MAX_ATOM_SIZE) { "MP4 metadata is too large." }

    val directory = file.absoluteFile.parentFile ?: throw IOException("MP4 file has no parent directory.")
    val temporary = File.createTempFile(".${file.name}.", ".tmp", directory)
    var moved = false
    try {
        file.inputStream().buffered().use { input ->
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var remaining = moov.offset
                while (remaining > 0L) {
                    val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                    if (count < 0) throw IOException("MP4 file changed while writing metadata.")
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                output.writeInt(newMoovSize.toInt())
                output.write(moov.bytes, 4, moov.bytes.size - 4)
                output.write(udta)
            }
        }
        replaceMp4File(temporary, file)
        moved = true
    } finally {
        if (!moved) temporary.delete()
    }
}

private data class FinalMoovAtom(val offset: Long, val bytes: ByteArray)

private fun readFinalMoovAtom(input: RandomAccessFile): FinalMoovAtom {
    val fileSize = input.length()
    var offset = 0L
    var moov: FinalMoovAtom? = null
    while (offset < fileSize) {
        val remaining = fileSize - offset
        require(remaining >= ATOM_HEADER_SIZE) { "Truncated MP4 atom header." }
        input.seek(offset)
        val declaredSize = input.readInt().toUnsignedLong()
        require(declaredSize != 1L) { "64-bit MP4 atoms are not supported." }
        val type = input.readInt()
        val atomSize = if (declaredSize == 0L) remaining else declaredSize
        require(atomSize >= ATOM_HEADER_SIZE && atomSize <= remaining) { "Invalid MP4 atom size." }
        if (type == MOOV_TYPE) {
            require(moov == null) { "MP4 file contains multiple moov atoms." }
            require(atomSize == remaining) { "The MP4 moov atom must be the final atom." }
            require(atomSize <= Int.MAX_VALUE) { "The MP4 moov atom is too large." }
            val bytes = ByteArray(atomSize.toInt())
            input.seek(offset)
            input.readFully(bytes)
            validateMoovChildren(bytes)
            moov = FinalMoovAtom(offset, bytes)
        }
        offset += atomSize
    }
    return requireNotNull(moov) { "MP4 file has no moov atom." }
}

private fun validateMoovChildren(moov: ByteArray) {
    var offset = ATOM_HEADER_SIZE.toInt()
    while (offset < moov.size) {
        val remaining = moov.size - offset
        require(remaining >= ATOM_HEADER_SIZE) { "Truncated atom inside MP4 moov." }
        val declaredSize = readUnsignedInt(moov, offset)
        require(declaredSize != 1L) { "64-bit atoms inside MP4 moov are not supported." }
        val atomSize = if (declaredSize == 0L) remaining.toLong() else declaredSize
        require(atomSize >= ATOM_HEADER_SIZE && atomSize <= remaining) {
            "Invalid atom size inside MP4 moov."
        }
        offset += atomSize.toInt()
    }
}

private fun mp4MetadataDataAtom(value: String): ByteArray = mp4Atom(
    "data",
    byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0),
    value.toByteArray(Charsets.UTF_8)
)

private fun mp4MetadataHandlerAtom(): ByteArray = mp4Atom(
    "hdlr",
    ByteArray(8),
    "mdir".toByteArray(Charsets.ISO_8859_1),
    ByteArray(13)
)

private fun mp4Atom(type: String, vararg payloads: ByteArray): ByteArray {
    val typeBytes = type.toByteArray(Charsets.ISO_8859_1)
    require(typeBytes.size == 4) { "MP4 atom types must contain four bytes." }
    val payloadSize = payloads.sumOf { it.size.toLong() }
    val atomSize = ATOM_HEADER_SIZE + payloadSize
    require(atomSize <= Int.MAX_VALUE) { "MP4 atom is too large." }
    val bytes = java.io.ByteArrayOutputStream(atomSize.toInt())
    val output = DataOutputStream(bytes)
    output.writeInt(atomSize.toInt())
    output.write(typeBytes)
    payloads.forEach(output::write)
    output.flush()
    return bytes.toByteArray()
}

private fun replaceMp4File(temporary: File, file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        if (!temporary.renameTo(file)) throw IOException("Could not replace MP4 file: $file")
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

private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long {
    return (bytes[offset].toLong() and 0xffL shl 24) or
        (bytes[offset + 1].toLong() and 0xffL shl 16) or
        (bytes[offset + 2].toLong() and 0xffL shl 8) or
        (bytes[offset + 3].toLong() and 0xffL)
}

private fun Int.toUnsignedLong(): Long = toLong() and 0xffffffffL

private const val ATOM_HEADER_SIZE = 8L
private const val MAX_ATOM_SIZE = 0xffffffffL
private const val MOOV_TYPE = 0x6d6f6f76
private const val COPY_BUFFER_SIZE = 64 * 1024
