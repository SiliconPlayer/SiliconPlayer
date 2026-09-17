package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets

internal enum class PlaylistExportFormat(
    val extension: String,
    val mimeType: String,
    val displayName: String
) {
    M3U8("m3u8", "audio/x-mpegurl", "M3U8 Playlist (*.m3u8)"),
    M3U("m3u", "audio/x-mpegurl", "M3U Playlist (*.m3u)");
}

internal interface PlaylistExporter {
    val format: PlaylistExportFormat
    fun export(playlist: StoredPlaylist): String
}

internal class M3uPlaylistExporter(
    override val format: PlaylistExportFormat = PlaylistExportFormat.M3U8
) : PlaylistExporter {
    override fun export(playlist: StoredPlaylist): String {
        return serializePlaylistToM3u(playlist)
    }
}

internal object PlaylistExportRegistry {
    private val exporters = mapOf<PlaylistExportFormat, PlaylistExporter>(
        PlaylistExportFormat.M3U8 to M3uPlaylistExporter(PlaylistExportFormat.M3U8),
        PlaylistExportFormat.M3U to M3uPlaylistExporter(PlaylistExportFormat.M3U)
    )

    fun exporterFor(format: PlaylistExportFormat): PlaylistExporter =
        exporters[format] ?: exporters.getValue(PlaylistExportFormat.M3U8)

    fun defaultExporter(): PlaylistExporter = exporterFor(PlaylistExportFormat.M3U8)
}

internal fun sanitizePlaylistFileName(title: String): String {
    val trimmed = title.trim().replace(Regex("""[\\/:*?"<>|]"""), "_")
    return trimmed.ifBlank { "Playlist" }
}

internal fun suggestedPlaylistExportFileName(
    playlist: StoredPlaylist,
    format: PlaylistExportFormat = PlaylistExportFormat.M3U8
): String {
    return "${sanitizePlaylistFileName(playlist.title)}.${format.extension}"
}

internal fun exportPlaylistToUri(
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

internal fun sharePlaylist(
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
