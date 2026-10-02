package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextRun
import com.flopster101.siliconplayer.ui.visualization.gl.ScopeTextQuadEmitter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScopeTextQuadsTest {

    private fun glyphTable(): ByteBuffer {
        val buf = ByteBuffer.allocateDirect(3 * 36).order(ByteOrder.nativeOrder())
        fun glyph(code: Int, advance: Float) {
            buf.putInt(code)
            buf.putFloat(0.1f)
            buf.putFloat(0.2f)
            buf.putFloat(0.3f)
            buf.putFloat(0.4f)
            buf.putFloat(advance)
            buf.putFloat(32f)
            buf.putFloat(advance)
            buf.putFloat(26f)
        }
        glyph('A'.code, 20f)
        glyph('B'.code, 22f)
        glyph('?'.code, 18f)
        buf.flip()
        return buf
    }

    private fun emitter() = ScopeTextQuadEmitter(glyphTable(), baseFontSizePx = 32f, lineHeightPx = 30f)

    @Test
    fun measuresWithAdvancesAndFallback() {
        val e = emitter()
        assertEquals(42f, e.measurer.widthOf("AB", 32f), 0.001f)
        assertEquals(21f, e.measurer.widthOf("AB", 16f), 0.001f)
        assertEquals(18f, e.measurer.widthOf("~", 32f), 0.001f)
        assertEquals(30f, e.measurer.lineHeightOf(32f), 0.001f)
    }

    @Test
    fun emitsOneQuadPerGlyphWithShadowDoubled() {
        val e = emitter()
        val runs = listOf(ChannelScopeTextRun("AB", 10f, 20f, 0xFF80D8FF.toInt()))
        val plain = e.emitRuns(runs, 32f, shadowEnabled = false)
        assertEquals(2 * 48, plain.size)
        assertEquals(10f, plain[0], 0.001f)
        assertEquals(20f, plain[1], 0.001f)
        assertEquals(0.1f, plain[2], 0.001f)
        assertEquals(30f, plain[8], 0.001f) // second glyph starts after first advance
        val shadowed = e.emitRuns(runs, 32f, shadowEnabled = true)
        assertEquals(4 * 48, shadowed.size)
        assertEquals(12.8f, shadowed[0], 0.001f)
        assertEquals(22.8f, shadowed[1], 0.001f)
    }

    @Test
    fun emptyRunsEmitNothing() {
        val e = emitter()
        assertTrue(e.emitRuns(emptyList(), 32f, shadowEnabled = true).isEmpty())
    }

    @Test
    fun adaptsShadowOffsetAndAlphaForSmallerText() {
        val e = emitter()
        val runs = listOf(ChannelScopeTextRun("AB", 10f, 20f, 0xFF80D8FF.toInt()))
        val shadowed = e.emitRuns(runs, 16f, shadowEnabled = true)
        assertEquals(4 * 48, shadowed.size)
        assertEquals(11.4f, shadowed[0], 0.001f)
        assertEquals(21.4f, shadowed[1], 0.001f)
        assertEquals(0.50f, shadowed[7], 0.001f)
    }
}
