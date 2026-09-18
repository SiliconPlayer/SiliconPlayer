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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal const val LIBRARY_ARTWORK_CACHE_DIR = "library_artwork_thumbnails"
private const val LIBRARY_ARTWORK_THUMB_SIZE_PX = 360
private const val LIBRARY_THUMBNAIL_MEMORY_CACHE_MAX_KB = 32 * 1024
private const val LIBRARY_NO_ART_CACHE_MAX_ENTRIES = 2000
private const val LIBRARY_ARTWORK_MAX_DISK_FILES = 1200
private const val LIBRARY_ARTWORK_MAX_DISK_BYTES = 64L * 1024L * 1024L

private object LibraryThumbnailMemoryCache : LruCache<String, ImageBitmap>(LIBRARY_THUMBNAIL_MEMORY_CACHE_MAX_KB) {
    override fun sizeOf(key: String, value: ImageBitmap): Int {
        return (value.asAndroidBitmap().byteCount / 1024).coerceAtLeast(1)
    }
}

private object LibraryNoArtworkMemoryCache : LruCache<String, Boolean>(LIBRARY_NO_ART_CACHE_MAX_ENTRIES)

private val FolderArtworkLookupCache = ConcurrentHashMap<String, String>()
private val InFlightLoads = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
private val LibraryThumbnailScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
private val LibraryThumbnailDiskPruneCounter = AtomicInteger(0)

private val ALLOWED_FOLDER_ARTWORK_NAMES = setOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)

internal fun peekLibraryThumbnail(artworkPath: String?): ImageBitmap? {
    val path = artworkPath?.trim()?.takeUnless { it.isBlank() } ?: return null
    if (LibraryNoArtworkMemoryCache.get(path) == true) return null
    return synchronized(LibraryThumbnailMemoryCache) {
        val cached = LibraryThumbnailMemoryCache.get(path)
        if (cached != null && !cached.asAndroidBitmap().isRecycled) {
            cached
        } else {
            null
        }
    }
}

internal suspend fun loadLibraryThumbnail(
    context: Context,
    artworkPath: String?
): ImageBitmap? {
    val path = artworkPath?.trim()?.takeUnless { it.isBlank() } ?: return null
    peekLibraryThumbnail(path)?.let { return it }
    if (LibraryNoArtworkMemoryCache.get(path) == true) return null

    val deferred = synchronized(InFlightLoads) {
        InFlightLoads[path]?.let { return@synchronized it }
        val newDeferred = LibraryThumbnailScope.async {
            try {
                loadLibraryThumbnailInternal(context, path)
            } finally {
                InFlightLoads.remove(path)
            }
        }
        InFlightLoads[path] = newDeferred
        newDeferred
    }
    return try {
        deferred.await()
    } catch (_: Throwable) {
        null
    }
}

private fun loadLibraryThumbnailInternal(context: Context, artworkPath: String): ImageBitmap? {
    val file = resolveArtworkLocalFile(artworkPath)
    if (file == null) {
        LibraryNoArtworkMemoryCache.put(artworkPath, true)
        return null
    }

    val cacheRoot = File(context.cacheDir, LIBRARY_ARTWORK_CACHE_DIR)
    if (!cacheRoot.exists()) {
        cacheRoot.mkdirs()
    }

    val fileLength = file.length()
    val fileMtime = file.lastModified()
    val parentMtime = file.parentFile?.takeIf { it.isDirectory }?.lastModified() ?: 0L
    val cacheStamp = "${file.absolutePath}|$fileLength|$fileMtime|$parentMtime"
    val cacheHash = sha1Hex(cacheStamp)
    val thumbFile = File(cacheRoot, "$cacheHash.jpg")
    val noArtFile = File(cacheRoot, "$cacheHash.noart")

    if (noArtFile.exists()) {
        LibraryNoArtworkMemoryCache.put(artworkPath, true)
        return null
    }

    if (thumbFile.exists() && thumbFile.length() > 0L) {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val cachedBitmap = BitmapFactory.decodeFile(thumbFile.absolutePath, options)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            thumbFile.setLastModified(System.currentTimeMillis())
            val imageBitmap = cachedBitmap.asImageBitmap()
            synchronized(LibraryThumbnailMemoryCache) {
                LibraryThumbnailMemoryCache.put(artworkPath, imageBitmap)
            }
            return imageBitmap
        }
    }

    val ext = file.extension.lowercase(Locale.ROOT)
    val isImageFile = ext in listOf("jpg", "jpeg", "png", "webp")
    val extractedBitmap: Bitmap? = if (isImageFile) {
        decodeScaledBitmapFromFile(file, LIBRARY_ARTWORK_THUMB_SIZE_PX)
    } else {
        var bmp: Bitmap? = synchronized(MediaMetadataRetrieverGlobalLock) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.embeddedPicture?.let { data ->
                    decodeScaledBitmapFromBytes(data, LIBRARY_ARTWORK_THUMB_SIZE_PX)
                }
            } catch (_: Throwable) {
                null
            } finally {
                retriever.release()
            }
        }
        if (bmp == null) {
            val folderArt = findFolderArtworkCached(file)
            if (folderArt != null) {
                bmp = decodeScaledBitmapFromFile(folderArt, LIBRARY_ARTWORK_THUMB_SIZE_PX)
            }
        }
        bmp
    }

    if (extractedBitmap != null && !extractedBitmap.isRecycled) {
        val scaled = scaleBitmapForLibraryThumb(extractedBitmap, LIBRARY_ARTWORK_THUMB_SIZE_PX)
        saveBitmapToLibraryArtworkCache(cacheRoot, thumbFile, scaled)
        val imageBitmap = scaled.asImageBitmap()
        synchronized(LibraryThumbnailMemoryCache) {
            LibraryThumbnailMemoryCache.put(artworkPath, imageBitmap)
        }
        if (LibraryThumbnailDiskPruneCounter.incrementAndGet() % 50 == 0) {
            pruneLibraryArtworkCache(cacheRoot)
        }
        return imageBitmap
    } else {
        try {
            noArtFile.createNewFile()
        } catch (_: Throwable) {}
        LibraryNoArtworkMemoryCache.put(artworkPath, true)
        return null
    }
}

internal fun clearLibraryArtworkThumbnailCache(context: Context): Int {
    synchronized(LibraryThumbnailMemoryCache) {
        LibraryThumbnailMemoryCache.evictAll()
    }
    LibraryNoArtworkMemoryCache.evictAll()
    FolderArtworkLookupCache.clear()

    val cacheRoot = File(context.cacheDir, LIBRARY_ARTWORK_CACHE_DIR)
    if (!cacheRoot.exists() || !cacheRoot.isDirectory) return 0
    val files = cacheRoot.listFiles().orEmpty().filter { it.isFile }
    var deletedCount = 0
    files.forEach { file ->
        if (file.delete()) {
            deletedCount++
        }
    }
    return deletedCount
}

private fun resolveArtworkLocalFile(path: String): File? {
    val trimmed = path.trim()
    if (trimmed.isBlank()) return null
    val localPath = when {
        trimmed.startsWith("file://", ignoreCase = true) -> Uri.parse(trimmed).path ?: trimmed
        else -> trimmed
    }
    return File(localPath).takeIf { it.exists() && it.isFile }
}

private fun findFolderArtworkCached(trackFile: File): File? {
    val parent = trackFile.parentFile ?: return null
    if (!parent.isDirectory) return null
    val parentKey = "${parent.absolutePath}|${parent.lastModified()}"
    val cachedPath = FolderArtworkLookupCache[parentKey]
    if (cachedPath != null) {
        return if (cachedPath.isEmpty()) null else File(cachedPath)
    }
    val found = parent.listFiles()?.firstOrNull { candidate ->
        candidate.isFile && candidate.name.lowercase(Locale.ROOT) in ALLOWED_FOLDER_ARTWORK_NAMES
    }
    FolderArtworkLookupCache[parentKey] = found?.absolutePath ?: ""
    return found
}

private fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int): Bitmap? {
    if (data.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

private fun decodeScaledBitmapFromFile(file: File, maxSize: Int): Bitmap? {
    if (!file.exists() || !file.isFile) return null
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

private fun scaleBitmapForLibraryThumb(bitmap: Bitmap, maxSize: Int): Bitmap {
    val srcWidth = bitmap.width.coerceAtLeast(1)
    val srcHeight = bitmap.height.coerceAtLeast(1)
    if (srcWidth <= maxSize && srcHeight <= maxSize) return bitmap
    val scale = maxSize.toFloat() / maxOf(srcWidth, srcHeight).toFloat()
    val targetWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
    val targetHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun saveBitmapToLibraryArtworkCache(cacheRoot: File, thumbFile: File, bitmap: Bitmap) {
    val tempFile = File(cacheRoot, "${thumbFile.name}.tmp")
    try {
        FileOutputStream(tempFile).use { out ->
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)) {
                out.fd.sync()
            }
        }
        if (tempFile.length() > 0L) {
            tempFile.renameTo(thumbFile)
            thumbFile.setLastModified(System.currentTimeMillis())
        } else {
            tempFile.delete()
        }
    } catch (_: Throwable) {
        tempFile.delete()
    }
}

private fun pruneLibraryArtworkCache(cacheRoot: File) {
    try {
        val files = cacheRoot.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= LIBRARY_ARTWORK_MAX_DISK_FILES) {
            val totalBytes = files.sumOf { it.length() }
            if (totalBytes <= LIBRARY_ARTWORK_MAX_DISK_BYTES) return
        }
        val sortedByMtime = files.sortedBy { it.lastModified() }
        val toDeleteCount = (files.size - LIBRARY_ARTWORK_MAX_DISK_FILES + 50).coerceAtLeast(files.size / 5)
        for (i in 0 until toDeleteCount.coerceAtMost(sortedByMtime.size)) {
            sortedByMtime[i].delete()
        }
    } catch (_: Throwable) {}
}
