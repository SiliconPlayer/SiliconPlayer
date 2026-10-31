package com.flopster101.siliconplayer.ui.visualization.basic

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.visualization.gl.BarsGlTextureVisualization
import com.flopster101.siliconplayer.ui.visualization.gl.OscilloscopeGlTextureVisualization
import com.flopster101.siliconplayer.ui.visualization.gl.VuMetersGlTextureVisualization
import kotlin.math.max
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.NativeBridge
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.flopster101.siliconplayer.R

private data class NativeVisSpec(
    val backend: VisualizationRenderBackend,
    val frame: com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlFrame,
    val cornerRadiusDp: Int
)

@Composable
fun BasicVisualizationOverlay(
    mode: VisualizationMode,
    bars: FloatArray,
    waveformLeft: FloatArray,
    waveformRight: FloatArray,
    vuLevels: FloatArray,
    channelCount: Int,
    barCount: Int,
    barRoundnessDp: Int,
    barOverlayArtwork: Boolean,
    barUseThemeColor: Boolean,
    barFrequencyGridEnabled: Boolean,
    barSampleRateHz: Int,
    barRenderBackend: VisualizationRenderBackend,
    barSmoothingPercent: Int = 50,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barContrastBackdropEnabled: Boolean = true,
    oscStereo: Boolean,
    oscRenderBackend: VisualizationRenderBackend,
    oscWindowMs: Int = 30,
    oscTriggerMode: Int = 0,
    artwork: ImageBitmap?,
    oscLineWidthDp: Int,
    oscGridWidthDp: Int,
    oscVerticalGridEnabled: Boolean,
    oscCenterLineEnabled: Boolean,
    oscContrastBackdropEnabled: Boolean = true,
    oscLineColorModeNoArtwork: VisualizationOscColorMode,
    oscGridColorModeNoArtwork: VisualizationOscColorMode,
    oscLineColorModeWithArtwork: VisualizationOscColorMode,
    oscGridColorModeWithArtwork: VisualizationOscColorMode,
    oscCustomLineColorArgb: Int,
    oscCustomGridColorArgb: Int,
    vuAnchor: VisualizationVuAnchor,
    vuUseThemeColor: Boolean,
    vuRenderBackend: VisualizationRenderBackend,
    vuSmoothingPercent: Int = 50,
    vuContrastBackdropEnabled: Boolean = true,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    channelScopeInstrumentNamesByIndex: Map<Int, String>,
    channelScopeSampleNamesByIndex: Map<Int, String>,
    channelScopeChipNamesByChannelIndex: Map<Int, String>,
    channelScopeTriggerModeNative: Int,
    channelScopeTriggerAlgorithmNative: Int = 0,
    channelScopeWaveRenderModeNative: Int = 1,
    channelScopeAntialiasMethodNative: Int = 0,
    // Track transition: 0 instant, 1 slide-fade reveal, 2 crossfade
    channelScopeTrackTransition: Int = 1,
    channelScopeTriggerIndices: IntArray,
    channelScopeWindowMs: Int = 30,
    channelScopeGainPercent: Int = 100,
    channelScopeDcRemovalEnabled: Boolean = true,
    channelScopeWaveformClippingEnabled: Boolean = true,
    channelScopeRenderBackend: VisualizationRenderBackend,
    channelScopeLineWidthDp: Int,
    channelScopeGridWidthDp: Int,
    channelScopeVerticalGridEnabled: Boolean,
    channelScopeCenterLineEnabled: Boolean,
    channelScopeContrastBackdropEnabled: Boolean = true,
    channelScopeLayout: VisualizationChannelScopeLayout,
    channelScopeLineColorModeNoArtwork: VisualizationOscColorMode,
    channelScopeGridColorModeNoArtwork: VisualizationOscColorMode,
    channelScopeLineColorModeWithArtwork: VisualizationOscColorMode,
    channelScopeGridColorModeWithArtwork: VisualizationOscColorMode,
    channelScopeCustomLineColorArgb: Int,
    channelScopeCustomGridColorArgb: Int,
    channelScopeBackgroundColorArgb: Int,
    channelScopeTextEnabled: Boolean,
    channelScopeTextAnchor: VisualizationChannelScopeTextAnchor,
    channelScopeTextPaddingDp: Int,
    channelScopeTextSizeSp: Int,
    channelScopeTextHideWhenOverflow: Boolean,
    channelScopeTextShadowEnabled: Boolean,
    channelScopeTextFont: VisualizationChannelScopeTextFont,
    channelScopeTextColorMode: VisualizationChannelScopeTextColorMode,
    channelScopeCustomTextColorArgb: Int,
    channelScopeTextNoteFormat: VisualizationNoteNameFormat,
    channelScopeTextShowChannel: Boolean,
    channelScopeTextShowNote: Boolean,
    channelScopeTextShowVolume: Boolean,
    channelScopeTextShowEffectPrimary: Boolean,
    channelScopeTextShowEffectSecondary: Boolean,
    channelScopeTextShowChip: Boolean,
    channelScopeTextShowInstrument: Boolean,
    channelScopeTextShowSample: Boolean,
    channelScopeTextVuEnabled: Boolean,
    channelScopeTextVuAnchor: VisualizationVuAnchor,
    channelScopeTextVuColorMode: VisualizationChannelScopeTextColorMode,
    channelScopeTextVuCustomColorArgb: Int,
    channelScopeCornerRadiusDp: Int = 0,
    starfieldRenderBackend: VisualizationRenderBackend = VisualizationRenderBackend.OpenGlTexture,
    projectMRenderBackend: VisualizationRenderBackend = VisualizationRenderBackend.OpenGlTexture,
    starfieldStarCount: Int = 350,
    starfieldSpeed: Float = 0.30f,
    starfieldFov: Float = 1.0f,
    starfieldNearPlane: Float = 0.06f,
    starfieldStarColorArgb: Int = 0xFFFFFFFF.toInt(),
    starfieldSquarePixels: Boolean = false,
    starfieldBaseSizePx: Float = 6.0f,
    starfieldSizeGrowth: Float = 1.2f,
    starfieldFarDim: Float = 0.6f,
    starfieldSoftness: Float = 0.25f,
    starfieldBeatGlow: Float = 0.0f,
    starfieldGlowSize: Float = 3.0f,
    starfieldTrailPersistence: Float = 0.55f,
    starfieldStreaks: Boolean = false,
    starfieldStreakLength: Float = 1.0f,
    starfieldCenterX: Float = 0f,
    starfieldCenterY: Float = 0f,
    starfieldAutoDrift: Boolean = false,
    starfieldBeatFollow: Boolean = false,
    starfieldReactSpeed: Float = 0.6f,
    starfieldFlash: Float = 0.1f,
    starfieldContrastBackdropEnabled: Boolean = true,
    starfieldMonochromeBackdrop: Boolean = true,
    // A SurfaceView is its own layer, so the artwork card's Compose clip does
    // not reach it; the backend masks its corners in GL instead.
    visCornerRadiusDp: Int = 0,
    // Backdrop the Surface backend cross-fades against while the overlay fades.
    surfaceVeilColor: Color = Color.Black,
    placeholderIcon: androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.material.icons.Icons.Default.MusicNote,
    placeholderIconResId: Int = R.drawable.ic_placeholder_music_note,
    showArtworkBackground: Boolean = true,
    isPlaying: Boolean = true,
    trackKey: String? = null,
    channelScopeOnFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    visualAlpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (mode == VisualizationMode.Off) return

    val monetBarColor = if (barUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
    }
    val monetOscLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    val monetOscGridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
    val artworkBaseColor = remember(artwork) {
        extractArtworkColorProfile(artwork)?.let(::deriveReadableArtworkVisualizationColor)
    } ?: monetOscLineColor
    val artworkGridColor = artworkBaseColor.copy(alpha = 0.34f)
    val hasArtwork = artwork != null
    val barCustomColor = Color(barCustomColorArgb)
    val customLineColor = Color(oscCustomLineColorArgb)
    val customGridColor = Color(oscCustomGridColorArgb)
    val barColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = barColorModeNoArtwork,
        withArtworkMode = barColorModeWithArtwork,
        artworkColor = artworkBaseColor.copy(alpha = monetBarColor.alpha),
        monetColor = monetBarColor,
        customColor = barCustomColor.copy(alpha = monetBarColor.alpha)
    )
    val oscColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = oscLineColorModeNoArtwork,
        withArtworkMode = oscLineColorModeWithArtwork,
        artworkColor = artworkBaseColor,
        monetColor = monetOscLineColor,
        customColor = customLineColor
    )
    val oscGridColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = oscGridColorModeNoArtwork,
        withArtworkMode = oscGridColorModeWithArtwork,
        artworkColor = artworkGridColor,
        monetColor = monetOscGridColor,
        customColor = customGridColor
    )
    val monetVuColor = if (vuUseThemeColor) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f)
    }
    val vuCustomColor = Color(vuCustomColorArgb)
    val vuAccentColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = vuColorModeNoArtwork,
        withArtworkMode = vuColorModeWithArtwork,
        artworkColor = artworkBaseColor.copy(alpha = monetVuColor.alpha),
        monetColor = monetVuColor,
        customColor = vuCustomColor.copy(alpha = monetVuColor.alpha)
    )
    val vuColor = vuAccentColor
    val vuLabelColor = deriveVuLabelColor(vuAccentColor)
    val vuBackgroundColor = deriveVuTrackColor(vuAccentColor)
    val channelScopeCustomLineColor = Color(channelScopeCustomLineColorArgb)
    val channelScopeCustomGridColor = Color(channelScopeCustomGridColorArgb)
    val channelScopeCustomTextColor = Color(channelScopeCustomTextColorArgb)
    val channelScopeVuCustomColor = Color(channelScopeTextVuCustomColorArgb)
    val channelScopeCornerRadiusShape = RoundedCornerShape(channelScopeCornerRadiusDp.coerceIn(0, 48).dp)
    val channelScopeCornerRadiusPx = with(LocalDensity.current) {
        channelScopeCornerRadiusDp.coerceIn(0, 48).dp.toPx()
    }
    val channelScopeLineColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = channelScopeLineColorModeNoArtwork,
        withArtworkMode = channelScopeLineColorModeWithArtwork,
        artworkColor = artworkBaseColor,
        monetColor = monetOscLineColor,
        customColor = channelScopeCustomLineColor
    )
    val channelScopeGridColor = resolveOscColor(
        hasArtwork = hasArtwork,
        noArtworkMode = channelScopeGridColorModeNoArtwork,
        withArtworkMode = channelScopeGridColorModeWithArtwork,
        artworkColor = artworkGridColor,
        monetColor = monetOscGridColor,
        customColor = channelScopeCustomGridColor
    )
    val channelScopeTextPalette = resolveChannelScopeTextPalette(
        mode = channelScopeTextColorMode,
        monetColor = monetOscLineColor,
        customColor = channelScopeCustomTextColor
    )
    val channelScopeVuColor = resolveChannelScopeVuColor(
        mode = channelScopeTextVuColorMode,
        monetColor = monetOscLineColor,
        customColor = channelScopeVuCustomColor
    )
    val barBackgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    // Traces are hairlines: widen on sparse screens, never past 1px per dp.
    val density = LocalDensity.current.density.coerceIn(
        AppDefaults.Visualization.tracePixelsPerDpMin,
        AppDefaults.Visualization.tracePixelsPerDpMax
    )
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val visUsesArtworkColor = when (mode) {
        VisualizationMode.Bars -> barColorModeWithArtwork == VisualizationOscColorMode.Artwork
        VisualizationMode.Oscilloscope -> oscLineColorModeWithArtwork == VisualizationOscColorMode.Artwork
        VisualizationMode.VuMeters -> vuColorModeWithArtwork == VisualizationOscColorMode.Artwork
        VisualizationMode.ChannelScope -> channelScopeLineColorModeWithArtwork == VisualizationOscColorMode.Artwork
        else -> false
    }
    val themeIsLight = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    // Dark scrim whenever real artwork with From-artwork colors is shown (the vis
    // may already be light); otherwise follow the UI theme's dark/light mode.
    val contrastScrimColor = if (hasArtwork && visUsesArtworkColor) {
        Color.Black
    } else {
        if (themeIsLight) Color.White else Color.Black
    }

    val placeholderIconType = when (placeholderIconResId) {
        R.drawable.ic_placeholder_tracker_chip -> 2
        R.drawable.ic_placeholder_gamepad -> 3
        else -> 1
    }

    val baseNativeFrame = remember(
        isPlaying,
        trackKey,
        visualAlpha,
        primaryColor,
        surfaceVariantColor,
        contrastScrimColor,
        placeholderIconType,
        placeholderIconResId,
        artwork,
        density
    ) {
        com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlFrame(
            mode = 0,
            isPlaying = isPlaying,
            trackKey = trackKey,
            primaryColorArgb = primaryColor.toArgb(),
            surfaceColorArgb = surfaceVariantColor.toArgb(),
            contrastScrimColorArgb = contrastScrimColor.toArgb(),
            placeholderIconType = placeholderIconType,
            placeholderIconResId = placeholderIconResId,
            artworkBitmap = artwork?.asAndroidBitmap(),
            showArtworkBackground = true,
            visualAlpha = visualAlpha
        )
    }

    // One shared slot for every native view: switching modes on the same
    // backend only swaps the frame, so the SurfaceView/TextureView (and its
    // window) survives the switch instead of tearing down and rebuilding.
    Box(
        modifier = if (mode == VisualizationMode.ChannelScope) {
            modifier.clip(channelScopeCornerRadiusShape)
        } else {
            modifier
        }
    ) {
        val nativeSpec: NativeVisSpec? = when (mode) {
            VisualizationMode.Bars -> {
                if (barRenderBackend == VisualizationRenderBackend.Compose) {
                    null
                } else {
                    val barContrastMode = if (barContrastBackdropEnabled) 1 else 0
                    val nativeFrame = baseNativeFrame.copy(
                        mode = 1,
                        surfaceColorArgb = if (barOverlayArtwork) surfaceVariantColor.toArgb() else barBackgroundColor.toArgb(),
                        barCount = barCount,
                        barSmoothingPercent = barSmoothingPercent,
                        barStartColorArgb = barColor.toArgb(),
                        barEndColorArgb = barColor.toArgb(),
                        barCornerRadiusPx = barRoundnessDp.toFloat(),
                        barShowFrequencyGuide = barFrequencyGridEnabled,
                        barGuideColorArgb = barColor.copy(alpha = 0.25f).toArgb(),
                        contrastMode = barContrastMode,
                        showArtworkBackground = barOverlayArtwork
                    )
                    NativeVisSpec(
                        backend = barRenderBackend,
                        frame = nativeFrame,
                        cornerRadiusDp = visCornerRadiusDp
                    )
                }
            }

            VisualizationMode.Oscilloscope -> {
                if (oscRenderBackend == VisualizationRenderBackend.Compose) {
                    null
                } else {
                    val isStereo = oscStereo && channelCount > 1
                    val oscContrastMode = if (!oscContrastBackdropEnabled) {
                        0
                    } else if (isStereo) {
                        3
                    } else {
                        2
                    }
                    val nativeFrame = baseNativeFrame.copy(
                        mode = 2,
                        oscStereo = isStereo,
                        oscWindowMs = oscWindowMs,
                        oscTriggerMode = oscTriggerMode,
                        oscWaveColorArgb = oscColor.toArgb(),
                        oscLineWidthPx = (oscLineWidthDp * density).coerceAtLeast(1f),
                        oscGridColorArgb = oscGridColor.toArgb(),
                        oscGridWidthPx = (oscGridWidthDp * density).coerceAtLeast(1f),
                        oscShowCenterLine = oscCenterLineEnabled,
                        oscShowGrid = oscVerticalGridEnabled,
                        contrastMode = oscContrastMode,
                        showArtworkBackground = true
                    )
                    NativeVisSpec(
                        backend = oscRenderBackend,
                        frame = nativeFrame,
                        cornerRadiusDp = visCornerRadiusDp
                    )
                }
            }

            VisualizationMode.VuMeters -> {
                if (vuRenderBackend == VisualizationRenderBackend.Compose) {
                    null
                } else {
                    val isTop = vuAnchor == VisualizationVuAnchor.Top
                    val vuContrastMode = if (!vuContrastBackdropEnabled) {
                        0
                    } else if (isTop) {
                        4
                    } else {
                        5
                    }
                    val nativeFrame = baseNativeFrame.copy(
                        mode = 3,
                        vuStereo = channelCount > 1,
                        vuMetersAnchor = when (vuAnchor) {
                            VisualizationVuAnchor.Top -> 0
                            VisualizationVuAnchor.Center -> 1
                            VisualizationVuAnchor.Bottom -> 2
                        },
                        vuSmoothingPercent = vuSmoothingPercent,
                        vuFillColorArgb = vuColor.toArgb(),
                        vuTrackColorArgb = vuBackgroundColor.toArgb(),
                        vuLabelColorArgb = vuLabelColor.toArgb(),
                        contrastMode = vuContrastMode,
                        showArtworkBackground = true
                    )
                    NativeVisSpec(
                        backend = vuRenderBackend,
                        frame = nativeFrame,
                        cornerRadiusDp = visCornerRadiusDp
                    )
                }
            }

            VisualizationMode.ChannelScope -> {
                val channelScopeContrastMode = if (channelScopeContrastBackdropEnabled) 6 else 0
                val paddingPx = with(LocalDensity.current) { channelScopeTextPaddingDp.dp.toPx() }

                val layoutInt = when (channelScopeLayout) {
                    VisualizationChannelScopeLayout.BalancedTwoColumn -> 2
                    VisualizationChannelScopeLayout.ColumnFirst -> 0
                }
                val anchorInt = when (channelScopeTextAnchor) {
                    VisualizationChannelScopeTextAnchor.TopLeft -> 0
                    VisualizationChannelScopeTextAnchor.TopCenter -> 1
                    VisualizationChannelScopeTextAnchor.TopRight -> 2
                    VisualizationChannelScopeTextAnchor.BottomLeft -> 3
                    VisualizationChannelScopeTextAnchor.BottomCenter -> 4
                    VisualizationChannelScopeTextAnchor.BottomRight -> 5
                }
                val vuAnchorInt = if (channelScopeTextVuAnchor == VisualizationVuAnchor.Top) 0 else 1
                val nativeFrame = baseNativeFrame.copy(
                    mode = 4,
                    surfaceColorArgb = channelScopeBackgroundColorArgb,
                    instrumentNamesByIndex = channelScopeInstrumentNamesByIndex,
                    sampleNamesByIndex = channelScopeSampleNamesByIndex,
                    chipNamesByChannelIndex = channelScopeChipNamesByChannelIndex,
                    channelLayout = layoutInt,
                    textAnchor = anchorInt,
                    vuAnchor = vuAnchorInt,
                    channelLayoutStrategy = channelScopeLayout,
                    channelTextAnchor = channelScopeTextAnchor,
                    channelVuAnchor = channelScopeTextVuAnchor,
                    channelScopeTextEnabled = channelScopeTextEnabled,
                    showChannel = channelScopeTextShowChannel,
                    showNote = channelScopeTextShowNote,
                    showVolume = channelScopeTextShowVolume,
                    showEffectPrimary = channelScopeTextShowEffectPrimary,
                    showEffectSecondary = channelScopeTextShowEffectSecondary,
                    showChip = channelScopeTextShowChip,
                    showInstrument = channelScopeTextShowInstrument,
                    showSample = channelScopeTextShowSample,
                    vuEnabled = channelScopeTextVuEnabled,
                    textSizeSp = channelScopeTextSizeSp,
                    textFont = channelScopeTextFont,
                    noteFormat = channelScopeTextNoteFormat,
                    paddingPx = paddingPx,
                    gridColorArgb = channelScopeGridColor.toArgb(),
                    gridWidthPx = (channelScopeGridWidthDp * density).coerceAtLeast(1f),
                    lineColorArgb = channelScopeLineColor.toArgb(),
                    lineWidthPx = (channelScopeLineWidthDp * density).coerceAtLeast(1f),
                    vuColorArgb = channelScopeVuColor.toArgb(),
                    textPalette = com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette(
                        channelArgb = channelScopeTextPalette.channel.toArgb(),
                        noteArgb = channelScopeTextPalette.note.toArgb(),
                        volumeArgb = channelScopeTextPalette.volume.toArgb(),
                        effectArgb = channelScopeTextPalette.effect.toArgb(),
                        instrumentOrSampleArgb = channelScopeTextPalette.instrumentOrSample.toArgb(),
                        separatorArgb = channelScopeTextPalette.separator.toArgb()
                    ),
                    shadowEnabled = channelScopeTextShadowEnabled,
                    hideWhenOverflow = channelScopeTextHideWhenOverflow,
                    contrastMode = channelScopeContrastMode,
                    showArtworkBackground = showArtworkBackground,
                    channelScopeWindowMs = channelScopeWindowMs,
                    channelScopeGainPercent = channelScopeGainPercent,
                    channelScopeDcRemovalEnabled = channelScopeDcRemovalEnabled,
                    channelScopeWaveformClippingEnabled = channelScopeWaveformClippingEnabled,
                    channelScopeTriggerMode = channelScopeTriggerModeNative,
                    channelScopeTriggerAlgorithm = channelScopeTriggerAlgorithmNative,
                    channelScopeWaveRenderMode = channelScopeWaveRenderModeNative,
                    channelScopeAntialiasMethod = channelScopeAntialiasMethodNative,
                    channelScopeTrackTransition = channelScopeTrackTransition
                )
                NativeVisSpec(
                    backend = if (channelScopeRenderBackend == VisualizationRenderBackend.Compose) {
                        VisualizationRenderBackend.OpenGlSurface
                    } else {
                        channelScopeRenderBackend
                    },
                    frame = nativeFrame,
                    cornerRadiusDp = channelScopeCornerRadiusDp
                )
            }

            VisualizationMode.ProjectM -> {
                // GL-only; projectM draws its own fullscreen background.
                val nativeFrame = baseNativeFrame.copy(
                    mode = 100,
                    contrastMode = 0,
                    showArtworkBackground = false
                )
                NativeVisSpec(
                    backend = projectMRenderBackend,
                    frame = nativeFrame,
                    cornerRadiusDp = visCornerRadiusDp
                )
            }

            VisualizationMode.Starfield -> {
                // Track changes dip isPlaying briefly; only a real pause holds it
                // off long enough to earn the dim/themed fade.
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
                val nativeFrame = baseNativeFrame.copy(
                    mode = 5,
                    contrastMode = if (starfieldContrastBackdropEnabled) 7 else 0,
                    contrastScrimColorArgb = 0xFF000000.toInt(),
                    showArtworkBackground = true,
                    monochromeBackdrop = starfieldMonochromeBackdrop && starfieldPlaybackActive,
                    visualAlpha = starfieldAlpha,
                    starfieldStarCount = starfieldStarCount,
                    starfieldSpeed = starfieldSpeed,
                    starfieldFov = starfieldFov,
                    starfieldNearPlane = starfieldNearPlane,
                    starfieldStarColorArgb = starfieldStarColorArgb,
                    starfieldSquarePixels = starfieldSquarePixels,
                    starfieldBaseSizePx = starfieldBaseSizePx,
                    starfieldSizeGrowth = starfieldSizeGrowth,
                    starfieldFarDim = starfieldFarDim,
                    starfieldSoftness = starfieldSoftness,
                    starfieldBeatGlow = starfieldBeatGlow,
                    starfieldGlowSize = starfieldGlowSize,
                    starfieldTrailPersistence = starfieldTrailPersistence,
                    starfieldStreaks = starfieldStreaks,
                    starfieldStreakLength = starfieldStreakLength,
                    starfieldCenterX = starfieldCenterX,
                    starfieldCenterY = starfieldCenterY,
                    starfieldAutoDrift = starfieldAutoDrift,
                    starfieldBeatFollow = starfieldBeatFollow,
                    starfieldReactSpeed = starfieldReactSpeed,
                    starfieldFlash = starfieldFlash
                )
                NativeVisSpec(
                    backend = starfieldRenderBackend,
                    frame = nativeFrame,
                    cornerRadiusDp = visCornerRadiusDp
                )
            }

            VisualizationMode.Off -> null
        }
        if (nativeSpec != null) {
            when (nativeSpec.backend) {
                VisualizationRenderBackend.VulkanTexture -> {
                    com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeVkTextureVisualization(
                        frame = nativeSpec.frame,
                        onFrameStats = channelScopeOnFrameStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                VisualizationRenderBackend.VulkanSurface -> {
                    com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeVkSurfaceVisualization(
                        frame = nativeSpec.frame,
                        cornerRadiusDp = nativeSpec.cornerRadiusDp,
                        veilColor = surfaceVeilColor,
                        onFrameStats = channelScopeOnFrameStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                VisualizationRenderBackend.OpenGlSurface -> {
                    com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlSurfaceVisualization(
                        frame = nativeSpec.frame,
                        cornerRadiusDp = nativeSpec.cornerRadiusDp,
                        veilColor = surfaceVeilColor,
                        onFrameStats = channelScopeOnFrameStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                VisualizationRenderBackend.OpenGlTexture -> {
                    com.flopster101.siliconplayer.ui.visualization.gl.SiliconNativeGlTextureVisualization(
                        frame = nativeSpec.frame,
                        onFrameStats = channelScopeOnFrameStats,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                VisualizationRenderBackend.Compose -> Unit
            }
        }
        when (mode) {
            VisualizationMode.Bars -> {
                if (barRenderBackend == VisualizationRenderBackend.Compose) {
                    BarsVisualization(
                        bars = bars,
                        barCount = barCount,
                        barRoundnessDp = barRoundnessDp,
                        barOverlayArtwork = barOverlayArtwork,
                        barFrequencyGridEnabled = barFrequencyGridEnabled,
                        sampleRateHz = barSampleRateHz,
                        barColor = barColor,
                        backgroundColor = barBackgroundColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (barFrequencyGridEnabled) {
                    BarsFrequencyGridLabelOverlay(
                        sampleRateHz = barSampleRateHz,
                        sourceSize = if (bars.isNotEmpty()) bars.size else 256,
                        textColor = barColor,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            VisualizationMode.Oscilloscope -> {
                if (oscRenderBackend == VisualizationRenderBackend.Compose) {
                    OscilloscopeVisualization(
                        waveformLeft = waveformLeft,
                        waveformRight = waveformRight,
                        channelCount = channelCount,
                        oscStereo = oscStereo,
                        oscColor = oscColor,
                        gridColor = oscGridColor,
                        lineWidthPx = (oscLineWidthDp * density).coerceAtLeast(1f),
                        gridWidthPx = (oscGridWidthDp * density).coerceAtLeast(1f),
                        showVerticalGrid = oscVerticalGridEnabled,
                        showCenterLine = oscCenterLineEnabled,
                        modifier = modifier
                    )
                }
            }

            VisualizationMode.VuMeters -> {
                if (vuRenderBackend == VisualizationRenderBackend.Compose) {
                    VuMetersVisualization(
                        vuLevels = vuLevels,
                        channelCount = channelCount,
                        vuAnchor = vuAnchor,
                        vuColor = vuColor,
                        vuLabelColor = vuLabelColor,
                        vuBackgroundColor = vuBackgroundColor,
                        modifier = modifier
                    )
                }
            }

            else -> Unit
        }
    }
}

private data class ChannelScopeTextPalette(
    val channel: Color,
    val note: Color,
    val volume: Color,
    val effect: Color,
    val instrumentOrSample: Color,
    val separator: Color
)

private fun resolveChannelScopeTextPalette(
    mode: VisualizationChannelScopeTextColorMode,
    monetColor: Color,
    customColor: Color
): ChannelScopeTextPalette {
    val uniform = when (mode) {
        VisualizationChannelScopeTextColorMode.Monet -> monetColor.copy(alpha = 0.93f)
        VisualizationChannelScopeTextColorMode.White -> Color.White
        VisualizationChannelScopeTextColorMode.Custom -> customColor
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> Color.Unspecified
    }
    if (mode != VisualizationChannelScopeTextColorMode.OpenMptInspired) {
        return ChannelScopeTextPalette(
            channel = uniform,
            note = uniform,
            volume = uniform,
            effect = uniform,
            instrumentOrSample = uniform,
            separator = uniform.copy(alpha = 0.86f)
        )
    }
    // Inspired by OpenMPT pattern syntax-highlight groups (dark schemes).
    return when (mode) {
        VisualizationChannelScopeTextColorMode.OpenMptInspired -> ChannelScopeTextPalette(
            channel = Color(0xFFBABDB6),
            note = Color(0xFF729FCF),
            volume = Color(0xFF8AE234),
            effect = Color(0xFFFCAF3E),
            instrumentOrSample = Color.White,
            separator = Color.White.copy(alpha = 0.78f)
        )
        else -> error("Unhandled channel scope text color mode: $mode")
    }
}

private fun deriveVuTrackColor(accent: Color): Color {
    val neutral = if (accent.luminance() > 0.56f) Color.Black else Color.White
    // Tonal track variant: tied to accent with contrast-aware neutral shift.
    return lerp(accent, neutral, 0.72f).copy(alpha = 0.62f)
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

private fun deriveVuLabelColor(accent: Color): Color {
    // Label should match the filled VU color.
    return accent
}

private fun resolveOscColor(
    hasArtwork: Boolean,
    noArtworkMode: VisualizationOscColorMode,
    withArtworkMode: VisualizationOscColorMode,
    artworkColor: Color,
    monetColor: Color,
    customColor: Color
): Color {
    val mode = if (hasArtwork) withArtworkMode else noArtworkMode
    return when (mode) {
        VisualizationOscColorMode.Artwork -> artworkColor
        VisualizationOscColorMode.Monet -> monetColor
        VisualizationOscColorMode.White -> Color.White.copy(alpha = monetColor.alpha)
        VisualizationOscColorMode.Custom -> customColor
    }
}

private data class ArtworkColorProfile(
    val accent: Color,
    val averageLuminance: Float,
    val minLuminance: Float,
    val maxLuminance: Float
)

private fun extractArtworkColorProfile(artwork: ImageBitmap?): ArtworkColorProfile? {
    if (artwork == null) return null
    val pixels = artwork.toPixelMap()
    val width = pixels.width
    val height = pixels.height
    if (width <= 0 || height <= 0) return null

    val stepX = max(1, width / 32)
    val stepY = max(1, height / 32)

    var weightedR = 0.0
    var weightedG = 0.0
    var weightedB = 0.0
    var weightSum = 0.0
    var avgR = 0.0
    var avgG = 0.0
    var avgB = 0.0
    var vividBestScore = -1.0
    var vividBestColor = Color.White
    var sampleCount = 0

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val c = pixels[x, y]
            val r = c.red.toDouble()
            val g = c.green.toDouble()
            val b = c.blue.toDouble()
            val maxCh = max(r, max(g, b))
            val minCh = min(r, min(g, b))
            val sat = if (maxCh <= 1e-6) 0.0 else (maxCh - minCh) / maxCh
            val value = maxCh
            val weight = (0.2 + (sat * 0.8)) * (0.3 + (value * 0.7))
            val vividScore = sat * (0.55 + (value * 0.45))

            weightedR += r * weight
            weightedG += g * weight
            weightedB += b * weight
            weightSum += weight

            avgR += r
            avgG += g
            avgB += b
            if (vividScore > vividBestScore) {
                vividBestScore = vividScore
                vividBestColor = Color(
                    red = r.toFloat().coerceIn(0f, 1f),
                    green = g.toFloat().coerceIn(0f, 1f),
                    blue = b.toFloat().coerceIn(0f, 1f),
                    alpha = 1f
                )
            }
            sampleCount++
            x += stepX
        }
        y += stepY
    }

    if (sampleCount <= 0) return null
    var accent = if (weightSum > 1e-6) {
        Color(
            red = (weightedR / weightSum).toFloat().coerceIn(0f, 1f),
            green = (weightedG / weightSum).toFloat().coerceIn(0f, 1f),
            blue = (weightedB / weightSum).toFloat().coerceIn(0f, 1f),
            alpha = 1f
        )
    } else {
        Color(
            red = (avgR / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
            green = (avgG / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
            blue = (avgB / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    val vividSat = colorSaturation(vividBestColor)
    if (vividSat > 0.24f && vividBestScore > 0.22) {
        val t = ((vividSat - 0.24f) / 0.56f).coerceIn(0f, 1f)
        val vividBlend = (0.42f + (t * 0.38f)).coerceIn(0.42f, 0.80f)
        accent = lerp(accent, vividBestColor, vividBlend)
    }
    val averageColor = Color(
        red = (avgR / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        green = (avgG / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        blue = (avgB / sampleCount.toDouble()).toFloat().coerceIn(0f, 1f),
        alpha = 1f
    )
    var minLuma = 1f
    var maxLuma = 0f
    y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val l = pixels[x, y].luminance().coerceIn(0f, 1f)
            if (l < minLuma) minLuma = l
            if (l > maxLuma) maxLuma = l
            x += stepX
        }
        y += stepY
    }

    return ArtworkColorProfile(
        accent = accent,
        averageLuminance = averageColor.luminance().coerceIn(0f, 1f),
        minLuminance = minLuma.coerceIn(0f, 1f),
        maxLuminance = maxLuma.coerceIn(0f, 1f)
    )
}

private fun deriveReadableArtworkVisualizationColor(profile: ArtworkColorProfile): Color {
    val bgLuma = profile.averageLuminance.coerceIn(0f, 1f)
    val bg = Color(bgLuma, bgLuma, bgLuma, 1f)
    val original = profile.accent.copy(alpha = 1f)
    val inverted = invertColor(original).copy(alpha = 1f)
    val preferred = if (contrastRatio(original, bg) >= contrastRatio(inverted, bg)) {
        original
    } else {
        inverted
    }
    val darkBiased = retargetLuminance(preferred, 0.14f)
    val lightBiased = retargetLuminance(preferred, 0.90f)
    val darkReadable = forceMinimumContrast(
        color = darkBiased,
        background = bg,
        minContrast = 4.2f
    )
    val lightReadable = forceMinimumContrast(
        color = lightBiased,
        background = bg,
        minContrast = 4.2f
    )
    val neutralLight = forceMinimumContrast(
        color = Color.White,
        background = bg,
        minContrast = 4.2f
    )
    val neutralDark = forceMinimumContrast(
        color = Color.Black,
        background = bg,
        minContrast = 4.2f
    )

    val minBg = Color(profile.minLuminance, profile.minLuminance, profile.minLuminance, 1f)
    val avgBg = Color(profile.averageLuminance, profile.averageLuminance, profile.averageLuminance, 1f)
    // Center contrast backdrop already dims bright artwork behind the waveform area.
    val effectiveMaxLuma = min(profile.maxLuminance, 0.74f)
    val maxBg = Color(effectiveMaxLuma, effectiveMaxLuma, effectiveMaxLuma, 1f)
    fun score(candidate: Color): Float {
        val darkRegion = contrastRatio(candidate, minBg)
        val avgRegion = contrastRatio(candidate, avgBg)
        val brightRegion = contrastRatio(candidate, maxBg)
        return darkRegion * 0.58f + avgRegion * 0.32f + brightRegion * 0.10f
    }

    val accentSaturation = colorSaturation(profile.accent)
    val allowNeutralCandidates = accentSaturation < 0.22f
    val baseCandidates = buildList {
        add(darkReadable)
        add(lightReadable)
        if (allowNeutralCandidates) {
            add(neutralLight)
            add(neutralDark)
        }
    }
    val readableBase = baseCandidates.maxBy { candidate ->
        val neutralBonus = if (accentSaturation < 0.14f && (candidate == neutralLight || candidate == neutralDark)) {
            0.45f
        } else {
            0f
        }
        val chromaBonus = if (accentSaturation > 0.26f) {
            colorSaturation(candidate) * 0.35f
        } else {
            0f
        }
        score(candidate) + neutralBonus + chromaBonus
    }
    fun minContrastAcross(candidate: Color): Float {
        return min(
            contrastRatio(candidate, minBg),
            min(contrastRatio(candidate, avgBg), contrastRatio(candidate, maxBg))
        )
    }

    // Keep a visible hue hint where possible without sacrificing readability.
    val maxTint = if (accentSaturation < 0.16f) {
        0f
    } else {
        (0.30f + (accentSaturation * 0.45f)).coerceIn(0.30f, 0.72f)
    }
    var tinted = readableBase
    if (maxTint > 0f) {
        var t = maxTint
        val minAllowedContrast = 3.4f
        val saturationFloor = when {
            accentSaturation >= 0.50f -> 0.32f
            accentSaturation >= 0.36f -> 0.24f
            else -> 0.16f
        }
        while (t >= 0.02f) {
            val candidate = lerp(readableBase, preferred, t)
            if (minContrastAcross(candidate) >= minAllowedContrast) {
                tinted = candidate
                if (colorSaturation(candidate) >= saturationFloor) break
            }
            t -= 0.04f
        }
    }
    if (accentSaturation >= 0.30f && colorSaturation(tinted) < 0.18f) {
        val boosted = lerp(tinted, preferred, 0.24f)
        if (minContrastAcross(boosted) >= 3.2f) {
            tinted = boosted
        }
    }
    return tinted.copy(alpha = 1f)
}

private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return if (l1 >= l2) l1 / l2 else l2 / l1
}

private fun forceMinimumContrast(
    color: Color,
    background: Color,
    minContrast: Float
): Color {
    if (contrastRatio(color, background) >= minContrast) return color
    val toward = if (background.luminance() < 0.5f) Color.White else Color.Black
    var t = 0.08f
    while (t <= 1f) {
        val candidate = lerp(color, toward, t)
        if (contrastRatio(candidate, background) >= minContrast) {
            return candidate
        }
        t += 0.08f
    }
    return toward
}

private fun retargetLuminance(color: Color, targetLuminance: Float): Color {
    val target = targetLuminance.coerceIn(0f, 1f)
    var low = 0f
    var high = 1f
    val toward = if (color.luminance() < target) Color.White else Color.Black
    var best = color
    repeat(10) {
        val t = (low + high) * 0.5f
        val candidate = lerp(color, toward, t)
        val l = candidate.luminance()
        best = candidate
        if (l < target) {
            low = t
        } else {
            high = t
        }
    }
    return best
}

private fun colorSaturation(color: Color): Float {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val maxCh = max(r, max(g, b))
    val minCh = min(r, min(g, b))
    if (maxCh <= 1e-6f) return 0f
    return ((maxCh - minCh) / maxCh).coerceIn(0f, 1f)
}

private fun invertColor(color: Color): Color {
    return Color(
        red = 1f - color.red,
        green = 1f - color.green,
        blue = 1f - color.blue,
        alpha = color.alpha
    )
}
