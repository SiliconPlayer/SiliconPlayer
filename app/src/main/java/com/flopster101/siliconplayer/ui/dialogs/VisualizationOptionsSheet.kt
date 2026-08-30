package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.flopster101.siliconplayer.AppDefaults
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
import android.os.Build
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
    data class Header(val setId: String, val label: String) : PresetListRow()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            OptionsSheetContent(mode, globalInputGain, onGlobalInputGainChange, trackInputGain, onTrackInputGainChange, showChannelLabels, onShowChannelLabelsChange, savedProjectMPreset, onProjectMPresetSelected, presetSetLabels, onResetDefaults)
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
                        OptionsSheetContent(mode, globalInputGain, onGlobalInputGainChange, trackInputGain, onTrackInputGainChange, showChannelLabels, onShowChannelLabelsChange, savedProjectMPreset, onProjectMPresetSelected, presetSetLabels, onResetDefaults)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionsSheetContent(
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
    onResetDefaults: () -> Unit
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogResetButton(text = "Reset defaults", onClick = onResetDefaults)
                }

                Spacer(modifier = Modifier.height(12.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectMOptionsContent(
    savedPreset: String?,
    onPresetSelected: (String) -> Unit,
    setLabels: Map<String, String>
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE) }
    var randomStart by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)) }
    var presetDuration by remember { mutableStateOf(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString())?.toDoubleOrNull() ?: AppDefaults.Visualization.ProjectM.presetDurationSeconds) }
    var hardCutEnabled by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled)) }
    var hardCutSensitivity by remember { mutableStateOf(prefs.getFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity)) }
    var rotationRandom by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom)) }
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
        DialogToggleRow(
            title = "Random rotation",
            subtitle = "Pick next preset randomly",
            checked = rotationRandom,
            onCheckedChange = { enabled ->
                rotationRandom = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, enabled).apply()
                try { SiliconVisNativeBridge.nativeProjectMSetRotationRandom(enabled) } catch (_: Throwable) {}
            }
        )
        DialogIntSliderRow(
            title = "Preset duration",
            value = presetDuration.toInt(),
            valueRange = 5..120,
            step = 1,
            unitLabel = "s",
            onValueChange = { v ->
                presetDuration = v.toDouble()
                prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, v.toDouble().toString()).apply()
                try { SiliconVisNativeBridge.nativeProjectMSetPresetDuration(v.toDouble()) } catch (_: Throwable) {}
            }
        )
        DialogToggleRow(
            title = "Hard cut",
            subtitle = "Allow hard cuts on beat",
            checked = hardCutEnabled,
            onCheckedChange = { enabled ->
                hardCutEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, enabled).apply()
                try { SiliconVisNativeBridge.nativeProjectMSetHardCutEnabled(enabled) } catch (_: Throwable) {}
            }
        )
        if (hardCutEnabled) {
            DialogIntSliderRow(
                title = "Hard cut sensitivity",
                value = (hardCutSensitivity * 10).toInt(),
                valueRange = 0..50,
                step = 1,
                unitLabel = "",
                onValueChange = { v ->
                    val f = v / 10.0f
                    hardCutSensitivity = f
                    prefs.edit().putFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, f).apply()
                    try { SiliconVisNativeBridge.nativeProjectMSetHardCutSensitivity(f) } catch (_: Throwable) {}
                }
            )
        }
    }

    if (showPresetList) {
        val presetKeys = remember(showPresetList) {
            SiliconVisNativeBridge.nativeProjectMGetPresetKeys()?.toList().orEmpty()
        }
        val presetSetIds = remember(showPresetList) {
            SiliconVisNativeBridge.nativeProjectMGetPresetSetIds()?.toList().orEmpty()
        }
        var searchQuery by remember { mutableStateOf("") }
        var debouncedQuery by remember { mutableStateOf("") }
        androidx.compose.runtime.LaunchedEffect(searchQuery) {
            kotlinx.coroutines.delay(300)
            debouncedQuery = searchQuery
        }
        val filteredIndices = remember(presetKeys, debouncedQuery) {
            if (debouncedQuery.isBlank()) presetKeys.indices.toList()
            else presetKeys.indices.filter { i ->
                val key = presetKeys[i]
                val display = presetDisplayName(key)
                val rel = presetKeyRelativePath(key)
                display.contains(debouncedQuery, ignoreCase = true) || rel.contains(debouncedQuery, ignoreCase = true)
            }
        }
        val groupedItems = remember(filteredIndices, presetKeys, presetSetIds, setLabels) {
            val rows = mutableListOf<PresetListRow>()
            var lastSet: String? = null
            for (i in filteredIndices) {
                val setId = presetSetIds.getOrElse(i) { ProjectMPresetSets.splitKey(presetKeys[i]).first }
                if (setId != lastSet) {
                    rows.add(PresetListRow.Header(setId, setLabels[setId] ?: setId))
                    lastSet = setId
                }
                rows.add(PresetListRow.Item(presetKeys[i]))
            }
            rows
        }
        val currentSetId = remember(currentPresetKey) {
            currentPresetKey?.let { ProjectMPresetSets.splitKey(it).first }
        }
        var collapsedSets by remember(presetKeys, currentSetId) {
            val allIds = presetSetIds.distinct()
            mutableStateOf(allIds.filter { it != currentSetId }.toSet())
        }
        val displayedRows = remember(groupedItems, collapsedSets, debouncedQuery) {
            if (debouncedQuery.isNotBlank()) groupedItems
            else {
                val out = mutableListOf<PresetListRow>()
                var isCollapsed = false
                for (row in groupedItems) {
                    when (row) {
                        is PresetListRow.Header -> {
                            isCollapsed = collapsedSets.contains(row.setId)
                            out.add(row)
                        }
                        is PresetListRow.Item -> if (!isCollapsed) out.add(row)
                    }
                }
                out
            }
        }
        val listState = rememberLazyListState()
        val currentDisplayedIndex = remember(displayedRows, currentPresetKey) {
            displayedRows.indexOfFirst { it is PresetListRow.Item && it.key == currentPresetKey }
        }
        androidx.compose.runtime.LaunchedEffect(currentDisplayedIndex) {
            if (currentDisplayedIndex >= 0) listState.scrollToItem(currentDisplayedIndex)
        }
        AlertDialog(
            onDismissRequest = { showPresetList = false },
            title = { Text("Choose a preset") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search presets") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (debouncedQuery.isBlank()) "${presetKeys.size} presets" else "${filteredIndices.size} of ${presetKeys.size} presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        displayedRows.forEachIndexed { index, row ->
                            when (row) {
                                is PresetListRow.Header -> {
                                    val isCollapsed = debouncedQuery.isBlank() && collapsedSets.contains(row.setId)
                                    stickyHeader {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            tonalElevation = 2.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .clickable {
                                                        collapsedSets = if (isCollapsed) collapsedSets - row.setId else collapsedSets + row.setId
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = row.label,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                is PresetListRow.Item -> {
                                    val key = row.key
                                    val isCurrent = key == currentPresetKey
                                    item(key = key) {
                                        Text(
                                            text = presetDisplayName(key),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
