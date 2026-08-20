package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBackendPreferenceTest {

    @Test
    fun fromStorage_parsesAllKnownBackends() {
        assertEquals(AudioBackendPreference.Auto, AudioBackendPreference.fromStorage("auto"))
        assertEquals(AudioBackendPreference.OpenSLES, AudioBackendPreference.fromStorage("opensl"))
        assertEquals(AudioBackendPreference.WASAPI, AudioBackendPreference.fromStorage("wasapi"))
        assertEquals(AudioBackendPreference.DirectSound, AudioBackendPreference.fromStorage("dsound"))
        assertEquals(AudioBackendPreference.WinMM, AudioBackendPreference.fromStorage("winmm"))
        assertEquals(AudioBackendPreference.CoreAudio, AudioBackendPreference.fromStorage("coreaudio"))
        assertEquals(AudioBackendPreference.ALSA, AudioBackendPreference.fromStorage("alsa"))
        assertEquals(AudioBackendPreference.PulseAudio, AudioBackendPreference.fromStorage("pulseaudio"))
        assertEquals(AudioBackendPreference.JACK, AudioBackendPreference.fromStorage("jack"))
        assertEquals(AudioBackendPreference.Sndio, AudioBackendPreference.fromStorage("sndio"))
        assertEquals(AudioBackendPreference.Audio4, AudioBackendPreference.fromStorage("audio4"))
        assertEquals(AudioBackendPreference.OSS, AudioBackendPreference.fromStorage("oss"))
        assertEquals(AudioBackendPreference.NullAudio, AudioBackendPreference.fromStorage("null"))
    }

    @Test
    fun fromStorage_migratesLegacyAudioTrack() {
        val parsed = AudioBackendPreference.fromStorage("audiotrack")
        assertNotNull(parsed)
        assertTrue(parsed == AudioBackendPreference.AAudio || parsed == AudioBackendPreference.OpenSLES)
    }

    @Test
    fun fromStorage_handlesNullAndUnknown() {
        val fromNull = AudioBackendPreference.fromStorage(null)
        assertNotNull(fromNull)
        val fromUnknown = AudioBackendPreference.fromStorage("unknown_backend_xyz")
        assertNotNull(fromUnknown)
    }

    @Test
    fun nativeValues_areDistinctAndValid() {
        val nativeValues = AudioBackendPreference.entries.map { it.nativeValue }
        assertEquals(AudioBackendPreference.entries.size, nativeValues.toSet().size)
    }

    @Test
    fun defaultPerformanceMode_configuredAppropriately() {
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.Auto.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.AAudio.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.WASAPI.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.ALSA.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.PulseAudio.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.JACK.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.LowLatency, AudioBackendPreference.CoreAudio.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.None, AudioBackendPreference.OpenSLES.defaultPerformanceMode())
        assertEquals(AudioPerformanceMode.None, AudioBackendPreference.NullAudio.defaultPerformanceMode())
    }
}
