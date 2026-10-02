package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTrackTransition
import com.flopster101.siliconplayer.VisualizationChannelScopeWaveRenderMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor

internal data class ChannelScopePrefs(
    val windowMs: Int,
    val renderBackend: VisualizationRenderBackend,
    val dcRemovalEnabled: Boolean,
    val waveformClippingEnabled: Boolean = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.waveformClippingEnabled,
    val gainPercent: Int,
    val contrastBackdropEnabled: Boolean,
    val triggerModeNative: Int,
    val triggerAlgorithmNative: Int,
    val waveRenderMode: VisualizationChannelScopeWaveRenderMode,
    val antialiasMethod: VisualizationChannelScopeAntialiasMethod = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.antialiasMethod,
    val trackTransition: VisualizationChannelScopeTrackTransition,
    val fpsMode: VisualizationOscFpsMode,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int,
    val showArtworkBackground: Boolean,
    val backgroundMode: VisualizationChannelScopeBackgroundMode,
    val customBackgroundColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textVisibleElementSelection: Set<String>,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
) {
    companion object
}

internal data class StarfieldPrefs(
    val renderBackend: VisualizationRenderBackend,
    val starCount: Int,
    val speed: Float,
    val fov: Float,
    val nearPlane: Float,
    val starColorArgb: Int,
    val baseSizePx: Float,
    val sizeGrowth: Float,
    val farDim: Float,
    val softness: Float,
    val beatGlow: Float,
    val glowSize: Float,
    val trailPersistence: Float,
    val streaks: Boolean,
    val streakLength: Float,
    val centerX: Float,
    val centerY: Float,
    val autoDrift: Boolean,
    val beatFollow: Boolean,
    val reactSpeed: Float,
    val flash: Float,
    val contrastBackdropEnabled: Boolean,
    val monochromeBackdrop: Boolean,
    val square: Boolean
) {
    companion object
}
