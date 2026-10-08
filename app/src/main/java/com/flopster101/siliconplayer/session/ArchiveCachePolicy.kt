package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.platform.AndroidPreferencesProvider


internal fun applyArchiveMountCachePolicyOnLaunch(context: Context, cacheDir: java.io.File) {
    val prefs = AndroidPreferencesProvider(context).getPreferences(AppPreferenceKeys.PREFS_NAME)
    val result = applyArchiveMountCachePolicy(prefs = prefs, cacheDir = cacheDir)
    if (result.clearedOnLaunch) {
        if (result.deletedMounts > 0) {
            SpLog.d("ArchiveCachePolicy",
                "Cleared archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes}"
            )
        }
        return
    }
    if (result.deletedMounts > 0) {
        SpLog.d("ArchiveCachePolicy",
            "Pruned archive mounts on launch: deleted=${result.deletedMounts} freed=${result.freedBytes} limits=(mounts=${result.maxMounts} bytes=${result.maxBytes} ageDays=${result.maxAgeDays})"
        )
    }
}
