package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import java.io.File
import java.net.URI
import java.util.Locale

internal fun normalizeSourceIdentity(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val trimmed = path.trim().substringBefore("#subtune=")
    val uri = runCatching { URI(trimmed) }.getOrNull()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        ?: if (trimmed.contains("://")) trimmed.substringBefore("://").lowercase(Locale.ROOT) else null
    return when (scheme) {
        "playlist" -> trimmed
        "http", "https" -> {
            val httpSpec = parseHttpSourceSpecFromInput(trimmed)
            if (httpSpec != null) {
                buildHttpSourceId(httpSpec)
            } else {
                uri?.toString() ?: trimmed
            }
        }
        "archive" -> {
            val parsedArchive = parseArchiveSourceId(trimmed) ?: return trimmed
            val normalizedArchivePath = normalizeArchiveContainerLocation(parsedArchive.archivePath)
            buildArchiveSourceId(normalizedArchivePath, parsedArchive.entryPath)
        }
        "archive-dir" -> {
            val parsedArchiveDirectory = parseArchiveLogicalPath(trimmed) ?: return trimmed
            val normalizedArchivePath = normalizeArchiveContainerLocation(parsedArchiveDirectory.first)
            buildArchiveDirectoryPath(
                archivePath = normalizedArchivePath,
                inArchiveDirectoryPath = parsedArchiveDirectory.second
            )
        }
        "file" -> {
            val localPath = (uri?.path ?: trimmed.removePrefix("file://")).takeIf { it.isNotBlank() } ?: return null
            try {
                File(localPath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(localPath).absoluteFile.normalize().path
            }
        }
        "smb" -> {
            val smbSpec = parseSmbSourceSpecFromInput(trimmed) ?: return null
            val canonicalHost = resolveSmbCanonicalHost(smbSpec.host)
            buildSmbSourceId(smbSpec.copy(host = canonicalHost))
        }
        else -> {
            try {
                File(trimmed).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(trimmed).absoluteFile.normalize().path
            }
        }
    }
}

private fun normalizeArchiveContainerLocation(rawArchiveLocation: String): String {
    val scheme = runCatching { URI(rawArchiveLocation).scheme?.lowercase(Locale.ROOT) }.getOrNull()
    return when (scheme) {
        "http", "https", "smb" -> normalizeSourceIdentity(rawArchiveLocation) ?: rawArchiveLocation
        "file" -> {
            val uriPath = runCatching { URI(rawArchiveLocation).path }.getOrNull()
            val localPath = uriPath?.takeIf { it.isNotBlank() } ?: rawArchiveLocation
            try {
                File(localPath).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(localPath).absoluteFile.normalize().path
            }
        }
        else -> {
            try {
                File(rawArchiveLocation).canonicalFile.absolutePath
            } catch (_: Exception) {
                File(rawArchiveLocation).absoluteFile.normalize().path
            }
        }
    }
}

internal fun samePath(a: String?, b: String?): Boolean {
    if (a != null && a == b) return true
    val left = normalizeSourceIdentity(a) ?: return false
    val right = normalizeSourceIdentity(b) ?: return false
    return left == right
}

internal fun sortPinnedHomeEntriesForDisplay(
    entries: List<HomePinnedEntry>
): List<HomePinnedEntry> {
    return entries.sortedWith(
        compareByDescending<HomePinnedEntry> { it.isFolder }
            .thenByDescending { it.pinnedAtEpochMs }
    )
}

internal fun resolvePinnedEvictionCandidate(
    entries: List<HomePinnedEntry>
): HomePinnedEntry? {
    val files = entries.filterNot { it.isFolder }
    val oldestFile = files.minByOrNull { it.pinnedAtEpochMs }
    if (oldestFile != null) return oldestFile
    return entries.minByOrNull { it.pinnedAtEpochMs }
}

internal fun previewPinnedHomeEntryInsertion(
    current: List<HomePinnedEntry>,
    candidate: HomePinnedEntry,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
): HomePinInsertPreview {
    val normalizedPath = normalizeSourceIdentity(candidate.path) ?: candidate.path
    val alreadyExists = current.any { existing -> samePath(existing.path, normalizedPath) }
    if (alreadyExists || current.size < maxItems) {
        return HomePinInsertPreview(
            requiresConfirmation = false,
            evictionCandidate = null
        )
    }
    val evictionCandidate = resolvePinnedEvictionCandidate(current)
    return HomePinInsertPreview(
        requiresConfirmation = evictionCandidate != null,
        evictionCandidate = evictionCandidate
    )
}

internal fun buildUpdatedPinnedHomeEntries(
    current: List<HomePinnedEntry>,
    candidate: HomePinnedEntry,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
): List<HomePinnedEntry> {
    val normalizedPath = normalizeSourceIdentity(candidate.path) ?: candidate.path
    val existing = current.firstOrNull { existing -> samePath(existing.path, normalizedPath) }
    val normalizedCandidate = candidate.copy(
        path = normalizedPath,
        locationId = candidate.locationId ?: existing?.locationId,
        title = candidate.title?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.title,
        artist = candidate.artist?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.artist,
        decoderName = candidate.decoderName?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.decoderName,
        sourceNodeId = candidate.sourceNodeId ?: existing?.sourceNodeId,
        artworkThumbnailCacheKey = candidate.artworkThumbnailCacheKey
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: existing?.artworkThumbnailCacheKey,
        pinnedAtEpochMs = System.currentTimeMillis()
    )
    val withoutExisting = current.filterNot { entry -> samePath(entry.path, normalizedPath) }
    val combined = listOf(normalizedCandidate) + withoutExisting
    if (combined.size <= maxItems) {
        return combined
    }
    val evictionCandidate = resolvePinnedEvictionCandidate(withoutExisting) ?: return combined.take(maxItems)
    var removed = false
    return combined.filter { entry ->
        if (!removed && samePath(entry.path, evictionCandidate.path)) {
            removed = true
            false
        } else {
            true
        }
    }
}

internal data class CacheExportResult(
    val exportedCount: Int,
    val failedCount: Int,
    val skippedCount: Int = 0,
    val cancelled: Boolean = false,
    val invalidDestination: Boolean = false
)

internal data class ExportFileItem(
    val sourceFile: File,
    val displayNameOverride: String? = null
)

internal enum class ExportConflictAction {
    Overwrite,
    Skip,
    Cancel
}

internal data class ExportConflictDecision(
    val action: ExportConflictAction,
    val applyToAll: Boolean = false
)

internal data class ExportNameConflict(
    val fileName: String
)
