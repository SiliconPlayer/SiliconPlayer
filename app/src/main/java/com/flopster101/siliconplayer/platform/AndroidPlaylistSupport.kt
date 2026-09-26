package com.flopster101.siliconplayer.platform

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.PlaylistExportFormat
import com.flopster101.siliconplayer.PlaylistExportRegistry
import com.flopster101.siliconplayer.PlaylistMetadataRefreshNotifier
import com.flopster101.siliconplayer.PlaylistStoredFormat
import com.flopster101.siliconplayer.RECENT_ARTWORK_CACHE_DIR
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.ensureRecentArtworkCached
import com.flopster101.siliconplayer.ensureRecentArtworkThumbnailCached
import com.flopster101.siliconplayer.library.LibraryRepository
import com.flopster101.siliconplayer.loadLibraryThumbnail
import com.flopster101.siliconplayer.normalizeSourceIdentity
import com.flopster101.siliconplayer.parseM3uPlaylistLines
import com.flopster101.siliconplayer.peekCachedArtworkBitmapForSource
import com.flopster101.siliconplayer.peekLibraryThumbnail
import com.flopster101.siliconplayer.queryRealPathFromUri
import com.flopster101.siliconplayer.recentArtworkFile
import com.flopster101.siliconplayer.sha1Hex
import com.flopster101.siliconplayer.suggestedPlaylistExportFileName
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val PLAYLIST_COVERS_DIR = "playlist_covers"
private const val PLAYLIST_MIME_TYPE = "audio/x-mpegurl"

@Composable
internal fun rememberAndroidLibraryRepositorySupport(): LibraryRepositorySupport {
    val context = LocalContext.current
    return remember(context) {
        object : LibraryRepositorySupport {
            override val isAvailable: Boolean = true
            override val scanState = LibraryRepository.scanState
            override suspend fun collections() = LibraryRepository.collections(context)
            override suspend fun search(rawQuery: String) = LibraryRepository.search(context, rawQuery)
            override suspend fun albumTracks(albumName: String) =
                LibraryRepository.albumTracks(context, albumName)

            override suspend fun artistTracks(artist: String) =
                LibraryRepository.artistTracks(context, artist)

            override fun requestScan() {
                LibraryRepository.requestScan(context)
            }
        }
    }
}

@Composable
internal fun rememberAndroidArtworkCacheSupport(): ArtworkCacheSupport {
    val context = LocalContext.current
    return remember(context) {
        object : ArtworkCacheSupport {
            override suspend fun ensureThumbnailCached(sourceId: String, requestUrlHint: String?): String? {
                return ensureRecentArtworkThumbnailCached(
                    context = context,
                    sourceId = sourceId,
                    requestUrlHint = requestUrlHint
                )
            }

            override suspend fun ensureArtworkCached(sourceId: String, requireLarge: Boolean): String? {
                return ensureRecentArtworkCached(
                    context = context,
                    sourceId = sourceId,
                    requireLarge = requireLarge
                )
            }

            override fun peekGeneratedKey(sourceId: String): String? {
                val normalized = normalizeSourceIdentity(sourceId)?.trim().orEmpty()
                if (normalized.isBlank()) return null
                val cacheRoot = File(context.cacheDir, RECENT_ARTWORK_CACHE_DIR)
                val key = "${sha1Hex(normalized)}.jpg"
                return key.takeIf { File(cacheRoot, it).exists() }
            }

            override fun cacheFile(cacheKey: String, preferLarge: Boolean): File? {
                return recentArtworkFile(context, cacheKey, preferLarge = preferLarge)
            }

            override suspend fun loadImageFile(file: File): ImageBitmap? {
                return withContext(Dispatchers.IO) {
                    runCatching {
                        BitmapFactory.decodeFile(file.absolutePath)
                            ?.apply { setHasMipMap(true) }
                            ?.asImageBitmap()
                    }.getOrNull()
                }
            }

            override suspend fun loadArtworkForSource(sourceId: String, requestUrl: String?): ImageBitmap? {
                return withContext(Dispatchers.IO) {
                    peekCachedArtworkBitmapForSource(
                        displayFile = null,
                        sourceId = sourceId,
                        requestUrl = requestUrl
                    )?.asImageBitmap()
                }
            }

            override fun peekLibraryArtwork(path: String?): ImageBitmap? = peekLibraryThumbnail(path)

            override suspend fun loadLibraryArtwork(path: String): ImageBitmap? =
                loadLibraryThumbnail(context, path)
        }
    }
}

@Composable
internal fun rememberAndroidPlaylistPlatformSupport(): PlaylistPlatformSupport {
    val context = LocalContext.current
    val documentPicker = rememberUriDocumentPicker()
    val createDocumentPicker = rememberCreateDocumentPicker()
    return remember(context, documentPicker, createDocumentPicker) {
        object : PlaylistPlatformSupport {
            override val supportsSystemPicker: Boolean = true
            override val coversDirectory: File
                get() = File(context.filesDir, PLAYLIST_COVERS_DIR).apply { mkdirs() }

            override suspend fun pickCoverImage(destFile: File): Boolean {
                val uri = documentPicker(arrayOf("image/*")) ?: return false
                return withContext(Dispatchers.IO) {
                    saveNormalizedPlaylistCover(context, uri, destFile)
                }
            }

            override suspend fun rotateCoverFile(file: File, degrees: Float): Boolean {
                return withContext(Dispatchers.IO) { rotatePlaylistCoverFile(file, degrees) }
            }

            override suspend fun pickPlaylistDocument(): ParsedPlaylistDocument? {
                val uri = documentPicker(arrayOf(PLAYLIST_MIME_TYPE, "text/plain", "*/*"))
                    ?: return null
                return withContext(Dispatchers.IO) { parsePlaylistDocumentFromUri(context, uri) }
            }

            override suspend fun exportPlaylist(
                playlist: StoredPlaylist,
                format: PlaylistExportFormat
            ): Boolean {
                val targetUri = createDocumentPicker(
                    suggestedPlaylistExportFileName(playlist, format)
                ) ?: return false
                return withContext(Dispatchers.IO) {
                    exportPlaylistToUri(context, targetUri, playlist, format)
                }
            }

            override suspend fun sharePlaylist(
                playlist: StoredPlaylist,
                format: PlaylistExportFormat
            ): Boolean {
                return withContext(Dispatchers.Main) { sharePlaylist(context, playlist, format) }
            }
        }
    }
}

/** Suspends until the user picks a document, or resumes with `null` when the picker is dismissed. */
@Composable
private fun rememberUriDocumentPicker(): suspend (Array<String>) -> Uri? {
    val pending = remember { AtomicReference<CancellableContinuation<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pending.getAndSet(null)?.takeIf { it.isActive }?.resume(uri)
    }
    return remember(launcher) {
        { mimeTypes ->
            suspendCancellableCoroutine<Uri?> { continuation ->
                pending.set(continuation)
                launcher.launch(mimeTypes)
                continuation.invokeOnCancellation { pending.compareAndSet(continuation, null) }
            }
        }
    }
}

/** Suspends until the user picks an export destination, or `null` when the picker is dismissed. */
@Composable
private fun rememberCreateDocumentPicker(): suspend (String) -> Uri? {
    val pending = remember { AtomicReference<CancellableContinuation<Uri?>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PLAYLIST_MIME_TYPE)
    ) { uri ->
        pending.getAndSet(null)?.takeIf { it.isActive }?.resume(uri)
    }
    return remember(launcher) {
        { suggestedName ->
            suspendCancellableCoroutine<Uri?> { continuation ->
                pending.set(continuation)
                launcher.launch(suggestedName)
                continuation.invokeOnCancellation { pending.compareAndSet(continuation, null) }
            }
        }
    }
}

internal class AndroidPlaylistRefreshNotifier(private val context: Context) : PlaylistRefreshNotifier {
    override fun update(current: Int, total: Int, title: String?) {
        when {
            total <= 0 -> Unit
            current <= 0 -> PlaylistMetadataRefreshNotifier.start(context, total)
            current >= total -> PlaylistMetadataRefreshNotifier.finish(context)
            else -> PlaylistMetadataRefreshNotifier.progress(context, current, total, title)
        }
    }
}

private fun saveNormalizedPlaylistCover(context: Context, sourceUri: Uri, destFile: File): Boolean {
    val tempFile = File(destFile.parentFile ?: context.cacheDir, "temp_cover_${UUID.randomUUID()}.tmp")
    return try {
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: return false
        if (!tempFile.exists() || tempFile.length() == 0L) return false

        val exif = try {
            ExifInterface(tempFile.absolutePath)
        } catch (_: Throwable) {
            null
        }
        val orientation = exif?.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        ) ?: ExifInterface.ORIENTATION_NORMAL
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        val needsScale = maxDim > 1024
        val needsRotation = rotationDegrees != 0f

        var sampleSize = 1
        if (needsScale) {
            while (maxDim / (sampleSize * 2) >= 1024) {
                sampleSize *= 2
            }
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, decodeOptions) ?: return false

        val matrix = Matrix()
        if (needsRotation) {
            matrix.postRotate(rotationDegrees)
        }
        val scale = if (needsScale) {
            1024f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        } else {
            1f
        }
        if (scale < 1f) {
            matrix.postScale(scale, scale)
        }

        val finalBitmap = if (needsRotation || scale < 1f) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }

        FileOutputStream(destFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (finalBitmap != bitmap) {
            finalBitmap.recycle()
        }
        bitmap.recycle()
        destFile.exists() && destFile.length() > 0L
    } catch (_: Throwable) {
        false
    } finally {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }
}

internal fun rotatePlaylistCoverFile(file: File, degrees: Float = 90f): Boolean {
    if (!file.exists() || !file.isFile || file.length() == 0L) return false
    return try {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return false
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        FileOutputStream(file).use { out ->
            rotated.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (rotated != bitmap) {
            rotated.recycle()
        }
        bitmap.recycle()
        true
    } catch (_: Throwable) {
        false
    }
}

private fun parsePlaylistDocumentFromUri(
    context: Context,
    uri: Uri
): ParsedPlaylistDocument? {
    val contentResolver = context.contentResolver
    val displayName = queryDisplayNameFromUri(contentResolver, uri)
    val baseFile = resolveBaseFileFromUri(context, uri)

    val rawTitle = displayName
        ?.substringBeforeLast('.')
        ?.ifBlank { null }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { null }
        ?: "Imported Playlist"

    val title = if (
        (rawTitle.equals("!playlist", ignoreCase = true) || rawTitle.equals("playlist", ignoreCase = true)) &&
        !baseFile?.parentFile?.name.isNullOrBlank()
    ) {
        baseFile?.parentFile?.name ?: rawTitle
    } else {
        rawTitle
    }

    val bytes = runCatching {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null

    val text = decodePickedPlaylistText(bytes)
    val lines = text.replace("\uFEFF", "").lineSequence().toList()
    if (lines.isEmpty()) return null

    return parseM3uPlaylistLines(
        lines = lines,
        title = title,
        baseFile = baseFile,
        sourceIdHint = null,
        format = PlaylistStoredFormat.M3u8,
        allowUnresolvedFiles = true
    )
}

private fun resolveBaseFileFromUri(context: Context, uri: Uri): File? {
    val realPath = queryRealPathFromUri(context, uri)
    val candidate = when {
        uri.scheme == "file" -> File(uri.path ?: "")
        realPath != null -> File(realPath)
        else -> null
    }
    return candidate?.takeIf { it.exists() }
}

private fun queryDisplayNameFromUri(contentResolver: ContentResolver, uri: Uri): String? {
    return runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()
}

private fun decodePickedPlaylistText(bytes: ByteArray): String {
    return decodePickedPlaylistTextWithCharset(bytes, StandardCharsets.UTF_8)
        ?: decodePickedPlaylistTextWithCharset(bytes, Charset.forName("windows-1252"))
        ?: String(bytes, StandardCharsets.ISO_8859_1)
}

private fun decodePickedPlaylistTextWithCharset(bytes: ByteArray, charset: Charset): String? {
    val decoder = charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
        null
    }
}

private fun exportPlaylistToUri(
    context: Context,
    targetUri: Uri,
    playlist: StoredPlaylist,
    format: PlaylistExportFormat = PlaylistExportFormat.M3U8
): Boolean {
    return runCatching {
        val exporter = PlaylistExportRegistry.exporterFor(format)
        val content = exporter.export(playlist)
        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
            outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(content)
            }
        }
        true
    }.getOrDefault(false)
}

private fun sharePlaylist(
    context: Context,
    playlist: StoredPlaylist,
    format: PlaylistExportFormat = PlaylistExportFormat.M3U8
): Boolean {
    return runCatching {
        val exporter = PlaylistExportRegistry.exporterFor(format)
        val content = exporter.export(playlist)
        val shareDir = File(context.cacheDir, "shared_playlists").apply { mkdirs() }
        val fileName = suggestedPlaylistExportFileName(playlist, format)
        val shareFile = File(shareDir, fileName)
        shareFile.writeText(content, StandardCharsets.UTF_8)
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, playlist.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share playlist"))
        true
    }.getOrDefault(false)
}
