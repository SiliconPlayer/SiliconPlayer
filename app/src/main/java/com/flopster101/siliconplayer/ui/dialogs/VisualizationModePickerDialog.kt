package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.isWatchDevice

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VisualizationModePickerDialog(
    availableModes: List<VisualizationMode>,
    selectedMode: VisualizationMode,
    onSelectMode: (VisualizationMode) -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    globalInputGain: Int = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.gainPercent,
    onGlobalInputGainChange: (Int) -> Unit = {},
    trackInputGain: Int = 100,
    onTrackInputGainChange: (Int) -> Unit = {},
    showChannelLabels: Boolean = true,
    onShowChannelLabelsChange: (Boolean) -> Unit = {},
    onResetChannelScopeDefaults: () -> Unit = {},
    onDismiss: () -> Unit
) {
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Visualizer",
            onDismissRequest = onDismiss
        ) {
            val allUniversal = listOf(
                VisualizationMode.Off,
                VisualizationMode.Bars,
                VisualizationMode.Oscilloscope,
                VisualizationMode.VuMeters
            )
            allUniversal.forEach { mode ->
                val isSelected = mode == selectedMode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable {
                            onSelectMode(mode)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (availableModes.contains(VisualizationMode.ChannelScope)) {
                val isSelected = selectedMode == VisualizationMode.ChannelScope
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable {
                            onSelectMode(VisualizationMode.ChannelScope)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = VisualizationMode.ChannelScope.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            FilledTonalButton(
                onClick = {
                    onDismiss()
                    onOpenVisualizationSettings()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Settings")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isOptionsExpanded by remember(selectedMode) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // 1. Header Bar: Title (left) + Settings IconButton (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Visualizer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = {
                        onDismiss()
                        onOpenVisualizationSettings()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Visualizer settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category 1: Universal
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Universal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val universalModes = listOf(
                        Triple(VisualizationMode.Off, "Off", Icons.Default.VisibilityOff),
                        Triple(VisualizationMode.Bars, "Bars", Icons.Default.GraphicEq),
                        Triple(VisualizationMode.Oscilloscope, "Oscilloscope", Icons.Default.MonitorHeart),
                        Triple(VisualizationMode.VuMeters, "VU meters", Icons.Default.Equalizer)
                    )

                    // 2-column grid layout
                    for (i in universalModes.indices step 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val first = universalModes[i]
                            VisualizerModeCard(
                                mode = first.first,
                                label = first.second,
                                icon = first.third,
                                isSelected = selectedMode == first.first,
                                isEnabled = true,
                                subtitle = null,
                                onClick = { onSelectMode(first.first) },
                                modifier = Modifier.weight(1f)
                            )
                            if (i + 1 < universalModes.size) {
                                val second = universalModes[i + 1]
                                VisualizerModeCard(
                                    mode = second.first,
                                    label = second.second,
                                    icon = second.third,
                                    isSelected = selectedMode == second.first,
                                    isEnabled = true,
                                    subtitle = null,
                                    onClick = { onSelectMode(second.first) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Category 2: Advanced
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Advanced",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    val isChannelScopeAvailable = availableModes.contains(VisualizationMode.ChannelScope)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VisualizerModeCard(
                            mode = VisualizationMode.ChannelScope,
                            label = "Channel scope",
                            icon = Icons.Default.MonitorHeart,
                            isSelected = selectedMode == VisualizationMode.ChannelScope,
                            isEnabled = isChannelScopeAvailable,
                            subtitle = if (isChannelScopeAvailable) "Tracker modules" else "Requires tracker/MIDI module",
                            onClick = {
                                if (isChannelScopeAvailable) {
                                    onSelectMode(VisualizationMode.ChannelScope)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. MD3-style Collapsible Dropdown for Options
                // Only Channel scope has configurable options currently
                val isChannelScope = selectedMode == VisualizationMode.ChannelScope
                val optionsTitle = when (selectedMode) {
                    VisualizationMode.ChannelScope -> "Channel scope options"
                    VisualizationMode.Off -> "Visualizer options"
                    else -> "${selectedMode.label} options"
                }

                DynamicPerTrackOptionsDropdown(
                    title = optionsTitle,
                    activeMode = selectedMode,
                    isEnabled = isChannelScope,
                    isExpanded = isOptionsExpanded && isChannelScope,
                    onToggleExpand = {
                        if (isChannelScope) {
                            isOptionsExpanded = !isOptionsExpanded
                        }
                    },
                    globalInputGain = globalInputGain,
                    onGlobalInputGainChange = onGlobalInputGainChange,
                    trackInputGain = trackInputGain,
                    onTrackInputGainChange = onTrackInputGainChange,
                    showChannelLabels = showChannelLabels,
                    onShowChannelLabelsChange = onShowChannelLabelsChange,
                    onResetDefaults = onResetChannelScopeDefaults
                )

                // 4. Primary action button: Configure this visualizer (placed outside and below the dropdown)
                FilledTonalButton(
                    onClick = {
                        onDismiss()
                        if (selectedMode != VisualizationMode.Off) {
                            onOpenSelectedVisualizationSettings()
                        } else {
                            onOpenVisualizationSettings()
                        }
                    },
                    enabled = selectedMode != VisualizationMode.Off,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configure this visualizer",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun VisualizerModeCard(
    mode: VisualizationMode,
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "visCardContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(180),
        label = "visCardContent"
    )
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .alpha(if (isEnabled) 1.0f else 0.45f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) contentColor.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DynamicPerTrackOptionsDropdown(
    title: String,
    activeMode: VisualizationMode,
    isEnabled: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    globalInputGain: Int,
    onGlobalInputGainChange: (Int) -> Unit,
    trackInputGain: Int,
    onTrackInputGainChange: (Int) -> Unit,
    showChannelLabels: Boolean,
    onShowChannelLabelsChange: (Boolean) -> Unit,
    onResetDefaults: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(200),
        label = "arrowRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .alpha(if (isEnabled) 1.0f else 0.45f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Dropdown Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = isEnabled, onClick = onToggleExpand)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEnabled) {
                            if (isExpanded) "Tap to collapse per-song overrides" else "Per-song overrides and adjustments"
                        } else if (activeMode == VisualizationMode.Off) {
                            "No options for disabled visualizer"
                        } else {
                            "No options for this visualizer"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(arrowRotation)
                )
            }

            // Expandable configuration body (only populated for Channel Scope)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Reset to defaults action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onResetDefaults,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset defaults",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    // 1. Global input gain
                    ConfigSteppedSlider(
                        title = "Global input gain",
                        value = globalInputGain,
                        range = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.gainRangePercent,
                        step = 25,
                        unitLabel = "%",
                        onValueChange = onGlobalInputGainChange
                    )

                    // 2. Track input gain (stored per track)
                    ConfigSteppedSlider(
                        title = "Track input gain",
                        value = trackInputGain,
                        range = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.gainRangePercent,
                        step = 25,
                        unitLabel = "%",
                        onValueChange = onTrackInputGainChange
                    )

                    // 3. Show channel labels (mirror of full settings toggle)
                    ConfigToggleRow(
                        title = "Show channel labels",
                        subtitle = "Display track index, notes, and instruments",
                        checked = showChannelLabels,
                        onCheckedChange = onShowChannelLabelsChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSteppedSlider(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    unitLabel: String,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$value$unitLabel",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedIconButton(
                onClick = { onValueChange((value - step).coerceIn(range)) },
                enabled = value > range.first,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease",
                    modifier = Modifier.size(16.dp)
                )
            }

            Slider(
                value = value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
                onValueChange = { floatVal ->
                    val rounded = (Math.round(floatVal / 5.0) * 5).toInt().coerceIn(range)
                    onValueChange(rounded)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            OutlinedIconButton(
                onClick = { onValueChange((value + step).coerceIn(range)) },
                enabled = value < range.last,
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfigToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
