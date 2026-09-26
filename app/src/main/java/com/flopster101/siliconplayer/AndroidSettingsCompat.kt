package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

fun readPlaylistCoverGenerationMode(prefs: SharedPreferences): PlaylistCoverGenerationMode =
    readPlaylistCoverGenerationMode(AndroidAppPreferences(prefs))

fun savePlaylistCoverGenerationMode(
    prefs: SharedPreferences,
    mode: PlaylistCoverGenerationMode
) = savePlaylistCoverGenerationMode(AndroidAppPreferences(prefs), mode)

internal fun readPlaylistLibraryState(prefs: SharedPreferences): PlaylistLibraryState =
    readPlaylistLibraryState(AndroidAppPreferences(prefs))

internal fun writePlaylistLibraryState(
    prefs: SharedPreferences,
    state: PlaylistLibraryState
) = writePlaylistLibraryState(AndroidAppPreferences(prefs), state)

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
