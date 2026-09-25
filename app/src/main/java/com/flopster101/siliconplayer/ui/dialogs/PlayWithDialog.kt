@file:JvmName("AndroidPlayWithDialog")
package com.flopster101.siliconplayer.ui.dialogs

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.platform.AndroidAppPreferences
import java.io.File

@Composable
internal fun PlayWithDialog(
    file: File,
    prefs: SharedPreferences,
    showDontAskAgain: Boolean = false,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    PlayWithDialog(
        file = file,
        prefs = AndroidAppPreferences(prefs),
        showDontAskAgain = showDontAskAgain,
        onPlay = onPlay,
        onDismiss = onDismiss
    )
}
