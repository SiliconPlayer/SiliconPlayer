package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.AudioExportCompletedTrack
import com.flopster101.siliconplayer.export.AudioExportCompletion
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioExportCompletionTest {

    @Test
    fun reportsTrackCountAndAverageSpeed() {
        val completion = AudioExportCompletion(
            outputName = "Song.flac",
            outputLocation = "/music/Song.flac",
            formatLabel = "FLAC",
            durationSeconds = 60.0,
            sizeBytes = 4_000_000L,
            elapsedSeconds = 4.0,
            tracks = listOf(
                AudioExportCompletedTrack("Song", 30.0),
                AudioExportCompletedTrack("Song (2)", 30.0)
            )
        )

        assertEquals(2, completion.trackCount)
        assertEquals(15.0, completion.averageSpeed, 0.0001)
    }

    @Test
    fun zeroElapsedTimeHasNoAverageSpeed() {
        val completion = AudioExportCompletion(
            outputName = "Song.flac",
            outputLocation = "/music/Song.flac",
            formatLabel = "FLAC",
            durationSeconds = 10.0,
            sizeBytes = 1L,
            elapsedSeconds = 0.0,
            tracks = emptyList()
        )

        assertEquals(0.0, completion.averageSpeed, 0.0)
        assertEquals(0, completion.trackCount)
    }
}
