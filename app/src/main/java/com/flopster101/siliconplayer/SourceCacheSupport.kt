package com.flopster101.siliconplayer

import android.content.Context
import android.net.Uri
import com.flopster101.siliconplayer.data.findExistingCachedFileForSource
import java.io.File

internal fun remoteFilenameHintFromUri(uri: Uri): String? {
    val fragmentHint = sanitizeRemoteLeafName(uri.fragment)
        ?.takeIf { it.contains('.') }
    if (fragmentHint != null) return fragmentHint

    val queryHint = listOf("filename", "file", "name")
        .firstNotNullOfOrNull { key ->
            sanitizeRemoteLeafName(uri.getQueryParameter(key))
                ?.takeIf { it.contains('.') }
        }
    if (queryHint != null) return queryHint

    return sanitizeRemoteLeafName(uri.lastPathSegment)
}

internal fun isRemoteSourceCached(context: Context, url: String): Boolean {
    val cacheRoot = File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR)
    return findExistingCachedFileForSource(cacheRoot, url) != null
}
