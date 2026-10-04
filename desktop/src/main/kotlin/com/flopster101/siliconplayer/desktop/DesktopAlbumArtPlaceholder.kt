package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.ArtworkSwipePreviewState
import com.flopster101.siliconplayer.ChannelScopeVisibleElementId
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationPerformanceMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.isChannelScopeVisibleElementEnabled
import com.flopster101.siliconplayer.supportsChannelScopeNoteText
import com.flopster101.siliconplayer.desktop.DesktopChannelScopeNameSource
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.ui.visualization.artworkNeedsBlurFill
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.blurThumbPixels
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import com.flopster101.siliconplayer.ui.visualization.VisualizationDebugOverlay
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.loadChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlDesktopVisualization
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlFrame
import com.flopster101.siliconplayer.visualizationRenderBackendForMode
import java.io.File
import kotlinx.coroutines.delay

private fun resolveOscColor(
    hasArtwork: Boolean,
    noArtworkMode: VisualizationOscColorMode,
    withArtworkMode: VisualizationOscColorMode,
    monetColor: Color,
    customColor: Color
): Color {
    val mode = if (hasArtwork) withArtworkMode else noArtworkMode
    return when (mode) {
        VisualizationOscColorMode.Artwork -> monetColor
        VisualizationOscColorMode.Monet -> monetColor
        VisualizationOscColorMode.White -> Color.White
        VisualizationOscColorMode.Custom -> customColor
    }
}

private fun resolveChannelScopeTextPalette(
    mode: VisualizationChannelScopeTextColorMode,
    monetColor: Color,
    customColor: Color
): GlChannelScopeTextPalette {
    return when (mode) {
        VisualizationChannelScopeTextColorMode.Monet -> {
            val c = monetColor.toArgb()
            GlChannelScopeTextPalette(c, c, c, c, c, c)
        }
        VisualizationChannelScopeTextColorMode.White -> {
            val c = Color.White.toArgb()
            GlChannelScopeTextPalette(c, c, c, c, c, c)
        }
        VisualizationChannelScopeTextColorMode.Custom -> {
            val c = customColor.toArgb()
            GlChannelScopeTextPalette(c, c, c, c, c, c)
        }
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> {
            GlChannelScopeTextPalette(
                channelArgb = 0xFFBABDB6.toInt(),
                noteArgb = 0xFF729FCF.toInt(),
                volumeArgb = 0xFF8AE234.toInt(),
                effectArgb = 0xFFFCAF3E.toInt(),
                instrumentOrSampleArgb = 0xFFFFFFFF.toInt(),
                separatorArgb = 0xC6FFFFFF.toInt()
            )
        }
    }
}

private fun resolveChannelScopeVuColor(
    mode: VisualizationChannelScopeTextColorMode,
    monetColor: Color,
    customColor: Color
): Color {
    return when (mode) {
        VisualizationChannelScopeTextColorMode.Monet -> monetColor.copy(alpha = 0.92f)
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> Color(0xFF8AE234)
        VisualizationChannelScopeTextColorMode.White -> Color.White
        VisualizationChannelScopeTextColorMode.Custom -> customColor
    }
}

@Composable
private fun rememberArtworkBlurFill(artwork: ImageBitmap?): ImageBitmap? = remember(artwork) {
    // Same thumbnail bytes as the native GL renderer: ARGB ints in native
    // order are BGRA bytes on little-endian, which is every target here.
    val thumb = artwork?.blurThumbPixels() ?: return@remember null
    runCatching {
        val bytes = ByteArray(thumb.width * thumb.height * 4)
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().put(thumb.argb)
        val info = ImageInfo(thumb.width, thumb.height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        org.jetbrains.skia.Image.makeRaster(info, bytes, thumb.width * 4).toComposeImageBitmap()
    }.getOrNull()
}

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
    var scopeNameMaps by remember { mutableStateOf(ChannelScopeNameMaps()) }
    LaunchedEffect(file?.absolutePath, decoderName, visualizationMode) {
        scopeNameMaps = if (visualizationMode == VisualizationMode.ChannelScope) {
            runCatching {
                loadChannelScopeNameMaps(pluginNameForCoreName(decoderName), DesktopChannelScopeNameSource)
            }.getOrDefault(ChannelScopeNameMaps())
        } else {
            ChannelScopeNameMaps()
        }
    }
    var hasStartedPlaybackForTrack by remember { mutableStateOf(false) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            hasStartedPlaybackForTrack = true
        }
    }

    if (visualizationMode == VisualizationMode.Off || (file == null && !isPlaying) || !hasStartedPlaybackForTrack) {
        ElevatedCard(
            modifier = modifier,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    // Same fit-plus-blurred-fill as every other canvas; the
                    // user-chosen crop mode arrives as a later setting.
                    val blurFill = rememberArtworkBlurFill(artwork)
                    if (blurFill != null && artworkNeedsBlurFill(
                            artworkWidth = artwork.width,
                            artworkHeight = artwork.height,
                            canvasWidth = maxWidth.value,
                            canvasHeight = maxHeight.value
                        )
                    ) {
                        Image(
                            bitmap = blurFill,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium
                        )
                    }
                    Image(
                        bitmap = artwork,
                        contentDescription = "Album Artwork",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val minDim = if (maxWidth.isSpecified && maxHeight.isSpecified) minOf(maxWidth, maxHeight) else 0.dp
                    val circleRadius = if (minDim > 0.dp) {
                        minOf(60.dp, minDim * 0.35f)
                    } else {
                        36.dp
                    }
                    val circleDiameter = circleRadius * 2
                    val iconSize = minOf(64.dp, circleRadius * (64f / 60f))

                    Box(
                        modifier = Modifier
                            .size(circleDiameter)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = placeholderIcon,
                            contentDescription = "No album artwork",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }
            }
        }
        return
    }

    DisposableEffect(visualizationMode) {
        val scopeMounted = visualizationMode == VisualizationMode.ChannelScope
        if (scopeMounted) {
            NativeBridge.setChannelScopeVisualizerActive(true)
        }
        onDispose { NativeBridge.setChannelScopeVisualizerActive(false) }
    }

    val themePrimary = MaterialTheme.colorScheme.primary
    val themeTertiary = MaterialTheme.colorScheme.tertiary
    val themeSurface = MaterialTheme.colorScheme.surface
    val hasArtwork = artwork != null

    val vectorPainter = rememberVectorPainter(placeholderIcon)
    val placeholderIconImage = remember(placeholderIcon, themePrimary) {
        try {
            val sizePx = 256
            val bmp = ImageBitmap(sizePx, sizePx)
            val canvas = Canvas(bmp)
            val canvasDrawScope = CanvasDrawScope()
            canvasDrawScope.draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = canvas,
                size = Size(sizePx.toFloat(), sizePx.toFloat())
            ) {
                with(vectorPainter) {
                    draw(
                        size = size,
                        colorFilter = ColorFilter.tint(themePrimary)
                    )
                }
            }
            bmp
        } catch (_: Throwable) {
            null
        }
    }

    val barColor = if (barUseThemeColor) themePrimary.copy(alpha = 0.85f) else themeTertiary.copy(alpha = 0.85f)
    val oscColor = resolveOscColor(hasArtwork, oscLineColorModeNoArtwork, oscLineColorModeWithArtwork, themePrimary.copy(alpha = 0.92f), Color(oscCustomLineColorArgb))
    val gridColor = resolveOscColor(hasArtwork, oscGridColorModeNoArtwork, oscGridColorModeWithArtwork, themePrimary.copy(alpha = 0.34f), Color(oscCustomGridColorArgb))
    val vuColor = if (vuUseThemeColor) themePrimary.copy(alpha = 0.9f) else themeTertiary.copy(alpha = 0.9f)
    val vuLabelColor = vuColor
    val vuBackgroundColor = themeSurface.copy(alpha = 0.6f)

    val channelScopeLineColor = resolveOscColor(hasArtwork, channelScopePrefs.lineColorModeNoArtwork, channelScopePrefs.lineColorModeWithArtwork, themePrimary.copy(alpha = 0.92f), Color(channelScopePrefs.customLineColorArgb))
    val channelScopeGridColor = resolveOscColor(hasArtwork, channelScopePrefs.gridColorModeNoArtwork, channelScopePrefs.gridColorModeWithArtwork, themePrimary.copy(alpha = 0.34f), Color(channelScopePrefs.customGridColorArgb))
    val channelScopeTextPalette = resolveChannelScopeTextPalette(
        mode = channelScopePrefs.textColorMode,
        monetColor = themePrimary.copy(alpha = 0.92f),
        customColor = Color(channelScopePrefs.customTextColorArgb)
    )
    val channelScopeVuColor = resolveChannelScopeVuColor(
        mode = channelScopePrefs.textVuColorMode,
        monetColor = themePrimary.copy(alpha = 0.92f),
        customColor = Color(channelScopePrefs.textVuCustomColorArgb)
    )

    val nativeMode = when (visualizationMode) {
        VisualizationMode.Bars -> 1
        VisualizationMode.Oscilloscope -> 2
        VisualizationMode.VuMeters -> 3
        VisualizationMode.ChannelScope -> 4
        VisualizationMode.Starfield -> 5
        VisualizationMode.ProjectM -> 100
        VisualizationMode.Off -> 0
    }

    val primaryColorArgb = themePrimary.toArgb()
    val surfaceColorArgb = themeSurface.toArgb()

    val basicVisualizationMode =
        visualizationMode == VisualizationMode.Bars ||
            visualizationMode == VisualizationMode.Oscilloscope ||
            visualizationMode == VisualizationMode.VuMeters
    val basicVisualizationAlpha by animateFloatAsState(
        targetValue = if (!basicVisualizationMode || isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "basicVisualizationVisibility"
    )

    var starfieldPlaybackActive by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            starfieldPlaybackActive = true
        } else {
            delay(700)
            starfieldPlaybackActive = false
        }
    }
    val starfieldAlpha by animateFloatAsState(
        targetValue = if (starfieldPlaybackActive) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "starfieldPauseFade"
    )

    val visualAlpha = when (visualizationMode) {
        VisualizationMode.Starfield -> starfieldAlpha
        VisualizationMode.Bars,
        VisualizationMode.Oscilloscope,
        VisualizationMode.VuMeters -> basicVisualizationAlpha
        else -> 1f
    }

    val contrastMode = when (visualizationMode) {
        VisualizationMode.Bars -> if (barContrastBackdropEnabled) 1 else 0
        VisualizationMode.Oscilloscope -> if (!oscContrastBackdropEnabled) 0 else if (oscStereo) 3 else 2
        VisualizationMode.VuMeters -> if (!vuContrastBackdropEnabled) 0 else if (vuAnchor == VisualizationVuAnchor.Top) 4 else 5
        VisualizationMode.ChannelScope -> if (channelScopePrefs.contrastBackdropEnabled) 6 else 0
        VisualizationMode.Starfield -> if (starfieldPrefs.contrastBackdropEnabled) 7 else 0
        else -> 0
    }

    val monochromeBackdrop = when (visualizationMode) {
        VisualizationMode.Starfield -> starfieldPrefs.monochromeBackdrop && starfieldPlaybackActive
        else -> false
    }

    // Desktop density is a UI scale factor, not physical dpi: derive
    // pixels-per-dp from the display resolution, hairline-capped like mobile.
    val pixelsPerDp = remember {
        runCatching {
            (java.awt.Toolkit.getDefaultToolkit().screenResolution / 160f).coerceIn(
                AppDefaults.Visualization.tracePixelsPerDpMin,
                AppDefaults.Visualization.tracePixelsPerDpMax
            )
        }.getOrDefault(1f)
    }

    val glFrame = remember(
        visualizationMode,
        isPlaying,
        file?.absolutePath,
        barCount,
        visualizationBarSmoothingPercent,
        barColor,
        barRoundnessDp,
        barFrequencyGridEnabled,
        barOverlayArtwork,
        barContrastBackdropEnabled,
        oscStereo,
        visualizationOscWindowMs,
        visualizationOscTriggerModeNative,
        oscColor,
        gridColor,
        oscLineWidthDp,
        oscGridWidthDp,
        oscCenterLineEnabled,
        oscVerticalGridEnabled,
        oscContrastBackdropEnabled,
        vuAnchor,
        vuColor,
        vuBackgroundColor,
        vuLabelColor,
        vuContrastBackdropEnabled,
        channelScopePrefs,
        starfieldPrefs,
        primaryColorArgb,
        surfaceColorArgb,
        visualAlpha,
        contrastMode,
        monochromeBackdrop,
        pixelsPerDp,
        artwork,
        placeholderIconImage,
        scopeNameMaps
    ) {
        SiliconNativeGlFrame(
            mode = nativeMode,
            isPlaying = isPlaying,
            trackKey = file?.absolutePath,
            artworkImage = artwork,
            placeholderIconImage = placeholderIconImage,
            showArtworkBackground = when (visualizationMode) {
                VisualizationMode.Bars -> barOverlayArtwork
                VisualizationMode.ChannelScope -> channelScopePrefs.showArtworkBackground
                else -> true
            },
            primaryColorArgb = primaryColorArgb,
            surfaceColorArgb = surfaceColorArgb,
            monochromeBackdrop = monochromeBackdrop,
            visualAlpha = visualAlpha,
            contrastMode = contrastMode,
            contrastScrimColorArgb = 0xFF000000.toInt(),
            channelLayout = channelScopePrefs.layout.ordinal,
            textAnchor = channelScopePrefs.textAnchor.ordinal,
            vuAnchor = channelScopePrefs.textVuAnchor.ordinal,
            channelLayoutStrategy = channelScopePrefs.layout,
            channelTextAnchor = channelScopePrefs.textAnchor,
            channelVuAnchor = channelScopePrefs.textVuAnchor,
            channelScopeTextEnabled = channelScopePrefs.textEnabled,
            showChannel = channelScopePrefs.textShowChannel,
            showNote = channelScopePrefs.textShowNote && supportsChannelScopeNoteText(decoderName),
            showVolume = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Volume),
            showEffectPrimary = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.EffectPrimary),
            showEffectSecondary = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.EffectSecondary),
            showChip = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Chip),
            showInstrument = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Instrument),
            showSample = isChannelScopeVisibleElementEnabled(channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Sample),
            vuEnabled = channelScopePrefs.textVuEnabled,
            textSizeSp = channelScopePrefs.textSizeSp,
            textFont = channelScopePrefs.textFont,
            noteFormat = channelScopePrefs.textNoteFormat,
            paddingPx = channelScopePrefs.textPaddingDp.toFloat(),
            gridColorArgb = channelScopeGridColor.toArgb(),
            gridWidthPx = (channelScopePrefs.gridWidthDp * pixelsPerDp).coerceAtLeast(1f),
            lineColorArgb = channelScopeLineColor.toArgb(),
            lineWidthPx = (channelScopePrefs.lineWidthDp * pixelsPerDp).coerceAtLeast(1f),
            vuColorArgb = channelScopeVuColor.toArgb(),
            textPalette = channelScopeTextPalette,
            instrumentNamesByIndex = scopeNameMaps.instrumentNamesByIndex,
            sampleNamesByIndex = scopeNameMaps.sampleNamesByIndex,
            chipNamesByChannelIndex = scopeNameMaps.chipNamesByChannelIndex,
            shadowEnabled = channelScopePrefs.textShadowEnabled,
            hideWhenOverflow = channelScopePrefs.textHideWhenOverflow,
            channelScopeWindowMs = channelScopePrefs.windowMs,
            channelScopeGainPercent = channelScopePrefs.gainPercent,
            channelScopeDcRemovalEnabled = channelScopePrefs.dcRemovalEnabled,
            channelScopeWaveformClippingEnabled = channelScopePrefs.waveformClippingEnabled,
            channelScopeTriggerMode = channelScopePrefs.triggerModeNative,
            channelScopeWaveRenderMode = channelScopePrefs.waveRenderMode.nativeValue,
            channelScopeAntialiasMethod = channelScopePrefs.antialiasMethod.nativeValue,
            channelScopeTrackTransition = channelScopePrefs.trackTransition.nativeValue,
            oscStereo = oscStereo,
            oscWindowMs = visualizationOscWindowMs,
            oscTriggerMode = visualizationOscTriggerModeNative,
            oscWaveColorArgb = oscColor.toArgb(),
            oscLineWidthPx = (oscLineWidthDp * pixelsPerDp).coerceAtLeast(1f),
            oscGridColorArgb = gridColor.toArgb(),
            oscGridWidthPx = (oscGridWidthDp * pixelsPerDp).coerceAtLeast(1f),
            oscShowCenterLine = oscCenterLineEnabled,
            oscShowGrid = oscVerticalGridEnabled,
            barCount = barCount,
            barSmoothingPercent = visualizationBarSmoothingPercent,
            barStartColorArgb = barColor.toArgb(),
            barEndColorArgb = barColor.toArgb(),
            barCornerRadiusPx = barRoundnessDp.toFloat(),
            barShowFrequencyGuide = barFrequencyGridEnabled,
            barGuideColorArgb = gridColor.toArgb(),
            vuStereo = true,
            vuMetersAnchor = when (vuAnchor) {
                VisualizationVuAnchor.Top -> 0
                VisualizationVuAnchor.Center -> 1
                VisualizationVuAnchor.Bottom -> 2
            },
            vuSmoothingPercent = visualizationVuSmoothingPercent,
            vuFillColorArgb = vuColor.toArgb(),
            vuTrackColorArgb = vuBackgroundColor.toArgb(),
            vuLabelColorArgb = vuLabelColor.toArgb(),
            starfieldStarCount = starfieldPrefs.starCount,
            starfieldSpeed = starfieldPrefs.speed,
            starfieldFov = starfieldPrefs.fov,
            starfieldNearPlane = starfieldPrefs.nearPlane,
            starfieldStarColorArgb = starfieldPrefs.starColorArgb,
            starfieldBaseSizePx = starfieldPrefs.baseSizePx,
            starfieldSizeGrowth = starfieldPrefs.sizeGrowth,
            starfieldFarDim = starfieldPrefs.farDim,
            starfieldSoftness = starfieldPrefs.softness,
            starfieldBeatGlow = starfieldPrefs.beatGlow,
            starfieldGlowSize = starfieldPrefs.glowSize,
            starfieldTrailPersistence = starfieldPrefs.trailPersistence,
            starfieldStreaks = starfieldPrefs.streaks,
            starfieldStreakLength = starfieldPrefs.streakLength,
            starfieldCenterX = starfieldPrefs.centerX,
            starfieldCenterY = starfieldPrefs.centerY,
            starfieldAutoDrift = starfieldPrefs.autoDrift,
            starfieldBeatFollow = starfieldPrefs.beatFollow,
            starfieldReactSpeed = starfieldPrefs.reactSpeed,
            starfieldFlash = starfieldPrefs.flash,
            starfieldSquarePixels = starfieldPrefs.square
        )
    }

    var visDebugDrawFps by remember { mutableIntStateOf(0) }
    var visDebugDrawFrameMs by remember { mutableIntStateOf(0) }
    val activeRenderBackend = when (visualizationMode) {
        VisualizationMode.ChannelScope -> channelScopePrefs.renderBackend
        VisualizationMode.Oscilloscope -> visualizationOscRenderBackend
        VisualizationMode.Bars -> barRenderBackend
        VisualizationMode.VuMeters -> vuRenderBackend
        VisualizationMode.ProjectM -> projectMRenderBackend
        else -> visualizationRenderBackendForMode(visualizationMode)
    }

    val cardShape = RoundedCornerShape(artworkCornerRadiusDp.coerceIn(0, 48).dp)
    ElevatedCard(
        modifier = modifier.clip(cardShape),
        colors = CardDefaults.elevatedCardColors(
            containerColor = Color.Black
        ),
        shape = cardShape
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape),
            contentAlignment = Alignment.Center
        ) {
            SiliconNativeGlDesktopVisualization(
                frame = glFrame,
                modifier = Modifier.fillMaxSize(),
                onFrameStats = { fps, frameMs ->
                    visDebugDrawFps = fps
                    visDebugDrawFrameMs = frameMs
                }
            )

            if (visualizationShowDebugInfo && visualizationMode != VisualizationMode.Off) {
                VisualizationDebugOverlay(
                    visualizationMode = visualizationMode,
                    activeRenderBackend = activeRenderBackend,
                    drawFps = visDebugDrawFps,
                    drawFrameMs = visDebugDrawFrameMs,
                    modifier = Modifier.align(Alignment.TopStart)
                )
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
                                VisualizationMode.Off -> Icons.Default.VisibilityOff
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
