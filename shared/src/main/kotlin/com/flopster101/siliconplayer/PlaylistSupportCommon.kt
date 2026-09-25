package com.flopster101.siliconplayer

import java.io.File
import java.util.Locale

internal const val FAVORITES_PLAYLIST_ID = "__favorites__"
internal val SUPPORTED_PLAYLIST_EXTENSIONS = setOf("m3u", "m3u8", "pls", "xspf", "asx", "b4s", "vlc")

internal fun isSupportedPlaylistFileName(name: String): Boolean {
    return inferredPrimaryExtensionForName(name)
        ?.lowercase(Locale.ROOT) in SUPPORTED_PLAYLIST_EXTENSIONS
}

internal fun isSupportedPlaylistFile(file: File?): Boolean {
    return file != null && file.isFile && isSupportedPlaylistFileName(file.name)
}
