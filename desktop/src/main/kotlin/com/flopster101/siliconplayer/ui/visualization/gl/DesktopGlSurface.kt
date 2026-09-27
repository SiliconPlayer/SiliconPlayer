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
        pixelBuffer: ByteBuffer,
        transitionOffsetX: Float = 0f,
        transitionAlpha: Float = 0f
    ): Boolean
    external fun nativeDestroy(hostHandle: Long, visHandle: Long)
    external fun nativeTakeTransitionSnapshot(hostHandle: Long): Boolean
    external fun nativeReleaseTransitionSnapshot(hostHandle: Long)
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
