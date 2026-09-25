package com.flopster101.siliconplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTrackTransition
import com.flopster101.siliconplayer.VisualizationChannelScopeWaveRenderMode
import com.flopster101.siliconplayer.VisualizationFullscreenMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.platform.AppPreferences

internal data class FullscreenTickerPrefs(
    val masterEnabled: Boolean,
    val durationSeconds: Int,
    val perModeEnabled: Map<VisualizationMode, Boolean>
) {
    fun isEnabledFor(mode: VisualizationMode): Boolean {
        return masterEnabled && (perModeEnabled[mode] ?: true)
    }
}

@Composable
internal fun rememberFullscreenTickerPrefs(prefs: AppPreferences): FullscreenTickerPrefs {
    return remember {
        FullscreenTickerPrefs(
            masterEnabled = false,
            durationSeconds = 5,
            perModeEnabled = emptyMap()
        )
    }
}

@Composable
internal fun rememberChannelScopePrefs(prefs: AppPreferences): ChannelScopePrefs {
    return remember {
        ChannelScopePrefs(
            windowMs = 30,
            renderBackend = VisualizationRenderBackend.OpenGlTexture,
            dcRemovalEnabled = true,
            gainPercent = 100,
            contrastBackdropEnabled = true,
            triggerModeNative = 0,
            triggerAlgorithmNative = 0,
            waveRenderMode = VisualizationChannelScopeWaveRenderMode.Antialiased,
            trackTransition = VisualizationChannelScopeTrackTransition.SlideFade,
            fpsMode = VisualizationOscFpsMode.Default,
            lineWidthDp = 2,
            gridWidthDp = 1,
            verticalGridEnabled = true,
            centerLineEnabled = true,
            layout = VisualizationChannelScopeLayout.BalancedTwoColumn,
            lineColorModeNoArtwork = VisualizationOscColorMode.Monet,
            gridColorModeNoArtwork = VisualizationOscColorMode.Monet,
            lineColorModeWithArtwork = VisualizationOscColorMode.Artwork,
            gridColorModeWithArtwork = VisualizationOscColorMode.Artwork,
            customLineColorArgb = 0xFF6BD8FF.toInt(),
            customGridColorArgb = 0x66FFFFFF,
            showArtworkBackground = true,
            backgroundMode = VisualizationChannelScopeBackgroundMode.AutoDarkAccent,
            customBackgroundColorArgb = 0xFF000000.toInt(),
            textEnabled = true,
            textAnchor = VisualizationChannelScopeTextAnchor.TopLeft,
            textPaddingDp = 4,
            textSizeSp = 12,
            textHideWhenOverflow = true,
            textShadowEnabled = true,
            textFont = VisualizationChannelScopeTextFont.RetroCuteMono,
            textColorMode = VisualizationChannelScopeTextColorMode.White,
            customTextColorArgb = 0xFFFFFFFF.toInt(),
            textNoteFormat = VisualizationNoteNameFormat.American,
            textShowChannel = true,
            textShowNote = true,
            textVisibleElementSelection = emptySet(),
            textVuEnabled = true,
            textVuAnchor = VisualizationVuAnchor.Center,
            textVuColorMode = VisualizationChannelScopeTextColorMode.White,
            textVuCustomColorArgb = 0xFFFFFFFF.toInt()
        )
    }
}

@Composable
internal fun rememberStarfieldPrefs(prefs: AppPreferences): StarfieldPrefs {
    return remember {
        StarfieldPrefs(
            renderBackend = VisualizationRenderBackend.OpenGlTexture,
            starCount = 1000,
            speed = 1.0f,
            fov = 60.0f,
            nearPlane = 1.0f,
            starColorArgb = 0xFFFFFFFF.toInt(),
            baseSizePx = 2.0f,
            sizeGrowth = 1.0f,
            farDim = 0.5f,
            softness = 0.5f,
            beatGlow = 0.5f,
            glowSize = 1.0f,
            trailPersistence = 0.5f,
            streaks = true,
            streakLength = 1.0f,
            centerX = 0.5f,
            centerY = 0.5f,
            autoDrift = false,
            beatFollow = false,
            reactSpeed = 1.0f,
            flash = 0.0f,
            contrastBackdropEnabled = true,
            monochromeBackdrop = false,
            square = false
        )
    }
}

internal fun writeStarfieldFactoryTune(prefs: AppPreferences, preset: StarfieldPreset) {}

internal fun starfieldActivePreset(prefs: AppPreferences): StarfieldPreset = StarfieldPreset.ClassicAmiga

@Composable
internal fun FullscreenToggleAffordance(
    onToggle: () -> Unit,
    show: Boolean
) {
}

@Composable
internal fun FullscreenVisualizerSwitcher(
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onVisualizerAction: () -> Unit,
    onVisualizerLongPress: (() -> Unit)? = null,
    onInteraction: () -> Unit = {},
    compact: Boolean,
    modifier: Modifier = Modifier
) {
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
    onSwipePreviousTrack: () -> Unit = onPreviousTrack,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
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
    if (isFullscreen) {
        visualizationContent()
    }
}
