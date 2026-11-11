package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.platform.AppPreferences

internal sealed interface ScopeKey {
    data object Global : ScopeKey
    data class Core(val name: String) : ScopeKey
    data class Playlist(val id: String) : ScopeKey
    data class Song(val path: String) : ScopeKey
}

internal fun resolveEffectiveEffectChain(
    global: EffectChain,
    core: EffectChain? = null,
    playlist: EffectChain? = null,
    song: EffectChain? = null
): EffectChain = song ?: playlist ?: core ?: global

internal fun synthesizeEffectChainFromLegacy(
    settings: DspSettings,
    forceMono: Boolean = false
): EffectChain {
    var chain = EffectChain(master = NodeOptions(mono = forceMono))
    if (settings.bassEnabled) {
        chain = chain.withLegacyNodeState(
            EffectNodeIds.OPENMPT_BASS,
            enabled = true,
            params = mapOf(
                "depth" to settings.bassDepth.coerceIn(0, 4).toFloat(),
                "range" to settings.bassRange.coerceIn(0, 4).toFloat()
            )
        )
    }
    if (settings.surroundEnabled) {
        chain = chain.withLegacyNodeState(
            EffectNodeIds.OPENMPT_SURROUND,
            enabled = true,
            params = mapOf(
                "depth" to settings.surroundDepth.coerceIn(1, 16).toFloat(),
                "delayMs" to normalizeSurroundDelayMsPref(settings.surroundDelayMs).toFloat()
            )
        )
    }
    if (settings.reverbEnabled) {
        chain = chain.withLegacyNodeState(
            EffectNodeIds.OPENMPT_REVERB,
            enabled = true,
            params = mapOf(
                "depth" to settings.reverbDepth.coerceIn(1, 16).toFloat(),
                "preset" to settings.reverbPreset.coerceIn(0, 28).toFloat()
            )
        )
    }
    if (settings.bitCrushEnabled) {
        chain = chain.withLegacyNodeState(
            EffectNodeIds.OPENMPT_BITCRUSH,
            enabled = true,
            params = mapOf(
                "bits" to settings.bitCrushBits.coerceIn(1, 24).toFloat()
            )
        )
    }
    return chain
}

internal fun readGlobalEffectChain(prefs: AppPreferences): EffectChain {
    if (prefs.contains(AppPreferenceKeys.AUDIO_EFFECT_CHAIN)) {
        return EffectChain.fromJson(prefs.getString(AppPreferenceKeys.AUDIO_EFFECT_CHAIN, null))
    }
    val legacyForceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
    val legacyDsp = readGlobalDspSettings(prefs)
    return synthesizeEffectChainFromLegacy(legacyDsp, legacyForceMono)
}

internal fun readCoreEffectChain(prefs: AppPreferences, coreName: String?): EffectChain? {
    val name = coreName?.takeIf { it.isNotBlank() } ?: return null
    val key = AppPreferenceKeys.audioEffectChainCoreKey(name)
    if (prefs.contains(key)) {
        return EffectChain.fromJson(prefs.getString(key, null))
    }
    if (hasCoreDspOverrides(prefs, name) || readCoreIgnoreGlobalDsp(prefs, name)) {
        val legacyForceMono = readGlobalEffectChain(prefs).master.mono
        val legacyCoreDsp = readCoreDspSettings(prefs, name)
        return synthesizeEffectChainFromLegacy(legacyCoreDsp, legacyForceMono)
    }
    return null
}

internal fun writeGlobalEffectChain(editor: AppPreferences.Editor, chain: EffectChain) {
    editor.putString(AppPreferenceKeys.AUDIO_EFFECT_CHAIN, chain.toJson())
}

internal fun writeCoreEffectChain(editor: AppPreferences.Editor, coreName: String, chain: EffectChain?) {
    val key = AppPreferenceKeys.audioEffectChainCoreKey(coreName)
    if (chain != null) {
        editor.putString(key, chain.toJson())
    } else {
        editor.remove(key)
        editor.remove(AppPreferenceKeys.audioDspCoreBassEnabledKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreBassDepthKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreBassRangeKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreSurroundEnabledKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreSurroundDepthKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreSurroundDelayMsKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreReverbEnabledKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreReverbDepthKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreReverbPresetKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreBitCrushEnabledKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreBitCrushBitsKey(coreName))
        editor.remove(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(coreName))
    }
}

internal fun resolveEffectiveEffectChainForPrefs(
    prefs: AppPreferences,
    coreName: String?
): EffectChain = resolveEffectiveEffectChain(
    global = readGlobalEffectChain(prefs),
    core = readCoreEffectChain(prefs, coreName)
)

internal fun applyEffectiveEffectChainFromPrefsToNative(
    prefs: AppPreferences,
    coreName: String?
) {
    applyEffectChainToNative(resolveEffectiveEffectChainForPrefs(prefs, coreName))
}
