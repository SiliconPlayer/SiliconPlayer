package com.flopster101.siliconplayer.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerBaseTheme

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SiliconPlayer"
    ) {
        SiliconPlayerBaseTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                Text("SiliconPlayer Desktop")
            }
        }
    }
}
