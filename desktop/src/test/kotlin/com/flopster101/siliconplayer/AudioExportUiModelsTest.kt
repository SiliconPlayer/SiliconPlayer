package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.export.AudioExportCodec
import com.flopster101.siliconplayer.export.AudioExportContainer
import com.flopster101.siliconplayer.export.AudioExportEncoder
import com.flopster101.siliconplayer.export.AudioExportRenderOptions
import com.flopster101.siliconplayer.export.audioExportBitrates
import com.flopster101.siliconplayer.export.audioExportSubtuneLabel
import com.flopster101.siliconplayer.export.formatAudioExportBitrate
import com.flopster101.siliconplayer.export.formatAudioExportSizeEstimate
import com.flopster101.siliconplayer.export.impliedCodec
import com.flopster101.siliconplayer.export.impliedContainer
import com.flopster101.siliconplayer.export.mediaCodecContainer
import com.flopster101.siliconplayer.export.suggestedAudioExportFileName
import com.flopster101.siliconplayer.export.supportedAudioExportContainers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportUiModelsTest {

    @Test
    fun subtuneLabelIsOnlyShownForMultipleTunes() {
        assertNull(audioExportSubtuneLabel(0, 0))
        assertNull(audioExportSubtuneLabel(0, 1))
        assertEquals("Subtune 2 of 4", audioExportSubtuneLabel(1, 4))
    }

    @Test
    fun codecDefaultsRemainCompatible() {
        AudioExportCodec.entries.forEach { codec ->
            val container = codec.impliedContainer()
            assertEquals(codec, container.impliedCodec())
            assertTrue(container in supportedAudioExportContainers(codec, AudioExportEncoder.SystemFfmpeg))
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
                bitrateBps = 128_000
            )
        )
        assertEquals(
            "≈ 281.3 KB · stereo",
            formatAudioExportSizeEstimate(
                durationSeconds = 1.0,
                container = AudioExportContainer.Wav,
                codec = AudioExportCodec.Pcm24,
                sampleRateHz = 48_000,
                bitrateBps = null
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

    @Test
    fun mediaCodecBitratesCoverAacOpusAndAmrNb() {
        assertEquals(510_000, audioExportBitrates(AudioExportCodec.Aac).last())
        assertEquals(510_000, audioExportBitrates(AudioExportCodec.Opus).last())
        assertEquals(
            listOf(4_750, 5_150, 5_900, 6_700, 7_400, 7_950, 10_200, 12_200),
            audioExportBitrates(AudioExportCodec.AmrNb)
        )
        assertEquals("510 kbps", formatAudioExportBitrate(510_000))
        assertEquals("5.9 kbps", formatAudioExportBitrate(5_900))
    }

    @Test
    fun mediaCodecContainersMatchCodecRequirements() {
        assertEquals(AudioExportContainer.M4a, AudioExportCodec.Aac.mediaCodecContainer())
        assertEquals(AudioExportContainer.ThreeGpp, AudioExportCodec.AmrNb.mediaCodecContainer())
        assertEquals(AudioExportContainer.M4a, AudioExportCodec.Opus.mediaCodecContainer())
        assertEquals(AudioExportContainer.M4a, AudioExportCodec.Flac.mediaCodecContainer())
    }

    @Test
    fun codecControlsTheAvailableContainers() {
        assertEquals(
            listOf(AudioExportContainer.M4a, AudioExportContainer.Adts, AudioExportContainer.ThreeGpp),
            supportedAudioExportContainers(AudioExportCodec.Aac, AudioExportEncoder.SystemFfmpeg)
        )
        assertEquals(
            listOf(AudioExportContainer.M4a, AudioExportContainer.ThreeGpp),
            supportedAudioExportContainers(AudioExportCodec.Aac, AudioExportEncoder.MediaCodec)
        )
        assertEquals(
            listOf(AudioExportContainer.Ogg, AudioExportContainer.WebM, AudioExportContainer.M4a),
            supportedAudioExportContainers(AudioExportCodec.Opus, AudioExportEncoder.SystemFfmpeg)
        )
        assertEquals(
            listOf(AudioExportContainer.M4a, AudioExportContainer.WebM),
            supportedAudioExportContainers(AudioExportCodec.Opus, AudioExportEncoder.MediaCodec)
        )
        assertEquals(
            listOf(AudioExportContainer.Wav),
            supportedAudioExportContainers(AudioExportCodec.Pcm24, AudioExportEncoder.SystemFfmpeg)
        )
    }
}
