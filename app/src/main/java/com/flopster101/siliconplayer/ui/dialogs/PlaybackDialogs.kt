package com.flopster101.siliconplayer.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.HttpSourceSpec
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.SubtuneEntry
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.isWatchDevice
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.formatShortDuration
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.recentArtworkThumbnailFile
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.ui.screens.BrowserLazyListScrollbar

@Composable
internal fun ManualSmbAuthenticationDialog(
    host: String,
    share: String,
    failureMessage: String?,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hasCredentials = username.trim().isNotEmpty() || password.trim().isNotEmpty()
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "SMB authentication required",
            onDismissRequest = onDismiss
        ) {
            failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = if (share.isBlank()) host else "$host/$share",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Username") },
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                enabled = hasCredentials,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue")
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
            title = { Text("SMB authentication required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = if (share.isBlank()) host else "$host/$share",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = hasCredentials) {
                    Text("Continue")
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

@Composable
internal fun ManualHttpAuthenticationDialog(
    requestSpec: HttpSourceSpec,
    failureMessage: String?,
    username: String,
    password: String,
    passwordVisible: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val hasCredentials = username.trim().isNotEmpty() || password.trim().isNotEmpty()
    val endpointLabel = buildString {
        append(requestSpec.scheme)
        append("://")
        append(requestSpec.host)
        requestSpec.port?.let { append(":$it") }
    }
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "HTTP authentication required",
            onDismissRequest = onDismiss
        ) {
            failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Text(
                text = endpointLabel,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Username") },
                shape = RoundedCornerShape(14.dp)
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password") },
                visualTransformation = if (passwordVisible) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onConfirm,
                enabled = hasCredentials,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue")
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
            title = { Text("HTTP authentication required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    failureMessage?.takeIf { it.isNotBlank() }?.let { message ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = endpointLabel,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Username") },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { onPasswordVisibilityChange(!passwordVisible) }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm, enabled = hasCredentials) {
                    Text("Continue")
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

@Composable
internal fun RemoteLoadProgressDialog(
    loadState: RemoteLoadUiState,
    onCancel: () -> Unit
) {
    val content: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val phaseText = when (loadState.phase) {
                RemoteLoadPhase.Connecting -> "Connecting..."
                RemoteLoadPhase.Downloading -> "Downloading..."
                RemoteLoadPhase.Opening -> "Opening..."
            }
            Text(phaseText, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (loadState.indeterminate) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                val progress = (loadState.percent ?: 0).coerceIn(0, 100) / 100f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (loadState.phase == RemoteLoadPhase.Downloading || loadState.phase == RemoteLoadPhase.Opening) {
                val downloadedLabel = formatByteCount(loadState.downloadedBytes)
                val sizeLabel = loadState.totalBytes?.let { total ->
                    "$downloadedLabel / ${formatByteCount(total)}"
                } ?: downloadedLabel
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                loadState.percent?.let { percent ->
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                loadState.bytesPerSecond?.takeIf { it > 0L }?.let { speed ->
                    Text(
                        text = "${formatByteCount(speed)}/s",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Opening remote source",
            onDismissRequest = {}
        ) {
            content()
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Cancel")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = {},
            title = { Text("Opening remote source") },
            text = content,
            confirmButton = {
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun SoxExperimentalDialog(
    onDismiss: () -> Unit
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "SoX is experimental",
            onDismissRequest = onDismiss
        ) {
            Text(
                text = "SoX output resampling may cause unstable timeline behavior on some content. " +
                    "For discontinuous timeline cores (like module pattern jumps/loop points), " +
                    "the engine automatically falls back to Built-in for stability.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("OK")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text("SoX is experimental") },
            text = {
                Text(
                    "SoX output resampling may cause unstable timeline behavior on some content. " +
                        "For discontinuous timeline cores (like module pattern jumps/loop points), " +
                        "the engine automatically falls back to Built-in for stability."
                )
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
internal fun SubtuneSelectorDialog(
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onSelectSubtune: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Subtunes",
            onDismissRequest = onDismiss
        ) {
            if (subtuneEntries.isEmpty()) {
                Text("No subtunes available.", textAlign = TextAlign.Center)
            } else {
                subtuneEntries.forEach { entry ->
                    val isCurrent = entry.index == currentSubtuneIndex
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSubtune(entry.index)
                                onDismiss()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${entry.index + 1}. ${entry.title}",
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val details = buildString {
                                append(formatShortDuration(entry.durationSeconds))
                                if (entry.artist.isNotBlank()) {
                                    append(" • ")
                                    append(entry.artist)
                                }
                            }
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Close")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text("Subtunes") },
            text = {
                if (subtuneEntries.isEmpty()) {
                    Text("No subtunes available.")
                } else {
                    val listState = rememberLazyListState()
                    val scrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
                        enabled = subtuneEntries.size > 1,
                        listState = listState,
                        flashKey = subtuneEntries.size to currentSubtuneIndex,
                        label = "subtuneSelectorScrollbarAlpha"
                    )
                    val scrollbarHeld = remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                count = subtuneEntries.size,
                                key = { index -> subtuneEntries[index].index }
                            ) { index ->
                                val entry = subtuneEntries[index]
                                val isCurrent = entry.index == currentSubtuneIndex
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectSubtune(entry.index) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = "${entry.index + 1}. ${entry.title}",
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val details = buildString {
                                            append(formatShortDuration(entry.durationSeconds))
                                            if (entry.artist.isNotBlank()) {
                                                append(" • ")
                                                append(entry.artist)
                                            }
                                        }
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        BrowserLazyListScrollbar(
                            listState = listState,
                            onDragActiveChanged = { isActive -> scrollbarHeld.value = isActive },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxSize()
                                .graphicsLayer(alpha = if (scrollbarHeld.value) 1f else scrollbarAlpha)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}


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
