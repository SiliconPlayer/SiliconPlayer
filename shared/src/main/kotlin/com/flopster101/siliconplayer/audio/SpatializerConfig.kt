package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.NativeBridge
import org.json.JSONObject

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
    val backend: SpatializerBackend = SpatializerBackend.Off,
    val enabled: Boolean = true,
    val spatializeStereo: Boolean = false,
    val centerGainDb: Float = 0f,
    val surroundGainDb: Float = 0f,
    val heightGainDb: Float = 0f,
    val lfeGainDb: Float = -3f,
    val lfeLowPassEnabled: Boolean = true,
    val wetMix: Float = 1f,
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
        .put("ambisonicOrder", ambisonicOrder)
        .put("renderMethod", renderMethod)
        .put("yawDegrees", yawDegrees.toDouble())
        .put("customSofaPath", customSofaPath)
        .toString()

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
                ambisonicOrder = root.optInt("ambisonicOrder", defaults.ambisonicOrder).coerceIn(1, 3),
                renderMethod = root.optInt("renderMethod", defaults.renderMethod).coerceIn(0, 1),
                yawDegrees = root.optDouble("yawDegrees", defaults.yawDegrees.toDouble()).toFloat(),
                customSofaPath = root.optString("customSofaPath", defaults.customSofaPath),
            )
        }
    }
}

internal fun applySpatializerConfigToNative(config: SpatializerConfig) {
    NativeBridge.setSpatializerConfig(config.toJson())
}

internal fun loadSpatializerConfigFromNative(): SpatializerConfig =
    SpatializerConfig.fromJson(NativeBridge.getSpatializerConfig())
