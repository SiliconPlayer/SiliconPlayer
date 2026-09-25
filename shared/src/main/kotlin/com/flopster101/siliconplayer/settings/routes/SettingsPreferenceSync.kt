package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.flopster101.siliconplayer.platform.AppPreferences

@Composable
internal fun PreferenceChangeSyncEffect(
    prefs: AppPreferences,
    watchedKeys: Set<String>,
    onRelevantChange: () -> Unit
) {
    DisposableEffect(prefs, watchedKeys) {
        val listener = AppPreferences.OnChangeListener { _, key ->
            if (key in watchedKeys) {
                onRelevantChange()
            }
        }
        prefs.addListener(listener)
        onDispose {
            prefs.removeListener(listener)
        }
    }
}

