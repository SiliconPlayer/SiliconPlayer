package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.PlaylistExportFormat
import com.flopster101.siliconplayer.PlaylistExportRegistry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.data.sha1Hex
import com.flopster101.siliconplayer.parsePlaylistDocument
import com.flopster101.siliconplayer.platform.ArtworkCacheSupport
import com.flopster101.siliconplayer.platform.PlaylistPlatformSupport
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.suggestedPlaylistExportFileName
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ARTWORK_CACHE_DIR = "artwork"
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

/** Cache directory holding extracted/generated playlist and library artwork. */
internal fun desktopArtworkCacheDir(cacheDir: File): File =
    File(cacheDir, ARTWORK_CACHE_DIR).apply { mkdirs() }

/**
 * Desktop artwork pipeline: covers are extracted from local files and memoized on disk keyed by
 * the source path, since the port has no MediaStore-style artwork index.
 */
@Composable
internal fun rememberDesktopArtworkCacheSupport(cacheDir: File): ArtworkCacheSupport {
    return remember(cacheDir) {
        val artworkCacheDir = desktopArtworkCacheDir(cacheDir)
        object : ArtworkCacheSupport {
            override suspend fun ensureThumbnailCached(sourceId: String, requestUrlHint: String?): String? =
                withContext(Dispatchers.IO) { ensureGeneratedArtwork(artworkCacheDir, sourceId) }

            override suspend fun ensureArtworkCached(sourceId: String, requireLarge: Boolean): String? =
                withContext(Dispatchers.IO) { ensureGeneratedArtwork(artworkCacheDir, sourceId) }

            override fun peekGeneratedKey(sourceId: String): String? =
                generatedKeyFor(artworkCacheDir, sourceId)

            override fun cacheFile(cacheKey: String, preferLarge: Boolean): File? =
                File(artworkCacheDir, cacheKey).takeIf { it.isFile && it.length() > 0L }

            override suspend fun loadImageFile(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
                decodeImageFile(file)
            }

            override suspend fun loadArtworkForSource(sourceId: String, requestUrl: String?): ImageBitmap? =
                withContext(Dispatchers.IO) {
                    resolvePlaylistEntryLocalFile(sourceId)
                        ?.let { DesktopArtworkSupport.loadArtworkForFile(it) }
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

private fun ensureGeneratedArtwork(artworkCacheDir: File, sourceId: String): String? {
    val sourceFile = resolvePlaylistEntryLocalFile(sourceId) ?: return null
    val bitmap = DesktopArtworkSupport.loadArtworkForFile(sourceFile) ?: return null
    return writeArtworkCache(artworkCacheDir, sourceFile, bitmap)
}

private fun generatedKeyFor(artworkCacheDir: File, sourceId: String): String? {
    val sourceFile = resolvePlaylistEntryLocalFile(sourceId) ?: return null
    val key = artworkCacheKey(sourceFile)
    return key.takeIf { File(artworkCacheDir, key).isFile }
}

private fun artworkCacheKey(sourceFile: File): String = "${sha1Hex(sourceFile.absolutePath)}.jpg"

private fun writeArtworkCache(artworkCacheDir: File, sourceFile: File, bitmap: ImageBitmap): String? {
    val key = artworkCacheKey(sourceFile)
    val dest = File(artworkCacheDir, key)
    if (dest.isFile && dest.length() > 0L) return key
    return runCatching {
        val encoded = org.jetbrains.skia.Image
            .makeFromBitmap(bitmap.asSkiaBitmap())
            .encodeToData(org.jetbrains.skia.EncodedImageFormat.JPEG, 90)
            ?: return null
        dest.writeBytes(encoded.bytes)
        key
    }.getOrNull()
}

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
