package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.matchesDecoderName
import com.flopster101.siliconplayer.platform.AppPreferences
import org.json.JSONObject

enum class SpatializerPreset(val id: String, val label: String) {
    Neutral("neutral", "Neutral"),
    Music("music", "Music"),
    Wide("wide", "Wide"),
    Atmosphere("atmosphere", "Atmosphere"),
    Cinema("cinema", "Cinema"),
    Custom("custom", "Custom");

    companion object {
        fun fromId(id: String?): SpatializerPreset = when (id) {
            "soft_v" -> Music
            "soft_v_wide", "soft_v_wide_spatial" -> Atmosphere
            else -> entries.firstOrNull { it.id == id } ?: Custom
        }
    }
}

enum class SpatializerBackend(val id: String) {
    Off("off"),
    LibSpatialAudio("libspatialaudio"),
    SteamAudio("steam_audio");

    companion object {
        fun fromId(id: String?): SpatializerBackend =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: Off
    }
}

data class SpatializerConfig(
    val backend: SpatializerBackend = SpatializerBackend.SteamAudio,
    val enabled: Boolean = true,
    val spatializeStereo: Boolean = false,
    val centerGainDb: Float = -1.5f,
    val surroundGainDb: Float = 1.5f,
    val heightGainDb: Float = 0f,
    val lfeGainDb: Float = -3f,
    val lfeLowPassEnabled: Boolean = true,
    val wetMix: Float = 1f,
    val bassGainDb: Float = 0f,
    val trebleGainDb: Float = 0.5f,
    val surroundTrebleGainDb: Float = -3f,
    val wideSurround: Boolean = true,
    val frontAmbience: Boolean = true,
    val virtualizerWidth: Float = 1.5f,
    val frontDiffuse: Boolean = true,
    val compressorMode: Int = 2,
    val preset: SpatializerPreset = SpatializerPreset.Atmosphere,
    // Output level makeup applied to the spatialized render.
    val outputGainDb: Float = 8.5f,
    val ambisonicOrder: Int = 3,
    val renderMethod: Int = 0,
    val yawDegrees: Float = 0f,
    val customSofaPath: String = "",
) {
    fun toJson(): String = JSONObject()
        .put("backend", backend.id)
        .put("enabled", enabled)
        .put("spatializeStereo", spatializeStereo)
        .put("centerGainDb", centerGainDb.toDouble())
        .put("surroundGainDb", surroundGainDb.toDouble())
        .put("heightGainDb", heightGainDb.toDouble())
        .put("lfeGainDb", lfeGainDb.toDouble())
        .put("lfeLowPassEnabled", lfeLowPassEnabled)
        .put("wetMix", wetMix.toDouble())
        .put("bassGainDb", bassGainDb.toDouble())
        .put("trebleGainDb", trebleGainDb.toDouble())
        .put("surroundTrebleGainDb", surroundTrebleGainDb.toDouble())
        .put("wideSurround", wideSurround)
        .put("frontAmbience", frontAmbience)
        .put("virtualizerWidth", virtualizerWidth.toDouble())
        .put("frontDiffuse", frontDiffuse)
        .put("compressorMode", compressorMode)
        .put("preset", preset.id)
        .put("outputGainDb", outputGainDb.toDouble())
        .put("ambisonicOrder", ambisonicOrder)
        .put("renderMethod", renderMethod)
        .put("yawDegrees", yawDegrees.toDouble())
        .put("customSofaPath", customSofaPath)
        .toString()

    fun withPreset(preset: SpatializerPreset): SpatializerConfig {
        val defaults = SpatializerConfig()
        return when (preset) {
            SpatializerPreset.Custom -> copy(preset = preset)
            SpatializerPreset.Neutral -> copy(
                bassGainDb = 0f,
                trebleGainDb = 0f,
                surroundTrebleGainDb = 0f,
                centerGainDb = 0f,
                surroundGainDb = 0f,
                heightGainDb = 0f,
                wideSurround = false,
                frontAmbience = false,
                virtualizerWidth = 1f,
                frontDiffuse = false,
                compressorMode = 0,
                preset = preset,
            )
            SpatializerPreset.Music -> copy(
                bassGainDb = 1f,
                trebleGainDb = 1f,
                surroundTrebleGainDb = -1.5f,
                centerGainDb = -0.5f,
                surroundGainDb = 0.5f,
                heightGainDb = defaults.heightGainDb,
                wideSurround = false,
                frontAmbience = false,
                virtualizerWidth = 1.2f,
                frontDiffuse = true,
                compressorMode = 1,
                preset = preset,
            )
            SpatializerPreset.Wide -> copy(
                bassGainDb = 0f,
                trebleGainDb = 0f,
                surroundTrebleGainDb = -3f,
                centerGainDb = -1.5f,
                surroundGainDb = 1.5f,
                heightGainDb = defaults.heightGainDb,
                wideSurround = true,
                frontAmbience = true,
                virtualizerWidth = 1.35f,
                frontDiffuse = false,
                compressorMode = 1,
                preset = preset,
            )
            SpatializerPreset.Atmosphere -> copy(
                bassGainDb = 0f,
                trebleGainDb = 0.5f,
                surroundTrebleGainDb = -3f,
                centerGainDb = -1.5f,
                surroundGainDb = 1.5f,
                heightGainDb = defaults.heightGainDb,
                wideSurround = true,
                frontAmbience = true,
                virtualizerWidth = 1.5f,
                frontDiffuse = true,
                compressorMode = 2,
                preset = preset,
            )
            SpatializerPreset.Cinema -> copy(
                bassGainDb = 1.5f,
                trebleGainDb = 1f,
                surroundTrebleGainDb = -2.5f,
                centerGainDb = -1f,
                surroundGainDb = 2.5f,
                heightGainDb = 1f,
                wideSurround = true,
                frontAmbience = true,
                virtualizerWidth = 1.75f,
                frontDiffuse = true,
                compressorMode = 2,
                preset = preset,
            )
        }
    }

    companion object {
        fun fromJson(json: String?): SpatializerConfig {
            if (json.isNullOrBlank()) return SpatializerConfig()
            val root = try {
                JSONObject(json)
            } catch (_: Exception) {
                return SpatializerConfig()
            }
            val defaults = SpatializerConfig()
            return SpatializerConfig(
                backend = SpatializerBackend.fromId(root.optString("backend", defaults.backend.id)),
                enabled = root.optBoolean("enabled", defaults.enabled),
                spatializeStereo = root.optBoolean("spatializeStereo", defaults.spatializeStereo),
                centerGainDb = root.optDouble("centerGainDb", defaults.centerGainDb.toDouble()).toFloat(),
                surroundGainDb = root.optDouble("surroundGainDb", defaults.surroundGainDb.toDouble()).toFloat(),
                heightGainDb = root.optDouble("heightGainDb", defaults.heightGainDb.toDouble()).toFloat(),
                lfeGainDb = root.optDouble("lfeGainDb", defaults.lfeGainDb.toDouble()).toFloat(),
                lfeLowPassEnabled = root.optBoolean("lfeLowPassEnabled", defaults.lfeLowPassEnabled),
                wetMix = root.optDouble("wetMix", defaults.wetMix.toDouble()).toFloat().coerceIn(0f, 1f),
                bassGainDb = root.optDouble("bassGainDb", defaults.bassGainDb.toDouble())
                    .toFloat().coerceIn(-12f, 12f),
                trebleGainDb = root.optDouble("trebleGainDb", defaults.trebleGainDb.toDouble())
                    .toFloat().coerceIn(-12f, 12f),
                surroundTrebleGainDb = root.optDouble(
                    "surroundTrebleGainDb",
                    defaults.surroundTrebleGainDb.toDouble()
                ).toFloat().coerceIn(-12f, 12f),
                wideSurround = root.optBoolean("wideSurround", defaults.wideSurround),
                frontAmbience = root.optBoolean("frontAmbience", defaults.frontAmbience),
                virtualizerWidth = root.optDouble("virtualizerWidth", defaults.virtualizerWidth.toDouble())
                    .toFloat().coerceIn(0f, 2f),
                frontDiffuse = root.optBoolean("frontDiffuse", defaults.frontDiffuse),
                compressorMode = root.optInt("compressorMode", defaults.compressorMode).coerceIn(0, 2),
                preset = SpatializerPreset.fromId(root.optString("preset", defaults.preset.id)),
                outputGainDb = root.optDouble("outputGainDb", defaults.outputGainDb.toDouble())
                    .toFloat().coerceIn(-24f, 18f),
                ambisonicOrder = root.optInt("ambisonicOrder", defaults.ambisonicOrder).coerceIn(1, 3),
                renderMethod = root.optInt("renderMethod", defaults.renderMethod).coerceIn(0, 2),
                yawDegrees = root.optDouble("yawDegrees", defaults.yawDegrees.toDouble()).toFloat(),
                customSofaPath = root.optString("customSofaPath", defaults.customSofaPath),
            )
        }
    }
}

internal fun applySpatializerConfigToNative(config: SpatializerConfig) {
    NativeBridge.setSpatializerConfig(config.copy(spatializeStereo = false).toJson())
}

internal fun loadSpatializerConfigFromNative(): SpatializerConfig =
    SpatializerConfig.fromJson(NativeBridge.getSpatializerConfig())

internal fun readSpatializerConfig(prefs: AppPreferences): SpatializerConfig =
    SpatializerConfig.fromJson(prefs.getString(AppPreferenceKeys.AUDIO_SPATIALIZER_CONFIG, null))

internal fun writeSpatializerConfig(
    editor: AppPreferences.Editor,
    config: SpatializerConfig
) {
    editor.putString(
        AppPreferenceKeys.AUDIO_SPATIALIZER_CONFIG,
        config.copy(spatializeStereo = false).toJson()
    )
}

internal fun shouldShowSpatializerNode(
    decoderName: String?,
    channelCount: Int,
    config: SpatializerConfig
): Boolean =
    decoderName.matchesDecoderName(DecoderNames.FFMPEG) &&
        channelCount > 2 &&
        config.enabled &&
        config.backend != SpatializerBackend.Off

// The platform Dolby core owns its own pipeline and never spatializes; the
// muted FFmpeg shadow engine it runs for visualization is excluded natively.
internal fun isSystemDolbyActive(decoderName: String?): Boolean =
    decoderName.matchesDecoderName(DecoderNames.PLATFORM_DOLBY)
