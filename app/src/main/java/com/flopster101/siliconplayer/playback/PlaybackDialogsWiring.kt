package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import java.io.File
import com.flopster101.siliconplayer.audio.EffectChain
import com.flopster101.siliconplayer.audio.EffectChainScopeTarget
import com.flopster101.siliconplayer.audio.applyEffectChainToNative
import com.flopster101.siliconplayer.audio.queryEffectDescriptors
import com.flopster101.siliconplayer.audio.readBypassAllEffects
import com.flopster101.siliconplayer.audio.readCoreEffectChain
import com.flopster101.siliconplayer.audio.readEffectPresets
import com.flopster101.siliconplayer.audio.readGlobalEffectChain
import com.flopster101.siliconplayer.audio.readPlaylistEffectChain
import com.flopster101.siliconplayer.audio.readSongEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChainForPrefs
import com.flopster101.siliconplayer.audio.resolveInitialDialogScope
import com.flopster101.siliconplayer.audio.writeBypassAllEffects
import com.flopster101.siliconplayer.audio.writeCoreEffectChain
import com.flopster101.siliconplayer.audio.writeEffectPresets
import com.flopster101.siliconplayer.audio.writeGlobalEffectChain
import com.flopster101.siliconplayer.audio.writePlaylistEffectChain
import com.flopster101.siliconplayer.audio.writeSongEffectChain
import com.flopster101.siliconplayer.platform.AndroidAppPreferences
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.BrowserPlaylistPrompt
import com.flopster101.siliconplayer.ui.dialogs.ManualHttpAuthenticationDialog
import com.flopster101.siliconplayer.ui.dialogs.ManualSmbAuthenticationDialog
import kotlinx.coroutines.launch

@Composable
internal fun AppNavigationPlaybackDialogsSection(
    prefs: SharedPreferences,
    volumeDatabase: VolumeDatabase,
    selectedFile: File?,
    lastUsedCoreName: String?,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates,
    onCancelRemoteLoadJob: () -> Unit,
    showUrlOrPathDialog: Boolean,
    urlOrPathInput: String,
    urlOrPathForceCaching: Boolean,
    onUrlOrPathInputChanged: (String) -> Unit,
    onUrlOrPathForceCachingChanged: (Boolean) -> Unit,
    onShowUrlOrPathDialogChanged: (Boolean) -> Unit,
    remoteLoadUiState: RemoteLoadUiState?,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    showSoxExperimentalDialog: Boolean,
    onShowSoxExperimentalDialogChanged: (Boolean) -> Unit,
    showSubtuneSelectorDialog: Boolean,
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    showPlaylistSelectorDialog: Boolean,
    playlistDialogTitle: String,
    playlistDialogSubtitle: String?,
    playlistDialogShuffleActive: Boolean,
    playlistEntries: List<PlaylistTrackEntry>,
    currentPlaylistEntryId: String?,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
    onSelectPlaylistEntry: (PlaylistTrackEntry) -> Unit,
    playlistLibraryState: PlaylistLibraryState,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit,
    activePlaylist: StoredPlaylist?,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit = {},
    browserPlaylistPrompt: BrowserPlaylistPrompt,
    showAudioEffectsDialog: Boolean,
    tempMasterVolumeDb: Float,
    tempPluginVolumeDb: Float,
    tempSongVolumeDb: Float,
    tempIgnoreCoreVolumeForSong: Boolean,
    tempForceMono: Boolean,
    masterVolumeDb: Float,
    songVolumeDb: Float,
    ignoreCoreVolumeForSong: Boolean,
    forceMono: Boolean,
    onTempMasterVolumeDbChanged: (Float) -> Unit,
    onTempPluginVolumeDbChanged: (Float) -> Unit,
    onTempSongVolumeDbChanged: (Float) -> Unit,
    onTempIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onTempForceMonoChanged: (Boolean) -> Unit,
    onMasterVolumeDbChanged: (Float) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onForceMonoChanged: (Boolean) -> Unit,
    onShowAudioEffectsDialogChanged: (Boolean) -> Unit
) {
    val wiringScope = androidx.compose.runtime.rememberCoroutineScope()
    SideEffect {
        RemoteLoadUiStateHolder.current = remoteLoadUiState
    }

    val context = LocalContext.current
    var pendingPlaylistImportEntries by remember { mutableStateOf<List<PlaylistTrackEntry>?>(null) }
    var pendingPlaylistImportTitle by remember { mutableStateOf<String?>(null) }
    var pendingPlaylistImportDialogTitle by remember { mutableStateOf("Add to playlist") }

    val onSaveActivePlaylist: (() -> Unit)? = activePlaylist?.takeIf { it.entries.isNotEmpty() }?.let { active ->
        {
            pendingPlaylistImportDialogTitle = "Save playlist"
            pendingPlaylistImportTitle = active.title
            pendingPlaylistImportEntries = active.entries
        }
    }
    val onActivePlaylistEntryAddTrackToPlaylist: (PlaylistTrackEntry) -> Unit = { entry ->
        pendingPlaylistImportDialogTitle = "Add to playlist"
        pendingPlaylistImportTitle = null
        pendingPlaylistImportEntries = listOf(entry)
    }
    val onActivePlaylistEntryToggleFavorite: (PlaylistTrackEntry) -> Unit = { entry ->
        toggleEntryFavorite(
            context = context,
            playlistLibraryState = playlistLibraryState,
            entry = entry,
            onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
        )
    }
    val isActivePlaylistEntryFavorite: (PlaylistTrackEntry) -> Boolean = { entry ->
        playlistContainsTrack(playlistLibraryState.favorites, entry.source, entry.subtuneIndex)
    }
    val onSaveBrowserPlaylist: (ParsedPlaylistDocument) -> Unit = { doc ->
        pendingPlaylistImportDialogTitle = "Save playlist"
        pendingPlaylistImportTitle = doc.title
        pendingPlaylistImportEntries = doc.entries
    }
    val onPlaylistPreviewEntryAddTrackToPlaylist: (PlaylistTrackEntry) -> Unit = { entry ->
        pendingPlaylistImportDialogTitle = "Add to playlist"
        pendingPlaylistImportTitle = null
        pendingPlaylistImportEntries = listOf(entry)
    }
    val onPlaylistPreviewEntryToggleFavorite: (PlaylistTrackEntry) -> Unit = { entry ->
        toggleEntryFavorite(
            context = context,
            playlistLibraryState = playlistLibraryState,
            entry = entry,
            onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
        )
    }
    val isPlaylistPreviewEntryFavorite: (PlaylistTrackEntry) -> Boolean = { entry ->
        playlistContainsTrack(playlistLibraryState.favorites, entry.source, entry.subtuneIndex)
    }

    val appPrefs = remember(prefs) { AndroidAppPreferences(prefs) }
    val availableEffects = remember { queryEffectDescriptors() }
    var globalChain by remember { mutableStateOf(readGlobalEffectChain(appPrefs)) }
    var coreChain by remember(lastUsedCoreName) { mutableStateOf(readCoreEffectChain(appPrefs, lastUsedCoreName)) }
    var bypassAllEffects by remember { mutableStateOf(readBypassAllEffects(appPrefs)) }
    var effectPresets by remember { mutableStateOf(readEffectPresets(appPrefs)) }
    var selectedScope by remember {
        mutableStateOf(
            EffectChainScopeTarget.fromStorageKey(
                prefs.getString(AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE, "global")
            )
        )
    }

    fun currentPlaylistEffectChain(): EffectChain? =
        readPlaylistEffectChain(playlistLibraryState, activePlaylist?.id)
            ?: readPlaylistEffectChain(activePlaylist)

    fun currentSongEffectChain(): EffectChain? =
        readSongEffectChain(volumeDatabase, selectedFile?.absolutePath)

    var tempSelectedScope by remember { mutableStateOf(selectedScope) }
    var tempGlobalChain by remember { mutableStateOf(globalChain) }
    var tempCoreChain by remember { mutableStateOf(coreChain) }
    var tempPlaylistChain by remember { mutableStateOf(currentPlaylistEffectChain()) }
    var tempSongChain by remember { mutableStateOf(currentSongEffectChain()) }
    var tempBypassAllEffects by remember { mutableStateOf(bypassAllEffects) }
    var tempEffectPresets by remember { mutableStateOf(effectPresets) }

    fun applyCurrentTempChainToNative() {
        applyEffectChainToNative(
            resolveEffectiveEffectChain(
                global = tempGlobalChain,
                core = if (lastUsedCoreName != null) tempCoreChain else null,
                playlist = if (activePlaylist != null) tempPlaylistChain else null,
                song = if (selectedFile != null) tempSongChain else null,
                bypassAll = tempBypassAllEffects
            )
        )
    }

    LaunchedEffect(showAudioEffectsDialog) {
        if (!showAudioEffectsDialog) return@LaunchedEffect
        globalChain = readGlobalEffectChain(appPrefs)
        coreChain = readCoreEffectChain(appPrefs, lastUsedCoreName)
        bypassAllEffects = readBypassAllEffects(appPrefs)
        effectPresets = readEffectPresets(appPrefs)
        val playlistChain = currentPlaylistEffectChain()
        val songChain = currentSongEffectChain()
        tempGlobalChain = globalChain
        tempCoreChain = coreChain
        tempPlaylistChain = playlistChain
        tempSongChain = songChain
        tempBypassAllEffects = bypassAllEffects
        tempEffectPresets = effectPresets
        tempSelectedScope = resolveInitialDialogScope(
            savedScope = selectedScope,
            hasActiveCore = lastUsedCoreName != null,
            hasActivePlaylist = activePlaylist != null,
            hasActiveSong = selectedFile != null,
            coreChain = coreChain,
            playlistChain = playlistChain,
            songChain = songChain
        )
    }

    LaunchedEffect(
        showAudioEffectsDialog,
        lastUsedCoreName,
        selectedFile?.absolutePath,
        activePlaylist?.id,
        activePlaylist?.effectChain,
        playlistLibraryState
    ) {
        if (showAudioEffectsDialog) return@LaunchedEffect
        globalChain = readGlobalEffectChain(appPrefs)
        coreChain = readCoreEffectChain(appPrefs, lastUsedCoreName)
        applyEffectChainToNative(
            resolveEffectiveEffectChainForPrefs(
                prefs = appPrefs,
                coreName = lastUsedCoreName,
                playlist = activePlaylist,
                playlistState = playlistLibraryState,
                songVolumeStore = volumeDatabase,
                songPath = selectedFile?.absolutePath
            )
        )
    }

    PlaybackDialogsHost(
        showUrlOrPathDialog = showUrlOrPathDialog,
        urlOrPathInput = urlOrPathInput,
        urlOrPathForceCaching = urlOrPathForceCaching,
        onUrlOrPathInputChange = onUrlOrPathInputChanged,
        onUrlOrPathForceCachingChange = { checked ->
            onUrlOrPathForceCachingChanged(checked)
            prefs.edit()
                .putBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, checked)
                .apply()
        },
        onUrlOrPathDismiss = { onShowUrlOrPathDialogChanged(false) },
        onUrlOrPathOpen = {
            val openOptions = ManualSourceOpenOptions(forceCaching = urlOrPathForceCaching)
            onShowUrlOrPathDialogChanged(false)
            manualOpenDelegates.applyManualInputSelection(urlOrPathInput, openOptions)
        },
        remoteLoadUiState = remoteLoadUiState,
        onCancelRemoteLoad = {
            onCancelRemoteLoadJob()
            onRemoteLoadUiStateChanged(null)
        },
        showSoxExperimentalDialog = showSoxExperimentalDialog,
        onDismissSoxExperimentalDialog = { onShowSoxExperimentalDialogChanged(false) },
        showSubtuneSelectorDialog = showSubtuneSelectorDialog,
        subtuneEntries = subtuneEntries,
        currentSubtuneIndex = currentSubtuneIndex,
        onSelectSubtune = { subtuneIndex ->
            wiringScope.launch { playbackStateDelegates.selectSubtune(subtuneIndex) }
            onShowSubtuneSelectorDialogChanged(false)
        },
        onDismissSubtuneSelector = { onShowSubtuneSelectorDialogChanged(false) },
        showPlaylistSelectorDialog = showPlaylistSelectorDialog,
        playlistDialogTitle = playlistDialogTitle,
        playlistDialogSubtitle = playlistDialogSubtitle,
        playlistDialogShuffleActive = playlistDialogShuffleActive,
        playlistEntries = playlistEntries,
        currentPlaylistEntryId = currentPlaylistEntryId,
        onSelectPlaylistEntry = {
            onSelectPlaylistEntry(it)
            onShowPlaylistSelectorDialogChanged(false)
        },
        onDismissPlaylistSelector = { onShowPlaylistSelectorDialogChanged(false) },
        onSaveActivePlaylist = onSaveActivePlaylist?.let { action ->
            {
                onShowPlaylistSelectorDialogChanged(false)
                action()
            }
        },
        onActivePlaylistEntryAddTrackToPlaylist = onActivePlaylistEntryAddTrackToPlaylist,
        onActivePlaylistEntryToggleFavorite = onActivePlaylistEntryToggleFavorite,
        isActivePlaylistEntryFavorite = isActivePlaylistEntryFavorite,
        browserPlaylistPrompt = browserPlaylistPrompt,
        onSaveBrowserPlaylist = onSaveBrowserPlaylist,
        onBrowserPlaylistPreviewEntryAddTrackToPlaylist = onPlaylistPreviewEntryAddTrackToPlaylist,
        onBrowserPlaylistPreviewEntryToggleFavorite = onPlaylistPreviewEntryToggleFavorite,
        isBrowserPlaylistPreviewEntryFavorite = isPlaylistPreviewEntryFavorite,
        showAudioEffectsDialog = showAudioEffectsDialog,
        tempMasterVolumeDb = tempMasterVolumeDb,
        tempPluginVolumeDb = tempPluginVolumeDb,
        tempSongVolumeDb = tempSongVolumeDb,
        tempIgnoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
        hasActiveCore = lastUsedCoreName != null,
        hasActivePlaylist = activePlaylist != null,
        hasActiveSong = selectedFile != null,
        currentCoreName = lastUsedCoreName,
        currentPlaylistName = activePlaylist?.title,
        currentSongName = selectedFile?.name,
        selectedScope = tempSelectedScope,
        tempGlobalChain = tempGlobalChain,
        tempCoreChain = tempCoreChain,
        tempPlaylistChain = tempPlaylistChain,
        tempSongChain = tempSongChain,
        availableEffects = availableEffects,
        tempBypassAllEffects = tempBypassAllEffects,
        tempPresets = tempEffectPresets,
        onMasterVolumeChange = {
            onTempMasterVolumeDbChanged(it)
            NativeBridge.setMasterGain(it)
        },
        onPluginVolumeChange = {
            onTempPluginVolumeDbChanged(it)
            NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else it)
        },
        onSongVolumeChange = {
            onTempSongVolumeDbChanged(it)
            NativeBridge.setSongGain(it)
        },
        onIgnoreCoreVolumeForSongChange = {
            onTempIgnoreCoreVolumeForSongChanged(it)
            NativeBridge.setPluginGain(if (it) 0f else tempPluginVolumeDb)
        },
        onSelectedScopeChange = { scope ->
            tempSelectedScope = scope
        },
        onGlobalChainChange = { next ->
            tempGlobalChain = next
            applyCurrentTempChainToNative()
        },
        onCoreChainChange = { next ->
            tempCoreChain = next
            applyCurrentTempChainToNative()
        },
        onPlaylistChainChange = { next ->
            tempPlaylistChain = next
            applyCurrentTempChainToNative()
        },
        onSongChainChange = { next ->
            tempSongChain = next
            applyCurrentTempChainToNative()
        },
        onBypassAllEffectsChange = { next ->
            tempBypassAllEffects = next
            applyCurrentTempChainToNative()
        },
        onPresetsChange = { next ->
            tempEffectPresets = next
        },
        onAudioEffectsResetVolumeTab = {
            onTempMasterVolumeDbChanged(0f)
            onTempPluginVolumeDbChanged(0f)
            onTempSongVolumeDbChanged(0f)
            onTempIgnoreCoreVolumeForSongChanged(false)
            NativeBridge.setMasterGain(0f)
            NativeBridge.setPluginGain(0f)
            NativeBridge.setSongGain(0f)
        },
        onAudioEffectsResetScopeChain = { scope ->
            when (scope) {
                EffectChainScopeTarget.Global -> tempGlobalChain = EffectChain.Empty
                EffectChainScopeTarget.Core -> tempCoreChain = null
                EffectChainScopeTarget.Playlist -> tempPlaylistChain = null
                EffectChainScopeTarget.Song -> tempSongChain = null
            }
            applyCurrentTempChainToNative()
        },
        onAudioEffectsDismiss = {
            NativeBridge.setMasterGain(masterVolumeDb)
            NativeBridge.setPluginGain(
                if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, lastUsedCoreName)
            )
            NativeBridge.setSongGain(songVolumeDb)
            applyEffectChainToNative(
                resolveEffectiveEffectChain(
                    global = globalChain,
                    core = coreChain,
                    playlist = currentPlaylistEffectChain(),
                    song = currentSongEffectChain(),
                    bypassAll = bypassAllEffects
                )
            )
            onShowAudioEffectsDialogChanged(false)
        },
        onAudioEffectsConfirm = {
            onMasterVolumeDbChanged(tempMasterVolumeDb)
            onPluginVolumeDbChanged(tempPluginVolumeDb)
            onSongVolumeDbChanged(tempSongVolumeDb)
            onIgnoreCoreVolumeForSongChanged(tempIgnoreCoreVolumeForSong)
            onForceMonoChanged(tempGlobalChain.master.mono)

            selectedScope = tempSelectedScope
            globalChain = tempGlobalChain
            coreChain = if (lastUsedCoreName != null) tempCoreChain else null
            bypassAllEffects = tempBypassAllEffects
            effectPresets = tempEffectPresets

            appPrefs.edit().apply {
                putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                putString(
                    AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE,
                    selectedScope.storageKey
                )
                writeBypassAllEffects(this, bypassAllEffects)
                writeEffectPresets(this, effectPresets)
                writeGlobalEffectChain(this, globalChain)
                if (lastUsedCoreName != null) {
                    writeCoreEffectChain(this, lastUsedCoreName, coreChain)
                }
                apply()
            }
            writePluginVolumeForDecoder(
                prefs = prefs,
                decoderName = lastUsedCoreName,
                valueDb = tempPluginVolumeDb
            )
            activePlaylist?.let { playlist ->
                val updatedPlaylistState = writePlaylistEffectChain(
                    state = playlistLibraryState,
                    playlistId = playlist.id,
                    chain = tempPlaylistChain
                )
                if (updatedPlaylistState != playlistLibraryState) {
                    onPlaylistLibraryStateChanged(updatedPlaylistState)
                }
                val normalizedJson = tempPlaylistChain?.toJson()
                if (playlist.effectChain != normalizedJson) {
                    onActivePlaylistChanged(playlist.copy(effectChain = normalizedJson))
                }
            }
            selectedFile?.absolutePath?.let { path ->
                volumeDatabase.setSongVolume(path, tempSongVolumeDb)
                volumeDatabase.setSongIgnoreCoreVolume(path, tempIgnoreCoreVolumeForSong)
                writeSongEffectChain(volumeDatabase, path, tempSongChain)
            }
            NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else tempPluginVolumeDb)
            applyEffectChainToNative(
                resolveEffectiveEffectChain(
                    global = globalChain,
                    core = coreChain,
                    playlist = if (activePlaylist != null) tempPlaylistChain else null,
                    song = if (selectedFile != null) tempSongChain else null,
                    bypassAll = bypassAllEffects
                )
            )
            onShowAudioEffectsDialogChanged(false)
        }
    )

    pendingPlaylistImportEntries?.let { entries ->
        AddToPlaylistChooserDialog(
            dialogTitle = pendingPlaylistImportDialogTitle,
            initialNewPlaylistTitle = pendingPlaylistImportTitle,
            playlists = playlistLibraryState.playlists,
            pendingSources = entries.map { it.source }.toSet(),
            onConfirm = { playlistId, newTitle ->
                applyLibraryAddToPlaylist(
                    context = context,
                    tracks = emptyList(),
                    entries = entries,
                    playlistId = playlistId,
                    newTitle = newTitle,
                    playlistLibraryState = playlistLibraryState,
                    onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
                )
                pendingPlaylistImportEntries = null
                pendingPlaylistImportTitle = null
            },
            onRemoveFromPlaylist = { playlistId ->
                if (playlistId == FAVORITES_PLAYLIST_ID) {
                    val matchingSources = entries.map { it.source to it.subtuneIndex }.toSet()
                    val filteredFavorites = playlistLibraryState.favorites.filterNot { fav ->
                        (fav.source to fav.subtuneIndex) in matchingSources
                    }
                    if (filteredFavorites.size != playlistLibraryState.favorites.size) {
                        onPlaylistLibraryStateChanged(
                            playlistLibraryState.copy(favorites = filteredFavorites)
                        )
                    }
                } else {
                    val target = playlistLibraryState.playlists.firstOrNull { it.id == playlistId }
                    if (target != null) {
                        val matchingSources = entries.map { it.source to it.subtuneIndex }.toSet()
                        val matching = target.entries.filter { (it.source to it.subtuneIndex) in matchingSources }
                        var updatedState = playlistLibraryState
                        matching.forEach { match ->
                            updatedState = removeStoredPlaylistEntry(updatedState, playlistId, match.id)
                        }
                        onPlaylistLibraryStateChanged(updatedState)
                    }
                }
            },
            onDismiss = {
                pendingPlaylistImportEntries = null
                pendingPlaylistImportTitle = null
            }
        )
    }

    ManualSmbAuthCoordinator.pendingPrompt?.let { prompt ->
        key(prompt.resolved.sourceId, prompt.host, prompt.share, prompt.failureMessage) {
            var username by remember(prompt.initialUsername) { mutableStateOf(prompt.initialUsername.orEmpty()) }
            var password by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }

            ManualSmbAuthenticationDialog(
                host = prompt.host,
                share = prompt.share,
                failureMessage = prompt.failureMessage,
                username = username,
                password = password,
                passwordVisible = passwordVisible,
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onPasswordVisibilityChange = { passwordVisible = it },
                onDismiss = {
                    ManualSmbAuthCoordinator.clearPrompt()
                },
                onConfirm = {
                    val smbSpec = prompt.resolved.smbSpec ?: run {
                        ManualSmbAuthCoordinator.clearPrompt()
                        return@ManualSmbAuthenticationDialog
                    }
                    val normalizedUsername = username.trim().ifBlank { null }
                    val normalizedPassword = password.trim().ifBlank { null }
                    val updatedSpec = smbSpec.copy(
                        username = normalizedUsername,
                        password = normalizedPassword
                    )
                    ManualSmbAuthCoordinator.rememberCredentials(
                        smbSpec,
                        normalizedUsername,
                        normalizedPassword
                    )
                    ManualSmbAuthCoordinator.clearPrompt()
                    manualOpenDelegates.launchManualRemoteSelection(
                        resolved = prompt.resolved.copy(
                            requestUrl = buildSmbRequestUri(updatedSpec),
                            smbSpec = updatedSpec
                        ),
                        options = prompt.options,
                        expandOverride = prompt.expandOverride
                    )
                }
            )
        }
    }

    ManualSmbAuthCoordinator.pendingHttpPrompt?.let { prompt ->
        key(
            prompt.resolved.sourceId,
            prompt.requestSpec.scheme,
            prompt.requestSpec.host,
            prompt.requestSpec.port ?: -1,
            prompt.failureMessage
        ) {
            var username by remember(prompt.initialUsername) { mutableStateOf(prompt.initialUsername.orEmpty()) }
            var password by remember { mutableStateOf("") }
            var passwordVisible by remember { mutableStateOf(false) }

            ManualHttpAuthenticationDialog(
                requestSpec = prompt.requestSpec,
                failureMessage = prompt.failureMessage,
                username = username,
                password = password,
                passwordVisible = passwordVisible,
                onUsernameChange = { username = it },
                onPasswordChange = { password = it },
                onPasswordVisibilityChange = { passwordVisible = it },
                onDismiss = { ManualSmbAuthCoordinator.clearHttpPrompt() },
                onConfirm = {
                    val normalizedUsername = username.trim().ifBlank { null }
                    val normalizedPassword = password.trim().ifBlank { null }
                    val updatedSpec = prompt.requestSpec.copy(
                        username = normalizedUsername,
                        password = normalizedPassword
                    )
                    ManualSmbAuthCoordinator.rememberCredentials(
                        prompt.requestSpec,
                        normalizedUsername,
                        normalizedPassword
                    )
                    ManualSmbAuthCoordinator.clearHttpPrompt()
                    manualOpenDelegates.launchManualRemoteSelection(
                        resolved = prompt.resolved.copy(
                            sourceId = buildHttpSourceId(updatedSpec),
                            requestUrl = buildHttpRequestUri(updatedSpec)
                        ),
                        options = prompt.options,
                        expandOverride = prompt.expandOverride
                    )
                }
            )
        }
    }
}

private fun toggleEntryFavorite(
    context: Context,
    playlistLibraryState: PlaylistLibraryState,
    entry: PlaylistTrackEntry,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit
) {
    val existingFavorite = playlistLibraryState.favorites.firstOrNull { fav ->
        samePath(fav.source, entry.source) && (fav.subtuneIndex ?: -1) == (entry.subtuneIndex ?: -1)
    }
    if (existingFavorite != null) {
        onPlaylistLibraryStateChanged(removeFavoriteTrack(playlistLibraryState, existingFavorite.id))
        Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
    } else {
        onPlaylistLibraryStateChanged(upsertFavoriteTrack(playlistLibraryState, entry))
        Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show()
    }
}
