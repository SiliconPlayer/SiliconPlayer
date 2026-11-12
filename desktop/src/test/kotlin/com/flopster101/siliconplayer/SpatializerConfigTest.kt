package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.audio.SpatializerBackend
import com.flopster101.siliconplayer.audio.SpatializerConfig
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
    fun defaultsToSteamAudio() {
        val config = SpatializerConfig.fromJson(null)
        assertEquals(SpatializerBackend.SteamAudio, config.backend)
        assertTrue(config.enabled)
        assertFalse(config.spatializeStereo)
        assertEquals(1f, config.wetMix, 0.0001f)
    }

    @Test
    fun clampsOutOfRangeValues() {
        val restored = SpatializerConfig.fromJson(
            """{"backend":"libspatialaudio","wetMix":4.0,"ambisonicOrder":9,"renderMethod":7}"""
        )
        assertEquals(1f, restored.wetMix, 0.0001f)
        assertEquals(3, restored.ambisonicOrder)
        assertEquals(1, restored.renderMethod)
    }

    @Test
    fun parsesUnknownBackendAsOff() {
        val restored = SpatializerConfig.fromJson("""{"backend":"unknown_engine"}""")
        assertEquals(SpatializerBackend.Off, restored.backend)
        assertTrue(restored.enabled)
    }
}
