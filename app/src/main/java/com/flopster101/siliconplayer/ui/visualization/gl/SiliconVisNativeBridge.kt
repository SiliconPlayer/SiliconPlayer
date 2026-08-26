package com.flopster101.siliconplayer.ui.visualization.gl

import java.nio.ByteBuffer

object SiliconVisNativeBridge {
    init {
        System.loadLibrary("silicon_vis")
    }

    external fun nativeCreate(): Long
    external fun nativeDestroy(handle: Long)

    external fun nativeInitGl(handle: Long): Boolean
    external fun nativeResize(handle: Long, widthPx: Int, heightPx: Int, density: Float)
    external fun nativeReleaseGl(handle: Long)

    external fun nativeSetMode(handle: Long, mode: Int)

    external fun nativeSetArtworkPixels(handle: Long, byteBuffer: ByteBuffer?, width: Int, height: Int)
    external fun nativeClearArtwork(handle: Long)
    external fun nativeSetIconPixels(handle: Long, byteBuffer: ByteBuffer?, width: Int, height: Int)
    external fun nativeClearIcon(handle: Long)
    external fun nativeSetArtworkTheme(handle: Long, primaryArgb: Int, surfaceArgb: Int, iconType: Int)
    external fun nativeSetContrastMode(handle: Long, contrastMode: Int)
    external fun nativeSetContrastScrim(handle: Long, argb: Int)
    external fun nativeSetShowArtworkBackground(handle: Long, show: Boolean)
    external fun nativeAttachProjectM(handle: Long, presetDir: String)
    external fun nativeProjectMNextPreset(handle: Long, smoothTransition: Boolean)

    external fun nativeSetFontAtlas(
        handle: Long,
        byteBuffer: ByteBuffer,
        width: Int,
        height: Int,
        baseFontSizePx: Float,
        lineHeightPx: Float,
        glyphBuffer: ByteBuffer,
        glyphCount: Int
    )

    external fun nativePushPcm(handle: Long, pcm: FloatArray, frames: Int, channels: Int, sampleRate: Int)
    external fun nativePushFft(handle: Long, fft: FloatArray, binCount: Int)
    external fun nativeSetVuLevels(handle: Long, left: Float, right: Float)
    external fun nativePushChannelScopeHistory(handle: Long, channel: Int, history: FloatArray, count: Int)
    external fun nativePushChannelScopeAllHistories(
        handle: Long,
        channelCount: Int,
        samplesPerChannel: Int,
        flatArray: FloatArray
    )

    external fun nativeSetChannelScopeOptions(
        handle: Long,
        layout: Int,
        anchor: Int,
        vuAnchor: Int,
        vuEnabled: Boolean,
        textSizeSp: Int,
        paddingPx: Float,
        gridColorArgb: Int,
        gridWidthPx: Float,
        lineColorArgb: Int,
        lineWidthPx: Float,
        vuColorArgb: Int,
        chArgb: Int,
        noteArgb: Int,
        volArgb: Int,
        effArgb: Int,
        instArgb: Int,
        sepArgb: Int,
        shadowEnabled: Boolean,
        hideWhenOverflow: Boolean,
        windowMs: Int,
        gainPercent: Int,
        dcRemovalEnabled: Boolean,
        triggerMode: Int
    )

    external fun nativeSetOscilloscopeOptions(
        handle: Long,
        stereo: Boolean,
        windowMs: Int,
        triggerMode: Int,
        waveColorArgb: Int,
        lineWidthPx: Float,
        gridColorArgb: Int,
        gridWidthPx: Float,
        showCenterLine: Boolean,
        showGrid: Boolean
    )

    external fun nativeSetBarsOptions(
        handle: Long,
        barCount: Int,
        smoothing: Float,
        startColorArgb: Int,
        endColorArgb: Int,
        cornerRadiusPx: Float,
        showFrequencyGuide: Boolean,
        guideColorArgb: Int
    )

    external fun nativeSetVuMetersOptions(
        handle: Long,
        stereo: Boolean,
        topPlacement: Boolean,
        smoothing: Float,
        fillColorArgb: Int,
        trackColorArgb: Int,
        labelColorArgb: Int
    )

    external fun nativeRender(handle: Long)
}
