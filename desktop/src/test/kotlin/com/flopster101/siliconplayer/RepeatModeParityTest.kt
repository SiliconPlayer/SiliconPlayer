package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeParityTest {

    @Test
    fun nativeValuesMatchEngineRepeatContract() {
        // AudioEngine.setRepeatMode clamps to 0..3; 2 is loop-point mode.
        assertEquals(0, RepeatMode.None.nativeValue)
        assertEquals(1, RepeatMode.Track.nativeValue)
        assertEquals(2, RepeatMode.LoopPoint.nativeValue)
        assertEquals(3, RepeatMode.Subtune.nativeValue)
        assertEquals(0, RepeatMode.Playlist.nativeValue)
    }

    @Test
    fun resolveDegradesUnsupportedModes() {
        assertEquals(
            RepeatMode.Playlist,
            resolveActiveRepeatMode(RepeatMode.Playlist, REPEAT_CAP_ALL)
        )
        assertEquals(
            RepeatMode.Track,
            resolveActiveRepeatMode(RepeatMode.Subtune, REPEAT_CAP_ALL, includeSubtuneRepeat = false)
        )
        assertEquals(
            RepeatMode.Track,
            resolveActiveRepeatMode(RepeatMode.LoopPoint, REPEAT_CAP_TRACK)
        )
    }

    @Test
    fun cycleOrderMatchesAndroid() {
        assertEquals(
            RepeatMode.Track,
            cycleRepeatModeValue(RepeatMode.None, REPEAT_CAP_TRACK)
        )
        assertEquals(
            RepeatMode.Playlist,
            cycleRepeatModeValue(RepeatMode.Track, REPEAT_CAP_TRACK)
        )
        assertEquals(
            RepeatMode.Subtune,
            cycleRepeatModeValue(RepeatMode.Track, REPEAT_CAP_ALL, includeSubtuneRepeat = true)
        )
        assertEquals(
            RepeatMode.LoopPoint,
            cycleRepeatModeValue(RepeatMode.Playlist, REPEAT_CAP_ALL)
        )
    }
}
