package com.flopster101.siliconplayer

// Drag-up expand preview shared by the Android overlay hosts and the desktop
// overlay wiring in Main.kt: while the finger drags the mini player upward,
// the expanded player is already visible, parked below the display edge by
// playerPreviewOffsetPx and faded to playerPreviewAlpha, so it follows the
// drag instead of flinging in after release.
internal fun playerDragPreviewVisible(
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    previewProgress: Float
): Boolean = isPlayerSurfaceVisible && !isPlayerExpanded && previewProgress > 0f

internal fun playerPreviewOffsetPx(previewProgress: Float, screenHeightPx: Float): Float =
    (1f - previewProgress.coerceIn(0f, 1f)) * screenHeightPx

internal fun playerPreviewAlpha(previewProgress: Float): Float =
    previewProgress.coerceIn(0f, 1f)

internal fun miniPlayerHiddenForExpand(
    dragExpandCommitInProgress: Boolean,
    expandFromMiniDrag: Boolean,
    isPlayerExpanded: Boolean
): Boolean = dragExpandCommitInProgress || expandFromMiniDrag || isPlayerExpanded
