package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextMeasurer
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelScopeSeparatorTest {
    private val measurer = object : ChannelScopeTextMeasurer {
        override fun widthOf(text: String, textSizePx: Float): Float = text.length * 6f
        override fun lineHeightOf(textSizePx: Float): Float = 12f
    }

    private fun frame(
        states: List<ChannelScopeChannelTextState>,
        showNote: Boolean = true,
        showVolume: Boolean = false,
        showInstrument: Boolean = false,
        instrumentNames: Map<Int, String> = emptyMap()
    ) = GlChannelScopeTextFrame(
        channelCount = states.size,
        channelTextStates = states,
        instrumentNamesByIndex = instrumentNames,
        sampleNamesByIndex = emptyMap(),
        chipNamesByChannelIndex = emptyMap(),
        layoutStrategy = VisualizationChannelScopeLayout.ColumnFirst,
        anchor = VisualizationChannelScopeTextAnchor.TopLeft,
        paddingPx = 4f,
        textSizeSp = 8,
        density = 1f,
        hideWhenOverflow = false,
        textShadowEnabled = false,
        textFont = VisualizationChannelScopeTextFont.System,
        noteFormat = VisualizationNoteNameFormat.American,
        showChannel = true,
        showNote = showNote,
        showVolume = showVolume,
        showEffectPrimary = false,
        showEffectSecondary = false,
        showChip = false,
        showInstrument = showInstrument,
        showSample = false,
        palette = GlChannelScopeTextPalette()
    )

    @Test
    fun separatorSplitsLeadOfCenteredNote() {
        // "Ch 1" (24) + spacing: cursor 30. Note "C4" (12) in a 24 slot
        // leaves lead 6, so the dot moves half of it right.
        val state = ChannelScopeChannelTextState(0, 49, 0, 0, -1, 0, -1, -1, -1, 0)
        val layout = layoutChannelScopeText(frame(listOf(state)), 800f, 100f, measurer)!!
        val bullet = layout.runs.first { it.text == "•" }
        assertEquals(33f, bullet.xPx, 0.001f)
        val note = layout.runs.first { it.text == "C4" }
        assertEquals(44f, note.xPx, 0.001f)
    }

    @Test
    fun separatorCentersBetweenTwoCenteredSlots() {
        // Note lead 6 becomes the previous trail; volume "V051" (24) in a
        // 30 slot leaves lead 3: dot at 64 + (3 - 6) / 2.
        val state = ChannelScopeChannelTextState(0, 49, 51, 0, -1, 0, -1, -1, -1, 0)
        val layout = layoutChannelScopeText(
            frame(states = listOf(state), showVolume = true),
            800f,
            100f,
            measurer
        )!!
        val bullets = layout.runs.filter { it.text == "•" }
        assertEquals(2, bullets.size)
        assertEquals(33f, bullets[0].xPx, 0.001f)
        assertEquals(62.5f, bullets[1].xPx, 0.001f)
    }

    @Test
    fun separatorBeforeLeftAlignedFieldStaysAtSlotStart() {
        // Instrument text is left-aligned (no lead, no trail), so the dot
        // keeps the slot start exactly.
        val state = ChannelScopeChannelTextState(0, -1, 0, 0, -1, 0, -1, 1, -1, 0)
        val layout = layoutChannelScopeText(
            frame(states = listOf(state), showNote = false, showInstrument = true, instrumentNames = mapOf(1 to "Drums")),
            800f,
            100f,
            measurer
        )!!
        val bullet = layout.runs.first { it.text == "•" }
        assertEquals(30f, bullet.xPx, 0.001f)
    }
}
