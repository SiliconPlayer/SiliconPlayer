package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SessionResumeStoreTest {

    private fun tempDir(): File =
        Files.createTempDirectory("session-resume-test").toFile()

    private fun snapshot() = SessionResumeSnapshot(
        sourceId = "/music/track.mod",
        positionSeconds = 42.5,
        durationSeconds = 180.0,
        playlistId = "playlist-1",
        entryId = "entry-7",
        shuffleActive = false
    )

    @Test
    fun roundTripPreservesAllFields() {
        val dir = tempDir()
        writeSessionResumeSnapshot(dir, snapshot())
        val restored = readSessionResumeSnapshot(dir)
        assertEquals(snapshot(), restored)
        assertTrue(restored!!.hasValidPosition())
    }

    @Test
    fun nullSnapshotDeletesFileAndBackup() {
        val dir = tempDir()
        writeSessionResumeSnapshot(dir, snapshot())
        writeSessionResumeSnapshot(dir, null)
        assertFalse(sessionResumeFile(dir).exists())
        assertNull(readSessionResumeSnapshot(dir))
    }

    @Test
    fun corruptMainFallsBackToBackup() {
        val dir = tempDir()
        writeSessionResumeSnapshot(dir, snapshot())
        // Second write moves the first version to .bak.
        writeSessionResumeSnapshot(dir, snapshot().copy(positionSeconds = 43.0))
        sessionResumeFile(dir).writeText("not json{{{")
        assertEquals(snapshot(), readSessionResumeSnapshot(dir))
    }

    @Test
    fun blankSourceIdReadsAsNull() {
        val dir = tempDir()
        sessionResumeFile(dir).writeText("""{"sourceId":"  ","positionSeconds":1.0,"durationSeconds":2.0}""")
        assertNull(readSessionResumeSnapshot(dir))
    }

    @Test
    fun outOfRangePositionIsInvalid() {
        assertFalse(snapshot().copy(positionSeconds = 500.0).hasValidPosition())
        assertFalse(snapshot().copy(durationSeconds = 0.0).hasValidPosition())
        assertFalse(snapshot().copy(positionSeconds = -1.0).hasValidPosition())
        assertTrue(snapshot().copy(positionSeconds = 180.04).hasValidPosition())
    }
}
