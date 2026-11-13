package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.audio.SpatializerBackend
import com.flopster101.siliconplayer.audio.SpatializerConfig
import com.flopster101.siliconplayer.audio.SpatializerPreset
import com.flopster101.siliconplayer.audio.shouldShowSpatializerNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpatializerConfigTest {

    @Test
    fun roundTripsEveryField() {
        val config = SpatializerConfig(
            backend = SpatializerBackend.LibSpatialAudio,
            enabled = true,
            spatializeStereo = true,
            centerGainDb = -3.5f,
            surroundGainDb = 2f,
            heightGainDb = -6f,
            lfeGainDb = -9f,
            lfeLowPassEnabled = false,
            wetMix = 0.25f,
            bassGainDb = 1.5f,
            trebleGainDb = -2f,
            surroundTrebleGainDb = -3f,
            wideSurround = true,
            frontAmbience = true,
            virtualizerWidth = 1.5f,
            frontDiffuse = true,
            compressorMode = 1,
            preset = SpatializerPreset.Atmosphere,
            outputGainDb = 3.5f,
            ambisonicOrder = 2,
            renderMethod = 1,
            yawDegrees = 45f,
            customSofaPath = "/tmp/hrtf/custom.sofa",
        )
        val restored = SpatializerConfig.fromJson(config.toJson())
        assertEquals(config, restored)
    }

    @Test
    fun showsImplicitNodeOnlyForEnabledFfmpegMultichannel() {
        val config = SpatializerConfig(backend = SpatializerBackend.SteamAudio)
        assertTrue(shouldShowSpatializerNode(DecoderNames.FFMPEG, 6, config))
        assertFalse(shouldShowSpatializerNode(DecoderNames.FFMPEG, 2, config))
        assertFalse(shouldShowSpatializerNode(DecoderNames.LIB_OPEN_MPT, 6, config))
        assertFalse(shouldShowSpatializerNode(DecoderNames.FFMPEG, 6, config.copy(enabled = false)))
        assertFalse(
            shouldShowSpatializerNode(
                DecoderNames.FFMPEG,
                6,
                config.copy(backend = SpatializerBackend.Off)
            )
        )
    }

    @Test
    fun defaultsToSteamAudioAtmosphere() {
        val config = SpatializerConfig.fromJson(null)
        assertEquals(SpatializerBackend.SteamAudio, config.backend)
        assertTrue(config.enabled)
        assertFalse(config.spatializeStereo)
        assertEquals(SpatializerPreset.Atmosphere, config.preset)
        assertTrue(config.frontDiffuse)
        assertTrue(config.frontAmbience)
        assertTrue(config.wideSurround)
        assertEquals(1.5f, config.virtualizerWidth, 0.0001f)
        assertEquals(2, config.compressorMode)
        assertEquals(1f, config.wetMix, 0.0001f)
    }

    @Test
    fun defaultsToTheConfiguredSpatializerBoost() {
        assertEquals(8.5f, SpatializerConfig.fromJson(null).outputGainDb, 0.0001f)
    }

    @Test
    fun clampsOutOfRangeValues() {
        val restored = SpatializerConfig.fromJson(
            """{"backend":"libspatialaudio","wetMix":4.0,"ambisonicOrder":9,"renderMethod":7,"compressorMode":9}"""
        )
        assertEquals(1f, restored.wetMix, 0.0001f)
        assertEquals(3, restored.ambisonicOrder)
        assertEquals(2, restored.renderMethod)
        assertEquals(2, restored.compressorMode)
        assertEquals(
            18f,
            SpatializerConfig.fromJson("""{"outputGainDb":99.0}""").outputGainDb,
            0.0001f
        )
    }

    @Test
    fun appliesSteamAudioPresetsWithoutChangingOutputMakeup() {
        val config = SpatializerConfig(outputGainDb = 6.5f)

        val music = config.withPreset(SpatializerPreset.Music)
        assertEquals(1f, music.bassGainDb, 0.0001f)
        assertEquals(1f, music.trebleGainDb, 0.0001f)
        assertFalse(music.wideSurround)
        assertFalse(music.frontAmbience)
        assertTrue(music.frontDiffuse)
        assertEquals(1.2f, music.virtualizerWidth, 0.0001f)
        assertEquals(1, music.compressorMode)
        assertEquals(6.5f, music.outputGainDb, 0.0001f)

        val wide = config.withPreset(SpatializerPreset.Wide)
        assertEquals(1.5f, wide.surroundGainDb, 0.0001f)
        assertEquals(-3f, wide.surroundTrebleGainDb, 0.0001f)
        assertEquals(-1.5f, wide.centerGainDb, 0.0001f)
        assertTrue(wide.wideSurround)
        assertTrue(wide.frontAmbience)
        assertFalse(wide.frontDiffuse)
        assertEquals(1.35f, wide.virtualizerWidth, 0.0001f)
        assertEquals(0f, wide.bassGainDb, 0.0001f)

        val atmosphere = config.withPreset(SpatializerPreset.Atmosphere)
        assertEquals(1.5f, atmosphere.virtualizerWidth, 0.0001f)
        assertTrue(atmosphere.frontDiffuse)
        assertTrue(atmosphere.frontAmbience)
        assertTrue(atmosphere.wideSurround)
        assertEquals(1.5f, atmosphere.surroundGainDb, 0.0001f)
        assertEquals(-1.5f, atmosphere.centerGainDb, 0.0001f)
        assertEquals(-3f, atmosphere.surroundTrebleGainDb, 0.0001f)
        assertEquals(2, atmosphere.compressorMode)
        assertEquals(0f, atmosphere.bassGainDb, 0.0001f)
        assertEquals(0.5f, atmosphere.trebleGainDb, 0.0001f)
        assertEquals(6.5f, atmosphere.outputGainDb, 0.0001f)

        val cinema = config.withPreset(SpatializerPreset.Cinema)
        assertEquals(1.75f, cinema.virtualizerWidth, 0.0001f)
        assertTrue(cinema.frontDiffuse)
        assertTrue(cinema.frontAmbience)
        assertTrue(cinema.wideSurround)
        assertEquals(2.5f, cinema.surroundGainDb, 0.0001f)
        assertEquals(1f, cinema.heightGainDb, 0.0001f)
        assertEquals(1.5f, cinema.bassGainDb, 0.0001f)
        assertEquals(1f, cinema.trebleGainDb, 0.0001f)
        assertEquals(2, cinema.compressorMode)
        assertEquals(6.5f, cinema.outputGainDb, 0.0001f)

        val neutral = cinema.withPreset(SpatializerPreset.Neutral)
        assertEquals(0f, neutral.bassGainDb, 0.0001f)
        assertEquals(0f, neutral.trebleGainDb, 0.0001f)
        assertEquals(0f, neutral.surroundGainDb, 0.0001f)
        assertEquals(0f, neutral.heightGainDb, 0.0001f)
        assertFalse(neutral.wideSurround)
        assertFalse(neutral.frontAmbience)
        assertEquals(1f, neutral.virtualizerWidth, 0.0001f)
        assertFalse(neutral.frontDiffuse)
        assertEquals(0, neutral.compressorMode)
        assertEquals(6.5f, neutral.outputGainDb, 0.0001f)

        assertEquals(SpatializerPreset.Music, SpatializerPreset.fromId("soft_v"))
        assertEquals(SpatializerPreset.Atmosphere, SpatializerPreset.fromId("soft_v_wide_spatial"))
    }

    @Test
    fun parsesUnknownBackendAsOff() {
        val restored = SpatializerConfig.fromJson("""{"backend":"unknown_engine"}""")
        assertEquals(SpatializerBackend.Off, restored.backend)
        assertTrue(restored.enabled)
    }
}
