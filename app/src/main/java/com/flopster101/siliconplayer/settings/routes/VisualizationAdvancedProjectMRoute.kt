package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VisualizationAdvancedProjectMRouteContent(
    onOpenPresetPacks: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val enabledSets = remember(context, prefs) { ProjectMPresetSets.enabledSets(context, prefs) }
    var presetCount by remember { mutableStateOf<Int?>(null) }
    var indexing by remember { mutableStateOf(false) }
    var randomStart by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)) }
    var presetDurationSeconds by remember {
        mutableStateOf(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString())?.toDoubleOrNull() ?: AppDefaults.Visualization.ProjectM.presetDurationSeconds)
    }
    var hardCutEnabled by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled)) }
    var hardCutSensitivity by remember { mutableStateOf(prefs.getFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity)) }
    var rotationRandom by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom)) }
    var meshSize by remember { mutableStateOf(prefs.getInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, AppDefaults.Visualization.ProjectM.meshSize)) }
    var aspectCorrection by remember { mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, AppDefaults.Visualization.ProjectM.aspectCorrection)) }
    var fpsMode by remember { mutableStateOf(VisualizationOscFpsMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, AppDefaults.Visualization.ProjectM.fpsMode.storageValue))) }
    var showDurationDialog by remember { mutableStateOf(false) }
    var showSensitivityDialog by remember { mutableStateOf(false) }
    var showMeshDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }

    fun scanCounts() {
        indexing = true
        scope.launch {
            val total = withContext(Dispatchers.Default) {
                enabledSets.sumOf { ProjectMPresetSets.presetCountForIndexed(context, it) }
            }
            presetCount = total
            indexing = false
        }
    }

    fun reindex() {
        indexing = true
        scope.launch {
            withContext(Dispatchers.Default) {
                try { ProjectMPresetSets.reindex(context, prefs) } catch (_: Throwable) { }
            }
            val total = withContext(Dispatchers.Default) {
                enabledSets.sumOf { ProjectMPresetSets.presetCountForIndexed(context, it) }
            }
            presetCount = total
            indexing = false
        }
    }

    LaunchedEffect(Unit) {
        scanCounts()
    }

    fun plural(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"

    val packsDescription = "${plural(enabledSets.size, "pack")} · ${presetCount?.let { plural(it, "preset") } ?: "… presets"}"
    val reindexDescription = if (indexing) "Re-indexing…" else "Rescan the enabled preset folders."

    Column {
        SettingsSectionLabel("Presets")
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Configure preset packs",
            description = packsDescription,
            icon = Icons.Default.FolderCopy,
            onClick = onOpenPresetPacks
        )
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Re-index presets",
            description = reindexDescription,
            icon = Icons.Default.Refresh,
            onClick = { reindex() }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Playback")
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Random preset on start",
            description = "Start with a random preset each time the visualizer opens.",
            checked = randomStart,
            onCheckedChange = { enabled ->
                randomStart = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, enabled).apply()
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Transitions")
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Preset duration",
            description = "Time before switching to the next preset.",
            value = "${presetDurationSeconds.toInt()}s",
            onClick = { showDurationDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Hard cut",
            description = "Allow hard cuts on beat. Sensitivity controls how easily they trigger.",
            checked = hardCutEnabled,
            onCheckedChange = { enabled ->
                hardCutEnabled = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetHardCutEnabled(enabled) } catch (_: Throwable) {}
            }
        )
        if (hardCutEnabled) {
            SettingsRowSpacer()
            SettingsValuePickerCard(
                title = "Hard cut sensitivity",
                description = "Beat sensitivity for hard cuts.",
                value = String.format(java.util.Locale.US, "%.1f", hardCutSensitivity),
                onClick = { showSensitivityDialog = true }
            )
        }
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Random rotation",
            description = "Pick the next preset randomly instead of sequentially.",
            checked = rotationRandom,
            onCheckedChange = { enabled ->
                rotationRandom = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetRotationRandom(enabled) } catch (_: Throwable) {}
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Rendering")
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Mesh size",
            description = "Resolution of the warp mesh.",
            value = "${meshSize}x${meshSize}",
            onClick = { showMeshDialog = true }
        )
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "Aspect correction",
            description = "Correct for non-square pixels.",
            checked = aspectCorrection,
            onCheckedChange = { enabled ->
                aspectCorrection = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, enabled).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetAspectCorrection(enabled) } catch (_: Throwable) {}
            }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Frame rate",
            description = "Target frame rate for projectM.",
            value = fpsMode.label,
            onClick = { showFpsDialog = true }
        )
    }
    if (showDurationDialog) {
        SteppedIntSliderDialog(
            title = "Preset duration",
            unitLabel = "s",
            range = 5..120,
            step = 1,
            currentValue = presetDurationSeconds.toInt(),
            onDismiss = { showDurationDialog = false },
            onConfirm = { v ->
                presetDurationSeconds = v.toDouble()
                prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, v.toDouble().toString()).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetPresetDuration(v.toDouble()) } catch (_: Throwable) {}
                showDurationDialog = false
            }
        )
    }
    if (showSensitivityDialog) {
        SteppedIntSliderDialog(
            title = "Hard cut sensitivity",
            unitLabel = "",
            range = 0..50,
            step = 1,
            currentValue = (hardCutSensitivity * 10).toInt(),
            valueLabelFormatter = { v -> String.format(java.util.Locale.US, "%.1f", v / 10.0) },
            onDismiss = { showSensitivityDialog = false },
            onConfirm = { v ->
                val f = v / 10.0f
                hardCutSensitivity = f
                prefs.edit().putFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, f).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetHardCutSensitivity(f) } catch (_: Throwable) {}
                showSensitivityDialog = false
            }
        )
    }
    if (showMeshDialog) {
        SettingsSingleChoiceDialog(
            title = "Mesh size",
            selectedValue = meshSize,
            options = listOf(32, 48, 64, 96, 128).map { s -> ChoiceDialogOption(value = s, label = "${s}x${s}") },
            onSelected = { s ->
                meshSize = s
                prefs.edit().putInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, s).apply()
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetMeshSize(s) } catch (_: Throwable) {}
                showMeshDialog = false
            },
            onDismiss = { showMeshDialog = false }
        )
    }
    if (showFpsDialog) {
        SettingsSingleChoiceDialog(
            title = "Frame rate",
            selectedValue = fpsMode,
            options = VisualizationOscFpsMode.entries.map { m -> ChoiceDialogOption(value = m, label = m.label) },
            onSelected = { m ->
                fpsMode = m
                prefs.edit().putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, m.storageValue).apply()
                val fps = when (m) {
                    VisualizationOscFpsMode.Default -> 35
                    VisualizationOscFpsMode.Fps60 -> 60
                    VisualizationOscFpsMode.NativeRefresh -> 0
                }
                try { com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge.nativeProjectMSetFps(fps) } catch (_: Throwable) {}
                showFpsDialog = false
            },
            onDismiss = { showFpsDialog = false }
        )
    }
}
