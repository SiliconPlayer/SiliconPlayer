package com.flopster101.siliconplayer.ui.visualization

import com.flopster101.siliconplayer.platform.AppPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.supportsChannelScopeVisualization
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isBasicVisualizationMode
import com.flopster101.siliconplayer.pluginNameForCoreName

internal data class VisualizationUiState(
    val mode: VisualizationMode,
    val enabledModes: Set<VisualizationMode>,
    val availableModes: List<VisualizationMode>,
    val onCycleMode: () -> Unit,
    val onSelectMode: (VisualizationMode) -> Unit,
    val onSetEnabledModes: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationUiState(
    prefs: AppPreferences,
    activeCoreName: String?,
    isPlayerSurfaceVisible: Boolean
): VisualizationUiState {
    val selectionState = rememberVisualizationSelectionState(prefs)
    val currentMode = selectionState.currentMode
    val enabledModes = selectionState.enabledModes
    val currentCorePluginName = pluginNameForCoreName(activeCoreName)

    LaunchedEffect(activeCoreName, currentCorePluginName, currentMode, isPlayerSurfaceVisible) {
        if (!supportsChannelScopeVisualization(activeCoreName)) {
            return@LaunchedEffect
        }
        val coreName = activeCoreName?.trim().takeIf { !it.isNullOrEmpty() } ?: return@LaunchedEffect
        val channelScopeActive =
            isPlayerSurfaceVisible && currentMode == VisualizationMode.ChannelScope
        NativeBridge.setCoreOption(
            coreName,
            "visualization.channel_scope_active",
            if (channelScopeActive) "true" else "false"
        )
    }

    val coordinator = rememberVisualizationModeCoordinator(
        enabledModes = enabledModes,
        activeCoreName = activeCoreName,
        currentMode = currentMode,
        requestedMode = selectionState.requestedMode,
        provideFallbackMode = {
            VisualizationMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_ADVANCED_FALLBACK_MODE,
                    VisualizationMode.Bars.storageValue
                )
            ).takeIf { it.isBasicVisualizationMode() } ?: VisualizationMode.Bars
        },
        onModeChanged = selectionState.onCurrentModeChanged,
        onRequestedModeChanged = selectionState.onRequestedModeChanged,
        onEnabledModesChanged = selectionState.onEnabledModesChanged
    )

    return remember(currentMode, enabledModes, coordinator) {
        VisualizationUiState(
            mode = currentMode,
            enabledModes = enabledModes,
            availableModes = coordinator.availableModes,
            onCycleMode = coordinator.onCycleMode,
            onSelectMode = coordinator.onSelectMode,
            onSetEnabledModes = coordinator.onSetEnabledModes
        )
    }
}
