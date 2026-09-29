package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlFontAtlas
import java.awt.Font
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopGlFontAtlasTest {
    private data class GlyphCell(val code: Int, val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    private fun parseGlyphs(upload: DesktopGlFontAtlas.AtlasUploadData): List<GlyphCell> {
        val cells = ArrayList<GlyphCell>()
        val dup = upload.glyphBuffer.duplicate().order(ByteOrder.nativeOrder())
        while (dup.remaining() >= 36) {
            val code = dup.int
            val u0 = dup.float
            val v0 = dup.float
            val u1 = dup.float
            val v1 = dup.float
            dup.float
            dup.float
            dup.float
            dup.float
            cells.add(GlyphCell(code, u0, v0, u1, v1))
        }
        return cells
    }

    private fun inkPixels(upload: DesktopGlFontAtlas.AtlasUploadData, cell: GlyphCell): Int {
        val px = upload.pixelBuffer.duplicate().order(ByteOrder.nativeOrder())
        val x0 = (cell.u0 * upload.width).toInt().coerceIn(0, upload.width)
        val y0 = (cell.v0 * upload.height).toInt().coerceIn(0, upload.height)
        val x1 = (cell.u1 * upload.width).toInt().coerceIn(0, upload.width)
        val y1 = (cell.v1 * upload.height).toInt().coerceIn(0, upload.height)
        var lit = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                if ((px.get((y * upload.width + x) * 4 + 3).toInt() and 0xFF) > 16) lit++
            }
        }
        return lit
    }

    @Test
    fun bundledVuFontResourceIsOnTheClasspath() {
        val stream = DesktopGlFontAtlas::class.java.getResourceAsStream("/fonts/vumeters/roboto_medium.ttf")
        assertTrue("roboto_medium.ttf missing from resources", stream != null)
        stream?.close()
    }

    @Test
    fun allScopeFontsKeepEveryGlyphOnAtlas() {
        val fonts = listOf(
            Pair(null, true),
            Pair("/fonts/scope/raccoon_serif_base.ttf", true),
            Pair("/fonts/scope/raccoon_serif_mono.ttf", true),
            Pair("/fonts/scope/retro_pixel_cute_mono.ttf", true),
            Pair("/fonts/scope/retro_pixel_thick.ttf", true),
            Pair("/fonts/vumeters/roboto_medium.ttf", false)
        )
        for ((resourcePath, bold) in fonts) {
            val upload = DesktopGlFontAtlas.createAtlasUploadData(
                fontName = Font.SANS_SERIF,
                fontResourcePath = resourcePath,
                baseFontSizePx = 32f,
                bold = bold
            )
            val cells = parseGlyphs(upload)
            assertEquals(resourcePath, 122, cells.size)
            assertEquals(resourcePath, 122, upload.glyphCount)
            for (cell in cells) {
                assertTrue("$resourcePath U+${cell.code} u0=${cell.u0}", cell.u0 in 0f..1f)
                assertTrue("$resourcePath U+${cell.code} u1=${cell.u1}", cell.u1 in 0f..1f)
                assertTrue("$resourcePath U+${cell.code} v0=${cell.v0}", cell.v0 in 0f..1f)
                assertTrue("$resourcePath U+${cell.code} v1=${cell.v1}", cell.v1 in 0f..1f)
            }
            for (code in 33..126) {
                val cell = cells.first { it.code == code }
                assertTrue("$resourcePath U+$code has no ink", inkPixels(upload, cell) > 0)
            }
        }
    }
}
