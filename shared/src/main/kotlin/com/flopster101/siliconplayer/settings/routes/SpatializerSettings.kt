package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.audio.SpatializerBackend
import com.flopster101.siliconplayer.audio.SpatializerConfig
import com.flopster101.siliconplayer.ui.theme.Radius
import java.util.Locale
import kotlin.math.roundToInt

private fun spatializerBackendLabel(backend: SpatializerBackend): String = when (backend) {
    SpatializerBackend.Off -> "Off"
    SpatializerBackend.LibSpatialAudio -> "libspatialaudio"
    SpatializerBackend.SteamAudio -> "Steam Audio"
}

@Composable
internal fun SpatializerSettingsSection(
    config: SpatializerConfig,
    onConfigChanged: (SpatializerConfig) -> Unit
) {
    var backendDialogOpen by remember { mutableStateOf(false) }
    var backendEditor by remember { mutableStateOf<SpatializerBackend?>(null) }
    var sofaDialogOpen by remember { mutableStateOf(false) }

    SettingsSectionLabel("Spatial audio")
    SettingsValuePickerCard(
        title = "Spatializer backend",
        description = "Binaural rendering for multichannel audio decoded by FFmpeg.",
        value = spatializerBackendLabel(config.backend),
        onClick = { backendDialogOpen = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "libspatialaudio",
        description = "Configure virtual-speaker or Ambisonic rendering.",
        showChevron = true,
        onClick = { backendEditor = SpatializerBackend.LibSpatialAudio }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Steam Audio",
        description = "Configure HRTF virtual-surround rendering.",
        showChevron = true,
        onClick = { backendEditor = SpatializerBackend.SteamAudio }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Custom HRTF file",
        description = config.customSofaPath.ifEmpty { "Use the backend's built-in HRTF" },
        showChevron = true,
        onClick = { sofaDialogOpen = true }
    )
    SettingsRowSpacer()

    if (backendDialogOpen) {
        SettingsSingleChoiceDialog(
            title = "Spatializer backend",
            selectedValue = config.backend,
            options = SpatializerBackend.entries.map { backend ->
                ChoiceDialogOption(value = backend, label = spatializerBackendLabel(backend))
            },
            onSelected = { backend ->
                onConfigChanged(config.copy(backend = backend, enabled = backend != SpatializerBackend.Off))
            },
            onDismiss = { backendDialogOpen = false }
        )
    }

    backendEditor?.let { backend ->
        SpatializerBackendDialog(
            backend = backend,
            config = config,
            onDismiss = { backendEditor = null },
            onConfirm = onConfigChanged
        )
    }

    if (sofaDialogOpen) {
        SettingsTextInputDialog(
            title = "Custom SOFA HRTF file",
            fieldLabel = "Absolute file path",
            initialValue = config.customSofaPath,
            placeholder = "Leave empty for built-in HRTF",
            supportingText = "Both backends use this file when supported by the current sample rate.",
            onDismiss = { sofaDialogOpen = false },
            onConfirm = { path ->
                onConfigChanged(config.copy(customSofaPath = path.trim()))
                true
            }
        )
    }
}

@Composable
private fun SpatializerBackendDialog(
    backend: SpatializerBackend,
    config: SpatializerConfig,
    onDismiss: () -> Unit,
    onConfirm: (SpatializerConfig) -> Unit
) {
    var draft by remember(backend, config) { mutableStateOf(config.copy(spatializeStereo = false)) }
    val title = "${spatializerBackendLabel(backend)} settings"
    val content: @Composable () -> Unit = {
        if (backend == SpatializerBackend.LibSpatialAudio) {
            SpatializerRenderModeSelector(
                selected = draft.renderMethod,
                onSelected = { draft = draft.copy(renderMethod = it) }
            )
        }
        SpatializerGainSlider(
            label = "Center",
            value = draft.centerGainDb,
            onValueChange = { draft = draft.copy(centerGainDb = it) }
        )
        SpatializerGainSlider(
            label = "Surround",
            value = draft.surroundGainDb,
            onValueChange = { draft = draft.copy(surroundGainDb = it) }
        )
        SpatializerGainSlider(
            label = "Height",
            value = draft.heightGainDb,
            onValueChange = { draft = draft.copy(heightGainDb = it) }
        )
        SpatializerGainSlider(
            label = "LFE",
            value = draft.lfeGainDb,
            range = -12f..6f,
            onValueChange = { draft = draft.copy(lfeGainDb = it) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("LFE low-pass (120 Hz)", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = draft.lfeLowPassEnabled,
                onCheckedChange = { draft = draft.copy(lfeLowPassEnabled = it) }
            )
        }
        SpatializerGainSlider(
            label = "Wet mix",
            value = draft.wetMix,
            range = 0f..1f,
            step = 0.05f,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            onValueChange = { draft = draft.copy(wetMix = it) }
        )
    }

    if (isWatchDevice()) {
        WatchDialogContainer(title = title, onDismissRequest = onDismiss) {
            content()
            Button(
                onClick = {
                    onConfirm(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = Radius.Control
            ) {
                Text("Apply")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
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
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirm(draft)
                    onDismiss()
                }) {
                    Text("Apply")
                }
            }
        )
    }
}

@Composable
private fun SpatializerRenderModeSelector(
    selected: Int,
    onSelected: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Render mode", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selected == 0,
                onClick = { onSelected(0) },
                label = { Text("Virtual speakers") }
            )
            FilterChip(
                selected = selected == 1,
                onClick = { onSelected(1) },
                label = { Text("Ambisonics") }
            )
        }
    }
}

@Composable
private fun SpatializerGainSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
    step: Float = 1f,
    valueLabel: (Float) -> String = {
        String.format(Locale.US, "%+.0f dB", it)
    },
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { raw ->
                val snapped = ((raw / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
                onValueChange(snapped)
            },
            valueRange = range,
            steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
        )
    }
}
