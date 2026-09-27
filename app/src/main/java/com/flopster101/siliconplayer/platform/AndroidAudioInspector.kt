package com.flopster101.siliconplayer.platform

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.BitPerfectCoordinator
import com.flopster101.siliconplayer.BitPerfectDriverMethod
import com.flopster101.siliconplayer.BitPerfectSupportStatus
import com.flopster101.siliconplayer.ChoiceDialogOption
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.SettingsSingleChoiceDialog
import com.flopster101.siliconplayer.supportsLiveSampleRateChange
import com.flopster101.siliconplayer.ui.dialogs.DialogSectionLabel
import com.flopster101.siliconplayer.ui.dialogs.formatSampleRateForInspector
import com.flopster101.siliconplayer.usb.DirectUacVolumeMode
import com.flopster101.siliconplayer.usb.UacDriverCoordinator
import kotlinx.coroutines.launch

/** USB/bit-perfect inspector detail; Android-only, slotted into the shared dialog. */
@Composable
internal fun AndroidBitPerfectSection(params: BitPerfectSectionParams) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    val driverMethod = remember(prefs) {
        BitPerfectDriverMethod.fromStorage(prefs.getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null))
    }
    val bitPerfectSupportStatus = remember(driverMethod) {
        BitPerfectCoordinator.checkBitPerfectSupport(context, driverMethod)
    }
    val isBitPerfectSupported = bitPerfectSupportStatus == BitPerfectSupportStatus.Supported
    val isUacDriverOpen by UacDriverCoordinator.isOpen.collectAsState()
    val isUacStreaming by UacDriverCoordinator.isStreaming.collectAsState()
    val uacLastError by UacDriverCoordinator.lastErrorMessage.collectAsState()
    val uacDiagnostics = remember(isUacStreaming, isUacDriverOpen) {
        if (isUacDriverOpen) UacDriverCoordinator.getDiagnostics() else null
    }
    val uacVolumeMode by UacDriverCoordinator.volumeMode.collectAsState()
    val uacManualVolume by UacDriverCoordinator.manualVolume.collectAsState()
    var showVolumeModeDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var showRestartConfirmDialog by remember { mutableStateOf(false) }
    var pendingBitPerfectState by remember { mutableStateOf(false) }
    var showReplugNoticeDialog by remember { mutableStateOf(false) }

    val onToggleBitPerfect: (Boolean) -> Unit = { targetEnabled ->
        val canLiveChange = supportsLiveSampleRateChange(params.playbackCapabilitiesFlags)
        if (params.isPlaying && !canLiveChange) {
            pendingBitPerfectState = targetEnabled
            showRestartConfirmDialog = true
        } else {
            params.onBitPerfectToggled(targetEnabled)
            if (targetEnabled) {
                if (driverMethod == BitPerfectDriverMethod.DirectUac) {
                    val rawUsb = UacDriverCoordinator.findUsbAudioDevice(context)
                    if (rawUsb != null) {
                        coroutineScope.launch {
                            val granted = UacDriverCoordinator.requestPermission(context, rawUsb)
                            if (granted) {
                                UacDriverCoordinator.open(context, rawUsb)
                                if (params.isPlaying) {
                                    val targetRate = params.effectiveDecoderRateHz
                                    val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                    val ok = UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                    NativeBridge.setBitPerfectMode(ok)
                                    if (!ok) {
                                        params.onBitPerfectToggled(false)
                                    }
                                } else {
                                    NativeBridge.setBitPerfectMode(true)
                                }
                            } else {
                                params.onBitPerfectToggled(false)
                            }
                        }
                    } else {
                        params.onBitPerfectToggled(false)
                    }
                } else if (BitPerfectCoordinator.isBitPerfectPlatformSupported()) {
                    val usbAudioDevice = BitPerfectCoordinator.findConnectedUsbAudioDevice(context)
                    if (usbAudioDevice != null) {
                        if (params.isPlaying) {
                            val targetRate = params.effectiveDecoderRateHz
                            BitPerfectCoordinator.setPreferredBitPerfectMixer(context, usbAudioDevice, targetRate, params.effectiveChannels)
                        }
                        NativeBridge.setBitPerfectMode(true)
                    } else {
                        BitPerfectCoordinator.clearBitPerfectMixer(context)
                        NativeBridge.setBitPerfectMode(false)
                        params.onBitPerfectToggled(false)
                    }
                }
            } else {
                UacDriverCoordinator.close()
                BitPerfectCoordinator.clearBitPerfectMixer(context)
                NativeBridge.setBitPerfectMode(false)
                showReplugNoticeDialog = true
            }
        }
    }

    val supportedRates = remember(context) {
        BitPerfectCoordinator.getUsbDeviceSupportedSampleRates(context)
    }
    val isUac1 = remember(context) {
        BitPerfectCoordinator.isConnectedUsbAudioUac1(context)
    }
    val uacLabel = if (isUac1) "UAC 1.0" else "UAC 2.0"

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DialogSectionLabel(
            text = "Bit-perfect routing",
            modifier = Modifier.padding(start = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bit-perfect USB audio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val (statusText, statusColor) = when (bitPerfectSupportStatus) {
                            BitPerfectSupportStatus.Supported -> Pair("Bypasses Android audio mixer for bit-perfect output.", MaterialTheme.colorScheme.onSurfaceVariant)
                            BitPerfectSupportStatus.UnsupportedAudioHal -> Pair("Platform bit-perfect API is not supported by this device's audio HAL.", MaterialTheme.colorScheme.error)
                            BitPerfectSupportStatus.UnsupportedApiLevel -> Pair("Platform bit-perfect USB routing requires Android 14 or higher.", MaterialTheme.colorScheme.error)
                            BitPerfectSupportStatus.NoUsbDeviceConnected -> Pair("No compatible USB audio device connected.", MaterialTheme.colorScheme.error)
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        val driverBadge = "Driver: ${driverMethod.displayName}"
                        Text(
                            text = driverBadge,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBitPerfectSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (driverMethod == BitPerfectDriverMethod.DirectUac && uacLastError != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Error: $uacLastError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = params.bitPerfectEnabled && isBitPerfectSupported,
                        onCheckedChange = onToggleBitPerfect,
                        enabled = isBitPerfectSupported
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Supported Rates ($uacLabel)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        supportedRates.forEach { rate ->
                            val isActive = rate == params.effectiveOutputRateHz
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                )
                            ) {
                                Text(
                                    text = formatSampleRateForInspector(rate),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFeatureSettings = "tnum"
                                    ),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                if (params.bitPerfectEnabled && isBitPerfectSupported && driverMethod == BitPerfectDriverMethod.DirectUac) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DAC Volume Scaling",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = when (uacVolumeMode) {
                                        DirectUacVolumeMode.None -> "None (0 dBFS unity gain)"
                                        DirectUacVolumeMode.System -> "Match Android system volume"
                                        DirectUacVolumeMode.Manual -> "Manual slider"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = { showVolumeModeDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = uacVolumeMode.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uacVolumeMode == DirectUacVolumeMode.Manual) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = uacManualVolume,
                                    onValueChange = { newVol ->
                                        UacDriverCoordinator.setManualVolume(context, newVol)
                                    },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${(uacManualVolume * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRestartConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRestartConfirmDialog = false },
            title = {
                Text(
                    text = "Restart playback?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Changing bit-perfect audio for ${params.effectiveDecoderName} won't take effect until playback is restarted. Would you like to restart playback now?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestartConfirmDialog = false
                        if (pendingBitPerfectState) {
                            if (driverMethod == BitPerfectDriverMethod.DirectUac) {
                                val rawUsb = UacDriverCoordinator.findUsbAudioDevice(context)
                                if (rawUsb != null) {
                                    coroutineScope.launch {
                                        val granted = UacDriverCoordinator.requestPermission(context, rawUsb)
                                        if (granted) {
                                            UacDriverCoordinator.open(context, rawUsb)
                                            params.onBitPerfectToggled(true)
                                            val targetRate = params.effectiveDecoderRateHz
                                            val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
                                            val ok = UacDriverCoordinator.start(targetRate, targetBitDepth, 2)
                                            NativeBridge.setBitPerfectMode(ok)
                                            if (ok) {
                                                params.onRestartTrack()
                                            } else {
                                                params.onBitPerfectToggled(false)
                                            }
                                        } else {
                                            params.onBitPerfectToggled(false)
                                        }
                                    }
                                } else {
                                    params.onBitPerfectToggled(false)
                                }
                            } else {
                                params.onBitPerfectToggled(true)
                                val usbDevice = BitPerfectCoordinator.findConnectedUsbAudioDevice(context)
                                if (usbDevice != null) {
                                    BitPerfectCoordinator.setPreferredBitPerfectMixer(context, usbDevice, params.effectiveDecoderRateHz, params.effectiveChannels)
                                    NativeBridge.setBitPerfectMode(true)
                                }
                                params.onRestartTrack()
                            }
                        } else {
                            UacDriverCoordinator.close()
                            BitPerfectCoordinator.clearBitPerfectMixer(context)
                            NativeBridge.setBitPerfectMode(false)
                            params.onBitPerfectToggled(false)
                            showReplugNoticeDialog = true
                            params.onRestartTrack()
                        }
                    }
                ) {
                    Text("Restart")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestartConfirmDialog = false
                        params.onBitPerfectToggled(pendingBitPerfectState)
                        if (!pendingBitPerfectState) {
                            UacDriverCoordinator.close()
                            BitPerfectCoordinator.clearBitPerfectMixer(context)
                            NativeBridge.setBitPerfectMode(false)
                            showReplugNoticeDialog = true
                        }
                    }
                ) {
                    Text("Keep playing")
                }
            }
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
                    Text("Got it")
                }
            }
        )
    }

    if (showVolumeModeDialog) {
        SettingsSingleChoiceDialog(
            title = "DAC Volume Scaling",
            selectedValue = uacVolumeMode.storageValue,
            options = DirectUacVolumeMode.entries.map {
                ChoiceDialogOption(
                    value = it.storageValue,
                    label = it.displayName
                )
            },
            onSelected = { selectedStorage ->
                val selected = DirectUacVolumeMode.fromStorage(selectedStorage)
                UacDriverCoordinator.setVolumeMode(context, selected)
                showVolumeModeDialog = false
            },
            onDismiss = { showVolumeModeDialog = false }
        )
    }
}

internal object AndroidAudioInspectorSupport : AudioInspectorSupport {
    override val inactiveBackendLabel: String = "AAudio"
    override val fileArtworkIcon: @Composable (DecoderArtworkHint?) -> Unit = @Composable { hint ->
        when (hint) {
            DecoderArtworkHint.TrackedFile -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file_tracked),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            DecoderArtworkHint.GameFile -> {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file_game),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
    override val decoderNodeIcon: @Composable () -> Unit = @Composable {
        Icon(
            painter = painterResource(id = R.drawable.ic_placeholder_tracker_chip),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.tertiary
        )
    }
    override val bitPerfectSection: @Composable (BitPerfectSectionParams) -> Unit = @Composable { sectionParams -> AndroidBitPerfectSection(sectionParams) }
    @Composable
    override fun rememberBitPerfectActive(bitPerfectEnabled: Boolean, routeIsUsb: Boolean): Boolean {
        val context = LocalContext.current
        val driverMethod = remember(context) {
            BitPerfectDriverMethod.fromStorage(
                context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null)
            )
        }
        val supportStatus = remember(driverMethod) {
            BitPerfectCoordinator.checkBitPerfectSupport(context, driverMethod)
        }
        val isUacDriverOpen by UacDriverCoordinator.isOpen.collectAsState()
        return remember(bitPerfectEnabled, routeIsUsb, isUacDriverOpen, supportStatus) {
            routeIsUsb && bitPerfectEnabled && (isUacDriverOpen ||
                BitPerfectCoordinator.isBitPerfectActive(context) ||
                supportStatus == BitPerfectSupportStatus.Supported)
        }
    }
    @Composable
    override fun rememberUsbSinkFormatText(): String? {
        val isUacStreaming by UacDriverCoordinator.isStreaming.collectAsState()
        val isUacDriverOpen by UacDriverCoordinator.isOpen.collectAsState()
        val diagnostics = remember(isUacStreaming, isUacDriverOpen) {
            if (isUacDriverOpen) UacDriverCoordinator.getDiagnostics() else null
        }
        if (!isUacStreaming) return null
        val bits = diagnostics?.bitsPerSample?.takeIf { it > 0 } ?: 16
        return "$bits-bit PCM"
    }
}
