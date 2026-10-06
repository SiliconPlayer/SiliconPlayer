package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.abs

private val MiniPlayerBackdropScrimHeight = 48.dp
private val MiniPlayerBackdropScrimBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    1f to Color.Black.copy(alpha = 0.32f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoxScope.MiniPlayerOverlayHost(
    miniPlayerFocusRequester: FocusRequester,
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    miniExpandPreviewProgressProvider: () -> Float,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    miniExpandPreviewActive: Boolean,
    onMiniExpandPreviewActiveChanged: (Boolean) -> Unit,
    expandFromMiniDrag: Boolean,
    onExpandFromMiniDragChanged: (Boolean) -> Unit,
    onCollapseFromSwipeChanged: (Boolean) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    miniPreviewLiftPx: Float,
    selectedFile: File?,
    isPlaying: Boolean,
    playbackStartInProgress: Boolean,
    seekUiBusy: Boolean,
    durationSeconds: Double,
    positionSecondsState: State<Double>,
    metadataTitle: String,
    metadataArtist: String,
    metadataSampleRate: Int,
    metadataChannelCount: Int,
    metadataBitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    artworkBitmap: ImageBitmap?,
    isTrackFavorited: Boolean,
    activeRepeatMode: RepeatMode,
    playbackCapabilitiesFlags: Int,
    canOpenCurrentCoreSettings: Boolean,
    openCurrentCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    cycleVisualizationMode: () -> Unit,
    setVisualizationMode: (VisualizationMode) -> Unit,
    openVisualizationSettings: () -> Unit,
    openSelectedVisualizationSettings: () -> Unit,
    visualizationBarCount: Int,
    visualizationBarSmoothingPercent: Int,
    visualizationBarRoundnessDp: Int,
    visualizationBarOverlayArtwork: Boolean,
    visualizationBarUseThemeColor: Boolean,
    visualizationBarRenderBackend: VisualizationRenderBackend,
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    visualizationVuSmoothingPercent: Int,
    visualizationVuRenderBackend: VisualizationRenderBackend,
    visualizationShowDebugInfo: Boolean,
    playerArtworkCornerRadiusDp: Int,
    filenameDisplayMode: FilenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean,
    showMiniPlayerFocusHighlight: Boolean,
    onMiniPlayerNavigateUpRequested: () -> Unit,
    onMiniPlayerExpandRequested: () -> Unit,
    canResumeStoppedTrack: Boolean,
    onHidePlayerSurface: () -> Unit,
    onPreviousTrack: () -> Boolean,
    onForcePreviousTrack: () -> Boolean,
    onNextTrack: () -> Boolean,
    onPlayPause: () -> Unit,
    onStopAndClear: () -> Unit,
    onToggleFavoriteTrack: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    previousRestartsAfterThreshold: Boolean,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    currentSubtuneIndex: Int,
    subtuneCount: Int
) {
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val isRound = LocalConfiguration.current.isRoundScreenCompat
    var dragExpandCommitInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(isPlayerExpanded, miniExpandPreviewActive) {
        if (!isPlayerExpanded && !miniExpandPreviewActive) {
            dragExpandCommitInProgress = false
        }
    }
    // Single per-frame writer: the float feeds layer blocks only, while the
    // stable boolean is what composition reads. Same-value writes are free.
    val onPreviewProgress: (Float) -> Unit = {
        onMiniExpandPreviewProgressChanged(it)
        onMiniExpandPreviewActiveChanged(it > 0f)
    }

    AnimatedVisibility(
        visible = isPlayerSurfaceVisible && !isPlayerExpanded && !dragExpandCommitInProgress,
        enter = slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(durationMillis = 240)) + scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
        ),
        exit = if (dragExpandCommitInProgress || expandFromMiniDrag) {
            fadeOut(animationSpec = tween(1))
        } else {
            slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 230))
        },
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                horizontal = if (isWatch) (if (isRound) 8.dp else 6.dp) else 14.dp,
                vertical = if (isWatch) (if (isRound) 8.dp else 4.dp) else 6.dp
            )
    ) {
        val previousButtonFocusRequester = remember { FocusRequester() }
        val stopButtonFocusRequester = remember { FocusRequester() }
        val playPauseButtonFocusRequester = remember { FocusRequester() }
        val nextButtonFocusRequester = remember { FocusRequester() }
        var miniPlayerHasFocus by remember { mutableStateOf(false) }
        val miniPlayerUiScope = rememberCoroutineScope()
        val blockedDismissSettleOffset = remember { Animatable(0f) }
        var blockedDismissOffsetPx by remember { mutableFloatStateOf(0f) }
        var blockedDismissSettling by remember { mutableStateOf(false) }
        val blockedDismissMaxOffsetPx = with(LocalDensity.current) { 108.dp.toPx() }
        val miniPlayerFocusHighlight by animateFloatAsState(
            targetValue = if (miniPlayerHasFocus && showMiniPlayerFocusHighlight) 1f else 0f,
            animationSpec = tween(durationMillis = 180, easing = LinearOutSlowInEasing),
            label = "miniPlayerFocusHighlight"
        )
        var dismissOffsetPx by remember { mutableFloatStateOf(0f) }
        val dismissSettleOffset = remember { Animatable(0f) }
        var dismissSettling by remember { mutableStateOf(false) }
        var miniDismissWidthPx by remember { mutableFloatStateOf(0f) }
        // A paused hide is the dismiss fling: leave the offset past the screen edge
        // until the exit finishes or the bar re-shows, or it pops back to center.
        LaunchedEffect(isPlaying, isPlayerSurfaceVisible) {
            if (isPlayerSurfaceVisible || isPlaying) {
                blockedDismissSettling = false
                blockedDismissOffsetPx = 0f
                dismissSettling = false
                dismissOffsetPx = 0f
            }
        }
        val miniPlayerModifier = Modifier
            .graphicsLayer {
                val dragProgress = miniExpandPreviewProgressProvider().coerceIn(0f, 1f)
                val hideMini = miniPlayerHiddenForExpand(dragExpandCommitInProgress, expandFromMiniDrag, isPlayerExpanded)
                alpha = if (hideMini) 0f else (1f - dragProgress).coerceIn(0f, 1f)
                translationX = if (isPlaying) blockedDismissOffsetPx else dismissOffsetPx
                translationY = -miniPreviewLiftPx * dragProgress
            }
            .onFocusChanged { state -> miniPlayerHasFocus = state.hasFocus }
            .focusRequester(miniPlayerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionUp -> {
                        onMiniPlayerNavigateUpRequested()
                        true
                    }
                    Key.DirectionRight -> {
                        if (canPreviousTrack) {
                            previousButtonFocusRequester.requestFocus()
                        } else {
                            stopButtonFocusRequester.requestFocus()
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onMiniPlayerExpandRequested()
                        onCollapseFromSwipeChanged(false)
                        onExpandFromMiniDragChanged(miniExpandPreviewProgressProvider() > 0f)
                        onMiniExpandPreviewProgressChanged(0f)
                        onMiniExpandPreviewActiveChanged(false)
                        onPlayerExpandedChanged(true)
                        true
                    }
                    else -> false
                }
            }
            .then(
                if (isWatch) {
                    Modifier.clip(MaterialTheme.shapes.large)
                } else {
                    Modifier
                        .padding(top = MiniPlayerBackdropScrimHeight)
                        .background(MiniPlayerBackdropScrimBrush, MaterialTheme.shapes.large)
                }
            )
            .border(
                width = (1.4f * miniPlayerFocusHighlight).dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f * miniPlayerFocusHighlight),
                shape = MaterialTheme.shapes.large
            )
        val blockedDismissModifier = if (isPlaying) {
            Modifier.pointerInput(blockedDismissMaxOffsetPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (blockedDismissSettling) {
                            return@detectHorizontalDragGestures
                        }
                        blockedDismissOffsetPx =
                            (blockedDismissOffsetPx + dragAmount).coerceIn(
                                -blockedDismissMaxOffsetPx,
                                blockedDismissMaxOffsetPx
                            )
                        change.consume()
                    },
                    onDragEnd = {
                        val releaseOffset = blockedDismissOffsetPx
                        miniPlayerUiScope.launch {
                            blockedDismissSettling = true
                            blockedDismissSettleOffset.snapTo(releaseOffset)
                            blockedDismissSettleOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = LinearOutSlowInEasing
                                )
                            ) {
                                blockedDismissOffsetPx = value
                            }
                            blockedDismissSettling = false
                            blockedDismissOffsetPx = 0f
                        }
                    },
                    onDragCancel = {
                        val releaseOffset = blockedDismissOffsetPx
                        miniPlayerUiScope.launch {
                            blockedDismissSettling = true
                            blockedDismissSettleOffset.snapTo(releaseOffset)
                            blockedDismissSettleOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = LinearOutSlowInEasing
                                )
                            ) {
                                blockedDismissOffsetPx = value
                            }
                            blockedDismissSettling = false
                            blockedDismissOffsetPx = 0f
                        }
                    }
                )
            }
        } else {
            Modifier
        }
        val dismissModifier = if (!isPlaying) {
            Modifier.pointerInput(blockedDismissMaxOffsetPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        if (dismissSettling) {
                            return@detectHorizontalDragGestures
                        }
                        dismissOffsetPx += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dismissSettling) {
                            return@detectHorizontalDragGestures
                        }
                        val releaseOffset = dismissOffsetPx
                        miniPlayerUiScope.launch {
                            dismissSettling = true
                            dismissSettleOffset.snapTo(releaseOffset)
                            val dismissed =
                                abs(releaseOffset) >= blockedDismissMaxOffsetPx * 0.6f
                            if (dismissed) {
                                // Fling off-screen toward the dragged side; the visibility exit
                                // below only slides vertically, so hiding here would sink the bar.
                                val exitDistancePx =
                                    if (miniDismissWidthPx > 0f) miniDismissWidthPx
                                    else abs(releaseOffset) + 600f
                                dismissSettleOffset.animateTo(
                                    targetValue = if (releaseOffset < 0f) -exitDistancePx else exitDistancePx,
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        easing = LinearOutSlowInEasing
                                    )
                                ) {
                                    dismissOffsetPx = value
                                }
                                onHidePlayerSurface()
                            } else {
                                dismissSettleOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 220,
                                        easing = LinearOutSlowInEasing
                                    )
                                ) {
                                    dismissOffsetPx = value
                                }
                            }
                            dismissSettling = false
                        }
                    },
                    onDragCancel = {
                        val releaseOffset = dismissOffsetPx
                        miniPlayerUiScope.launch {
                            dismissSettling = true
                            dismissSettleOffset.snapTo(releaseOffset)
                            dismissSettleOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(
                                    durationMillis = 220,
                                    easing = LinearOutSlowInEasing
                                )
                            ) {
                                dismissOffsetPx = value
                            }
                            dismissSettling = false
                            dismissOffsetPx = 0f
                        }
                    }
                )
            }
        } else {
            Modifier
        }

        // Quantize the position handed to the mini player to whole seconds.
        // The mini progress indicator is only a few hundred pixels wide and
        // the time label is rendered at MM:SS precision, so anything finer
        // than 1 Hz is invisible. Snapping to integer seconds collapses the
        // ~5 Hz playback poll into ~1 Hz of meaningful change for child
        // composables (Text labels, LinearProgressIndicator), letting them
        // skip recomposition between integer-second transitions.
        val miniPlayerDisplayPositionSeconds by remember(positionSecondsState) {
            derivedStateOf { positionSecondsState.value.toLong().toDouble() }
        }
        val miniPlayerContent: @Composable () -> Unit = {
            val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(metadataTitle, selectedFile)
            val displayTitle = resolveMiniPlayerTitle(sanitizedTitle, selectedFile)
            val displayArtist = resolveMiniPlayerArtist(formatDisplayArtist(metadataArtist), selectedFile)
            if (isWatch) {
                WearMiniPlayerPill(
                    artwork = artworkBitmap,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                    isPlaying = isPlaying,
                    onExpand = {
                        onMiniPlayerExpandRequested()
                        onCollapseFromSwipeChanged(false)
                        onExpandFromMiniDragChanged(miniExpandPreviewProgressProvider() > 0f)
                        onMiniExpandPreviewProgressChanged(0f)
                        onMiniExpandPreviewActiveChanged(false)
                        onPlayerExpandedChanged(true)
                    },
                    onPlayPause = onPlayPause
                )
            } else {
                MiniPlayerBar(
                    file = selectedFile,
                    title = displayTitle,
                    artist = displayArtist,
                    metadataTitleResolved = sanitizedTitle.isNotBlank(),
                    artwork = artworkBitmap,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                    artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                    isPlaying = isPlaying,
                    playbackStartInProgress = playbackStartInProgress,
                    seekInProgress = seekUiBusy,
                    canResumeStoppedTrack = canResumeStoppedTrack,
                    positionSeconds = miniPlayerDisplayPositionSeconds,
                    durationSeconds = durationSeconds,
                    hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    canPreviousSubtune = canPreviousSubtune,
                    canNextSubtune = canNextSubtune,
                    currentSubtuneIndex = currentSubtuneIndex,
                    subtuneCount = subtuneCount,
                    onExpand = {
                        onMiniPlayerExpandRequested()
                        onCollapseFromSwipeChanged(false)
                        onExpandFromMiniDragChanged(miniExpandPreviewProgressProvider() > 0f)
                        onMiniExpandPreviewProgressChanged(0f)
                        onMiniExpandPreviewActiveChanged(false)
                        onPlayerExpandedChanged(true)
                    },
                    onExpandDragProgress = onPreviewProgress,
                    onExpandDragCommit = {
                        if (dragExpandCommitInProgress) {
                            return@MiniPlayerBar
                        }
                        dragExpandCommitInProgress = true
                        onMiniPlayerExpandRequested()
                        onExpandFromMiniDragChanged(true)
                        onCollapseFromSwipeChanged(false)
                        onPlayerExpandedChanged(true)
                        onMiniExpandPreviewProgressChanged(0f)
                        onMiniExpandPreviewActiveChanged(false)
                    },
                    onPreviousTrack = { onPreviousTrack(); Unit },
                    onForcePreviousTrack = { onForcePreviousTrack(); Unit },
                    onNextTrack = { onNextTrack(); Unit },
                    onPreviousSubtune = onPreviousSubtune,
                    onNextSubtune = onNextSubtune,
                    onPlayPause = onPlayPause,
                    onStopAndClear = onStopAndClear,
                    miniContainerFocusRequester = miniPlayerFocusRequester,
                    previousButtonFocusRequester = previousButtonFocusRequester,
                    stopButtonFocusRequester = stopButtonFocusRequester,
                    playPauseButtonFocusRequester = playPauseButtonFocusRequester,
                    nextButtonFocusRequester = nextButtonFocusRequester
                )
            }
        }

        if (isPlaying) {
            Box(
                modifier = miniPlayerModifier.then(blockedDismissModifier)
            ) {
                miniPlayerContent()
            }
        } else {
            Box(
                modifier = miniPlayerModifier
                    .then(dismissModifier)
                    .onSizeChanged { miniDismissWidthPx = it.width.toFloat() }
            ) {
                miniPlayerContent()
            }
        }
    }
}
