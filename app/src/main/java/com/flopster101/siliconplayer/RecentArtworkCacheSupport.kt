package com.flopster101.siliconplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

internal const val RECENT_ARTWORK_CACHE_DIR = "recent_artwork"
private const val RECENT_ARTWORK_THUMB_MAX_SIZE_PX = 240
private const val RECENT_ARTWORK_LARGE_MAX_SIZE_PX = 1024

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

    val sourceFile = resolveRecentArtworkSourceFile(context, normalizedSource)
    val bitmap = when {
        sourceFile != null -> loadRecentArtworkBitmap(sourceFile)
        else -> loadRemoteEmbeddedArtworkBitmap(
            sourceId = normalizedSource,
            requestUrlHint = requestUrlHint
        )
    } ?: return if (thumbExists) cacheKey else null

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

    if (!bitmap.isRecycled) {
        bitmap.recycle()
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
