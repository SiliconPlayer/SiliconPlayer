package com.flopster101.siliconplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.buildHttpRequestUri
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource
import com.flopster101.siliconplayer.httpBasicAuthorizationHeader
import com.flopster101.siliconplayer.joinSmbRelativePath
import com.flopster101.siliconplayer.normalizeHttpDirectoryPath
import com.flopster101.siliconplayer.normalizeHttpPath
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import com.flopster101.siliconplayer.resolveCredentialedHttpSpec
import com.flopster101.siliconplayer.resolveCredentialedSmbSpec
import com.flopster101.siliconplayer.withAppSmbSession
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Image

private const val SMB_SCHEME = "smb"
private const val HTTP_SCHEME = "http"
private const val HTTPS_SCHEME = "https"

// Embedded art sits in the opening metadata block of both containers, so a bounded
// prefix covers remote tracks; the same cap covers remote cover files.
private const val MAX_REMOTE_ARTWORK_BYTES = 4 * 1024 * 1024

private val COMPANION_ARTWORK_NAMES = listOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)

/** Random access over a local file or an in-memory remote prefix. */
private interface ArtworkByteSource : Closeable {
    fun read(offset: Long, dest: ByteArray, destOffset: Int, length: Int): Int
}

private class FileArtworkByteSource(file: File) : ArtworkByteSource {
    private val raf = RandomAccessFile(file, "r")

    override fun read(offset: Long, dest: ByteArray, destOffset: Int, length: Int): Int {
        raf.seek(offset)
        return raf.read(dest, destOffset, length)
    }

    override fun close() = raf.close()
}

private class BytesArtworkByteSource(private val bytes: ByteArray) : ArtworkByteSource {
    override fun read(offset: Long, dest: ByteArray, destOffset: Int, length: Int): Int {
        if (offset < 0L || offset >= bytes.size) return -1
        val count = minOf(length, bytes.size - offset.toInt())
        if (count <= 0) return -1
        bytes.copyInto(dest, destOffset, offset.toInt(), offset.toInt() + count)
        return count
    }

    override fun close() = Unit
}

/** Cursor covering the metadata parsing reads, over any [ArtworkByteSource]. */
private class ArtworkByteReader(private val source: ArtworkByteSource) : Closeable {
    var position = 0L
        private set

    fun readUnsignedByte(): Int = readFully(ByteArray(1))[0].toInt() and 0xFF

    fun readInt(): Int {
        val bytes = readFully(ByteArray(4))
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    fun readFully(dest: ByteArray): ByteArray {
        var written = 0
        while (written < dest.size) {
            val read = source.read(position, dest, written, dest.size - written)
            if (read <= 0) throw EOFException("Unexpected end of artwork source")
            position += read
            written += read
        }
        return dest
    }

    fun read(dest: ByteArray): Int {
        val read = source.read(position, dest, 0, dest.size)
        if (read > 0) position += read
        return read
    }

    fun skipBytes(count: Int) {
        position += count
    }

    override fun close() = source.close()
}

internal object DesktopArtworkSupport {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap?>()

    internal fun peekMemoryArtwork(key: String?): ImageBitmap? =
        if (key == null) null else memoryCache[key]?.takeIf { it != null }

    fun loadArtworkForFile(file: File?): ImageBitmap? {
        if (file == null || !file.exists() || !file.isFile) return null
        val cacheKey = file.absolutePath
        memoryCache[cacheKey]?.let { return it }

        val imageBitmap = extractEmbeddedArtworkBytes(file)?.let(::decodeArtworkBytes)
            ?: runCatching {
                findCompanionArtworkFile(file)?.let { decodeArtworkBytes(it.readBytes()) }
            }.getOrNull()

        if (imageBitmap != null) {
            memoryCache[cacheKey] = imageBitmap
        }
        return imageBitmap
    }

    /** Embedded artwork bytes, split out so metadata parsing stays testable without decoding. */
    internal fun extractEmbeddedArtworkBytes(file: File?): ByteArray? {
        if (file == null || !file.exists() || !file.isFile) return null
        return extractFlacArtworkForSource { FileArtworkByteSource(file) }
            ?: extractId3ArtworkForSource { FileArtworkByteSource(file) }
    }

    /**
     * Resolves artwork for a playback source: local file, cached download of a
     * remote source, or a direct remote lookup. Mirrors Android's
     * `loadArtworkForSource`.
     */
    fun loadArtworkForSource(
        displayFile: File?,
        sourceId: String?,
        requestUrl: String? = null
    ): ImageBitmap? {
        displayFile?.takeIf { it.exists() && it.isFile }?.let { return loadArtworkForFile(it) }
        val remoteRequest = requestUrl?.trim().takeUnless { it.isNullOrBlank() }
            ?: sourceId?.trim().takeUnless { it.isNullOrBlank() }
            ?: return null
        val scheme = remoteRequest.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
        if (scheme != SMB_SCHEME && scheme != HTTP_SCHEME && scheme != HTTPS_SCHEME) return null
        val cacheKey = sourceId?.trim().takeUnless { it.isNullOrBlank() } ?: remoteRequest
        memoryCache[cacheKey]?.let { return it }

        val imageBitmap = runCatching {
            loadCachedDownloadArtwork(sourceId)
                ?: loadEmbeddedRemoteArtwork(remoteRequest, scheme)
                ?: loadCompanionRemoteArtwork(remoteRequest, scheme)
        }.getOrNull() ?: return null
        memoryCache[cacheKey] = imageBitmap
        return imageBitmap
    }

    private fun loadCachedDownloadArtwork(sourceId: String?): ImageBitmap? {
        val normalizedSource = sourceId?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        val cachedFile = runCatching {
            findExistingCachedFileForSource(
                File(DesktopPaths.cacheDir(), REMOTE_SOURCE_CACHE_DIR),
                normalizedSource
            )
        }.getOrNull() ?: return null
        return loadArtworkForFile(cachedFile)
    }

    private fun loadEmbeddedRemoteArtwork(remoteRequest: String, scheme: String): ImageBitmap? {
        val prefix = if (scheme == SMB_SCHEME) {
            readSmbFileBytes(remoteRequest)
        } else {
            readHttpBytes(remoteRequest, byteRangePrefix = true)
        } ?: return null
        if (prefix.isEmpty()) return null
        return extractEmbeddedArtworkBytesFromBuffer(prefix)?.let(::decodeArtworkBytes)
    }

    private fun extractEmbeddedArtworkBytesFromBuffer(bytes: ByteArray): ByteArray? =
        extractFlacArtworkForSource { BytesArtworkByteSource(bytes) }
            ?: extractId3ArtworkForSource { BytesArtworkByteSource(bytes) }

    private fun loadCompanionRemoteArtwork(remoteRequest: String, scheme: String): ImageBitmap? {
        return if (scheme == SMB_SCHEME) {
            loadSmbCompanionArtwork(remoteRequest)
        } else {
            loadHttpCompanionArtwork(remoteRequest)
        }
    }

    private fun loadSmbCompanionArtwork(remoteRequest: String): ImageBitmap? {
        val spec = resolveCredentialedSmbSpec(remoteRequest) ?: return null
        val normalizedPath = normalizeSmbPathForShare(spec.path).orEmpty()
        if (spec.share.isBlank() || normalizedPath.isBlank()) return null
        val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim()

        return runCatching {
            withAppSmbSession(spec) { session ->
                val share = session.connectShare(spec.share)
                if (share !is DiskShare) {
                    runCatching { share.close() }
                    return@withAppSmbSession null
                }
                try {
                    val listPath = normalizeSmbPathForShare(parentPath).orEmpty()
                    val fileNames = runCatching {
                        share.list(listPath, FileDirectoryInformation::class.java)
                    }.getOrNull().orEmpty().mapNotNull { entry ->
                        entry.fileName?.trim()?.takeIf { it.isNotBlank() && it != "." && it != ".." }
                    }
                    val matchedName = COMPANION_ARTWORK_NAMES.firstNotNullOfOrNull { candidate ->
                        fileNames.firstOrNull { it.equals(candidate, ignoreCase = true) }
                    } ?: return@withAppSmbSession null
                    val artworkPath = joinSmbRelativePath(parentPath, matchedName)
                    readOpenSmbFile(share, artworkPath)?.let(::decodeArtworkBytes)
                } finally {
                    runCatching { share.close() }
                }
            }
        }.getOrNull()
    }

    private fun loadHttpCompanionArtwork(remoteRequest: String): ImageBitmap? {
        val spec = resolveCredentialedHttpSpec(remoteRequest) ?: return null
        val normalizedPath = normalizeHttpPath(spec.path).trimEnd('/')
        if (normalizedPath.isBlank()) return null
        val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim()
        val directoryPath = if (parentPath.isBlank()) "/" else normalizeHttpDirectoryPath(parentPath)

        return COMPANION_ARTWORK_NAMES.firstNotNullOfOrNull { artworkName ->
            val artworkSpec = spec.copy(
                path = normalizeHttpPath("$directoryPath$artworkName"),
                query = null
            )
            readHttpBytes(buildHttpRequestUri(artworkSpec), byteRangePrefix = false)
                ?.let(::decodeArtworkBytes)
        }
    }

    private fun readSmbFileBytes(remoteRequest: String): ByteArray? {
        val spec = resolveCredentialedSmbSpec(remoteRequest) ?: return null
        val remotePath = normalizeSmbPathForShare(spec.path).orEmpty()
        if (spec.share.isBlank() || remotePath.isBlank()) return null
        return runCatching {
            withAppSmbSession(spec) { session ->
                val share = session.connectShare(spec.share)
                if (share !is DiskShare) {
                    runCatching { share.close() }
                    return@withAppSmbSession null
                }
                try {
                    readOpenSmbFile(share, remotePath)
                } finally {
                    runCatching { share.close() }
                }
            }
        }.getOrNull()
    }

    private fun readOpenSmbFile(share: DiskShare, remotePath: String): ByteArray? {
        val smbFile = share.openFile(
            remotePath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        return smbFile.use { file ->
            file.inputStream?.use { input -> readBounded(input) }
        }
    }

    private fun readHttpBytes(url: String, byteRangePrefix: Boolean): ByteArray? {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 20_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Desktop)")
                setRequestProperty("Accept", "*/*")
                if (byteRangePrefix) {
                    setRequestProperty("Range", "bytes=0-${MAX_REMOTE_ARTWORK_BYTES - 1}")
                }
                resolveCredentialedHttpSpec(url)?.let { spec ->
                    httpBasicAuthorizationHeader(
                        username = spec.username,
                        password = spec.password
                    )?.let { header ->
                        setRequestProperty("Authorization", header)
                    }
                }
            }
        }.getOrNull() ?: return null
        return try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                connection.inputStream?.use { input -> readBounded(input) }
            }
        } catch (_: Throwable) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (total < MAX_REMOTE_ARTWORK_BYTES) {
            val remaining = MAX_REMOTE_ARTWORK_BYTES - total
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return output.toByteArray()
    }

    private fun extractFlacArtworkForSource(openSource: () -> ArtworkByteSource): ByteArray? =
        extractFromSource(openSource) { reader -> extractFlacArtwork(reader) }

    private fun extractId3ArtworkForSource(openSource: () -> ArtworkByteSource): ByteArray? =
        extractFromSource(openSource) { reader -> extractId3Artwork(reader) }

    private fun extractFromSource(
        openSource: () -> ArtworkByteSource,
        extract: (ArtworkByteReader) -> ByteArray?
    ): ByteArray? {
        return try {
            openSource().use { source ->
                ArtworkByteReader(source).use(extract)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeArtworkBytes(bytes: ByteArray): ImageBitmap? {
        return try {
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: Throwable) {
            null
        }
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
        val source = try {
            FileArtworkByteSource(file)
        } catch (_: Throwable) {
            return null
        }
        return try {
            ArtworkByteReader(source).use { reader ->
                if (!readerMatchesFlacMagic(reader)) {
                    null
                } else {
                    val header = reader.readUnsignedByte()
                    val type = header and 0x7F
                    val length = (reader.readUnsignedByte() shl 16) or
                                 (reader.readUnsignedByte() shl 8) or
                                 reader.readUnsignedByte()
                    if (type == 0 && length >= 14) {
                        val streamInfo = reader.readFully(ByteArray(length))
                        val b20 = streamInfo[12].toInt() and 0xFF
                        val b21 = streamInfo[13].toInt() and 0xFF
                        val bps = (((b20 and 0x01) shl 4) or ((b21 ushr 4) and 0x0F)) + 1
                        if (bps in 4..64) bps else null
                    } else {
                        null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun readerMatchesFlacMagic(reader: ArtworkByteReader): Boolean {
        val magic = reader.readFully(ByteArray(4))
        return magic[0] == 0x66.toByte() && magic[1] == 0x4C.toByte() &&
            magic[2] == 0x61.toByte() && magic[3] == 0x43.toByte()
    }

    private fun extractFlacArtwork(reader: ArtworkByteReader): ByteArray? {
        if (!readerMatchesFlacMagic(reader)) {
            return null
        }
        var isLast = false
        while (!isLast) {
            val header = reader.readUnsignedByte()
            isLast = (header and 0x80) != 0
            val type = header and 0x7F
            val length = (reader.readUnsignedByte() shl 16) or
                         (reader.readUnsignedByte() shl 8) or
                         reader.readUnsignedByte()

            if (type == 6) { // PICTURE
                reader.skipBytes(4) // picture type
                val mimeLength = reader.readInt()
                if (mimeLength in 0..1024) reader.skipBytes(mimeLength) else return null
                val descLength = reader.readInt()
                if (descLength in 0..1024) reader.skipBytes(descLength) else return null
                reader.skipBytes(16) // width, height, colorDepth, colorsUsed
                val dataLength = reader.readInt()
                if (dataLength in 1..(32 * 1024 * 1024)) {
                    return reader.readFully(ByteArray(dataLength))
                }
                return null
            } else {
                reader.skipBytes(length)
            }
        }
        return null
    }

    private fun extractId3Artwork(reader: ArtworkByteReader): ByteArray? {
        val header = ByteArray(10)
        if (reader.read(header) < 10) return null
        if (header[0] != 0x49.toByte() || header[1] != 0x44.toByte() || header[2] != 0x33.toByte()) {
            return null // Not "ID3"
        }
        val majorVersion = header[3].toInt()
        val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                      ((header[7].toInt() and 0x7F) shl 14) or
                      ((header[8].toInt() and 0x7F) shl 7) or
                      (header[9].toInt() and 0x7F)
        val tagDataEnd = 10L + tagSize
        if (tagSize <= 0) return null

        while (reader.position < tagDataEnd - 10) {
            val frameId = ByteArray(4)
            if (reader.read(frameId) < 4) break
            if (frameId[0].toInt() == 0) break
            val frameIdStr = String(frameId, Charsets.ISO_8859_1)
            val frameSize = if (majorVersion >= 4) {
                (reader.readUnsignedByte() and 0x7F shl 21) or
                (reader.readUnsignedByte() and 0x7F shl 14) or
                (reader.readUnsignedByte() and 0x7F shl 7) or
                (reader.readUnsignedByte() and 0x7F)
            } else {
                reader.readInt()
            }
            reader.skipBytes(2) // flags
            if (frameSize <= 0 || reader.position + frameSize > tagDataEnd) break

            if (frameIdStr == "APIC") {
                val frameData = reader.readFully(ByteArray(frameSize))
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
                reader.skipBytes(frameSize)
            }
        }
        return null
    }
}
