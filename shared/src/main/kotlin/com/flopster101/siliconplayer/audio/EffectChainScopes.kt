package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.updateStoredPlaylistEffectChain
import org.json.JSONArray
import org.json.JSONObject

internal sealed interface ScopeKey {
    data object Global : ScopeKey
    data class Core(val name: String) : ScopeKey
    data class Playlist(val id: String) : ScopeKey
    data class Song(val path: String) : ScopeKey
}

internal data class EffectPreset(
    val id: String,
    val name: String,
    val chain: EffectChain
)

internal fun parseEffectPresetsJson(json: String?): List<EffectPreset> {
    if (json.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val out = ArrayList<EffectPreset>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val id = obj.optString("id", "").trim().takeIf { it.isNotEmpty() } ?: continue
        val name = obj.optString("name", "").trim().takeIf { it.isNotEmpty() } ?: continue
        val chainObj = obj.optJSONObject("chain")
        val chainJson = chainObj?.toString() ?: obj.optString("chain", "")
        out.add(
            EffectPreset(
                id = id,
                name = name,
                chain = EffectChain.fromJson(chainJson)
            )
        )
    }
    return out
}

internal fun serializeEffectPresetsJson(presets: List<EffectPreset>): String {
    val array = JSONArray()
    for (preset in presets) {
        val obj = JSONObject()
            .put("id", preset.id)
            .put("name", preset.name)
            .put("chain", JSONObject(preset.chain.toJson()))
        array.put(obj)
    }
    return array.toString()
}

internal fun readEffectPresets(prefs: AppPreferences): List<EffectPreset> =
    parseEffectPresetsJson(prefs.getString(AppPreferenceKeys.AUDIO_EFFECT_PRESETS, null))

internal fun writeEffectPresets(editor: AppPreferences.Editor, presets: List<EffectPreset>) {
    if (presets.isEmpty()) {
        editor.remove(AppPreferenceKeys.AUDIO_EFFECT_PRESETS)
    } else {
        editor.putString(AppPreferenceKeys.AUDIO_EFFECT_PRESETS, serializeEffectPresetsJson(presets))
    }
}

internal fun readBypassAllEffects(prefs: AppPreferences): Boolean =
    prefs.getBoolean(AppPreferenceKeys.AUDIO_EFFECTS_BYPASS_ALL, false)

internal fun writeBypassAllEffects(editor: AppPreferences.Editor, bypassAll: Boolean) {
    editor.putBoolean(AppPreferenceKeys.AUDIO_EFFECTS_BYPASS_ALL, bypassAll)
}

internal fun resolveEffectiveEffectChain(
    global: EffectChain,
    core: EffectChain? = null,
    playlist: EffectChain? = null,
    song: EffectChain? = null,
    bypassAll: Boolean = false
): EffectChain {
    if (bypassAll) return EffectChain.Empty
    return song ?: playlist ?: core ?: global
}

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

internal fun readPlaylistEffectChain(playlist: StoredPlaylist?): EffectChain? {
    val raw = playlist?.effectChain?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return EffectChain.fromJson(raw)
}

internal fun readPlaylistEffectChain(
    state: PlaylistLibraryState?,
    playlistId: String?
): EffectChain? {
    val id = playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val playlist = state?.playlists?.firstOrNull { it.id == id } ?: return null
    return readPlaylistEffectChain(playlist)
}

internal fun readSongEffectChain(
    songVolumeStore: SongVolumeStore?,
    songPath: String?
): EffectChain? {
    val path = songPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val raw = songVolumeStore?.getSongEffectChain(path)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return EffectChain.fromJson(raw)
}

internal fun readScopeEffectChain(
    key: ScopeKey,
    prefs: AppPreferences,
    songVolumeStore: SongVolumeStore? = null,
    playlistState: PlaylistLibraryState? = null,
    activePlaylist: StoredPlaylist? = null
): EffectChain? = when (key) {
    ScopeKey.Global -> readGlobalEffectChain(prefs)
    is ScopeKey.Core -> readCoreEffectChain(prefs, key.name)
    is ScopeKey.Playlist -> {
        val fromState = readPlaylistEffectChain(playlistState, key.id)
        if (fromState != null) {
            fromState
        } else if (activePlaylist?.id == key.id) {
            readPlaylistEffectChain(activePlaylist)
        } else {
            null
        }
    }
    is ScopeKey.Song -> readSongEffectChain(songVolumeStore, key.path)
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

internal fun writePlaylistEffectChain(
    state: PlaylistLibraryState,
    playlistId: String,
    chain: EffectChain?
): PlaylistLibraryState = updateStoredPlaylistEffectChain(
    state = state,
    playlistId = playlistId,
    effectChain = chain?.toJson()
)

internal fun writeSongEffectChain(
    songVolumeStore: SongVolumeStore,
    songPath: String,
    chain: EffectChain?
) {
    val path = songPath.trim()
    if (path.isEmpty()) return
    songVolumeStore.setSongEffectChain(path, chain?.toJson())
}

@Volatile
internal var activeDialogPreviewChainProvider: (() -> EffectChain)? = null

internal fun resolveEffectiveEffectChainForPrefs(
    prefs: AppPreferences,
    coreName: String? = null,
    playlist: StoredPlaylist? = null,
    playlistState: PlaylistLibraryState? = null,
    playlistId: String? = playlist?.id,
    songVolumeStore: SongVolumeStore? = null,
    songPath: String? = null
): EffectChain {
    activeDialogPreviewChainProvider?.invoke()?.let { return it }
    if (readBypassAllEffects(prefs)) return EffectChain.Empty
    val resolvedPlaylistChain = readPlaylistEffectChain(playlistState, playlistId)
        ?: readPlaylistEffectChain(playlist)
    val resolvedSongChain = readSongEffectChain(songVolumeStore, songPath)
    return resolveEffectiveEffectChain(
        global = readGlobalEffectChain(prefs),
        core = readCoreEffectChain(prefs, coreName),
        playlist = resolvedPlaylistChain,
        song = resolvedSongChain,
        bypassAll = false
    )
}

internal fun applyEffectiveEffectChainFromPrefsToNative(
    prefs: AppPreferences,
    coreName: String? = null,
    playlist: StoredPlaylist? = null,
    playlistState: PlaylistLibraryState? = null,
    playlistId: String? = playlist?.id,
    songVolumeStore: SongVolumeStore? = null,
    songPath: String? = null
) {
    applyEffectChainToNative(
        resolveEffectiveEffectChainForPrefs(
            prefs = prefs,
            coreName = coreName,
            playlist = playlist,
            playlistState = playlistState,
            playlistId = playlistId,
            songVolumeStore = songVolumeStore,
            songPath = songPath
        )
    )
}

internal enum class EffectChainScopeTarget(val storageKey: String, val label: String) {
    Global("global", "Global"),
    Core("core", "Current core"),
    Playlist("playlist", "Playlist"),
    Song("song", "Song");

    companion object {
        fun fromStorageKey(value: String?): EffectChainScopeTarget =
            entries.firstOrNull { it.storageKey == value } ?: Global
    }
}

internal fun isDialogScopeAvailable(
    target: EffectChainScopeTarget,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean
): Boolean = when (target) {
    EffectChainScopeTarget.Global -> true
    EffectChainScopeTarget.Core -> hasActiveCore
    EffectChainScopeTarget.Playlist -> hasActivePlaylist
    EffectChainScopeTarget.Song -> hasActiveSong
}

internal fun resolveInitialDialogScope(
    savedScope: EffectChainScopeTarget,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    coreChain: EffectChain?,
    playlistChain: EffectChain?,
    songChain: EffectChain?
): EffectChainScopeTarget {
    if (hasActiveSong && songChain != null) return EffectChainScopeTarget.Song
    if (hasActivePlaylist && playlistChain != null) return EffectChainScopeTarget.Playlist
    if (hasActiveCore && coreChain != null) return EffectChainScopeTarget.Core
    if (isDialogScopeAvailable(savedScope, hasActiveCore, hasActivePlaylist, hasActiveSong)) {
        return savedScope
    }
    return EffectChainScopeTarget.Global
}

internal fun resolveActiveScopeTarget(
    core: EffectChain? = null,
    playlist: EffectChain? = null,
    song: EffectChain? = null
): EffectChainScopeTarget = when {
    song != null -> EffectChainScopeTarget.Song
    playlist != null -> EffectChainScopeTarget.Playlist
    core != null -> EffectChainScopeTarget.Core
    else -> EffectChainScopeTarget.Global
}

internal fun resolveInheritedChainForScope(
    target: EffectChainScopeTarget,
    global: EffectChain,
    core: EffectChain? = null,
    playlist: EffectChain? = null
): Pair<EffectChainScopeTarget, EffectChain> = when (target) {
    EffectChainScopeTarget.Global -> EffectChainScopeTarget.Global to global
    EffectChainScopeTarget.Core -> EffectChainScopeTarget.Global to global
    EffectChainScopeTarget.Playlist -> {
        if (core != null) EffectChainScopeTarget.Core to core
        else EffectChainScopeTarget.Global to global
    }
    EffectChainScopeTarget.Song -> {
        when {
            playlist != null -> EffectChainScopeTarget.Playlist to playlist
            core != null -> EffectChainScopeTarget.Core to core
            else -> EffectChainScopeTarget.Global to global
        }
    }
}

internal fun higherPriorityOverrideScope(
    viewingScope: EffectChainScopeTarget,
    core: EffectChain? = null,
    playlist: EffectChain? = null,
    song: EffectChain? = null
): EffectChainScopeTarget? {
    val active = resolveActiveScopeTarget(core = core, playlist = playlist, song = song)
    return if (active.ordinal > viewingScope.ordinal) active else null
}
