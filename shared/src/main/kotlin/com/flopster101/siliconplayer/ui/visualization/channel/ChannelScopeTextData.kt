package com.flopster101.siliconplayer.ui.visualization.channel

import kotlinx.coroutines.delay

data class GlChannelScopeTextPalette(
    val channelArgb: Int = 0xFFCCCCCC.toInt(),
    val noteArgb: Int = 0xFF80D8FF.toInt(),
    val volumeArgb: Int = 0xFFB9F6CA.toInt(),
    val effectArgb: Int = 0xFFFFD180.toInt(),
    val instrumentOrSampleArgb: Int = 0xFFEA80FC.toInt(),
    val separatorArgb: Int = 0x88FFFFFF.toInt()
)

// Generated fallback bullet (U+2022): the bundled scope fonts lack it and
// device fallback metrics vary, so both atlas builders rasterize the same
// centered dot with a normalized advance instead of a fallback face.
const val SCOPE_TEXT_BULLET_ADVANCE_RATIO = 0.375f
const val SCOPE_TEXT_BULLET_RADIUS_RATIO = 0.23f
const val SCOPE_TEXT_BULLET_RAISE_RATIO = 0.25f

// Wire format of the packed channel text states; mirrors NativeBridge on both platforms.
internal const val CHANNEL_SCOPE_TEXT_STATE_STRIDE_SHARED = 10

fun parseChannelScopeTextStates(flat: IntArray): List<ChannelScopeChannelTextState> {
    val stride = CHANNEL_SCOPE_TEXT_STATE_STRIDE_SHARED
    if (stride <= 0 || flat.isEmpty()) return emptyList()
    val channels = flat.size / stride
    if (channels <= 0) return emptyList()
    return List(channels) { channel ->
        val base = channel * stride
        ChannelScopeChannelTextState(
            channelIndex = flat.getOrElse(base + 0) { channel },
            note = flat.getOrElse(base + 1) { -1 },
            volume = flat.getOrElse(base + 2) { 0 },
            effectPrimaryLetterAscii = flat.getOrElse(base + 3) { 0 },
            effectPrimaryParam = flat.getOrElse(base + 4) { -1 },
            effectSecondaryLetterAscii = flat.getOrElse(base + 5) { 0 },
            effectSecondaryParam = flat.getOrElse(base + 6) { -1 },
            instrumentIndex = flat.getOrElse(base + 7) { -1 },
            sampleIndex = flat.getOrElse(base + 8) { -1 },
            flags = flat.getOrElse(base + 9) { 0 }
        )
    }
}

internal fun parseChannelScopeIndexedNames(raw: String): Map<Int, String> {
    if (raw.isBlank()) return emptyMap()
    val out = LinkedHashMap<Int, String>()
    raw.lineSequence().forEach { lineRaw ->
        val line = lineRaw.trim()
        if (line.isEmpty()) return@forEach
        val dotIndex = line.indexOf(". ")
        if (dotIndex <= 0) return@forEach
        val index = line.substring(0, dotIndex).toIntOrNull() ?: return@forEach
        val name = line.substring(dotIndex + 2).trim()
        if (index > 0) {
            out[index] = name
        }
    }
    return out
}

interface ChannelScopeNameSource {
    fun openMptInstrumentNames(): String
    fun openMptSampleNames(): String
    fun xmpInstrumentNames(): String
    fun xmpSampleNames(): String
    fun furnaceInstrumentNames(): String
    fun furnaceSampleNames(): String
    fun klystrackInstrumentNames(): String
    fun hivelyInstrumentNames(): String
    fun dnfamitrackerInstrumentNames(): String
    fun dnfamitrackerSampleNames(): String
    fun decoderToggleChannelNames(): Array<String>
}

data class ChannelScopeNameMaps(
    val instrumentNamesByIndex: Map<Int, String> = emptyMap(),
    val sampleNamesByIndex: Map<Int, String> = emptyMap(),
    val chipNamesByChannelIndex: Map<Int, String> = emptyMap()
)

fun loadChannelScopeNameMaps(pluginName: String?, source: ChannelScopeNameSource): ChannelScopeNameMaps {
    // Chip voice names come from the decoder itself: any core currently
    // reporting toggle names gets them as row labels.
    val chipNamesByChannelIndex = source.decoderToggleChannelNames()
        .mapIndexedNotNull { index, name -> index.takeIf { name.isNotBlank() }?.let { index to name } }
        .toMap()
    return when (pluginName) {
        com.flopster101.siliconplayer.DecoderNames.LIB_OPEN_MPT -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.openMptInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.openMptSampleNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        com.flopster101.siliconplayer.DecoderNames.LIBXMP -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.xmpInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.xmpSampleNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        com.flopster101.siliconplayer.DecoderNames.FURNACE -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.furnaceInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.furnaceSampleNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        com.flopster101.siliconplayer.DecoderNames.KLYSTRACK -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.klystrackInstrumentNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        com.flopster101.siliconplayer.DecoderNames.LIB_DN_FAMITRACKER -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.dnfamitrackerInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.dnfamitrackerSampleNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        com.flopster101.siliconplayer.DecoderNames.HIVELY_TRACKER -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.hivelyInstrumentNames()),
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
        else -> ChannelScopeNameMaps(
            chipNamesByChannelIndex = chipNamesByChannelIndex
        )
    }
}

private const val CHANNEL_SCOPE_NAME_RETRY_COUNT = 40
private const val CHANNEL_SCOPE_NAME_RETRY_DELAY_MS = 250L

// Names only exist once the decoder is open and its mutex is free, so keep
// asking until they arrive instead of pinning generic labels.
suspend fun loadChannelScopeNameMapsWhenReady(
    pluginName: String?,
    source: ChannelScopeNameSource
): ChannelScopeNameMaps? {
    repeat(CHANNEL_SCOPE_NAME_RETRY_COUNT) { attempt ->
        val maps = loadChannelScopeNameMaps(pluginName, source)
        if (maps.chipNamesByChannelIndex.isNotEmpty()) return maps
        if (attempt < CHANNEL_SCOPE_NAME_RETRY_COUNT - 1) delay(CHANNEL_SCOPE_NAME_RETRY_DELAY_MS)
    }
    return null
}
