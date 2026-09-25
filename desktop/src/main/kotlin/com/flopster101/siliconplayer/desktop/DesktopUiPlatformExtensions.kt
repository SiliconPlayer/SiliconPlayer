package com.flopster101.siliconplayer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.flopster101.siliconplayer.platform.AudioOutputRouteInfo
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import java.io.File
import kotlin.math.roundToInt

@Composable
internal fun isWatchDevice(): Boolean = false

internal val isRoundScreenCompat: Boolean = false

@Composable
internal fun Modifier.onSizeChangedDeferred(onSizeChanged: (IntSize) -> Unit): Modifier {
    return onSizeChanged(onSizeChanged)
}

@Composable
internal fun Modifier.onGloballyPositionedDeferred(onPositioned: (LayoutCoordinates) -> Unit): Modifier {
    return onGloballyPositioned { coords ->
        if (coords.isAttached) onPositioned(coords)
    }
}

fun Modifier.tvKeyLongPress(onLongClick: (() -> Unit)?): Modifier = this

@Composable
internal fun WatchDialogContainer(
    title: String? = null,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
}

@Composable
internal fun adaptiveDialogModifier(): Modifier {
    val windowSize = LocalWindowSizeInfo.current
    val widthDp = windowSize.screenWidthDp
    val heightDp = windowSize.screenHeightDp
    val isLandscape = widthDp > heightDp

    if (!isLandscape) {
        return Modifier
            .fillMaxWidth(0.92f)
            .widthIn(max = 520.dp)
    }

    val landscapeWidthFraction = when {
        widthDp >= 1400 -> 0.50f
        widthDp >= 1100 -> 0.56f
        widthDp >= 840 -> 0.62f
        else -> 0.68f
    }
    val landscapeMaxWidth = when {
        widthDp >= 1400 -> 900.dp
        widthDp >= 1100 -> 860.dp
        else -> 800.dp
    }

    return Modifier
        .fillMaxWidth(landscapeWidthFraction)
        .widthIn(max = landscapeMaxWidth)
}

internal fun adaptiveDialogProperties(): DialogProperties {
    return DialogProperties(usePlatformDefaultWidth = false)
}


@Composable
internal fun VerticalScrollbarTrack(
    thumbFraction: Float,
    offsetFraction: Float,
    modifier: Modifier = Modifier,
    trackThickness: Dp = 4.dp,
    minThumbHeight: Dp = 18.dp,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    thumbColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
    onDragFractionChanged: ((Float) -> Unit)? = null,
    onDragActiveChanged: ((Boolean) -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier.width(trackThickness).fillMaxHeight()) {
        val trackHeight = maxHeight
        val thumbHeight = maxOf(minThumbHeight, trackHeight * thumbFraction.coerceIn(0f, 1f))
        val maxOffset = (trackHeight - thumbHeight).coerceAtLeast(0.dp)
        val thumbOffset = maxOffset * offsetFraction.coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(trackColor)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .offset(y = thumbOffset)
                .clip(RoundedCornerShape(999.dp))
                .background(thumbColor)
        )
    }
}

internal fun readCurrentFormatName(decoderName: String?): String? = null
