package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import kotlinx.coroutines.delay

private fun presetKeyRelativePath(key: String): String {
    return ProjectMPresetSets.splitKey(key).second
}

private fun presetDisplayName(key: String): String {
    val name = presetKeyRelativePath(key).substringAfterLast('/')
    val withoutExt = if (name.endsWith(".milk")) name.removeSuffix(".milk") else name
    return withoutExt.replace('_', ' ')
}

private sealed class PresetListRow {
    data class Header(val label: String) : PresetListRow()
    data class Item(val key: String) : PresetListRow()
}

/**
 * Content-height options sheet for the selected visualizer.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun VisualizationOptionsSheet(
    mode: VisualizationMode,
    globalInputGain: Int,
    onGlobalInputGainChange: (Int) -> Unit,
    trackInputGain: Int,
    onTrackInputGainChange: (Int) -> Unit,
    showChannelLabels: Boolean,
    onShowChannelLabelsChange: (Boolean) -> Unit,
    savedProjectMPreset: String?,
    onProjectMPresetSelected: (String) -> Unit,
    presetSetLabels: Map<String, String>,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${mode.label} options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogResetButton(text = "Reset defaults", onClick = onResetDefaults)
                }

                when (mode) {
                    VisualizationMode.ChannelScope -> ChannelScopeOptionsContent(
                        globalInputGain = globalInputGain,
                        onGlobalInputGainChange = onGlobalInputGainChange,
                        trackInputGain = trackInputGain,
                        onTrackInputGainChange = onTrackInputGainChange,
                        showChannelLabels = showChannelLabels,
                        onShowChannelLabelsChange = onShowChannelLabelsChange
                    )
                    VisualizationMode.ProjectM -> ProjectMOptionsContent(
                        savedPreset = savedProjectMPreset,
                        onPresetSelected = onProjectMPresetSelected,
                        setLabels = presetSetLabels
                    )
                    else -> Unit
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ChannelScopeOptionsContent(
    globalInputGain: Int,
    onGlobalInputGainChange: (Int) -> Unit,
    trackInputGain: Int,
    onTrackInputGainChange: (Int) -> Unit,
    showChannelLabels: Boolean,
    onShowChannelLabelsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DialogIntSliderRow(
            title = "Global input gain",
            value = globalInputGain,
            valueRange = 25..1000,
            step = 25,
            dragSnap = 5,
            unitLabel = "%",
            onValueChange = onGlobalInputGainChange
        )
        DialogIntSliderRow(
            title = "Track input gain",
            value = trackInputGain,
            valueRange = 25..1000,
            step = 25,
            dragSnap = 5,
            unitLabel = "%",
            onValueChange = onTrackInputGainChange
        )
        DialogToggleRow(
            title = "Show channel labels",
            subtitle = "Display track index, notes, and instruments",
            checked = showChannelLabels,
            onCheckedChange = onShowChannelLabelsChange
        )
    }
}

@Composable
private fun ProjectMOptionsContent(
    savedPreset: String?,
    onPresetSelected: (String) -> Unit,
    setLabels: Map<String, String>
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE) }
    var randomStart by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)) }
    var presetName by remember { mutableStateOf<String?>(null) }
    var currentPresetKey by remember { mutableStateOf<String?>(null) }
    var locked by remember { mutableStateOf(false) }
    var showPresetList by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            presetName = SiliconVisNativeBridge.nativeProjectMGetPresetName()
            currentPresetKey = SiliconVisNativeBridge.nativeProjectMGetCurrentPresetKey()
            locked = SiliconVisNativeBridge.nativeProjectMIsPresetLocked()
            delay(500)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Saved preset: " + (savedPreset?.let { presetDisplayName(it) } ?: "(none)"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedIconButton(
                    onClick = {
                        SiliconVisNativeBridge.nativeProjectMPreviousPreset(true)
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous preset",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = presetName ?: "No preset loaded",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showPresetList = true }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )

                OutlinedIconButton(
                    onClick = {
                        SiliconVisNativeBridge.nativeProjectMNextPreset(true)
                    },
                    modifier = Modifier.size(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next preset",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        DialogToggleRow(
            title = "Lock preset",
            subtitle = "Pause automatic preset rotation",
            checked = locked,
            onCheckedChange = { enabled ->
                SiliconVisNativeBridge.nativeProjectMSetPresetLocked(enabled)
                locked = enabled
            }
        )
        DialogToggleRow(
            title = "Random preset on start",
            subtitle = "Start with a random preset each time",
            checked = randomStart,
            onCheckedChange = { enabled ->
                randomStart = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, enabled).apply()
            }
        )
    }

    if (showPresetList) {
        val presetKeys = remember(showPresetList) {
            SiliconVisNativeBridge.nativeProjectMGetPresetKeys()?.toList().orEmpty()
        }
        val presetSetIds = remember(showPresetList) {
            SiliconVisNativeBridge.nativeProjectMGetPresetSetIds()?.toList().orEmpty()
        }
        // Group contiguous rows by set; keys are sorted by setId then relativePath.
        val groupedItems = remember(presetKeys, presetSetIds) {
            val rows = mutableListOf<PresetListRow>()
            var lastSet: String? = null
            for (i in presetKeys.indices) {
                val setId = presetSetIds.getOrElse(i) { ProjectMPresetSets.splitKey(presetKeys[i]).first }
                if (setId != lastSet) {
                    rows.add(PresetListRow.Header(setLabels[setId] ?: setId))
                    lastSet = setId
                }
                rows.add(PresetListRow.Item(presetKeys[i]))
            }
            rows
        }
        AlertDialog(
            onDismissRequest = { showPresetList = false },
            title = { Text("Choose a preset") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    groupedItems.forEach { row ->
                        when (row) {
                            is PresetListRow.Header -> {
                                item {
                                    Text(
                                        text = row.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            is PresetListRow.Item -> {
                                val key = row.key
                                val isCurrent = key == currentPresetKey
                                item {
                                    Text(
                                        text = presetDisplayName(key),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                SiliconVisNativeBridge.nativeProjectMLoadPreset(key, true)
                                                onPresetSelected(key)
                                                showPresetList = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPresetList = false }) {
                    Text("Close")
                }
            }
        )
    }
}
