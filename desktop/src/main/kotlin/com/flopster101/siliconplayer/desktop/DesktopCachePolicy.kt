package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.applyArchiveMountCachePolicy
import com.flopster101.siliconplayer.applyFileSourceCachePolicy
import com.flopster101.siliconplayer.applyStreamingSourceCachePolicy
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.readSessionResumeSnapshot
import java.io.File

// Startup cache policy mirroring MainActivity: clear/prune file, streaming, and archive caches,
// protecting the cached file the session is about to reopen.
internal fun applyDesktopCachePoliciesOnLaunch(
    prefs: AppPreferences,
    cacheDir: File,
    configDir: File
) {
    val fileCacheRoot = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val fileResult = applyFileSourceCachePolicy(
        prefs = prefs,
        cacheRoot = fileCacheRoot,
        protectedPaths = desktopResumedCachePaths(fileCacheRoot, configDir)
    )
    if (fileResult.clearedOnLaunch) {
        println(
            "[SiliconPlayer] cleared file cache on launch: deleted=${fileResult.deletedFiles} " +
                "skipped=${fileResult.skippedFiles} freed=${fileResult.freedBytes}"
        )
    } else if (fileResult.deletedFiles > 0) {
        println(
            "[SiliconPlayer] pruned file cache on launch: deleted=${fileResult.deletedFiles} " +
                "freed=${fileResult.freedBytes} limits=(tracks=${fileResult.maxTracks} bytes=${fileResult.maxBytes})"
        )
    }

    val streamingCacheRoot = File(cacheDir, PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR)
    val streamingResult = applyStreamingSourceCachePolicy(
        prefs = prefs,
        cacheRoot = streamingCacheRoot,
        protectedPaths = desktopResumedCachePaths(streamingCacheRoot, configDir)
    )
    if (streamingResult.clearedOnLaunch) {
        println(
            "[SiliconPlayer] cleared streaming cache on launch: deleted=${streamingResult.deletedFiles} " +
                "skipped=${streamingResult.skippedFiles} freed=${streamingResult.freedBytes}"
        )
    } else if (streamingResult.deletedFiles > 0) {
        println(
            "[SiliconPlayer] pruned streaming cache on launch: deleted=${streamingResult.deletedFiles} " +
                "freed=${streamingResult.freedBytes} limits=(tracks=${streamingResult.maxTracks} bytes=${streamingResult.maxBytes})"
        )
    }

    val archiveResult = applyArchiveMountCachePolicy(prefs = prefs, cacheDir = cacheDir)
    if (archiveResult.deletedMounts > 0) {
        val action = if (archiveResult.clearedOnLaunch) "cleared" else "pruned"
        println(
            "[SiliconPlayer] $action archive mounts on launch: deleted=${archiveResult.deletedMounts} " +
                "freed=${archiveResult.freedBytes}"
        )
    }
}

private fun desktopResumedCachePaths(cacheRoot: File, configDir: File): Set<String> {
    val resumedSourceId = readSessionResumeSnapshot(configDir)?.sourceId ?: return emptySet()
    return setOf(resumedSourceId).filter { it.startsWith(cacheRoot.absolutePath) }.toSet()
}
