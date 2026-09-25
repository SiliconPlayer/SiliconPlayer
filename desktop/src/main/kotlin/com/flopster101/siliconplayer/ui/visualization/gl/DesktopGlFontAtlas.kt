package com.flopster101.siliconplayer.ui.visualization.gl

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max

internal object DesktopGlFontAtlas {
    data class AtlasUploadData(
        val pixelBuffer: ByteBuffer,
        val width: Int,
        val height: Int,
        val baseFontSizePx: Float,
        val lineHeightPx: Float,
        val glyphBuffer: ByteBuffer,
        val glyphCount: Int
    )

    fun createAtlasUploadData(
        fontName: String = Font.MONOSPACED,
        baseFontSizePx: Float = 32f
    ): AtlasUploadData {
        val font = Font(fontName, Font.BOLD, baseFontSizePx.toInt().coerceAtLeast(12))
        val dummyImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val dummyG = dummyImg.createGraphics()
        dummyG.font = font
        val fm = dummyG.fontMetrics
        val fontAscent = fm.ascent.toFloat()
        val fontDescent = fm.descent.toFloat()
        val measuredLineHeight = fontAscent + fontDescent
        dummyG.dispose()

        val chars = ArrayList<Char>(160)
        for (c in 32..126) chars.add(c.toChar())
        val extraChars = charArrayOf(
            '▲', '▼', '◄', '►', '■', '□', '▪', '▫',
            '│', '─', '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼',
            '°', '±', '·', '•', '…', '♯', '♭', '?'
        )
        for (c in extraChars) chars.add(c)

        val padding = 2
        var maxAdvance = fm.stringWidth("W").toFloat()
        for (c in chars) {
            val adv = fm.charWidth(c).toFloat()
            if (adv > maxAdvance) maxAdvance = adv
        }
        val cellW = ceil(maxAdvance + (padding * 2)).toInt().coerceAtLeast(16)
        val cellH = ceil(measuredLineHeight + (padding * 2)).toInt().coerceAtLeast(16)
        val cols = 16
        val rows = ceil(chars.size.toDouble() / cols.toDouble()).toInt()
        val atlasW = 512
        val atlasH = max(256, (rows * cellH + 31) / 32 * 32)

        val atlasImg = BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB)
        val g2d = atlasImg.createGraphics()
        g2d.font = font
        g2d.color = Color.WHITE
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        // struct Glyph: codepoint(int), u0, v0, u1, v1, widthPx, heightPx, advanceX, ascentPx (36 bytes)
        val glyphBuffer = ByteBuffer.allocateDirect(chars.size * 36).order(ByteOrder.nativeOrder())

        var col = 0
        var row = 0
        for (ch in chars) {
            val adv = fm.charWidth(ch).toFloat().coerceAtLeast(1f)
            val x = col * cellW + padding
            val y = row * cellH + padding
            val drawY = (y + fontAscent).toInt()

            g2d.drawString(ch.toString(), x, drawY)

            val u0 = x.toFloat() / atlasW.toFloat()
            val v0 = y.toFloat() / atlasH.toFloat()
            val u1 = (x + adv) / atlasW.toFloat()
            val v1 = (y + measuredLineHeight) / atlasH.toFloat()

            glyphBuffer.putInt(ch.code)
            glyphBuffer.putFloat(u0)
            glyphBuffer.putFloat(v0)
            glyphBuffer.putFloat(u1)
            glyphBuffer.putFloat(v1)
            glyphBuffer.putFloat(adv)
            glyphBuffer.putFloat(measuredLineHeight)
            glyphBuffer.putFloat(adv)
            glyphBuffer.putFloat(fontAscent)

            col++
            if (col >= cols) {
                col = 0
                row++
            }
        }
        g2d.dispose()
        glyphBuffer.flip()

        val pixelBuffer = ByteBuffer.allocateDirect(atlasW * atlasH * 4).order(ByteOrder.nativeOrder())
        val intPixels = (atlasImg.raster.dataBuffer as DataBufferInt).data
        for (pixel in intPixels) {
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            pixelBuffer.put(r.toByte())
            pixelBuffer.put(g.toByte())
            pixelBuffer.put(b.toByte())
            pixelBuffer.put(a.toByte())
        }
        pixelBuffer.flip()

        return AtlasUploadData(
            pixelBuffer = pixelBuffer,
            width = atlasW,
            height = atlasH,
            baseFontSizePx = baseFontSizePx,
            lineHeightPx = measuredLineHeight,
            glyphBuffer = glyphBuffer,
            glyphCount = chars.size
        )
    }
}
