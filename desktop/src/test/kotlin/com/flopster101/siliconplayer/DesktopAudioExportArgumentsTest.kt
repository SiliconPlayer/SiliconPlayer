package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.buildDesktopAudioExportArguments
import com.flopster101.siliconplayer.export.AudioExportCodec
import com.flopster101.siliconplayer.export.AudioExportContainer
import com.flopster101.siliconplayer.export.AudioExportDialogSource
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportFfmpegMode
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.AudioExportRepeatChoice
import com.flopster101.siliconplayer.export.AudioExportUiRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopAudioExportArgumentsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun mapsUiRequestToIsolatedCliArguments() {
        val input = tempFolder.newFile("song.sid")
        val request = request(input.absolutePath)

        val arguments = buildDesktopAudioExportArguments(request)

        assertEquals("3", arguments.valueAfter("--subtune"))
        assertEquals("2", arguments.valueAfter("--repeatCount"))
        assertEquals("false", arguments.valueAfter("--useGain"))
        assertEquals("false", arguments.valueAfter("--useEffects"))
        assertEquals("ffmpeg-custom", arguments.valueAfter("--ffmpeg"))
        assertEquals("-c:a flac", arguments.valueAfter("--ffmpegArgs"))
        assertFalse(arguments.contains("--force"))
    }

    @Test
    fun rejectsAndroidEncoderOnDesktop() {
        val input = tempFolder.newFile("song.sid")
        assertThrows(IllegalArgumentException::class.java) {
            buildDesktopAudioExportArguments(
                request(input.absolutePath).copy(encoder = AudioExportEncoder.MediaCodec)
            )
        }
    }

    private fun request(inputPath: String) = AudioExportUiRequest(
        source = AudioExportDialogSource(
            sourceId = inputPath,
            filePath = inputPath,
            title = "Song",
            artist = "Artist",
            decoderName = "Game Music Emu",
            subtuneIndex = 2,
            subtuneCount = 4,
            durationSeconds = 10.0
        ),
        encoder = AudioExportEncoder.ExternalFfmpeg,
        container = AudioExportContainer.Flac,
        codec = AudioExportCodec.Flac,
        bitrateKbps = null,
        ffmpegMode = AudioExportFfmpegMode.Advanced,
        ffmpegArguments = "-c:a flac",
        externalBinaryLocation = "ffmpeg-custom",
        destinationLocation = tempFolder.root.resolve("out.flac").absolutePath,
        includeMetadata = true,
        repeatChoice = AudioExportRepeatChoice.TrackTwice,
        renderOptions = AudioExportRenderOptions(useActiveGain = false, useActiveEffects = false)
    )

    private fun List<String>.valueAfter(name: String): String = this[indexOf(name) + 1]
}
