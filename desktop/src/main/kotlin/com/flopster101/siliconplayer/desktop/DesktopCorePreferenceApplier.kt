package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.CoreOptionApplyPolicy
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.ToastHandler
import com.flopster101.siliconplayer.pushCorePrefToNative as pushSharedCorePrefToNative
import com.flopster101.siliconplayer.pushStoredCorePreferencesToNative as pushSharedStoredCorePreferencesToNative

internal fun pushDesktopCorePrefToNative(
    prefs: AppPreferences,
    key: String,
    toast: ToastHandler?,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
    activeDecoderName: String?,
    silent: Boolean = false
) = pushSharedCorePrefToNative(
    prefs = prefs,
    key = key,
    toast = toast,
    isPlaying = isPlaying,
    hasCurrentTrack = hasCurrentTrack,
    activeDecoderName = activeDecoderName,
    silent = silent
)

internal fun pushStoredDesktopCorePreferencesToNative(prefs: AppPreferences) =
    pushSharedStoredCorePreferencesToNative(prefs)
