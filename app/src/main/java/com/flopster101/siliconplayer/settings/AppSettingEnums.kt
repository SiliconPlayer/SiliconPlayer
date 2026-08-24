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
            return entries.firstOrNull { it.storageValue == value } ?: TrackerOnly
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

enum class VisualizationPerformanceMode(
    val storageValue: String,
    val label: String,
    val description: String
) {
    Auto("auto", "Auto (Recommended)", "Automatically selects the best performance profile based on device CPU capabilities."),
    HighPerformance("high_performance", "High performance", "Elevates thread priority to maintain high FPS on constrained devices."),
    Balanced("balanced", "Balanced", "Standard UI display priority with good balance between performance and battery life."),
    PowerSaving("power_saving", "Power saving", "Lowers thread priority to maximize battery life.");

    companion object {
        fun fromStorage(value: String?): VisualizationPerformanceMode {
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

enum class EffectiveVisualizationPerformanceMode(
    val label: String,
    val threadPriority: Int
) {
    HighPerformance("High performance", android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY),
    Balanced("Balanced", android.os.Process.THREAD_PRIORITY_DISPLAY),
    PowerSaving("Power saving", android.os.Process.THREAD_PRIORITY_BACKGROUND)
}

data class CpuArchitectureInfo(
    val isLegacyOrConstrained: Boolean,
    val summary: String
)

object CpuHardwareDetector {
    val info: CpuArchitectureInfo by lazy { detectCpuArchitecture() }

    private fun detectCpuArchitecture(): CpuArchitectureInfo {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        if (cores <= 4) {
            return CpuArchitectureInfo(
                isLegacyOrConstrained = true,
                summary = "Constrained CPU (≤4 cores)"
            )
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return CpuArchitectureInfo(
                isLegacyOrConstrained = true,
                summary = "Legacy Android platform (API < 28)"
            )
        }
        try {
            val cpuInfoFile = java.io.File("/proc/cpuinfo")
            if (cpuInfoFile.exists() && cpuInfoFile.canRead()) {
                val text = cpuInfoFile.readText()
                val cpuParts = Regex("""CPU\s+part\s*:\s*(0x[0-9a-fA-F]+|\d+)""")
                    .findAll(text)
                    .mapNotNull { match ->
                        val raw = match.groupValues[1]
                        if (raw.startsWith("0x", ignoreCase = true)) {
                            raw.substring(2).toIntOrNull(16)
                        } else {
                            raw.toIntOrNull()
                        }
                    }
                    .toSet()

                // Known legacy / in-order or weak LITTLE cores:
                // 0xd03 = Cortex-A53
                // 0xd04 = Cortex-A35
                // 0xd07 = Cortex-A57
                // 0x801, 0x803, 0x205 = Kryo 260 / 280 Silver (A53-derived)
                // 0xc07, 0xc08, 0xc09, 0xc0f = ARMv7 (A7, A8, A9, A15)
                val legacyParts = setOf(
                    0xd03, 0xd04, 0xd07,
                    0x801, 0x803, 0x205,
                    0xc07, 0xc08, 0xc09, 0xc0f
                )
                if (cpuParts.isNotEmpty()) {
                    if (cpuParts.any { it in legacyParts }) {
                        return CpuArchitectureInfo(
                            isLegacyOrConstrained = true,
                            summary = "Cortex-A53 / legacy cores detected"
                        )
                    }
                    return CpuArchitectureInfo(
                        isLegacyOrConstrained = false,
                        summary = "Modern CPU architecture (Cortex-A55+)"
                    )
                }
            }
        } catch (_: Throwable) {
            // Fallback
        }
        return CpuArchitectureInfo(
            isLegacyOrConstrained = false,
            summary = "Standard multi-core CPU ($cores cores)"
        )
    }
}

fun resolveEffectiveVisualizationPerformanceMode(
    preference: VisualizationPerformanceMode
): EffectiveVisualizationPerformanceMode {
    return when (preference) {
        VisualizationPerformanceMode.HighPerformance -> EffectiveVisualizationPerformanceMode.HighPerformance
        VisualizationPerformanceMode.Balanced -> EffectiveVisualizationPerformanceMode.Balanced
        VisualizationPerformanceMode.PowerSaving -> EffectiveVisualizationPerformanceMode.PowerSaving
        VisualizationPerformanceMode.Auto -> {
            if (CpuHardwareDetector.info.isLegacyOrConstrained) {
                EffectiveVisualizationPerformanceMode.HighPerformance
            } else {
                EffectiveVisualizationPerformanceMode.Balanced
            }
        }
    }
}
