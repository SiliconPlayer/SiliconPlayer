package com.flopster101.siliconplayer

import org.json.JSONObject
import java.io.File

internal const val SOURCE_CACHE_MAX_TRACKS_DEFAULT = 100
internal const val SOURCE_CACHE_MAX_BYTES_DEFAULT = 1024L * 1024L * 1024L
private const val SOURCE_CACHE_INDEX_FILE = ".source_index.json"

internal data class RemoteCachePruneResult(
    val deletedFiles: Int,
    val freedBytes: Long
)

internal data class RemoteCacheClearResult(
    val deletedFiles: Int,
    val skippedFiles: Int,
    val freedBytes: Long
)

internal data class RemoteCacheDeleteResult(
    val deletedFiles: Int,
    val skippedFiles: Int,
    val missingFiles: Int,
    val freedBytes: Long
)

internal fun isCachedRemoteSourceFile(file: File?): Boolean {
    val candidate = file ?: return false
    return candidate.isFile && candidate.parentFile?.name == REMOTE_SOURCE_CACHE_DIR
}

private fun cacheIndexFile(cacheRoot: File): File = File(cacheRoot, SOURCE_CACHE_INDEX_FILE)

private fun loadSourceCacheIndex(cacheRoot: File): MutableMap<String, String> {
    val indexFile = cacheIndexFile(cacheRoot)
    if (!indexFile.exists() || !indexFile.isFile) return mutableMapOf()
    return try {
        val root = JSONObject(indexFile.readText())
        val out = mutableMapOf<String, String>()
        root.keys().forEach { key ->
            val value = root.optString(key, "").trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                out[key] = value
            }
        }
        out
    } catch (_: Throwable) {
        mutableMapOf()
    }
}

private fun saveSourceCacheIndex(cacheRoot: File, index: Map<String, String>) {
    try {
        if (!cacheRoot.exists()) cacheRoot.mkdirs()
        val root = JSONObject()
        index.entries
            .sortedBy { it.key }
            .forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) {
                    root.put(key, value)
                }
            }
        cacheIndexFile(cacheRoot).writeText(root.toString())
    } catch (_: Throwable) {}
}

internal fun rememberSourceForCachedFile(cacheRoot: File, fileName: String, sourceId: String) {
    if (fileName.isBlank() || sourceId.isBlank()) return
    val index = loadSourceCacheIndex(cacheRoot)
    index[fileName] = sourceId
    saveSourceCacheIndex(cacheRoot, index)
}

private fun removeSourceMappingsForFiles(cacheRoot: File, fileNames: Set<String>) {
    if (fileNames.isEmpty()) return
    val normalized = fileNames.filter { it.isNotBlank() }.toSet()
    if (normalized.isEmpty()) return
    val index = loadSourceCacheIndex(cacheRoot)
    val changed = index.keys.removeAll(normalized)
    if (changed) saveSourceCacheIndex(cacheRoot, index)
}

private fun isCacheDataFileName(name: String): Boolean {
    if (name == SOURCE_CACHE_INDEX_FILE) return false
    if (name.endsWith(".part", ignoreCase = true)) return false
    if (name.endsWith(".chunks", ignoreCase = true)) return false
    if (name.endsWith(".meta", ignoreCase = true)) return false
    return true
}

private fun isCacheDataFile(file: File): Boolean = file.isFile && isCacheDataFileName(file.name)

private fun deleteCompanionFiles(file: File): Long {
    var bytes = 0L
    val chunks = File(file.absolutePath + ".chunks")
    if (chunks.exists()) {
        bytes += chunks.length().coerceAtLeast(0L)
        if (!chunks.delete()) chunks.deleteOnExit()
    }
    val meta = File(file.absolutePath + ".meta")
    if (meta.exists()) {
        bytes += meta.length().coerceAtLeast(0L)
        if (!meta.delete()) meta.deleteOnExit()
    }
    return bytes
}

private fun pruneStaleSourceMappings(cacheRoot: File) {
    val index = loadSourceCacheIndex(cacheRoot)
    if (index.isEmpty()) return
    val existingNames = cacheRoot.listFiles().orEmpty()
        .filter { isCacheDataFile(it) }
        .map { it.name }
        .toSet()
    val changed = index.keys.removeAll { it !in existingNames }
    if (changed) saveSourceCacheIndex(cacheRoot, index)
}

internal fun listCachedSourceFiles(cacheRoot: File): List<CachedSourceFile> {
    if (!cacheRoot.exists()) return emptyList()
    pruneStaleSourceMappings(cacheRoot)
    val index = loadSourceCacheIndex(cacheRoot)
    return cacheRoot.listFiles().orEmpty()
        .filter { isCacheDataFile(it) }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            CachedSourceFile(
                absolutePath = file.absolutePath,
                fileName = file.name,
                sizeBytes = file.length().coerceAtLeast(0L),
                lastModified = file.lastModified(),
                sourceId = index[file.name] ?: file.name.substringAfter('_', file.name)
            )
        }
}

internal fun sourceIdForCachedFileName(cacheRoot: File, fileName: String): String? {
    if (fileName.isBlank()) return null
    return loadSourceCacheIndex(cacheRoot)[fileName]
}

internal fun enforceRemoteCacheLimits(
    cacheRoot: File,
    maxTracks: Int,
    maxBytes: Long,
    protectedPaths: Set<String> = emptySet()
): RemoteCachePruneResult {
    if (!cacheRoot.exists()) return RemoteCachePruneResult(0, 0L)

    val normalizedMaxTracks = maxTracks.coerceAtLeast(1)
    val normalizedMaxBytes = maxBytes.coerceAtLeast(1L)
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()

    val entries = cacheRoot.listFiles().orEmpty()
        .filter { isCacheDataFile(it) }
        .toMutableList()
    if (entries.isEmpty()) return RemoteCachePruneResult(0, 0L)

    var totalBytes = entries.sumOf { it.length().coerceAtLeast(0L) }
    var totalCount = entries.size
    var deletedFiles = 0
    var freedBytes = 0L

    entries.sortBy { it.lastModified() }
    val deletedNames = mutableSetOf<String>()
    for (file in entries) {
        if (totalCount <= normalizedMaxTracks && totalBytes <= normalizedMaxBytes) break
        if (protected.contains(file.absolutePath)) continue
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            val companionFreed = deleteCompanionFiles(file)
            deletedFiles++
            freedBytes += size + companionFreed
            totalCount--
            totalBytes = (totalBytes - size).coerceAtLeast(0L)
            deletedNames.add(file.name)
        } else {
            file.deleteOnExit()
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)

    return RemoteCachePruneResult(deletedFiles, freedBytes)
}

internal fun clearRemoteCacheFiles(
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheClearResult {
    if (!cacheRoot.exists()) return RemoteCacheClearResult(0, 0, 0L)
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()
    var deletedFiles = 0
    var skippedFiles = 0
    var freedBytes = 0L
    val deletedNames = mutableSetOf<String>()
    cacheRoot.listFiles().orEmpty().forEach { file ->
        if (!file.isFile) {
            file.deleteRecursively()
            return@forEach
        }
        if (file.name == SOURCE_CACHE_INDEX_FILE) return@forEach
        if (protected.contains(file.absolutePath) || protected.any { file.absolutePath.startsWith(it) }) {
            skippedFiles++
            return@forEach
        }
        val isDataFile = isCacheDataFileName(file.name)
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            if (isDataFile) {
                deletedFiles++
                deletedNames.add(file.name)
            }
            freedBytes += size
        } else {
            file.deleteOnExit()
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)
    return RemoteCacheClearResult(deletedFiles, skippedFiles, freedBytes)
}

internal fun deleteSpecificRemoteCacheFiles(
    cacheRoot: File,
    absolutePaths: Set<String>,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheDeleteResult {
    if (!cacheRoot.exists() || absolutePaths.isEmpty()) {
        return RemoteCacheDeleteResult(0, 0, absolutePaths.size, 0L)
    }
    val protected = protectedPaths.filter { it.isNotBlank() }.toSet()
    var deletedFiles = 0
    var skippedFiles = 0
    var missingFiles = 0
    var freedBytes = 0L
    val deletedNames = mutableSetOf<String>()
    absolutePaths.forEach { absolutePath ->
        val file = File(absolutePath)
        if (!file.exists() || !file.isFile || file.parentFile?.absolutePath != cacheRoot.absolutePath) {
            missingFiles++
            return@forEach
        }
        if (file.name == SOURCE_CACHE_INDEX_FILE) {
            missingFiles++
            return@forEach
        }
        if (protected.contains(file.absolutePath)) {
            skippedFiles++
            return@forEach
        }
        val size = file.length().coerceAtLeast(0L)
        if (file.delete()) {
            val companionFreed = deleteCompanionFiles(file)
            deletedFiles++
            freedBytes += size + companionFreed
            deletedNames.add(file.name)
        } else {
            file.deleteOnExit()
            skippedFiles++
        }
    }
    removeSourceMappingsForFiles(cacheRoot, deletedNames)
    return RemoteCacheDeleteResult(deletedFiles, skippedFiles, missingFiles, freedBytes)
}
