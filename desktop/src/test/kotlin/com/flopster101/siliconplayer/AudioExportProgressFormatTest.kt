package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.AudioExportProgress
import com.flopster101.siliconplayer.export.audioExportEstimatedRemainingSeconds
import com.flopster101.siliconplayer.export.formatAudioExportByteRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioExportProgressFormatTest {

    @Test
    fun estimatesRemainingWallTimeFromFrameProgress() {
        val progress = AudioExportProgress(
            renderedFrames = 25,
            totalFrames = 100,
            sampleRateHz = 10,
            elapsedSeconds = 10.0,
            writtenBytes = 2 * 1024 * 1024
        )
        assertEquals(30.0, audioExportEstimatedRemainingSeconds(progress)!!, 0.0001)
        assertEquals("204.8 KB/s", formatAudioExportByteRate(progress.writtenBytes, progress.elapsedSeconds))
        assertEquals("2.0 MB/s", formatAudioExportByteRate(2 * 1024 * 1024, 1.0))
    }

    @Test
    fun unknownProgressHasNoEstimateOrRate() {
        val progress = AudioExportProgress(0, 100, 10)
        assertNull(audioExportEstimatedRemainingSeconds(progress))
        assertEquals("—", formatAudioExportByteRate(0, 0.0))
    }
}
