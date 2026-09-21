package com.flopster101.siliconplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow

internal const val RECENT_ARTWORK_CACHE_DIR = "recent_artwork"
private const val RECENT_ARTWORK_THUMB_MAX_SIZE_PX = 240
private const val RECENT_ARTWORK_LARGE_MAX_SIZE_PX = 1024

// Bumped whenever a recents thumbnail file is newly written, so chips already on
// screen re-peek instead of showing the fallback until the list itself changes.
internal val recentArtworkCacheRevision = MutableStateFlow(0L)

internal fun ensureRecentArtworkThumbnailCached(
    context: Context,
    sourceId: String,
    requestUrlHint: String? = null
): String? = ensureRecentArtworkCached(
    context = context,
    sourceId = sourceId,
    requestUrlHint = requestUrlHint,
    requireLarge = false
)

internal fun saveBitmapToRecentArtworkCache(
    context: Context,
    sourceId: String,
    bitmap: Bitmap,
    recycleSource: Boolean = false
): String? {
    if (bitmap.isRecycled) return null
    val normalizedSource = normalizeSourceIdentity(sourceId)?.trim().orEmpty()
    if (normalizedSource.isBlank()) return null
    val cacheRoot = File(context.cacheDir, RECENT_ARTWORK_CACHE_DIR)
    if (!cacheRoot.exists() && !cacheRoot.mkdirs()) return null
    val cacheKey = "${sha1Hex(normalizedSource)}.jpg"
    val largeKey = "${sha1Hex(normalizedSource)}_large.jpg"
    val cacheFile = File(cacheRoot, cacheKey)
    val largeFile = File(cacheRoot, largeKey)

    val largeExists = largeFile.exists() && largeFile.isFile && largeFile.length() > 0L
    if (!largeExists) {
        val largeScaled = scaleBitmapForRecentThumb(bitmap, RECENT_ARTWORK_LARGE_MAX_SIZE_PX)
        val tempLargeFile = File(cacheRoot, "$largeKey.tmp")
        try {
            FileOutputStream(tempLargeFile).use { output ->
                if (largeScaled.compress(Bitmap.CompressFormat.JPEG, 88, output)) {
                    output.fd.sync()
                }
            }
            if (tempLargeFile.length() > 0L) {
                tempLargeFile.renameTo(largeFile)
            } else {
                tempLargeFile.delete()
            }
        } catch (_: Throwable) {
            tempLargeFile.delete()
        } finally {
            if (largeScaled !== bitmap && !largeScaled.isRecycled) {
                largeScaled.recycle()
            }
        }
    }

    val thumbExists = cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L
    if (!thumbExists) {
        val thumbScaled = scaleBitmapForRecentThumb(bitmap, RECENT_ARTWORK_THUMB_MAX_SIZE_PX)
        val tempThumbFile = File(cacheRoot, "$cacheKey.tmp")
        try {
            FileOutputStream(tempThumbFile).use { output ->
                if (thumbScaled.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
                    output.fd.sync()
                }
            }
            if (tempThumbFile.length() > 0L) {
                tempThumbFile.renameTo(cacheFile)
            } else {
                tempThumbFile.delete()
            }
        } catch (_: Throwable) {
            tempThumbFile.delete()
        } finally {
            if (thumbScaled !== bitmap && !thumbScaled.isRecycled) {
                thumbScaled.recycle()
            }
        }
    }

    val thumbReady = cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L
    if (thumbReady && !thumbExists) {
        // A fresh thumbnail appeared for this source: drop stale negative/memory
        // hits and nudge on-screen chips to re-peek.
        RecentThumbNoArtworkCache.remove(cacheKey)
        synchronized(RecentThumbMemoryCache) {
            RecentThumbMemoryCache.remove(cacheKey)
        }
        RecentThumbFileMtimes.remove(cacheKey)
        recentArtworkCacheRevision.value = recentArtworkCacheRevision.value + 1L
    }

    if (recycleSource && !bitmap.isRecycled) {
        bitmap.recycle()
    }

    return if (thumbReady) cacheKey else if (largeFile.exists() && largeFile.length() > 0L) cacheKey else null
}

internal fun ensureRecentArtworkCached(
    context: Context,
    sourceId: String,
    requestUrlHint: String? = null,
    requireLarge: Boolean = false
): String? {
    val normalizedSource = normalizeSourceIdentity(sourceId)?.trim().orEmpty()
    if (normalizedSource.isBlank()) return null
    val cacheRoot = File(context.cacheDir, RECENT_ARTWORK_CACHE_DIR)
    if (!cacheRoot.exists() && !cacheRoot.mkdirs()) return null
    val cacheKey = "${sha1Hex(normalizedSource)}.jpg"
    val largeKey = "${sha1Hex(normalizedSource)}_large.jpg"
    val cacheFile = File(cacheRoot, cacheKey)
    val largeFile = File(cacheRoot, largeKey)

    val thumbExists = cacheFile.exists() && cacheFile.isFile && cacheFile.length() > 0L
    val largeExists = largeFile.exists() && largeFile.isFile && largeFile.length() > 0L

    if (requireLarge) {
        if (largeExists) return cacheKey
    } else {
        if (thumbExists) return cacheKey
    }

    val memoryBitmap = peekCachedArtworkBitmapForSource(
        displayFile = null,
        sourceId = normalizedSource,
        requestUrl = requestUrlHint
    ) ?: peekCachedArtworkBitmapForSource(
        displayFile = null,
        sourceId = sourceId,
        requestUrl = requestUrlHint
    )
    if (memoryBitmap != null && !memoryBitmap.isRecycled) {
        return saveBitmapToRecentArtworkCache(
            context = context,
            sourceId = normalizedSource,
            bitmap = memoryBitmap,
            recycleSource = false
        )
    }

    val sourceFile = resolveRecentArtworkSourceFile(context, normalizedSource)
    val loadedBitmap = loadArtworkBitmapForSource(
        context = context,
        displayFile = sourceFile,
        sourceId = normalizedSource,
        requestUrl = requestUrlHint
    ) ?: (if (sourceId != normalizedSource) {
        loadArtworkBitmapForSource(
            context = context,
            displayFile = sourceFile,
            sourceId = sourceId,
            requestUrl = requestUrlHint
        )
    } else null) ?: (if (sourceFile != null) {
        loadRecentArtworkBitmap(sourceFile)
    } else {
        loadRemoteEmbeddedArtworkBitmap(
            sourceId = normalizedSource,
            requestUrlHint = requestUrlHint
        )
    })

    if (loadedBitmap != null && !loadedBitmap.isRecycled) {
        return saveBitmapToRecentArtworkCache(
            context = context,
            sourceId = normalizedSource,
            bitmap = loadedBitmap,
            recycleSource = false
        )
    }

    return if (cacheFile.exists() && cacheFile.length() > 0L) cacheKey else if (largeFile.exists() && largeFile.length() > 0L) cacheKey else null
}

internal fun recentArtworkFile(
    context: Context,
    cacheKey: String?,
    preferLarge: Boolean = false
): File? {
    val normalizedKey = cacheKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    val cacheDir = File(context.cacheDir, RECENT_ARTWORK_CACHE_DIR)
    if (preferLarge) {
        val baseName = normalizedKey.substringBeforeLast('.')
        val ext = normalizedKey.substringAfterLast('.', "jpg")
        val largeFile = File(cacheDir, "${baseName}_large.$ext")
        if (largeFile.exists() && largeFile.isFile && largeFile.length() > 0L) {
            return largeFile
        }
    }
    val file = File(cacheDir, normalizedKey)
    return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
}

internal fun recentArtworkThumbnailFile(
    context: Context,
    cacheKey: String?
): File? = recentArtworkFile(context, cacheKey, preferLarge = false)

// In-memory cache for decoded recents chips: without it every chip
// recomposition/scroll re-decoded the JPEG and allocated a fresh native
// bitmap, which kept the NativeAlloc GC churning on the home screen.
private const val RECENT_THUMB_MEMORY_CACHE_MAX_KB = 8 * 1024
private const val RECENT_THUMB_NO_ART_CACHE_MAX_ENTRIES = 1000

private object RecentThumbMemoryCache : LruCache<String, ImageBitmap>(RECENT_THUMB_MEMORY_CACHE_MAX_KB) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
    }
}

// Cache file mtimes so a rewritten thumbnail (new artwork for the same
// source) invalidates the decoded bitmap.
private val RecentThumbFileMtimes = ConcurrentHashMap<String, Long>()

private object RecentThumbNoArtworkCache : LruCache<String, Boolean>(RECENT_THUMB_NO_ART_CACHE_MAX_ENTRIES)

private val RecentThumbInFlightLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
private val RecentThumbLoadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

internal fun peekRecentArtworkThumbnail(context: Context, cacheKey: String?): ImageBitmap? {
    val key = cacheKey?.trim()?.takeUnless { it.isBlank() } ?: return null
    if (RecentThumbNoArtworkCache.get(key) == true) return null
    val cached = synchronized(RecentThumbMemoryCache) {
        RecentThumbMemoryCache.get(key)
    }?.takeUnless { it.asAndroidBitmap().isRecycled } ?: return null
    val fileMtime = recentArtworkThumbnailFile(context, key)?.lastModified() ?: 0L
    if (fileMtime != RecentThumbFileMtimes[key]) {
        synchronized(RecentThumbMemoryCache) {
            RecentThumbMemoryCache.remove(key)
        }
        RecentThumbFileMtimes.remove(key)
        return null
    }
    return cached
}

internal suspend fun loadRecentArtworkThumbnail(
    context: Context,
    cacheKey: String?
): ImageBitmap? {
    val key = cacheKey?.trim()?.takeUnless { it.isBlank() } ?: return null
    peekRecentArtworkThumbnail(context, key)?.let { return it }
    if (RecentThumbNoArtworkCache.get(key) == true) return null

    val deferred = synchronized(RecentThumbInFlightLoads) {
        RecentThumbInFlightLoads[key]?.let { return@synchronized it }
        val newDeferred = RecentThumbLoadScope.async {
            try {
                val file = recentArtworkThumbnailFile(context, key)
                val decoded = file?.let { BitmapFactory.decodeFile(it.absolutePath) }?.asImageBitmap()
                if (decoded != null) {
                    RecentThumbFileMtimes[key] = file?.lastModified() ?: 0L
                    synchronized(RecentThumbMemoryCache) {
                        RecentThumbMemoryCache.put(key, decoded)
                    }
                } else {
                    RecentThumbNoArtworkCache.put(key, true)
                }
                decoded
            } finally {
                RecentThumbInFlightLoads.remove(key)
            }
        }
        RecentThumbInFlightLoads[key] = newDeferred
        newDeferred
    }
    return try {
        deferred.await()
    } catch (_: Throwable) {
        null
    }
}

private fun resolveRecentArtworkSourceFile(context: Context, sourceId: String): File? {
    val uri = Uri.parse(sourceId)
    return when (uri.scheme?.lowercase(Locale.ROOT)) {
        null -> File(sourceId).takeIf { it.exists() && it.isFile }
        "file" -> uri.path?.let(::File)?.takeIf { it.exists() && it.isFile }
        "http", "https", "smb" -> {
            findExistingCachedFileForSource(File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR), sourceId)
                ?.takeIf { it.exists() && it.isFile }
        }
        else -> File(sourceId).takeIf { it.exists() && it.isFile }
    }
}

private fun loadRecentArtworkBitmap(trackFile: File): Bitmap? {
    loadEmbeddedArtworkBitmap(trackFile)?.let { return it }
    findFolderArtworkNearTrack(trackFile)?.let { artworkFile ->
        decodeScaledBitmapFromFile(artworkFile)?.let { return it }
    }
    return null
}

private fun loadEmbeddedArtworkBitmap(file: File): Bitmap? {
    synchronized(MediaMetadataRetrieverGlobalLock) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmapFromBytes(embedded)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun loadRemoteEmbeddedArtworkBitmap(
    sourceId: String,
    requestUrlHint: String?
): Bitmap? {
    val requestSpec = resolveCredentialedHttpSpec(
        input = sourceId,
        credentialHint = requestUrlHint
    ) ?: return null
    val requestUrl = stripUrlFragment(buildHttpRequestUri(requestSpec))
    val headers = mutableMapOf(
        "User-Agent" to "SiliconPlayer/1.0 (Android)",
        "Icy-MetaData" to "1"
    )
    httpBasicAuthorizationHeader(
        username = requestSpec.username,
        password = requestSpec.password
    )?.let { authHeader ->
        headers["Authorization"] = authHeader
    }
    synchronized(MediaMetadataRetrieverGlobalLock) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requestUrl, headers)
            val embedded = retriever.embeddedPicture ?: return null
            decodeScaledBitmapFromBytes(embedded)
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }
}

private fun findFolderArtworkNearTrack(trackFile: File): File? {
    val parent = trackFile.parentFile ?: return null
    if (!parent.isDirectory) return null
    return parent.listFiles()
        ?.firstOrNull { file ->
            file.isFile && file.name.lowercase(Locale.ROOT) in RECENT_FOLDER_ARTWORK_NAMES
        }
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
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}

private fun scaleBitmapForRecentThumb(bitmap: Bitmap, maxSize: Int): Bitmap {
    val srcWidth = bitmap.width.coerceAtLeast(1)
    val srcHeight = bitmap.height.coerceAtLeast(1)
    if (srcWidth <= maxSize && srcHeight <= maxSize) return bitmap
    val scale = maxSize.toFloat() / maxOf(srcWidth, srcHeight).toFloat()
    val targetWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
    val targetHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private val RECENT_FOLDER_ARTWORK_NAMES = setOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)
