package com.flopster101.siliconplayer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun rememberScrollStateScrollbarDragHandler(
    scrollState: ScrollState
): (Float) -> Unit {
    return remember(scrollState) {
        { requestedFraction ->
            val targetFraction = requestedFraction.coerceIn(0f, 1f)
            val targetScroll = (scrollState.maxValue.toFloat() * targetFraction).roundToInt()
            val delta = (targetScroll - scrollState.value).toFloat()
            if (delta != 0f) {
                scrollState.dispatchRawDelta(delta)
            }
        }
    }
}

@Composable
internal fun rememberLazyListScrollbarDragHandler(
    listState: LazyListState,
    totalItems: Int,
    visibleCount: Int,
    averageItemSizePx: Float
): (Float) -> Unit {
    return remember(listState, totalItems, visibleCount, averageItemSizePx) {
        { requestedFraction ->
            val clampedFraction = requestedFraction.coerceIn(0f, 1f)
            val maxFirstIndex = (totalItems - visibleCount).coerceAtLeast(0)
            val targetFirstIndex = maxFirstIndex.toFloat() * clampedFraction
            val targetIndex = floor(targetFirstIndex).toInt().coerceIn(0, maxFirstIndex)
            val targetOffsetPx = ((targetFirstIndex - targetIndex.toFloat()) * averageItemSizePx)
                .roundToInt()
                .coerceAtLeast(0)
            val currentAbsoluteScrollPx = (
                (listState.firstVisibleItemIndex.toFloat() * averageItemSizePx) +
                    listState.firstVisibleItemScrollOffset.toFloat()
                ).coerceAtLeast(0f)
            val targetAbsoluteScrollPx = (
                (targetIndex.toFloat() * averageItemSizePx) +
                    targetOffsetPx.toFloat()
                ).coerceAtLeast(0f)
            val delta = targetAbsoluteScrollPx - currentAbsoluteScrollPx
            if (delta != 0f) {
                listState.dispatchRawDelta(delta)
            }
        }
    }
}
