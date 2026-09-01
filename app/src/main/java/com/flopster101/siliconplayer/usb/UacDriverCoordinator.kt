package com.flopster101.siliconplayer.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.BitPerfectDriverMethod
import com.flopster101.siliconplayer.NativeBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.ByteBuffer
import kotlin.coroutines.resume

enum class UacStartError(val code: Int) {
    Ok(0),
    NoDevice(1),
    NoMatchingAlt(2),
    ClaimInterfaceFailed(3),
    SetAltFailed(4),
    SetSampleRateFailed(5),
    IsoPumpAllocFailed(6),
    IsoPumpSubmitFailed(7);

    companion object {
        fun fromCode(code: Int): UacStartError = entries.firstOrNull { it.code == code } ?: Ok
    }
}

data class UacClockRange(
    val clockId: Int,
    val minHz: Int,
    val maxHz: Int,
    val resHz: Int
)

data class UacDiagnostics(
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val channels: Int,
    val interfaceNumber: Int,
    val altSetting: Int,
    val endpointAddress: Int,
    val maxPacketSize: Int,
    val bInterval: Int,
    val uacVersion: Int,
    val clockSourceId: Int,
    val feedbackEndpointAddress: Int,
    val isHighSpeed: Boolean,
    val bytesPerSample: Int
)

object UacDriverCoordinator {
    private const val TAG = "UacDriverCoordinator"
    private const val ACTION_USB_PERMISSION = "com.flopster101.siliconplayer.USB_PERMISSION"

    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _activeDevice = MutableStateFlow<UsbDevice?>(null)
    val activeDevice: StateFlow<UsbDevice?> = _activeDevice.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _volumeMode = MutableStateFlow(DirectUacVolumeMode.System)
    val volumeMode: StateFlow<DirectUacVolumeMode> = _volumeMode.asStateFlow()

    private val _manualVolume = MutableStateFlow(1.0f)
    val manualVolume: StateFlow<Float> = _manualVolume.asStateFlow()

    private val _effectiveVolumeScale = MutableStateFlow(1.0f)
    val effectiveVolumeScale: StateFlow<Float> = _effectiveVolumeScale.asStateFlow()

    private var volumeReceiverRegistered = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            context?.let { syncVolume(it) }
        }
    }

    private var activeConnection: UsbDeviceConnection? = null

    init {
        UacDriverNative.nativeInit()
    }

    fun findUsbAudioDevice(context: Context): UsbDevice? {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        return usbManager.deviceList.values.firstOrNull { device ->
            isUsbAudioDevice(device)
        }
    }

    fun isUsbAudioDevice(device: UsbDevice): Boolean {
        if (device.deviceClass == UsbConstants.USB_CLASS_AUDIO) return true
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) return true
        }
        return false
    }

    fun hasPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        return usbManager.hasPermission(device)
    }

    suspend fun requestPermission(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        if (usbManager.hasPermission(device)) return true

        return suspendCancellableCoroutine { cont ->
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    runCatching { ctx.unregisterReceiver(this) }
                    if (cont.isActive) cont.resume(granted)
                }
            }
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            cont.invokeOnCancellation {
                runCatching { context.unregisterReceiver(receiver) }
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pi = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION).setPackage(context.packageName), flags
            )
            usbManager.requestPermission(device, pi)
        }
    }

    fun open(context: Context, device: UsbDevice): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return false
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "Cannot open USB device without permission: ${device.deviceName}")
            return false
        }
        close()
        val conn = try {
            usbManager.openDevice(device)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in UsbManager.openDevice", e)
            null
        } ?: run {
            Log.e(TAG, "UsbManager.openDevice returned null")
            _lastErrorMessage.value = "Failed to open USB device connection"
            return false
        }
        val fd = conn.fileDescriptor
        if (fd < 0) {
            Log.e(TAG, "Invalid USB connection file descriptor")
            conn.close()
            _lastErrorMessage.value = "Invalid USB device file descriptor"
            return false
        }
        val ok = UacDriverNative.nativeOpen(fd)
        if (!ok) {
            conn.close()
            _lastErrorMessage.value = "libusb wrap device failed"
            return false
        }
        activeConnection = conn
        _activeDevice.value = device
        _isOpen.value = true
        _lastErrorMessage.value = null
        registerVolumeReceiver(context)
        syncVolume(context)
        Log.i(TAG, "Opened USB audio device '${device.deviceName}' (vid=0x${device.vendorId.toString(16)}, pid=0x${device.productId.toString(16)})")
        return true
    }

    fun close() {
        if (_isOpen.value) {
            stop()
            UacDriverNative.nativeClose()
            activeConnection?.close()
            activeConnection = null
            _activeDevice.value = null
            _isOpen.value = false
            Log.i(TAG, "Closed USB audio device")
        }
    }

    fun start(sampleRateHz: Int, bitsPerSample: Int, channels: Int = 2): Boolean {
        if (!_isOpen.value) {
            _lastErrorMessage.value = "USB device is not opened"
            Log.w(TAG, "start called but USB device is not open")
            return false
        }
        val ok = UacDriverNative.nativeStart(sampleRateHz, bitsPerSample, channels)
        _isStreaming.value = ok
        if (ok) {
            _lastErrorMessage.value = null
            Log.i(TAG, "UAC stream successfully started: ${sampleRateHz}Hz ${bitsPerSample}-bit ${channels}ch")
        } else {
            val detail = UacDriverNative.nativeLastErrorDetail()
            val code = UacDriverNative.nativeLastErrorCode()
            _lastErrorMessage.value = if (!detail.isNullOrBlank()) detail else "Failed to start stream (error $code)"
            Log.w(TAG, "UAC start failed (code $code): ${_lastErrorMessage.value}")
        }
        return ok
    }

    suspend fun ensureUacReadyForPlayback(context: Context): Boolean {
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val bitPerfectEnabled = prefs.getBoolean(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, false)
        val driverMethod = BitPerfectDriverMethod.fromStorage(prefs.getString(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, null))
        if (!bitPerfectEnabled || driverMethod != BitPerfectDriverMethod.DirectUac) {
            return true
        }
        val rawUsb = findUsbAudioDevice(context) ?: return true
        if (!_isOpen.value) {
            val hasPerm = hasPermission(context, rawUsb)
            val granted = if (hasPerm) true else requestPermission(context, rawUsb)
            if (!granted) {
                _lastErrorMessage.value = "USB permission not granted"
                return false
            }
            val opened = open(context, rawUsb)
            if (!opened) {
                return false
            }
        }
        val targetRate = NativeBridge.getDecoderRenderSampleRateHz().takeIf { it > 0 } ?: 48000
        val targetBitDepth = NativeBridge.getTrackBitDepth().takeIf { it in listOf(16, 24, 32) } ?: 16
        val targetChannels = NativeBridge.getTrackChannelCount().takeIf { it > 0 } ?: 2
        val ok = start(targetRate, targetBitDepth, targetChannels)
        if (ok) syncVolume(context)
        NativeBridge.setBitPerfectMode(ok)
        return ok
    }

    fun registerVolumeReceiver(context: Context) {
        if (volumeReceiverRegistered) return
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply {
            addAction("android.media.EXTRA_VOLUME_STREAM_TYPE")
        }
        val appCtx = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appCtx.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appCtx.registerReceiver(volumeReceiver, filter)
        }
        volumeReceiverRegistered = true
        syncVolume(appCtx)
    }

    fun unregisterVolumeReceiver(context: Context) {
        if (!volumeReceiverRegistered) return
        runCatching { context.applicationContext.unregisterReceiver(volumeReceiver) }
        volumeReceiverRegistered = false
    }

    fun setVolumeMode(context: Context, mode: DirectUacVolumeMode) {
        _volumeMode.value = mode
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AppPreferenceKeys.DIRECT_UAC_VOLUME_MODE, mode.storageValue).apply()
        syncVolume(context)
    }

    fun setManualVolume(context: Context, volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _manualVolume.value = clamped
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(AppPreferenceKeys.DIRECT_UAC_MANUAL_VOLUME, clamped).apply()
        syncVolume(context)
    }

    fun syncVolume(context: Context) {
        val prefs = context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
        val mode = DirectUacVolumeMode.fromStorage(prefs.getString(AppPreferenceKeys.DIRECT_UAC_VOLUME_MODE, DirectUacVolumeMode.System.storageValue))
        _volumeMode.value = mode
        val manualVol = prefs.getFloat(AppPreferenceKeys.DIRECT_UAC_MANUAL_VOLUME, 1.0f).coerceIn(0f, 1f)
        _manualVolume.value = manualVol

        val scale = when (mode) {
            DirectUacVolumeMode.None -> 1.0f
            DirectUacVolumeMode.Manual -> manualVol
            DirectUacVolumeMode.System -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
                    if (cur <= min) {
                        0.0f
                    } else {
                        var computedScale: Float? = null
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            try {
                                val db = am.getStreamVolumeDb(AudioManager.STREAM_MUSIC, cur, android.media.AudioDeviceInfo.TYPE_USB_DEVICE)
                                if (!db.isNaN() && db <= 0.0f && db > -120.0f) {
                                    computedScale = Math.pow(10.0, db.toDouble() / 20.0).toFloat().coerceIn(0f, 1f)
                                }
                            } catch (_: Throwable) {}
                        }
                        if (computedScale != null) {
                            computedScale
                        } else {
                            val range = (max - min).coerceAtLeast(1)
                            val normalized = ((cur - min).toFloat() / range.toFloat()).coerceIn(0f, 1f)
                            normalized * normalized * normalized
                        }
                    }
                } else {
                    1.0f
                }
            }
        }
        _effectiveVolumeScale.value = scale
        UacDriverNative.nativeSetVolumeScale(scale)
    }

    fun clearError() {
        _lastErrorMessage.value = null
    }

    fun stop() {
        if (_isStreaming.value) {
            UacDriverNative.nativeStop()
            _isStreaming.value = false
            Log.i(TAG, "UAC stream stopped")
        }
    }

    fun flush() {
        if (_isStreaming.value) {
            UacDriverNative.nativeFlushRing()
        }
    }

    fun writePcm(directBuffer: ByteBuffer, frames: Int): Int {
        return UacDriverNative.nativeWrite(directBuffer, frames)
    }

    fun getSupportedRates(): List<UacClockRange> {
        val raw = UacDriverNative.nativeSupportedRates() ?: return emptyList()
        val list = ArrayList<UacClockRange>(raw.size / 4)
        for (i in raw.indices step 4) {
            if (i + 3 < raw.size) {
                list.add(UacClockRange(raw[i], raw[i + 1], raw[i + 2], raw[i + 3]))
            }
        }
        return list
    }

    fun getDiagnostics(): UacDiagnostics? {
        val raw = UacDriverNative.nativeFormatDiagnostics() ?: return null
        if (raw.size < 13) return null
        return UacDiagnostics(
            sampleRateHz = raw[0].toInt(),
            bitsPerSample = raw[1].toInt(),
            channels = raw[2].toInt(),
            interfaceNumber = raw[3].toInt(),
            altSetting = raw[4].toInt(),
            endpointAddress = raw[5].toInt(),
            maxPacketSize = raw[6].toInt(),
            bInterval = raw[7].toInt(),
            uacVersion = raw[8].toInt(),
            clockSourceId = raw[9].toInt(),
            feedbackEndpointAddress = raw[10].toInt(),
            isHighSpeed = raw[11] == 1L,
            bytesPerSample = raw[12].toInt()
        )
    }

    fun getLastErrorCode(): UacStartError = UacStartError.fromCode(UacDriverNative.nativeLastErrorCode())
    fun getLastErrorDetail(): String? = UacDriverNative.nativeLastErrorDetail()
}
