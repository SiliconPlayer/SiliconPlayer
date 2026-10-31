package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.sha1Hex
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import java.io.ByteArrayOutputStream
import java.io.File

// Adjacent artwork pulled from a remote folder is kept beside the cached
// audio rather than inside it, keyed by the hash of the source identity so
// the same file answers for both downloaded and streamed SMB sources.
internal const val REMOTE_SOURCE_ARTWORK_CACHE_DIR = "remote_source_artwork"
internal const val MAX_CACHED_SOURCE_ARTWORK_BYTES = 8 * 1024 * 1024

internal val FOLDER_ARTWORK_FILE_NAMES = listOf(
    "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
    "folder.jpg", "folder.jpeg", "folder.png", "folder.webp",
    "album.jpg", "album.jpeg", "album.png", "album.webp",
    "front.jpg", "front.jpeg", "front.png", "front.webp",
    "artwork.jpg", "artwork.jpeg", "artwork.png", "artwork.webp"
)

internal fun remoteSourceArtworkCacheDir(sourceCacheRoot: File): File =
    File(sourceCacheRoot.parentFile ?: sourceCacheRoot, REMOTE_SOURCE_ARTWORK_CACHE_DIR)

internal fun remoteSourceArtworkCacheKey(sourceId: String?): String? {
    val normalized = sourceId?.let(::normalizeSourceIdentity)?.trim().takeUnless { it.isNullOrBlank() }
        ?: return null
    return "${sha1Hex(normalized)}.artwork"
}

internal fun cachedSourceArtworkFile(sourceCacheRoot: File, sourceId: String?): File? {
    val key = remoteSourceArtworkCacheKey(sourceId) ?: return null
    return File(remoteSourceArtworkCacheDir(sourceCacheRoot), key)
        .takeIf { it.isFile && it.length() > 0L }
}

internal fun writeCachedSourceArtwork(sourceCacheRoot: File, sourceId: String?, bytes: ByteArray?): File? {
    if (bytes == null || bytes.isEmpty() || bytes.size > MAX_CACHED_SOURCE_ARTWORK_BYTES) return null
    val key = remoteSourceArtworkCacheKey(sourceId) ?: return null
    val dir = remoteSourceArtworkCacheDir(sourceCacheRoot)
    if (!dir.exists() && !dir.mkdirs()) return null
    val target = File(dir, key)
    val temp = File(dir, "$key.part")
    return try {
        temp.writeBytes(bytes)
        if (temp.length() <= 0L) {
            temp.delete()
            return null
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        target
    } catch (_: Throwable) {
        temp.delete()
        null
    }
}

internal fun deleteCachedSourceArtwork(sourceCacheRoot: File, sourceId: String?): Long {
    val key = remoteSourceArtworkCacheKey(sourceId) ?: return 0L
    val dir = remoteSourceArtworkCacheDir(sourceCacheRoot)
    var freedBytes = 0L
    listOf(File(dir, key), File(dir, "$key.part")).forEach { candidate ->
        if (!candidate.exists()) return@forEach
        val size = candidate.length().coerceAtLeast(0L)
        if (candidate.delete()) freedBytes += size else candidate.deleteOnExit()
    }
    return freedBytes
}

internal fun readSmbFileBytes(
    share: DiskShare,
    remotePath: String,
    maxBytes: Int = MAX_CACHED_SOURCE_ARTWORK_BYTES
): ByteArray? {
    val normalizedPath = normalizeSmbPathForShare(remotePath).orEmpty()
    if (normalizedPath.isBlank()) return null
    return try {
        val smbFile = share.openFile(
            normalizedPath,
            setOf(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        try {
            val input = smbFile.inputStream ?: return null
            try {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } finally {
                runCatching { input.close() }
            }
        } finally {
            runCatching { smbFile.close() }
        }
    } catch (_: Throwable) {
        null
    }
}

internal suspend fun readSmbFolderArtworkBytes(spec: SmbSourceSpec): ByteArray? {
    val remotePath = normalizeSmbPathForShare(spec.path).orEmpty()
    if (spec.share.isBlank() || remotePath.isBlank()) return null
    val parentPath = remotePath.substringBeforeLast('/', missingDelimiterValue = "").trim()
    val credentialedSpec = NetworkCredentialStore.applyTo(spec)
    return try {
        withAppSmbSession(credentialedSpec) { session ->
            val share = session.connectShare(credentialedSpec.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                return@withAppSmbSession null
            }
            try {
                val listPath = normalizeSmbPathForShare(parentPath).orEmpty()
                val rawEntries = runCatching {
                    share.list(listPath, FileDirectoryInformation::class.java)
                }.getOrNull() ?: return@withAppSmbSession null
                val fileNames = rawEntries.mapNotNull { entry ->
                    val name = entry.fileName?.trim().orEmpty()
                    if (name.isBlank() || name == "." || name == "..") null else name
                }
                val matchedName = FOLDER_ARTWORK_FILE_NAMES.firstNotNullOfOrNull { candidate ->
                    fileNames.firstOrNull { it.equals(candidate, ignoreCase = true) }
                } ?: return@withAppSmbSession null
                readSmbFileBytes(share, joinSmbRelativePath(parentPath, matchedName))
            } finally {
                runCatching { share.close() }
            }
        }
    } catch (_: Throwable) {
        null
    }
}
