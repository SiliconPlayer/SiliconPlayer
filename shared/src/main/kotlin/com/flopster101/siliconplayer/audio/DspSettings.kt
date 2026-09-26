package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.platform.AppPreferences

internal enum class DspSettingsNamespace {
    Global,
    CurrentCore
}

internal data class DspSettings(
    val bassEnabled: Boolean,
    val bassDepth: Int,
    val bassRange: Int,
    val surroundEnabled: Boolean,
    val surroundDepth: Int,
    val surroundDelayMs: Int,
    val reverbEnabled: Boolean,
    val reverbDepth: Int,
    val reverbPreset: Int,
    val bitCrushEnabled: Boolean,
    val bitCrushBits: Int
)

internal fun normalizeBassDepthPref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (8 - value.coerceIn(4, 8)).coerceIn(0, 4)
    }
}

internal fun normalizeBassRangePref(value: Int): Int {
    return if (value in 0..4) {
        value
    } else {
        (4 - ((value.coerceIn(5, 21) - 1) / 5)).coerceIn(0, 4)
    }
}

internal fun normalizeSurroundDelayMsPref(value: Int): Int {
    if (value in 5..45 && value % 5 == 0) return value
    val clamped = value.coerceIn(5, 45)
    val step = ((clamped - 5) + 2) / 5
    return 5 + (step * 5)
}

internal fun defaultDspSettings(): DspSettings {
    return DspSettings(
        bassEnabled = AppDefaults.AudioProcessing.Dsp.bassEnabled,
        bassDepth = AppDefaults.AudioProcessing.Dsp.bassDepth,
        bassRange = AppDefaults.AudioProcessing.Dsp.bassRange,
        surroundEnabled = AppDefaults.AudioProcessing.Dsp.surroundEnabled,
        surroundDepth = AppDefaults.AudioProcessing.Dsp.surroundDepth,
        surroundDelayMs = AppDefaults.AudioProcessing.Dsp.surroundDelayMs,
        reverbEnabled = AppDefaults.AudioProcessing.Dsp.reverbEnabled,
        reverbDepth = AppDefaults.AudioProcessing.Dsp.reverbDepth,
        reverbPreset = AppDefaults.AudioProcessing.Dsp.reverbPreset,
        bitCrushEnabled = AppDefaults.AudioProcessing.Dsp.bitCrushEnabled,
        bitCrushBits = AppDefaults.AudioProcessing.Dsp.bitCrushBits
    )
}

internal fun readGlobalDspSettings(prefs: AppPreferences): DspSettings {
    val defaults = defaultDspSettings()
    return DspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, defaults.bitCrushBits).coerceIn(1, 24)
    )
}

internal fun writeGlobalDspSettings(editor: AppPreferences.Editor, settings: DspSettings) {
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, settings.bassEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, settings.bassDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, settings.bassRange)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, settings.surroundEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, settings.surroundDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, settings.surroundDelayMs)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, settings.reverbEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, settings.reverbDepth)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, settings.reverbPreset)
    editor.putBoolean(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, settings.bitCrushEnabled)
    editor.putInt(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, settings.bitCrushBits)
}

internal fun readCoreDspSettings(prefs: AppPreferences, coreName: String?): DspSettings {
    val defaults = defaultDspSettings()
    val name = coreName ?: return defaults
    return DspSettings(
        bassEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey(name), defaults.bassEnabled),
        bassDepth = normalizeBassDepthPref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassDepthKey(name), defaults.bassDepth)),
        bassRange = normalizeBassRangePref(prefs.getInt(AppPreferenceKeys.audioDspCoreBassRangeKey(name), defaults.bassRange)),
        surroundEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(name), defaults.surroundEnabled),
        surroundDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDepthKey(name), defaults.surroundDepth).coerceIn(1, 16),
        surroundDelayMs = normalizeSurroundDelayMsPref(
            prefs.getInt(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(name), defaults.surroundDelayMs)
        ),
        reverbEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreReverbEnabledKey(name), defaults.reverbEnabled),
        reverbDepth = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbDepthKey(name), defaults.reverbDepth).coerceIn(1, 16),
        reverbPreset = prefs.getInt(AppPreferenceKeys.audioDspCoreReverbPresetKey(name), defaults.reverbPreset).coerceIn(0, 28),
        bitCrushEnabled = prefs.getBoolean(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(name), defaults.bitCrushEnabled),
        bitCrushBits = prefs.getInt(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(name), defaults.bitCrushBits).coerceIn(1, 24)
    )
}

internal fun writeCoreDspSettings(editor: AppPreferences.Editor, coreName: String, settings: DspSettings) {
    editor.putBoolean(AppPreferenceKeys.audioDspCoreBassEnabledKey(coreName), settings.bassEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreBassDepthKey(coreName), settings.bassDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreBassRangeKey(coreName), settings.bassRange)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(coreName), settings.surroundEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreSurroundDepthKey(coreName), settings.surroundDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(coreName), settings.surroundDelayMs)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreReverbEnabledKey(coreName), settings.reverbEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreReverbDepthKey(coreName), settings.reverbDepth)
    editor.putInt(AppPreferenceKeys.audioDspCoreReverbPresetKey(coreName), settings.reverbPreset)
    editor.putBoolean(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(coreName), settings.bitCrushEnabled)
    editor.putInt(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(coreName), settings.bitCrushBits)
}

internal fun hasCoreDspOverrides(prefs: AppPreferences, coreName: String?): Boolean {
    val name = coreName ?: return false
    return prefs.contains(AppPreferenceKeys.audioDspCoreBassEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBassRangeKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbDepthKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreReverbPresetKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(name)) ||
            prefs.contains(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(name))
}

internal fun readCoreIgnoreGlobalDsp(prefs: AppPreferences, coreName: String?): Boolean {
    val name = coreName ?: return false
    return prefs.getBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(name), false)
}

internal fun resolveEffectiveDspSettings(
    coreName: String?,
    global: DspSettings,
    core: DspSettings,
    coreHasOverrides: Boolean,
    ignoreGlobalForCore: Boolean
): DspSettings {
    return if (coreName == null) {
        global
    } else if (ignoreGlobalForCore || coreHasOverrides) {
        core
    } else {
        global
    }
}

internal fun applyDspSettingsToNative(settings: DspSettings) {
    NativeBridge.setDspBassEnabled(settings.bassEnabled)
    NativeBridge.setDspBassDepth(settings.bassDepth)
    NativeBridge.setDspBassRange(settings.bassRange)
    NativeBridge.setDspSurroundEnabled(settings.surroundEnabled)
    NativeBridge.setDspSurroundDepth(settings.surroundDepth)
    NativeBridge.setDspSurroundDelayMs(settings.surroundDelayMs)
    NativeBridge.setDspReverbEnabled(settings.reverbEnabled)
    NativeBridge.setDspReverbDepth(settings.reverbDepth)
    NativeBridge.setDspReverbPreset(settings.reverbPreset)
    NativeBridge.setDspBitCrushEnabled(settings.bitCrushEnabled)
    NativeBridge.setDspBitCrushBits(settings.bitCrushBits)
}
