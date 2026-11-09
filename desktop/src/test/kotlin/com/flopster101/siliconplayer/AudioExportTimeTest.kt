package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.parseAudioExportTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioExportTimeTest {

    @Test
    fun parsesSecondsAndClockValues() {
        assertEquals(62.5, parseAudioExportTime("62.5")!!, 0.0001)
        assertEquals(62.5, parseAudioExportTime("1:02.5")!!, 0.0001)
        assertEquals(3723.0, parseAudioExportTime("1:02:03")!!, 0.0001)
    }

    @Test
    fun rejectsMalformedOrNegativeValues() {
        listOf("", "abc", "-1", "1:60", "1:2:3:4", "1::2", "1:02:61").forEach {
            assertNull(it, parseAudioExportTime(it))
        }
    }
}
