package com.flopster101.siliconplayer.platform

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.isRoundScreenCompat
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets

class AndroidAppPreferences(val sharedPreferences: SharedPreferences) : AppPreferences {
    private val listenerMap = mutableMapOf<AppPreferences.OnChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getString(key: String, defValue: String?): String? = sharedPreferences.getString(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = sharedPreferences.getInt(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = sharedPreferences.getBoolean(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = sharedPreferences.getFloat(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = sharedPreferences.getLong(key, defValue)
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = sharedPreferences.getStringSet(key, defValues)

    override fun edit(): AppPreferences.Editor = Editor(sharedPreferences.edit())

    override fun addListener(listener: AppPreferences.OnChangeListener) {
        synchronized(listenerMap) {
            val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null) listener.onPreferenceChanged(this, key)
            }
            listenerMap[listener] = spListener
            sharedPreferences.registerOnSharedPreferenceChangeListener(spListener)
        }
    }

    override fun removeListener(listener: AppPreferences.OnChangeListener) {
        synchronized(listenerMap) {
            val spListener = listenerMap.remove(listener)
            if (spListener != null) {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(spListener)
            }
        }
    }

    private class Editor(private val editor: SharedPreferences.Editor) : AppPreferences.Editor {
        override fun putString(key: String, value: String?): AppPreferences.Editor {
            editor.putString(key, value)
            return this
        }
        override fun putInt(key: String, value: Int): AppPreferences.Editor {
            editor.putInt(key, value)
            return this
        }
        override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor {
            editor.putBoolean(key, value)
            return this
        }
        override fun putFloat(key: String, value: Float): AppPreferences.Editor {
            editor.putFloat(key, value)
            return this
        }
        override fun putLong(key: String, value: Long): AppPreferences.Editor {
            editor.putLong(key, value)
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): AppPreferences.Editor {
            editor.putStringSet(key, values)
            return this
        }
        override fun remove(key: String): AppPreferences.Editor {
            editor.remove(key)
            return this
        }
        override fun clear(): AppPreferences.Editor {
            editor.clear()
            return this
        }
        override fun apply() {
            editor.apply()
        }
        override fun commit(): Boolean = editor.commit()
    }
}

class AndroidPreferencesProvider(private val context: Context) : PreferencesProvider {
    override fun getPreferences(name: String): AppPreferences {
        return AndroidAppPreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}

class AndroidAudioRouteManager(private val context: Context) : AudioRouteManager {
    @Composable
    override fun rememberCurrentRoute(): AudioOutputRouteInfo {
        var routeInfo by remember { mutableStateOf(resolveCurrentAudioOutputRoute(context)) }

        DisposableEffect(context) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                object : AudioDeviceCallback() {
                    override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                        routeInfo = resolveCurrentAudioOutputRoute(context)
                    }

                    override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                        routeInfo = resolveCurrentAudioOutputRoute(context)
                    }
                }
            } else {
                null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    routeInfo = resolveCurrentAudioOutputRoute(context)
                }
            }
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val registered = runCatching {
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                true
            }.getOrElse {
                runCatching {
                    context.registerReceiver(receiver, filter)
                    true
                }.getOrDefault(false)
            }

            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                    audioManager?.unregisterAudioDeviceCallback(callback)
                }
                if (registered) {
                    runCatching { context.unregisterReceiver(receiver) }
                }
            }
        }

        return routeInfo
    }

    override fun openAudioOutputSwitcher() {
        openAudioOutputSwitcher(context)
    }

    override fun formatUsbAudioName(rawName: String): String {
        return formatUsbAudioPillName(context, rawName)
    }
}

internal fun resolveCurrentAudioOutputRoute(context: Context): AudioOutputRouteInfo {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val bluetoothDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.type == AudioDeviceInfo.TYPE_HEARING_AID)
        }
        if (bluetoothDevice != null) {
            val name = bluetoothDevice.productName?.toString()?.trim()
            val displayName = if (!name.isNullOrBlank()) name else "Bluetooth"
            return AudioOutputRouteInfo(AudioOutputRouteType.Bluetooth, displayName)
        }

        val usbDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usbDevice != null) {
            val rawName = usbDevice.productName?.toString()?.trim()
            val name = rawName
                ?.removePrefix("USB-Audio - ")
                ?.removePrefix("USB-Audio-")
                ?.removePrefix("USB Audio - ")
                ?.trim()
            val displayName = if (!name.isNullOrBlank()) name else "USB Audio"
            return AudioOutputRouteInfo(AudioOutputRouteType.Usb, displayName)
        }

        val wiredDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            device.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            device.type == AudioDeviceInfo.TYPE_AUX_LINE ||
            device.type == AudioDeviceInfo.TYPE_HDMI ||
            device.type == AudioDeviceInfo.TYPE_HDMI_ARC
        }
        if (wiredDevice != null) {
            return AudioOutputRouteInfo(AudioOutputRouteType.Headphones, "Wired Headset")
        }

        return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")
    } else {
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn ->
                AudioOutputRouteInfo(AudioOutputRouteType.Bluetooth, "Bluetooth")
            audioManager.isWiredHeadsetOn ->
                AudioOutputRouteInfo(AudioOutputRouteType.Headphones, "Wired Headset")
            else ->
                AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")
        }
    }
}

internal fun openAudioOutputSwitcher(context: Context) {
    runCatching {
        val panelIntent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
            putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(panelIntent)
    }.onFailure {
        runCatching {
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(btIntent)
        }.onFailure {
            runCatching {
                val soundIntent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(soundIntent)
            }
        }
    }
}

private fun isGenericUsbAudioProductName(name: String?): Boolean {
    if (name.isNullOrBlank()) return true
    val lower = name.trim().lowercase()
    val genericExact = setOf(
        "usb audio",
        "usb-audio",
        "usb audio device",
        "usb-audio device",
        "usb composite device",
        "usb advanced audio device",
        "usb dac",
        "usb audio dac",
        "usb sound device",
        "usb pnp sound device",
        "generic usb audio",
        "audio device",
        "composite device",
        "android audio",
        "android usb audio"
    )
    if (lower in genericExact) return true
    if (lower.startsWith("linux") && (lower.contains("gadget") || lower.contains("uac") || lower.contains("audio"))) return true
    if (lower.contains("uac1_gadget") || lower.contains("uac2_gadget")) return true
    return false
}

private fun getUsbAudioProtocolVersion(device: UsbDevice): String {
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
            if (iface.interfaceProtocol >= 0x20) {
                return if (iface.interfaceProtocol == 0x30) "USB Audio 3.0" else "USB Audio 2.0"
            }
        }
    }
    return "USB Audio 1.0"
}

private fun formatUsbAudioPillName(context: Context, rawName: String): String {
    if (!isGenericUsbAudioProductName(rawName)) {
        return rawName
    }

    val rawUsb = com.flopster101.siliconplayer.usb.UacDriverCoordinator.findUsbAudioDevice(context)
    val uacVersion = if (rawUsb != null) {
        getUsbAudioProtocolVersion(rawUsb)
    } else {
        "USB Audio 1.0"
    }

    val manufacturer = rawUsb?.manufacturerName?.trim()?.takeIf {
        it.isNotBlank() &&
        !it.equals("Linux Foundation", ignoreCase = true) &&
        !it.equals("Linux", ignoreCase = true) &&
        !it.equals("Android", ignoreCase = true) &&
        !it.equals("Generic", ignoreCase = true)
    }

    return if (manufacturer != null) {
        "$manufacturer $uacVersion"
    } else {
        uacVersion
    }
}

@Composable
fun ProvideAndroidPlatformAdapters(
    context: Context = LocalContext.current,
    content: @Composable () -> Unit
) {
    val prefs = remember(context) {
        AndroidAppPreferences(context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE))
    }
    val prefsProvider = remember(context) { AndroidPreferencesProvider(context) }
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val audioRouteManager = remember(context) { AndroidAudioRouteManager(context) }
    val toastHandler = remember(context) {
        ToastHandler { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }
    val configuration = LocalConfiguration.current
    val windowSizeInfo = remember(configuration) {
        WindowSizeInfo(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp,
            isRound = configuration.isRoundScreenCompat
        )
    }
    val projectMOptionsProvider = remember(context, prefs) {
        object : ProjectMOptionsProvider {
            override fun getEnabledPresetLabels(): Map<String, String> {
                return ProjectMPresetSets.enabledSets(context, prefs.sharedPreferences)
                    .associate { it.id to it.label }
            }
        }
    }

    val appVersionInfo = remember {
        AppVersionInfo(
            versionName = com.flopster101.siliconplayer.BuildConfig.VERSION_NAME,
            abiOrArch = Build.SUPPORTED_ABIS.firstOrNull()?.replace("-", "") ?: "unknown",
            gitSha = com.flopster101.siliconplayer.BuildConfig.GIT_SHA
        )
    }

    val settingsPlatformContent = remember {
        object : SettingsPlatformContent {
            @Composable
            override fun LibrarySettingsContent(onOpenScanner: () -> Unit) {
                com.flopster101.siliconplayer.settings.routes.LibrarySettingsRouteContent(
                    onOpenScanner = onOpenScanner
                )
            }

            @Composable
            override fun LibraryScannerContent() {
                com.flopster101.siliconplayer.settings.routes.LibraryScannerRouteContent()
            }

            @Composable
            override fun PlatformAudioOptions(
                bitPerfectUsbAudio: Boolean,
                onBitPerfectUsbAudioChanged: (Boolean) -> Unit
            ) {
                com.flopster101.siliconplayer.settings.AndroidBitPerfectSettingsCard(
                    bitPerfectUsbAudio = bitPerfectUsbAudio,
                    onBitPerfectUsbAudioChanged = onBitPerfectUsbAudioChanged
                )
            }

            @Composable
            override fun PlatformDolbyDetailContent() {
                com.flopster101.siliconplayer.settings.AndroidPlatformDolbyDetailContent()
            }

            @Composable
            override fun ProjectMRouteContent(onOpenPresetPacks: () -> Unit) {
                com.flopster101.siliconplayer.VisualizationAdvancedProjectMRouteContent(
                    onOpenPresetPacks = onOpenPresetPacks
                )
            }

            @Composable
            override fun ProjectMSetsRouteContent() {
                com.flopster101.siliconplayer.VisualizationProjectMSetsRouteContent()
            }
        }
    }

    CompositionLocalProvider(
        LocalAppPreferences provides prefs,
        LocalPreferencesProvider provides prefsProvider,
        LocalIsWatchDevice provides isWatch,
        LocalAudioRouteManager provides audioRouteManager,
        LocalToastHandler provides toastHandler,
        LocalPlatformBackHandler provides { enabled, onBack ->
            androidx.activity.compose.BackHandler(enabled, onBack)
        },
        LocalWindowSizeInfo provides windowSizeInfo,
        LocalProjectMOptionsProvider provides projectMOptionsProvider,
        LocalAppVersionInfo provides appVersionInfo,
        LocalSettingsPlatformContent provides settingsPlatformContent,
        content = content
    )
}


