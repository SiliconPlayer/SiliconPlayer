package com.flopster101.siliconplayer
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.hierynomus.msfscc.fileinformation.FileDirectoryInformation
import com.hierynomus.smbj.share.DiskShare
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val ARTWORK_MEMORY_CACHE_MAX_KB = 24 * 1024

private object ArtworkBitmapMemoryCache : LruCache<String, Bitmap>(ARTWORK_MEMORY_CACHE_MAX_KB) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return (value.byteCount / 1024).coerceAtLeast(1)
    }
}

private object LocalFileNoArtworkCache : LruCache<String, Boolean>(1000)

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
    val keys = mutableListOf<String>()
    artworkCacheKeyForSource(displayFile, sourceId, requestUrl)?.let { keys.add(it) }
    sourceId?.trim()?.takeUnless { it.isBlank() }?.let {
        keys.add(it)
        normalizeSourceIdentity(it)?.let { norm -> keys.add(norm) }
    }
    requestUrl?.trim()?.takeUnless { it.isBlank() }?.let {
        keys.add(it)
        normalizeSourceIdentity(it)?.let { norm -> keys.add(norm) }
    }
    displayFile?.absolutePath?.trim()?.takeUnless { it.isBlank() }?.let { keys.add(it) }

    return synchronized(ArtworkBitmapMemoryCache) {
        for (key in keys) {
            val bitmap = ArtworkBitmapMemoryCache.get(key)
            if (bitmap != null && !bitmap.isRecycled) {
                return@synchronized bitmap
            }
        }
        null
    }
}

private fun cacheArtworkBitmapForSource(
    displayFile: File?,
    sourceId: String?,
    requestUrl: String? = null,
    bitmap: Bitmap?
) {
    val stableBitmap = bitmap?.takeUnless { it.isRecycled } ?: return
    val keys = mutableSetOf<String>()
    artworkCacheKeyForSource(displayFile, sourceId, requestUrl)?.let { keys.add(it) }
    sourceId?.trim()?.takeUnless { it.isBlank() }?.let {
        keys.add(it)
        normalizeSourceIdentity(it)?.let { norm -> keys.add(norm) }
    }
    requestUrl?.trim()?.takeUnless { it.isBlank() }?.let {
        keys.add(it)
        normalizeSourceIdentity(it)?.let { norm -> keys.add(norm) }
    }
    displayFile?.absolutePath?.trim()?.takeUnless { it.isBlank() }?.let { keys.add(it) }

    synchronized(ArtworkBitmapMemoryCache) {
        for (key in keys) {
            ArtworkBitmapMemoryCache.put(key, stableBitmap)
        }
    }
}

internal fun loadArtworkForFile(file: File): ImageBitmap? {
    if (LocalFileNoArtworkCache.get(file.absolutePath) == true) return null
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
    LocalFileNoArtworkCache.put(file.absolutePath, true)
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
    // A streaming track can report its local cache path as the source, leaving
    // the request URL as the only remote identity.
    val remoteScheme = when {
        scheme == "http" || scheme == "https" || scheme == "smb" -> scheme
        requestScheme == "http" || requestScheme == "https" || requestScheme == "smb" -> requestScheme
        else -> null
    }
    val remoteId = if (remoteScheme == scheme) normalized else normalizedRequestUrl
    val isRemote = remoteScheme != null

    fun onBitmapLoaded(bitmap: Bitmap): Bitmap {
        cacheArtworkBitmapForSource(displayFile, sourceId, requestUrl, bitmap)
        val persistSource = sourceId ?: requestUrl ?: displayFile?.absolutePath
        if (!persistSource.isNullOrBlank()) {
            saveBitmapToRecentArtworkCache(context, persistSource, bitmap, recycleSource = false)
        }
        return bitmap
    }

    displayFile?.takeIf { it.exists() && it.isFile }?.let { local ->
        loadEmbeddedArtwork(local)?.let {
            return onBitmapLoaded(it)
        }
        findFolderArtworkFile(local)?.let { folderImage ->
            decodeScaledBitmapFromFile(folderImage)?.let {
                return onBitmapLoaded(it)
            }
        }
    }

    // Artwork pulled from the remote folder is cached beside the audio, so a
    // recents entry resolves without reaching the share again.
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)

    fun loadCachedSourceArtwork(cachedSourceId: String?): Bitmap? =
        cachedSourceArtworkFile(cacheRoot, cachedSourceId)
            ?.let { decodeScaledBitmapFromFile(it) }

    if (!isRemote) {
        loadCachedSourceArtwork(displayFile?.absolutePath?.let(::resolveCachedRemoteSourceId))
            ?.let { return onBitmapLoaded(it) }
    }

    if (isRemote && !remoteId.isNullOrBlank()) {
        findExistingCachedFileForSource(cacheRoot, remoteId)?.let { cached ->
            loadEmbeddedArtwork(cached)?.let {
                return onBitmapLoaded(it)
            }
            findFolderArtworkFile(cached)?.let { folderImage ->
                decodeScaledBitmapFromFile(folderImage)?.let {
                    return onBitmapLoaded(it)
                }
            }
        }
        loadEmbeddedArtworkViaDecoder(artworkRequestUrl)?.let {
            return onBitmapLoaded(it)
        }
        loadCachedSourceArtwork(remoteId)?.let { return onBitmapLoaded(it) }
        if (remoteScheme == "smb" && !artworkRequestUrl.isNullOrBlank()) {
            loadFolderArtworkFromSmb(context, artworkRequestUrl, remoteId)?.let {
                return onBitmapLoaded(it)
            }
        }
        if ((remoteScheme == "http" || remoteScheme == "https") && !artworkRequestUrl.isNullOrBlank()) {
            loadFolderArtworkFromHttpSource(artworkRequestUrl)?.let {
                return onBitmapLoaded(it)
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

// The decoder reads the cover out of the container header, so a remote probe
// never walks the body the way MediaMetadataRetriever does.
private fun loadEmbeddedArtworkViaDecoder(requestUri: String?): Bitmap? {
    if (requestUri.isNullOrBlank()) return null
    return NativeBridge.probeArtwork(requestUri)?.let { decodeScaledBitmapFromBytes(it) }
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

private fun loadFolderArtworkFromSmb(
    context: Context,
    requestUri: String,
    sourceId: String?
): Bitmap? {
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
                val bytes = readSmbFileBytes(share, artworkPath)
                    ?: return@withAppSmbSession null
                writeCachedSourceArtwork(
                    sourceCacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR),
                    sourceId = sourceId,
                    bytes = bytes
                )
                decodeScaledBitmapFromBytes(bytes)
            } finally {
                runCatching { share.close() }
            }
        }
    } catch (_: Throwable) {
        null
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

    return parent.listFiles()
        ?.firstOrNull { it.isFile && FOLDER_ARTWORK_FILE_NAMES.contains(it.name.lowercase()) }
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
