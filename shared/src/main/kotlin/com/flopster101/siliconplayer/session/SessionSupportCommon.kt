package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
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

// Cached remote tracks live under the remote cache dir; the index maps the
// hash-prefixed file name back to the source it was downloaded from.
internal fun resolveCachedRemoteSourceId(localPath: String): String? {
    val candidate = File(localPath)
    val parent = candidate.parentFile ?: return null
    if (parent.name != REMOTE_SOURCE_CACHE_DIR) return null
    return sourceIdForCachedFileName(parent, candidate.name)
}

internal fun resolvePlaybackSourceLabel(
    selectedFile: File?,
    sourceId: String?,
    networkNodes: List<NetworkNode> = emptyList()
): String? {
    if (selectedFile == null) return null
    val normalizedSource = normalizeSourceIdentity(sourceId ?: selectedFile.absolutePath) ?: return "Local"
    val scheme = normalizedSource
        .substringBefore(':', missingDelimiterValue = "")
        .lowercase(Locale.ROOT)
    val isCachedRemote = selectedFile.absolutePath.contains("/$REMOTE_SOURCE_CACHE_DIR/")
    when (scheme) {
        "smb" -> {
            val smbSpec = parseSmbSourceSpecFromInput(normalizedSource) ?: return "SMB"
            val displayHost = resolveSmbDisplayHost(smbSpec.host, networkNodes)
            val decodedShare = decodePercentEncodedForDisplay(smbSpec.share) ?: smbSpec.share
            val smbTarget = if (decodedShare.isBlank()) {
                displayHost
            } else {
                "$displayHost/$decodedShare"
            }
            val suffix = if (isCachedRemote) " (cached)" else ""
            return "SMB ($smbTarget)$suffix"
        }
        "archive" -> return "Archive"
        "http", "https" -> return if (isCachedRemote) "Streamed (cached)" else "Streamed"
    }
    return "Local"
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

internal const val MANUAL_INPUT_INVALID_MESSAGE =
    "Enter a valid file/folder path, file:// path, http(s) URL, or smb:// source"

internal enum class ManualSourceType {
    LocalFile,
    LocalDirectory,
    RemoteUrl,
    Smb
}

internal data class ManualSourceResolution(
    val type: ManualSourceType,
    val sourceId: String,
    val requestUrl: String,
    val localFile: File?,
    val directoryPath: String?,
    val displayFile: File?,
    val smbSpec: SmbSourceSpec? = null
)

internal data class ManualSourceOpenOptions(
    val forceCaching: Boolean = false,
    val initialSubtuneIndex: Int? = null
)

private fun manualSourceScheme(trimmed: String): String? {
    val candidate = trimmed.substringBefore(':', missingDelimiterValue = "")
    if (candidate.isEmpty() || candidate.length > 16) return null
    if (!candidate.first().isLetter()) return null
    if (!candidate.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
    return candidate.lowercase(Locale.ROOT)
}

internal fun resolveManualSourceInput(rawInput: String): ManualSourceResolution? {
    val trimmed = rawInput.trim()
    if (trimmed.isEmpty()) return null
    rememberEmbeddedNetworkCredentials(trimmed)

    val scheme = manualSourceScheme(trimmed)
    if (scheme == "http" || scheme == "https") {
        val httpSpec = resolveCredentialedHttpSpec(trimmed) ?: return null
        val normalizedUrl = buildHttpSourceId(httpSpec)
        val requestUrl = stripUrlFragment(buildHttpRequestUri(httpSpec))
        val safeName = remoteFilenameHintForUrl(trimmed)
            ?: sanitizeRemoteLeafName(runCatching { URI(trimmed).host }.getOrNull())
            ?: "remote"
        return ManualSourceResolution(
            type = ManualSourceType.RemoteUrl,
            sourceId = normalizedUrl,
            requestUrl = requestUrl,
            localFile = null,
            directoryPath = null,
            displayFile = File("/virtual/remote/$safeName"),
            smbSpec = null
        )
    }

    if (scheme == "smb") {
        val smbSpec = resolveCredentialedSmbSpec(trimmed) ?: return null
        val sourceId = buildSmbSourceId(smbSpec)
        val requestUri = buildSmbRequestUri(smbSpec)
        val safeName = sanitizeRemoteLeafName(smbSpec.path?.substringAfterLast('/'))
            ?: sanitizeRemoteLeafName(smbSpec.share)
            ?: "smb"
        return ManualSourceResolution(
            type = ManualSourceType.Smb,
            sourceId = sourceId,
            requestUrl = requestUri,
            localFile = null,
            directoryPath = null,
            displayFile = File("/virtual/remote/$safeName"),
            smbSpec = smbSpec
        )
    }

    fun resolveLocalPath(path: String, sourceIdOverride: String? = null): ManualSourceResolution? {
        val file = File(path).absoluteFile
        if (!file.exists()) return null
        if (file.isDirectory) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalDirectory,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = null,
                directoryPath = file.absolutePath,
                displayFile = null
            )
        }
        if (file.isFile) {
            return ManualSourceResolution(
                type = ManualSourceType.LocalFile,
                sourceId = sourceIdOverride ?: file.absolutePath,
                requestUrl = sourceIdOverride ?: file.absolutePath,
                localFile = file,
                directoryPath = null,
                displayFile = file,
                smbSpec = null
            )
        }
        return null
    }

    if (scheme == "file") {
        val localPath = (runCatching { URI(trimmed).path }.getOrNull() ?: trimmed.substringAfter(':', ""))
            .takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("/")) "/" + it.trimStart('/') else it }
            ?: return null
        return resolveLocalPath(
            localPath,
            sourceIdOverride = "$scheme:${trimmed.substringAfter(':')}"
        )
    }

    val expandedPath = when {
        trimmed == "~" -> System.getProperty("user.home") ?: trimmed
        trimmed.startsWith("~/") -> {
            val home = System.getProperty("user.home") ?: return null
            home + trimmed.removePrefix("~")
        }

        else -> trimmed
    }
    return resolveLocalPath(expandedPath)
}

internal fun storagePresentationForPath(path: String): StoragePresentation {
    val scheme = runCatching { URI(path) }.getOrNull()?.scheme?.lowercase(Locale.ROOT)
        ?: if (path.contains("://")) path.substringBefore("://").lowercase(Locale.ROOT) else null
    return when (scheme) {
        "playlist" -> StoragePresentation("Playlist", Icons.Default.LibraryMusic)
        "archive-dir", "archive" -> StoragePresentation("Archive", Icons.Default.Folder)
        "http", "https" -> {
            val host = runCatching { URI(path)?.host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown host"
            StoragePresentation("${scheme.uppercase(Locale.ROOT)} ($host)", NetworkIcons.WorldCode)
        }
        "smb" -> {
            val host = runCatching { URI(path)?.host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "SMB"
            StoragePresentation("SMB ($host)", NetworkIcons.SmbShare)
        }
        else -> StoragePresentation("Local", Icons.Default.Folder)
    }
}

internal fun isRemoteQueuePlaybackSource(sourceId: String?): Boolean {
    val normalizedSourceId = normalizeSourceIdentity(sourceId) ?: return false
    val scheme = normalizedSourceId.substringBefore(':', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https", "smb" -> true
        else -> false
    }
}
