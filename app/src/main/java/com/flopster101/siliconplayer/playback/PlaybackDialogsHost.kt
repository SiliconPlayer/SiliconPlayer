package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.audio.EffectChain
import com.flopster101.siliconplayer.audio.EffectChainScopeTarget
import com.flopster101.siliconplayer.audio.EffectDescriptor
import com.flopster101.siliconplayer.audio.EffectPreset
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import com.flopster101.siliconplayer.ui.dialogs.BrowserPlaylistPrompt
import com.flopster101.siliconplayer.ui.dialogs.BrowserPlaylistPromptDialogs
import com.flopster101.siliconplayer.ui.dialogs.PlaylistSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.SoxExperimentalDialog
import com.flopster101.siliconplayer.ui.dialogs.SubtuneSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.UrlOrPathDialog

@Composable
internal fun PlaybackDialogsHost(
    showUrlOrPathDialog: Boolean,
    urlOrPathInput: String,
    urlOrPathForceCaching: Boolean,
    onUrlOrPathInputChange: (String) -> Unit,
    onUrlOrPathForceCachingChange: (Boolean) -> Unit,
    onUrlOrPathDismiss: () -> Unit,
    onUrlOrPathOpen: () -> Unit,
    remoteLoadUiState: RemoteLoadUiState?,
    onCancelRemoteLoad: () -> Unit,
    showSoxExperimentalDialog: Boolean,
    onDismissSoxExperimentalDialog: () -> Unit,
    showSubtuneSelectorDialog: Boolean,
    subtuneEntries: List<SubtuneEntry>,
    currentSubtuneIndex: Int,
    onSelectSubtune: (Int) -> Unit,
    onDismissSubtuneSelector: () -> Unit,
    showPlaylistSelectorDialog: Boolean,
    playlistDialogTitle: String,
    playlistDialogSubtitle: String?,
    playlistDialogShuffleActive: Boolean,
    playlistEntries: List<PlaylistTrackEntry>,
    currentPlaylistEntryId: String?,
    onSelectPlaylistEntry: (PlaylistTrackEntry) -> Unit,
    onDismissPlaylistSelector: () -> Unit,
    onSaveActivePlaylist: (() -> Unit)? = null,
    onActivePlaylistEntryAddTrackToPlaylist: ((PlaylistTrackEntry) -> Unit)? = null,
    onActivePlaylistEntryToggleFavorite: ((PlaylistTrackEntry) -> Unit)? = null,
    isActivePlaylistEntryFavorite: ((PlaylistTrackEntry) -> Boolean)? = null,
    browserPlaylistPrompt: BrowserPlaylistPrompt,
    onSaveBrowserPlaylist: (ParsedPlaylistDocument) -> Unit,
    onBrowserPlaylistPreviewEntryAddTrackToPlaylist: ((PlaylistTrackEntry) -> Unit)? = null,
    onBrowserPlaylistPreviewEntryToggleFavorite: ((PlaylistTrackEntry) -> Unit)? = null,
    isBrowserPlaylistPreviewEntryFavorite: ((PlaylistTrackEntry) -> Boolean)? = null,
    showAudioEffectsDialog: Boolean,
    tempMasterVolumeDb: Float,
    tempPluginVolumeDb: Float,
    tempSongVolumeDb: Float,
    tempIgnoreCoreVolumeForSong: Boolean,
    hasActiveCore: Boolean,
    hasActivePlaylist: Boolean,
    hasActiveSong: Boolean,
    currentCoreName: String?,
    currentPlaylistName: String?,
    currentSongName: String?,
    selectedScope: EffectChainScopeTarget,
    tempGlobalChain: EffectChain,
    tempCoreChain: EffectChain?,
    tempPlaylistChain: EffectChain?,
    tempSongChain: EffectChain?,
    availableEffects: List<EffectDescriptor>,
    tempBypassAllEffects: Boolean = false,
    tempPresets: List<EffectPreset> = emptyList(),
    onMasterVolumeChange: (Float) -> Unit,
    onPluginVolumeChange: (Float) -> Unit,
    onSongVolumeChange: (Float) -> Unit,
    onIgnoreCoreVolumeForSongChange: (Boolean) -> Unit,
    onSelectedScopeChange: (EffectChainScopeTarget) -> Unit,
    onGlobalChainChange: (EffectChain) -> Unit,
    onCoreChainChange: (EffectChain?) -> Unit,
    onPlaylistChainChange: (EffectChain?) -> Unit,
    onSongChainChange: (EffectChain?) -> Unit,
    onBypassAllEffectsChange: (Boolean) -> Unit = {},
    onPresetsChange: (List<EffectPreset>) -> Unit = {},
    onAudioEffectsResetVolumeTab: () -> Unit,
    onAudioEffectsResetScopeChain: (EffectChainScopeTarget) -> Unit,
    onAudioEffectsDismiss: () -> Unit,
    onAudioEffectsConfirm: () -> Unit
) {
    if (showUrlOrPathDialog) {
        UrlOrPathDialog(
            input = urlOrPathInput,
            forceCaching = urlOrPathForceCaching,
            onInputChange = onUrlOrPathInputChange,
            onForceCachingChange = onUrlOrPathForceCachingChange,
            onDismiss = onUrlOrPathDismiss,
            onOpen = onUrlOrPathOpen
        )
    }

    if (showSoxExperimentalDialog) {
        SoxExperimentalDialog(onDismiss = onDismissSoxExperimentalDialog)
    }

    if (showSubtuneSelectorDialog) {
        SubtuneSelectorDialog(
            subtuneEntries = subtuneEntries,
            currentSubtuneIndex = currentSubtuneIndex,
            onSelectSubtune = onSelectSubtune,
            onDismiss = onDismissSubtuneSelector
        )
    }

    if (showPlaylistSelectorDialog) {
        PlaylistSelectorDialog(
            title = playlistDialogTitle,
            subtitle = playlistDialogSubtitle,
            shuffleActive = playlistDialogShuffleActive,
            entries = playlistEntries,
            currentEntryId = currentPlaylistEntryId,
            onSelectEntry = onSelectPlaylistEntry,
            onDismiss = onDismissPlaylistSelector,
            onSaveAsPlaylist = onSaveActivePlaylist,
            onEntryAddTrackToPlaylist = onActivePlaylistEntryAddTrackToPlaylist,
            onEntryToggleFavorite = onActivePlaylistEntryToggleFavorite,
            isEntryFavorite = isActivePlaylistEntryFavorite
        )
    }

    BrowserPlaylistPromptDialogs(
        prompt = browserPlaylistPrompt,
        onSave = onSaveBrowserPlaylist,
        onEntryAddTrackToPlaylist = onBrowserPlaylistPreviewEntryAddTrackToPlaylist,
        onEntryToggleFavorite = onBrowserPlaylistPreviewEntryToggleFavorite,
        isEntryFavorite = isBrowserPlaylistPreviewEntryFavorite
    )

    if (showAudioEffectsDialog) {
        AudioEffectsDialog(
            masterVolumeDb = tempMasterVolumeDb,
            pluginVolumeDb = tempPluginVolumeDb,
            songVolumeDb = tempSongVolumeDb,
            ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
            hasActiveCore = hasActiveCore,
            hasActivePlaylist = hasActivePlaylist,
            hasActiveSong = hasActiveSong,
            currentCoreName = currentCoreName,
            currentPlaylistName = currentPlaylistName,
            currentSongName = currentSongName,
            selectedScope = selectedScope,
            globalChain = tempGlobalChain,
            coreChain = tempCoreChain,
            playlistChain = tempPlaylistChain,
            songChain = tempSongChain,
            availableEffects = availableEffects,
            bypassAllEffects = tempBypassAllEffects,
            presets = tempPresets,
            onMasterVolumeChange = onMasterVolumeChange,
            onPluginVolumeChange = onPluginVolumeChange,
            onSongVolumeChange = onSongVolumeChange,
            onIgnoreCoreVolumeForSongChange = onIgnoreCoreVolumeForSongChange,
            onSelectedScopeChange = onSelectedScopeChange,
            onGlobalChainChange = onGlobalChainChange,
            onCoreChainChange = onCoreChainChange,
            onPlaylistChainChange = onPlaylistChainChange,
            onSongChainChange = onSongChainChange,
            onBypassAllEffectsChange = onBypassAllEffectsChange,
            onPresetsChange = onPresetsChange,
            onResetVolumeTab = onAudioEffectsResetVolumeTab,
            onResetScopeChain = onAudioEffectsResetScopeChain,
            onDismiss = onAudioEffectsDismiss,
            onConfirm = onAudioEffectsConfirm
        )
    }
}
