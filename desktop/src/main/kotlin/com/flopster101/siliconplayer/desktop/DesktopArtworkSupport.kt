package com.flopster101.siliconplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import java.io.File
import java.io.RandomAccessFile

private val COMPANION_ARTWORK_NAMES = listOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)

internal object DesktopArtworkSupport {
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap?>()

    fun loadArtworkForFile(file: File?): ImageBitmap? {
        if (file == null || !file.exists() || !file.isFile) return null
        val cacheKey = file.absolutePath
        memoryCache[cacheKey]?.let { return it }

        val imageBitmap = try {
            extractFlacArtwork(file)?.let { bytes ->
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            } ?: extractId3Artwork(file)?.let { bytes ->
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            } ?: findCompanionArtworkFile(file)?.let { companion ->
                Image.makeFromEncoded(companion.readBytes()).toComposeImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }

        if (imageBitmap != null) {
            memoryCache[cacheKey] = imageBitmap
        }
        return imageBitmap
    }

    private fun findCompanionArtworkFile(audioFile: File): File? {
        val parent = audioFile.parentFile ?: return null
        val baseName = audioFile.nameWithoutExtension.lowercase()
        val siblings = parent.listFiles() ?: return null

        val directMatch = siblings.firstOrNull {
            it.isFile && it.nameWithoutExtension.lowercase() == baseName &&
                (it.extension.equals("jpg", ignoreCase = true) ||
                 it.extension.equals("jpeg", ignoreCase = true) ||
                 it.extension.equals("png", ignoreCase = true) ||
                 it.extension.equals("webp", ignoreCase = true))
        }
        if (directMatch != null) return directMatch

        val allowed = COMPANION_ARTWORK_NAMES.toSet()
        return siblings.firstOrNull { it.isFile && allowed.contains(it.name.lowercase()) }
    }

    fun extractFlacBitDepth(file: File): Int? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                    magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()) {
                    return null
                }
                val header = raf.readUnsignedByte()
                val type = header and 0x7F
                val length = (raf.readUnsignedByte() shl 16) or
                             (raf.readUnsignedByte() shl 8) or
                             raf.readUnsignedByte()
                if (type == 0 && length >= 14) {
                    val streamInfo = ByteArray(length)
                    raf.readFully(streamInfo)
                    val b20 = streamInfo[12].toInt() and 0xFF
                    val b21 = streamInfo[13].toInt() and 0xFF
                    val bps = (((b20 and 0x01) shl 4) or ((b21 ushr 4) and 0x0F)) + 1
                    if (bps in 4..64) return bps
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun extractFlacArtwork(file: File): ByteArray? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x66.toByte() || magic[1] != 0x4C.toByte() ||
                    magic[2] != 0x61.toByte() || magic[3] != 0x43.toByte()) {
                    return null // Not "fLaC"
                }

                var isLast = false
                while (!isLast) {
                    val header = raf.readUnsignedByte()
                    isLast = (header and 0x80) != 0
                    val type = header and 0x7F
                    val length = (raf.readUnsignedByte() shl 16) or
                                 (raf.readUnsignedByte() shl 8) or
                                 raf.readUnsignedByte()

                    if (type == 6) { // PICTURE
                        raf.skipBytes(4) // picture type
                        val mimeLength = raf.readInt()
                        if (mimeLength in 0..1024) raf.skipBytes(mimeLength) else return null
                        val descLength = raf.readInt()
                        if (descLength in 0..1024) raf.skipBytes(descLength) else return null
                        raf.skipBytes(16) // width, height, colorDepth, colorsUsed
                        val dataLength = raf.readInt()
                        if (dataLength in 1..(32 * 1024 * 1024)) {
                            val data = ByteArray(dataLength)
                            raf.readFully(data)
                            return data
                        }
                        return null
                    } else {
                        raf.skipBytes(length)
                    }
                }
            }
        } catch (_: Throwable) {
            // Non-fatal, fallback to companion art
        }
        return null
    }

    private fun extractId3Artwork(file: File): ByteArray? {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                if (raf.read(header) < 10) return null
                if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
                    return null // Not "ID3"
                }
                val majorVersion = header[3].toInt()
                val tagSize = (header[6].toInt() and 0x7F shl 21) or
                              (header[7].toInt() and 0x7F shl 14) or
                              (header[8].toInt() and 0x7F shl 7) or
                              (header[9].toInt() and 0x7F)
                val tagDataEnd = 10L + tagSize

                while (raf.filePointer < tagDataEnd - 10) {
                    val frameId = ByteArray(4)
                    if (raf.read(frameId) < 4) break
                    if (frameId[0].toInt() == 0) break
                    val frameIdStr = String(frameId, Charsets.ISO_8859_1)
                    val frameSize = if (majorVersion >= 4) {
                        (raf.readUnsignedByte() and 0x7F shl 21) or
                        (raf.readUnsignedByte() and 0x7F shl 14) or
                        (raf.readUnsignedByte() and 0x7F shl 7) or
                        (raf.readUnsignedByte() and 0x7F)
                    } else {
                        raf.readInt()
                    }
                    raf.skipBytes(2) // flags
                    if (frameSize <= 0 || raf.filePointer + frameSize > tagDataEnd) break

                    if (frameIdStr == "APIC") {
                        val frameData = ByteArray(frameSize)
                        raf.readFully(frameData)
                        var idx = 0
                        val textEncoding = frameData[idx++].toInt()
                        while (idx < frameData.size && frameData[idx] != 0.toByte()) idx++
                        idx++ // skip null byte
                        if (idx >= frameData.size) return null
                        idx++ // skip picture type
                        if (textEncoding == 1 || textEncoding == 2) {
                            while (idx < frameData.size - 1 && !(frameData[idx] == 0.toByte() && frameData[idx + 1] == 0.toByte())) idx += 2
                            idx += 2
                        } else {
                            while (idx < frameData.size && frameData[idx] != 0.toByte()) idx++
                            idx++
                        }
                        if (idx < frameData.size) {
                            return frameData.copyOfRange(idx, frameData.size)
                        }
                        return null
                    } else {
                        raf.skipBytes(frameSize)
                    }
                }
            }
        } catch (_: Throwable) {
            // Non-fatal
        }
        return null
    }
}
