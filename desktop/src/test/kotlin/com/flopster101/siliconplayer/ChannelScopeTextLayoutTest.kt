package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameSource
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextMeasurer
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import com.flopster101.siliconplayer.ui.visualization.channel.loadChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.channel.parseChannelScopeTextStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelScopeTextLayoutTest {

    private val measurer = object : ChannelScopeTextMeasurer {
        override fun widthOf(text: String, textSizePx: Float): Float = text.length * (textSizePx / 2f)
        override fun lineHeightOf(textSizePx: Float): Float = textSizePx * 1.25f
    }

    private fun frame(
        states: List<ChannelScopeChannelTextState>,
        widthPx: Float = 800f,
        heightPx: Float = 600f
    ) = GlChannelScopeTextFrame(
        channelCount = states.size,
        channelTextStates = states,
        instrumentNamesByIndex = mapOf(1 to "Piano"),
        sampleNamesByIndex = mapOf(2 to "Kick"),
        chipNamesByChannelIndex = emptyMap(),
        layoutStrategy = VisualizationChannelScopeLayout.ColumnFirst,
        anchor = VisualizationChannelScopeTextAnchor.TopLeft,
        paddingPx = 6f,
        textSizeSp = 8,
        density = 2f,
        hideWhenOverflow = false,
        textShadowEnabled = true,
        textFont = VisualizationChannelScopeTextFont.System,
        noteFormat = VisualizationNoteNameFormat.American,
        showChannel = true,
        showNote = true,
        showVolume = true,
        showEffectPrimary = true,
        showEffectSecondary = false,
        showChip = false,
        showInstrument = true,
        showSample = true,
        palette = GlChannelScopeTextPalette()
    )

    private fun state(
        note: Int = 49,
        volume: Int = 64,
        instrumentIndex: Int = 1,
        sampleIndex: Int = 2
    ) = ChannelScopeChannelTextState(
        channelIndex = 0,
        note = note,
        volume = volume,
        effectPrimaryLetterAscii = 'A'.code,
        effectPrimaryParam = 0,
        effectSecondaryLetterAscii = 0,
        effectSecondaryParam = -1,
        instrumentIndex = instrumentIndex,
        sampleIndex = sampleIndex,
        flags = 0
    )

    @Test
    fun laysOutChannelNoteVolumeAndInstrumentRuns() {
        val layout = layoutChannelScopeText(frame(listOf(state(), state(), state(), state())), 800f, 600f, measurer)
        assertNotNull(layout)
        val texts = layout!!.runs.map { it.text }
        assertTrue(texts.contains("Ch 1"))
        assertTrue(texts.contains("C4"))
        assertTrue(texts.contains("V064"))
        assertTrue(texts.any { it.startsWith("I#1 Piano") })
        assertTrue(layout.shadowEnabled)
        assertTrue(layout.textSizePx > 0f)
    }

    @Test
    fun hidesEverythingWhenOverflowForbidden() {
        val tiny = frame(listOf(state())).copy(hideWhenOverflow = true)
        assertNull(layoutChannelScopeText(tiny, 20f, 20f, measurer))
    }

    @Test
    fun emptySurfaceYieldsNoLayout() {
        assertNull(layoutChannelScopeText(frame(listOf(state())), 0f, 600f, measurer))
        assertNull(layoutChannelScopeText(frame(emptyList()), 800f, 600f, measurer))
    }

    @Test
    fun parsesPackedTextStates() {
        val flat = IntArray(20) { -1 }
        flat[0] = 3
        flat[1] = 61
        flat[2] = 100
        flat[10] = 5
        val parsed = parseChannelScopeTextStates(flat)
        assertEquals(2, parsed.size)
        assertEquals(3, parsed[0].channelIndex)
        assertEquals(61, parsed[0].note)
        assertEquals(100, parsed[0].volume)
        assertEquals(5, parsed[1].channelIndex)
    }

    @Test
    fun loadsFurnaceNameMaps() {
        val source = object : ChannelScopeNameSource {
            override fun openMptInstrumentNames() = ""
            override fun openMptSampleNames() = ""
            override fun xmpInstrumentNames() = ""
            override fun xmpSampleNames() = ""
            override fun furnaceInstrumentNames() = "1. Lead\n2. Bass\n"
            override fun furnaceSampleNames() = "1. Snare\n"
            override fun klystrackInstrumentNames() = ""
            override fun hivelyInstrumentNames() = ""
            override fun decoderToggleChannelNames() = emptyArray<String>()
        }
        val maps = loadChannelScopeNameMaps(DecoderNames.FURNACE, source)
        assertEquals("Lead", maps.instrumentNamesByIndex[1])
        assertEquals("Bass", maps.instrumentNamesByIndex[2])
        assertEquals("Snare", maps.sampleNamesByIndex[1])
        assertTrue(maps.chipNamesByChannelIndex.isEmpty())
        val empty = loadChannelScopeNameMaps("Nope", source)
        assertEquals(ChannelScopeNameMaps(), empty)
    }
}
