package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.ui.screens.ChannelScopePrefs
import com.flopster101.siliconplayer.ui.visualization.OffscreenVisualizationBridge
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge

internal object DesktopOffscreenVisualizationBridge : OffscreenVisualizationBridge {
    override fun setVisualizationActive(
        mode: VisualizationMode,
        decoderName: String,
        active: Boolean
    ) {
        if (mode != VisualizationMode.ChannelScope) return
        NativeBridge.setCoreOption(
            com.flopster101.siliconplayer.pluginNameForCoreName(decoderName) ?: decoderName,
            "visualization.channel_scope_active",
            active.toString()
        )
        NativeBridge.setChannelScopeVisualizerActive(active)
    }

    override fun createVisualizer(): Long = SiliconVisNativeBridge.nativeCreate()

    override fun attachAudioEngine(visualizerHandle: Long) {
        NativeBridge.attachAudioEngineToVisualizer(visualizerHandle)
    }

    override fun configureVisualization(
        visualizerHandle: Long,
        mode: VisualizationMode,
        prefs: AppPreferences,
        channelScopePrefs: ChannelScopePrefs,
        density: Float
    ) {
        val nativeMode = when (mode) {
            VisualizationMode.Bars -> 1
            VisualizationMode.Oscilloscope -> 2
            VisualizationMode.VuMeters -> 3
            VisualizationMode.ChannelScope -> 4
            VisualizationMode.Starfield -> 5
            VisualizationMode.ProjectM -> 100
            VisualizationMode.Off -> error("Cannot render visualization mode Off.")
        }
        SiliconVisNativeBridge.nativeSetMode(visualizerHandle, nativeMode)
        SiliconVisNativeBridge.nativeSetArtworkTheme(
            visualizerHandle, 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 1
        )
        SiliconVisNativeBridge.nativeClearArtwork(visualizerHandle)
        SiliconVisNativeBridge.nativeSetShowArtworkBackground(visualizerHandle, false)
        SiliconVisNativeBridge.nativeSetContrastMode(visualizerHandle, 0)
        if (mode != VisualizationMode.ChannelScope) return
        SiliconVisNativeBridge.nativeSetChannelScopeAntialiasMethod(
            visualizerHandle, channelScopePrefs.antialiasMethod.nativeValue
        )
        val layout = when (channelScopePrefs.layout) {
            VisualizationChannelScopeLayout.BalancedTwoColumn -> 2
            VisualizationChannelScopeLayout.ColumnFirst -> 0
        }
        val anchor = when (channelScopePrefs.textAnchor) {
            VisualizationChannelScopeTextAnchor.TopLeft -> 0
            VisualizationChannelScopeTextAnchor.TopCenter -> 1
            VisualizationChannelScopeTextAnchor.TopRight -> 2
            VisualizationChannelScopeTextAnchor.BottomLeft -> 3
            VisualizationChannelScopeTextAnchor.BottomCenter -> 4
            VisualizationChannelScopeTextAnchor.BottomRight -> 5
        }
        SiliconVisNativeBridge.nativeSetChannelScopeOptions(
            handle = visualizerHandle,
            layout = layout,
            anchor = anchor,
            vuAnchor = if (channelScopePrefs.textVuAnchor == VisualizationVuAnchor.Top) 0 else 1,
            vuEnabled = channelScopePrefs.textVuEnabled,
            textSizeSp = channelScopePrefs.textSizeSp,
            paddingPx = channelScopePrefs.textPaddingDp * density,
            gridColorArgb = when (channelScopePrefs.gridColorModeNoArtwork) {
                VisualizationOscColorMode.White -> 0x66FFFFFF
                VisualizationOscColorMode.Custom -> channelScopePrefs.customGridColorArgb
                else -> 0x57BAC4FF
            },
            gridWidthPx = channelScopePrefs.gridWidthDp * density,
            lineColorArgb = 0xFFFFFFFF.toInt(),
            lineWidthPx = channelScopePrefs.lineWidthDp * density,
            vuColorArgb = textColor(channelScopePrefs.textVuColorMode, channelScopePrefs.textVuCustomColorArgb),
            chArgb = 0xFFBABDB6.toInt(),
            noteArgb = 0xFF729FCF.toInt(),
            volArgb = 0xFF8AE234.toInt(),
            effArgb = 0xFFFCAF3E.toInt(),
            instArgb = 0xFFFFFFFF.toInt(),
            sepArgb = 0xC6FFFFFF.toInt(),
            shadowEnabled = channelScopePrefs.textShadowEnabled,
            hideWhenOverflow = channelScopePrefs.textHideWhenOverflow,
            windowMs = channelScopePrefs.windowMs,
            gainPercent = channelScopePrefs.gainPercent,
            dcRemovalEnabled = channelScopePrefs.dcRemovalEnabled,
            triggerMode = channelScopePrefs.triggerModeNative,
            triggerAlgorithm = channelScopePrefs.triggerAlgorithmNative,
            waveRenderMode = channelScopePrefs.waveRenderMode.nativeValue,
            waveformClipping = channelScopePrefs.waveformClippingEnabled
        )
    }

    override fun getChannelScopeTextState(maxChannels: Int): IntArray =
        NativeBridge.getChannelScopeTextState(maxChannels)

    override fun destroyVisualizer(visualizerHandle: Long) {
        SiliconVisNativeBridge.nativeDestroy(visualizerHandle)
    }

    private fun textColor(mode: VisualizationChannelScopeTextColorMode, custom: Int): Int = when (mode) {
        VisualizationChannelScopeTextColorMode.White -> 0xFFFFFFFF.toInt()
        VisualizationChannelScopeTextColorMode.Custom -> custom
        else -> 0xFF8AE234.toInt()
    }
}
