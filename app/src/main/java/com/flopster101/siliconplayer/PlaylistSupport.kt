package com.flopster101.siliconplayer

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import com.flopster101.siliconplayer.library.LibraryTrackEntity

private val SUPPORTED_PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8")

internal fun LibraryTrackEntity.toPlaylistTrackEntry(): PlaylistTrackEntry {
    val artist = artist.takeUnless { it.isBlank() || it.equals("Unknown artist", ignoreCase = true) }
    val album = album.takeUnless { it.isBlank() || it.equals("Unknown album", ignoreCase = true) }
    return PlaylistTrackEntry(
        source = path,
        title = title,
        artist = artist,
        album = album,
        durationSecondsOverride = if (durationMs > 0L) durationMs / 1000.0 else null
    )
}

internal enum class PlaylistStoredFormat(
    val storageValue: String,
    val label: String
) {
    Internal("internal", "Internal"),
    M3u("m3u", "M3U"),
    M3u8("m3u8", "M3U8");

    companion object {
        fun fromStorage(value: String?): PlaylistStoredFormat {
            return values().firstOrNull { it.storageValue == value } ?: Internal
        }
    }
}

internal enum class PlaylistEntrySortMode(
    val storageValue: String,
    val label: String
) {
    Custom("custom", "Custom"),
    Title("title", "Title"),
    Artist("artist", "Artist"),
    Album("album", "Album"),
    RecentlyAdded("recently_added", "Recently added");

    companion object {
        fun fromStorage(value: String?): PlaylistEntrySortMode {
            return entries.firstOrNull { it.storageValue == value || it.name.equals(value, ignoreCase = true) }
                ?: Custom
        }
    }
}

internal data class PlaylistTrackEntry(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val requestUrlHint: String? = null,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val artworkThumbnailCacheKey: String? = null,
    val subtuneIndex: Int? = null,
    val durationSecondsOverride: Double? = null,
    val addedAtMs: Long = System.currentTimeMillis()
)

internal data class StoredPlaylist(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>,
    val updatedAtMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

internal data class PlaylistLibraryState(
    val favorites: List<PlaylistTrackEntry>,
    val playlists: List<StoredPlaylist>
)

internal data class ParsedPlaylistDocument(
    val title: String,
    val format: PlaylistStoredFormat,
    val sourceIdHint: String? = null,
    val entries: List<PlaylistTrackEntry>
)

internal const val FAVORITES_PLAYLIST_ID = "__favorites__"

internal fun favoritesAsStoredPlaylist(favorites: List<PlaylistTrackEntry>): StoredPlaylist {
    return StoredPlaylist(
        id = FAVORITES_PLAYLIST_ID,
        title = "Favorites",
        format = PlaylistStoredFormat.Internal,
        sourceIdHint = null,
        entries = favorites,
        updatedAtMs = favorites.maxOfOrNull { it.addedAtMs } ?: 0L
    )
}

internal fun emptyPlaylistLibraryState(): PlaylistLibraryState {
    return PlaylistLibraryState(
        favorites = emptyList(),
        playlists = emptyList()
    )
}

internal fun isSupportedPlaylistFileName(name: String): Boolean {
    return inferredPrimaryExtensionForName(name)
        ?.lowercase(Locale.ROOT) in SUPPORTED_PLAYLIST_EXTENSIONS
}

internal fun isSupportedPlaylistFile(file: File?): Boolean {
    return file != null && file.isFile && isSupportedPlaylistFileName(file.name)
}

internal fun parsePlaylistDocument(
    file: File,
    sourceIdHint: String? = null,
    allowUnresolvedFiles: Boolean = true
): ParsedPlaylistDocument? {
    if (!file.exists() || !file.isFile) return null
    return when (inferredPrimaryExtensionForName(file.name)?.lowercase(Locale.ROOT)) {
        "m3u" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u, allowUnresolvedFiles)
        "m3u8" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u8, allowUnresolvedFiles)
        else -> null
    }
}

internal fun resolvePlaylistEntryLocalFile(source: String): File? {
    if (parseHttpSourceSpecFromInput(source) != null ||
        parseSmbSourceSpecFromInput(source) != null ||
        source.startsWith("content:", ignoreCase = true) ||
        (source.contains("://") && !source.startsWith("file://", ignoreCase = true))
    ) {
        return null
    }
    val localPath = if (source.startsWith("file://", ignoreCase = true)) {
        Uri.parse(source).path
    } else {
        source
    } ?: return null
    return File(localPath)
}

internal fun playlistEntryMatchesPlayback(
    entry: PlaylistTrackEntry,
    activeSourceId: String?,
    currentSubtuneIndex: Int
): Boolean {
    if (!samePath(entry.source, activeSourceId)) return false
    val entrySubtune = entry.subtuneIndex ?: return true
    return entrySubtune == currentSubtuneIndex
}

internal fun playlistContainsTrack(
    entries: List<PlaylistTrackEntry>,
    source: String?,
    subtuneIndex: Int? = null
): Boolean {
    if (source.isNullOrBlank()) return false
    return entries.any { entry ->
        val pathMatches = entry.source == source ||
            runCatching { samePath(entry.source, source) }.getOrDefault(false)
        pathMatches && (subtuneIndex == null || entry.subtuneIndex == null || entry.subtuneIndex == subtuneIndex)
    }
}

internal fun buildInternalPlaylistCopy(
    title: String,
    entries: List<PlaylistTrackEntry>
): StoredPlaylist {
    val normalizedTitle = title.trim().ifBlank { "Playlist" }
    return StoredPlaylist(
        title = normalizedTitle,
        format = PlaylistStoredFormat.Internal,
        entries = entries
    )
}

internal fun duplicateStoredPlaylist(
    playlist: StoredPlaylist,
    newTitle: String
): StoredPlaylist {
    val now = System.currentTimeMillis()
    val duplicatedEntries = playlist.entries.mapIndexed { index, entry ->
        entry.copy(
            id = UUID.randomUUID().toString(),
            addedAtMs = now + index
        )
    }
    return StoredPlaylist(
        id = UUID.randomUUID().toString(),
        title = newTitle.trim().ifBlank { "${playlist.title} (Copy)" },
        format = PlaylistStoredFormat.Internal,
        sourceIdHint = null,
        entries = duplicatedEntries,
        updatedAtMs = now
    )
}

internal fun buildImportedPlaylist(
    document: ParsedPlaylistDocument
): StoredPlaylist {
    return StoredPlaylist(
        title = document.title,
        format = document.format,
        sourceIdHint = document.sourceIdHint,
        entries = document.entries
    )
}

internal fun playlistTrackSubtitle(entry: PlaylistTrackEntry): String {
    val artist = entry.artist?.trim().orEmpty()
    val sourceTail = when {
        parseHttpSourceSpecFromInput(entry.source) != null ||
            parseSmbSourceSpecFromInput(entry.source) != null -> entry.source
        else -> resolvePlaylistEntryLocalFile(entry.source)?.name ?: entry.source
    }
    return buildString {
        if (artist.isNotBlank()) {
            append(artist)
        }
        if (entry.subtuneIndex != null) {
            if (isNotEmpty()) append(" • ")
            append("Subtune ")
            append(entry.subtuneIndex + 1)
        }
        if (sourceTail.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(sourceTail)
        }
    }
}

private data class PendingM3uMetadata(
    val title: String,
    val artist: String?,
    val durationSecondsOverride: Double?
)

private data class EmbeddedM3uEntryMetadata(
    val rawTarget: String,
    val rawSubtuneNumber: Int?,
    val title: String?,
    val artist: String?,
    val durationSecondsOverride: Double?
)

private data class PendingParsedM3uEntry(
    val source: String,
    val requestUrlHint: String?,
    val title: String,
    val artist: String?,
    val embeddedRawSubtuneNumber: Int?,
    val fragmentSubtuneIndex: Int?,
    val durationSecondsOverride: Double?
)

private data class PlaylistTargetResolution(
    val source: String,
    val requestUrlHint: String?,
    val subtuneIndex: Int?
)

internal fun parseM3uPlaylistLines(
    lines: List<String>,
    title: String,
    baseFile: File? = null,
    sourceIdHint: String? = null,
    format: PlaylistStoredFormat = PlaylistStoredFormat.M3u8,
    allowUnresolvedFiles: Boolean = false
): ParsedPlaylistDocument? {
    if (lines.isEmpty()) return null
    var pendingMetadata: PendingM3uMetadata? = null
    val pendingEntries = buildList {
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEachIndexed
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pendingMetadata = parseExtInfMetadata(line)
                return@forEachIndexed
            }
            if (line.startsWith("#")) return@forEachIndexed
            val embeddedMetadata = parseEmbeddedM3uEntryMetadata(line)
            val playlistTarget = embeddedMetadata?.rawTarget ?: line
            val resolved = resolvePlaylistTarget(
                rawEntry = playlistTarget,
                playlistFile = baseFile,
                sourceIdHint = sourceIdHint,
                allowUnresolvedFiles = allowUnresolvedFiles
            ) ?: return@forEachIndexed
            val metadata = pendingMetadata
            pendingMetadata = null
            val fallbackTitle = derivePlaylistEntryTitle(
                source = resolved.source,
                lineIndex = index
            )
            add(
                PendingParsedM3uEntry(
                    source = resolved.source,
                    requestUrlHint = resolved.requestUrlHint,
                    title = metadata?.title?.takeIf { it.isNotBlank() }
                        ?: embeddedMetadata?.title?.takeIf { it.isNotBlank() }
                        ?: fallbackTitle,
                    artist = metadata?.artist ?: embeddedMetadata?.artist,
                    embeddedRawSubtuneNumber = embeddedMetadata?.rawSubtuneNumber,
                    fragmentSubtuneIndex = resolved.subtuneIndex,
                    durationSecondsOverride = metadata?.durationSecondsOverride ?: embeddedMetadata?.durationSecondsOverride
                )
            )
        }
    }
    if (pendingEntries.isEmpty()) return null
    val embeddedSubtuneMode = determineEmbeddedM3uSubtuneMode(pendingEntries)
    val entries = pendingEntries.map { entry ->
        PlaylistTrackEntry(
            source = entry.source,
            requestUrlHint = entry.requestUrlHint,
            title = entry.title,
            artist = entry.artist,
            subtuneIndex = entry.embeddedRawSubtuneNumber?.let { rawSubtuneNumber ->
                normalizeEmbeddedM3uSubtuneNumber(rawSubtuneNumber, embeddedSubtuneMode)
            } ?: entry.fragmentSubtuneIndex,
            durationSecondsOverride = entry.durationSecondsOverride
        )
    }
    return ParsedPlaylistDocument(
        title = title,
        format = format,
        sourceIdHint = sourceIdHint,
        entries = entries
    )
}

private fun parseM3uPlaylist(
    file: File,
    sourceIdHint: String?,
    format: PlaylistStoredFormat,
    allowUnresolvedFiles: Boolean = true
): ParsedPlaylistDocument? {
    val lines = readPlaylistLines(file)
    val rawTitle = file.nameWithoutExtension.ifBlank { file.name }
    val title = if (
        (rawTitle.equals("!playlist", ignoreCase = true) || rawTitle.equals("playlist", ignoreCase = true)) &&
        !file.parentFile?.name.isNullOrBlank()
    ) {
        file.parentFile?.name ?: rawTitle
    } else {
        rawTitle
    }
    return parseM3uPlaylistLines(
        lines = lines,
        title = title,
        baseFile = file,
        sourceIdHint = sourceIdHint,
        format = format,
        allowUnresolvedFiles = allowUnresolvedFiles
    )
}

internal fun parsePlaylistDocumentFromUri(
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

    val text = decodePlaylistText(bytes)
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

internal fun resolveBaseFileFromUri(context: Context, uri: Uri): File? {
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

private fun readPlaylistLines(file: File): List<String> {
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return emptyList()
    val text = decodePlaylistText(bytes)
    return text
        .replace("\uFEFF", "")
        .lineSequence()
        .toList()
}

private fun decodePlaylistText(bytes: ByteArray): String {
    return decodePlaylistTextWithCharset(bytes, StandardCharsets.UTF_8)
        ?: decodePlaylistTextWithCharset(bytes, Charset.forName("windows-1252"))
        ?: String(bytes, StandardCharsets.ISO_8859_1)
}

private fun decodePlaylistTextWithCharset(bytes: ByteArray, charset: Charset): String? {
    val decoder = charset
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun parseExtInfMetadata(line: String): PendingM3uMetadata? {
    val extInfPayload = line.substringAfter(':', missingDelimiterValue = "")
    val durationToken = extInfPayload.substringBefore(',', missingDelimiterValue = "").trim()
    val payload = extInfPayload
        .substringAfter(',', missingDelimiterValue = "")
        .trim()
    val durationSecondsOverride = parsePlaylistDurationSeconds(durationToken)
    if (payload.isBlank() && durationSecondsOverride == null) return null
    val artist = payload.substringBefore(" - ", missingDelimiterValue = "")
        .trim()
        .takeIf { it.isNotBlank() }
    val title = if (payload.contains(" - ")) {
        payload.substringAfter(" - ").trim().ifBlank { payload }
    } else {
        payload
    }
    return PendingM3uMetadata(
        title = title,
        artist = artist,
        durationSecondsOverride = durationSecondsOverride
    )
}

internal fun serializePlaylistToM3u(playlist: StoredPlaylist): String {
    return buildString {
        appendLine("#EXTM3U")
        for (entry in playlist.entries) {
            val durationSeconds = entry.durationSecondsOverride?.let {
                if (it > 0) it.roundToInt() else -1
            } ?: -1
            val titlePart = if (!entry.artist.isNullOrBlank()) {
                "${entry.artist.trim()} - ${entry.title.trim()}"
            } else {
                entry.title.trim()
            }
            appendLine("#EXTINF:$durationSeconds,$titlePart")
            val target = buildString {
                val localFile = resolvePlaylistEntryLocalFile(entry.source)
                if (localFile != null) {
                    append(localFile.absolutePath)
                } else {
                    append(entry.requestUrlHint?.takeIf { it.isNotBlank() } ?: entry.source)
                }
                if (entry.subtuneIndex != null) {
                    append("#subtune=${entry.subtuneIndex + 1}")
                }
            }
            appendLine(target)
        }
    }
}

private fun parseEmbeddedM3uEntryMetadata(rawEntry: String): EmbeddedM3uEntryMetadata? {
    val separatorIndex = rawEntry.indexOf("::")
    if (separatorIndex <= 0 || separatorIndex >= rawEntry.lastIndex) return null
    val rawTarget = rawEntry.substring(0, separatorIndex).trim()
    if (rawTarget.isBlank()) return null
    val payload = rawEntry.substring(separatorIndex + 2).trim()
    if (payload.isBlank()) return null
    val fields = splitEscapedPlaylistFields(payload)
    if (fields.size < 2) return null
    val rawSubtuneNumber = fields.getOrNull(1)?.trim()?.toIntOrNull() ?: return null
    return EmbeddedM3uEntryMetadata(
        rawTarget = rawTarget,
        rawSubtuneNumber = rawSubtuneNumber.coerceAtLeast(0),
        title = fields.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() },
        artist = null,
        durationSecondsOverride = parsePlaylistDurationSeconds(fields.getOrNull(3))
    )
}

private enum class EmbeddedM3uSubtuneMode {
    ZeroBased,
    HumanOneBased
}

private fun determineEmbeddedM3uSubtuneMode(entries: List<PendingParsedM3uEntry>): EmbeddedM3uSubtuneMode {
    val embeddedSubtuneNumbers = entries.mapNotNull { it.embeddedRawSubtuneNumber }
    if (embeddedSubtuneNumbers.size <= 1) {
        return EmbeddedM3uSubtuneMode.ZeroBased
    }
    return if (embeddedSubtuneNumbers.any { it == 0 }) {
        EmbeddedM3uSubtuneMode.ZeroBased
    } else {
        EmbeddedM3uSubtuneMode.HumanOneBased
    }
}

private fun normalizeEmbeddedM3uSubtuneNumber(
    rawSubtuneNumber: Int,
    mode: EmbeddedM3uSubtuneMode
): Int {
    return when (mode) {
        EmbeddedM3uSubtuneMode.ZeroBased -> rawSubtuneNumber.coerceAtLeast(0)
        EmbeddedM3uSubtuneMode.HumanOneBased -> normalizeHumanPlaylistSubtuneIndex(rawSubtuneNumber) ?: 0
    }
}

private fun splitEscapedPlaylistFields(payload: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaping = false
    payload.forEach { char ->
        when {
            escaping -> {
                current.append(char)
                escaping = false
            }
            char == '\\' -> escaping = true
            char == ',' -> {
                fields += current.toString()
                current.setLength(0)
            }
            else -> current.append(char)
        }
    }
    if (escaping) {
        current.append('\\')
    }
    fields += current.toString()
    return fields
}

private fun resolvePlaylistTarget(
    rawEntry: String,
    playlistFile: File?,
    sourceIdHint: String?,
    allowUnresolvedFiles: Boolean = false
): PlaylistTargetResolution? {
    val stripped = stripRecognizedPlaylistFragment(rawEntry)
    val normalized = stripped.first.trim()
    if (normalized.isBlank()) return null
    val fragmentSubtune = stripped.second
    parseHttpSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildHttpSourceId(spec),
            requestUrlHint = stripUrlFragment(buildHttpRequestUri(spec)),
            subtuneIndex = fragmentSubtune
        )
    }
    parseSmbSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildSmbSourceId(spec),
            requestUrlHint = buildSmbRequestUri(spec),
            subtuneIndex = fragmentSubtune
        )
    }
    if (normalized.startsWith("content://", ignoreCase = true)) {
        return PlaylistTargetResolution(
            source = normalized,
            requestUrlHint = null,
            subtuneIndex = fragmentSubtune
        )
    }
    if (normalized.startsWith("file://", ignoreCase = true)) {
        val fileUri = Uri.parse(normalized)
        val localPath = fileUri.path?.takeIf { it.isNotBlank() } ?: return null
        val resolved = File(localPath)
        if (!allowUnresolvedFiles && (!resolved.exists() || !resolved.isFile)) return null
        return PlaylistTargetResolution(
            source = resolved.absolutePath,
            requestUrlHint = null,
            subtuneIndex = fragmentSubtune
        )
    }
    val remoteRelative = resolveRelativeRemotePlaylistSource(normalized, sourceIdHint)
    if (remoteRelative != null) {
        parseHttpSourceSpecFromInput(remoteRelative)?.let { spec ->
            return PlaylistTargetResolution(
                source = buildHttpSourceId(spec),
                requestUrlHint = stripUrlFragment(buildHttpRequestUri(spec)),
                subtuneIndex = fragmentSubtune
            )
        }
        parseSmbSourceSpecFromInput(remoteRelative)?.let { spec ->
            return PlaylistTargetResolution(
                source = buildSmbSourceId(spec),
                requestUrlHint = buildSmbRequestUri(spec),
                subtuneIndex = fragmentSubtune
            )
        }
        return PlaylistTargetResolution(
            source = remoteRelative,
            requestUrlHint = remoteRelative,
            subtuneIndex = fragmentSubtune
        )
    }
    val candidate = if (File(normalized).isAbsolute || WINDOWS_ABSOLUTE_PATH_REGEX.matches(normalized)) {
        File(normalized)
    } else if (playlistFile != null) {
        val parent = if (playlistFile.isDirectory) {
            playlistFile
        } else {
            playlistFile.parentFile ?: playlistFile.absoluteFile.parentFile ?: File("/")
        }
        File(parent, normalized.replace('\\', '/'))
    } else {
        File(normalized.replace('\\', '/'))
    }
    val normalizedFile = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile.normalize() }
    if (!allowUnresolvedFiles && (!normalizedFile.exists() || !normalizedFile.isFile)) return null
    return PlaylistTargetResolution(
        source = normalizedFile.absolutePath,
        requestUrlHint = null,
        subtuneIndex = fragmentSubtune
    )
}

private fun stripRecognizedPlaylistFragment(rawEntry: String): Pair<String, Int?> {
    val fragmentIndex = rawEntry.lastIndexOf('#')
    if (fragmentIndex < 0 || fragmentIndex == rawEntry.lastIndex) {
        return rawEntry to null
    }
    val fragment = rawEntry.substring(fragmentIndex + 1).trim()
    val subtuneIndex = parsePlaylistSubtuneFragment(fragment) ?: return rawEntry to null
    return rawEntry.substring(0, fragmentIndex) to subtuneIndex
}

private fun parsePlaylistSubtuneFragment(fragment: String): Int? {
    val normalized = fragment.trim()
    if (normalized.isBlank()) return null
    if (normalized.all { it.isDigit() }) {
        return normalizeHumanPlaylistSubtuneIndex(normalized.toIntOrNull())
    }
    val key = normalized.substringBefore('=').trim().lowercase(Locale.ROOT)
    val value = normalized.substringAfter('=', missingDelimiterValue = "").trim()
    if (value.isBlank()) return null
    return when (key) {
        "subtune", "tune", "track" -> normalizeHumanPlaylistSubtuneIndex(value.toIntOrNull())
        "subtune_index", "subtuneindex" -> value.toIntOrNull()?.coerceAtLeast(0)
        else -> null
    }
}

private fun normalizeHumanPlaylistSubtuneIndex(value: Int?): Int? {
    val parsed = value ?: return null
    return if (parsed <= 0) 0 else parsed - 1
}

private fun parsePlaylistDurationSeconds(rawValue: String?): Double? {
    val normalized = rawValue?.trim().orEmpty()
    if (normalized.isBlank()) return null
    if (normalized.contains(':')) {
        val parts = normalized
            .split(':')
            .map { it.trim() }
        if (parts.isEmpty() || parts.any { it.isEmpty() || it.any { ch -> !ch.isDigit() } }) {
            return null
        }
        var totalSeconds = 0L
        parts.forEach { part ->
            totalSeconds = (totalSeconds * 60L) + part.toLong()
        }
        return totalSeconds.toDouble().takeIf { it > 0.0 }
    }
    val numeric = normalized.toDoubleOrNull() ?: return null
    return numeric.takeIf { it > 0.0 }
}

private fun resolveRelativeRemotePlaylistSource(
    relativePath: String,
    sourceIdHint: String?
): String? {
    val sourceHint = sourceIdHint?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    parseHttpSourceSpecFromInput(sourceHint)?.let { spec ->
        val baseUri = URI(buildHttpRequestUri(spec))
        val resolvedUri = runCatching {
            baseUri.resolve(relativePath)
        }.getOrNull() ?: return null
        return resolvedUri.toASCIIString()
    }
    parseSmbSourceSpecFromInput(sourceHint)?.let { spec ->
        val basePath = buildString {
            val currentPath = normalizeSmbPathForShare(spec.path)
            if (currentPath.isNullOrBlank()) {
                append("")
            } else {
                append(currentPath.substringBeforeLast('/', missingDelimiterValue = ""))
            }
        }
        val combinedPath = normalizeSmbPathForShare(
            listOf(basePath, relativePath.replace('\\', '/'))
                .filter { it.isNotBlank() }
                .joinToString("/")
        )
        val resolvedSpec = spec.copy(path = combinedPath)
        return buildSmbSourceId(resolvedSpec)
    }
    return null
}

private fun derivePlaylistEntryTitle(source: String, lineIndex: Int): String {
    return when {
        parseHttpSourceSpecFromInput(source) != null ||
            parseSmbSourceSpecFromInput(source) != null -> source.substringAfterLast('/').ifBlank {
            "Entry ${lineIndex + 1}"
        }
        else -> resolvePlaylistEntryLocalFile(source)?.nameWithoutExtension?.ifBlank {
            resolvePlaylistEntryLocalFile(source)?.name
        } ?: "Entry ${lineIndex + 1}"
    }
}

private val WINDOWS_ABSOLUTE_PATH_REGEX = Regex("^[A-Za-z]:[\\\\/].*")

internal fun sortPlaylistEntries(
    entries: List<PlaylistTrackEntry>,
    sortMode: PlaylistEntrySortMode
): List<PlaylistTrackEntry> {
    if (entries.size <= 1 || sortMode == PlaylistEntrySortMode.Custom) return entries
    val indexedEntries = entries.withIndex()
    return when (sortMode) {
        PlaylistEntrySortMode.Custom -> entries
        PlaylistEntrySortMode.Title -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { sortablePlaylistText(it.value.artist) }
                        .thenBy { sortablePlaylistText(it.value.album) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.Artist -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { sortablePlaylistText(it.value.artist) }
                        .thenBy { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { sortablePlaylistText(it.value.album) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.Album -> {
            indexedEntries
                .sortedWith(
                    compareBy<IndexedValue<PlaylistTrackEntry>> { sortablePlaylistText(it.value.album) }
                        .thenBy { sortablePlaylistText(it.value.artist) }
                        .thenBy { it.value.title.lowercase(Locale.ROOT) }
                        .thenBy { it.index }
                )
                .map { it.value }
        }

        PlaylistEntrySortMode.RecentlyAdded -> {
            indexedEntries
                .sortedWith(
                    compareByDescending<IndexedValue<PlaylistTrackEntry>> { it.value.addedAtMs }
                        .thenBy { it.index }
                )
                .map { it.value }
        }
    }
}

private fun sortablePlaylistText(value: String?): String {
    val normalized = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return if (normalized.isBlank()) "\uFFFF" else normalized
}

internal enum class PlaylistSortMode(val label: String) {
    RecentlyUpdated("Recently updated"),
    AlphabeticalAsc("Name (A–Z)"),
    AlphabeticalDesc("Name (Z–A)"),
    TrackCount("Track count")
}

internal fun sortStoredPlaylists(
    playlists: List<StoredPlaylist>,
    sortMode: PlaylistSortMode
): List<StoredPlaylist> {
    if (playlists.size <= 1) return playlists
    val indexed = playlists.withIndex()
    return when (sortMode) {
        PlaylistSortMode.RecentlyUpdated -> {
            indexed.sortedWith(
                compareByDescending<IndexedValue<StoredPlaylist>> { it.value.updatedAtMs }
                    .thenBy { it.index }
            ).map { it.value }
        }
        PlaylistSortMode.AlphabeticalAsc -> {
            indexed.sortedWith(
                compareBy<IndexedValue<StoredPlaylist>> { it.value.title.lowercase(Locale.ROOT) }
                    .thenBy { it.index }
            ).map { it.value }
        }
        PlaylistSortMode.AlphabeticalDesc -> {
            indexed.sortedWith(
                compareByDescending<IndexedValue<StoredPlaylist>> { it.value.title.lowercase(Locale.ROOT) }
                    .thenBy { it.index }
            ).map { it.value }
        }
        PlaylistSortMode.TrackCount -> {
            indexed.sortedWith(
                compareByDescending<IndexedValue<StoredPlaylist>> { it.value.entries.size }
                    .thenBy { it.index }
            ).map { it.value }
        }
    }
}
