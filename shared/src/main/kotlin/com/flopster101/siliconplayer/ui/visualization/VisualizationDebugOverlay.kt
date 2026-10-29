package com.flopster101.siliconplayer.ui.visualization

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationRenderBackend

@Composable
fun VisualizationDebugOverlay(
    visualizationMode: VisualizationMode,
    activeRenderBackend: VisualizationRenderBackend,
    updateFps: Int = 0,
    updateFrameMs: Int = 0,
    sourceUniqueFps: Int = 0,
    sourceUniqueFrameMs: Int = 0,
    sourceDuplicatePercent: Int = 0,
    drawFps: Int = 0,
    drawFrameMs: Int = 0,
    vulkanDeviceLine: String? = null,
    modifier: Modifier = Modifier
) {
    val pollerActive = activeRenderBackend == VisualizationRenderBackend.Compose
    val drawAvailable = activeRenderBackend != VisualizationRenderBackend.Compose
    val vulkanLine = if (activeRenderBackend == VisualizationRenderBackend.VulkanSurface) {
        (vulkanDeviceLine ?: "unknown device") + "\n"
    } else {
        ""
    }
    Surface(
        modifier = modifier.padding(start = 10.dp, top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.22f)
    ) {
        Text(
            text = buildList {
                add("Mode: ${visualizationMode.label}")
                add("Backend: ${activeRenderBackend.label}")
                if (pollerActive) {
                    add("Update: $updateFps fps  ($updateFrameMs ms)")
                    add("Source unique: $sourceUniqueFps fps  ($sourceUniqueFrameMs ms)")
                    add("Source duplicates: $sourceDuplicatePercent%")
                }
                if (drawAvailable) {
                    add("Draw: $drawFps fps")
                    add("Frame interval: ${if (drawFps > 0) "%.2f".format(1000f / drawFps) else "—"} ms")
                    add("Render call: $drawFrameMs ms")
                }
                vulkanLine.trimEnd().takeIf { it.isNotEmpty() }?.let(::add)
            }.joinToString("\n"),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
