package com.flopster101.siliconplayer.settings

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.BitPerfectCoordinator
import com.flopster101.siliconplayer.BitPerfectDriverMethod
import com.flopster101.siliconplayer.BitPerfectSupportStatus
import com.flopster101.siliconplayer.ChoiceDialogOption
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PlaybackService
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.SettingsRowSpacer
import com.flopster101.siliconplayer.SettingsSingleChoiceDialog
import com.flopster101.siliconplayer.SettingsValuePickerCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AndroidBitPerfectSettingsCard(
    bitPerfectUsbAudio: Boolean,
    onBitPerfectUsbAudioChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE) }
    var driverMethod by remember {
        mutableStateOf(BitPerfectDriverMethod.fromStorage(prefs.getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null)))
    }
    var showDriverMethodDialog by remember { mutableStateOf(false) }

    val bitPerfectSupportStatus = remember(driverMethod) {
        BitPerfectCoordinator.checkBitPerfectSupport(context, driverMethod)
    }
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

    var showReplugNoticeDialog by remember { mutableStateOf(false) }

    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Bit-perfect USB audio",
        description = bitPerfectDesc,
        checked = bitPerfectUsbAudio,
        descriptionColor = bitPerfectColor,
        badgeText = driverBadge,
        errorText = if (driverMethod == BitPerfectDriverMethod.DirectUac) uacLastError else null,
        onCheckedChange = { targetEnabled ->
            onBitPerfectUsbAudioChanged(targetEnabled)
            if (targetEnabled && driverMethod == BitPerfectDriverMethod.DirectUac) {
                val rawUsb = com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context)
                if (rawUsb != null) {
                    coroutineScope.launch {
                        val granted = com.flopster101.siliconplayer.usb.UacDriverCoordinator.requestPermission(context, rawUsb)
                        if (granted) {
                            // USB open/start block: off Main.
                            val ok = withContext(Dispatchers.IO) {
                                com.flopster101.siliconplayer.usb.UacDriverCoordinator.open(context, rawUsb)
                                val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
                                val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                val started = com.flopster101.siliconplayer.usb.UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                NativeBridge.setBitPerfectMode(started)
                                started
                            }
                            if (!ok) {
                                onBitPerfectUsbAudioChanged(false)
                            }
                        } else {
                            onBitPerfectUsbAudioChanged(false)
                        }
                    }
                }
            } else if (!targetEnabled) {
                // USB close blocks: off Main; the disable rebuild is async.
                coroutineScope.launch {
                    withContext(Dispatchers.IO) {
                        com.flopster101.siliconplayer.usb.UacDriverCoordinator.close()
                        NativeBridge.setBitPerfectMode(false)
                    }
                    BitPerfectCoordinator.clearBitPerfectMixer(context)
                    showReplugNoticeDialog = true
                }
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

    var showVolumeModeDialog by remember { mutableStateOf(false) }
    val uacVolumeMode by com.flopster101.siliconplayer.usb.UacDriverCoordinator.volumeMode.collectAsState()
    var uacSettlePilotTone by remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.UAC_SETTLE_PILOT_TONE, false))
    }

    if (driverMethod == BitPerfectDriverMethod.DirectUac) {
        SettingsRowSpacer()
        PlayerSettingToggleCard(
            title = "USB DAC settle pilot tone",
            description = "Emit a near-silent tone for ~60 ms on fresh USB starts so DACs that lock channel sync on the first signal stay aligned. Off by default.",
            checked = uacSettlePilotTone,
            onCheckedChange = { enabled ->
                uacSettlePilotTone = enabled
                prefs.edit().putBoolean(AppPreferenceKeys.UAC_SETTLE_PILOT_TONE, enabled).apply()
                NativeBridge.setUacSettlePilotTone(enabled)
            }
        )
        SettingsRowSpacer()
        SettingsValuePickerCard(
            title = "Direct UAC volume scaling",
            description = "Choose how output volume sent to the USB DAC is scaled.",
            value = uacVolumeMode.displayName,
            onClick = { showVolumeModeDialog = true }
        )
    }

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
                if (bitPerfectUsbAudio && selected == BitPerfectDriverMethod.DirectUac) {
                    val rawUsb = com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context)
                    if (rawUsb != null) {
                        coroutineScope.launch {
                            val granted = com.flopster101.siliconplayer.usb.UacDriverCoordinator.requestPermission(context, rawUsb)
                            if (granted) {
                                // USB open and the device rebuild block: off Main.
                                withContext(Dispatchers.IO) {
                                    com.flopster101.siliconplayer.usb.UacDriverCoordinator.open(context, rawUsb)
                                    val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
                                    val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                    com.flopster101.siliconplayer.usb.UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                    NativeBridge.setBitPerfectMode(true)
                                }
                            }
                        }
                    }
                } else if (bitPerfectUsbAudio && selected == BitPerfectDriverMethod.Platform) {
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

    if (showVolumeModeDialog) {
        SettingsSingleChoiceDialog(
            title = "Direct UAC volume scaling",
            selectedValue = uacVolumeMode.storageValue,
            options = com.flopster101.siliconplayer.usb.DirectUacVolumeMode.entries.map {
                ChoiceDialogOption(
                    value = it.storageValue,
                    label = it.displayName
                )
            },
            onSelected = { selectedStorage ->
                val selected = com.flopster101.siliconplayer.usb.DirectUacVolumeMode.fromStorage(selectedStorage)
                com.flopster101.siliconplayer.usb.UacDriverCoordinator.setVolumeMode(context, selected)
                showVolumeModeDialog = false
            },
            onDismiss = { showVolumeModeDialog = false }
        )
    }

    if (showReplugNoticeDialog) {
        AlertDialog(
            onDismissRequest = { showReplugNoticeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Device reconnect notice",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Disabling direct USB audio while a device is active releases exclusive hardware control. To route audio through Android's standard audio mixer again, you may need to unplug and reconnect your USB audio device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = { showReplugNoticeDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
