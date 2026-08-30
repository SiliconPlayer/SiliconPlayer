package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Context
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    onOpenOptions: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    var keepScreenOnState by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, AppDefaults.Visualization.keepScreenOn)) }
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
            val isSelected = selectedMode == VisualizationMode.ProjectM
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable {
                        onSelectMode(VisualizationMode.ProjectM)
                        onDismiss()
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = VisualizationMode.ProjectM.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            if (selectedMode != VisualizationMode.Off) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep screen on",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Keep on while visualization is visible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = keepScreenOnState,
                        onCheckedChange = { enabled ->
                            keepScreenOnState = enabled
                            prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, enabled).apply()
                        }
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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            PickerSheetContent(availableModes, selectedMode, onSelectMode, onOpenSelectedVisualizationSettings, onOpenVisualizationSettings, onOpenOptions, onDismiss, keepScreenOnState, onKeepScreenOnChange = { keepScreenOnState = it; prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, it).apply() })
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(top = 48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).size(width = 36.dp, height = 4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp)))
                        PickerSheetContent(availableModes, selectedMode, onSelectMode, onOpenSelectedVisualizationSettings, onOpenVisualizationSettings, onOpenOptions, onDismiss, keepScreenOnState, onKeepScreenOnChange = { keepScreenOnState = it; prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, it).apply() })
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSheetContent(
    availableModes: List<VisualizationMode>,
    selectedMode: VisualizationMode,
    onSelectMode: (VisualizationMode) -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenOptions: () -> Unit,
    onDismiss: () -> Unit,
    keepScreenOnState: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit
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
                    DialogSectionLabel(text = "Universal")

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
                            DialogSelectableCard(
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
                                DialogSelectableCard(
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
                    DialogSectionLabel(text = "Advanced")

                    val isChannelScopeAvailable = availableModes.contains(VisualizationMode.ChannelScope)
                    val isProjectMAvailable = availableModes.contains(VisualizationMode.ProjectM)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DialogSelectableCard(
                            label = "Channel scope",
                            icon = Icons.Default.MonitorHeart,
                            isSelected = selectedMode == VisualizationMode.ChannelScope,
                            isEnabled = isChannelScopeAvailable,
                            subtitle = if (isChannelScopeAvailable) "Tracker modules" else "Requires compatible decoder",
                            onClick = {
                                if (isChannelScopeAvailable) {
                                    onSelectMode(VisualizationMode.ChannelScope)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (isProjectMAvailable) {
                            DialogSelectableCard(
                                label = "projectM",
                                icon = Icons.Default.AutoAwesome,
                                isSelected = selectedMode == VisualizationMode.ProjectM,
                                isEnabled = isProjectMAvailable,
                                subtitle = "MilkDrop presets",
                                onClick = {
                                    if (isProjectMAvailable) {
                                        onSelectMode(VisualizationMode.ProjectM)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                val hasQuickOptions = when (selectedMode) {
                    VisualizationMode.ChannelScope -> availableModes.contains(VisualizationMode.ChannelScope)
                    VisualizationMode.ProjectM -> true
                    else -> false
                }
                if (hasQuickOptions) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = onOpenOptions),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Options",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Quick adjustments with a live preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                if (selectedMode != VisualizationMode.Off) {
                    DialogToggleRow(
                        title = "Keep screen on",
                        subtitle = "Keep screen on while visualization is visible",
                        checked = keepScreenOnState,
                        onCheckedChange = { enabled ->
                            onKeepScreenOnChange(enabled)
                        }
                    )
                }

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
