package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.sha1Hex
import java.io.File

// Recent-track artwork disk cache shared by Android's RecentArtworkCacheSupport
// and the desktop artwork pipeline: thumbnails are filed under the SHA-1 of
// the normalized source identity, so a recents entry keeps a stable
// artworkThumbnailCacheKey that still resolves after an app restart.
internal const val RECENT_ARTWORK_CACHE_DIR = "recent_artwork"
internal const val RECENT_ARTWORK_THUMB_MAX_SIZE_PX = 240
internal const val RECENT_ARTWORK_LARGE_MAX_SIZE_PX = 1024

internal fun recentArtworkCacheKeyForSource(sourceId: String?): String? {
    val normalized = sourceId?.let(::normalizeSourceIdentity)?.trim().takeUnless { it.isNullOrBlank() }
        ?: return null
    return "${sha1Hex(normalized)}.jpg"
}

internal fun recentLargeArtworkCacheKeyForSource(sourceId: String?): String? {
    val normalized = sourceId?.let(::normalizeSourceIdentity)?.trim().takeUnless { it.isNullOrBlank() }
        ?: return null
    return "${sha1Hex(normalized)}_large.jpg"
}

internal fun recentArtworkCacheFile(cacheRoot: File, cacheKey: String?): File? {
    val key = cacheKey?.trim().takeUnless { it.isNullOrBlank() } ?: return null
    return File(cacheRoot, key).takeIf { it.isFile && it.length() > 0L }
}

internal fun mergeRecentPlayedTrackArtworkCacheKey(
    current: List<RecentPathEntry>,
    path: String,
    artworkThumbnailCacheKey: String?
): List<RecentPathEntry> {
    val normalized = normalizeSourceIdentity(path) ?: path
    val normalizedCacheKey = artworkThumbnailCacheKey?.trim().takeUnless { it.isNullOrBlank() }
        ?: return current
    var changed = false
    val updated = current.map { entry ->
        if (!samePath(entry.path, normalized)) return@map entry
        if (entry.artworkThumbnailCacheKey == normalizedCacheKey) {
            entry
        } else {
            changed = true
            entry.copy(artworkThumbnailCacheKey = normalizedCacheKey)
        }
    }
    return if (changed) updated else current
}
