package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeGainMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelScopeGainMappingTest {

    @Test
    fun testMinAndMaxBounds() {
        assertEquals(0f, ChannelScopeGainMapping.gainPercentToFraction(10), 0.0001f)
        assertEquals(1f, ChannelScopeGainMapping.gainPercentToFraction(5000), 0.0001f)

        assertEquals(0f, ChannelScopeGainMapping.gainPercentToFraction(0), 0.0001f)
        assertEquals(1f, ChannelScopeGainMapping.gainPercentToFraction(10000), 0.0001f)

        assertEquals(10, ChannelScopeGainMapping.fractionToGainPercent(0f))
        assertEquals(5000, ChannelScopeGainMapping.fractionToGainPercent(1f))
    }

    @Test
    fun testKeyMilestonesRoundtrip() {
        val milestones = listOf(10, 25, 50, 100, 200, 240, 500, 1000, 2000, 5000)
        for (expected in milestones) {
            val fraction = ChannelScopeGainMapping.gainPercentToFraction(expected)
            val roundtrip = ChannelScopeGainMapping.fractionToGainPercent(fraction)
            assertEquals("Milestone $expected should roundtrip exactly", expected, roundtrip)
        }
    }

    @Test
    fun testMonotonicityAcrossFullRange() {
        var previousGain = 10
        for (i in 0..1000) {
            val fraction = i / 1000f
            val gain = ChannelScopeGainMapping.fractionToGainPercent(fraction)
            assertTrue("Gain should be non-decreasing at fraction $fraction", gain >= previousGain)
            assertTrue("Gain should be within [10, 5000]", gain in 10..5000)
            previousGain = gain
        }
        assertEquals(5000, previousGain)
    }

    @Test
    fun testNudgeBehavior() {
        assertEquals(250, ChannelScopeGainMapping.nudgeGainPercent(240, increase = true))
        assertEquals(230, ChannelScopeGainMapping.nudgeGainPercent(240, increase = false))

        assertEquals(110, ChannelScopeGainMapping.nudgeGainPercent(100, increase = true))
        assertEquals(90, ChannelScopeGainMapping.nudgeGainPercent(100, increase = false))

        assertEquals(1050, ChannelScopeGainMapping.nudgeGainPercent(1000, increase = true))
        assertEquals(950, ChannelScopeGainMapping.nudgeGainPercent(1000, increase = false))

        assertEquals(10, ChannelScopeGainMapping.nudgeGainPercent(10, increase = false))
        assertEquals(5000, ChannelScopeGainMapping.nudgeGainPercent(5000, increase = true))
    }
}
