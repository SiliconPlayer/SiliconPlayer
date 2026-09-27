package com.flopster101.siliconplayer.ui.visualization.gl

import com.flopster101.siliconplayer.NativeBridge
import java.nio.ByteBuffer

object DesktopGlSurface {
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
        pixelBuffer: ByteBuffer
    ): Boolean
    external fun nativeDestroy(hostHandle: Long, visHandle: Long)
    external fun nativeUploadScopeAtlas(
        hostHandle: Long,
        pixelBuffer: ByteBuffer,
        width: Int,
        height: Int,
        baseFontSizePx: Float,
        lineHeightPx: Float,
        glyphBuffer: ByteBuffer,
        glyphCount: Int
    ): Boolean
    external fun nativeSetScopeTextQuads(hostHandle: Long, quadArray: FloatArray?, floatCount: Int)
}
