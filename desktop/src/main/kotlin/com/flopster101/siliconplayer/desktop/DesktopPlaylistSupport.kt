package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.flopster101.siliconplayer.RECENT_ARTWORK_CACHE_DIR
import com.flopster101.siliconplayer.RECENT_ARTWORK_LARGE_MAX_SIZE_PX
import com.flopster101.siliconplayer.RECENT_ARTWORK_THUMB_MAX_SIZE_PX
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.PlaylistExportFormat
import com.flopster101.siliconplayer.PlaylistExportRegistry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.recentArtworkCacheKeyForSource
import com.flopster101.siliconplayer.recentLargeArtworkCacheKeyForSource
import com.flopster101.siliconplayer.parsePlaylistDocument
import com.flopster101.siliconplayer.platform.ArtworkCacheSupport
import com.flopster101.siliconplayer.platform.PlaylistPlatformSupport
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.suggestedPlaylistExportFileName
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

private const val PLAYLIST_COVERS_DIR = "playlist_covers"
private const val MAX_COVER_DIMENSION = 1024

private val IMAGE_FILE_FILTER = FileNameExtensionFilter(
    "Images",
    "jpg",
    "jpeg",
    "png",
    "webp",
    "bmp"
)

private val PLAYLIST_FILE_FILTER = FileNameExtensionFilter("Playlists", "m3u", "m3u8")

/**
 * Desktop artwork pipeline: recents thumbnails are memoized on disk under the
 * shared recent-artwork key, so remote (SMB/HTTP) artwork survives restarts.
 * Mirrors Android's RecentArtworkCacheSupport scale/quality ladder.
 */
internal val desktopRecentArtworkCacheRevision = MutableStateFlow(0L)

internal fun desktopRecentArtworkCacheDir(cacheDir: File): File =
    File(cacheDir, RECENT_ARTWORK_CACHE_DIR).apply { mkdirs() }

@Composable
internal fun rememberDesktopArtworkCacheSupport(cacheDir: File): ArtworkCacheSupport {
    return remember(cacheDir) {
        val recentArtworkCacheDir = desktopRecentArtworkCacheDir(cacheDir)
        object : ArtworkCacheSupport {
            override suspend fun ensureThumbnailCached(sourceId: String, requestUrlHint: String?): String? =
                withContext(Dispatchers.IO) {
                    ensureDesktopRecentArtworkCached(recentArtworkCacheDir, sourceId, requestUrlHint, false)
                }

            override suspend fun ensureArtworkCached(sourceId: String, requireLarge: Boolean): String? =
                withContext(Dispatchers.IO) {
                    ensureDesktopRecentArtworkCached(recentArtworkCacheDir, sourceId, null, requireLarge)
                }

            override fun peekGeneratedKey(sourceId: String): String? =
                recentArtworkCacheKeyForSource(sourceId)?.takeIf { File(recentArtworkCacheDir, it).isFile }

            override fun cacheFile(cacheKey: String, preferLarge: Boolean): File? =
                desktopRecentArtworkCacheFile(recentArtworkCacheDir, cacheKey, preferLarge)

            override suspend fun loadImageFile(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
                decodeImageFile(file)
            }

            override suspend fun loadArtworkForSource(sourceId: String, requestUrl: String?): ImageBitmap? =
                withContext(Dispatchers.IO) {
                    resolvePlaylistEntryLocalFile(sourceId)
                        ?.let { DesktopArtworkSupport.loadArtworkForFile(it) }
                        ?: DesktopArtworkSupport.loadArtworkForSource(null, sourceId, requestUrl)
                }

            override fun peekLibraryArtwork(path: String?): ImageBitmap? =
                path?.let { DesktopArtworkSupport.loadArtworkForFile(File(it)) }

            override suspend fun loadLibraryArtwork(path: String): ImageBitmap? =
                withContext(Dispatchers.IO) {
                    DesktopArtworkSupport.loadArtworkForFile(File(path))
                }
        }
    }
}

/**
 * Desktop playlist file/cover pickers backed by Swing choosers. The port has no system document
 * contract, so [PlaylistPlatformSupport.supportsSystemPicker] stays `false` and the shared UI
 * falls back to its in-app file picker.
 */
@Composable
internal fun rememberDesktopPlaylistPlatformSupport(cacheDir: File): PlaylistPlatformSupport {
    return remember(cacheDir) {
        val coversDir = File(cacheDir, PLAYLIST_COVERS_DIR).apply { mkdirs() }
        object : PlaylistPlatformSupport {
            override val supportsSystemPicker: Boolean = false
            override val coversDirectory: File = coversDir

            override suspend fun pickCoverImage(destFile: File): Boolean = withContext(Dispatchers.IO) {
                val source = chooseOpenFile("Choose cover image", IMAGE_FILE_FILTER)
                    ?: return@withContext false
                runCatching {
                    destFile.parentFile?.mkdirs()
                    normalizeCoverImage(source, destFile)
                    destFile.isFile && destFile.length() > 0L
                }.getOrDefault(false)
            }

            override suspend fun rotateCoverFile(file: File, degrees: Float): Boolean =
                withContext(Dispatchers.IO) {
                    runCatching { rotateImageFile(file, degrees) }.getOrDefault(false)
                }

            override suspend fun pickPlaylistDocument(): ParsedPlaylistDocument? =
                withContext(Dispatchers.IO) {
                    val file = chooseOpenFile("Import playlist", PLAYLIST_FILE_FILTER)
                        ?: return@withContext null
                    parsePlaylistDocument(file, allowUnresolvedFiles = true)
                }

            override suspend fun exportPlaylist(
                playlist: StoredPlaylist,
                format: PlaylistExportFormat
            ): Boolean = withContext(Dispatchers.IO) {
                val target = chooseSaveFile(
                    suggestedPlaylistExportFileName(playlist, format),
                    format.extension
                ) ?: return@withContext false
                runCatching {
                    target.writeText(PlaylistExportRegistry.exporterFor(format).export(playlist))
                    true
                }.getOrDefault(false)
            }

            override suspend fun sharePlaylist(
                playlist: StoredPlaylist,
                format: PlaylistExportFormat
            ): Boolean = false
        }
    }
}

internal fun ensureDesktopRecentArtworkCached(
    cacheRoot: File,
    sourceId: String,
    requestUrlHint: String?,
    requireLarge: Boolean
): String? {
    val cacheKey = recentArtworkCacheKeyForSource(sourceId) ?: return null
    val largeKey = recentLargeArtworkCacheKeyForSource(sourceId) ?: return null
    if (!cacheRoot.exists() && !cacheRoot.mkdirs()) return null
    val thumbFile = File(cacheRoot, cacheKey)
    val largeFile = File(cacheRoot, largeKey)
    val thumbReady = thumbFile.isFile && thumbFile.length() > 0L
    val largeReady = largeFile.isFile && largeFile.length() > 0L
    if (!requireLarge && thumbReady) return cacheKey
    if (requireLarge && largeReady) return cacheKey
    val bitmap = resolvePlaylistEntryLocalFile(sourceId)
        ?.let { DesktopArtworkSupport.loadArtworkForFile(it) }
        ?: DesktopArtworkSupport.loadArtworkForSource(null, sourceId, requestUrlHint)
        ?: return if (thumbReady || largeReady) cacheKey else null
    if (!thumbReady) {
        writeScaledRecentArtwork(thumbFile, bitmap, RECENT_ARTWORK_THUMB_MAX_SIZE_PX, 82)
    }
    if (!largeReady) {
        writeScaledRecentArtwork(largeFile, bitmap, RECENT_ARTWORK_LARGE_MAX_SIZE_PX, 88)
    }
    if (!thumbReady && thumbFile.isFile && thumbFile.length() > 0L) {
        desktopRecentArtworkCacheRevision.value = desktopRecentArtworkCacheRevision.value + 1L
    }
    return cacheKey
}

internal fun desktopRecentArtworkCacheFile(cacheRoot: File, cacheKey: String?, preferLarge: Boolean): File? {
    val key = cacheKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    if (preferLarge) {
        val largeFile = File(cacheRoot, key.substringBeforeLast('.') + "_large." + key.substringAfterLast('.', "jpg"))
        if (largeFile.isFile && largeFile.length() > 0L) return largeFile
    }
    return File(cacheRoot, key).takeIf { it.isFile && it.length() > 0L }
}

private fun writeScaledRecentArtwork(dest: File, bitmap: ImageBitmap, maxSize: Int, quality: Int): Boolean {
    if (dest.isFile && dest.length() > 0L) return true
    return runCatching {
        val pixels = bitmap.toPixelMap()
        val width = pixels.width.coerceAtLeast(1)
        val height = pixels.height.coerceAtLeast(1)
        val argb = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        argb.setRGB(0, 0, width, height, pixels.buffer, 0, pixels.stride)
        val scale = maxSize.toDouble() / maxOf(width, height).coerceAtLeast(1)
        val targetWidth = if (scale >= 1.0) width else (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = if (scale >= 1.0) height else (height * scale).toInt().coerceAtLeast(1)
        val rgb = java.awt.image.BufferedImage(targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        try {
            graphics.drawImage(
                argb.getScaledInstance(targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH),
                0, 0, null
            )
        } finally {
            graphics.dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = (quality.coerceIn(0, 100)) / 100f
        }
        val temp = File(dest.parentFile, dest.name + ".tmp")
        try {
            temp.outputStream().use { output ->
                writer.output = ImageIO.createImageOutputStream(output)
                writer.write(null, IIOImage(rgb, null, null), params)
            }
            temp.length() > 0L && temp.renameTo(dest)
        } finally {
            writer.dispose()
            if (temp.exists() && !dest.isFile) temp.delete()
        }
    }.getOrDefault(false)
}

internal fun looksLikeRemoteSourceId(value: String): Boolean = value.contains("://")

internal fun decodeRecentArtworkFile(cacheKey: String, file: File): ImageBitmap? =
    decodeImageFile(file)?.also { DesktopArtworkSupport.rememberMemoryArtwork(cacheKey, it) }

private fun decodeImageFile(file: File): ImageBitmap? {
    if (!file.isFile || file.length() == 0L) return null
    return runCatching {
        org.jetbrains.skia.Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()
}

private fun normalizeCoverImage(source: File, destFile: File) {
    val image = ImageIO.read(source) ?: error("Unsupported image file")
    val maxDimension = maxOf(image.width, image.height)
    val scaled = if (maxDimension > MAX_COVER_DIMENSION) {
        val scale = MAX_COVER_DIMENSION.toDouble() / maxDimension
        image.getScaledInstance(
            (image.width * scale).toInt().coerceAtLeast(1),
            (image.height * scale).toInt().coerceAtLeast(1),
            java.awt.Image.SCALE_SMOOTH
        )
    } else {
        image
    }
    val output = java.awt.image.BufferedImage(
        scaled.getWidth(null),
        scaled.getHeight(null),
        java.awt.image.BufferedImage.TYPE_INT_RGB
    )
    val graphics = output.createGraphics()
    try {
        graphics.drawImage(scaled, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    ImageIO.write(output, "jpg", destFile)
}

private fun rotateImageFile(file: File, degrees: Float): Boolean {
    val image = ImageIO.read(file) ?: return false
    val radians = Math.toRadians(degrees.toDouble())
    val sin = kotlin.math.abs(kotlin.math.sin(radians))
    val cos = kotlin.math.abs(kotlin.math.cos(radians))
    val width = (image.width * cos + image.height * sin).toInt().coerceAtLeast(1)
    val height = (image.width * sin + image.height * cos).toInt().coerceAtLeast(1)
    val rotated = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
    val graphics = rotated.createGraphics()
    try {
        graphics.translate((width - image.width) / 2.0, (height - image.height) / 2.0)
        graphics.rotate(radians, image.width / 2.0, image.height / 2.0)
        graphics.drawImage(image, 0, 0, null)
    } finally {
        graphics.dispose()
    }
    ImageIO.write(rotated, "jpg", file)
    return true
}

private fun chooseOpenFile(title: String, filter: FileNameExtensionFilter): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.FILES_ONLY
        fileFilter = filter
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun chooseSaveFile(suggestedName: String, extension: String): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Export playlist"
        fileSelectionMode = JFileChooser.FILES_ONLY
        selectedFile = File(suggestedName)
    }
    if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return null
    val target = chooser.selectedFile
    return if (target.extension.isEmpty()) File("${target.absolutePath}.$extension") else target
}
