package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.HttpRemoteExportRequest
import com.flopster101.siliconplayer.ManualSourceResolution
import com.flopster101.siliconplayer.ManualSourceType
import com.flopster101.siliconplayer.RemoteExportRequest
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.SmbRemoteExportRequest
import com.flopster101.siliconplayer.enforceRemoteCacheLimitsFromPrefs
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.RemoteSourceExportSupport
import java.io.File

/**
 * Downloads a remote source into the URL cache and returns the local file, mirroring Android's
 * "play as cached" open path. The freshly cached file is protected from the limit prune.
 */
internal suspend fun prepareRemotePlaybackCacheFile(
    support: RemoteSourceExportSupport,
    resolution: ManualSourceResolution,
    prefs: AppPreferences,
    cacheRoot: File,
    onStatus: suspend (RemoteLoadUiState) -> Unit
): Result<File> {
    val preferredName = resolution.displayFile?.name ?: "remote"
    val request: RemoteExportRequest = when (resolution.type) {
        ManualSourceType.RemoteUrl -> HttpRemoteExportRequest(
            sourceId = resolution.sourceId,
            requestUrl = resolution.requestUrl,
            preferredFileName = preferredName
        )

        ManualSourceType.Smb -> {
            val spec = resolution.smbSpec
                ?: return Result.failure(IllegalStateException("Invalid SMB source"))
            SmbRemoteExportRequest(
                sourceId = resolution.sourceId,
                smbSpec = spec,
                preferredFileName = preferredName
            )
        }

        else -> return Result.failure(IllegalStateException("Not a remote source"))
    }

    val cachedItem = support.prepareRemoteExportFile(request, onStatus).getOrElse { return Result.failure(it) }
    enforceRemoteCacheLimitsFromPrefs(
        prefs = prefs,
        cacheRoot = cacheRoot,
        protectedPaths = setOf(cachedItem.sourceFile.absolutePath)
    )
    return Result.success(cachedItem.sourceFile)
}
