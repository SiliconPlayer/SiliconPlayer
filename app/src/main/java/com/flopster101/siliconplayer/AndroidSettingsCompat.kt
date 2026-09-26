package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

fun readPlaylistCoverGenerationMode(prefs: SharedPreferences): PlaylistCoverGenerationMode =
    readPlaylistCoverGenerationMode(AndroidAppPreferences(prefs))

fun savePlaylistCoverGenerationMode(
    prefs: SharedPreferences,
    mode: PlaylistCoverGenerationMode
) = savePlaylistCoverGenerationMode(AndroidAppPreferences(prefs), mode)

internal fun readPlaylistLibraryState(prefs: SharedPreferences): PlaylistLibraryState {
    val dir = DomainStoreDirs.configDir
    return if (dir != null) {
        readPlaylistLibraryState(dir, AndroidAppPreferences(prefs))
    } else {
        readPlaylistLibraryState(AndroidAppPreferences(prefs))
    }
}

internal fun writePlaylistLibraryState(
    prefs: SharedPreferences,
    state: PlaylistLibraryState
) {
    val dir = DomainStoreDirs.configDir
    if (dir != null) {
        writePlaylistLibraryState(dir, state)
    } else {
        writePlaylistLibraryState(AndroidAppPreferences(prefs), state)
    }
}

fun readChannelScopeVisibleElementSelection(prefs: SharedPreferences): Set<String> =
    readChannelScopeVisibleElementSelection(AndroidAppPreferences(prefs))

fun SharedPreferences.Editor.putChannelScopeVisibleElementSelection(
    selectedStorageKeys: Set<String>
): SharedPreferences.Editor {
    channelScopeVisibleElementOptions().forEach { option ->
        putBoolean(option.storageKey, selectedStorageKeys.contains(option.storageKey))
    }
    return this
}
