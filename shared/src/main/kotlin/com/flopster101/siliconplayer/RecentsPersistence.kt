package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import org.json.JSONArray
import org.json.JSONObject

internal fun readRecentEntries(
    prefs: AppPreferences,
    key: String,
    maxItems: Int
): List<RecentPathEntry> {
    val raw = prefs.getString(key, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        val deduped = mutableListOf<RecentPathEntry>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val path = objectValue.optString("path", "").trim()
            if (path.isBlank()) continue
            val locationId = objectValue.optString("locationId", "").ifBlank { null }
            val title = objectValue.optString("title", "").ifBlank { null }
            val artist = objectValue.optString("artist", "").ifBlank { null }
            val decoderName = objectValue.optString("decoderName", "").ifBlank { null }
            val artworkThumbnailCacheKey = objectValue
                .optString("artworkThumbnailCacheKey", "")
                .ifBlank { null }
            val isPlaylist = objectValue.optBoolean("isPlaylist", false)
            val playlistSourceHint = objectValue
                .optString("playlistSourceHint", "")
                .ifBlank { null }
            val sourceNodeId = if (
                objectValue.has("sourceNodeId") &&
                !objectValue.isNull("sourceNodeId")
            ) {
                objectValue.optLong("sourceNodeId").takeIf { it > 0L }
            } else {
                null
            }
            val existingIndex = deduped.indexOfFirst { samePath(it.path, path) }
            if (existingIndex >= 0) {
                val existing = deduped[existingIndex]
                deduped[existingIndex] = existing.copy(
                    locationId = existing.locationId ?: locationId,
                    title = existing.title ?: title,
                    artist = existing.artist ?: artist,
                    decoderName = existing.decoderName ?: decoderName,
                    sourceNodeId = existing.sourceNodeId ?: sourceNodeId,
                    artworkThumbnailCacheKey = existing.artworkThumbnailCacheKey ?: artworkThumbnailCacheKey,
                    isPlaylist = existing.isPlaylist || isPlaylist,
                    playlistSourceHint = existing.playlistSourceHint ?: playlistSourceHint
                )
                continue
            }
            deduped += RecentPathEntry(
                path = path,
                locationId = locationId,
                title = title,
                artist = artist,
                decoderName = decoderName,
                sourceNodeId = sourceNodeId,
                artworkThumbnailCacheKey = artworkThumbnailCacheKey,
                isPlaylist = isPlaylist,
                playlistSourceHint = playlistSourceHint
            )
            if (deduped.size >= maxItems) break
        }
        deduped
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun readPinnedHomeEntries(
    prefs: AppPreferences,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
): List<HomePinnedEntry> {
    val raw = prefs.getString(key, null) ?: return emptyList()
    return try {
        val array = JSONArray(raw)
        val deduped = mutableListOf<HomePinnedEntry>()
        for (index in 0 until array.length()) {
            val objectValue = array.optJSONObject(index) ?: continue
            val path = objectValue.optString("path", "").trim()
            if (path.isBlank()) continue
            val isFolder = objectValue.optBoolean("isFolder", false)
            val locationId = objectValue.optString("locationId", "").ifBlank { null }
            val title = objectValue.optString("title", "").ifBlank { null }
            val artist = objectValue.optString("artist", "").ifBlank { null }
            val decoderName = objectValue.optString("decoderName", "").ifBlank { null }
            val artworkThumbnailCacheKey = objectValue
                .optString("artworkThumbnailCacheKey", "")
                .ifBlank { null }
            val sourceNodeId = if (
                objectValue.has("sourceNodeId") &&
                !objectValue.isNull("sourceNodeId")
            ) {
                objectValue.optLong("sourceNodeId").takeIf { it > 0L }
            } else {
                null
            }
            val pinnedAtEpochMs = objectValue
                .optLong("pinnedAtEpochMs", 0L)
                .takeIf { it > 0L }
                ?: (System.currentTimeMillis() - index)
            val existingIndex = deduped.indexOfFirst { samePath(it.path, path) }
            if (existingIndex >= 0) {
                val existing = deduped[existingIndex]
                deduped[existingIndex] = existing.copy(
                    isFolder = existing.isFolder || isFolder,
                    locationId = existing.locationId ?: locationId,
                    title = existing.title ?: title,
                    artist = existing.artist ?: artist,
                    decoderName = existing.decoderName ?: decoderName,
                    sourceNodeId = existing.sourceNodeId ?: sourceNodeId,
                    artworkThumbnailCacheKey = existing.artworkThumbnailCacheKey ?: artworkThumbnailCacheKey,
                    pinnedAtEpochMs = maxOf(existing.pinnedAtEpochMs, pinnedAtEpochMs)
                )
                continue
            }
            deduped += HomePinnedEntry(
                path = path,
                isFolder = isFolder,
                locationId = locationId,
                title = title,
                artist = artist,
                decoderName = decoderName,
                sourceNodeId = sourceNodeId,
                artworkThumbnailCacheKey = artworkThumbnailCacheKey,
                pinnedAtEpochMs = pinnedAtEpochMs
            )
            if (deduped.size >= maxItems) break
        }
        deduped
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun writePinnedHomeEntries(
    prefs: AppPreferences,
    entries: List<HomePinnedEntry>,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
) {
    val deduped = mutableListOf<HomePinnedEntry>()
    entries.forEach { entry ->
        val existingIndex = deduped.indexOfFirst { samePath(it.path, entry.path) }
        if (existingIndex >= 0) {
            val existing = deduped[existingIndex]
            deduped[existingIndex] = existing.copy(
                isFolder = existing.isFolder || entry.isFolder,
                locationId = existing.locationId ?: entry.locationId,
                title = existing.title ?: entry.title,
                artist = existing.artist ?: entry.artist,
                decoderName = existing.decoderName ?: entry.decoderName,
                sourceNodeId = existing.sourceNodeId ?: entry.sourceNodeId,
                artworkThumbnailCacheKey = existing.artworkThumbnailCacheKey ?: entry.artworkThumbnailCacheKey,
                pinnedAtEpochMs = maxOf(existing.pinnedAtEpochMs, entry.pinnedAtEpochMs)
            )
        } else {
            deduped += entry
        }
    }
    val trimmed = deduped.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("isFolder", entry.isFolder)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
                .put("decoderName", entry.decoderName ?: "")
                .put("sourceNodeId", entry.sourceNodeId)
                .put("artworkThumbnailCacheKey", entry.artworkThumbnailCacheKey ?: "")
                .put("pinnedAtEpochMs", entry.pinnedAtEpochMs)
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}

internal fun writeRecentEntries(
    prefs: AppPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    val deduped = mutableListOf<RecentPathEntry>()
    entries.forEach { entry ->
        val existingIndex = deduped.indexOfFirst { samePath(it.path, entry.path) }
        if (existingIndex >= 0) {
            val existing = deduped[existingIndex]
            deduped[existingIndex] = existing.copy(
                locationId = existing.locationId ?: entry.locationId,
                title = existing.title ?: entry.title,
                artist = existing.artist ?: entry.artist,
                decoderName = existing.decoderName ?: entry.decoderName,
                sourceNodeId = existing.sourceNodeId ?: entry.sourceNodeId,
                artworkThumbnailCacheKey = existing.artworkThumbnailCacheKey ?: entry.artworkThumbnailCacheKey,
                isPlaylist = existing.isPlaylist || entry.isPlaylist,
                playlistSourceHint = existing.playlistSourceHint ?: entry.playlistSourceHint
            )
        } else {
            deduped += entry
        }
    }
    val trimmed = deduped.take(maxItems)
    val array = JSONArray()
    trimmed.forEach { entry ->
        array.put(
            JSONObject()
                .put("path", entry.path)
                .put("locationId", entry.locationId ?: "")
                .put("title", entry.title ?: "")
                .put("artist", entry.artist ?: "")
                .put("decoderName", entry.decoderName ?: "")
                .put("sourceNodeId", entry.sourceNodeId)
                .put("artworkThumbnailCacheKey", entry.artworkThumbnailCacheKey ?: "")
                .put("isPlaylist", entry.isPlaylist)
                .put("playlistSourceHint", entry.playlistSourceHint ?: "")
        )
    }
    prefs.edit().putString(key, array.toString()).apply()
}
