package com.flopster101.siliconplayer.ui.visualization.gl

import com.flopster101.siliconplayer.ui.visualization.channel.SCOPE_TEXT_BULLET_ADVANCE_RATIO
import com.flopster101.siliconplayer.ui.visualization.channel.SCOPE_TEXT_BULLET_RAISE_RATIO
import com.flopster101.siliconplayer.ui.visualization.channel.SCOPE_TEXT_BULLET_RADIUS_RATIO
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
        fontResourcePath: String? = null,
        baseFontSizePx: Float = 32f,
        bold: Boolean = true
    ): AtlasUploadData {
        val requestedSize = baseFontSizePx.toInt().coerceAtLeast(12)
        val style = if (bold) Font.BOLD else Font.PLAIN
        val font = if (fontResourcePath != null) {
            runCatching {
                DesktopGlFontAtlas.javaClass.getResourceAsStream(fontResourcePath)?.use {
                    Font.createFont(Font.TRUETYPE_FONT, it).deriveFont(style, requestedSize.toFloat())
                }
            }.getOrNull()
                ?: return createAtlasUploadData(fontName = fontName, baseFontSizePx = baseFontSizePx, bold = bold)
        } else {
            Font(fontName, style, requestedSize)
        }
        val dummyImg = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val dummyG = dummyImg.createGraphics()
        dummyG.font = font
        val fm = dummyG.fontMetrics
        val fontAscent = fm.ascent.toFloat()
        val fontDescent = fm.descent.toFloat()
        val measuredLineHeight = fontAscent + fontDescent
        // The bundled pixel fonts miss symbols Android backfills from the
        // system font (sharps, box drawing); those cells still rasterize
        // from a fallback face like Android's Minikin fallback does. The
        // bullet is generated instead: fallback metrics vary by device,
        // so a centered dot with a normalized advance keeps it identical
        // on both platforms.
        val fallbackFont = Font(Font.SANS_SERIF, style, requestedSize)
        dummyG.font = fallbackFont
        val fallbackFm = dummyG.fontMetrics
        dummyG.dispose()
        fun drawFontFor(ch: Char) = if (font.canDisplay(ch)) font else fallbackFont
        fun advanceFor(ch: Char): Float {
            if (ch == '\u2022' && !font.canDisplay(ch)) return baseFontSizePx * SCOPE_TEXT_BULLET_ADVANCE_RATIO
            return (if (font.canDisplay(ch)) fm else fallbackFm).charWidth(ch).toFloat()
        }

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
            val adv = advanceFor(c)
            if (adv > maxAdvance) maxAdvance = adv
        }
        val cellW = ceil(maxAdvance + (padding * 2)).toInt().coerceAtLeast(16)
        val cellH = ceil(measuredLineHeight + (padding * 2)).toInt().coerceAtLeast(16)
        val atlasW = 512
        // Grid columns must fit the atlas: a wide cell (bold/large face)
        // would otherwise push the last column off-atlas, leaving its
        // glyphs (u0 > 1) blank. Shrink the grid instead of overflowing.
        val cols = min(16, (atlasW / cellW).coerceAtLeast(1))
        val rows = ceil(chars.size.toDouble() / cols.toDouble()).toInt()
        val atlasH = max(256, (rows * cellH + 31) / 32 * 32)

        val atlasImg = BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB)
        val g2d = atlasImg.createGraphics()
        g2d.font = font
        g2d.color = Color.WHITE
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)

        // struct Glyph: codepoint(int), u0, v0, u1, v1, widthPx, heightPx, advanceX, ascentPx (36 bytes)
        val glyphBuffer = ByteBuffer.allocateDirect(chars.size * 36).order(ByteOrder.nativeOrder())

        var col = 0
        var row = 0
        for (ch in chars) {
            val adv = advanceFor(ch).coerceAtLeast(1f)
            val x = col * cellW + padding
            val y = row * cellH + padding
            val drawY = (y + fontAscent).toInt()

            if (ch == '\u2022' && !font.canDisplay(ch)) {
                val r = adv * SCOPE_TEXT_BULLET_RADIUS_RATIO
                val cx = x + adv * 0.5f
                val cy = drawY - baseFontSizePx * SCOPE_TEXT_BULLET_RAISE_RATIO
                g2d.fillOval((cx - r).toInt(), (cy - r).toInt(), (r * 2f).toInt().coerceAtLeast(1), (r * 2f).toInt().coerceAtLeast(1))
            } else {
                g2d.font = drawFontFor(ch)
                g2d.drawString(ch.toString(), x, drawY)
            }

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
