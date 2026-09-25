package com.flopster101.siliconplayer

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

enum class MultiChannelOutputMode(val storageValue: String, val label: String, val nativeValue: Int) {
    FfmpegOnly("ffmpeg_only", "FFmpeg only", 0),
    AllDecoders("all_decoders", "All decoders", 1),
    Disabled("disabled", "Disabled", 2);

    companion object {
        fun fromStorage(value: String?): MultiChannelOutputMode {
            return entries.firstOrNull { it.storageValue == value } ?: FfmpegOnly
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
                return AAudio
            }
            return entries.firstOrNull { it.storageValue == value } ?: Auto
        }
    }
}

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

fun isAaudioAvailableOnDevice(): Boolean {
    return try {
        val sdkInt = Class.forName("android.os.Build\$VERSION").getField("SDK_INT").getInt(null)
        sdkInt >= 26
    } catch (_: Throwable) {
        false
    }
}

fun AudioBackendPreference.coerceForCurrentApi(): AudioBackendPreference {
    if (this == AudioBackendPreference.AAudio && !isAaudioAvailableOnDevice()) {
        return AudioBackendPreference.OpenSLES
    }
    return this
}

fun defaultAudioBackendForCurrentApi(): AudioBackendPreference {
    return if (isAaudioAvailableOnDevice()) AudioBackendPreference.AAudio else AudioBackendPreference.OpenSLES
}

fun AudioBackendPreference.isAvailableOnCurrentPlatform(): Boolean {
    val osName = System.getProperty("os.name")?.lowercase() ?: ""
    return when (this) {
        AudioBackendPreference.Auto -> true
        AudioBackendPreference.AAudio -> isAaudioAvailableOnDevice()
        AudioBackendPreference.OpenSLES -> isAaudioAvailableOnDevice() || try { Class.forName("android.os.Build"); true } catch (_: Throwable) { false }
        AudioBackendPreference.NullAudio -> true
        AudioBackendPreference.ALSA,
        AudioBackendPreference.PulseAudio,
        AudioBackendPreference.JACK -> osName.contains("linux")
        AudioBackendPreference.WASAPI,
        AudioBackendPreference.DirectSound,
        AudioBackendPreference.WinMM -> osName.contains("win")
        AudioBackendPreference.CoreAudio -> osName.contains("mac")
        AudioBackendPreference.OSS -> osName.contains("bsd")
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

fun supportsMonetTheming(): Boolean {
    return try {
        val sdkInt = Class.forName("android.os.Build\$VERSION").getField("SDK_INT").getInt(null)
        sdkInt >= 31
    } catch (_: Throwable) {
        false
    }
}

fun defaultUseMonetForCurrentApi(): Boolean = supportsMonetTheming()

fun AudioBackendPreference.defaultPerformanceMode(): AudioPerformanceMode {
    return AudioPerformanceMode.None
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

enum class EffectiveVisualizationPerformanceMode(
    val label: String,
    val threadPriority: Int
) {
    HighPerformance("High performance", -16),
    Balanced("Balanced", -8),
    PowerSaving("Power saving", -4)
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
        val sdkInt = try {
            Class.forName("android.os.Build\$VERSION").getField("SDK_INT").getInt(null)
        } catch (_: Throwable) {
            null
        }
        if (sdkInt != null && sdkInt < 28) {
            return CpuArchitectureInfo(
                isLegacyOrConstrained = true,
                summary = "Legacy Android platform (API < 28)"
            )
        }

        val hardwareTags = try {
            val buildClass = Class.forName("android.os.Build")
            listOfNotNull(
                buildClass.getField("HARDWARE").get(null) as? String,
                buildClass.getField("BOARD").get(null) as? String,
                buildClass.getField("DEVICE").get(null) as? String,
                try {
                    buildClass.getField("SOC_MODEL").get(null) as? String
                } catch (_: Throwable) { null }
            ).filter { it.isNotBlank() }
        } catch (_: Throwable) {
            emptyList()
        }

        val constrainedSoCKeywords = listOf(
            "sm6125", "trinket", "ginkgo", "willow", "sdm660", "sdm636", "sdm632",
            "msm8953", "msm8937", "msm8917", "universal7884", "universal7885", "universal7904",
            "mt6768", "mt6765", "mt6762", "mt6761", "mt6769", "sc9863a"
        )
        for (tag in hardwareTags) {
            val lower = tag.lowercase()
            if (constrainedSoCKeywords.any { lower.contains(it) }) {
                return CpuArchitectureInfo(
                    isLegacyOrConstrained = true,
                    summary = "Constrained SoC detected ($tag)"
                )
            }
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
        }

        try {
            var maxFreqKhz = 0L
            for (cpuIdx in 0 until cores) {
                val freqFile = java.io.File("/sys/devices/system/cpu/cpu$cpuIdx/cpufreq/cpuinfo_max_freq")
                if (freqFile.exists() && freqFile.canRead()) {
                    val freq = freqFile.readText().trim().toLongOrNull() ?: 0L
                    if (freq > maxFreqKhz) maxFreqKhz = freq
                }
            }
            if (maxFreqKhz in 1..2_250_000L) {
                return CpuArchitectureInfo(
                    isLegacyOrConstrained = true,
                    summary = "Constrained CPU (max clock ≤ ${(maxFreqKhz / 1000)} MHz)"
                )
            }
        } catch (_: Throwable) {
        }

        return CpuArchitectureInfo(
            isLegacyOrConstrained = false,
            summary = "Standard multi-core CPU ($cores cores)"
        )
    }
}

fun resolveEffectiveVisualizationFullscreenMode(
    preference: VisualizationFullscreenMode,
    isWatch: Boolean
): VisualizationFullscreenMode {
    if (isWatch) return VisualizationFullscreenMode.SuperCompact
    return preference
}

fun resolveEffectiveVisualizationPerformanceMode(
    preference: VisualizationPerformanceMode,
    isWatch: Boolean = false
): EffectiveVisualizationPerformanceMode {
    if (isWatch) return EffectiveVisualizationPerformanceMode.PowerSaving
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

enum class PlaylistCoverGenerationMode(val storageValue: String, val label: String) {
    Never("never", "Never"),
    AtLeastFour("four_tracks", "At least 4 tracks"),
    AtLeastOne("one_track", "At least 1 track");

    companion object {
        fun fromStorage(value: String?): PlaylistCoverGenerationMode {
            return when (value) {
                "never", "false" -> Never
                "four_tracks", "4", "four" -> AtLeastFour
                "one_track", "1", "one" -> AtLeastOne
                "true" -> AtLeastFour
                else -> AtLeastFour
            }
        }
    }
}

fun readPlaylistCoverGenerationMode(prefs: com.flopster101.siliconplayer.platform.AppPreferences): PlaylistCoverGenerationMode {
    val rawString = try {
        prefs.getString(AppPreferenceKeys.PLAYLIST_COVER_GENERATION_MODE, null)
    } catch (_: ClassCastException) {
        null
    }
    if (rawString != null) {
        return PlaylistCoverGenerationMode.fromStorage(rawString)
    }
    val legacyBoolean = try {
        prefs.getBoolean(AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC, true)
    } catch (_: Exception) {
        null
    }
    return when (legacyBoolean) {
        false -> PlaylistCoverGenerationMode.Never
        true -> PlaylistCoverGenerationMode.AtLeastFour
        null -> PlaylistCoverGenerationMode.AtLeastFour
    }
}

fun savePlaylistCoverGenerationMode(
    prefs: com.flopster101.siliconplayer.platform.AppPreferences,
    mode: PlaylistCoverGenerationMode
) {
    prefs.edit()
        .putString(AppPreferenceKeys.PLAYLIST_COVER_GENERATION_MODE, mode.storageValue)
        .putBoolean(AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC, mode != PlaylistCoverGenerationMode.Never)
        .apply()
}

