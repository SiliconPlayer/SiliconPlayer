package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.platform.AudioOutputRouteInfo
import java.io.File

@Composable
internal fun AddToPlaylistChooserDialog(
    playlists: List<StoredPlaylist>,
    favorites: List<PlaylistTrackEntry> = emptyList(),
    showFavorites: Boolean = false,
    pendingSources: Set<String>,
    dialogTitle: String = "Add to playlist",
    initialNewPlaylistTitle: String? = null,
    onConfirm: (playlistId: String?, newTitle: String) -> Unit,
    onRemoveFromPlaylist: (playlistId: String) -> Unit,
    onDismiss: () -> Unit
) {
}

@Composable
internal fun AudioOutputDetailsDialog(
    routeInfo: AudioOutputRouteInfo,
    displayFile: File?,
    sourceId: String?,
    requestUrl: String?,
    decoderName: String?,
    trackSampleRateHz: Int,
    decoderRenderRateHz: Int = 0,
    outputStreamRateHz: Int = 0,
    channelCount: Int,
    bitDepthLabel: String,
    isPlaying: Boolean = false,
    playbackCapabilitiesFlags: Int = 0,
    bitPerfectEnabled: Boolean = false,
    onBitPerfectToggled: (Boolean) -> Unit = {},
    onRestartTrack: () -> Unit = {},
    onOpenAudioSettings: () -> Unit,
    onDismiss: () -> Unit
) {
}

@Composable
internal fun AudioOutputDeviceDialog(
    onDismiss: () -> Unit
) {
}

@Composable
internal fun VisualizationModePickerDialog(
    availableModes: List<VisualizationMode>,
    selectedMode: VisualizationMode,
    onSelectMode: (VisualizationMode) -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenOptions: () -> Unit,
    onDismiss: () -> Unit
) {
}

@Composable
internal fun VisualizationOptionsSheet(
    mode: VisualizationMode,
    globalInputGain: Int,
    onGlobalInputGainChange: (Int) -> Unit,
    trackInputGain: Int,
    onTrackInputGainChange: (Int) -> Unit,
    showChannelLabels: Boolean,
    onShowChannelLabelsChange: (Boolean) -> Unit,
    savedProjectMPreset: String?,
    onProjectMPresetSelected: (String) -> Unit,
    presetSetLabels: Map<String, String>,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit,
    resetNonce: Int = 0
) {
}
