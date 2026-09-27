package com.flopster101.siliconplayer

import android.content.Context
import android.util.Log
import com.flopster101.siliconplayer.platform.AndroidPreferencesProvider

private const val ARCHIVE_CACHE_TAG = "ArchiveCachePolicy"

internal fun applyArchiveMountCachePolicyOnLaunch(context: Context, cacheDir: java.io.File) {
    val prefs = AndroidPreferencesProvider(context).getPreferences(AppPreferenceKeys.PREFS_NAME)
    val result = applyArchiveMountCachePolicy(prefs = prefs, cacheDir = cacheDir)
    if (result.clearedOnLaunch) {
        if (result.deletedMounts > 0) {
            Log.d(
                ARCHIVE_CACHE_TAG,
                "Cleared archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes}"
            )
        }
        return
    }
    if (result.deletedMounts > 0) {
        Log.d(
            ARCHIVE_CACHE_TAG,
            "Pruned archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes} limits=(mounts=${result.maxMounts} bytes=${result.maxBytes} ageDays=${result.maxAgeDays})"
        )
    }
}
