package com.flopster101.siliconplayer.ui.visualization

import com.flopster101.siliconplayer.platform.AppPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isBasicVisualizationMode
import com.flopster101.siliconplayer.parseEnabledVisualizationModes
import com.flopster101.siliconplayer.serializeEnabledVisualizationModes

internal data class VisualizationSelectionState(
    val currentMode: VisualizationMode,
    val requestedMode: VisualizationMode,
    val advancedFallbackMode: VisualizationMode,
    val enabledModes: Set<VisualizationMode>,
    val onCurrentModeChanged: (VisualizationMode) -> Unit,
    val onRequestedModeChanged: (VisualizationMode) -> Unit,
    val onAdvancedFallbackModeChanged: (VisualizationMode) -> Unit,
    val onEnabledModesChanged: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationSelectionState(
    prefs: AppPreferences
): VisualizationSelectionState {
    var currentMode by remember {
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
            )
        )
    }
    var requestedMode by remember {
        val storedCurrent = VisualizationMode.fromStorage(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
        )
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE,
                    storedCurrent.storageValue
                )
            )
        )
    }
    var advancedFallbackMode by remember {
        val stored = prefs.getString(
            AppPreferenceKeys.VISUALIZATION_ADVANCED_FALLBACK_MODE,
            VisualizationMode.Bars.storageValue
        )
        mutableStateOf(
            VisualizationMode.fromStorage(stored).takeIf { it != VisualizationMode.Off }
                ?: VisualizationMode.Bars
        )
    }
    var enabledModes by remember {
        val parsed = parseEnabledVisualizationModes(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, null)
        )
        // One-time migration: enable newly added modes for existing installs.
        val projectMMigrated = prefs.getBoolean(
            AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED,
            false
        )
        val starfieldMigrated = prefs.getBoolean(
            AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED,
            false
        )
        val migrated = when {
            projectMMigrated && starfieldMigrated -> parsed
            else -> {
                prefs.edit()
                    .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED, true)
                    .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED, true)
                    .apply()
                var result = parsed
                if (!projectMMigrated) result = result + VisualizationMode.ProjectM
                if (!starfieldMigrated) result = result + VisualizationMode.Starfield
                result
            }
        }
        mutableStateOf(migrated)
    }

    LaunchedEffect(currentMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_MODE, currentMode.storageValue)
            .apply()
    }

    LaunchedEffect(requestedMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE,
                requestedMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(advancedFallbackMode) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ADVANCED_FALLBACK_MODE,
                advancedFallbackMode.storageValue
            )
            .apply()
    }

    LaunchedEffect(enabledModes) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                serializeEnabledVisualizationModes(enabledModes)
            )
            .apply()
    }

    return remember(currentMode, requestedMode, advancedFallbackMode, enabledModes) {
        VisualizationSelectionState(
            currentMode = currentMode,
            requestedMode = requestedMode,
            advancedFallbackMode = advancedFallbackMode,
            enabledModes = enabledModes,
            onCurrentModeChanged = { currentMode = it },
            onRequestedModeChanged = { requestedMode = it },
            onAdvancedFallbackModeChanged = { advancedFallbackMode = it },
            onEnabledModesChanged = { enabledModes = it }
        )
    }
}
