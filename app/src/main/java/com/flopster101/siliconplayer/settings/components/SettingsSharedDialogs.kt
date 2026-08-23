package com.flopster101.siliconplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class ChoiceDialogOption<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true
)

internal data class SettingsActionDialogItem(
    val label: String,
    val onSelected: () -> Unit
)

@Composable
internal fun SettingsConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel"
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(confirmLabel)
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(dismissLabel)
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm()
                    onDismiss()
                }) {
                    Text(confirmLabel)
                }
            }
        )
    }
}

@Composable
internal fun SettingsInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "OK"
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(confirmLabel)
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(confirmLabel)
                }
            }
        )
    }
}

@Composable
internal fun SettingsActionListDialog(
    title: String,
    actions: List<SettingsActionDialogItem>,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String = "Cancel"
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            actions.forEach { action ->
                FilledTonalButton(
                    onClick = {
                        action.onSelected()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(action.label)
                }
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(dismissLabel)
            }
        }
    } else {
        val scrollState = rememberScrollState()
        val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)
        var scrollViewportHeightPx by remember { mutableFloatStateOf(0f) }

        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .onSizeChanged { scrollViewportHeightPx = it.height.toFloat() }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 10.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (!message.isNullOrBlank()) {
                            Text(message)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        actions.forEach { action ->
                            TextButton(
                                onClick = {
                                    action.onSelected()
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(action.label)
                            }
                        }
                    }
                    if (scrollState.maxValue > 0 && scrollViewportHeightPx > 0f) {
                        val contentHeightPx = scrollViewportHeightPx + scrollState.maxValue.toFloat()
                        val thumbFraction = (scrollViewportHeightPx / contentHeightPx).coerceIn(0.08f, 1f)
                        val offsetFraction = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                        VerticalScrollbarTrack(
                            thumbFraction = thumbFraction,
                            offsetFraction = offsetFraction,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(10.dp)
                                .fillMaxHeight(),
                            onDragFractionChanged = dragToFraction
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(dismissLabel)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun <T> SettingsSingleChoiceDialog(
    title: String,
    selectedValue: T,
    options: List<ChoiceDialogOption<T>>,
    onSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    showCancelButton: Boolean = true
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            options.forEach { option ->
                val isSelected = option.value == selectedValue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable(enabled = option.enabled) {
                            onSelected(option.value)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (!option.enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (showCancelButton) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    } else {
        val configuration = LocalConfiguration.current
        val scrollState = rememberScrollState()
        val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)
        var scrollViewportHeightPx by remember { mutableFloatStateOf(0f) }
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                val maxDialogListHeight = configuration.screenHeightDp.dp * 0.62f
                CompositionLocalProvider(
                    androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement provides false
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogListHeight)
                            .onSizeChanged { scrollViewportHeightPx = it.height.toFloat() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 10.dp)
                                .verticalScroll(scrollState)
                        ) {
                            if (!description.isNullOrBlank()) {
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            options.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .clickable(enabled = option.enabled) {
                                            onSelected(option.value)
                                            onDismiss()
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = option.value == selectedValue,
                                        enabled = option.enabled,
                                        onClick = {
                                            if (option.enabled) {
                                                onSelected(option.value)
                                                onDismiss()
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (option.enabled) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                        if (scrollState.maxValue > 0 && scrollViewportHeightPx > 0f) {
                            val contentHeightPx = scrollViewportHeightPx + scrollState.maxValue.toFloat()
                            val thumbFraction = (scrollViewportHeightPx / contentHeightPx).coerceIn(0.08f, 1f)
                            val offsetFraction = (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
                            VerticalScrollbarTrack(
                                thumbFraction = thumbFraction,
                                offsetFraction = offsetFraction,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(10.dp)
                                    .fillMaxHeight(),
                                onDragFractionChanged = dragToFraction
                            )
                        }
                    }
                }
            },
            dismissButton = {
                if (showCancelButton) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            },
            confirmButton = {}
        )
    }
}
