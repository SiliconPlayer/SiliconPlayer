package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscTriggerMode
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.ui.visualization.OffscreenVisualizationRenderer
import com.flopster101.siliconplayer.ui.visualization.OffscreenVisualizationRendererFactory
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameSource
import com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlSurface

internal object DesktopOffscreenVisualizationRendererFactory : OffscreenVisualizationRendererFactory {
    override fun create(
        mode: VisualizationMode,
        prefs: AppPreferences,
        decoderName: String,
        width: Int,
        height: Int,
        channelScopeAntialiasMethod: VisualizationChannelScopeAntialiasMethod?,
        channelScopeTriggerMode: VisualizationOscTriggerMode?
    ): OffscreenVisualizationRenderer {
        return OffscreenVisualizationRenderer(
            mode = mode,
            prefs = prefs,
            decoderName = decoderName,
            width = width,
            height = height,
            surface = DesktopGlSurface,
            bridge = DesktopOffscreenVisualizationBridge,
            nameSource = if (mode == VisualizationMode.ChannelScope) {
                DesktopChannelScopeNameSource
            } else null,
            channelScopeAntialiasMethod = channelScopeAntialiasMethod,
            channelScopeTriggerMode = channelScopeTriggerMode,
            asyncReadback = true
        )
    }
}
