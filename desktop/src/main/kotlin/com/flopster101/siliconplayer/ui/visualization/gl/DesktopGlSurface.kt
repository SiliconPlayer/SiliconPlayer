package com.flopster101.siliconplayer.ui.visualization.gl

import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.ui.visualization.OffscreenVisualizationSurface
import java.awt.Font
import java.nio.ByteBuffer

object DesktopGlSurface : OffscreenVisualizationSurface {
    init {
        NativeBridge
    }

    external fun nativeInit(visHandle: Long): Long
    external fun nativeRenderFrame(
        hostHandle: Long,
        visHandle: Long,
        width: Int,
        height: Int,
        density: Float,
        pixelBuffer: ByteBuffer,
        captureOutput: Boolean,
        asyncReadback: Boolean,
        transitionOffsetX: Float = 0f,
        transitionAlpha: Float = 0f
    ): Boolean
    external fun nativeDestroy(hostHandle: Long, visHandle: Long)
    external fun nativeTakeTransitionSnapshot(hostHandle: Long): Boolean
    external fun nativeReleaseTransitionSnapshot(hostHandle: Long)

    override fun initialize(visualizerHandle: Long): Long = nativeInit(visualizerHandle)

    override fun renderFrame(
        surfaceHandle: Long,
        visualizerHandle: Long,
        width: Int,
        height: Int,
        density: Float,
        pixels: ByteBuffer,
        captureOutput: Boolean,
        asyncReadback: Boolean
    ): Boolean = nativeRenderFrame(
        surfaceHandle, visualizerHandle, width, height, density, pixels, captureOutput, asyncReadback
    )

    override fun close(surfaceHandle: Long, visualizerHandle: Long) {
        nativeDestroy(surfaceHandle, visualizerHandle)
    }

    override fun prepareTextAtlas(
        surfaceHandle: Long,
        visualizerHandle: Long,
        font: VisualizationChannelScopeTextFont
    ): Boolean {
        val resourcePath = when (font) {
            VisualizationChannelScopeTextFont.System -> null
            VisualizationChannelScopeTextFont.RaccoonSerif -> "/fonts/scope/raccoon_serif_base.ttf"
            VisualizationChannelScopeTextFont.RaccoonMono -> "/fonts/scope/raccoon_serif_mono.ttf"
            VisualizationChannelScopeTextFont.RetroCuteMono -> "/fonts/scope/retro_pixel_cute_mono.ttf"
            VisualizationChannelScopeTextFont.RetroThick -> "/fonts/scope/retro_pixel_thick.ttf"
        }
        val atlas = DesktopGlFontAtlas.createAtlasUploadData(
            fontName = Font.SANS_SERIF,
            fontResourcePath = resourcePath,
            baseFontSizePx = 32f
        )
        SiliconVisNativeBridge.nativeSetFontAtlas(
            handle = visualizerHandle,
            byteBuffer = atlas.pixelBuffer,
            width = atlas.width,
            height = atlas.height,
            baseFontSizePx = atlas.baseFontSizePx,
            lineHeightPx = atlas.lineHeightPx,
            glyphBuffer = atlas.glyphBuffer,
            glyphCount = atlas.glyphCount
        )
        return true
    }
}
