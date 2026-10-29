package com.flopster101.siliconplayer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val PLAYLIST_PACKAGE_EXTENSION = "sppl"
internal const val PLAYLIST_PACKAGE_MIME_TYPE = "application/zip"

private const val PLAYLIST_PACKAGE_INDEX = "index.json"
private const val PLAYLIST_PACKAGE_COVERS = "covers/"
private const val PLAYLIST_PACKAGE_MAX_BYTES = 64L * 1024L * 1024L
private const val PLAYLIST_PACKAGE_MAX_ENTRIES = 10_000

internal data class PlaylistPackageImport(
    val state: PlaylistLibraryState,
    val covers: Map<String, ByteArray>
)

internal fun createPlaylistPackage(state: PlaylistLibraryState): ByteArray {
    return ByteArrayOutputStream().use { output ->
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(PLAYLIST_PACKAGE_INDEX))
            zip.write(writePlaylistBackupIndex(state).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            state.playlists.forEach { playlist ->
                val extension = when (playlist.format) {
                    PlaylistStoredFormat.Internal -> "json"
                    PlaylistStoredFormat.M3u -> "m3u"
                    PlaylistStoredFormat.M3u8 -> "m3u8"
                }
                zip.putNextEntry(ZipEntry("playlists/${playlist.id}.$extension"))
                val content = when (playlist.format) {
                    PlaylistStoredFormat.Internal -> writeStoredPlaylistToJson(playlist)
                    PlaylistStoredFormat.M3u,
                    PlaylistStoredFormat.M3u8 -> serializePlaylistToM3u(playlist)
                }
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                playlist.customArtworkUri?.let { artworkUri ->
                    val artworkFile = File(artworkUri)
                    if (artworkFile.isFile) {
                        zip.putNextEntry(ZipEntry("$PLAYLIST_PACKAGE_COVERS${playlist.id}"))
                        artworkFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        output.toByteArray()
    }
}

internal fun parsePlaylistPackage(bytes: ByteArray): PlaylistPackageImport? {
    if (bytes.isEmpty() || bytes.size > PLAYLIST_PACKAGE_MAX_BYTES) return null
    var index: String? = null
    val covers = mutableMapOf<String, ByteArray>()
    var entryCount = 0
    var totalBytes = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            entryCount++
            if (entryCount > PLAYLIST_PACKAGE_MAX_ENTRIES) return null
            val name = entry.name
            if (name != PLAYLIST_PACKAGE_INDEX &&
                ((!name.startsWith("playlists/") && !name.startsWith(PLAYLIST_PACKAGE_COVERS)) ||
                    name.contains("..") || name.endsWith('/'))
            ) return null
            val content = readPlaylistPackageEntry(zip, PLAYLIST_PACKAGE_MAX_BYTES - totalBytes)
                ?: return null
            totalBytes += content.size
            when {
                name == PLAYLIST_PACKAGE_INDEX -> index = content.toString(Charsets.UTF_8)
                name.startsWith(PLAYLIST_PACKAGE_COVERS) -> {
                    covers[name.removePrefix(PLAYLIST_PACKAGE_COVERS)] = content
                }
            }
        }
    }
    val state = index?.let(::readPlaylistBackupIndex) ?: return null
    return PlaylistPackageImport(
        state = state,
        covers = state.playlists.mapNotNull { playlist ->
            covers[playlist.id]?.let { playlist.id to it }
        }.toMap()
    )
}

internal fun restorePlaylistPackageCovers(
    imported: PlaylistPackageImport,
    coversDirectory: File
): PlaylistLibraryState {
    if (imported.covers.isEmpty()) return imported.state
    coversDirectory.mkdirs()
    val playlists = imported.state.playlists.map { playlist ->
        val cover = imported.covers[playlist.id] ?: return@map playlist
        val destination = File(coversDirectory, playlist.id)
        destination.writeBytes(cover)
        playlist.copy(customArtworkUri = destination.absolutePath)
    }
    return imported.state.copy(playlists = playlists)
}

private fun readPlaylistPackageEntry(input: ZipInputStream, remaining: Long): ByteArray? {
    if (remaining <= 0L) return null
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > remaining) return null
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
