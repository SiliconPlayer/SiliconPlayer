package com.flopster101.siliconplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoderPlatformSupportTest {
    @Test
    fun dolbyMediaCodecIsAndroidOnly() {
        assertTrue(isDecoderSupportedOnPlatform(DecoderNames.PLATFORM_DOLBY, DecoderPlatform.Android))
        assertFalse(isDecoderSupportedOnPlatform(DecoderNames.PLATFORM_DOLBY, DecoderPlatform.Desktop))
    }

    @Test
    fun restrictionIsCaseInsensitive() {
        assertFalse(isDecoderSupportedOnPlatform("dolby mediacodec", DecoderPlatform.Desktop))
    }

    @Test
    fun unrestrictedDecodersRunEverywhere() {
        assertTrue(isDecoderSupportedOnPlatform(DecoderNames.FFMPEG, DecoderPlatform.Android))
        assertTrue(isDecoderSupportedOnPlatform(DecoderNames.FFMPEG, DecoderPlatform.Desktop))
        assertTrue(isDecoderSupportedOnPlatform("anything-else", DecoderPlatform.Desktop))
    }
}
