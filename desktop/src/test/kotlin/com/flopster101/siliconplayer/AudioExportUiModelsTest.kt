package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.AudioExportCodec
import com.flopster101.siliconplayer.export.AudioExportContainer
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.audioExportSubtuneLabel
import com.flopster101.siliconplayer.export.formatAudioExportSizeEstimate
import com.flopster101.siliconplayer.export.impliedCodec
import com.flopster101.siliconplayer.export.impliedContainer
import com.flopster101.siliconplayer.export.suggestedAudioExportFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioExportUiModelsTest {

    @Test
    fun subtuneLabelIsOnlyShownForMultipleTunes() {
        assertNull(audioExportSubtuneLabel(0, 0))
        assertNull(audioExportSubtuneLabel(0, 1))
        assertEquals("Subtune 2 of 4", audioExportSubtuneLabel(1, 4))
    }

    @Test
    fun containerAndCodecSelectionsStayPaired() {
        AudioExportContainer.entries.forEach { container ->
            assertEquals(container, container.impliedCodec().impliedContainer())
        }
        AudioExportCodec.entries.forEach { codec ->
            assertEquals(codec, codec.impliedContainer().impliedCodec())
        }
    }

    @Test
    fun suggestedFileNameRemovesPathSeparatorsAndUsesContainerExtension() {
        assertEquals(
            "Artist_Title_.flac",
            suggestedAudioExportFileName("Artist/Title" + '\\', AudioExportContainer.Flac)
        )
        assertEquals(
            "audio-export.m4a",
            suggestedAudioExportFileName(" ", AudioExportContainer.M4a)
        )
    }

    @Test
    fun sizeEstimateUsesBitrateOrPcmSize() {
        assertEquals(
            "≈ 1.5 MB · stereo",
            formatAudioExportSizeEstimate(
                durationSeconds = 100.0,
                container = AudioExportContainer.M4a,
                codec = AudioExportCodec.Aac,
                sampleRateHz = 48_000,
                bitrateKbps = 128
            )
        )
        assertEquals(
            "≈ 281.3 KB · stereo",
            formatAudioExportSizeEstimate(
                durationSeconds = 1.0,
                container = AudioExportContainer.Wav,
                codec = AudioExportCodec.Pcm24,
                sampleRateHz = 48_000,
                bitrateKbps = null
            )
        )
        assertEquals(
            "Lossless · size varies",
            formatAudioExportSizeEstimate(10.0, AudioExportContainer.Flac, AudioExportCodec.Flac, 48_000, null)
        )
    }

    @Test
    fun renderOptionsUseActiveGainAndEffectsByDefault() {
        val defaults = AudioExportRenderOptions()
        assertEquals(true, defaults.useActiveGain)
        assertEquals(true, defaults.useActiveEffects)
    }
}
