package com.flopster101.siliconplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileDirectoryInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val ARTWORK_MEMORY_CACHE_MAX_KB = 12 * 1024

private object ArtworkBitmapMemoryCache : LruCache<String, Bitmap>(ARTWORK_MEMORY_CACHE_MAX_KB) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}

internal fun artworkCacheKeyForSource(
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null
): String? {
    val normalizedRequestUrl = requestUrl?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedSourceId = sourceId?.trim().takeUnless { it.isNullOrBlank() }
    val displayPath = displayFile?.absolutePath?.trim().takeUnless { it.isNullOrBlank() }
    return normalizedRequestUrl ?: normalizedSourceId ?: displayPath
}

internal fun peekCachedArtworkBitmapForSource(
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null
): Bitmap? {
    val cacheKey = artworkCacheKeyForSource(
        displayFile = displayFile,
        sourceId = sourceId,
        requestUrl = requestUrl
    ) ?: return null
    return synchronized(ArtworkBitmapMemoryCache) {
        ArtworkBitmapMemoryCache.get(cacheKey)?.takeUnless { it.isRecycled }
    }
}

private fun cacheArtworkBitmapForSource(
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null,
    bitmap: Bitmap?
) {
    val cacheKey = artworkCacheKeyForSource(
        displayFile = displayFile,
        sourceId = sourceId,
        requestUrl = requestUrl
    ) ?: return
    val stableBitmap = bitmap?.takeUnless { it.isRecycled } ?: return
    synchronized(ArtworkBitmapMemoryCache) {
        ArtworkBitmapMemoryCache.put(cacheKey, stableBitmap)
    }
}

internal fun loadArtworkForFile(file: File): ImageBitmap? {
    peekCachedArtworkBitmapForSource(
        displayFile = file,
        sourceId = file.absolutePath
    )?.let { return it.asImageBitmap() }
    loadEmbeddedArtwork(file)?.let {
        cacheArtworkBitmapForSource(
            displayFile = file,
            sourceId = file.absolutePath,
            bitmap = it
        )
        return it.asImageBitmap()
    }
    findFolderArtworkFile(file)?.let { folderImage ->
        decodeScaledBitmapFromFile(folderImage)?.let {
            cacheArtworkBitmapForSource(
                displayFile = file,
                sourceId = file.absolutePath,
                bitmap = it
            )
            return it.asImageBitmap()
        }
    }
    return null
}

internal fun loadArtworkForSource(
    context: Context,
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null
): ImageBitmap? {
    return loadArtworkBitmapForSource(
        context = context,
        displayFile = displayFile,
        sourceId = sourceId,
        requestUrl = requestUrl
    )?.asImageBitmap()
}

internal fun loadArtworkBitmapForSource(
    context: Context,
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null
): Bitmap? {
    peekCachedArtworkBitmapForSource(
        displayFile = displayFile,
        sourceId = sourceId,
        requestUrl = requestUrl
    )?.let { return it }

    val normalized = sourceId?.trim()
    val scheme = normalized?.let { Uri.parse(it).scheme?.lowercase(Locale.ROOT) }
    val normalizedRequestUrl = requestUrl?.trim().takeUnless { it.isNullOrBlank() }
    val requestScheme = normalizedRequestUrl?.let { Uri.parse(it).scheme?.lowercase(Locale.ROOT) }
    val artworkRequestUrl = when (requestScheme) {
        "http", "https", "smb" -> normalizedRequestUrl
        else -> normalized
    }
    val isRemote = scheme == "http" || scheme == "https" || scheme == "smb"

    displayFile?.takeIf { it.exists() && it.isFile }?.let { local ->
        loadEmbeddedArtwork(local)?.let {
            cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
            return it
        }
        findFolderArtworkFile(local)?.let { folderImage ->
            decodeScaledBitmapFromFile(folderImage)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
        }
    }

    if (isRemote && !normalized.isNullOrBlank()) {
        if ((scheme == "http" || scheme == "https") && !artworkRequestUrl.isNullOrBlank()) {
            loadEmbeddedArtworkFromRemote(artworkRequestUrl)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
        }
        val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
        findExistingCachedFileForSource(cacheRoot, normalized)?.let { cached ->
            loadEmbeddedArtwork(cached)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
            findFolderArtworkFile(cached)?.let { folderImage ->
                decodeScaledBitmapFromFile(folderImage)?.let {
                    cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                    return it
                }
            }
        }
        if ((scheme == "http" || scheme == "https") && !artworkRequestUrl.isNullOrBlank()) {
            loadFolderArtworkFromHttpSource(artworkRequestUrl)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
        }
        if (scheme == "smb" && !artworkRequestUrl.isNullOrBlank()) {
            loadEmbeddedArtworkFromSmb(artworkRequestUrl)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
        }
        if (scheme == "smb" && !artworkRequestUrl.isNullOrBlank()) {
            loadFolderArtworkFromSmb(artworkRequestUrl)?.let {
                cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, it)
                return it
            }
        }
    }
    return null
}

internal val MediaMetadataRetrieverGlobalLock = Any()

private fun loadEmbeddedArtwork(file: File): Bitmap? {
    synchronized(MediaMetadataRetrieverGlobalLock) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmapFromBytes(embedded)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun loadEmbeddedArtworkFromRemote(url: String): Bitmap? {
    val headers = mutableMapOf(
        "User-Agent" to "SiliconPlayer/1.0 (Android)",
        "Icy-MetaData" to "1"
    )
    parseHttpSourceSpecFromInput(url)?.let { spec ->
        httpBasicAuthorizationHeader(
            username = spec.username,
            password = spec.password
        )?.let { authHeader ->
            headers["Authorization"] = authHeader
        }
    }
    synchronized(MediaMetadataRetrieverGlobalLock) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(
                url,
                headers
            )
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmapFromBytes(embedded)
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun loadFolderArtworkFromHttpSource(sourceId: String): Bitmap? {
    val spec = resolveCredentialedHttpSpec(sourceId) ?: return null
    val normalizedPath = normalizeHttpPath(spec.path).trimEnd('/')
    if (normalizedPath.isBlank()) return null
    val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim()
    val directoryPath = if (parentPath.isBlank()) "/" else normalizeHttpDirectoryPath(parentPath)
    return FOLDER_ARTWORK_FILE_NAMES.firstNotNullOfOrNull { artworkName ->
        val artworkSpec = spec.copy(
            path = normalizeHttpPath("$directoryPath$artworkName"),
            query = null
        )
        loadBitmapFromHttpUrl(buildHttpRequestUri(artworkSpec))
    }
}

private fun loadEmbeddedArtworkFromSmb(requestUri: String): Bitmap? {
    val spec = resolveCredentialedSmbSpec(requestUri) ?: return null
    val remotePath = normalizeSmbPathForShare(spec.path).orEmpty()
    if (spec.share.isBlank() || remotePath.isBlank()) return null
    return withOpenedSmbFile(spec, remotePath) { _, smbFile, sizeBytes ->
        if (sizeBytes <= 0L) return@withOpenedSmbFile null
        val dataSource = SmbRetrieverDataSource(smbFile, sizeBytes)
        synchronized(MediaMetadataRetrieverGlobalLock) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(dataSource)
                val embedded = retriever.embeddedPicture ?: return@withOpenedSmbFile null
                decodeScaledBitmapFromBytes(embedded)
            } catch (_: Throwable) {
                null
            } finally {
                retriever.release()
                dataSource.close()
            }
        }
    }
}

private fun loadFolderArtworkFromSmb(requestUri: String): Bitmap? {
    val spec = resolveCredentialedSmbSpec(requestUri) ?: return null
    val normalizedPath = normalizeSmbPathForShare(spec.path).orEmpty()
    if (spec.share.isBlank() || normalizedPath.isBlank()) return null
    val parentPath = normalizedPath.substringBeforeLast('/', missingDelimiterValue = "").trim()
    val credentialedSpec = NetworkCredentialStore.applyTo(spec)
    return try {
        withAppSmbSession(credentialedSpec) { session ->
            val share = session.connectShare(credentialedSpec.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                return@withAppSmbSession null
            }
            try {
                val listPath = normalizeSmbPathForShare(parentPath).orEmpty()
                val rawEntries = runCatching {
                    share.list(listPath, FileDirectoryInformation::class.java)
                }.getOrNull() ?: return@withAppSmbSession null

                val fileNames = rawEntries.mapNotNull { entry ->
                    val name = entry.fileName?.trim().orEmpty()
                    if (name.isBlank() || name == "." || name == "..") null else name
                }

                val matchedName = FOLDER_ARTWORK_FILE_NAMES.firstNotNullOfOrNull { candidate ->
                    fileNames.firstOrNull { it.equals(candidate, ignoreCase = true) }
                } ?: return@withAppSmbSession null

                val artworkPath = joinSmbRelativePath(parentPath, matchedName)
                loadBitmapFromDiskShare(share, artworkPath)
            } finally {
                runCatching { share.close() }
            }
        }
    } catch (_: Throwable) {
        null
    }
}

private fun loadBitmapFromDiskShare(share: DiskShare, remotePath: String): Bitmap? {
    val normalizedPath = normalizeSmbPathForShare(remotePath).orEmpty()
    if (normalizedPath.isBlank()) return null
    return try {
        val smbFile = share.openFile(
            normalizedPath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        try {
            val input = smbFile.inputStream ?: return null
            try {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_REMOTE_ARTWORK_BYTES) return null
                    output.write(buffer, 0, read)
                }
                decodeScaledBitmapFromBytes(output.toByteArray())
            } finally {
                runCatching { input.close() }
            }
        } finally {
            runCatching { smbFile.close() }
        }
    } catch (_: Throwable) {
        null
    }
}

private inline fun <T> withOpenedSmbFile(
    spec: SmbSourceSpec,
    remotePath: String,
    block: (DiskShare, SmbFile, Long) -> T
): T? {
    return try {
        withAppSmbSession(spec) { session ->
            val share = session.connectShare(spec.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                return@withAppSmbSession null
            }
            try {
                val smbFile = share.openFile(
                    remotePath,
                    setOf(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                try {
                    val sizeBytes = runCatching {
                        smbFile.getFileInformation(FileStandardInformation::class.java)
                            .getEndOfFile()
                            .coerceAtLeast(0L)
                    }.getOrDefault(0L)
                    block(share, smbFile, sizeBytes)
                } finally {
                    runCatching { smbFile.close() }
                }
            } finally {
                runCatching { share.close() }
            }
        }
    } catch (_: Throwable) {
        null
    }
}

private class SmbRetrieverDataSource(
    private val smbFile: SmbFile,
    private val sizeBytes: Long
) : MediaDataSource() {
    @Volatile
    private var closed = false

    private val bufferLock = Any()
    private val buffer = ByteArray(512 * 1024)
    private var bufferStart: Long = -1L
    private var bufferLength: Int = 0

    override fun readAt(position: Long, dest: ByteArray, offset: Int, size: Int): Int {
        if (closed || position < 0L || offset !in 0..dest.size || size <= 0) return -1
        if (position >= sizeBytes) return -1
        val maxReadable = (dest.size - offset).coerceAtLeast(0)
        val requestedSize = size.coerceAtMost(maxReadable).toLong().coerceAtMost(sizeBytes - position).toInt()
        if (requestedSize <= 0) return -1

        synchronized(bufferLock) {
            if (closed) return -1
            if (bufferStart >= 0L && position >= bufferStart && position + requestedSize <= bufferStart + bufferLength) {
                val bufferOffset = (position - bufferStart).toInt()
                System.arraycopy(buffer, bufferOffset, dest, offset, requestedSize)
                return requestedSize
            }

            if (requestedSize > buffer.size) {
                return try {
                    val read = smbFile.read(dest, position, offset, requestedSize)
                    if (read <= 0) -1 else read
                } catch (_: Throwable) {
                    -1
                }
            }

            val toRead = buffer.size.toLong().coerceAtMost(sizeBytes - position).toInt()
            val read = try {
                smbFile.read(buffer, position, 0, toRead)
            } catch (_: Throwable) {
                -1
            }
            if (read <= 0) {
                bufferStart = -1L
                bufferLength = 0
                return -1
            }
            bufferStart = position
            bufferLength = read

            val copyCount = requestedSize.coerceAtMost(read)
            System.arraycopy(buffer, 0, dest, offset, copyCount)
            return copyCount
        }
    }

    override fun getSize(): Long = sizeBytes

    override fun close() {
        closed = true
        synchronized(bufferLock) {
            bufferStart = -1L
            bufferLength = 0
        }
    }
}

private fun loadBitmapFromHttpUrl(url: String): Bitmap? {
    val requestSpec = resolveCredentialedHttpSpec(url) ?: return null
    val requestUrl = buildHttpRequestUri(requestSpec)
    val connection = (URL(requestUrl).openConnection() as? HttpURLConnection) ?: return null
    return try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "SiliconPlayer/1.0 (Android)")
        connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
        connection.setRequestProperty("Connection", "close")
        httpBasicAuthorizationHeader(
            username = requestSpec.username,
            password = requestSpec.password
        )?.let { authHeader ->
            connection.setRequestProperty("Authorization", authHeader)
        }
        if (connection.responseCode !in 200..299) return null
        val data = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > MAX_REMOTE_ARTWORK_BYTES) return null
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        decodeScaledBitmapFromBytes(data)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

private fun findFolderArtworkFile(trackFile: File): File? {
    val parent = trackFile.parentFile ?: return null
    if (!parent.isDirectory) return null

    val allowedNames = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
        "album.jpg", "album.jpeg", "album.png", "album.webp",
        "front.jpg", "front.jpeg", "front.png", "front.webp",
        "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
    )

    return parent.listFiles()
        ?.firstOrNull { it.isFile && allowedNames.contains(it.name.lowercase()) }
}

private fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int = 1024): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun decodeScaledBitmapFromFile(file: File, maxSize: Int = 1024): Bitmap? {
    val path = file.absolutePath
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sampleSize = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sampleSize *= 2
    }
    return sampleSize.coerceAtLeast(1)
}

private const val MAX_REMOTE_ARTWORK_BYTES = 8 * 1024 * 1024

private val FOLDER_ARTWORK_FILE_NAMES = listOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)
