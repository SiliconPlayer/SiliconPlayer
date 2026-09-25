@file:JvmName("AndroidBrowserLocationPersistence")

package com.flopster101.siliconplayer

import android.content.SharedPreferences
import com.flopster101.siliconplayer.platform.AndroidAppPreferences

internal fun readRememberedBrowserLaunchState(
    prefs: SharedPreferences
): BrowserLaunchState = readRememberedBrowserLaunchState(AndroidAppPreferences(prefs))

internal fun persistRememberedBrowserLaunchState(
    prefs: SharedPreferences,
    state: BrowserLaunchState
) = persistRememberedBrowserLaunchState(AndroidAppPreferences(prefs), state)

internal fun clearRememberedBrowserLaunchState(
    prefs: SharedPreferences
) = clearRememberedBrowserLaunchState(AndroidAppPreferences(prefs))
