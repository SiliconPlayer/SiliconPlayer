package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo

@Composable
internal fun AboutLicenseTextDialog(
    entity: AboutEntity,
    text: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        modifier = adaptiveDialogModifier(),
        properties = adaptiveDialogProperties(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = entity.name,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            AboutLicenseTextContent(text = text)
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AboutLicenseTextContent(
    text: String
) {
    val configuration = LocalWindowSizeInfo.current
    val maxHeight = configuration.screenHeightDp.dp * 0.60f
    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = scrollState,
        label = "aboutLicenseScrollbarAlpha"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .onSizeChangedDeferred { viewportHeightPx = it.height }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 12.dp)
        )
        if (viewportHeightPx > 0 && scrollState.maxValue > 0) {
            val viewportHeightDp = with(density) { viewportHeightPx.toDp() }
            val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)
            val totalContentPx = viewportHeightPx + scrollState.maxValue
            val thumbFraction = if (totalContentPx <= 0) {
                1f
            } else {
                (viewportHeightPx.toFloat() / totalContentPx.toFloat()).coerceIn(0f, 1f)
            }
            val offsetFraction = if (scrollState.maxValue == 0) {
                0f
            } else {
                scrollState.value.toFloat() / scrollState.maxValue.toFloat()
            }
            VerticalScrollbarTrack(
                thumbFraction = thumbFraction,
                offsetFraction = offsetFraction,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(4.dp)
                    .height(viewportHeightDp)
                    .offset(x = (-2).dp)
                    .graphicsLayer(alpha = scrollbarAlpha),
                onDragFractionChanged = dragToFraction
            )
        }
    }
}
