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
    return when (pluginNameForCoreName(coreNameForUi)) {
        DecoderNames.LIB_OPEN_MPT,
        DecoderNames.LIBXMP,
        DecoderNames.AYFLY,
        DecoderNames.C_RSID,
        DecoderNames.LIB_SID_PLAY_FP,
        DecoderNames.FURNACE,
        DecoderNames.GAME_MUSIC_EMU,
        DecoderNames.SC68,
        DecoderNames.HIVELY_TRACKER,
        DecoderNames.KLYSTRACK,
        DecoderNames.UADE,
        DecoderNames.VGM_PLAY,
        DecoderNames.AD_PLUG,
        DecoderNames.UFMOD,
        DecoderNames.LIB_DN_FAMITRACKER,
        DecoderNames.LIB_UPSE -> true
        else -> false
    }
}

fun supportsChannelScopeNoteText(coreNameForUi: String?): Boolean {
    return when (pluginNameForCoreName(coreNameForUi)) {
        DecoderNames.LIB_OPEN_MPT,
        DecoderNames.LIBXMP,
        DecoderNames.FURNACE,
        DecoderNames.KLYSTRACK,
        DecoderNames.HIVELY_TRACKER,
        DecoderNames.LIB_DN_FAMITRACKER -> true
        else -> false
    }
}

fun isVisualizationModeSelectable(
    mode: VisualizationMode,
    enabledModes: Set<VisualizationMode>,
    coreNameForUi: String?
): Boolean {
    if (!enabledModes.contains(mode)) return false
    return isVisualizationModeSupported(mode, coreNameForUi)
}
