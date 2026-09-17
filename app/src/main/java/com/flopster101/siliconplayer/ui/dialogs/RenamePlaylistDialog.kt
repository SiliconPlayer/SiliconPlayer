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
 * Common rename-playlist dialog: prefilled with current title,
 * using the same FloatingActionDialog house styling as NewPlaylistDialog.
 */
@Composable
internal fun RenamePlaylistDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    val isRenamed = title.isNotBlank() && title.trim() != currentTitle.trim()
    FloatingActionDialog(
        title = "Rename playlist",
        onDismiss = onDismiss,
        confirmText = "Rename",
        confirmEnabled = isRenamed,
        onConfirm = { onConfirm(title.trim()) }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
