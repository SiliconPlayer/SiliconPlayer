package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.ExperimentalComposeUiApi
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import com.flopster101.siliconplayer.ui.screens.LocalPlayerFocusIndicatorsEnabled
import java.io.File
import android.view.MotionEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ExpandedPlayerOverlayHost(
    isPlayerSurfaceVisible: Boolean,
    isPlayerExpanded: Boolean,
    miniExpandPreviewProgress: Float,
    expandFromMiniDrag: Boolean,
    collapseFromSwipe: Boolean,
    onCollapseFromSwipeChanged: (Boolean) -> Unit,
    onCollapseDragProgressChanged: (Boolean) -> Unit,
    onExpandedOverlayCurrentVisibleChanged: (Boolean) -> Unit,
    onExpandedOverlaySettledVisibleChanged: (Boolean) -> Unit,
    onMiniExpandPreviewProgressChanged: (Float) -> Unit,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    screenHeightPx: Float,
    selectedFile: File?,
    isPlaying: Boolean,
    playbackStartInProgress: Boolean,
    canResumeStoppedTrack: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    durationSeconds: Double,
    positionSecondsState: State<Double>,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    title: String,
    artist: String,
    album: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    playbackSourceId: String? = null,
    playlistTitle: String?,
    playlistFormatLabel: String?,
    playlistTrackCount: Int,
    playlistPathOrUrl: String?,
    artworkBitmap: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState,
    isTrackFavorited: Boolean,
    repeatMode: RepeatMode,
    playbackCapabilitiesFlags: Int,
    seekInProgress: Boolean,
    previousRestartsAfterThreshold: Boolean,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    canOpenPlaylistSelector: Boolean,
    onOpenPlaylistSelector: () -> Unit,
    currentSubtuneIndex: Int,
    subtuneCount: Int,
    titleCurrentSubtuneIndex: Int,
    titleSubtuneCount: Int,
    subtuneTitleClickable: Boolean,
    onCycleRepeatMode: () -> Unit,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
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
    visualizationPerformanceMode: VisualizationPerformanceMode = AppDefaults.Visualization.performanceMode,
    visualizationShowDebugInfo: Boolean,
    artworkCornerRadiusDp: Int,
    showAudioOutputRouteChip: Boolean = AppDefaults.Player.showAudioOutputRouteChip,
    canvasTapToSeekSeconds: Int = AppDefaults.Player.canvasTapToSeekSeconds,
    onToggleFavoriteTrack: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    playlists: List<StoredPlaylist> = emptyList(),
    onAddToPlaylist: ((playlistId: String?, newTitle: String) -> Unit)? = null,
    onRemoveFromPlaylist: ((playlistId: String) -> Unit)? = null,
    filenameDisplayMode: FilenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean,
    externalTrackInfoDialogRequestToken: Int,
    bitPerfectUsbAudio: Boolean = false,
    onBitPerfectUsbAudioChanged: (Boolean) -> Unit = {},
    showFocusIndicators: Boolean,
    onHardwareNavigationInput: () -> Unit,
    onTouchInteraction: () -> Unit
) {
    val dragPreviewVisible =
        isPlayerSurfaceVisible && !isPlayerExpanded && miniExpandPreviewProgress > 0f
    val expandedOverlayVisible = isPlayerSurfaceVisible && isPlayerExpanded
    val overlayVisible = dragPreviewVisible || expandedOverlayVisible
    val noOp: () -> Unit = {}
    val noOpSeek: (Double) -> Unit = {}
    val noOpVisualizationModeSelect: (VisualizationMode) -> Unit = {}
    val expandedVisibilityState = remember { MutableTransitionState(false) }

    // Latches that the overlay was fully expanded, so a preview that never
    // got there does not earn the sequenced slide on its way out.
    var overlayWasExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(expandedOverlayVisible) {
        if (expandedOverlayVisible) overlayWasExpanded = true
    }
    LaunchedEffect(expandedVisibilityState.currentState, overlayVisible) {
        if (!expandedVisibilityState.currentState && !overlayVisible) overlayWasExpanded = false
    }

    // No hold and no content fade: the back exit veils the vis, then
    // slides the fully opaque panel off-screen. The exit transition below
    // only keeps the composition alive until the slide has parked the
    // surface past the display edge.
    LaunchedEffect(overlayVisible) {
        expandedVisibilityState.targetState = overlayVisible
    }
    LaunchedEffect(expandedVisibilityState.currentState) {
        onExpandedOverlayCurrentVisibleChanged(expandedVisibilityState.currentState)
    }
    LaunchedEffect(
        expandedVisibilityState.isIdle,
        expandedVisibilityState.currentState,
        expandedVisibilityState.targetState,
        expandedOverlayVisible
    ) {
        onExpandedOverlaySettledVisibleChanged(
            expandedVisibilityState.isIdle &&
                expandedVisibilityState.currentState &&
                expandedVisibilityState.targetState &&
                expandedOverlayVisible
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            onExpandedOverlayCurrentVisibleChanged(false)
            onExpandedOverlaySettledVisibleChanged(false)
        }
    }

    LaunchedEffect(isPlayerSurfaceVisible, isPlayerExpanded) {
        if (!isPlayerSurfaceVisible || !isPlayerExpanded) {
            onCollapseDragProgressChanged(false)
        }
    }

    // Drives the vis veil and the surface's own dimmer. The exit fall
    // spans most of the slide so the vis flattens gradually as the panel
    // travels instead of snapping to the veil color up front.
    val overlayVisibilityForVis = animateFloatAsState(
        targetValue = if (overlayVisible) 1f else 0f,
        animationSpec = if (overlayVisible) {
            tween(durationMillis = 250, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 180, easing = LinearEasing)
        },
        label = "playerOverlayVisibilityForVis"
    )
    val overlayVisibleAtoms = rememberUpdatedState(
        Triple(dragPreviewVisible, expandedOverlayVisible, miniExpandPreviewProgress.coerceIn(0f, 1f))
    )
    // Exit slide, in units of a third of the screen height; the panel is
    // fully below the display at 3. The bezier launches at roughly the
    // enter's launch speed so the gesture answers instantly, then keeps
    // accelerating gently past the display edge, so the exit's end still
    // releases the surface off-screen, where a released layer cannot
    // linger in the compositor and show its last buffer.
    val exitSlideArmed = overlayWasExpanded &&
        run {
            val (atomPreview, atomExpanded, _) = overlayVisibleAtoms.value
            !atomPreview && !atomExpanded
        }
    val exitSlideClock = remember { Animatable(0f) }
    LaunchedEffect(exitSlideArmed) {
        if (exitSlideArmed) {
            exitSlideClock.animateTo(
                4f,
                tween(
                    durationMillis = 280,
                    easing = CubicBezierEasing(0.2f, 0.15f, 0.7f, 0.6f)
                )
            )
        } else {
            exitSlideClock.snapTo(0f)
        }
    }
    val exitSlideFraction = if (exitSlideArmed) exitSlideClock.value else 0f
    val previewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
    val previewMode = !expandedOverlayVisible && previewProgress > 0f
    val previewOffsetPx = (1f - previewProgress) * screenHeightPx
    // The enter slide runs on a local clock instead of slideInVertically,
    // keeping it a plain translation on the host layer below: an embedded
    // surface follows layer translations through the interop offset, but
    // it could never follow the old scaleIn, so the scale is gone too.
    val enterSlideClock = remember { Animatable(1f) }
    val enterFromDrag = expandFromMiniDrag || dragPreviewVisible
    LaunchedEffect(overlayVisible, enterFromDrag) {
        if (overlayVisible && enterFromDrag) {
            enterSlideClock.snapTo(0f)
        } else if (overlayVisible && enterSlideClock.value > 0f) {
            enterSlideClock.animateTo(
                0f,
                animationSpec = tween(durationMillis = 320, easing = LinearOutSlowInEasing)
            )
        }
    }
    // Rearm only once fully hidden: during the exit the clock holds 0 so
    // the sequenced slide inside the player owns the travel alone.
    LaunchedEffect(expandedVisibilityState.currentState) {
        if (!expandedVisibilityState.currentState) enterSlideClock.snapTo(1f)
    }
    // Preview travel of the content, applied on the host layer below.
    // No scrim: the exit fades over the real content behind the player.
    // Stable instance: a fresh lambda per recomposition would re-trigger every reader.
    val overlayVisibilityProvider = remember(overlayVisibilityForVis) {
        {
            val (previewVisible, expandedVisible, previewProgress) = overlayVisibleAtoms.value
            if (previewVisible && !expandedVisible) previewProgress else overlayVisibilityForVis.value
        }
    }

    AnimatedVisibility(
        visibleState = expandedVisibilityState,
        enter = if (expandFromMiniDrag || dragPreviewVisible) {
            EnterTransition.None
        } else {
            fadeIn(animationSpec = tween(durationMillis = 240))
        },
        exit = if (collapseFromSwipe) {
            fadeOut(animationSpec = tween(1))
        } else if (dragPreviewVisible) {
            fadeOut(animationSpec = tween(1))
        } else {
            // No fade: the panel slides off fully opaque. The near-unity
            // target alpha only stretches the transition across the slide
            // so disposal (and the surface release with it) happens once
            // the panel has left the display.
            fadeOut(
                animationSpec = tween(durationMillis = 380, easing = LinearEasing),
                targetAlpha = 0.999f
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (previewMode) {
                        translationY = previewOffsetPx
                        alpha = previewProgress
                    } else {
                        translationY = enterSlideClock.value * screenHeightPx / 3f
                    }
                }
                .pointerInteropFilter { event ->
                    if (expandedOverlayVisible && event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onTouchInteraction()
                    }
                    false
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (!expandedOverlayVisible || keyEvent.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    if (
                        keyEvent.key == Key.DirectionLeft ||
                        keyEvent.key == Key.DirectionRight ||
                        keyEvent.key == Key.DirectionUp ||
                        keyEvent.key == Key.DirectionDown ||
                        keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter ||
                        keyEvent.key == Key.Tab
                    ) {
                        onHardwareNavigationInput()
                    }
                    false
                }
        ) {
            CompositionLocalProvider(
                LocalPlayerFocusIndicatorsEnabled provides showFocusIndicators,
                LocalPlayerOverlayVisibility provides overlayVisibilityProvider,
                LocalPlayerExitSlideFraction provides exitSlideFraction
            ) {
                PlayerScreen(
                    file = selectedFile,
                    onBack = if (expandedOverlayVisible) {
                        {
                            onCollapseFromSwipeChanged(false)
                            onCollapseDragProgressChanged(false)
                            onMiniExpandPreviewProgressChanged(0f)
                            onPlayerExpandedChanged(false)
                        }
                    } else {
                        noOp
                    },
                    onCollapseBySwipe = if (expandedOverlayVisible) {
                        {
                            onCollapseFromSwipeChanged(true)
                            onCollapseDragProgressChanged(false)
                            onMiniExpandPreviewProgressChanged(0f)
                            onPlayerExpandedChanged(false)
                        }
                    } else {
                        noOp
                    },
                    isPlaying = isPlaying,
                    canResumeStoppedTrack = if (expandedOverlayVisible) canResumeStoppedTrack else false,
                    onPlay = if (expandedOverlayVisible) onPlay else noOp,
                    onPause = if (expandedOverlayVisible) onPause else noOp,
                    onStopAndClear = if (expandedOverlayVisible) onStopAndClear else noOp,
                    durationSeconds = durationSeconds,
                    // Host does not subscribe to position ticks; leaf reads provider
                    // while the expanded overlay is composing.
                    positionSeconds = 0.0,
                    positionSecondsProvider = remember { { positionSecondsState.value } },
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    title = title,
                    artist = artist,
                    album = album,
                    sampleRateHz = sampleRateHz,
                    channelCount = channelCount,
                    bitDepthLabel = bitDepthLabel,
                    decoderName = decoderName,
                    playbackSourceLabel = playbackSourceLabel,
                    pathOrUrl = pathOrUrl,
                    playbackSourceId = playbackSourceId,
                    playlistTitle = playlistTitle,
                    playlistFormatLabel = playlistFormatLabel,
                    playlistTrackCount = playlistTrackCount,
                    playlistPathOrUrl = playlistPathOrUrl,
                    artwork = artworkBitmap,
                    artworkSwipePreviewState = artworkSwipePreviewState,
                    isTrackFavorited = isTrackFavorited,
                    noArtworkIcon = placeholderArtworkIconForFile(selectedFile, decoderName),
                    repeatMode = repeatMode,
                    canCycleRepeatMode = supportsLiveRepeatMode(playbackCapabilitiesFlags),
                    canSeek = canSeekPlayback(playbackCapabilitiesFlags),
                    hasReliableDuration = hasReliableDuration(playbackCapabilitiesFlags),
                    playbackStartInProgress = playbackStartInProgress,
                    seekInProgress = seekInProgress,
                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                    onSeek = if (expandedOverlayVisible) onSeek else noOpSeek,
                    onPreviousTrack = if (expandedOverlayVisible) onPreviousTrack else noOp,
                    onForcePreviousTrack = if (expandedOverlayVisible) onForcePreviousTrack else noOp,
                    onNextTrack = if (expandedOverlayVisible) onNextTrack else noOp,
                    onPreviousSubtune = if (expandedOverlayVisible) onPreviousSubtune else noOp,
                    onNextSubtune = if (expandedOverlayVisible) onNextSubtune else noOp,
                    onOpenSubtuneSelector = if (expandedOverlayVisible) onOpenSubtuneSelector else noOp,
                    canPreviousSubtune = if (expandedOverlayVisible) canPreviousSubtune else false,
                    canNextSubtune = if (expandedOverlayVisible) canNextSubtune else false,
                    canOpenSubtuneSelector = if (expandedOverlayVisible) canOpenSubtuneSelector else false,
                    canOpenPlaylistSelector = if (expandedOverlayVisible) canOpenPlaylistSelector else false,
                    onOpenPlaylistSelector = if (expandedOverlayVisible) onOpenPlaylistSelector else noOp,
                    currentSubtuneIndex = currentSubtuneIndex,
                    subtuneCount = subtuneCount,
                    titleCurrentSubtuneIndex = titleCurrentSubtuneIndex,
                    titleSubtuneCount = titleSubtuneCount,
                    subtuneTitleClickable = if (expandedOverlayVisible) subtuneTitleClickable else false,
                    onCycleRepeatMode = if (expandedOverlayVisible) onCycleRepeatMode else noOp,
                    canOpenCoreSettings = if (expandedOverlayVisible) canOpenCoreSettings else false,
                    onOpenCoreSettings = if (expandedOverlayVisible) onOpenCoreSettings else noOp,
                    visualizationMode = visualizationMode,
                    availableVisualizationModes = availableVisualizationModes,
                    onCycleVisualizationMode = if (expandedOverlayVisible) onCycleVisualizationMode else noOp,
                    onSelectVisualizationMode = if (expandedOverlayVisible) onSelectVisualizationMode else noOpVisualizationModeSelect,
                    onOpenVisualizationSettings = if (expandedOverlayVisible) onOpenVisualizationSettings else noOp,
                    onOpenSelectedVisualizationSettings = if (expandedOverlayVisible) onOpenSelectedVisualizationSettings else noOp,
                    visualizationBarCount = visualizationBarCount,
                    visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                    visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                    visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                    visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                    visualizationBarRenderBackend = visualizationBarRenderBackend,
                    visualizationOscStereo = visualizationOscStereo,
                    visualizationVuAnchor = visualizationVuAnchor,
                    visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                    visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                    visualizationVuRenderBackend = visualizationVuRenderBackend,
                    visualizationPerformanceMode = visualizationPerformanceMode,
                    visualizationShowDebugInfo = visualizationShowDebugInfo,
                    artworkCornerRadiusDp = artworkCornerRadiusDp,
                    canvasTapToSeekSeconds = canvasTapToSeekSeconds,
                    onToggleFavoriteTrack = if (expandedOverlayVisible) onToggleFavoriteTrack else noOp,
                    onOpenAudioEffects = if (expandedOverlayVisible) onOpenAudioEffects else noOp,
                    playlists = playlists,
                    onAddToPlaylist = if (expandedOverlayVisible) onAddToPlaylist else null,
                    onRemoveFromPlaylist = if (expandedOverlayVisible) onRemoveFromPlaylist else null,
                    showAudioOutputRouteChip = showAudioOutputRouteChip,
                    filenameDisplayMode = filenameDisplayMode,
                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                    externalTrackInfoDialogRequestToken = externalTrackInfoDialogRequestToken,
                    playbackCapabilitiesFlags = playbackCapabilitiesFlags,
                    bitPerfectUsbAudio = bitPerfectUsbAudio,
                    onBitPerfectUsbAudioChanged = onBitPerfectUsbAudioChanged,
                    onCollapseDragProgressChanged = onCollapseDragProgressChanged
                )
            }
        }
    }
}
