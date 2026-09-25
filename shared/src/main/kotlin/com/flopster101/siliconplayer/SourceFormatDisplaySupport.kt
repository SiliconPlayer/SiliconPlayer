package com.flopster101.siliconplayer

import java.io.File
import java.net.URLDecoder

internal const val REMOTE_SOURCE_CACHE_DIR = "remote_sources"

private val REMOTE_CACHE_HASH_PREFIX_REGEX = Regex("^[0-9a-fA-F]{40}_(.+)$")

internal fun stripRemoteCacheHashPrefix(rawName: String): String {
    val normalized = rawName.trim()
    if (normalized.isEmpty()) return rawName
    return REMOTE_CACHE_HASH_PREFIX_REGEX.matchEntire(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: normalized
}

internal fun sanitizeRemoteCachedMetadataTitle(
    rawTitle: String,
    selectedFile: File?
): String {
    val normalizedTitle = rawTitle.trim()
    if (normalizedTitle.isBlank()) return rawTitle
    val file = selectedFile ?: return rawTitle
    if (file.parentFile?.name == REMOTE_SOURCE_CACHE_DIR) {
        val strippedHashPrefix = stripRemoteCacheHashPrefix(normalizedTitle)
        if (strippedHashPrefix == normalizedTitle) return rawTitle
        return inferredDisplayTitleForName(strippedHashPrefix)
    }
    return rawTitle
}

internal fun formatDisplayArtist(rawArtist: String?): String {
    val trimmed = rawArtist?.trim().orEmpty()
    if (trimmed.isBlank() || !trimmed.contains(';')) return trimmed
    return trimmed
        .split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
}

internal fun decodePercentEncodedForDisplay(raw: String?): String? {
    val trimmed = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    if (!trimmed.contains('%')) return trimmed
    return runCatching { URLDecoder.decode(trimmed, "UTF-8") }
        .getOrNull()
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: trimmed
}
