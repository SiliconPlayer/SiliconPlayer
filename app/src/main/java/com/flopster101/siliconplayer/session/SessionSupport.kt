package com.flopster101.siliconplayer
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.TabletAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.ui.graphics.vector.ImageVector
import com.flopster101.siliconplayer.data.buildArchiveDirectoryPath
import com.flopster101.siliconplayer.data.buildArchiveSourceId
import com.flopster101.siliconplayer.data.parseArchiveLogicalPath
import com.flopster101.siliconplayer.data.parseArchiveSourceId
import com.flopster101.siliconplayer.platform.AndroidAppPreferences
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import java.io.File
import java.util.Locale


internal fun resolveStorageRootFromAppDir(appSpecificDir: File): File? {
    val marker = "/Android/"
    val absolutePath = appSpecificDir.absolutePath
    val markerIndex = absolutePath.indexOf(marker)
    if (markerIndex <= 0) return null
    return File(absolutePath.substring(0, markerIndex))
}

internal fun detectStorageDescriptors(context: Context): List<StorageDescriptor> {
    val descriptors = mutableListOf<StorageDescriptor>()
    val seen = mutableSetOf<String>()

    fun add(path: String, label: String, icon: ImageVector) {
        if (path in seen) return
        seen += path
        descriptors += StorageDescriptor(path, label, icon)
    }

    add("/", "Root (/)", Icons.Default.Folder)
    val internalRoot = Environment.getExternalStorageDirectory().absolutePath
    val internalIcon = if (context.resources.configuration.smallestScreenWidthDp >= 600) {
        Icons.Default.TabletAndroid
    } else {
        Icons.Default.PhoneAndroid
    }
    add(internalRoot, "Internal storage", internalIcon)

    context.getExternalFilesDirs(null)
        .orEmpty()
        .forEach { externalDir ->
            if (externalDir == null) return@forEach
            val volumeRoot = resolveStorageRootFromAppDir(externalDir) ?: return@forEach
            if (!Environment.isExternalStorageRemovable(externalDir)) return@forEach

            val lower = volumeRoot.absolutePath.lowercase()
            val isUsb = lower.contains("usb") || lower.contains("otg")
            val volumeName = volumeRoot.name.ifBlank { volumeRoot.absolutePath }
            val label = volumeName
            add(volumeRoot.absolutePath, label, if (isUsb) Icons.Default.Usb else Icons.Default.SdCard)
        }

    return descriptors
}

internal fun storagePresentationForEntry(
    context: Context,
    entry: RecentPathEntry,
    descriptors: List<StorageDescriptor>,
    networkNodes: List<NetworkNode> = emptyList()
): StoragePresentation {
    val rawPath = entry.path.trim()
    val normalizedPath = normalizeSourceIdentity(rawPath) ?: rawPath
    val parsed = Uri.parse(normalizedPath)
    val scheme = parsed.scheme?.lowercase(Locale.ROOT)
    if (scheme == "playlist") {
        return StoragePresentation(
            label = "Playlist",
            icon = Icons.Default.LibraryMusic
        )
    }
    if (scheme == "archive-dir") {
        val archivePath = parseArchiveLogicalPath(normalizedPath)?.first
            ?: parseArchiveLogicalPath(rawPath)?.first
        if (!archivePath.isNullOrBlank()) {
            val archiveUri = Uri.parse(archivePath)
            val archiveScheme = archiveUri.scheme?.lowercase(Locale.ROOT)
            if (archiveScheme == "http" || archiveScheme == "https") {
                val hostLabel = archiveUri.host?.takeIf { it.isNotBlank() } ?: "unknown host"
                val protocolLabel = archiveScheme.uppercase(Locale.ROOT)
                val qualifier = if (isRemoteSourceCached(context, archivePath)) "Cached" else null
                return StoragePresentation(
                    label = "$protocolLabel ($hostLabel)",
                    icon = NetworkIcons.WorldCode,
                    qualifier = qualifier
                )
            }
            if (archiveScheme == "smb") {
                val smbSpec = parseSmbSourceSpecFromInput(archivePath)
                val qualifier = if (isRemoteSourceCached(context, archivePath)) "Cached" else null
                val smbLabel = if (smbSpec == null) {
                    "SMB"
                } else {
                    val hostLabel = resolveRecentSmbHostDisplayLabel(
                        entry = entry,
                        smbSpec = smbSpec,
                        networkNodes = networkNodes
                    )
                    val smbTarget = if (smbSpec.share.isBlank()) {
                        hostLabel
                    } else {
                        "$hostLabel/${smbSpec.share}"
                    }
                    "SMB ($smbTarget)"
                }
                return StoragePresentation(
                    label = smbLabel,
                    icon = NetworkIcons.SmbShare,
                    qualifier = qualifier
                )
            }
            val archiveName = sourceLeafNameForDisplay(archivePath)
                ?.takeIf { it.isNotBlank() }
                ?: decodePercentEncodedForDisplay(File(archivePath).name)
                    ?.takeIf { it.isNotBlank() }
                ?: "Archive"
            return StoragePresentation(
                label = archiveName,
                icon = Icons.Default.Folder
            )
        }
    }
    if (scheme == "http" || scheme == "https") {
        val hostLabel = parsed.host?.takeIf { it.isNotBlank() } ?: "unknown host"
        val protocolLabel = scheme.uppercase(Locale.ROOT)
        val qualifier = if (isRemoteSourceCached(context, normalizedPath)) "Cached" else null
        return StoragePresentation(
            label = "$protocolLabel ($hostLabel)",
            icon = NetworkIcons.WorldCode,
            qualifier = qualifier
        )
    }
    if (scheme == "smb") {
        val smbSpec = parseSmbSourceSpecFromInput(normalizedPath)
        val qualifier = if (isRemoteSourceCached(context, normalizedPath)) "Cached" else null
        val smbLabel = if (smbSpec == null) {
            "SMB"
        } else {
            val hostLabel = resolveRecentSmbHostDisplayLabel(
                entry = entry,
                smbSpec = smbSpec,
                networkNodes = networkNodes
            )
            val smbTarget = if (smbSpec.share.isBlank()) {
                hostLabel
            } else {
                "$hostLabel/${smbSpec.share}"
            }
            "SMB ($smbTarget)"
        }
        return StoragePresentation(
            label = smbLabel,
            icon = NetworkIcons.SmbShare,
            qualifier = qualifier
        )
    }
    if (scheme == "archive") {
        val archiveName = parseArchiveSourceId(entry.path)
            ?.archivePath
            ?.let { archivePath ->
                sourceLeafNameForDisplay(archivePath)
                    ?.takeIf { it.isNotBlank() }
                    ?: decodePercentEncodedForDisplay(File(archivePath).name)
                        ?.takeIf { it.isNotBlank() }
                    ?: archivePath
            }
            ?: "Archive"
        return StoragePresentation(
            label = archiveName,
            icon = Icons.Default.Folder
        )
    }

    val pathForMatching = when (scheme) {
        "file" -> parsed.path?.takeIf { it.isNotBlank() } ?: normalizedPath
        else -> normalizedPath
    }

    entry.locationId?.let { locationId ->
        descriptors.firstOrNull { it.rootPath == locationId }?.let {
            return StoragePresentation(label = it.label, icon = it.icon)
        }
    }
    val matching = descriptors
        .filter { pathForMatching == it.rootPath || pathForMatching.startsWith("${it.rootPath}/") }
        .maxByOrNull { it.rootPath.length }
    return if (matching != null) {
        StoragePresentation(label = matching.label, icon = matching.icon)
    } else {
        StoragePresentation(label = "Unknown storage", icon = Icons.Default.Folder)
    }
}

private fun resolveRecentSmbHostDisplayLabel(
    entry: RecentPathEntry,
    smbSpec: SmbSourceSpec,
    networkNodes: List<NetworkNode>
): String {
    val sourceNode = entry.sourceNodeId
        ?.let { sourceId -> networkNodes.firstOrNull { it.id == sourceId } }
        ?.takeIf { it.type == NetworkNodeType.RemoteSource && it.sourceKind == NetworkSourceKind.Smb }
    val resolved = sourceNode?.smbDiscoveredHostName
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: sourceNode?.title
            ?.trim()
            .takeUnless { it.isNullOrBlank() || it.startsWith("smb://", ignoreCase = true) }
        ?: resolveSmbDisplayHost(smbSpec.host, networkNodes)
    return resolved.ifBlank { smbSpec.host }
}

internal fun readRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    maxItems: Int,
    perSubtuneRows: Boolean = false
): List<RecentPathEntry> {
    val dir = DomainStoreDirs.configDir
    return if (dir != null) {
        readRecentEntries(dir, key, maxItems, AndroidAppPreferences(prefs), perSubtuneRows)
    } else {
        readRecentEntries(AndroidAppPreferences(prefs), key, maxItems, perSubtuneRows)
    }
}

internal fun readPinnedHomeEntries(
    prefs: android.content.SharedPreferences,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
): List<HomePinnedEntry> {
    val dir = DomainStoreDirs.configDir
    return if (dir != null) {
        readPinnedHomeEntries(dir, key, maxItems, AndroidAppPreferences(prefs))
    } else {
        readPinnedHomeEntries(AndroidAppPreferences(prefs), key, maxItems)
    }
}

internal fun writePinnedHomeEntries(
    prefs: android.content.SharedPreferences,
    entries: List<HomePinnedEntry>,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
) {
    val dir = DomainStoreDirs.configDir
    if (dir != null) {
        writePinnedHomeEntries(dir, entries, key, maxItems)
    } else {
        writePinnedHomeEntries(AndroidAppPreferences(prefs), entries, key, maxItems)
    }
}


internal fun writeRecentEntries(
    prefs: android.content.SharedPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int,
    perSubtuneRows: Boolean = false
) {
    val dir = DomainStoreDirs.configDir
    if (dir != null) {
        writeRecentEntries(dir, key, entries, maxItems, perSubtuneRows)
    } else {
        writeRecentEntries(AndroidAppPreferences(prefs), key, entries, maxItems, perSubtuneRows)
    }
}

internal fun buildUpdatedRecentFolders(
    current: List<RecentPathEntry>,
    newPath: String,
    locationId: String?,
    sourceNodeId: Long? = null,
    title: String? = null,
    limit: Int
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(newPath) ?: newPath
    val existing = current.firstOrNull { samePath(it.path, normalized) }
    val updated = listOf(
        RecentPathEntry(
            path = normalized,
            locationId = locationId ?: existing?.locationId,
            title = title?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.title,
            artist = null,
            sourceNodeId = sourceNodeId ?: existing?.sourceNodeId
        )
    ) + current.filterNot { samePath(it.path, normalized) }
    return updated.take(limit)
}

internal fun buildUpdatedRecentPlayedTracks(
    current: List<RecentPathEntry>,
    newPath: String,
    locationId: String?,
    sourceNodeId: Long? = null,
    title: String? = null,
    artist: String? = null,
    decoderName: String? = null,
    artworkThumbnailCacheKey: String? = null,
    isPlaylist: Boolean = false,
    playlistSourceHint: String? = null,
    subtuneIndex: Int? = null,
    perSubtuneRows: Boolean = false,
    clearBlankMetadataOnUpdate: Boolean = false,
    limit: Int
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(newPath) ?: newPath
    val existing = current.firstOrNull {
        if (perSubtuneRows) sameRecentTrack(it.path, it.subtuneIndex, normalized, subtuneIndex)
        else samePath(it.path, normalized)
    }
    val resolvedIsPlaylist = isPlaylist || existing?.isPlaylist == true
    val resolvedPlaylistSourceHint = playlistSourceHint
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: existing?.playlistSourceHint
    val trimmedTitle = title?.trim()
    val trimmedArtist = artist?.trim()
    val resolvedTitle = when {
        resolvedIsPlaylist -> null
        clearBlankMetadataOnUpdate && trimmedTitle != null -> trimmedTitle.ifBlank { null }
        else -> trimmedTitle.takeUnless { it.isNullOrBlank() } ?: existing?.title
    }
    val resolvedArtist = when {
        resolvedIsPlaylist -> null
        clearBlankMetadataOnUpdate && trimmedArtist != null -> trimmedArtist.ifBlank { null }
        else -> trimmedArtist.takeUnless { it.isNullOrBlank() } ?: existing?.artist
    }
    val resolvedDecoderName = decoderName?.trim().takeUnless { it.isNullOrBlank() } ?: existing?.decoderName
    val resolvedArtworkThumbnailCacheKey = artworkThumbnailCacheKey
        ?.trim()
        .takeUnless { it.isNullOrBlank() }
        ?: existing?.artworkThumbnailCacheKey
    val updated = listOf(
        RecentPathEntry(
            path = normalized,
            locationId = locationId ?: existing?.locationId,
            title = resolvedTitle,
            artist = resolvedArtist,
            decoderName = resolvedDecoderName,
            sourceNodeId = sourceNodeId ?: existing?.sourceNodeId,
            artworkThumbnailCacheKey = resolvedArtworkThumbnailCacheKey,
            isPlaylist = resolvedIsPlaylist,
            playlistSourceHint = resolvedPlaylistSourceHint,
            subtuneIndex = subtuneIndex ?: existing?.subtuneIndex
        )
    ) + current.filterNot {
        if (perSubtuneRows) sameRecentTrack(it.path, it.subtuneIndex, normalized, subtuneIndex)
        else samePath(it.path, normalized)
    }
    return updated.take(limit)
}

internal fun mergePinnedFileMetadataAndArtwork(
    current: List<HomePinnedEntry>,
    path: String,
    title: String?,
    artist: String?,
    decoderName: String?,
    artworkThumbnailCacheKey: String?
): List<HomePinnedEntry> {
    val normalizedTitle = title?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedDecoder = decoderName?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtworkKey = artworkThumbnailCacheKey?.trim().takeUnless { it.isNullOrBlank() }
    var changed = false
    val updated = current.map { entry ->
        if (entry.isFolder || !samePath(entry.path, path)) {
            entry
        } else {
            val next = entry.copy(
                title = normalizedTitle ?: entry.title,
                artist = normalizedArtist ?: entry.artist,
                decoderName = normalizedDecoder ?: entry.decoderName,
                artworkThumbnailCacheKey = normalizedArtworkKey ?: entry.artworkThumbnailCacheKey
            )
            if (next != entry) {
                changed = true
            }
            next
        }
    }
    return if (changed) updated else current
}

internal fun mergeRecentPlayedTrackMetadata(
    current: List<RecentPathEntry>,
    path: String,
    title: String?,
    artist: String?
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(path) ?: path
    val normalizedTitle = title?.trim().takeUnless { it.isNullOrBlank() }
    val normalizedArtist = artist?.trim().takeUnless { it.isNullOrBlank() }
    if (normalizedTitle == null && normalizedArtist == null) return current
    var changed = false
    val updated = current.map { entry ->
        if (!samePath(entry.path, normalized)) return@map entry
        if (entry.isPlaylist) return@map entry
        val resolvedTitle = normalizedTitle ?: entry.title
        val resolvedArtist = normalizedArtist ?: entry.artist
        if (resolvedTitle == entry.title && resolvedArtist == entry.artist) {
            entry
        } else {
            changed = true
            entry.copy(title = resolvedTitle, artist = resolvedArtist)
        }
    }
    return if (changed) updated else current
}

internal fun resolveShareableFileForRecentEntry(
    context: Context,
    entry: RecentPathEntry
): File? {
    val normalized = normalizeSourceIdentity(entry.path) ?: return null
    val uri = Uri.parse(normalized)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    return when (scheme) {
        "http", "https", "smb" -> {
            findExistingCachedFileForSource(File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR), normalized)
                ?.takeIf { it.exists() && it.isFile }
        }

        "file" -> {
            uri.path?.let { File(it) }?.takeIf { it.exists() && it.isFile }
        }

        else -> {
            File(normalized).takeIf { it.exists() && it.isFile }
        }
    }
}

internal fun resolveStorageLocationForPath(
    path: String,
    descriptors: List<StorageDescriptor>
): String? {
    val normalizedPath = path.trim()
    if (normalizedPath.isBlank()) return null
    return descriptors
        .filter { descriptor ->
            val root = descriptor.rootPath.trimEnd('/')
            if (root.isEmpty()) {
                normalizedPath.startsWith("/")
            } else {
                normalizedPath == root || normalizedPath.startsWith("$root/")
            }
        }
        .maxByOrNull { it.rootPath.length }
        ?.rootPath
}
