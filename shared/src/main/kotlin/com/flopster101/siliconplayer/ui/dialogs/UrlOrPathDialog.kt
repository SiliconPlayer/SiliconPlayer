package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.TrackTextInputActive
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.platform.isWatchDevice

@Composable
internal fun UrlOrPathDialog(
    input: String,
    forceCaching: Boolean,
    onInputChange: (String) -> Unit,
    onForceCachingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onOpen: () -> Unit
) {
    TrackTextInputActive()
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Open URL or path",
            onDismissRequest = onDismiss
        ) {
            Text(
                text = "Supported: /unix/path, file:///path, http(s)://...",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Path or URL") },
                shape = RoundedCornerShape(14.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onForceCachingChange(!forceCaching) }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = forceCaching,
                    onCheckedChange = onForceCachingChange
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Force caching",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                enabled = input.isNotBlank(),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Open")
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
            title = { Text("Open URL or path") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Supported: /unix/path, file:///path/to/file, http(s)://example/file")
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Path or URL") },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForceCachingChange(!forceCaching) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = forceCaching,
                            onCheckedChange = onForceCachingChange
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Force caching",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = input.isNotBlank(),
                    onClick = onOpen
                ) {
                    Text("Open")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}
