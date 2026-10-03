package com.flopster101.siliconplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtuneTransportTest {

    @Test
    fun plainTrackFallsThroughToQueue() {
        val state = SubtuneTransportState(
            hasTrack = true,
            subtuneCount = 0,
            canPreviousTrack = true,
            canNextTrack = true
        )
        assertFalse(state.useSubtuneTransport)
        assertFalse(state.hasSubtuneBefore)
        assertFalse(state.hasSubtuneAfter)
        assertFalse(state.previousWantsRestart(30.0))
        assertTrue(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }

    @Test
    fun singleSubtuneBehavesLikePlainTrack() {
        val state = SubtuneTransportState(
            hasTrack = true,
            currentSubtuneIndex = 0,
            subtuneCount = 1,
            canPreviousTrack = false,
            canNextTrack = true
        )
        assertFalse(state.useSubtuneTransport)
        assertFalse(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }

    @Test
    fun midSubtuneStepsThroughSubtunes() {
        val state = SubtuneTransportState(
            hasTrack = true,
            currentSubtuneIndex = 1,
            subtuneCount = 3,
            canPreviousSubtune = true,
            canNextSubtune = true,
            canPreviousTrack = false,
            canNextTrack = false
        )
        assertTrue(state.useSubtuneTransport)
        assertTrue(state.hasSubtuneBefore)
        assertTrue(state.hasSubtuneAfter)
        assertTrue(state.previousEnabled)
        assertTrue(state.nextEnabled)
    }

    @Test
    fun firstSubtuneHasNoBefore() {
        val state = SubtuneTransportState(
            hasTrack = true,
            currentSubtuneIndex = 0,
            subtuneCount = 3,
            canPreviousSubtune = true,
            canNextSubtune = true
        )
        assertFalse(state.hasSubtuneBefore)
        assertTrue(state.hasSubtuneAfter)
    }

    @Test
    fun previousRestartsPastThreshold() {
        val state = SubtuneTransportState(
            hasTrack = true,
            currentSubtuneIndex = 1,
            subtuneCount = 3,
            canPreviousSubtune = true
        )
        assertTrue(state.previousWantsRestart(30.0))
        assertFalse(state.previousWantsRestart(1.0))
    }

    @Test
    fun restartDisabledWithoutThresholdPref() {
        val state = SubtuneTransportState(
            hasTrack = true,
            currentSubtuneIndex = 1,
            subtuneCount = 3,
            canPreviousSubtune = true,
            previousRestartsAfterThreshold = false
        )
        assertFalse(state.previousWantsRestart(30.0))
    }

    @Test
    fun noTrackDisablesEverything() {
        val state = SubtuneTransportState(
            hasTrack = false,
            currentSubtuneIndex = 1,
            subtuneCount = 3,
            canPreviousSubtune = true,
            canNextSubtune = true
        )
        assertFalse(state.previousEnabled)
        assertFalse(state.nextEnabled)
        assertFalse(state.previousWantsRestart(30.0))
    }
}
