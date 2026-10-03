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

    @Test
    fun fallbackBulletIsCenteredWithNormalizedAdvance() {
        // The bundled scope fonts lack U+2022, so the builder generates
        // the dot; it must sit centered in a normalized advance with no
        // ink spilling into neighboring cells.
        val fonts = listOf(
            Pair(null, true),
            Pair("/fonts/scope/raccoon_serif_base.ttf", true),
            Pair("/fonts/scope/raccoon_serif_mono.ttf", true),
            Pair("/fonts/scope/retro_pixel_cute_mono.ttf", true),
            Pair("/fonts/scope/retro_pixel_thick.ttf", true)
        )
        for ((resourcePath, bold) in fonts) {
            val upload = DesktopGlFontAtlas.createAtlasUploadData(
                fontName = Font.SANS_SERIF,
                fontResourcePath = resourcePath,
                baseFontSizePx = 32f,
                bold = bold
            )
            val dup = upload.glyphBuffer.duplicate().order(ByteOrder.nativeOrder())
            var sawBullet = false
            while (dup.remaining() >= 36) {
                val code = dup.int
                val u0 = dup.float
                val v0 = dup.float
                val u1 = dup.float
                val v1 = dup.float
                dup.float
                dup.float
                val advance = dup.float
                dup.float
                if (code != 0x2022) continue
                sawBullet = true
                if (resourcePath != null) {
                    assertEquals(resourcePath, 32f * 0.375f, advance, 0.01f)
                }
                val bounds = inkBounds(upload, u0, v0, u1, v1)
                assertTrue("$resourcePath bullet has no ink", bounds[4] > 0)
                val slotLeft = u0 * upload.width
                val slotRight = u1 * upload.width
                val slotTop = v0 * upload.height
                val slotBottom = v1 * upload.height
                assertEquals(resourcePath, (slotLeft + slotRight) / 2f, (bounds[0] + bounds[2]) / 2f, 1.5f)
                assertTrue("$resourcePath bullet spills left", bounds[0] >= slotLeft - 0.5f)
                assertTrue("$resourcePath bullet spills right", bounds[2] <= slotRight + 0.5f)
                assertTrue("$resourcePath bullet spills top", bounds[1] >= slotTop - 0.5f)
                assertTrue("$resourcePath bullet spills bottom", bounds[3] <= slotBottom + 0.5f)
            }
            assertTrue("$resourcePath bullet missing", sawBullet)
        }
    }

    // left, top, right, bottom, litCount of alpha > 16 px in the UV rect.
    private fun inkBounds(
        upload: DesktopGlFontAtlas.AtlasUploadData,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float
    ): FloatArray {
        val px = upload.pixelBuffer.duplicate().order(ByteOrder.nativeOrder())
        val x0 = (u0 * upload.width).toInt().coerceIn(0, upload.width)
        val y0 = (v0 * upload.height).toInt().coerceIn(0, upload.height)
        val x1 = (u1 * upload.width).toInt().coerceIn(0, upload.width)
        val y1 = (v1 * upload.height).toInt().coerceIn(0, upload.height)
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = Int.MIN_VALUE
        var b = Int.MIN_VALUE
        var lit = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                if ((px.get((y * upload.width + x) * 4 + 3).toInt() and 0xFF) > 16) {
                    lit++
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        return floatArrayOf(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), lit.toFloat())
    }
}
