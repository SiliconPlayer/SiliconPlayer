package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.platform.AndroidPreferencesProvider
import java.io.File

internal fun applyRemoteSourceCachePolicyOnLaunch(context: Context, cacheDir: File) {
    val prefs = AndroidPreferencesProvider(context).getPreferences(AppPreferenceKeys.PREFS_NAME)
    val cacheRoot = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val protectedPaths = buildSet {
        prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
            ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
            ?.let { add(it) }
    }
    val result = applyRemoteSourceCachePolicy(
        prefs = prefs,
        cacheRoot = cacheRoot,
        protectedPaths = protectedPaths
    )
    if (result.clearedOnLaunch) {
        SpLog.d("UrlSource",
            "Cleared remote cache on launch: deleted=${result.deletedFiles} skipped=${result.skippedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath}"
        )
    } else if (result.deletedFiles > 0) {
        SpLog.d("UrlSource",
            "Pruned remote cache on launch: deleted=${result.deletedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath} limits=(tracks=${result.maxTracks} bytes=${result.maxBytes})"
        )
    }
}

internal fun applyStreamingSourceCachePolicyOnLaunch(context: Context, cacheDir: File) {
    val prefs = AndroidPreferencesProvider(context).getPreferences(AppPreferenceKeys.PREFS_NAME)
    val cacheRoot = File(cacheDir, PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR)
    val protectedPaths = buildSet {
        prefs.getString(AppPreferenceKeys.SESSION_CURRENT_PATH, null)
            ?.takeIf { it.startsWith(cacheRoot.absolutePath) }
            ?.let { add(it) }
    }
    val result = applyStreamingSourceCachePolicy(
        prefs = prefs,
        cacheRoot = cacheRoot,
        protectedPaths = protectedPaths
    )
    if (result.clearedOnLaunch) {
        SpLog.d("UrlSource",
            "Cleared streaming cache on launch: deleted=${result.deletedFiles} skipped=${result.skippedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath}"
        )
    } else if (result.deletedFiles > 0) {
        SpLog.d("UrlSource",
            "Pruned streaming cache on launch: deleted=${result.deletedFiles} freed=${result.freedBytes} path=${cacheRoot.absolutePath} limits=(tracks=${result.maxTracks} bytes=${result.maxBytes})"
        )
    }
}
