package com.flopster101.siliconplayer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviousTrackNavigationTest {

    @Test
    fun previousRestartsCurrentTrackAfterThreshold() {
        assertTrue(
            shouldRestartCurrentTrackOnPrevious(
                previousRestartsAfterThreshold = true,
                hasTrackLoaded = true,
                positionSeconds = PREVIOUS_RESTART_THRESHOLD_SECONDS + 0.01
            )
        )
    }

    @Test
    fun previousDoesNotRestartAtThresholdBoundary() {
        assertFalse(
            shouldRestartCurrentTrackOnPrevious(
                previousRestartsAfterThreshold = true,
                hasTrackLoaded = true,
                positionSeconds = PREVIOUS_RESTART_THRESHOLD_SECONDS
            )
        )
    }

    @Test
    fun previousPrefersRestartAfterThresholdBeforePreviousTrack() {
        val action = resolvePreviousTrackAction(
            previousRestartsAfterThreshold = true,
            hasTrackLoaded = true,
            positionSeconds = PREVIOUS_RESTART_THRESHOLD_SECONDS + 0.01,
            hasPreviousTrack = true
        )

        assertEquals(PreviousTrackAction.RestartCurrent, action)
    }

    @Test
    fun previousPlaysPreviousTrackBeforeThresholdWhenAvailable() {
        val action = resolvePreviousTrackAction(
            previousRestartsAfterThreshold = true,
            hasTrackLoaded = true,
            positionSeconds = 1.0,
            hasPreviousTrack = true
        )

        assertEquals(PreviousTrackAction.PlayPreviousTrack, action)
    }

    @Test
    fun previousRestartsCurrentTrackWhenNoPreviousTrackExists() {
        val action = resolvePreviousTrackAction(
            previousRestartsAfterThreshold = true,
            hasTrackLoaded = true,
            positionSeconds = 1.0,
            hasPreviousTrack = false
        )

        assertEquals(PreviousTrackAction.RestartCurrent, action)
    }
}
