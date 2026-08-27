package com.flopster101.siliconplayer.ui.screens

import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flopster101.siliconplayer.VisualizationFullscreenMode
import com.flopster101.siliconplayer.resolveEffectiveVisualizationFullscreenMode
import kotlinx.coroutines.delay

private val FullscreenScrim = Color.Black.copy(alpha = 0.28f)

@Composable
internal fun FullscreenToggleAffordance(
    onToggle: () -> Unit,
    show: Boolean
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier.size(40.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Enter fullscreen",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FullscreenTransportControls(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        FilledTonalIconButton(
            onClick = onPreviousTrack,
            enabled = canPreviousTrack,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(24.dp))
        }

        FilledIconButton(
            onClick = { if (isPlaying) onPause() else onPlay() },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }

        FilledTonalIconButton(
            onClick = onNextTrack,
            enabled = canNextTrack,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun FullscreenSeekBar(
    positionSeconds: Double,
    durationSeconds: Double,
    canSeek: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationSeconds <= 0.0) return
    if (!canSeek) {
        val progress = (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f)
        )
        return
    }

    var sliderPosition by remember(positionSeconds, durationSeconds) { mutableStateOf(positionSeconds) }
    var isSeeking by remember { mutableStateOf(false) }
    val displayPos = if (isSeeking) sliderPosition else positionSeconds
    LineageStyleSeekBar(
        value = displayPos.toFloat(),
        maxValue = durationSeconds.toFloat(),
        enabled = true,
        seekInProgress = isSeeking,
        layoutScale = 0.9f,
        activeColor = Color.White,
        inactiveColor = Color.White.copy(alpha = 0.30f),
        thumbColor = Color.White,
        forceMonochromeWhite = true,
        onSeekInteractionChanged = {},
        onValueChange = { v ->
            isSeeking = true
            sliderPosition = v.toDouble()
        },
        onValueChangeFinished = {
            isSeeking = false
            onSeek(sliderPosition)
        },
        modifier = modifier.fillMaxWidth().height(36.dp)
    )
}

@Composable
private fun FullscreenBottomControls(
    displayTitle: String,
    displayArtist: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    canSeek: Boolean,
    onSeek: (Double) -> Unit,
    effectiveMode: VisualizationFullscreenMode
) {
    val scrimModifier = Modifier
        .fillMaxWidth()
        .background(FullscreenScrim)
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp)

    when (effectiveMode) {
        VisualizationFullscreenMode.SuperCompact -> Unit

        VisualizationFullscreenMode.Compact -> {
            Column(
                modifier = scrimModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (displayArtist.isNotBlank()) "$displayTitle — $displayArtist" else displayTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                FullscreenTransportControls(
                    isPlaying = isPlaying,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    modifier = Modifier.fillMaxWidth()
                )
                if (durationSeconds > 0.0) {
                    val progress = (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        VisualizationFullscreenMode.Complete -> {
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            Column(
                modifier = scrimModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLandscape) {
                    Text(
                        text = if (displayArtist.isNotBlank()) "$displayTitle — $displayArtist" else displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayArtist.isNotBlank()) {
                        Text(
                            text = displayArtist,
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FullscreenSeekBar(
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    canSeek = canSeek,
                    onSeek = onSeek
                )
                Spacer(modifier = Modifier.height(8.dp))
                FullscreenTransportControls(
                    isPlaying = isPlaying,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun FullscreenVisualizationOverlay(
    isFullscreen: Boolean,
    onExitFullscreen: () -> Unit,
    visualizationContent: @Composable () -> Unit,
    displayTitle: String,
    displayArtist: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    canSeek: Boolean = true,
    onSeek: (Double) -> Unit = {},
    fullscreenModePref: VisualizationFullscreenMode,
    modifier: Modifier = Modifier
) {
    if (!isFullscreen) return
    val context = LocalContext.current
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val effectiveMode = resolveEffectiveVisualizationFullscreenMode(fullscreenModePref, isWatch)
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(controlsVisible, isFullscreen) {
        if (controlsVisible && isFullscreen) {
            delay(3000)
            controlsVisible = false
        }
    }

    BackHandler(enabled = isFullscreen) {
        onExitFullscreen()
    }

    DisposableEffect(isFullscreen) {
        val window = (context as? Activity)?.window
        if (window != null) {
            if (isFullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView)?.show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
        onDispose {
            val w = (context as? Activity)?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                WindowCompat.getInsetsController(w, w.decorView)?.show(
                    WindowInsetsCompat.Type.systemBars()
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            visualizationContent()
        }

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FullscreenScrim)
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                VisualizationFullscreenMode.Compact,
                VisualizationFullscreenMode.Complete -> {
                    FullscreenBottomControls(
                        displayTitle = displayTitle,
                        displayArtist = displayArtist,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        onSeek = onSeek,
                        effectiveMode = effectiveMode
                    )
                }
            }
        }
    }
}
