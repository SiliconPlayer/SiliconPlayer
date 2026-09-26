package com.flopster101.siliconplayer

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
