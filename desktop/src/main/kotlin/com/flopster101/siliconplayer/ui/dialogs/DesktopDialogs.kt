package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
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
