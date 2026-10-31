package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameSource
import com.flopster101.siliconplayer.ui.visualization.channel.loadChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.channel.parseChannelScopeTextStates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelScopeTextLayoutTest {

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
            override fun dnfamitrackerInstrumentNames() = ""
            override fun dnfamitrackerSampleNames() = ""
            override fun decoderToggleChannelNames() = arrayOf("Pulse 1", "Pulse 2", "Triangle")
        }
        val maps = loadChannelScopeNameMaps(DecoderNames.FURNACE, source)
        assertEquals("Lead", maps.instrumentNamesByIndex[1])
        assertEquals("Bass", maps.instrumentNamesByIndex[2])
        assertEquals("Snare", maps.sampleNamesByIndex[1])
        assertEquals("Pulse 1", maps.chipNamesByChannelIndex[0])
        assertEquals("Pulse 2", maps.chipNamesByChannelIndex[1])
        assertEquals("Triangle", maps.chipNamesByChannelIndex[2])
        // Row labels come from the decoder regardless of the named maps.
        val unnamed = loadChannelScopeNameMaps("Nope", source)
        assertEquals("Pulse 1", unnamed.chipNamesByChannelIndex[0])
        assertTrue(unnamed.instrumentNamesByIndex.isEmpty())
        assertTrue(unnamed.sampleNamesByIndex.isEmpty())
    }
}
