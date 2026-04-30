package com.flopster101.siliconplayer

import android.net.Uri
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.util.Locale

internal fun isSupportedPlaylistFile(file: File?): Boolean {
    return file != null && file.isFile && isSupportedPlaylistFileName(file.name)
}

internal fun parsePlaylistDocument(
    file: File,
    sourceIdHint: String? = null
): ParsedPlaylistDocument? {
    if (!file.exists() || !file.isFile) return null
    return when (inferredPrimaryExtensionForName(file.name)?.lowercase(Locale.ROOT)) {
        "m3u" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u)
        "m3u8" -> parseM3uPlaylist(file, sourceIdHint, PlaylistStoredFormat.M3u8)
        else -> null
    }
}

internal fun resolvePlaylistEntryLocalFile(source: String): File? {
    if (parseHttpSourceSpecFromInput(source) != null || parseSmbSourceSpecFromInput(source) != null) {
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
        val subtuneIndex = entry.subtuneIndex
        if (subtuneIndex != null) {
            if (isNotEmpty()) append(" • ")
            append("Subtune ")
            append(subtuneIndex + 1)
        }
        if (sourceTail.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(sourceTail)
        }
    }
}

private fun parseM3uPlaylist(
    file: File,
    sourceIdHint: String?,
    format: PlaylistStoredFormat
): ParsedPlaylistDocument? {
    val lines = readPlaylistLines(file)
    return parseM3uPlaylistLines(
        lines = lines,
        title = file.nameWithoutExtension.ifBlank { file.name },
        format = format,
        sourceIdHint = sourceIdHint,
        resolveTarget = { rawEntry ->
            resolvePlaylistTarget(
                rawEntry = rawEntry,
                playlistFile = file,
                sourceIdHint = sourceIdHint
            )
        },
        deriveFallbackTitle = ::derivePlaylistEntryTitle
    )
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

private fun resolvePlaylistTarget(
    rawEntry: String,
    playlistFile: File,
    sourceIdHint: String?
): PlaylistTargetResolution? {
    val normalized = rawEntry.trim()
    if (normalized.isBlank()) return null
    parseHttpSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildHttpSourceId(spec),
            requestUrlHint = stripUrlFragment(buildHttpRequestUri(spec)),
            subtuneIndex = null
        )
    }
    parseSmbSourceSpecFromInput(normalized)?.let { spec ->
        return PlaylistTargetResolution(
            source = buildSmbSourceId(spec),
            requestUrlHint = buildSmbRequestUri(spec),
            subtuneIndex = null
        )
    }
    if (normalized.startsWith("file://", ignoreCase = true)) {
        val fileUri = Uri.parse(normalized)
        val localPath = fileUri.path?.takeIf { it.isNotBlank() } ?: return null
        val resolved = File(localPath)
        if (!resolved.exists() || !resolved.isFile) return null
        return PlaylistTargetResolution(
            source = resolved.absolutePath,
            requestUrlHint = null,
            subtuneIndex = null
        )
    }
    val remoteRelative = resolveRelativeRemotePlaylistSource(normalized, sourceIdHint)
    if (remoteRelative != null) {
        parseHttpSourceSpecFromInput(remoteRelative)?.let { spec ->
            return PlaylistTargetResolution(
                source = buildHttpSourceId(spec),
                requestUrlHint = stripUrlFragment(buildHttpRequestUri(spec)),
                subtuneIndex = null
            )
        }
        parseSmbSourceSpecFromInput(remoteRelative)?.let { spec ->
            return PlaylistTargetResolution(
                source = buildSmbSourceId(spec),
                requestUrlHint = buildSmbRequestUri(spec),
                subtuneIndex = null
            )
        }
        return PlaylistTargetResolution(
            source = remoteRelative,
            requestUrlHint = remoteRelative,
            subtuneIndex = null
        )
    }
    val candidate = if (File(normalized).isAbsolute || WINDOWS_ABSOLUTE_PATH_REGEX.matches(normalized)) {
        File(normalized)
    } else {
        File(playlistFile.parentFile ?: playlistFile.absoluteFile.parentFile ?: File("/"), normalized)
    }
    val normalizedFile = runCatching { candidate.canonicalFile }.getOrElse { candidate.absoluteFile.normalize() }
    if (!normalizedFile.exists() || !normalizedFile.isFile) return null
    return PlaylistTargetResolution(
        source = normalizedFile.absolutePath,
        requestUrlHint = null,
        subtuneIndex = null
    )
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
