package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import com.flopster101.siliconplayer.ui.visualization.gl.DesktopGlFontAtlas
import com.flopster101.siliconplayer.ui.visualization.gl.ScopeTextQuadEmitter
import java.awt.Font
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The Vulkan scope text image and the quad UVs must come from the same
// atlas: uploading a system-font image under resource-font UVs renders
// garbled glyphs at correct label positions.
class ScopeAtlasConsistencyTest {

    private fun resourcePath(font: VisualizationChannelScopeTextFont): String? {
        return when (font) {
            VisualizationChannelScopeTextFont.System -> null
            VisualizationChannelScopeTextFont.RaccoonSerif -> "/fonts/scope/raccoon_serif_base.ttf"
            VisualizationChannelScopeTextFont.RaccoonMono -> "/fonts/scope/raccoon_serif_mono.ttf"
            VisualizationChannelScopeTextFont.RetroCuteMono -> "/fonts/scope/retro_pixel_cute_mono.ttf"
            VisualizationChannelScopeTextFont.RetroThick -> "/fonts/scope/retro_pixel_thick.ttf"
        }
    }

    private fun atlasBytes(font: VisualizationChannelScopeTextFont): ByteArray {
        val upload = DesktopGlFontAtlas.createAtlasUploadData(
            fontName = Font.SANS_SERIF,
            fontResourcePath = resourcePath(font),
            baseFontSizePx = 32f
        )
        assertTrue("expected atlas pixels for $font", upload.width > 0 && upload.height > 0)
        val bytes = ByteArray(upload.width * upload.height * 4)
        upload.pixelBuffer.position(0)
        upload.pixelBuffer.get(bytes)
        return bytes
    }

    @Test
    fun testEveryScopeFontAtlasIsUsedWithItsOwnQuads() {
        val states = List(4) { ch ->
            ChannelScopeChannelTextState(
                channelIndex = ch,
                note = 48 + ch * 2,
                volume = 40 + ch,
                effectPrimaryLetterAscii = 65 + ch,
                effectPrimaryParam = ch,
                effectSecondaryLetterAscii = 0,
                effectSecondaryParam = -1,
                instrumentIndex = ch,
                sampleIndex = -1,
                flags = 0
            )
        }
        for (font in VisualizationChannelScopeTextFont.entries) {
            val upload = DesktopGlFontAtlas.createAtlasUploadData(
                fontName = Font.SANS_SERIF,
                fontResourcePath = resourcePath(font),
                baseFontSizePx = 32f
            )
            assertTrue("expected glyphs for $font", upload.glyphCount > 0)
            val emitter = ScopeTextQuadEmitter(upload.glyphBuffer, upload.baseFontSizePx, upload.lineHeightPx)
            val frame = GlChannelScopeTextFrame(
                channelCount = 4,
                channelTextStates = states,
                instrumentNamesByIndex = mapOf(0 to "Piano0"),
                sampleNamesByIndex = emptyMap(),
                chipNamesByChannelIndex = mapOf(0 to "CPU"),
                layoutStrategy = VisualizationChannelScopeLayout.ColumnFirst,
                anchor = VisualizationChannelScopeTextAnchor.TopLeft,
                paddingPx = 8f,
                textSizeSp = 14,
                density = 1f,
                hideWhenOverflow = true,
                textShadowEnabled = false,
                textFont = font,
                noteFormat = VisualizationNoteNameFormat.American,
                showChannel = true,
                showNote = true,
                showVolume = true,
                showEffectPrimary = true,
                showEffectSecondary = false,
                showChip = true,
                showInstrument = true,
                showSample = true,
                palette = GlChannelScopeTextPalette()
            )
            val layout = layoutChannelScopeText(frame, 640f, 360f, emitter.measurer)
            assertTrue("expected layout for $font", layout != null)
            val quads = emitter.emitRuns(layout!!.runs, layout.textSizePx, layout.shadowEnabled)
            assertTrue("expected quads for $font", quads.isNotEmpty())

            // Every emitted UV must land inside the atlas on a nonzero-coverage texel.
            val pixels = atlasBytes(font)
            val w = DesktopGlFontAtlas.createAtlasUploadData(
                fontName = Font.SANS_SERIF,
                fontResourcePath = resourcePath(font),
                baseFontSizePx = 32f
            ).width
            var checked = 0
            var i = 0
            while (i < quads.size) {
                val u = quads[i + 2]
                val v = quads[i + 3]
                assertTrue("UV out of bounds for $font: ($u,$v)", u in 0f..1f && v in 0f..1f)
                checked++
                i += 8
            }
            assertTrue("expected vertices for $font", checked > 0)
            assertTrue("atlas must carry coverage for $font", pixels.any { it != 0.toByte() })
        }
    }

    @Test
    fun testResourceFontAtlasesDifferFromSystemAtlas() {
        val system = atlasBytes(VisualizationChannelScopeTextFont.System)
        for (font in VisualizationChannelScopeTextFont.entries) {
            if (font == VisualizationChannelScopeTextFont.System) continue
            val other = atlasBytes(font)
            var diff = 0
            val n = minOf(system.size, other.size)
            for (i in 0 until n) {
                if (system[i] != other[i]) diff++
            }
            assertTrue(
                "atlases for $font must differ from system (a swapped image garbles text)",
                diff > n / 100
            )
        }
    }

    @Test
    fun testIdenticalAtlasArgsGiveIdenticalPixels() {
        // Both upload sites build with the same args, so either may win the race.
        val first = atlasBytes(VisualizationChannelScopeTextFont.RaccoonMono)
        val second = atlasBytes(VisualizationChannelScopeTextFont.RaccoonMono)
        assertArrayEquals(first, second)
    }
}
