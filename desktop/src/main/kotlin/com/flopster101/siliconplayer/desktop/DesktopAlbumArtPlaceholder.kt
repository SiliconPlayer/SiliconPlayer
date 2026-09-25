package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.ArtworkSwipePreviewState
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationPerformanceMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import java.io.File

@Composable
internal fun AlbumArtPlaceholder(
    file: File?,
    isPlaying: Boolean,
    decoderName: String?,
    sampleRateHz: Int,
    artwork: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState = ArtworkSwipePreviewState(),
    placeholderIcon: ImageVector,
    visualizationModeBadgeText: String,
    showVisualizationModeBadge: Boolean,
    visualizationMode: VisualizationMode,
    visualizationPerformanceMode: VisualizationPerformanceMode = AppDefaults.Visualization.performanceMode,
    visualizationShowDebugInfo: Boolean,
    visualizationOscWindowMs: Int,
    visualizationOscTriggerModeNative: Int,
    visualizationOscFpsMode: VisualizationOscFpsMode,
    visualizationBarFpsMode: VisualizationOscFpsMode,
    visualizationVuFpsMode: VisualizationOscFpsMode,
    visualizationOscRenderBackend: VisualizationRenderBackend,
    visualizationBarSmoothingPercent: Int,
    visualizationVuSmoothingPercent: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barFrequencyGridEnabled: Boolean,
    barRenderBackend: VisualizationRenderBackend,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barContrastBackdropEnabled: Boolean,
    oscStereo: Boolean,
    oscLineWidthDp: Int,
    oscGridWidthDp: Int,
    oscVerticalGridEnabled: Boolean,
    oscCenterLineEnabled: Boolean,
    oscLineColorModeNoArtwork: VisualizationOscColorMode,
    oscGridColorModeNoArtwork: VisualizationOscColorMode,
    oscLineColorModeWithArtwork: VisualizationOscColorMode,
    oscGridColorModeWithArtwork: VisualizationOscColorMode,
    oscCustomLineColorArgb: Int,
    oscCustomGridColorArgb: Int,
    oscContrastBackdropEnabled: Boolean,
    vuAnchor: VisualizationVuAnchor,
    vuUseThemeColor: Boolean,
    vuRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    vuContrastBackdropEnabled: Boolean,
    channelScopePrefs: ChannelScopePrefs,
    starfieldPrefs: StarfieldPrefs,
    projectMRenderBackend: VisualizationRenderBackend = AppDefaults.Visualization.ProjectM.renderBackend,
    artworkCornerRadiusDp: Int = AppDefaults.Player.artworkCornerRadiusDp,
    enableSwipe: Boolean = true,
    onSwipePreviousTrack: () -> Unit = {},
    onSwipeNextTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Album Artwork",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = placeholderIcon,
                        contentDescription = "No album artwork",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showVisualizationModeBadge && visualizationMode != VisualizationMode.Off,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp),
                enter = fadeIn(animationSpec = tween(170)),
                exit = fadeOut(animationSpec = tween(260))
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (visualizationMode) {
                                VisualizationMode.Off -> Icons.Default.GraphicEq
                                VisualizationMode.Bars -> Icons.Default.GraphicEq
                                VisualizationMode.Oscilloscope -> Icons.Default.MonitorHeart
                                VisualizationMode.VuMeters -> Icons.Default.Equalizer
                                VisualizationMode.ChannelScope -> Icons.Default.MonitorHeart
                                VisualizationMode.Starfield -> Icons.Default.Star
                                VisualizationMode.ProjectM -> Icons.Default.AutoAwesome
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = visualizationModeBadgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
