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
    modifier: Modifier = Modifier
) {
    val pollerActive = activeRenderBackend == VisualizationRenderBackend.Compose
    val updateLine = if (pollerActive) {
        "$updateFps fps  ($updateFrameMs ms)"
    } else {
        "N/A"
    }
    val sourceUniqueLine = if (pollerActive) {
        "$sourceUniqueFps fps  ($sourceUniqueFrameMs ms)"
    } else {
        "N/A"
    }
    val sourceDuplicatesLine = if (pollerActive) {
        "$sourceDuplicatePercent%"
    } else {
        "N/A"
    }
    val drawLine = if (activeRenderBackend != VisualizationRenderBackend.Compose) {
        "$drawFps fps  ($drawFrameMs ms)"
    } else {
        "N/A"
    }
    Surface(
        modifier = modifier.padding(start = 10.dp, top = 10.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.22f)
    ) {
        Text(
            text = "Mode: ${visualizationMode.label}\n" +
                "Backend: ${activeRenderBackend.label}\n" +
                "Update: $updateLine\n" +
                "Source unique: $sourceUniqueLine\n" +
                "Source duplicates: $sourceDuplicatesLine\n" +
                "Draw: $drawLine",
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
