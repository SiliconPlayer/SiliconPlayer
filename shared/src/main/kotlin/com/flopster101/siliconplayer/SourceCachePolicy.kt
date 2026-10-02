package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_BYTES_DEFAULT
import com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT
import com.flopster101.siliconplayer.data.ArchiveMountCachePruneResult
import com.flopster101.siliconplayer.data.clearArchiveMountCache
import com.flopster101.siliconplayer.data.enforceArchiveMountCacheLimits
import com.flopster101.siliconplayer.platform.AppPreferences
import java.io.File

internal data class RemoteCacheLaunchPolicyResult(
    val clearedOnLaunch: Boolean,
    val deletedFiles: Int,
    val skippedFiles: Int,
    val freedBytes: Long,
    val maxTracks: Int,
    val maxBytes: Long
)

internal data class ArchiveCacheLaunchPolicyResult(
    val clearedOnLaunch: Boolean,
    val deletedMounts: Int,
    val freedBytes: Long,
    val maxMounts: Int,
    val maxBytes: Long,
    val maxAgeDays: Int
)

internal fun applyFileSourceCachePolicy(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheLaunchPolicyResult {
    val clearOnLaunch = prefs.getBoolean(
        AppPreferenceKeys.FILE_CACHE_CLEAR_ON_LAUNCH,
        prefs.getBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false)
    )
    val maxTracks = prefs.getInt(
        AppPreferenceKeys.FILE_CACHE_MAX_TRACKS,
        prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT)
    )
    val maxBytes = prefs.getLong(
        AppPreferenceKeys.FILE_CACHE_MAX_BYTES,
        prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT)
    )
    if (!cacheRoot.exists()) {
        return RemoteCacheLaunchPolicyResult(clearOnLaunch, 0, 0, 0L, maxTracks, maxBytes)
    }
    if (clearOnLaunch) {
        val result = clearRemoteCacheFiles(cacheRoot, protectedPaths)
        return RemoteCacheLaunchPolicyResult(
            clearedOnLaunch = true,
            deletedFiles = result.deletedFiles,
            skippedFiles = result.skippedFiles,
            freedBytes = result.freedBytes,
            maxTracks = maxTracks,
            maxBytes = maxBytes
        )
    }
    val result = enforceRemoteCacheLimits(cacheRoot, maxTracks, maxBytes, protectedPaths)
    return RemoteCacheLaunchPolicyResult(
        clearedOnLaunch = false,
        deletedFiles = result.deletedFiles,
        skippedFiles = 0,
        freedBytes = result.freedBytes,
        maxTracks = maxTracks,
        maxBytes = maxBytes
    )
}

internal fun applyStreamingSourceCachePolicy(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheLaunchPolicyResult {
    val clearOnLaunch = prefs.getBoolean(AppPreferenceKeys.STREAMING_CACHE_CLEAR_ON_LAUNCH, false)
    val maxTracks = prefs.getInt(AppPreferenceKeys.STREAMING_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT)
    val maxBytes = prefs.getLong(AppPreferenceKeys.STREAMING_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT)
    if (!cacheRoot.exists()) {
        return RemoteCacheLaunchPolicyResult(clearOnLaunch, 0, 0, 0L, maxTracks, maxBytes)
    }
    if (clearOnLaunch) {
        val result = clearRemoteCacheFiles(cacheRoot, protectedPaths)
        return RemoteCacheLaunchPolicyResult(
            clearedOnLaunch = true,
            deletedFiles = result.deletedFiles,
            skippedFiles = result.skippedFiles,
            freedBytes = result.freedBytes,
            maxTracks = maxTracks,
            maxBytes = maxBytes
        )
    }
    val result = enforceRemoteCacheLimits(cacheRoot, maxTracks, maxBytes, protectedPaths)
    return RemoteCacheLaunchPolicyResult(
        clearedOnLaunch = false,
        deletedFiles = result.deletedFiles,
        skippedFiles = 0,
        freedBytes = result.freedBytes,
        maxTracks = maxTracks,
        maxBytes = maxBytes
    )
}

internal fun applyRemoteSourceCachePolicy(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCacheLaunchPolicyResult = applyFileSourceCachePolicy(prefs, cacheRoot, protectedPaths)

internal fun applyArchiveMountCachePolicy(
    prefs: AppPreferences,
    cacheDir: File
): ArchiveCacheLaunchPolicyResult {
    val clearOnLaunch = prefs.getBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, false)
    val maxMounts = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT)
    val maxBytes = prefs.getLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, ARCHIVE_CACHE_MAX_BYTES_DEFAULT)
    val maxAgeDays = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT)
    if (clearOnLaunch) {
        val result = clearArchiveMountCache(cacheDir)
        return ArchiveCacheLaunchPolicyResult(
            clearedOnLaunch = true,
            deletedMounts = result.deletedMounts,
            freedBytes = result.freedBytes,
            maxMounts = maxMounts,
            maxBytes = maxBytes,
            maxAgeDays = maxAgeDays
        )
    }
    val result = enforceArchiveMountCacheLimits(cacheDir, maxMounts, maxBytes, maxAgeDays)
    return ArchiveCacheLaunchPolicyResult(
        clearedOnLaunch = false,
        deletedMounts = result.deletedMounts,
        freedBytes = result.freedBytes,
        maxMounts = maxMounts,
        maxBytes = maxBytes,
        maxAgeDays = maxAgeDays
    )
}

internal fun enforceFileCacheLimitsFromPrefs(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCachePruneResult {
    return enforceRemoteCacheLimits(
        cacheRoot = cacheRoot,
        maxTracks = prefs.getInt(
            AppPreferenceKeys.FILE_CACHE_MAX_TRACKS,
            prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT)
        ),
        maxBytes = prefs.getLong(
            AppPreferenceKeys.FILE_CACHE_MAX_BYTES,
            prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT)
        ),
        protectedPaths = protectedPaths
    )
}

internal fun enforceStreamingCacheLimitsFromPrefs(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCachePruneResult {
    return enforceRemoteCacheLimits(
        cacheRoot = cacheRoot,
        maxTracks = prefs.getInt(AppPreferenceKeys.STREAMING_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT),
        maxBytes = prefs.getLong(AppPreferenceKeys.STREAMING_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT),
        protectedPaths = protectedPaths
    )
}

internal fun enforceRemoteCacheLimitsFromPrefs(
    prefs: AppPreferences,
    cacheRoot: File,
    protectedPaths: Set<String> = emptySet()
): RemoteCachePruneResult = enforceFileCacheLimitsFromPrefs(prefs, cacheRoot, protectedPaths)

internal fun enforceArchiveMountCacheLimitsFromPrefs(
    prefs: AppPreferences,
    cacheDir: File
): ArchiveMountCachePruneResult {
    return enforceArchiveMountCacheLimits(
        cacheDir = cacheDir,
        maxMounts = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT),
        maxBytes = prefs.getLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, ARCHIVE_CACHE_MAX_BYTES_DEFAULT),
        maxAgeDays = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT)
    )
}
