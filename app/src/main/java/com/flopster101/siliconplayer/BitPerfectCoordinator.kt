package com.flopster101.siliconplayer

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build

enum class BitPerfectSupportStatus {
    Supported,
    UnsupportedApiLevel,
    UnsupportedAudioHal
}

object BitPerfectCoordinator {

    fun isBitPerfectPlatformSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun checkBitPerfectSupport(context: Context, usbDevice: AudioDeviceInfo? = null): BitPerfectSupportStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return BitPerfectSupportStatus.UnsupportedApiLevel
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return BitPerfectSupportStatus.UnsupportedAudioHal
        val device = usbDevice ?: findConnectedUsbAudioDevice(context)
            ?: return BitPerfectSupportStatus.Supported

        val supportedMixers = runCatching { audioManager.getSupportedMixerAttributes(device) }.getOrNull() ?: emptyList()
        return if (supportedMixers.any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }) {
            BitPerfectSupportStatus.Supported
        } else {
            BitPerfectSupportStatus.UnsupportedAudioHal
        }
    }

    fun findConnectedUsbAudioDevice(context: Context): AudioDeviceInfo? {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
        }
        return null
    }

    fun getUsbDeviceSupportedSampleRates(context: Context, device: AudioDeviceInfo? = null): List<Int> {
        val rates = mutableSetOf<Int>()

        // 1. Linux ALSA kernel procfs (queries all UAC 1.0 & 2.0 altsets from snd-usb-audio driver)
        val alsaRates = queryAlsaUsbSupportedSampleRates()
        rates.addAll(alsaRates)

        // 2. Android 14+ AudioMixerAttributes hardware queries
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val targetDevice = device ?: findConnectedUsbAudioDevice(context)
            if (audioManager != null && targetDevice != null) {
                val mixerAttrs = runCatching { audioManager.getSupportedMixerAttributes(targetDevice) }.getOrNull()
                mixerAttrs?.forEach { attr ->
                    val rate = attr.format.sampleRate
                    if (rate > 0) rates.add(rate)
                }
            }
        }

        // 3. UsbManager direct hardware raw descriptor parsing & native UAC driver GET_RANGE
        val usbRates = queryUsbDeviceSampleRatesFromUsbManager(context)
        rates.addAll(usbRates)
        val uacRanges = com.flopster101.siliconplayer.usb.UacDriverCoordinator.getSupportedRates()
        uacRanges.forEach { range ->
            if (range.minHz > 0) rates.add(range.minHz)
            if (range.maxHz > 0) rates.add(range.maxHz)
        }

        // 4. AudioDeviceInfo audioProfiles (API 31+) & sampleRates
        val targetDevice = device ?: findConnectedUsbAudioDevice(context)
        if (targetDevice != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                targetDevice.audioProfiles.forEach { profile ->
                    profile.sampleRates.forEach { if (it > 0) rates.add(it) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetDevice.sampleRates.forEach { if (it > 0) rates.add(it) }
            }
        }

        val isUac1 = isConnectedUsbAudioUac1(context)
        val resolvedRates = if (rates.isEmpty() || (rates.size == 1 && rates.contains(48000))) {
            if (isUac1) {
                listOf(44100, 48000, 96000)
            } else {
                listOf(44100, 48000, 88200, 96000, 176400, 192000)
            }
        } else {
            rates.sorted()
        }

        return if (isUac1) {
            resolvedRates.filter { it <= 96000 }
        } else {
            resolvedRates
        }
    }

    fun isConnectedUsbAudioUac1(context: Context): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager ?: return false
        for (device in usbManager.deviceList.values) {
            var hasAudio = false
            var isUac2OrHigher = false
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == android.hardware.usb.UsbConstants.USB_CLASS_AUDIO) {
                    hasAudio = true
                    if (iface.interfaceProtocol >= 0x20) {
                        isUac2OrHigher = true
                    }
                }
            }
            if (hasAudio && !isUac2OrHigher) {
                return true
            }
        }
        return false
    }

    fun queryAlsaUsbSupportedSampleRates(): Set<Int> {
        val rates = mutableSetOf<Int>()
        val asoundDir = java.io.File("/proc/asound")
        if (!asoundDir.exists() || !asoundDir.isDirectory) return rates

        val cardDirs = asoundDir.listFiles { file -> file.isDirectory && file.name.startsWith("card") } ?: emptyArray()
        for (cardDir in cardDirs) {
            val streamFiles = cardDir.listFiles { file -> file.isFile && file.name.startsWith("stream") } ?: emptyArray()
            for (streamFile in streamFiles) {
                parseAlsaStreamFile(streamFile, rates)
            }
        }
        return rates
    }

    private fun parseAlsaStreamFile(file: java.io.File, outRates: MutableSet<Int>) {
        runCatching {
            var isPlayback = false
            file.forEachLine { rawLine ->
                val line = rawLine.trim()
                if (line.equals("Playback:", ignoreCase = true)) {
                    isPlayback = true
                } else if (line.equals("Capture:", ignoreCase = true)) {
                    isPlayback = false
                }

                if (isPlayback && line.startsWith("Rates:", ignoreCase = true)) {
                    val ratesPart = line.substringAfter("Rates:").trim()
                    if (ratesPart.contains("-") && ratesPart.contains("continuous", ignoreCase = true)) {
                        val parts = ratesPart.substringBefore("(").split("-").mapNotNull { it.trim().toIntOrNull() }
                        if (parts.size == 2) {
                            val min = parts[0]
                            val max = parts[1]
                            val standardRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000, 705600, 768000)
                            for (r in standardRates) {
                                if (r in min..max) outRates.add(r)
                            }
                        }
                    } else {
                        val tokens = ratesPart.split(",")
                        for (token in tokens) {
                            val rate = token.trim().takeWhile { it.isDigit() }.toIntOrNull()
                            if (rate != null && rate in 8000..768000) {
                                outRates.add(rate)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun queryUsbDeviceSampleRatesFromUsbManager(context: Context): Set<Int> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? android.hardware.usb.UsbManager ?: return emptySet()
        val rates = mutableSetOf<Int>()
        for (device in usbManager.deviceList.values) {
            val rawDescriptors = runCatching {
                val method = device.javaClass.getMethod("getRawDescriptors")
                method.invoke(device) as? ByteArray
            }.getOrNull() ?: runCatching {
                val connection = usbManager.openDevice(device)
                val bytes = connection?.rawDescriptors
                connection?.close()
                bytes
            }.getOrNull()

            if (rawDescriptors != null && rawDescriptors.isNotEmpty()) {
                parseUacSampleRatesFromDescriptors(rawDescriptors, rates)
            }
        }
        return rates
    }

    private fun parseUacSampleRatesFromDescriptors(descriptors: ByteArray, outRates: MutableSet<Int>) {
        var offset = 0
        while (offset + 2 <= descriptors.size) {
            val length = descriptors[offset].toInt() and 0xFF
            if (length < 2 || offset + length > descriptors.size) break
            val descType = descriptors[offset + 1].toInt() and 0xFF

            // CS_INTERFACE = 0x24
            if (descType == 0x24 && length >= 8) {
                val descSubtype = descriptors[offset + 2].toInt() and 0xFF
                // FORMAT_TYPE = 0x02
                if (descSubtype == 0x02) {
                    val formatType = descriptors[offset + 3].toInt() and 0xFF
                    if (formatType == 0x01 || formatType == 0x03) { // FORMAT_TYPE_I or FORMAT_TYPE_III
                        val samFreqType = descriptors[offset + 7].toInt() and 0xFF
                        if (samFreqType > 0) {
                            // Discrete frequencies (3-byte little-endian per frequency)
                            for (i in 0 until samFreqType) {
                                val fIdx = offset + 8 + i * 3
                                if (fIdx + 3 <= offset + length) {
                                    val rate = (descriptors[fIdx].toInt() and 0xFF) or
                                            ((descriptors[fIdx + 1].toInt() and 0xFF) shl 8) or
                                            ((descriptors[fIdx + 2].toInt() and 0xFF) shl 16)
                                    if (rate in 8000..768000) {
                                        outRates.add(rate)
                                    }
                                }
                            }
                        } else if (samFreqType == 0 && offset + 14 <= offset + length) {
                            // Continuous frequency range: lower (3 bytes) and upper (3 bytes)
                            val minRate = (descriptors[offset + 8].toInt() and 0xFF) or
                                    ((descriptors[offset + 9].toInt() and 0xFF) shl 8) or
                                    ((descriptors[offset + 10].toInt() and 0xFF) shl 16)
                            val maxRate = (descriptors[offset + 11].toInt() and 0xFF) or
                                    ((descriptors[offset + 12].toInt() and 0xFF) shl 8) or
                                    ((descriptors[offset + 13].toInt() and 0xFF) shl 16)
                            val standardRates = listOf(44100, 48000, 88200, 96000, 176400, 192000, 352800, 384000)
                            for (r in standardRates) {
                                if (r in minRate..maxRate) {
                                    outRates.add(r)
                                }
                            }
                        }
                    }
                }
            }
            offset += length
        }
    }

    fun setPreferredBitPerfectMixer(
        context: Context,
        usbDevice: AudioDeviceInfo,
        sampleRateHz: Int,
        channelCount: Int = 2
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false

        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val mediaAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val supportedMixers = runCatching { audioManager.getSupportedMixerAttributes(usbDevice) }.getOrNull() ?: emptyList()
        val matchingBitPerfectMixer = supportedMixers.firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
            (sampleRateHz <= 0 || it.format.sampleRate == sampleRateHz || it.format.sampleRate == 0)
        } ?: supportedMixers.firstOrNull {
            it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
        }

        if (matchingBitPerfectMixer != null) {
            val ok = runCatching {
                audioManager.setPreferredMixerAttributes(mediaAudioAttributes, usbDevice, matchingBitPerfectMixer)
            }.getOrDefault(false)
            if (ok) return true
        }

        val encodings = listOf(
            AudioFormat.ENCODING_PCM_FLOAT,
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_24BIT_PACKED,
            AudioFormat.ENCODING_PCM_32BIT
        )
        for (enc in encodings) {
            val format = AudioFormat.Builder()
                .setSampleRate(sampleRateHz)
                .setChannelMask(channelMask)
                .setEncoding(enc)
                .build()
            val mixer = runCatching {
                AudioMixerAttributes.Builder(format)
                    .setMixerBehavior(AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT)
                    .build()
            }.getOrNull()
            if (mixer != null) {
                val ok = runCatching {
                    audioManager.setPreferredMixerAttributes(mediaAudioAttributes, usbDevice, mixer)
                }.getOrDefault(false)
                if (ok) return true
            }
        }
        return false
    }

    fun clearBitPerfectMixer(context: Context, usbDevice: AudioDeviceInfo? = null): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val device = usbDevice ?: findConnectedUsbAudioDevice(context) ?: return false

        val mediaAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        return runCatching {
            audioManager.clearPreferredMixerAttributes(mediaAudioAttributes, device)
        }.getOrDefault(false)
    }

    fun isBitPerfectActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val usbDevice = findConnectedUsbAudioDevice(context) ?: return false

        val mediaAudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val currentMixer = runCatching {
            audioManager.getPreferredMixerAttributes(mediaAudioAttributes, usbDevice)
        }.getOrNull()

        return currentMixer?.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT
    }
}
