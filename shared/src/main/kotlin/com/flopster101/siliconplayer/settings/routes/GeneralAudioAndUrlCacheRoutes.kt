package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

internal data class GeneralAudioRouteState(
    val respondHeadphoneMediaButtons: Boolean,
    val pauseOnHeadphoneDisconnect: Boolean,
    val audioFocusInterrupt: Boolean,
    val audioDucking: Boolean,
    val headphoneMediaButtonsAvailable: Boolean = true,
    val pauseOnHeadphoneDisconnectAvailable: Boolean = true,
    val audioFocusInterruptAvailable: Boolean = true,
    val audioDuckingAvailable: Boolean = true,
    val audioBackendPreference: AudioBackendPreference,
    val audioPerformanceMode: AudioPerformanceMode,
    val audioBufferPreset: AudioBufferPreset,
    val audioResamplerPreference: AudioResamplerPreference,
    val audioOutputLimiterEnabled: Boolean,
    val lookaheadClipperMode: LookaheadClipperMode,
    val multiChannelOutputMode: MultiChannelOutputMode,
    val audioAllowBackendFallback: Boolean,
    val bitPerfectUsbAudio: Boolean
)

internal data class GeneralAudioRouteActions(
    val onRespondHeadphoneMediaButtonsChanged: (Boolean) -> Unit,
    val onPauseOnHeadphoneDisconnectChanged: (Boolean) -> Unit,
    val onAudioFocusInterruptChanged: (Boolean) -> Unit,
    val onAudioDuckingChanged: (Boolean) -> Unit,
    val onOpenAudioEffects: () -> Unit,
    val onClearAllAudioParameters: () -> Unit,
    val onClearPluginAudioParameters: () -> Unit,
    val onClearSongAudioParameters: () -> Unit,
    val onAudioBackendPreferenceChanged: (AudioBackendPreference) -> Unit,
    val onAudioPerformanceModeChanged: (AudioPerformanceMode) -> Unit,
    val onAudioBufferPresetChanged: (AudioBufferPreset) -> Unit,
    val onAudioResamplerPreferenceChanged: (AudioResamplerPreference) -> Unit,
    val onAudioOutputLimiterEnabledChanged: (Boolean) -> Unit,
    val onLookaheadClipperModeChanged: (LookaheadClipperMode) -> Unit,
    val onMultiChannelOutputModeChanged: (MultiChannelOutputMode) -> Unit,
    val onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    val onBitPerfectUsbAudioChanged: (Boolean) -> Unit
)

internal data class UrlCacheRouteState(
    val fileCacheClearOnLaunch: Boolean,
    val fileCacheMaxTracks: Int,
    val fileCacheMaxBytes: Long,
    val streamingCacheClearOnLaunch: Boolean,
    val streamingCacheMaxTracks: Int,
    val streamingCacheMaxBytes: Long,
    val archiveCacheClearOnLaunch: Boolean,
    val archiveCacheMaxMounts: Int,
    val archiveCacheMaxBytes: Long,
    val archiveCacheMaxAgeDays: Int,
    val urlCacheClearOnLaunch: Boolean = fileCacheClearOnLaunch,
    val urlCacheMaxTracks: Int = fileCacheMaxTracks,
    val urlCacheMaxBytes: Long = fileCacheMaxBytes
)

internal data class UrlCacheRouteActions(
    val onFileCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onFileCacheMaxTracksChanged: (Int) -> Unit,
    val onFileCacheMaxBytesChanged: (Long) -> Unit,
    val onOpenFileCacheManager: () -> Unit,
    val onClearFileCacheNow: () -> Unit,
    val onStreamingCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onStreamingCacheMaxTracksChanged: (Int) -> Unit,
    val onStreamingCacheMaxBytesChanged: (Long) -> Unit,
    val onOpenStreamingCacheManager: () -> Unit,
    val onClearStreamingCacheNow: () -> Unit,
    val onArchiveCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onArchiveCacheMaxMountsChanged: (Int) -> Unit,
    val onArchiveCacheMaxBytesChanged: (Long) -> Unit,
    val onArchiveCacheMaxAgeDaysChanged: (Int) -> Unit,
    val onClearArchiveCacheNow: () -> Unit,
    val onOpenCacheManager: () -> Unit = onOpenFileCacheManager,
    val onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit = onFileCacheClearOnLaunchChanged,
    val onUrlCacheMaxTracksChanged: (Int) -> Unit = onFileCacheMaxTracksChanged,
    val onUrlCacheMaxBytesChanged: (Long) -> Unit = onFileCacheMaxBytesChanged,
    val onClearUrlCacheNow: () -> Unit = onClearFileCacheNow
)

@Composable
internal fun GeneralAudioRouteContent(
    state: GeneralAudioRouteState,
    actions: GeneralAudioRouteActions
) {
    // Whole section is platform-gated: unavailable rows hide with their spacer,
    // so hiding all four removes the section with no leftover gaps.
    if (
        state.headphoneMediaButtonsAvailable || state.pauseOnHeadphoneDisconnectAvailable ||
            state.audioFocusInterruptAvailable || state.audioDuckingAvailable
    ) {
        SettingsSectionLabel("Output behavior")
        if (state.headphoneMediaButtonsAvailable) {
            PlayerSettingToggleCard(
                title = "Respond to headset media buttons",
                description = "Allow headphone/bluetooth media buttons to control playback.",
                checked = state.respondHeadphoneMediaButtons,
                onCheckedChange = actions.onRespondHeadphoneMediaButtonsChanged
            )
            SettingsRowSpacer()
        }
        if (state.pauseOnHeadphoneDisconnectAvailable) {
            PlayerSettingToggleCard(
                title = "Pause on output disconnect",
                description = "Pause playback when headphones/output device disconnects.",
                checked = state.pauseOnHeadphoneDisconnect,
                onCheckedChange = actions.onPauseOnHeadphoneDisconnectChanged
            )
            SettingsRowSpacer()
        }
        if (state.audioFocusInterruptAvailable) {
            PlayerSettingToggleCard(
                title = "Allow interruption by other apps",
                description = "Pause playback when another app starts playing audio.",
                checked = state.audioFocusInterrupt,
                onCheckedChange = actions.onAudioFocusInterruptChanged
            )
            SettingsRowSpacer()
        }
        if (state.audioDuckingAvailable) {
            PlayerSettingToggleCard(
                title = "Duck audio instead of pausing",
                description = "Lower volume temporarily for brief interruptions (e.g., notifications) instead of pausing.",
                checked = state.audioDucking,
                onCheckedChange = actions.onAudioDuckingChanged
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    SettingsSectionLabel("Audio processing")
    SettingsItemCard(
        title = "Audio effects",
        description = "Volume controls and audio processing.",
        icon = Icons.Default.Tune,
        onClick = actions.onOpenAudioEffects
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Output limiter",
        description = "Enable dynamic limiting before soft-clip to reduce hard clipping crackle at high gain.",
        checked = state.audioOutputLimiterEnabled,
        onCheckedChange = actions.onAudioOutputLimiterEnabledChanged
    )
    SettingsRowSpacer()
    LookaheadClipperSelectorCard(
        selectedMode = state.lookaheadClipperMode,
        onSelectedModeChanged = actions.onLookaheadClipperModeChanged
    )
    SettingsRowSpacer()
    ClearAudioParametersCard(
        onClearAll = actions.onClearAllAudioParameters,
        onClearPlugins = actions.onClearPluginAudioParameters,
        onClearSongs = actions.onClearSongAudioParameters
    )
    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Audio output pipeline")
    AudioBackendSelectorCard(
        selectedPreference = state.audioBackendPreference,
        onSelectedPreferenceChanged = actions.onAudioBackendPreferenceChanged
    )
    val selectedBackend = state.audioBackendPreference
    SettingsRowSpacer()
    AudioPerformanceModeSelectorCard(
        selectedMode = state.audioPerformanceMode,
        onSelectedModeChanged = actions.onAudioPerformanceModeChanged,
        title = "${selectedBackend.label} performance mode",
        description = "Stream mode for ${selectedBackend.label}. None uses the platform's power-efficient deep buffer; low latency trades it for responsiveness."
    )
    SettingsRowSpacer()
    AudioBufferPresetSelectorCard(
        selectedPreset = state.audioBufferPreset,
        onSelectedPresetChanged = actions.onAudioBufferPresetChanged,
        title = "${selectedBackend.label} buffer preset",
        description = "Buffer sizing profile for ${selectedBackend.label}. Large is the recommended default; Very large adds extra underrun headroom on slower devices."
    )
    SettingsRowSpacer()
    AudioResamplerSelectorCard(
        selectedPreference = state.audioResamplerPreference,
        onSelectedPreferenceChanged = actions.onAudioResamplerPreferenceChanged,
        description = "Applies before backend output. SoX is experimental and falls back to built-in for discontinuous timeline cores."
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Allow backend fallback",
        description = "If selected backend is unavailable, fall back automatically to a working output backend.",
        checked = state.audioAllowBackendFallback,
        onCheckedChange = actions.onAudioAllowBackendFallbackChanged
    )
    SettingsRowSpacer()
    MultiChannelOutputModeSelectorCard(
        selectedMode = state.multiChannelOutputMode,
        onSelectedModeChanged = actions.onMultiChannelOutputModeChanged
    )
    com.flopster101.siliconplayer.platform.LocalSettingsPlatformContent.current.PlatformAudioOptions(
        bitPerfectUsbAudio = state.bitPerfectUsbAudio,
        onBitPerfectUsbAudioChanged = actions.onBitPerfectUsbAudioChanged
    )
}

@Composable
internal fun UrlCacheRouteContent(
    state: UrlCacheRouteState,
    actions: UrlCacheRouteActions
) {
    val fileCacheClearOnLaunch = state.fileCacheClearOnLaunch
    val onFileCacheClearOnLaunchChanged = actions.onFileCacheClearOnLaunchChanged
    val fileCacheMaxTracks = state.fileCacheMaxTracks
    val onFileCacheMaxTracksChanged = actions.onFileCacheMaxTracksChanged
    val fileCacheMaxBytes = state.fileCacheMaxBytes
    val onFileCacheMaxBytesChanged = actions.onFileCacheMaxBytesChanged
    val onOpenFileCacheManager = actions.onOpenFileCacheManager
    val onClearFileCacheNow = actions.onClearFileCacheNow

    val streamingCacheClearOnLaunch = state.streamingCacheClearOnLaunch
    val onStreamingCacheClearOnLaunchChanged = actions.onStreamingCacheClearOnLaunchChanged
    val streamingCacheMaxTracks = state.streamingCacheMaxTracks
    val onStreamingCacheMaxTracksChanged = actions.onStreamingCacheMaxTracksChanged
    val streamingCacheMaxBytes = state.streamingCacheMaxBytes
    val onStreamingCacheMaxBytesChanged = actions.onStreamingCacheMaxBytesChanged
    val onOpenStreamingCacheManager = actions.onOpenStreamingCacheManager
    val onClearStreamingCacheNow = actions.onClearStreamingCacheNow

    val archiveCacheClearOnLaunch = state.archiveCacheClearOnLaunch
    val onArchiveCacheClearOnLaunchChanged = actions.onArchiveCacheClearOnLaunchChanged
    val archiveCacheMaxMounts = state.archiveCacheMaxMounts
    val onArchiveCacheMaxMountsChanged = actions.onArchiveCacheMaxMountsChanged
    val archiveCacheMaxBytes = state.archiveCacheMaxBytes
    val onArchiveCacheMaxBytesChanged = actions.onArchiveCacheMaxBytesChanged
    val archiveCacheMaxAgeDays = state.archiveCacheMaxAgeDays
    val onArchiveCacheMaxAgeDaysChanged = actions.onArchiveCacheMaxAgeDaysChanged
    val onClearArchiveCacheNow = actions.onClearArchiveCacheNow

    var showFileCacheTrackLimitDialog by remember { mutableStateOf(false) }
    var showFileCacheSizeLimitDialog by remember { mutableStateOf(false) }
    var showClearFileCacheConfirmDialog by remember { mutableStateOf(false) }

    var showStreamingCacheTrackLimitDialog by remember { mutableStateOf(false) }
    var showStreamingCacheSizeLimitDialog by remember { mutableStateOf(false) }
    var showClearStreamingCacheConfirmDialog by remember { mutableStateOf(false) }

    var showArchiveMountLimitDialog by remember { mutableStateOf(false) }
    var showArchiveSizeLimitDialog by remember { mutableStateOf(false) }
    var showArchiveAgeLimitDialog by remember { mutableStateOf(false) }
    var showClearArchiveCacheConfirmDialog by remember { mutableStateOf(false) }

    SettingsSectionLabel("File cache")
    PlayerSettingToggleCard(
        title = "Clear cache on app launch",
        description = "Delete all cached files each time the app starts.",
        checked = fileCacheClearOnLaunch,
        onCheckedChange = onFileCacheClearOnLaunchChanged
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache song limit",
        description = "$fileCacheMaxTracks songs",
        icon = Icons.Default.MoreHoriz,
        onClick = { showFileCacheTrackLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache size limit",
        description = String.format(Locale.US, "%.2f GB", fileCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
        icon = Icons.Default.MoreHoriz,
        onClick = { showFileCacheSizeLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Manage cached files",
        description = "Browse cached files, long-press multi-select, delete, and export.",
        icon = Icons.Default.MoreHoriz,
        onClick = onOpenFileCacheManager
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Clear cache now",
        description = "Delete all currently cached files immediately.",
        icon = Icons.Default.Delete,
        onClick = { showClearFileCacheConfirmDialog = true }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("URL/Streaming cache")
    PlayerSettingToggleCard(
        title = "Clear cache on app launch",
        description = "Delete streaming cache each time the app starts.",
        checked = streamingCacheClearOnLaunch,
        onCheckedChange = onStreamingCacheClearOnLaunchChanged
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache song limit",
        description = "$streamingCacheMaxTracks songs",
        icon = Icons.Default.MoreHoriz,
        onClick = { showStreamingCacheTrackLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache size limit",
        description = String.format(Locale.US, "%.2f GB", streamingCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
        icon = Icons.Default.MoreHoriz,
        onClick = { showStreamingCacheSizeLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Manage cached files",
        description = "Browse cached streaming files, long-press multi-select, delete, and export.",
        icon = Icons.Default.MoreHoriz,
        onClick = onOpenStreamingCacheManager
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Clear cache now",
        description = "Delete all currently cached streaming files immediately.",
        icon = Icons.Default.Delete,
        onClick = { showClearStreamingCacheConfirmDialog = true }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Archive cache")
    PlayerSettingToggleCard(
        title = "Clear archive cache on app launch",
        description = "Delete mounted ZIP extraction folders each time the app starts.",
        checked = archiveCacheClearOnLaunch,
        onCheckedChange = onArchiveCacheClearOnLaunchChanged
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Archive mount limit",
        description = "$archiveCacheMaxMounts mounted archives",
        icon = Icons.Default.MoreHoriz,
        onClick = { showArchiveMountLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Archive cache size limit",
        description = String.format(Locale.US, "%.2f GB", archiveCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
        icon = Icons.Default.MoreHoriz,
        onClick = { showArchiveSizeLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Archive max age",
        description = "$archiveCacheMaxAgeDays days",
        icon = Icons.Default.MoreHoriz,
        onClick = { showArchiveAgeLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Clear archive cache now",
        description = "Delete mounted ZIP extraction folders immediately.",
        icon = Icons.Default.Delete,
        onClick = { showClearArchiveCacheConfirmDialog = true }
    )

    if (showFileCacheTrackLimitDialog) {
        SettingsTextInputDialog(
            title = "Cache song limit",
            fieldLabel = "Max songs",
            initialValue = fileCacheMaxTracks.toString(),
            placeholder = "100",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(6) },
            onDismiss = { showFileCacheTrackLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onFileCacheMaxTracksChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showFileCacheSizeLimitDialog) {
        CacheSizeLimitDialog(
            initialBytes = fileCacheMaxBytes,
            onDismiss = { showFileCacheSizeLimitDialog = false },
            onConfirmBytes = onFileCacheMaxBytesChanged
        )
    }

    if (showStreamingCacheTrackLimitDialog) {
        SettingsTextInputDialog(
            title = "Cache song limit",
            fieldLabel = "Max songs",
            initialValue = streamingCacheMaxTracks.toString(),
            placeholder = "100",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(6) },
            onDismiss = { showStreamingCacheTrackLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onStreamingCacheMaxTracksChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showStreamingCacheSizeLimitDialog) {
        CacheSizeLimitDialog(
            initialBytes = streamingCacheMaxBytes,
            onDismiss = { showStreamingCacheSizeLimitDialog = false },
            onConfirmBytes = onStreamingCacheMaxBytesChanged
        )
    }

    if (showArchiveMountLimitDialog) {
        SettingsTextInputDialog(
            title = "Archive mount limit",
            fieldLabel = "Max mounted archives",
            initialValue = archiveCacheMaxMounts.toString(),
            placeholder = "24",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(6) },
            onDismiss = { showArchiveMountLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onArchiveCacheMaxMountsChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showArchiveSizeLimitDialog) {
        CacheSizeLimitDialog(
            initialBytes = archiveCacheMaxBytes,
            onDismiss = { showArchiveSizeLimitDialog = false },
            onConfirmBytes = onArchiveCacheMaxBytesChanged
        )
    }

    if (showArchiveAgeLimitDialog) {
        SettingsTextInputDialog(
            title = "Archive max age",
            fieldLabel = "Max age (days)",
            initialValue = archiveCacheMaxAgeDays.toString(),
            placeholder = "14",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(4) },
            onDismiss = { showArchiveAgeLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onArchiveCacheMaxAgeDaysChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showClearFileCacheConfirmDialog) {
        SettingsConfirmDialog(
            title = "Clear cached files now?",
            message = "This will remove all cached files, except the currently active one if it is being played.",
            confirmLabel = "Clear cache",
            onDismiss = { showClearFileCacheConfirmDialog = false },
            onConfirm = onClearFileCacheNow
        )
    }

    if (showClearStreamingCacheConfirmDialog) {
        SettingsConfirmDialog(
            title = "Clear streaming cache now?",
            message = "This will remove all cached streaming files, except the currently active one if it is being played.",
            confirmLabel = "Clear cache",
            onDismiss = { showClearStreamingCacheConfirmDialog = false },
            onConfirm = onClearStreamingCacheNow
        )
    }

    if (showClearArchiveCacheConfirmDialog) {
        SettingsConfirmDialog(
            title = "Clear archive cache now?",
            message = "This removes extracted archive mounts in app cache.",
            confirmLabel = "Clear archive cache",
            onDismiss = { showClearArchiveCacheConfirmDialog = false },
            onConfirm = onClearArchiveCacheNow
        )
    }
}
