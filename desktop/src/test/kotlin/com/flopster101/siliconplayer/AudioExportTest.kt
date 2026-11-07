package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.moveCompletedExport
import com.flopster101.siliconplayer.desktop.parseAudioExportOptions
import com.flopster101.siliconplayer.export.AudioExportProgress
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.AudioExportSampleFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files

class AudioExportTest {

    @Test
    fun parsesDefaultsAndForceFlag() {
        val options = parseAudioExportOptions(
            arrayOf("--input", "~/song.sid", "--output", "out.wav", "--force")
        )

        assertEquals(File(System.getProperty("user.home"), "song.sid"), options.input)
        assertEquals(File("out.wav"), options.output)
        assertEquals(48_000, options.renderOptions.sampleRateHz)
        assertEquals(AudioExportSampleFormat.Pcm24, options.renderOptions.sampleFormat)
        assertTrue(options.force)
    }

    @Test
    fun parsesRenderOverrides() {
        val options = parseAudioExportOptions(
            arrayOf(
                "--input", "song.sid", "--output", "out.wav", "--subtune", "2",
                "--sampleRate", "96000", "--sampleFormat", "float32", "--duration", "12.5",
                "--unknownDuration", "240", "--fadeMs", "0", "--fadeCurve", "ease_out",
                "--fadeAll", "true"
            )
        )

        assertEquals(1, options.subtuneIndex)
        assertEquals(96_000, options.renderOptions.sampleRateHz)
        assertEquals(AudioExportSampleFormat.Float32, options.renderOptions.sampleFormat)
        assertEquals(12.5, options.renderOptions.durationCapSeconds)
        assertEquals(240, options.renderOptions.unknownDurationSeconds)
        assertEquals(0, options.renderOptions.endFadeDurationMs)
        assertEquals(EndFadeCurve.EaseOut, options.renderOptions.endFadeCurve)
        assertEquals(true, options.renderOptions.endFadeApplyToAllTracks)
        assertFalse(options.force)
    }

    @Test
    fun rejectsInvalidOptions() {
        assertThrows(IllegalArgumentException::class.java) {
            parseAudioExportOptions(arrayOf("--input", "in.sid", "--output", "out.wav", "--subtune", "0"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseAudioExportOptions(arrayOf("--input", "in.sid", "--output", "out.wav", "--sampleRate", "fast"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseAudioExportOptions(arrayOf("--input", "in.sid", "--output", "out.wav", "--sampleFormat", "mp3"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseAudioExportOptions(arrayOf("--input", "in.sid", "--output", "out.wav", "--repeatMode", "track"))
        }
    }

    @Test
    fun validatesRenderRanges() {
        assertThrows(IllegalArgumentException::class.java) { AudioExportRenderOptions(sampleRateHz = 1) }
        assertThrows(IllegalArgumentException::class.java) { AudioExportRenderOptions(durationCapSeconds = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { AudioExportRenderOptions(unknownDurationSeconds = 86_401) }
        assertThrows(IllegalArgumentException::class.java) { AudioExportRenderOptions(endFadeDurationMs = -1) }
        assertThrows(IllegalArgumentException::class.java) { AudioExportRenderOptions(endFadeDurationMs = 99) }
    }

    @Test
    fun progressIsBounded() {
        assertEquals(0.5, AudioExportProgress(50, 100).fraction, 0.0)
        assertEquals(1.0, AudioExportProgress(150, 100).fraction, 0.0)
        assertEquals(0.0, AudioExportProgress(0, 0).fraction, 0.0)
    }

    @Test
    fun completedMoveDoesNotOverwriteWithoutForce() {
        val directory = Files.createTempDirectory("audio-export-move-").toFile()
        try {
            val temp = File(directory, "temporary.part").apply { writeText("new") }
            val output = File(directory, "output.wav").apply { writeText("old") }

            assertThrows(FileAlreadyExistsException::class.java) {
                moveCompletedExport(temp, output, force = false)
            }
            assertEquals("old", output.readText())
            assertEquals("new", temp.readText())

            moveCompletedExport(temp, output, force = true)
            assertEquals("new", output.readText())
            assertFalse(temp.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
