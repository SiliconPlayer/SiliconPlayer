package com.flopster101.siliconplayer

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

internal data class GeneralAudioRouteState(
    val respondHeadphoneMediaButtons: Boolean,
    val pauseOnHeadphoneDisconnect: Boolean,
    val audioFocusInterrupt: Boolean,
    val audioDucking: Boolean,
    val audioBackendPreference: AudioBackendPreference,
    val audioPerformanceMode: AudioPerformanceMode,
    val audioBufferPreset: AudioBufferPreset,
    val audioResamplerPreference: AudioResamplerPreference,
    val audioOutputLimiterEnabled: Boolean,
    val lookaheadClipperMode: LookaheadClipperMode,
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
    val onAudioAllowBackendFallbackChanged: (Boolean) -> Unit,
    val onBitPerfectUsbAudioChanged: (Boolean) -> Unit
)

internal data class UrlCacheRouteState(
    val urlCacheClearOnLaunch: Boolean,
    val urlCacheMaxTracks: Int,
    val urlCacheMaxBytes: Long,
    val archiveCacheClearOnLaunch: Boolean,
    val archiveCacheMaxMounts: Int,
    val archiveCacheMaxBytes: Long,
    val archiveCacheMaxAgeDays: Int
)

internal data class UrlCacheRouteActions(
    val onUrlCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onUrlCacheMaxTracksChanged: (Int) -> Unit,
    val onUrlCacheMaxBytesChanged: (Long) -> Unit,
    val onArchiveCacheClearOnLaunchChanged: (Boolean) -> Unit,
    val onArchiveCacheMaxMountsChanged: (Int) -> Unit,
    val onArchiveCacheMaxBytesChanged: (Long) -> Unit,
    val onArchiveCacheMaxAgeDaysChanged: (Int) -> Unit,
    val onOpenCacheManager: () -> Unit,
    val onClearUrlCacheNow: () -> Unit,
    val onClearArchiveCacheNow: () -> Unit
)

@Composable
internal fun GeneralAudioRouteContent(
    state: GeneralAudioRouteState,
    actions: GeneralAudioRouteActions
) {
    SettingsSectionLabel("Output behavior")
    PlayerSettingToggleCard(
        title = "Respond to headset media buttons",
        description = "Allow headphone/bluetooth media buttons to control playback.",
        checked = state.respondHeadphoneMediaButtons,
        onCheckedChange = actions.onRespondHeadphoneMediaButtonsChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Pause on output disconnect",
        description = "Pause playback when headphones/output device disconnects.",
        checked = state.pauseOnHeadphoneDisconnect,
        onCheckedChange = actions.onPauseOnHeadphoneDisconnectChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Allow interruption by other apps",
        description = "Pause playback when another app starts playing audio.",
        checked = state.audioFocusInterrupt,
        onCheckedChange = actions.onAudioFocusInterruptChanged
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Duck audio instead of pausing",
        description = "Lower volume temporarily for brief interruptions (e.g., notifications) instead of pausing.",
        checked = state.audioDucking,
        onCheckedChange = actions.onAudioDuckingChanged
    )
    Spacer(modifier = Modifier.height(16.dp))
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
        description = "Stream mode for ${selectedBackend.label}. Low latency is recommended for responsive playback; power saving reduces CPU overhead."
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
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    var driverMethod by remember {
        mutableStateOf(BitPerfectDriverMethod.fromStorage(prefs.getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null)))
    }
    var showDriverMethodDialog by remember { mutableStateOf(false) }

    val bitPerfectSupportStatus = remember(driverMethod) {
        BitPerfectCoordinator.checkBitPerfectSupport(context, driverMethod)
    }
    val isBitPerfectSupported = bitPerfectSupportStatus == BitPerfectSupportStatus.Supported
    val (bitPerfectDesc, bitPerfectColor) = when (bitPerfectSupportStatus) {
        BitPerfectSupportStatus.Supported -> {
            Pair(
                "Bypasses Android audio mixer for bit-perfect output.",
                null
            )
        }
        BitPerfectSupportStatus.UnsupportedAudioHal -> {
            Pair(
                "Platform bit-perfect API is not supported by this device's audio HAL.",
                MaterialTheme.colorScheme.error
            )
        }
        BitPerfectSupportStatus.UnsupportedApiLevel -> {
            Pair(
                "Platform bit-perfect USB routing requires Android 14 or higher.",
                MaterialTheme.colorScheme.error
            )
        }
        BitPerfectSupportStatus.NoUsbDeviceConnected -> {
            Pair(
                "No compatible USB audio device connected.",
                MaterialTheme.colorScheme.error
            )
        }
    }

    val isUacOpen by com.flopster101.siliconplayer.usb.UacDriverCoordinator.isOpen.collectAsState()
    val isUacStreaming by com.flopster101.siliconplayer.usb.UacDriverCoordinator.isStreaming.collectAsState()
    val uacLastError by com.flopster101.siliconplayer.usb.UacDriverCoordinator.lastErrorMessage.collectAsState()
    val driverBadge = "Driver: ${driverMethod.displayName}"

    val coroutineScope = rememberCoroutineScope()

    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Bit-perfect USB audio",
        description = bitPerfectDesc,
        checked = state.bitPerfectUsbAudio && isBitPerfectSupported,
        enabled = isBitPerfectSupported,
        descriptionColor = bitPerfectColor,
        badgeText = driverBadge,
        errorText = if (driverMethod == BitPerfectDriverMethod.DirectUac) uacLastError else null,
        onCheckedChange = { targetEnabled ->
            actions.onBitPerfectUsbAudioChanged(targetEnabled)
            if (targetEnabled && driverMethod == BitPerfectDriverMethod.DirectUac) {
                val rawUsb = com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context)
                if (rawUsb != null) {
                    coroutineScope.launch {
                        val granted = com.flopster101.siliconplayer.usb.UacDriverCoordinator.requestPermission(context, rawUsb)
                        if (granted) {
                            com.flopster101.siliconplayer.usb.UacDriverCoordinator.open(context, rawUsb)
                            val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
                            val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                            val targetChannels = NativeBridge.getTrackChannelCount().takeIf { it > 0 } ?: 2
                            val ok = com.flopster101.siliconplayer.usb.UacDriverCoordinator.start(targetRate, targetBitDepth, targetChannels)
                            NativeBridge.setBitPerfectMode(ok)
                            if (!ok) {
                                actions.onBitPerfectUsbAudioChanged(false)
                            }
                        } else {
                            actions.onBitPerfectUsbAudioChanged(false)
                        }
                    }
                } else {
                    actions.onBitPerfectUsbAudioChanged(false)
                }
            } else if (!targetEnabled) {
                com.flopster101.siliconplayer.usb.UacDriverCoordinator.close()
                BitPerfectCoordinator.clearBitPerfectMixer(context)
                NativeBridge.setBitPerfectMode(false)
            }
        }
    )

    SettingsRowSpacer()
    SettingsValuePickerCard(
        title = "Bit-perfect driver mode",
        description = "Select whether to route via Android's platform audio HAL or the user-space direct UAC driver.",
        value = driverMethod.displayName,
        onClick = { showDriverMethodDialog = true }
    )

    if (showDriverMethodDialog) {
        SettingsSingleChoiceDialog(
            title = "Bit-perfect driver mode",
            selectedValue = driverMethod.storageValue,
            options = BitPerfectDriverMethod.entries.map {
                ChoiceDialogOption(
                    value = it.storageValue,
                    label = it.displayName
                )
            },
            onSelected = { selectedStorage ->
                val selected = BitPerfectDriverMethod.fromStorage(selectedStorage, context)
                driverMethod = selected
                prefs.edit().putString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, selected.storageValue).apply()
                showDriverMethodDialog = false
                if (state.bitPerfectUsbAudio && selected == BitPerfectDriverMethod.DirectUac) {
                    val rawUsb = com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context)
                    if (rawUsb != null) {
                        coroutineScope.launch {
                            val granted = com.flopster101.siliconplayer.usb.UacDriverCoordinator.requestPermission(context, rawUsb)
                            if (granted) {
                                com.flopster101.siliconplayer.usb.UacDriverCoordinator.open(context, rawUsb)
                                val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
                                val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                val targetChannels = NativeBridge.getTrackChannelCount().takeIf { it > 0 } ?: 2
                                com.flopster101.siliconplayer.usb.UacDriverCoordinator.start(targetRate, targetBitDepth, targetChannels)
                                NativeBridge.setBitPerfectMode(true)
                            }
                        }
                    }
                } else if (state.bitPerfectUsbAudio && selected == BitPerfectDriverMethod.Platform) {
                    com.flopster101.siliconplayer.usb.UacDriverCoordinator.close()
                    val usbAudioDevice = BitPerfectCoordinator.findConnectedUsbAudioDevice(context)
                    if (usbAudioDevice != null && BitPerfectCoordinator.isBitPerfectPlatformSupported()) {
                        val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
                        val targetChannels = NativeBridge.getTrackChannelCount().takeIf { it > 0 } ?: 2
                        BitPerfectCoordinator.setPreferredBitPerfectMixer(context, usbAudioDevice, targetRate, targetChannels)
                        NativeBridge.setBitPerfectMode(true)
                    }
                }
                PlaybackService.refreshSettings(context)
            },
            onDismiss = { showDriverMethodDialog = false }
        )
    }
}

@Composable
internal fun UrlCacheRouteContent(
    state: UrlCacheRouteState,
    actions: UrlCacheRouteActions
) {
    val urlCacheClearOnLaunch = state.urlCacheClearOnLaunch
    val onUrlCacheClearOnLaunchChanged = actions.onUrlCacheClearOnLaunchChanged
    val urlCacheMaxTracks = state.urlCacheMaxTracks
    val onUrlCacheMaxTracksChanged = actions.onUrlCacheMaxTracksChanged
    val urlCacheMaxBytes = state.urlCacheMaxBytes
    val onUrlCacheMaxBytesChanged = actions.onUrlCacheMaxBytesChanged
    val archiveCacheClearOnLaunch = state.archiveCacheClearOnLaunch
    val onArchiveCacheClearOnLaunchChanged = actions.onArchiveCacheClearOnLaunchChanged
    val archiveCacheMaxMounts = state.archiveCacheMaxMounts
    val onArchiveCacheMaxMountsChanged = actions.onArchiveCacheMaxMountsChanged
    val archiveCacheMaxBytes = state.archiveCacheMaxBytes
    val onArchiveCacheMaxBytesChanged = actions.onArchiveCacheMaxBytesChanged
    val archiveCacheMaxAgeDays = state.archiveCacheMaxAgeDays
    val onArchiveCacheMaxAgeDaysChanged = actions.onArchiveCacheMaxAgeDaysChanged
    val onOpenCacheManager = actions.onOpenCacheManager
    val onClearUrlCacheNow = actions.onClearUrlCacheNow
    val onClearArchiveCacheNow = actions.onClearArchiveCacheNow

    var showCacheTrackLimitDialog by remember { mutableStateOf(false) }
    var showCacheSizeLimitDialog by remember { mutableStateOf(false) }
    var showArchiveMountLimitDialog by remember { mutableStateOf(false) }
    var showArchiveSizeLimitDialog by remember { mutableStateOf(false) }
    var showArchiveAgeLimitDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirmDialog by remember { mutableStateOf(false) }
    var showClearArchiveCacheConfirmDialog by remember { mutableStateOf(false) }
    SettingsSectionLabel("File cache")
    PlayerSettingToggleCard(
        title = "Clear cache on app launch",
        description = "Delete all cached files each time the app starts.",
        checked = urlCacheClearOnLaunch,
        onCheckedChange = onUrlCacheClearOnLaunchChanged
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache song limit",
        description = "$urlCacheMaxTracks songs",
        icon = Icons.Default.MoreHoriz,
        onClick = { showCacheTrackLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Cache size limit",
        description = String.format(Locale.US, "%.2f GB", urlCacheMaxBytes / (1024.0 * 1024.0 * 1024.0)),
        icon = Icons.Default.MoreHoriz,
        onClick = { showCacheSizeLimitDialog = true }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Manage cached files",
        description = "Browse cached files, long-press multi-select, delete, and export.",
        icon = Icons.Default.MoreHoriz,
        onClick = onOpenCacheManager
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "Clear cache now",
        description = "Delete all currently cached files immediately.",
        icon = Icons.Default.Delete,
        onClick = { showClearCacheConfirmDialog = true }
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

    if (showCacheTrackLimitDialog) {
        SettingsTextInputDialog(
            title = "Cache song limit",
            fieldLabel = "Max songs",
            initialValue = urlCacheMaxTracks.toString(),
            placeholder = "100",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            sanitizer = { input -> input.filter { it.isDigit() }.take(6) },
            onDismiss = { showCacheTrackLimitDialog = false },
            onConfirm = { input ->
                val parsed = input.trim().toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onUrlCacheMaxTracksChanged(parsed)
                    true
                } else {
                    false
                }
            }
        )
    }

    if (showCacheSizeLimitDialog) {
        CacheSizeLimitDialog(
            initialBytes = urlCacheMaxBytes,
            onDismiss = { showCacheSizeLimitDialog = false },
            onConfirmBytes = onUrlCacheMaxBytesChanged
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

    if (showClearCacheConfirmDialog) {
        SettingsConfirmDialog(
            title = "Clear cached files now?",
            message = "This will remove all cached files, except the currently active one if it is being played.",
            confirmLabel = "Clear cache",
            onDismiss = { showClearCacheConfirmDialog = false },
            onConfirm = onClearUrlCacheNow
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
