package com.flopster101.siliconplayer.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.SubtuneTransportState
import com.flopster101.siliconplayer.VisualizationFullscreenMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.resolveEffectiveVisualizationFullscreenMode
import kotlinx.coroutines.delay


@Composable
internal fun FullscreenVisualizationOverlay(
    isFullscreen: Boolean,
    onExitFullscreen: () -> Unit,
    visualizationContent: @Composable () -> Unit,
    displayTitle: String,
    displayArtist: String,
    album: String = "",
    subtuneBadge: String? = null,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSwipePreviousTrack: () -> Unit = onPreviousTrack,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    subtuneTransport: SubtuneTransportState = SubtuneTransportState(),
    onPreviousSubtune: () -> Unit = {},
    onNextSubtune: () -> Unit = {},
    onRestartCurrentSelection: () -> Unit = {},
    positionSecondsProvider: () -> Double,
    durationSeconds: Double,
    canSeek: Boolean = true,
    onSeek: (Double) -> Unit = {},
    repeatMode: RepeatMode = RepeatMode.None,
    onStopAndClear: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    canCycleRepeatMode: Boolean = false,
    visualizationMode: VisualizationMode = VisualizationMode.Off,
    availableVisualizationModes: List<VisualizationMode> = emptyList(),
    onCycleVisualizationMode: () -> Unit = {},
    onSelectVisualizationMode: (VisualizationMode) -> Unit = {},
    onVisualizerAction: () -> Unit = {},
    fullscreenModePref: VisualizationFullscreenMode,
    hasReliableDuration: Boolean = true,
    tickerEnabled: Boolean = false,
    tickerDurationSeconds: Int = 5,
    tickerTrackKey: String? = null,
    tickerFormatLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (!isFullscreen) return
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val effectiveMode = resolveEffectiveVisualizationFullscreenMode(fullscreenModePref, isWatch)
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var fullscreenSwipeDelta by remember { mutableFloatStateOf(0f) }
    val fullscreenSwipeThresholdPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp * 0.32f).dp.toPx()
    }

    LaunchedEffect(controlsVisible, isFullscreen, controlsInteractionTick) {
        if (controlsVisible && isFullscreen) {
            delay(3000)
            controlsVisible = false
        }
    }

    BackHandler(enabled = isFullscreen) {
        onExitFullscreen()
    }

    DisposableEffect(isFullscreen, context) {
        val window = (context as? Activity)?.window
        if (window != null) {
            if (isFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = window.attributes
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = attrs
                }
                WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                // Force insets re-dispatch so the layout expands into the
                // cutout immediately instead of on an arbitrary relayout.
                window.decorView.requestApplyInsets()
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                window.decorView.requestApplyInsets()
                WindowCompat.getInsetsController(window, window.decorView)?.show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
        onDispose {
            val w = (context as? Activity)?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = w.attributes
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    w.attributes = attrs
                }
                WindowCompat.getInsetsController(w, w.decorView)?.apply {
                    show(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                }
                // The window must re-measure to the system-fits layout before the
                // insets re-dispatch, else it stays edge-to-edge and every other
                // screen reads the stale system-bar insets.
                w.decorView.requestLayout()
                w.decorView.post { w.decorView.requestApplyInsets() }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(canPreviousTrack, canNextTrack, onSwipePreviousTrack, onNextTrack) {
                detectHorizontalDragGestures(
                    onDragStart = { fullscreenSwipeDelta = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        fullscreenSwipeDelta += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            fullscreenSwipeDelta <= -fullscreenSwipeThresholdPx && canNextTrack -> {
                                onNextTrack()
                            }
                            fullscreenSwipeDelta >= fullscreenSwipeThresholdPx && canPreviousTrack -> {
                                onSwipePreviousTrack()
                            }
                        }
                        fullscreenSwipeDelta = 0f
                    },
                    onDragCancel = { fullscreenSwipeDelta = 0f }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            visualizationContent()
        }

        FullscreenTrackTicker(
            trackKey = tickerTrackKey,
            title = displayTitle,
            artist = displayArtist,
            album = album,
            subtuneBadge = subtuneBadge,
            formatLabel = tickerFormatLabel,
            trackDurationSeconds = durationSeconds,
            trackDurationReliable = hasReliableDuration,
            holdSeconds = tickerDurationSeconds,
            enabled = tickerEnabled && !isWatch,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(12.dp)
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
        ) {
            Surface(
                onClick = onExitFullscreen,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit fullscreen",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            when (effectiveMode) {
                VisualizationFullscreenMode.SuperCompact -> {
                    FullscreenTransportControls(
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        positionSecondsProvider = positionSecondsProvider,
                        subtuneTransport = subtuneTransport,
                        onPreviousSubtune = onPreviousSubtune,
                        onNextSubtune = onNextSubtune,
                        onRestartCurrentSelection = onRestartCurrentSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FullscreenBottomScrimBrush)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                VisualizationFullscreenMode.Compact,
                VisualizationFullscreenMode.Complete -> {
                    FullscreenBottomControls(
                        displayTitle = displayTitle,
                        displayArtist = displayArtist,
                        album = album,
                        subtuneBadge = subtuneBadge,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        subtuneTransport = subtuneTransport,
                        onPreviousSubtune = onPreviousSubtune,
                        onNextSubtune = onNextSubtune,
                        onRestartCurrentSelection = onRestartCurrentSelection,
                        positionSecondsProvider = positionSecondsProvider,
                        durationSeconds = durationSeconds,
                        durationReliable = hasReliableDuration,
                        canSeek = canSeek,
                        onSeek = onSeek,
                        repeatMode = repeatMode,
                        onStopAndClear = onStopAndClear,
                        onCycleRepeatMode = onCycleRepeatMode,
                        canCycleRepeatMode = canCycleRepeatMode,
                        effectiveMode = effectiveMode,
                        switcherContent = {
                            FullscreenVisualizerSwitcher(
                                visualizationMode = visualizationMode,
                                availableVisualizationModes = availableVisualizationModes,
                                onCycleVisualizationMode = onCycleVisualizationMode,
                                onSelectVisualizationMode = onSelectVisualizationMode,
                                onVisualizerAction = onVisualizerAction,
                                onInteraction = { controlsInteractionTick++ },
                                compact = effectiveMode == VisualizationFullscreenMode.Compact
                            )
                        }
                    )
                }
            }
        }
    }
}
