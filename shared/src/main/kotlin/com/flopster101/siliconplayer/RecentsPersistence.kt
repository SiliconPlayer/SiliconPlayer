package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.platform.AppPreferences
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal fun readRecentEntries(
    prefs: AppPreferences,
    key: String,
    maxItems: Int
): List<RecentPathEntry> {
    return decodeRecentEntries(prefs.getString(key, null), maxItems)
}

internal fun readRecentEntries(
    configDir: File,
    key: String,
    maxItems: Int,
    legacyPrefs: AppPreferences? = null
): List<RecentPathEntry> {
    val file = domainFileForKey(configDir, key)
    firstParsableJson(readCandidateTexts(file), isObject = false)?.let { raw ->
        clearLegacyDomainKey(legacyPrefs, key)
        return decodeRecentEntries(raw, maxItems)
    }
    if (legacyPrefs != null && legacyPrefs.contains(key)) {
        val migrated = decodeRecentEntries(legacyPrefs.getString(key, null), maxItems)
        writeTextAtomic(file, encodeRecentEntries(migrated, maxItems))
        clearLegacyDomainKey(legacyPrefs, key)
        return migrated
    }
    return emptyList()
}

internal fun decodeRecentEntries(
    raw: String?,
    maxItems: Int
): List<RecentPathEntry> {
    val normalized = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return emptyList()
    return try {
        val array = JSONArray(normalized)
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
    return decodePinnedHomeEntries(prefs.getString(key, null), maxItems)
}

internal fun readPinnedHomeEntries(
    configDir: File,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT,
    legacyPrefs: AppPreferences? = null
): List<HomePinnedEntry> {
    val file = domainFileForKey(configDir, key)
    firstParsableJson(readCandidateTexts(file), isObject = false)?.let { raw ->
        clearLegacyDomainKey(legacyPrefs, key)
        return decodePinnedHomeEntries(raw, maxItems)
    }
    if (legacyPrefs != null && legacyPrefs.contains(key)) {
        val migrated = decodePinnedHomeEntries(legacyPrefs.getString(key, null), maxItems)
        writeTextAtomic(file, encodePinnedHomeEntries(migrated, maxItems))
        clearLegacyDomainKey(legacyPrefs, key)
        return migrated
    }
    return emptyList()
}

internal fun decodePinnedHomeEntries(
    raw: String?,
    maxItems: Int
): List<HomePinnedEntry> {
    val normalized = raw?.trim().takeUnless { it.isNullOrBlank() } ?: return emptyList()
    return try {
        val array = JSONArray(normalized)
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

internal fun encodePinnedHomeEntries(
    entries: List<HomePinnedEntry>,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
): String {
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
    return array.toString()
}

internal fun writePinnedHomeEntries(
    prefs: AppPreferences,
    entries: List<HomePinnedEntry>,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
) {
    prefs.edit().putString(key, encodePinnedHomeEntries(entries, maxItems)).apply()
}

internal fun writePinnedHomeEntries(
    configDir: File,
    entries: List<HomePinnedEntry>,
    key: String = AppPreferenceKeys.PINNED_HOME_ENTRIES,
    maxItems: Int = PINNED_HOME_ENTRIES_LIMIT
) {
    writeTextAtomic(domainFileForKey(configDir, key), encodePinnedHomeEntries(entries, maxItems))
}

internal fun encodeRecentEntries(
    entries: List<RecentPathEntry>,
    maxItems: Int
): String {
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
    return array.toString()
}

internal fun writeRecentEntries(
    prefs: AppPreferences,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    prefs.edit().putString(key, encodeRecentEntries(entries, maxItems)).apply()
}

internal fun writeRecentEntries(
    configDir: File,
    key: String,
    entries: List<RecentPathEntry>,
    maxItems: Int
) {
    writeTextAtomic(domainFileForKey(configDir, key), encodeRecentEntries(entries, maxItems))
}

internal fun clearLegacyDomainKey(prefs: AppPreferences?, key: String) {
    if (prefs != null && prefs.contains(key)) {
        runCatching { prefs.edit().remove(key).apply() }
    }
}
