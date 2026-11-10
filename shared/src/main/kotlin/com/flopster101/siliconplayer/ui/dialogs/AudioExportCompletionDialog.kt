package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.export.AudioExportCompletion
import com.flopster101.siliconplayer.export.AudioExportCompletionAction
import com.flopster101.siliconplayer.export.formatAudioExportByteRate
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.formatTime
import com.flopster101.siliconplayer.ui.theme.Radius

@Composable
internal fun AudioExportCompletionDialog(
    completion: AudioExportCompletion,
    supportedActions: Set<AudioExportCompletionAction>,
    onShare: () -> Unit,
    onOpenFolder: () -> Unit,
    onPlayNow: () -> Unit,
    onClose: () -> Unit
) {
    var tracksExpanded by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = adaptiveDialogProperties()) {
        Surface(
            modifier = adaptiveDialogModifier().heightIn(max = 560.dp),
            shape = Radius.Dialog,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Export complete", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = completion.outputName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radius.Card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            AudioExportStatRow("Duration", formatTime(completion.durationSeconds))
                            AudioExportStatRow("Size", formatByteCount(completion.sizeBytes))
                            AudioExportStatRow("Elapsed", formatTime(completion.elapsedSeconds))
                            AudioExportStatRow(
                                "Average speed",
                                formatAudioExportByteRate(completion.sizeBytes, completion.elapsedSeconds)
                            )
                            AudioExportStatRow("Format", completion.formatLabel)
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radius.Card,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tracksExpanded = !tracksExpanded }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (completion.trackCount == 1) {
                                        "1 track"
                                    } else {
                                        "${completion.trackCount} tracks"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (tracksExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .rotate(if (tracksExpanded) 180f else 0f)
                                )
                            }
                            AnimatedVisibility(
                                visible = tracksExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                                    completion.tracks.forEachIndexed { index, track ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${index + 1}.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = formatTime(track.durationSeconds),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClose, shape = Radius.ControlCompact) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (AudioExportCompletionAction.Share in supportedActions) {
                        TextButton(onClick = onShare, shape = Radius.ControlCompact) {
                            Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                    if (AudioExportCompletionAction.OpenFolder in supportedActions) {
                        TextButton(onClick = onOpenFolder, shape = Radius.ControlCompact) {
                            Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Folder")
                        }
                    }
                    if (AudioExportCompletionAction.PlayNow in supportedActions) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onPlayNow, shape = Radius.ControlCompact) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioExportStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
