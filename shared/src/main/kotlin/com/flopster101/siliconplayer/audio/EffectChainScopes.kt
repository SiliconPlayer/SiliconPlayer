package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.updateStoredPlaylistEffectChain

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

internal fun resolveEffectiveEffectChainForPrefs(
    prefs: AppPreferences,
    coreName: String? = null,
    playlist: StoredPlaylist? = null,
    playlistState: PlaylistLibraryState? = null,
    playlistId: String? = playlist?.id,
    songVolumeStore: SongVolumeStore? = null,
    songPath: String? = null
): EffectChain {
    val resolvedPlaylistChain = readPlaylistEffectChain(playlistState, playlistId)
        ?: readPlaylistEffectChain(playlist)
    val resolvedSongChain = readSongEffectChain(songVolumeStore, songPath)
    return resolveEffectiveEffectChain(
        global = readGlobalEffectChain(prefs),
        core = readCoreEffectChain(prefs, coreName),
        playlist = resolvedPlaylistChain,
        song = resolvedSongChain
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
