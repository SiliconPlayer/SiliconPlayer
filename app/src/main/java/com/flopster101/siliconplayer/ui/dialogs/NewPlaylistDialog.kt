package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Common new-playlist dialog: name field with a deduplicated
 * "My Playlist" default shown as ghost placeholder text. Used standalone
 * (library FAB) and stacked on top of the add-to-playlist sheet. Extra
 * options (custom cover, …) will grow here later.
 */
@Composable
internal fun NewPlaylistDialog(
    existingTitles: Set<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultTitle = remember(existingTitles) {
        var candidate = "My Playlist"
        var suffix = 2
        while (candidate in existingTitles) {
            candidate = "My Playlist $suffix"
            suffix += 1
        }
        candidate
    }
    var title by remember(existingTitles) { mutableStateOf("") }
    FloatingActionDialog(
        title = "Name your playlist",
        onDismiss = onDismiss,
        confirmText = "Create",
        confirmEnabled = true,
        onConfirm = { onConfirm(title.trim().ifBlank { defaultTitle }) }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(defaultTitle) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
