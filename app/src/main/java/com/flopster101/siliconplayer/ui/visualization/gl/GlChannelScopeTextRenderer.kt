package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.Typeface
import android.opengl.GLES20
import androidx.core.content.res.ResourcesCompat
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextMeasurer
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import com.flopster101.siliconplayer.ui.visualization.channel.resolveChannelGrid
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

private class AtlasChannelScopeTextMeasurer(private val atlas: GlFontAtlas) : ChannelScopeTextMeasurer {
    override fun widthOf(text: String, textSizePx: Float): Float =
        atlas.measureTextWidth(text, textSizePx / atlas.baseFontSizePx)
    override fun lineHeightOf(textSizePx: Float): Float =
        atlas.lineHeightPx * (textSizePx / atlas.baseFontSizePx)
}

/**
 * High-performance batched OpenGL text and mini VU meter renderer for Channel Scope view.
 */
internal class GlChannelScopeTextRenderer(
    private val context: Context,
    private val isGl: Boolean = true
) {
    private var fontAtlas: GlFontAtlas? = null
    private var currentFont: VisualizationChannelScopeTextFont? = null
    private val textProgram = GlTextProgram()
    private val batchBuilder = GlTextBatchBuilder(1024)
    private var textVertexBuffer: FloatBuffer? = null
    private var currentVertexCount: Int = 0
    private var vuProgram = 0
    private var vuPositionLoc = -1
    private var vuColorLoc = -1
    private var currentFrame: GlChannelScopeTextFrame? = null
    private var lastBuiltFrame: GlChannelScopeTextFrame? = null
    private var lastBuildWidth = 0f
    private var lastBuildHeight = 0f

    val vertexBuffer: FloatBuffer? get() = textVertexBuffer
    val vertexCount: Int get() = currentVertexCount

    fun onSurfaceCreated() {
        if (!isGl) return
        textProgram.init()
        vuProgram = GlSimplePrimitives.createProgram()
        vuPositionLoc = GLES20.glGetAttribLocation(vuProgram, "aPosition")
        vuColorLoc = GLES20.glGetUniformLocation(vuProgram, "uColor")
    }

    fun release() {
        if (isGl) {
            textProgram.release()
            if (vuProgram != 0) {
                GLES20.glDeleteProgram(vuProgram)
                vuProgram = 0
            }
        }
        fontAtlas?.release()
        fontAtlas = null
        currentFont = null
        textVertexBuffer = null
        currentVertexCount = 0
        currentFrame = null
        lastBuiltFrame = null
        lastBuildWidth = 0f
        lastBuildHeight = 0f
    }

    private fun ensureAtlas(font: VisualizationChannelScopeTextFont) {
        if (fontAtlas != null && currentFont == font) return
        fontAtlas?.release()
        val typeface = resolveTypeface(context, font)
        val atlas = GlFontAtlas(typeface = typeface, baseFontSizePx = 32f)
        if (isGl) {
            atlas.initGl()
        } else {
            atlas.initCpu()
        }
        fontAtlas = atlas
        currentFont = font
    }

    fun buildGeometry(
        frame: GlChannelScopeTextFrame,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        currentFrame = frame
        // Text states refresh at ~10 Hz; skip the per-frame string rebuild
        // when nothing visible changed.
        if (frame == lastBuiltFrame &&
            surfaceWidth == lastBuildWidth &&
            surfaceHeight == lastBuildHeight
        ) {
            return
        }
        val channels = frame.channelCount
        if (channels <= 0 || surfaceWidth <= 0f || surfaceHeight <= 0f) {
            currentVertexCount = 0
            lastBuiltFrame = frame
            lastBuildWidth = surfaceWidth
            lastBuildHeight = surfaceHeight
            return
        }

        ensureAtlas(frame.textFont)
        val atlas = fontAtlas ?: return

        val layout = layoutChannelScopeText(frame, surfaceWidth, surfaceHeight, AtlasChannelScopeTextMeasurer(atlas))
        batchBuilder.clear()
        if (layout == null) {
            currentVertexCount = 0
            lastBuiltFrame = frame
            lastBuildWidth = surfaceWidth
            lastBuildHeight = surfaceHeight
            return
        }
        val quadScale = layout.textSizePx / atlas.baseFontSizePx
        for (run in layout.runs) {
            val a = ((run.colorArgb ushr 24) and 0xFF) / 255f
            val r = ((run.colorArgb ushr 16) and 0xFF) / 255f
            val g = ((run.colorArgb ushr 8) and 0xFF) / 255f
            val b = (run.colorArgb and 0xFF) / 255f
            batchBuilder.addText(
                atlas = atlas,
                text = run.text,
                startX = run.xPx,
                startY = run.yPx,
                scale = quadScale,
                r = r, g = g, b = b, a = a,
                shadow = layout.shadowEnabled,
                maxWidthPx = atlas.measureTextWidth(run.text, quadScale) + 1f
            )
        }

        currentVertexCount = batchBuilder.count
        if (currentVertexCount > 0) {
            textVertexBuffer = batchBuilder.uploadToBuffer(textVertexBuffer)
        }
        lastBuiltFrame = frame
        lastBuildWidth = surfaceWidth
        lastBuildHeight = surfaceHeight
    }

    fun drawVu(surfaceWidth: Float, surfaceHeight: Float) {
        val frame = currentFrame ?: return
        if (frame.vuEnabled && vuProgram != 0 && frame.channelHistories.isNotEmpty()) {
            drawVuBars(surfaceWidth, surfaceHeight, frame)
        }
    }

    fun drawText(surfaceWidth: Float, surfaceHeight: Float) {
        val atlas = fontAtlas ?: return
        val buffer = textVertexBuffer ?: return
        if (currentVertexCount <= 0 || !textProgram.isReady) return

        textProgram.draw(
            buffer = buffer,
            vertexCount = vertexCount,
            atlas = atlas,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight
        )
    }

    fun draw(surfaceWidth: Float, surfaceHeight: Float) {
        drawVu(surfaceWidth, surfaceHeight)
        drawText(surfaceWidth, surfaceHeight)
    }

    private fun drawVuBars(
        surfaceWidth: Float,
        surfaceHeight: Float,
        frame: GlChannelScopeTextFrame
    ) {
        val channels = frame.channelCount
        if (channels <= 0 || surfaceWidth <= 0f || surfaceHeight <= 0f) return
        val (columns, rows) = resolveChannelGrid(channels, frame.layoutStrategy)
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = surfaceWidth / safeCols.toFloat()
        val cellHeight = surfaceHeight / safeRows.toFloat()
        val inset = frame.vuInsetPx.coerceAtLeast(1f)
        val h = frame.vuStripHeightPx.coerceAtLeast(1f)

        GLES20.glUseProgram(vuProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (col in 0 until safeCols) {
            for (row in 0 until safeRows) {
                val channel = (col * safeRows) + row
                if (channel >= channels) continue

                val cellLeft = col * cellWidth
                val cellTop = row * cellHeight
                val cellBottom = cellTop + cellHeight

                val usableWidth = (cellWidth - (inset * 2f)).coerceAtLeast(0f)
                val trackX = cellLeft + inset
                val trackY = if (frame.vuAnchor == VisualizationVuAnchor.Top) {
                    cellTop + inset
                } else {
                    (cellBottom - h - inset).coerceAtLeast(cellTop)
                }

                // 1. Draw track
                val trackVertices = GlSimplePrimitives.rectToTrianglesNdc(
                    x = trackX,
                    y = trackY,
                    w = usableWidth,
                    h = h,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight
                )
                GlSimplePrimitives.drawTriangles(trackVertices, frame.vuTrackColorArgb, vuPositionLoc, vuColorLoc)

                // 2. Draw level fill
                val history = frame.channelHistories.getOrNull(channel)
                val vuLevel = if (history != null) computeChannelScopeVuLevel(history) else 0f
                val fillWidth = (usableWidth * vuLevel).coerceAtLeast(0f)
                if (fillWidth > 0f) {
                    val fillVertices = GlSimplePrimitives.rectToTrianglesNdc(
                        x = trackX,
                        y = trackY,
                        w = fillWidth,
                        h = h,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight
                    )
                    GlSimplePrimitives.drawTriangles(fillVertices, frame.vuColorArgb, vuPositionLoc, vuColorLoc)
                }
            }
        }
    }

    private fun computeChannelScopeVuLevel(history: FloatArray): Float {
        if (history.isEmpty()) return 0f
        var peak = 0f
        var i = 0
        val step = max(1, history.size / 64)
        while (i < history.size) {
            val sample = kotlin.math.abs(history[i])
            if (sample > peak) peak = sample
            i += step
        }
        return peak.coerceIn(0f, 1f)
    }

    private fun resolveTypeface(context: Context, font: VisualizationChannelScopeTextFont): Typeface {
        return when (font) {
            VisualizationChannelScopeTextFont.System -> Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RaccoonSerif -> ResourcesCompat.getFont(context, R.font.raccoon_serif_base) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RaccoonMono -> ResourcesCompat.getFont(context, R.font.raccoon_serif_mono) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RetroCuteMono -> ResourcesCompat.getFont(context, R.font.retro_pixel_cute_mono) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RetroThick -> ResourcesCompat.getFont(context, R.font.retro_pixel_thick) ?: Typeface.MONOSPACE
        }
    }
}

