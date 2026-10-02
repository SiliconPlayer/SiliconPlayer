package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.ChoiceDialogOption
import com.flopster101.siliconplayer.SettingsSingleChoiceDialog
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.TrackTextInputActive
import com.flopster101.siliconplayer.VisualizationChannelScopeTrackTransition
import com.flopster101.siliconplayer.VisualizationChannelScopeWaveRenderMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.ui.screens.starfieldActivePreset
import com.flopster101.siliconplayer.ui.screens.starfieldKeysFor
import com.flopster101.siliconplayer.ui.screens.starfieldPresetTuneFor
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import kotlinx.coroutines.delay
import java.util.Locale

private fun splitPresetKey(key: String): Pair<String, String> {
    val idx = key.indexOf('\u001F')
    if (idx < 0) return key to ""
    return key.substring(0, idx) to key.substring(idx + 1)
}

private fun presetKeyRelativePath(key: String): String {
    return splitPresetKey(key).second
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
    onDismiss: () -> Unit,
    resetNonce: Int = 0
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ) {
        OptionsSheetContent(
            mode = mode,
            globalInputGain = globalInputGain,
            onGlobalInputGainChange = onGlobalInputGainChange,
            trackInputGain = trackInputGain,
            onTrackInputGainChange = onTrackInputGainChange,
            showChannelLabels = showChannelLabels,
            onShowChannelLabelsChange = onShowChannelLabelsChange,
            savedProjectMPreset = savedProjectMPreset,
            onProjectMPresetSelected = onProjectMPresetSelected,
            presetSetLabels = presetSetLabels,
            onResetDefaults = onResetDefaults,
            resetNonce = resetNonce
        )
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
    onResetDefaults: () -> Unit,
    resetNonce: Int = 0
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
                    onShowChannelLabelsChange = onShowChannelLabelsChange,
                    resetNonce = resetNonce
                )
                VisualizationMode.ProjectM -> ProjectMOptionsContent(
                    savedPreset = savedProjectMPreset,
                    onPresetSelected = onProjectMPresetSelected,
                    setLabels = presetSetLabels
                )
                VisualizationMode.Starfield -> StarfieldOptionsContent(
                    resetNonce = resetNonce
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
    onShowChannelLabelsChange: (Boolean) -> Unit,
    resetNonce: Int = 0
) {
    val prefs = LocalAppPreferences.current
    var waveformClippingEnabled by remember(resetNonce) {
        mutableStateOf(
            prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVEFORM_CLIPPING_ENABLED,
                AppDefaults.Visualization.ChannelScope.waveformClippingEnabled
            )
        )
    }
    var waveRenderMode by remember(resetNonce) {
        mutableStateOf(
            VisualizationChannelScopeWaveRenderMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVE_RENDER_MODE,
                    AppDefaults.Visualization.ChannelScope.waveRenderMode.storageValue
                )
            )
        )
    }
    var showWaveRenderModeDialog by remember { mutableStateOf(false) }
    var trackTransition by remember(resetNonce) {
        mutableStateOf(
            VisualizationChannelScopeTrackTransition.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRACK_TRANSITION,
                    AppDefaults.Visualization.ChannelScope.trackTransition.storageValue
                )
            )
        )
    }
    var showTrackTransitionDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DialogIntSliderRow(
            title = "Gain (current track)",
            value = trackInputGain,
            valueRange = 10..500,
            step = 5,
            unitLabel = "%",
            onValueChange = onTrackInputGainChange
        )
        DialogIntSliderRow(
            title = "Gain (all tracks)",
            value = globalInputGain,
            valueRange = 10..500,
            step = 5,
            unitLabel = "%",
            onValueChange = onGlobalInputGainChange
        )
        DialogToggleRow(
            title = "Channel labels",
            subtitle = "Show channel label overlay",
            checked = showChannelLabels,
            onCheckedChange = onShowChannelLabelsChange
        )
        DialogToggleRow(
            title = "Waveform clipping",
            subtitle = "Constrain waveforms to ceiling and floor",
            checked = waveformClippingEnabled,
            onCheckedChange = { enabled ->
                waveformClippingEnabled = enabled
                prefs.edit().putBoolean(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVEFORM_CLIPPING_ENABLED,
                    enabled
                ).apply()
            }
        )
        DialogValuePickerRow(
            title = "Wave rendering",
            subtitle = "Antialiased smooths trace edges. CRT renders steep transitions softer and dimmer, like a phosphor screen.",
            value = waveRenderMode.label,
            onClick = { showWaveRenderModeDialog = true }
        )
        DialogValuePickerRow(
            title = "Track transition",
            subtitle = "How the previous song's layout leaves when the track changes.",
            value = trackTransition.label,
            onClick = { showTrackTransitionDialog = true }
        )
    }

    if (showWaveRenderModeDialog) {
        SettingsSingleChoiceDialog(
            title = "Wave rendering",
            selectedValue = waveRenderMode,
            options = VisualizationChannelScopeWaveRenderMode.entries.map { mode ->
                ChoiceDialogOption(value = mode, label = mode.label)
            },
            onSelected = { mode ->
                waveRenderMode = mode
                prefs.edit().putString(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVE_RENDER_MODE,
                    mode.storageValue
                ).apply()
            },
            onDismiss = { showWaveRenderModeDialog = false }
        )
    }
    if (showTrackTransitionDialog) {
        SettingsSingleChoiceDialog(
            title = "Track transition",
            selectedValue = trackTransition,
            options = VisualizationChannelScopeTrackTransition.entries.map { transition ->
                ChoiceDialogOption(value = transition, label = transition.label)
            },
            onSelected = { transition ->
                trackTransition = transition
                prefs.edit().putString(
                    AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRACK_TRANSITION,
                    transition.storageValue
                ).apply()
            },
            onDismiss = { showTrackTransitionDialog = false }
        )
    }
}

@Composable
private fun StarfieldSheetSliderRow(
    title: String,
    displayValue: String,
    value: Int,
    valueRange: IntRange,
    step: Int = 1,
    dragSnap: Int = 1,
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
                text = displayValue,
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
                onClick = { onValueChange((value - step).coerceIn(valueRange)) },
                enabled = value > valueRange.first,
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
                value = value.toFloat().coerceIn(valueRange.first.toFloat(), valueRange.last.toFloat()),
                onValueChange = { floatVal ->
                    onValueChange(snapSheetSliderToStep(floatVal, valueRange, dragSnap))
                },
                valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            OutlinedIconButton(
                onClick = { onValueChange((value + step).coerceIn(valueRange)) },
                enabled = value < valueRange.last,
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

private fun snapSheetSliderToStep(floatVal: Float, valueRange: IntRange, snap: Int): Int {
    val clamped = floatVal.toInt().coerceIn(valueRange.first, valueRange.last)
    if (snap <= 1) return clamped
    val offset = clamped - valueRange.first
    val snappedOffset = ((offset + (snap / 2)) / snap) * snap
    return (valueRange.first + snappedOffset).coerceIn(valueRange.first, valueRange.last)
}

@Composable
private fun StarfieldOptionsContent(resetNonce: Int) {
    val prefs = LocalAppPreferences.current
    val d = AppDefaults.Visualization.Starfield
    var activePreset by remember(resetNonce) { mutableStateOf(starfieldActivePreset(prefs)) }
    val tune = starfieldPresetTuneFor(activePreset)
    val keys = starfieldKeysFor(activePreset)
    var beatFollowEnabled by remember(resetNonce) {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, d.beatFollowEnabled))
    }
    var monochromeBackdropEnabled by remember(resetNonce) {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_MONOCHROME_BACKDROP_ENABLED, d.monochromeBackdropEnabled))
    }
    var speedCenti by remember(activePreset, resetNonce) {
        mutableIntStateOf(prefs.getInt(keys.speedCenti, tune.speedCenti)
            .coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last))
    }
    var reactSpeedCenti by remember(activePreset, resetNonce) {
        mutableIntStateOf(prefs.getInt(keys.reactSpeedCenti, tune.reactSpeedCenti)
            .coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last))
    }
    var flashPercent by remember(activePreset, resetNonce) {
        mutableIntStateOf(prefs.getInt(keys.flashPercent, tune.flashPercent)
            .coerceIn(d.percentRange.first, d.percentRange.last))
    }
    var showPresetDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DialogValuePickerRow(
            title = "Flight preset",
            subtitle = "Preset slot under edit. Tuning below rewrites it.",
            value = activePreset.label,
            onClick = { showPresetDialog = true }
        )
        DialogToggleRow(
            title = "Follow the beat",
            subtitle = "Ride beat energy for speed and glow.",
            checked = beatFollowEnabled,
            onCheckedChange = { enabled ->
                beatFollowEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, enabled).apply()
            }
        )
        DialogToggleRow(
            title = "Monochrome backdrop",
            subtitle = "Fade fallback art to black and white while playing.",
            checked = monochromeBackdropEnabled,
            onCheckedChange = { enabled ->
                monochromeBackdropEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_MONOCHROME_BACKDROP_ENABLED, enabled).apply()
            }
        )
        StarfieldSheetSliderRow(
            title = "Flight speed",
            displayValue = String.format(Locale.US, "%.2f", speedCenti / 100f),
            value = speedCenti,
            valueRange = d.speedRangeCenti,
            step = 1,
            dragSnap = 2,
            onValueChange = { value ->
                val clamped = value.coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last)
                speedCenti = clamped
                prefs.edit().putInt(keys.speedCenti, clamped).apply()
            }
        )
        StarfieldSheetSliderRow(
            title = "Speed reaction",
            displayValue = String.format(Locale.US, "%.2f×", reactSpeedCenti / 100f),
            value = reactSpeedCenti,
            valueRange = d.reactSpeedRangeCenti,
            step = 5,
            dragSnap = 5,
            onValueChange = { value ->
                val clamped = value.coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last)
                reactSpeedCenti = clamped
                prefs.edit().putInt(keys.reactSpeedCenti, clamped).apply()
            }
        )
        StarfieldSheetSliderRow(
            title = "Brightness flash",
            displayValue = "$flashPercent%",
            value = flashPercent,
            valueRange = d.percentRange,
            step = 1,
            dragSnap = 1,
            onValueChange = { value ->
                val clamped = value.coerceIn(d.percentRange.first, d.percentRange.last)
                flashPercent = clamped
                prefs.edit().putInt(keys.flashPercent, clamped).apply()
            }
        )
    }

    if (showPresetDialog) {
        SettingsSingleChoiceDialog(
            title = "Flight preset",
            selectedValue = activePreset,
            options = StarfieldPreset.entries.map { preset ->
                ChoiceDialogOption(value = preset, label = preset.label)
            },
            onSelected = { preset ->
                activePreset = preset
                prefs.edit().putString(
                    AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET,
                    preset.storageValue
                ).apply()
                showPresetDialog = false
            },
            onDismiss = { showPresetDialog = false }
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
    TrackTextInputActive()
    val prefs = LocalAppPreferences.current
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
                    textAlign = TextAlign.Center,
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
        LaunchedEffect(searchQuery) {
            delay(300)
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
                val setId = presetSetIds.getOrElse(i) { splitPresetKey(presetKeys[i]).first }
                if (setId != lastSet) {
                    rows.add(PresetListRow.Header(setId, setLabels[setId] ?: setId))
                    lastSet = setId
                }
                rows.add(PresetListRow.Item(presetKeys[i]))
            }
            rows
        }
        val currentSetId = remember(currentPresetKey) {
            currentPresetKey?.let { splitPresetKey(it).first }
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
        LaunchedEffect(currentDisplayedIndex) {
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
