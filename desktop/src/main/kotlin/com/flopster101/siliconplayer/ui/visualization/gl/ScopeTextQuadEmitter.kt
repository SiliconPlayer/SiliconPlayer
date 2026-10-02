package com.flopster101.siliconplayer.ui.visualization.gl

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextMeasurer
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextRun
import java.nio.ByteBuffer
import java.nio.ByteOrder

// JVM mirror of Android's GlTextBatchBuilder quad math. Positioned runs from
// the shared layout engine become (x, y, u, v, r, g, b, a) triangle vertices
// the native scope overlay draws with the same text program as Android.
internal class ScopeTextQuadEmitter(glyphBuffer: ByteBuffer, val baseFontSizePx: Float, val lineHeightPx: Float) {
    private data class Glyph(
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val widthPx: Float,
        val heightPx: Float,
        val advanceX: Float
    )

    private val glyphs: Map<Int, Glyph>
    private val fallback: Glyph

    init {
        val table = LinkedHashMap<Int, Glyph>()
        val dup = glyphBuffer.duplicate().order(ByteOrder.nativeOrder())
        while (dup.remaining() >= 36) {
            val code = dup.int
            val g = Glyph(
                u0 = dup.float,
                v0 = dup.float,
                u1 = dup.float,
                v1 = dup.float,
                widthPx = dup.float,
                heightPx = dup.float,
                advanceX = dup.float
            )
            dup.float // ascentPx; baked into the raster, not needed here
            table[code] = g
        }
        glyphs = table
        fallback = table['?'.code] ?: table.values.first()
    }

    val measurer = object : ChannelScopeTextMeasurer {
        override fun widthOf(text: String, textSizePx: Float): Float {
            val scale = textSizePx / baseFontSizePx
            var width = 0f
            for (ch in text) {
                width += (glyphs[ch.code] ?: fallback).advanceX * scale
            }
            return width
        }

        override fun lineHeightOf(textSizePx: Float): Float =
            lineHeightPx * (textSizePx / baseFontSizePx)
    }

    fun emitRuns(runs: List<ChannelScopeTextRun>, textSizePx: Float, shadowEnabled: Boolean): FloatArray {
        val scale = textSizePx / baseFontSizePx
        var quads = 0
        for (run in runs) {
            quads += countQuads(run.text, scale, measurer.widthOf(run.text, textSizePx) + 1f)
            if (shadowEnabled) quads += countQuads(run.text, scale, measurer.widthOf(run.text, textSizePx) + 1f)
        }
        val out = FloatArray(quads * 48)
        var offset = 0
        val shadowOffset = (scale * 2.8f).coerceIn(0.75f, 3.0f)
        for (run in runs) {
            val a = ((run.colorArgb ushr 24) and 0xFF) / 255f
            val r = ((run.colorArgb ushr 16) and 0xFF) / 255f
            val g = ((run.colorArgb ushr 8) and 0xFF) / 255f
            val b = (run.colorArgb and 0xFF) / 255f
            val maxWidth = measurer.widthOf(run.text, textSizePx) + 1f
            if (shadowEnabled && a > 0f) {
                offset = emitText(out, offset, run.text, run.xPx + shadowOffset, run.yPx + shadowOffset, scale, 0f, 0f, 0f, a * 0.50f, maxWidth)
            }
            offset = emitText(out, offset, run.text, run.xPx, run.yPx, scale, r, g, b, a, maxWidth)
        }
        return out
    }

    private fun countQuads(text: String, scale: Float, maxWidthPx: Float): Int {
        if (maxWidthPx <= 0f) return 0
        var cursorX = 0f
        var quads = 0
        for (ch in text) {
            val glyphW = (glyphs[ch.code] ?: fallback).advanceX * scale
            if (cursorX + glyphW > maxWidthPx + 0.5f) break
            cursorX += glyphW
            quads++
        }
        return quads
    }

    private fun emitText(
        out: FloatArray,
        offset: Int,
        text: String,
        startX: Float,
        startY: Float,
        scale: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        maxWidthPx: Float
    ): Int {
        if (maxWidthPx <= 0f) return offset
        var cursorX = startX
        var o = offset
        for (ch in text) {
            val glyph = glyphs[ch.code] ?: fallback
            val glyphW = glyph.advanceX * scale
            if (cursorX + glyphW > startX + maxWidthPx + 0.5f) break
            val x0 = cursorX
            val y0 = startY
            val x1 = cursorX + glyph.widthPx * scale
            val y1 = startY + glyph.heightPx * scale
            // Triangle 1: (x0, y0), (x1, y0), (x0, y1)
            o = putVert(out, o, x0, y0, glyph.u0, glyph.v0, r, g, b, a)
            o = putVert(out, o, x1, y0, glyph.u1, glyph.v0, r, g, b, a)
            o = putVert(out, o, x0, y1, glyph.u0, glyph.v1, r, g, b, a)
            // Triangle 2: (x1, y0), (x1, y1), (x0, y1)
            o = putVert(out, o, x1, y0, glyph.u1, glyph.v0, r, g, b, a)
            o = putVert(out, o, x1, y1, glyph.u1, glyph.v1, r, g, b, a)
            o = putVert(out, o, x0, y1, glyph.u0, glyph.v1, r, g, b, a)
            cursorX += glyphW
        }
        return o
    }

    private fun putVert(
        out: FloatArray,
        o: Int,
        x: Float,
        y: Float,
        u: Float,
        v: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ): Int {
        out[o] = x
        out[o + 1] = y
        out[o + 2] = u
        out[o + 3] = v
        out[o + 4] = r
        out[o + 5] = g
        out[o + 6] = b
        out[o + 7] = a
        return o + 8
    }
}
