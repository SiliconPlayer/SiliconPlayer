package com.flopster101.siliconplayer.ui.dialogs

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.isWatchDevice
import com.flopster101.siliconplayer.ui.screens.AudioOutputRouteType
import com.flopster101.siliconplayer.ui.screens.openAudioOutputSwitcher
import com.flopster101.siliconplayer.ui.screens.resolveCurrentAudioOutputRoute

internal data class AudioOutputDeviceItem(
    val id: String,
    val name: String,
    val type: AudioOutputRouteType,
    val description: String,
    val isSelected: Boolean,
    val audioDeviceInfo: AudioDeviceInfo? = null
)

internal fun getConnectedAudioOutputDevices(context: Context): List<AudioOutputDeviceItem> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val activeRoute = resolveCurrentAudioOutputRoute(context)
    val result = mutableListOf<AudioOutputDeviceItem>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioManager != null) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // 1. Built-in Speaker (Always present as a supported hardware sink)
        val speakerDevice = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val isSpeakerActive = activeRoute.type == AudioOutputRouteType.Speaker
        result.add(
            AudioOutputDeviceItem(
                id = "speaker_${speakerDevice?.id ?: 0}",
                name = "Speaker",
                type = AudioOutputRouteType.Speaker,
                description = "Internal speaker",
                isSelected = isSpeakerActive,
                audioDeviceInfo = speakerDevice
            )
        )

        // 2. 3.5mm / Analog / AUX / HDMI Wired Headsets
        val wiredDevices = devices.filter { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            device.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            device.type == AudioDeviceInfo.TYPE_AUX_LINE ||
            device.type == AudioDeviceInfo.TYPE_HDMI ||
            device.type == AudioDeviceInfo.TYPE_HDMI_ARC
        }
        for (device in wiredDevices) {
            val description = when (device.type) {
                AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI Audio"
                AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Output"
                else -> "3.5mm audio jack"
            }
            val isWiredActive = activeRoute.type == AudioOutputRouteType.Headphones &&
                activeRoute.name == "Wired Headset"
            result.add(
                AudioOutputDeviceItem(
                    id = "wired_${device.id}",
                    name = "Wired Headset",
                    type = AudioOutputRouteType.Headphones,
                    description = description,
                    isSelected = isWiredActive,
                    audioDeviceInfo = device
                )
            )
        }

        // 3. USB Audio Devices (Deduplicated so multiple profiles of the same DAC appear once)
        val usbDevices = devices.filter { device ->
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        val deduplicatedUsbDevices = usbDevices
            .groupBy { device ->
                val name = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "USB Audio"
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    device.address?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                address ?: name
            }
            .values
            .map { it.first() }

        for (device in deduplicatedUsbDevices) {
            val productName = device.productName?.toString()?.trim()
            val description = if (!productName.isNullOrBlank() && !productName.equals("Android", ignoreCase = true) && !productName.equals("USB Audio", ignoreCase = true)) {
                productName
            } else {
                "USB audio output"
            }
            val isUsbActive = activeRoute.type == AudioOutputRouteType.Usb
            result.add(
                AudioOutputDeviceItem(
                    id = "usb_${device.id}",
                    name = "USB Audio",
                    type = AudioOutputRouteType.Usb,
                    description = description,
                    isSelected = isUsbActive,
                    audioDeviceInfo = device
                )
            )
        }

        // 4. Connected Bluetooth Devices (Deduplicated so multi-profile headphones like A2DP + SCO only appear once)
        val bluetoothDevices = devices.filter { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.type == AudioDeviceInfo.TYPE_HEARING_AID)
        }

        fun bluetoothDevicePriority(type: Int): Int {
            return when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 1
                26 /* TYPE_BLE_HEADSET */ -> 2
                27 /* TYPE_BLE_SPEAKER */ -> 3
                23 /* TYPE_HEARING_AID */ -> 4
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 5
                else -> 6
            }
        }

        val deduplicatedBluetoothDevices = bluetoothDevices
            .groupBy { device ->
                val name = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    device.address?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                address ?: name
            }
            .values
            .map { group ->
                group.minByOrNull { bluetoothDevicePriority(it.type) } ?: group.first()
            }

        for (device in deduplicatedBluetoothDevices) {
            val btName = device.productName?.toString()?.trim()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            val isBtActive = activeRoute.type == AudioOutputRouteType.Bluetooth &&
                (activeRoute.name == btName || activeRoute.name == "Bluetooth")
            result.add(
                AudioOutputDeviceItem(
                    id = "bt_${device.id}",
                    name = btName,
                    type = AudioOutputRouteType.Bluetooth,
                    description = "Bluetooth audio",
                    isSelected = isBtActive,
                    audioDeviceInfo = device
                )
            )
        }
    } else {
        val isBt = activeRoute.type == AudioOutputRouteType.Bluetooth
        val isUsb = activeRoute.type == AudioOutputRouteType.Usb
        val isWired = activeRoute.type == AudioOutputRouteType.Headphones
        result.add(
            AudioOutputDeviceItem(
                id = "speaker_0",
                name = "Speaker",
                type = AudioOutputRouteType.Speaker,
                description = "Internal speaker",
                isSelected = !isBt && !isUsb && !isWired
            )
        )
        if (isWired) {
            result.add(
                AudioOutputDeviceItem(
                    id = "wired_0",
                    name = "Wired Headset",
                    type = AudioOutputRouteType.Headphones,
                    description = "Wired audio connection",
                    isSelected = true
                )
            )
        }
        if (isUsb) {
            result.add(
                AudioOutputDeviceItem(
                    id = "usb_0",
                    name = "USB Audio",
                    type = AudioOutputRouteType.Usb,
                    description = "USB audio connection",
                    isSelected = true
                )
            )
        }
        if (isBt) {
            result.add(
                AudioOutputDeviceItem(
                    id = "bt_0",
                    name = activeRoute.name,
                    type = AudioOutputRouteType.Bluetooth,
                    description = "Bluetooth audio",
                    isSelected = true
                )
            )
        }
    }

    return result
}

internal fun selectAudioOutputDevice(context: Context, item: AudioOutputDeviceItem) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (item.type == AudioOutputRouteType.Speaker) {
            val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null) {
                audioManager.setCommunicationDevice(speaker)
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else if (item.audioDeviceInfo != null) {
            val target = audioManager.availableCommunicationDevices.firstOrNull {
                it.id == item.audioDeviceInfo.id || it.type == item.audioDeviceInfo.type
            } ?: item.audioDeviceInfo
            audioManager.setCommunicationDevice(target)
        }
    }
}

@Composable
internal fun AudioOutputDeviceDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var outputDevices by remember { mutableStateOf(getConnectedAudioOutputDevices(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    outputDevices = getConnectedAudioOutputDevices(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    outputDevices = getConnectedAudioOutputDevices(context)
                }
            }
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager?.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Audio output",
            onDismissRequest = onDismiss
        ) {
            outputDevices.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (item.isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable {
                            selectAudioOutputDevice(context, item)
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = when (item.type) {
                            AudioOutputRouteType.Bluetooth -> Icons.Default.Bluetooth
                            AudioOutputRouteType.Headphones -> Icons.Default.Headphones
                            AudioOutputRouteType.Usb -> Icons.Default.Usb
                            AudioOutputRouteType.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = if (item.isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (item.isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (item.isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = {
                    onDismiss()
                    openAudioOutputSwitcher(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("System output settings")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Audio output",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Connected output devices:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    outputDevices.forEach { item ->
                        Surface(
                            onClick = {
                                selectAudioOutputDevice(context, item)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (item.isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (item.isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.isSelected) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHighest
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (item.type) {
                                            AudioOutputRouteType.Bluetooth -> Icons.Default.Bluetooth
                                            AudioOutputRouteType.Headphones -> Icons.Default.Headphones
                                            AudioOutputRouteType.Usb -> Icons.Default.Usb
                                            AudioOutputRouteType.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                                        },
                                        contentDescription = null,
                                        tint = if (item.isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (item.isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        openAudioOutputSwitcher(context)
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("System output settings")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}
