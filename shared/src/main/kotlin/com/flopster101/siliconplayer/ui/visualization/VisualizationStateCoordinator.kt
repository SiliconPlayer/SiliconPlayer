package com.flopster101.siliconplayer.ui.visualization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isAdvancedVisualizationMode
import com.flopster101.siliconplayer.isVisualizationModeSelectable
import com.flopster101.siliconplayer.selectableVisualizationModes

internal data class VisualizationModeCoordinator(
    val availableModes: List<VisualizationMode>,
    val onCycleMode: () -> Unit,
    val onSelectMode: (VisualizationMode) -> Unit,
    val onSetEnabledModes: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationModeCoordinator(
    enabledModes: Set<VisualizationMode>,
    activeCoreName: String?,
    currentMode: VisualizationMode,
    requestedMode: VisualizationMode,
    provideFallbackMode: () -> VisualizationMode,
    onModeChanged: (VisualizationMode) -> Unit,
    onRequestedModeChanged: (VisualizationMode) -> Unit,
    onEnabledModesChanged: (Set<VisualizationMode>) -> Unit
): VisualizationModeCoordinator {
    val availableModes = remember(enabledModes, activeCoreName) {
        listOf(VisualizationMode.Off) + selectableVisualizationModes.filter { mode ->
            isVisualizationModeSelectable(mode, enabledModes, activeCoreName)
        }
    }

    val resolvedMode = remember(enabledModes, activeCoreName, requestedMode, provideFallbackMode) {
        resolveVisualizationMode(
            requestedMode = requestedMode,
            fallbackMode = provideFallbackMode(),
            enabledModes = enabledModes,
            activeCoreName = activeCoreName
        )
    }

    LaunchedEffect(resolvedMode, currentMode) {
        if (currentMode != resolvedMode) {
            onModeChanged(resolvedMode)
        }
    }

    return remember(
        availableModes,
        enabledModes,
        activeCoreName,
        currentMode,
        requestedMode,
        provideFallbackMode,
        onModeChanged,
        onRequestedModeChanged,
        onEnabledModesChanged
    ) {
        VisualizationModeCoordinator(
            availableModes = availableModes,
            onCycleMode = {
                val currentIndex = availableModes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
                val requested = availableModes[(currentIndex + 1) % availableModes.size]
                onRequestedModeChanged(requested)
            },
            onSelectMode = { requestedMode ->
                if (availableModes.contains(requestedMode)) {
                    onRequestedModeChanged(requestedMode)
                }
            },
            onSetEnabledModes = { requested ->
                val normalized = requested.intersect(selectableVisualizationModes.toSet())
                onEnabledModesChanged(normalized)
                val requestedAfterNormalization = when {
                    requestedMode == VisualizationMode.Off -> VisualizationMode.Off
                    normalized.contains(requestedMode) -> requestedMode
                    currentMode != VisualizationMode.Off &&
                        currentMode != requestedMode &&
                        isVisualizationModeSelectable(currentMode, normalized, activeCoreName) -> currentMode
                    else -> resolveVisualizationMode(
                        requestedMode = requestedMode,
                        fallbackMode = provideFallbackMode(),
                        enabledModes = normalized,
                        activeCoreName = activeCoreName
                    )
                }
                if (requestedAfterNormalization != requestedMode) {
                    onRequestedModeChanged(requestedAfterNormalization)
                }
            }
        )
    }
}

private fun resolveVisualizationMode(
    requestedMode: VisualizationMode,
    fallbackMode: VisualizationMode,
    enabledModes: Set<VisualizationMode>,
    activeCoreName: String?
): VisualizationMode {
    if (requestedMode == VisualizationMode.Off) {
        return VisualizationMode.Off
    }
    if (isVisualizationModeSelectable(requestedMode, enabledModes, activeCoreName)) {
        return requestedMode
    }
    return fallbackMode.takeIf {
        isVisualizationModeSelectable(it, enabledModes, activeCoreName)
    } ?: VisualizationMode.Off
}
