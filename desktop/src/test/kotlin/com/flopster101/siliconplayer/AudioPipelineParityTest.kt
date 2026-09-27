package com.flopster101.siliconplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioPipelineParityTest {

    private fun prefsOf(values: Map<String, String?>) = object {
        val contains: (String) -> Boolean = { values.containsKey(it) }
        val getString: (String, String?) -> String? = { key, def -> values[key] ?: def }
    }

    @Test
    fun perBackendValueWins() {
        val prefs = prefsOf(
            mapOf(
                AppPreferenceKeys.audioPerformanceModeForBackend(AudioBackendPreference.ALSA) to "power_saving",
                AppPreferenceKeys.audioBufferPresetForBackend(AudioBackendPreference.ALSA) to "very_small",
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE to "low_latency",
                AppPreferenceKeys.AUDIO_BUFFER_PRESET to "large"
            )
        )
        assertEquals(
            AudioPerformanceMode.PowerSaving,
            restoreAudioPerformanceModeForBackend(prefs.contains, prefs.getString, AudioBackendPreference.ALSA)
        )
        assertEquals(
            AudioBufferPreset.VerySmall,
            restoreAudioBufferPresetForBackend(prefs.contains, prefs.getString, AudioBackendPreference.ALSA)
        )
    }

    @Test
    fun autoFallsBackToLegacyGlobal() {
        val prefs = prefsOf(
            mapOf(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE to "low_latency",
                AppPreferenceKeys.AUDIO_BUFFER_PRESET to "very_large"
            )
        )
        assertEquals(
            AudioPerformanceMode.LowLatency,
            restoreAudioPerformanceModeForBackend(prefs.contains, prefs.getString, AudioBackendPreference.Auto)
        )
        assertEquals(
            AudioBufferPreset.VeryLarge,
            restoreAudioBufferPresetForBackend(prefs.contains, prefs.getString, AudioBackendPreference.Auto)
        )
    }

    @Test
    fun otherBackendsFallBackToDefaults() {
        val prefs = prefsOf(
            mapOf(
                AppPreferenceKeys.AUDIO_PERFORMANCE_MODE to "low_latency",
                AppPreferenceKeys.AUDIO_BUFFER_PRESET to "very_large"
            )
        )
        assertEquals(
            AudioBackendPreference.JACK.defaultPerformanceMode(),
            restoreAudioPerformanceModeForBackend(prefs.contains, prefs.getString, AudioBackendPreference.JACK)
        )
        assertEquals(
            AudioBackendPreference.JACK.defaultBufferPreset(),
            restoreAudioBufferPresetForBackend(prefs.contains, prefs.getString, AudioBackendPreference.JACK)
        )
    }

    @Test
    fun emptyPrefsResolveToBackendDefaults() {
        val prefs = prefsOf(emptyMap())
        assertEquals(
            AudioPerformanceMode.None,
            restoreAudioPerformanceModeForBackend(prefs.contains, prefs.getString, AudioBackendPreference.Auto)
        )
        assertEquals(
            AudioBackendPreference.Auto.defaultBufferPreset(),
            restoreAudioBufferPresetForBackend(prefs.contains, prefs.getString, AudioBackendPreference.Auto)
        )
    }
}
