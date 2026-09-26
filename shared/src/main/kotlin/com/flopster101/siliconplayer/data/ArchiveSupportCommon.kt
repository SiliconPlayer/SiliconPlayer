package com.flopster101.siliconplayer.data

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import com.flopster101.siliconplayer.normalizeSourceIdentity
import com.flopster101.siliconplayer.remoteFilenameHintForUrl
import kotlin.math.max
import com.flopster101.siliconplayer.sanitizeRemoteLeafName

internal const val ARCHIVE_SOURCE_SCHEME = "archive"
internal const val ARCHIVE_DIRECTORY_SCHEME = "archive-dir"

internal data class ArchiveSourceRef(
    val archivePath: String,
    val entryPath: String
)

internal fun isSupportedArchive(file: File): Boolean {
    return file.isFile && file.extension.lowercase(Locale.ROOT) == "zip"
}

internal fun parseArchiveLogicalPath(path: String?): Pair<String, String?>? {
    return parseArchiveDirectoryPath(path)
}

internal fun isArchiveLogicalFolderPath(path: String?): Boolean {
    return parseArchiveLogicalPath(path) != null
}

private fun parseArchiveDirectoryPath(path: String?): Pair<String, String?>? {
    val raw = path?.trim().orEmpty()
    if (!raw.startsWith("$ARCHIVE_DIRECTORY_SCHEME://")) return null
    val body = raw.removePrefix("$ARCHIVE_DIRECTORY_SCHEME://")
    if (body.isBlank()) return null
    val hashIndex = body.indexOf('#')
    val archiveEncoded = if (hashIndex >= 0) body.substring(0, hashIndex) else body
    val dirEncoded = if (hashIndex >= 0 && hashIndex < body.lastIndex) {
        body.substring(hashIndex + 1)
    } else {
        null
    }
    val archivePath = runCatching { URLDecoder.decode(archiveEncoded, "UTF-8") }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val directoryPath = dirEncoded
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
    return archivePath to directoryPath
}

internal fun parseArchiveSourceId(sourceId: String?): ArchiveSourceRef? {
    if (sourceId.isNullOrBlank()) return null
    val trimmed = sourceId.trim()
    if (!trimmed.startsWith("$ARCHIVE_SOURCE_SCHEME://")) return null
    val body = trimmed.removePrefix("$ARCHIVE_SOURCE_SCHEME://")
    val hashIndex = body.indexOf('#')
    if (hashIndex <= 0 || hashIndex == body.lastIndex) return null
    val archiveEncoded = body.substring(0, hashIndex)
    val entryEncoded = body.substring(hashIndex + 1)
    val archivePath = runCatching { URLDecoder.decode(archiveEncoded, "UTF-8") }.getOrNull()?.trim() ?: return null
    val entryPath = runCatching { URLDecoder.decode(entryEncoded, "UTF-8") }.getOrNull()?.replace('\\', '/')?.trimStart('/') ?: return null
    if (archivePath.isBlank() || entryPath.isBlank()) return null
    return ArchiveSourceRef(
        archivePath = archivePath,
        entryPath = entryPath
    )
}

internal fun buildArchiveSourceId(
    archivePath: String,
    entryRelativePath: String
): String {
    val encodedArchive = URLEncoder.encode(archivePath, "UTF-8")
    val normalizedEntry = entryRelativePath.replace('\\', '/').trimStart('/')
    val encodedEntry = URLEncoder.encode(normalizedEntry, "UTF-8")
    return "$ARCHIVE_SOURCE_SCHEME://$encodedArchive#$encodedEntry"
}

internal fun buildArchiveDirectoryPath(
    archivePath: String,
    inArchiveDirectoryPath: String? = null
): String {
    val encodedArchive = URLEncoder.encode(archivePath, "UTF-8")
    val normalizedDirectory = inArchiveDirectoryPath
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
    return if (normalizedDirectory == null) {
        "$ARCHIVE_DIRECTORY_SCHEME://$encodedArchive"
    } else {
        "$ARCHIVE_DIRECTORY_SCHEME://$encodedArchive#${URLEncoder.encode(normalizedDirectory, "UTF-8")}"
    }
}

internal const val ARCHIVE_MOUNT_ROOT_DIR = "archive_mounts"
private const val ARCHIVE_READY_MARKER = ".ready"
private const val MAX_ARCHIVE_ENTRIES = 20_000

internal data class ResolvedArchiveDirectory(
    val archivePath: String,
    val parentPath: String,
    val mountDirectory: File,
    val targetDirectory: File
)

internal fun archiveMountRoot(cacheDir: File): File = File(cacheDir, ARCHIVE_MOUNT_ROOT_DIR)

internal fun touchArchiveMountMarker(marker: File) {
    val now = System.currentTimeMillis()
    marker.setLastModified(now)
    marker.parentFile?.setLastModified(now)
}

internal fun sha1Hex(value: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        for (b in digest) {
            append(((b.toInt() ushr 4) and 0xF).toString(16))
            append((b.toInt() and 0xF).toString(16))
        }
    }
}

internal fun findExistingCachedFileForSource(cacheRoot: File, url: String): File? {
    if (!cacheRoot.exists()) return null
    val prefix = "${sha1Hex(url)}_"
    return cacheRoot.listFiles().orEmpty()
        .firstOrNull { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part") && it.length() > 0L }
}

internal fun remoteCacheFileForSource(cacheRoot: File, url: String): File {
    if (!cacheRoot.exists()) {
        cacheRoot.mkdirs()
    }
    val safeLeaf = remoteFilenameHintForUrl(url)
        ?: sanitizeRemoteLeafName(runCatching { URI(url).host }.getOrNull())
        ?: "remote"
    return File(cacheRoot, "${sha1Hex(url)}_$safeLeaf")
}

internal fun ensureArchiveMounted(cacheDir: File, archiveFile: File): File {
    require(isSupportedArchive(archiveFile)) { "Unsupported archive: ${archiveFile.absolutePath}" }
    val mountRoot = archiveMountRoot(cacheDir)
    if (!mountRoot.exists()) {
        mountRoot.mkdirs()
    }
    val sourceStamp = "${archiveFile.absolutePath}|${archiveFile.lastModified()}|${archiveFile.length()}"
    val mountDir = File(mountRoot, sha1Hex(sourceStamp))
    val readyMarker = File(mountDir, ARCHIVE_READY_MARKER)
    if (mountDir.exists() && readyMarker.exists()) {
        touchArchiveMountMarker(readyMarker)
        return mountDir
    }

    if (mountDir.exists()) {
        mountDir.deleteRecursively()
    }
    if (!mountDir.mkdirs()) {
        error("Failed to create archive mount directory: ${mountDir.absolutePath}")
    }

    val mountCanonical = mountDir.canonicalPath
    val mountCanonicalPrefix = mountCanonical + File.separator
    var entriesSeen = 0

    try {
        java.util.zip.ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entriesSeen += 1
                if (entriesSeen > MAX_ARCHIVE_ENTRIES) {
                    error("Archive has too many entries")
                }

                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isBlank()) continue
                val outputFile = File(mountDir, normalizedName)
                val outputCanonical = outputFile.canonicalPath
                if (outputCanonical != mountDir.canonicalPath &&
                    !outputCanonical.startsWith(mountCanonicalPrefix)
                ) {
                    error("Archive entry escapes mount root: $normalizedName")
                }

                if (entry.isDirectory) {
                    outputFile.mkdirs()
                    continue
                }

                outputFile.parentFile?.mkdirs()
                if (!outputFile.exists()) {
                    outputFile.createNewFile()
                }
            }
        }
        readyMarker.writeText(sourceStamp)
        touchArchiveMountMarker(readyMarker)
        return mountDir
    } catch (t: Throwable) {
        mountDir.deleteRecursively()
        throw t
    }
}

internal fun resolveArchiveContainerParentLocation(archiveLocation: String): String? {
    val trimmed = archiveLocation.trim()
    if (trimmed.isBlank()) return null
    com.flopster101.siliconplayer.parseSmbSourceSpecFromInput(trimmed)?.let { smbSpec ->
        if (smbSpec.share.isBlank()) return null
        val normalizedPath = com.flopster101.siliconplayer.normalizeSmbPathForShare(smbSpec.path).orEmpty()
        val parentPath = normalizedPath
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
            .ifBlank { null }
        return com.flopster101.siliconplayer.buildSmbRequestUri(smbSpec.copy(path = parentPath))
    }
    com.flopster101.siliconplayer.parseHttpSourceSpecFromInput(trimmed)?.let { httpSpec ->
        val normalizedPath = com.flopster101.siliconplayer.normalizeHttpPath(httpSpec.path)
        val parentPath = normalizedPath
            .trimEnd('/')
            .substringBeforeLast('/', missingDelimiterValue = "")
            .trim()
            .ifBlank { null }
        return com.flopster101.siliconplayer.buildHttpRequestUri(httpSpec.copy(path = parentPath ?: "/"))
    }
    return File(trimmed).parentFile?.absolutePath
}

internal fun resolveArchiveFileForLocation(cacheDir: File, archiveLocation: String): File? {
    val trimmed = archiveLocation.trim()
    if (trimmed.isBlank()) return null
    val normalized = normalizeSourceIdentity(trimmed) ?: trimmed
    val normalizedUri = runCatching { java.net.URI(normalized) }.getOrNull()
    val scheme = normalizedUri?.scheme?.lowercase(Locale.ROOT)
    if (scheme == "http" || scheme == "https" || scheme == "smb") {
        val cacheRoot = File(cacheDir, com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR)
        return findExistingCachedFileForSource(cacheRoot, normalized)
    }
    val path = if (scheme == "file") {
        normalizedUri?.path?.trim()
    } else {
        normalized
    }
    val candidate = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
    return candidate.takeIf { it.exists() && it.isFile }
}

internal fun resolveArchiveLocationToFile(
    cacheDir: File,
    archiveLocation: String
): File? {
    return resolveArchiveFileForLocation(cacheDir, archiveLocation)
}

internal fun resolveArchiveLogicalDirectory(
    cacheDir: File,
    logicalPath: String?
): ResolvedArchiveDirectory? {
    val parsed = parseArchiveLogicalPath(logicalPath) ?: return null
    val archiveLocation = parsed.first
    val archiveFile = resolveArchiveFileForLocation(cacheDir, archiveLocation) ?: return null
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists()) return null
    val mountDir = ensureArchiveMounted(cacheDir, archiveFile)
    val targetDirectory = parsed.second?.let { entryPath ->
        File(mountDir, entryPath)
    } ?: mountDir

    val mountCanonicalPrefix = mountDir.canonicalPath + File.separator
    val targetCanonical = targetDirectory.canonicalPath
    if (targetCanonical != mountDir.canonicalPath &&
        !targetCanonical.startsWith(mountCanonicalPrefix)
    ) {
        return null
    }
    if (!targetDirectory.exists() || !targetDirectory.isDirectory) return null

    return ResolvedArchiveDirectory(
        archivePath = archiveLocation,
        parentPath = resolveArchiveContainerParentLocation(archiveLocation)
            ?: archiveFile.parentFile?.absolutePath
            ?: return null,
        mountDirectory = mountDir,
        targetDirectory = targetDirectory
    )
}

internal fun readZipEntrySizesForDirectory(
    cacheDir: File,
    archivePath: String,
    relativeDirectory: String
): Map<String, Long> {
    val normalizedDirectory = relativeDirectory.replace('\\', '/').trim('/')
    val archiveFile = resolveArchiveLocationToFile(cacheDir, archivePath) ?: return emptyMap()
    return try {
        java.util.zip.ZipFile(archiveFile).use { zip ->
            val sizes = LinkedHashMap<String, Long>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || entry.size < 0L) continue
                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (normalizedName.isBlank()) continue
                val parent = normalizedName.substringBeforeLast('/', "")
                if (parent != normalizedDirectory) continue
                val leaf = normalizedName.substringAfterLast('/')
                if (leaf.isBlank()) continue
                sizes[leaf] = entry.size
            }
            sizes
        }
    } catch (_: Throwable) {
        emptyMap()
    }
}

internal data class ArchiveMountCacheEntry(
    val directory: File,
    val readyMarker: File,
    val sizeBytes: Long,
    val lastAccessTimeMs: Long
)

internal data class ArchiveMountCachePruneResult(
    val deletedMounts: Int,
    val freedBytes: Long
)

internal data class ArchiveMountCacheClearResult(
    val deletedMounts: Int,
    val freedBytes: Long
)

internal fun clearArchiveMountCache(cacheDir: File): ArchiveMountCacheClearResult {
    val mountRoot = archiveMountRoot(cacheDir)
    if (!mountRoot.exists()) {
        return ArchiveMountCacheClearResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }
    var deletedMounts = 0
    var freedBytes = 0L
    mountRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .forEach { mountDir ->
            val bytes = directorySizeBytes(mountDir)
            if (mountDir.deleteRecursively()) {
                deletedMounts += 1
                freedBytes += bytes
            } else {
                mountDir.deleteOnExit()
            }
        }
    return ArchiveMountCacheClearResult(
        deletedMounts = deletedMounts,
        freedBytes = freedBytes
    )
}

internal fun enforceArchiveMountCacheLimits(
    cacheDir: File,
    maxMounts: Int,
    maxBytes: Long,
    maxAgeDays: Int
): ArchiveMountCachePruneResult {
    val mountRoot = archiveMountRoot(cacheDir)
    if (!mountRoot.exists()) {
        return ArchiveMountCachePruneResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }
    val normalizedMaxMounts = max(maxMounts, 1)
    val normalizedMaxBytes = max(maxBytes, 1L)
    val normalizedMaxAgeDays = max(maxAgeDays, 1)

    val now = System.currentTimeMillis()
    val cutoff = now - (normalizedMaxAgeDays.toLong() * 24L * 60L * 60L * 1000L)
    val entries = listArchiveMountCacheEntries(mountRoot)
    if (entries.isEmpty()) {
        return ArchiveMountCachePruneResult(
            deletedMounts = 0,
            freedBytes = 0L
        )
    }

    var deletedMounts = 0
    var freedBytes = 0L
    val survivors = mutableListOf<ArchiveMountCacheEntry>()
    entries.forEach { entry ->
        if (entry.lastAccessTimeMs <= cutoff && entry.directory.deleteRecursively()) {
            deletedMounts += 1
            freedBytes += entry.sizeBytes
        } else {
            survivors.add(entry)
        }
    }

    var totalBytes = survivors.sumOf { it.sizeBytes }
    var totalMounts = survivors.size
    survivors.sortBy { it.lastAccessTimeMs }
    for (entry in survivors) {
        if (totalMounts <= normalizedMaxMounts && totalBytes <= normalizedMaxBytes) break
        if (entry.directory.deleteRecursively()) {
            deletedMounts += 1
            freedBytes += entry.sizeBytes
            totalMounts -= 1
            totalBytes = (totalBytes - entry.sizeBytes).coerceAtLeast(0L)
        } else {
            entry.directory.deleteOnExit()
        }
    }

    return ArchiveMountCachePruneResult(
        deletedMounts = deletedMounts,
        freedBytes = freedBytes
    )
}

private fun listArchiveMountCacheEntries(mountRoot: File): List<ArchiveMountCacheEntry> {
    return mountRoot.listFiles().orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { mountDir ->
            val readyMarker = File(mountDir, ARCHIVE_READY_MARKER)
            if (!readyMarker.exists() || !readyMarker.isFile) {
                mountDir.deleteRecursively()
                return@mapNotNull null
            }
            ArchiveMountCacheEntry(
                directory = mountDir,
                readyMarker = readyMarker,
                sizeBytes = directorySizeBytes(mountDir),
                lastAccessTimeMs = max(readyMarker.lastModified(), mountDir.lastModified())
            )
        }
}

private fun directorySizeBytes(directory: File): Long {
    if (!directory.exists()) return 0L
    return directory.walkTopDown()
        .filter { it.isFile }
        .sumOf { it.length().coerceAtLeast(0L) }
}
