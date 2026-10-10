package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.platform.isWatchDevice

@Composable
internal fun PlaylistOpenActionDialog(
    title: String,
    entryCount: Int,
    onPlayNow: () -> Unit,
    onBrowseEntries: () -> Unit,
    onDismiss: () -> Unit,
    onSaveAsPlaylist: (() -> Unit)? = null
) {
    val message = if (entryCount == 1) {
        "This playlist contains 1 entry. You can play it immediately or inspect its tracks first."
    } else {
        "This playlist contains $entryCount entries. You can play from the top or inspect its tracks first."
    }
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onPlayNow()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Play")
            }
            if (onSaveAsPlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        onSaveAsPlaylist()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save as playlist")
                }
            }
            FilledTonalButton(
                onClick = {
                    onBrowseEntries()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("List tracks")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Text(text = message)
            },
            confirmButton = {
                TextButton(onClick = onPlayNow) {
                    Text("Play")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSaveAsPlaylist != null) {
                        TextButton(onClick = onSaveAsPlaylist) {
                            Text("Save")
                        }
                    }
                    TextButton(onClick = onBrowseEntries) {
                        Text("List tracks")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}
