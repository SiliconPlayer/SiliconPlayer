package com.flopster101.siliconplayer

import java.util.Locale

val selectableVisualizationModes: List<VisualizationMode> = listOf(
    VisualizationMode.Bars,
    VisualizationMode.Oscilloscope,
    VisualizationMode.VuMeters,
    VisualizationMode.ChannelScope,
    VisualizationMode.Starfield,
    VisualizationMode.ProjectM
)

private val visualizationModeStorageAliases: Map<String, VisualizationMode> = buildMap {
    selectableVisualizationModes.forEach { mode ->
        val normalizedStorage = mode.storageValue.lowercase(Locale.ROOT)
        put(normalizedStorage, mode)
        put(mode.name.lowercase(Locale.ROOT), mode)
        put(mode.label.lowercase(Locale.ROOT), mode)
        put(normalizedStorage.replace("_", ""), mode)
    }
    put("vumeters", VisualizationMode.VuMeters)
    put("vu", VisualizationMode.VuMeters)
    put("channelscope", VisualizationMode.ChannelScope)
    put("starfield_classic", VisualizationMode.Starfield)
    put("starfield_warp", VisualizationMode.Starfield)
    put("starfield_snow", VisualizationMode.Starfield)
    put("starfield_beat", VisualizationMode.Starfield)
}
private val visualizationModeAliasStripPattern = Regex("[^a-z0-9_]")

fun parseEnabledVisualizationModes(raw: String?): Set<VisualizationMode> {
    if (raw.isNullOrBlank()) return selectableVisualizationModes.toSet()
    val parsed = raw
        .split(',')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .mapNotNull { value ->
            visualizationModeStorageAliases[value]
                ?: visualizationModeStorageAliases[value.replace(visualizationModeAliasStripPattern, "")]
        }
        .toSet()
    if (parsed.isEmpty()) return selectableVisualizationModes.toSet()
    return parsed
}

fun serializeEnabledVisualizationModes(modes: Set<VisualizationMode>): String {
    return selectableVisualizationModes
        .filter { modes.contains(it) }
        .joinToString(",") { it.storageValue }
}

fun isVisualizationModeSupported(
    mode: VisualizationMode,
    coreNameForUi: String?
): Boolean {
    return when (mode) {
        VisualizationMode.ChannelScope -> supportsChannelScopeVisualization(coreNameForUi)
        VisualizationMode.ProjectM -> supportsProjectM()
        else -> true
    }
}

fun supportsChannelScopeVisualization(coreNameForUi: String?): Boolean {
    val pluginName = pluginNameForCoreName(coreNameForUi) ?: return false
    return NativeBridge.decoderSupportsFeature(pluginName, DecoderFeatures.CHANNEL_SCOPE)
}

/** Channel-scope text capability bits (see AudioDecoder::kScopeText*). */
object ChannelScopeTextCapability {
    const val NOTE = 1
    const val VOLUME = 1 shl 1
    const val EFFECTS = 1 shl 2
    const val INSTRUMENT = 1 shl 3
    const val SAMPLE = 1 shl 4
}

fun channelScopeTextCapabilities(coreNameForUi: String?): Int {
    val pluginName = pluginNameForCoreName(coreNameForUi) ?: return 0
    return NativeBridge.getChannelScopeTextCapabilities(pluginName)
}

fun isVisualizationModeSelectable(
    mode: VisualizationMode,
    enabledModes: Set<VisualizationMode>,
    coreNameForUi: String?
): Boolean {
    if (!enabledModes.contains(mode)) return false
    return isVisualizationModeSupported(mode, coreNameForUi)
}
