package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.applyArchiveMountCachePolicy
import com.flopster101.siliconplayer.applyRemoteSourceCachePolicy
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.readSessionResumeSnapshot
import java.io.File

// Startup cache policy mirroring MainActivity: clear/prune URL and archive caches, protecting
// the cached file the session is about to reopen.
internal fun applyDesktopCachePoliciesOnLaunch(
    prefs: AppPreferences,
    cacheDir: File,
    configDir: File
) {
    val cacheRoot = File(cacheDir, REMOTE_SOURCE_CACHE_DIR)
    val remoteResult = applyRemoteSourceCachePolicy(
        prefs = prefs,
        cacheRoot = cacheRoot,
        protectedPaths = desktopResumedCachePaths(cacheRoot, configDir)
    )
    if (remoteResult.clearedOnLaunch) {
        println(
            "[SiliconPlayer] cleared remote cache on launch: deleted=${remoteResult.deletedFiles} " +
                "skipped=${remoteResult.skippedFiles} freed=${remoteResult.freedBytes}"
        )
    } else if (remoteResult.deletedFiles > 0) {
        println(
            "[SiliconPlayer] pruned remote cache on launch: deleted=${remoteResult.deletedFiles} " +
                "freed=${remoteResult.freedBytes} limits=(tracks=${remoteResult.maxTracks} bytes=${remoteResult.maxBytes})"
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
