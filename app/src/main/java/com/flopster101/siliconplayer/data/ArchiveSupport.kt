package com.flopster101.siliconplayer.data

import android.net.Uri
import android.content.Context
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.buildHttpRequestUri
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.findExistingCachedFileForSource
import com.flopster101.siliconplayer.normalizeHttpPath
import com.flopster101.siliconplayer.normalizeSourceIdentity
import com.flopster101.siliconplayer.normalizeSmbPathForShare
import com.flopster101.siliconplayer.parseHttpSourceSpecFromInput
import com.flopster101.siliconplayer.parseSmbSourceSpecFromInput
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.max
import java.util.zip.ZipFile

private const val ARCHIVE_READY_MARKER = ".ready"
private const val MAX_ARCHIVE_ENTRIES = 20_000
private const val MAX_ARCHIVE_TOTAL_UNCOMPRESSED_BYTES = 1_000_000_000L // ~1 GB
private const val MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES = 256_000_000L // ~256 MB
internal const val ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT = 24
internal const val ARCHIVE_CACHE_MAX_BYTES_DEFAULT = 2L * 1024L * 1024L * 1024L // 2 GB
internal const val ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT = 14

internal data class ArchiveMountedPathOrigin(
    val mountRootPath: String,
    val archivePath: String,
    val parentPath: String
)

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

internal fun ensureArchiveMounted(context: Context, archiveFile: File): File =
    ensureArchiveMounted(context.cacheDir, archiveFile)



internal fun resolveArchiveSourceToMountedFile(
    context: Context,
    sourceId: String?
): File? {
    val parsed = parseArchiveSourceId(sourceId) ?: return null
    val archiveFile = resolveArchiveFileForLocation(context, parsed.archivePath) ?: return null
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists() || !archiveFile.isFile) return null
    val mountDir = ensureArchiveMounted(context, archiveFile)
    val targetFile = File(mountDir, parsed.entryPath)
    val mountCanonicalPrefix = mountDir.canonicalPath + File.separator
    val targetCanonical = targetFile.canonicalPath
    if (targetCanonical != mountDir.canonicalPath &&
        !targetCanonical.startsWith(mountCanonicalPrefix)
    ) {
        return null
    }
    if (!targetFile.exists() || !targetFile.isFile) return null
    ensureArchiveEntryExtracted(
        archiveFile = archiveFile,
        mountDir = mountDir,
        entryPath = parsed.entryPath,
        outputFile = targetFile
    )
    return targetFile
}

internal fun resolveArchiveMountedCompanionPath(
    basePath: String?,
    requestedPath: String?
): String? {
    val base = basePath?.trim().orEmpty().takeIf { it.isNotBlank() } ?: return null
    val requestedRaw = requestedPath?.trim().orEmpty().takeIf { it.isNotBlank() } ?: return null
    val baseFile = File(base)
    val mountRoot = findArchiveMountRoot(baseFile) ?: return null
    val mountCanonical = mountRoot.canonicalPath
    val mountCanonicalPrefix = "$mountCanonical${File.separator}"

    val resolvedRequested = runCatching {
        val normalizedRequested = requestedRaw.replace('\\', '/')
        val requestedAsFile = File(normalizedRequested)
        val candidate = if (requestedAsFile.isAbsolute) {
            requestedAsFile
        } else {
            File(baseFile.parentFile ?: mountRoot, normalizedRequested)
        }
        candidate.canonicalFile
    }.getOrNull() ?: return null

    val requestedCanonical = resolvedRequested.canonicalPath
    if (requestedCanonical != mountCanonical && !requestedCanonical.startsWith(mountCanonicalPrefix)) {
        return null
    }
    if (resolvedRequested.isDirectory) {
        return null
    }

    val readyMarker = File(mountRoot, ARCHIVE_READY_MARKER)
    if (!readyMarker.exists() || !readyMarker.isFile) {
        return null
    }
    val archivePath = parseArchivePathFromReadyMarker(readyMarker) ?: return null
    val archiveFile = File(archivePath)
    if (!isSupportedArchive(archiveFile) || !archiveFile.exists() || !archiveFile.isFile) {
        return null
    }

    val relativeEntryPath = requestedCanonical
        .removePrefix(mountCanonical)
        .trimStart(File.separatorChar)
        .replace('\\', '/')
        .trimStart('/')
    if (relativeEntryPath.isBlank()) {
        return null
    }

    ensureArchiveEntryExtracted(
        archiveFile = archiveFile,
        mountDir = mountRoot,
        entryPath = relativeEntryPath,
        outputFile = resolvedRequested
    )
    touchArchiveMountMarker(readyMarker)
    return if (resolvedRequested.exists() && resolvedRequested.isFile) {
        resolvedRequested.absolutePath
    } else {
        null
    }
}

internal fun resolveArchiveMountedPathOrigin(path: File): ArchiveMountedPathOrigin? {
    val mountRoot = findArchiveMountRoot(path) ?: return null
    val readyMarker = File(mountRoot, ARCHIVE_READY_MARKER)
    if (!readyMarker.exists() || !readyMarker.isFile) return null
    val archivePath = parseArchivePathFromReadyMarker(readyMarker) ?: return null
    val parentPath = File(archivePath).parentFile?.absolutePath ?: return null
    return ArchiveMountedPathOrigin(
        mountRootPath = mountRoot.absolutePath,
        archivePath = archivePath,
        parentPath = parentPath
    )
}

internal fun resolveArchiveLogicalDirectory(
    context: Context,
    logicalPath: String?
): ResolvedArchiveDirectory? = resolveArchiveLogicalDirectory(context.cacheDir, logicalPath)

internal fun resolveArchiveLocationToFile(
    context: Context,
    archiveLocation: String
): File? = resolveArchiveLocationToFile(context.cacheDir, archiveLocation)

private fun resolveArchiveFileForLocation(context: Context, archiveLocation: String): File? =
    resolveArchiveFileForLocation(context.cacheDir, archiveLocation)

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

private fun findArchiveMountRoot(file: File): File? {
    var current: File? = runCatching { file.canonicalFile }.getOrNull()
    while (current != null) {
        val marker = File(current, ARCHIVE_READY_MARKER)
        if (marker.exists() && marker.isFile) {
            return current
        }
        current = current.parentFile
    }
    return null
}

private fun parseArchivePathFromReadyMarker(marker: File): String? {
    val stamp = runCatching { marker.readText() }.getOrNull()?.trim().orEmpty()
    if (stamp.isBlank()) return null
    val lastSep = stamp.lastIndexOf('|')
    if (lastSep <= 0) return null
    val secondLastSep = stamp.lastIndexOf('|', startIndex = lastSep - 1)
    if (secondLastSep <= 0) return null
    return stamp.substring(0, secondLastSep).trim().takeIf { it.isNotBlank() }
}

private fun ensureArchiveEntryExtracted(
    archiveFile: File,
    mountDir: File,
    entryPath: String,
    outputFile: File
) {
    // Placeholder files are zero-byte. Non-empty files are already extracted.
    if (outputFile.length() > 0L) return

    val normalizedEntryPath = entryPath.replace('\\', '/').trimStart('/')
    val mountCanonical = mountDir.canonicalPath
    val mountCanonicalPrefix = mountCanonical + File.separator
    val outputCanonical = outputFile.canonicalPath
    if (outputCanonical != mountCanonical && !outputCanonical.startsWith(mountCanonicalPrefix)) {
        error("Archive entry escapes mount root: $normalizedEntryPath")
    }

    ZipFile(archiveFile).use { zip ->
        val zipEntry = zip.getEntry(normalizedEntryPath)
            ?: error("Missing archive entry: $normalizedEntryPath")
        if (zipEntry.isDirectory) {
            error("Archive entry is a directory: $normalizedEntryPath")
        }
        val declaredSize = zipEntry.size
        if (declaredSize > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
            error("Archive entry too large: $normalizedEntryPath")
        }

        outputFile.parentFile?.mkdirs()
        val tempFile = File(outputFile.parentFile, "${outputFile.name}.extracting")
        zip.getInputStream(zipEntry).use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var entryBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    entryBytes += read
                    if (entryBytes > MAX_ARCHIVE_ENTRY_UNCOMPRESSED_BYTES) {
                        error("Archive entry exceeded size limit: $normalizedEntryPath")
                    }
                }
            }
        }
        if (outputFile.exists()) {
            outputFile.delete()
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.delete()
            error("Failed to finalize extracted entry: $normalizedEntryPath")
        }
    }
}
