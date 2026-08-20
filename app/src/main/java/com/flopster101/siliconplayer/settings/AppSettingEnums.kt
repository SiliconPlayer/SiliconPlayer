package com.flopster101.siliconplayer

import android.os.Build

enum class ThemeMode(val storageValue: String, val label: String) {
    Auto("auto", "Auto"),
    Light("light", "Light"),
    Dark("dark", "Dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class AudioBackendPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    Auto("auto", "Auto (Default)", 0),
    AAudio("aaudio", "AAudio", 1),
    OpenSLES("opensl", "OpenSL ES", 2),
    WASAPI("wasapi", "WASAPI", 3),
    DirectSound("dsound", "DirectSound", 4),
    WinMM("winmm", "WinMM", 5),
    CoreAudio("coreaudio", "Core Audio", 6),
    ALSA("alsa", "ALSA", 7),
    PulseAudio("pulseaudio", "PulseAudio", 8),
    JACK("jack", "JACK", 9),
    Sndio("sndio", "sndio", 10),
    Audio4("audio4", "audio(4)", 11),
    OSS("oss", "OSS", 12),
    NullAudio("null", "Null Audio", 13);

    companion object {
        fun fromStorage(value: String?): AudioBackendPreference {
            if (value == "audiotrack") {
                return defaultAudioBackendForCurrentApi()
            }
            val stored = entries.firstOrNull { it.storageValue == value } ?: defaultAudioBackendForCurrentApi()
            return stored.coerceForCurrentApi()
        }
    }
}

fun AudioBackendPreference.coerceForCurrentApi(): AudioBackendPreference {
    if (this == AudioBackendPreference.AAudio && !isAaudioAvailableOnDevice()) {
        return AudioBackendPreference.OpenSLES
    }
    return this
}

fun isAaudioAvailableOnDevice(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

fun defaultAudioBackendForCurrentApi(): AudioBackendPreference {
    return if (isAaudioAvailableOnDevice()) AudioBackendPreference.AAudio else AudioBackendPreference.OpenSLES
}

fun AudioBackendPreference.isAvailableOnCurrentPlatform(): Boolean {
    return when (this) {
        AudioBackendPreference.Auto -> true
        AudioBackendPreference.AAudio -> isAaudioAvailableOnDevice()
        AudioBackendPreference.OpenSLES -> true
        AudioBackendPreference.NullAudio -> true
        else -> false
    }
}

fun AudioBackendPreference.platformRequirementLabel(): String? {
    return when (this) {
        AudioBackendPreference.AAudio -> if (!isAaudioAvailableOnDevice()) "Android 8.0+ (API 26) required" else null
        AudioBackendPreference.WASAPI,
        AudioBackendPreference.DirectSound,
        AudioBackendPreference.WinMM -> "Windows"
        AudioBackendPreference.CoreAudio -> "macOS / iOS"
        AudioBackendPreference.ALSA,
        AudioBackendPreference.PulseAudio,
        AudioBackendPreference.JACK -> "Linux"
        AudioBackendPreference.Sndio -> "OpenBSD"
        AudioBackendPreference.Audio4 -> "NetBSD / OpenBSD"
        AudioBackendPreference.OSS -> "FreeBSD"
        else -> null
    }
}

fun supportsMonetTheming(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun defaultUseMonetForCurrentApi(): Boolean = supportsMonetTheming()

fun AudioBackendPreference.defaultPerformanceMode(): AudioPerformanceMode {
    return when (this) {
        AudioBackendPreference.Auto,
        AudioBackendPreference.AAudio,
        AudioBackendPreference.WASAPI,
        AudioBackendPreference.ALSA,
        AudioBackendPreference.PulseAudio,
        AudioBackendPreference.JACK,
        AudioBackendPreference.CoreAudio -> AudioPerformanceMode.LowLatency
        else -> AudioPerformanceMode.None
    }
}

fun AudioBackendPreference.defaultBufferPreset(): AudioBufferPreset {
    return recommendedAudioBufferPresetForCurrentDevice()
}

enum class AudioPerformanceMode(val storageValue: String, val label: String, val nativeValue: Int) {
    LowLatency("low_latency", "Low latency", 1),
    None("none", "None", 2),
    PowerSaving("power_saving", "Power saving", 3);

    companion object {
        fun fromStorage(value: String?): AudioPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: None
        }
    }
}

enum class AudioBufferPreset(val storageValue: String, val label: String, val nativeValue: Int) {
    VerySmall("very_small", "Very small", 0),
    Small("small", "Small", 1),
    Medium("medium", "Medium", 2),
    Large("large", "Large", 3),
    VeryLarge("very_large", "Very large", 4);

    companion object {
        fun fromStorage(value: String?): AudioBufferPreset {
            return entries.firstOrNull { it.storageValue == value } ?: recommendedAudioBufferPresetForCurrentDevice()
        }
    }
}

private fun recommendedAudioBufferPresetForCurrentDevice(): AudioBufferPreset {
    return if (Runtime.getRuntime().availableProcessors().coerceAtLeast(1) <= 4) {
        AudioBufferPreset.VeryLarge
    } else {
        AudioBufferPreset.Large
    }
}

enum class AudioResamplerPreference(val storageValue: String, val label: String, val nativeValue: Int) {
    BuiltIn("builtin", "Built-in", 1),
    Sox("sox", "SoX (Experimental)", 2);

    companion object {
        fun fromStorage(value: String?): AudioResamplerPreference {
            return entries.firstOrNull { it.storageValue == value } ?: BuiltIn
        }
    }
}

enum class LookaheadClipperMode(val storageValue: String, val label: String, val nativeValue: Int) {
    Off("off", "Off", 0),
    Soft("soft", "Soft", 1),
    Hard("hard", "Hard", 2);

    companion object {
        fun fromStorage(value: String?): LookaheadClipperMode {
            return entries.firstOrNull { it.storageValue == value } ?: Soft
        }
    }
}

enum class FilenameDisplayMode(val storageValue: String, val label: String) {
    Always("always", "Always"),
    Never("never", "Never"),
    TrackerOnly("tracker_only", "Tracker/Chiptune formats only");

    companion object {
        fun fromStorage(value: String?): FilenameDisplayMode {
            return entries.firstOrNull { it.storageValue == value } ?: Always
        }
    }
}

enum class EndFadeCurve(val storageValue: String, val label: String, val nativeValue: Int) {
    Linear("linear", "Linear", 0),
    EaseIn("ease_in", "Ease-in", 1),
    EaseOut("ease_out", "Ease-out", 2);

    companion object {
        fun fromStorage(value: String?): EndFadeCurve {
            return entries.firstOrNull { it.storageValue == value } ?: Linear
        }
    }
}
