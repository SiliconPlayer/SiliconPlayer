package com.flopster101.siliconplayer.ui.visualization.channel

import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationVuAnchor
import kotlin.math.max

data class GlChannelScopeTextPalette(
    val channelArgb: Int = 0xFFCCCCCC.toInt(),
    val noteArgb: Int = 0xFF80D8FF.toInt(),
    val volumeArgb: Int = 0xFFB9F6CA.toInt(),
    val effectArgb: Int = 0xFFFFD180.toInt(),
    val instrumentOrSampleArgb: Int = 0xFFEA80FC.toInt(),
    val separatorArgb: Int = 0x88FFFFFF.toInt()
)

data class GlChannelScopeTextFrame(
    val channelCount: Int,
    val channelTextStates: List<ChannelScopeChannelTextState>,
    val instrumentNamesByIndex: Map<Int, String>,
    val sampleNamesByIndex: Map<Int, String>,
    val chipNamesByChannelIndex: Map<Int, String>,
    val layoutStrategy: VisualizationChannelScopeLayout,
    val anchor: VisualizationChannelScopeTextAnchor,
    val paddingPx: Float,
    val textSizeSp: Int,
    val density: Float,
    val hideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val noteFormat: VisualizationNoteNameFormat,
    val showChannel: Boolean,
    val showNote: Boolean,
    val showVolume: Boolean,
    val showEffectPrimary: Boolean,
    val showEffectSecondary: Boolean,
    val showChip: Boolean,
    val showInstrument: Boolean,
    val showSample: Boolean,
    val palette: GlChannelScopeTextPalette,
    val channelHistories: List<FloatArray> = emptyList(),
    val vuEnabled: Boolean = false,
    val vuAnchor: VisualizationVuAnchor = VisualizationVuAnchor.Bottom,
    val vuColorArgb: Int = 0,
    val vuTrackColorArgb: Int = 0,
    val vuInsetPx: Float = 1f,
    val vuStripHeightPx: Float = 2f
)

data class ChannelScopeTextRun(
    val text: String,
    val xPx: Float,
    val yPx: Float,
    val colorArgb: Int
)

data class ChannelScopeTextLayout(
    val runs: List<ChannelScopeTextRun>,
    val textSizePx: Float,
    val lineHeightPx: Float,
    val shadowEnabled: Boolean
)

interface ChannelScopeTextMeasurer {
    fun widthOf(text: String, textSizePx: Float): Float
    fun lineHeightOf(textSizePx: Float): Float
}

// Generated fallback bullet (U+2022): the bundled scope fonts lack it and
// device fallback metrics vary, so both atlas builders rasterize the same
// centered dot with a normalized advance instead of a fallback face.
const val SCOPE_TEXT_BULLET_ADVANCE_RATIO = 0.375f
const val SCOPE_TEXT_BULLET_RADIUS_RATIO = 0.23f
const val SCOPE_TEXT_BULLET_RAISE_RATIO = 0.25f

// Wire format of the packed channel text states; mirrors NativeBridge on both platforms.
internal const val CHANNEL_SCOPE_TEXT_STATE_STRIDE_SHARED = 10
internal const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT_SHARED = 1 shl 1
internal const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT_SHARED = 1 shl 2

fun layoutChannelScopeText(
    frame: GlChannelScopeTextFrame,
    surfaceWidthPx: Float,
    surfaceHeightPx: Float,
    measurer: ChannelScopeTextMeasurer
): ChannelScopeTextLayout? {
    val channels = frame.channelCount
    if (channels <= 0 || surfaceWidthPx <= 0f || surfaceHeightPx <= 0f) return null
    val (columns, rows) = resolveChannelGrid(channels, frame.layoutStrategy)
    val safeCols = columns.coerceAtLeast(1)
    val safeRows = rows.coerceAtLeast(1)
    val cellWidth = surfaceWidthPx / safeCols.toFloat()
    val cellHeight = surfaceHeightPx / safeRows.toFloat()
    val density = frame.density.coerceAtLeast(1f)
    val cellWidthDp = cellWidth / density
    val paddingDp = (frame.paddingPx / density).coerceAtLeast(2f)
    val selectedTextSizeSp = frame.textSizeSp.coerceIn(6, 32)
    val minimumAutoTextSizeSp = (selectedTextSizeSp - 6).coerceAtLeast(6)
    val effectSlotCount = listOf(frame.showEffectPrimary, frame.showEffectSecondary).count { it }
    val effectiveTextSizeSp = computeAutoChannelScopeTextSizeSp(
        selectedTextSizeSp = selectedTextSizeSp,
        minimumTextSizeSp = minimumAutoTextSizeSp,
        cellWidthDp = cellWidthDp,
        paddingDp = paddingDp,
        showChannel = frame.showChannel,
        showNote = frame.showNote,
        showVolume = frame.showVolume,
        effectSlotCount = effectSlotCount,
        showChip = frame.showChip,
        showInstrument = frame.showInstrument,
        showSample = frame.showSample
    )
    val canRenderAtEffectiveSize = estimateChannelScopeTextWidthDp(
        sp = effectiveTextSizeSp,
        paddingDp = paddingDp,
        showChannel = frame.showChannel,
        showNote = frame.showNote,
        showVolume = frame.showVolume,
        effectSlotCount = effectSlotCount,
        showChip = frame.showChip,
        showInstrument = frame.showInstrument,
        showSample = frame.showSample
    ) <= cellWidthDp
    if (frame.hideWhenOverflow && !canRenderAtEffectiveSize) return null
    val effectiveTextSizePx = effectiveTextSizeSp.toFloat() * density
    val lineHeightPx = measurer.lineHeightOf(effectiveTextSizePx)
    val slotScale = effectiveTextSizeSp.toFloat() / 8f
    val noteSlotWidth = 24f * slotScale * density
    val volumeSlotWidth = 30f * slotScale * density
    val effectSlotWidth = 20f * slotScale * density
    val itemSpacing = 2f * density
    val padding = frame.paddingPx.coerceAtLeast(2f)
    val runs = ArrayList<ChannelScopeTextRun>(channels * 4)
    val sideCounts = IntArray(2)
    for (col in 0 until safeCols) {
        for (row in 0 until safeRows) {
            val channel = (col * safeRows) + row
            if (channel >= channels) continue
            val fields = buildChannelScopeTextFields(
                channel = channel,
                state = frame.channelTextStates.getOrNull(channel),
                instrumentNamesByIndex = frame.instrumentNamesByIndex,
                sampleNamesByIndex = frame.sampleNamesByIndex,
                chipNamesByChannelIndex = frame.chipNamesByChannelIndex,
                noteFormat = frame.noteFormat,
                showChannel = frame.showChannel,
                showNote = frame.showNote,
                showVolume = frame.showVolume,
                showEffectPrimary = frame.showEffectPrimary,
                showEffectSecondary = frame.showEffectSecondary,
                showChip = frame.showChip,
                showInstrument = frame.showInstrument,
                showSample = frame.showSample,
                sideCounts = sideCounts
            )
            runs += layoutChannelScopeCellRuns(
                fields = fields,
                cellLeft = col * cellWidth,
                cellTop = row * cellHeight,
                cellRight = col * cellWidth + cellWidth,
                cellBottom = row * cellHeight + cellHeight,
                anchor = frame.anchor,
                padding = padding,
                textSizePx = effectiveTextSizePx,
                lineHeightPx = lineHeightPx,
                noteSlotWidth = noteSlotWidth,
                volumeSlotWidth = volumeSlotWidth,
                effectSlotWidth = effectSlotWidth,
                itemSpacing = itemSpacing,
                palette = frame.palette,
                measurer = measurer
            )
        }
    }
    return ChannelScopeTextLayout(
        runs = runs,
        textSizePx = effectiveTextSizePx,
        lineHeightPx = lineHeightPx,
        shadowEnabled = frame.textShadowEnabled
    )
}

internal data class ChannelScopeTextFields(
    val channel: String?,
    val note: String?,
    val volume: String?,
    val effects: List<String>,
    val chip: String?,
    val instrumentOrSample: String?
)

internal fun layoutChannelScopeCellRuns(
    fields: ChannelScopeTextFields,
    cellLeft: Float,
    cellTop: Float,
    cellRight: Float,
    cellBottom: Float,
    anchor: VisualizationChannelScopeTextAnchor,
    padding: Float,
    textSizePx: Float,
    lineHeightPx: Float,
    noteSlotWidth: Float,
    volumeSlotWidth: Float,
    effectSlotWidth: Float,
    itemSpacing: Float,
    palette: GlChannelScopeTextPalette,
    measurer: ChannelScopeTextMeasurer
): List<ChannelScopeTextRun> {
    val maxRight = cellRight - padding
    if (cellLeft + padding >= maxRight) return emptyList()
    val originY = when (anchor) {
        VisualizationChannelScopeTextAnchor.TopLeft,
        VisualizationChannelScopeTextAnchor.TopCenter,
        VisualizationChannelScopeTextAnchor.TopRight -> cellTop + padding
        VisualizationChannelScopeTextAnchor.BottomLeft,
        VisualizationChannelScopeTextAnchor.BottomCenter,
        VisualizationChannelScopeTextAnchor.BottomRight -> cellBottom - lineHeightPx - padding
    }
    val runs = ArrayList<ChannelScopeTextRun>(8)
    var cursorX = cellLeft + padding
    var hasPrevious = false
    // Trailing air of the last placed run; centered slot text leaves
    // margins on both sides, left-aligned text (approx.) none.
    var prevTrail = 0f
    // Center the dot in the visual gap: slot margins would otherwise
    // strand it against one neighbor. Stays in [prevEnd, nextStart].
    fun drawSeparator(nextLead: Float = 0f) {
        if (!hasPrevious || cursorX >= maxRight) return
        val bullet = "•"
        val sepWidth = measurer.widthOf(bullet, textSizePx)
        if (cursorX + sepWidth + itemSpacing > maxRight) return
        val lower = cursorX - itemSpacing
        val upper = max(lower, cursorX + itemSpacing + nextLead)
        val bulletX = (cursorX + (nextLead - prevTrail) / 2f).coerceIn(lower, upper)
        runs += ChannelScopeTextRun(bullet, bulletX, originY, palette.separatorArgb)
        cursorX += sepWidth + itemSpacing
    }
    if (fields.channel != null && cursorX < maxRight) {
        val remainingW = maxRight - cursorX
        val channelText = truncateChannelScopeText(fields.channel, measurer, textSizePx, remainingW)
        if (channelText != null) {
            runs += ChannelScopeTextRun(channelText, cursorX, originY, palette.channelArgb)
            cursorX += measurer.widthOf(channelText, textSizePx) + itemSpacing
            hasPrevious = true
            prevTrail = 0f
        }
    }
    if (fields.note != null) {
        val noteLead = (noteSlotWidth - measurer.widthOf(fields.note, textSizePx)) * 0.5f
        drawSeparator(noteLead)
        if (cursorX + noteSlotWidth <= maxRight) {
            val textX = cursorX + noteLead
            runs += ChannelScopeTextRun(fields.note, textX, originY, palette.noteArgb)
            cursorX += noteSlotWidth + itemSpacing
            hasPrevious = true
            prevTrail = noteLead
        }
    }
    if (fields.volume != null) {
        val volumeLead = (volumeSlotWidth - measurer.widthOf(fields.volume, textSizePx)) * 0.5f
        drawSeparator(volumeLead)
        if (cursorX + volumeSlotWidth <= maxRight) {
            val textX = cursorX + volumeLead
            runs += ChannelScopeTextRun(fields.volume, textX, originY, palette.volumeArgb)
            cursorX += volumeSlotWidth + itemSpacing
            hasPrevious = true
            prevTrail = volumeLead
        }
    }
    for (eff in fields.effects) {
        val effLead = (effectSlotWidth - measurer.widthOf(eff, textSizePx)) * 0.5f
        drawSeparator(effLead)
        if (cursorX + effectSlotWidth <= maxRight) {
            val textX = cursorX + effLead
            runs += ChannelScopeTextRun(eff, textX, originY, palette.effectArgb)
            cursorX += effectSlotWidth + itemSpacing
            hasPrevious = true
            prevTrail = effLead
        }
    }
    if (fields.chip != null) {
        drawSeparator()
        val remainingW = maxRight - cursorX
        if (remainingW > textSizePx / 4f) {
            val ellipsized = truncateChannelScopeText(fields.chip, measurer, textSizePx, remainingW)
            if (ellipsized != null) {
                runs += ChannelScopeTextRun(ellipsized, cursorX, originY, palette.channelArgb)
                cursorX += measurer.widthOf(ellipsized, textSizePx) + itemSpacing
                hasPrevious = true
                prevTrail = 0f
            }
        }
    }
    if (fields.instrumentOrSample != null) {
        drawSeparator()
        val remainingW = maxRight - cursorX
        if (remainingW > textSizePx / 4f) {
            val ellipsized = truncateChannelScopeText(fields.instrumentOrSample, measurer, textSizePx, remainingW)
            if (ellipsized != null) {
                runs += ChannelScopeTextRun(ellipsized, cursorX, originY, palette.instrumentOrSampleArgb)
            }
        }
    }
    return runs
}

internal fun truncateChannelScopeText(
    text: String,
    measurer: ChannelScopeTextMeasurer,
    textSizePx: Float,
    maxWidth: Float
): String? {
    if (maxWidth <= 0f) return null
    if (measurer.widthOf(text, textSizePx) <= maxWidth) return text
    val ellipsis = "…"
    if (measurer.widthOf(ellipsis, textSizePx) > maxWidth) return null
    val availableForChars = maxWidth - measurer.widthOf(ellipsis, textSizePx)
    var len = text.length - 1
    while (len > 0) {
        val sub = text.substring(0, len)
        if (measurer.widthOf(sub, textSizePx) <= availableForChars) return sub + ellipsis
        len--
    }
    return null
}

internal fun computeAutoChannelScopeTextSizeSp(
    selectedTextSizeSp: Int,
    minimumTextSizeSp: Int,
    cellWidthDp: Float,
    paddingDp: Float,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    effectSlotCount: Int,
    showChip: Boolean,
    showInstrument: Boolean,
    showSample: Boolean
): Int {
    val selected = selectedTextSizeSp.coerceIn(6, 32)
    val minimum = minimumTextSizeSp.coerceAtMost(selected).coerceAtLeast(6)
    val availableWidth = cellWidthDp.coerceAtLeast(0f)
    if (
        estimateChannelScopeTextWidthDp(
            sp = selected,
            paddingDp = paddingDp,
            showChannel = showChannel,
            showNote = showNote,
            showVolume = showVolume,
            effectSlotCount = effectSlotCount,
            showChip = showChip,
            showInstrument = showInstrument,
            showSample = showSample
        ) <= availableWidth
    ) {
        return selected
    }
    var size = selected
    while (
        size > minimum &&
        estimateChannelScopeTextWidthDp(
            sp = size,
            paddingDp = paddingDp,
            showChannel = showChannel,
            showNote = showNote,
            showVolume = showVolume,
            effectSlotCount = effectSlotCount,
            showChip = showChip,
            showInstrument = showInstrument,
            showSample = showSample
        ) > availableWidth
    ) {
        size--
    }
    return size
}

internal fun estimateChannelScopeTextWidthDp(
    sp: Int,
    paddingDp: Float,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    effectSlotCount: Int,
    showChip: Boolean,
    showInstrument: Boolean,
    showSample: Boolean
): Float {
    val scale = sp.toFloat() / 8f
    var fieldCount = 0
    var width = 0f
    if (showChannel) {
        width += 26f * scale
        fieldCount++
    }
    if (showNote) {
        width += 24f * scale
        fieldCount++
    }
    if (showVolume) {
        width += 30f * scale
        fieldCount++
    }
    repeat(effectSlotCount.coerceAtLeast(0)) {
        width += 20f * scale
        fieldCount++
    }
    if (showChip) {
        width += 60f * scale
        fieldCount++
    }
    if (showInstrument || showSample) {
        width += if (showInstrument && showSample) 48f * scale else 28f * scale
        fieldCount++
    }
    val separators = (fieldCount - 1).coerceAtLeast(0)
    width += separators * (8f * scale)
    width += separators * 3f
    width += paddingDp * 2f
    width += 4f
    return width
}

internal fun buildChannelScopeTextFields(
    channel: Int,
    state: ChannelScopeChannelTextState?,
    instrumentNamesByIndex: Map<Int, String>,
    sampleNamesByIndex: Map<Int, String>,
    chipNamesByChannelIndex: Map<Int, String>,
    noteFormat: VisualizationNoteNameFormat,
    showChannel: Boolean,
    showNote: Boolean,
    showVolume: Boolean,
    showEffectPrimary: Boolean,
    showEffectSecondary: Boolean,
    showChip: Boolean,
    showInstrument: Boolean,
    showSample: Boolean,
    sideCounts: IntArray
): ChannelScopeTextFields {
    val effects = ArrayList<String>(2)
    if (showEffectPrimary) {
        effects += formatChannelScopeEffect(
            state?.effectPrimaryLetterAscii ?: 0,
            state?.effectPrimaryParam ?: -1
        )
    }
    if (showEffectSecondary) {
        effects += formatChannelScopeEffect(
            state?.effectSecondaryLetterAscii ?: 0,
            state?.effectSecondaryParam ?: -1
        )
    }
    val channelLabel = if (showChannel) {
        resolveChannelScopeLabel(channel, state, sideCounts, chipNamesByChannelIndex)
    } else null
    val chipLabel = if (showChip) {
        formatChannelScopeChipName(channel, state, chipNamesByChannelIndex)
    } else null
    return ChannelScopeTextFields(
        channel = channelLabel,
        note = if (showNote) (formatChannelScopeNoteName(state?.note ?: -1, noteFormat) ?: "--") else null,
        volume = if (showVolume) formatChannelScopeVolume(state?.volume ?: 0) else null,
        effects = effects,
        chip = chipLabel?.takeUnless { it == channelLabel },
        instrumentOrSample = if (showInstrument || showSample) {
            formatChannelScopeInstrumentOrSample(state, instrumentNamesByIndex, sampleNamesByIndex, showInstrument, showSample)
        } else null
    )
}

internal fun resolveChannelScopeLabel(
    channel: Int,
    state: ChannelScopeChannelTextState?,
    sideCounts: IntArray,
    channelNamesByChannelIndex: Map<Int, String>
): String {
    val preferredIndex = state?.channelIndex ?: channel
    val explicitName = channelNamesByChannelIndex[preferredIndex] ?: channelNamesByChannelIndex[channel]
    if (!explicitName.isNullOrBlank()) return explicitName
    val flags = state?.flags ?: 0
    val isLeft = (flags and CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT_SHARED) != 0
    val isRight = (flags and CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT_SHARED) != 0
    if (isLeft) {
        sideCounts[0]++
        return if (sideCounts[0] <= 2) "L${sideCounts[0]}" else "Ch ${channel + 1}"
    }
    if (isRight) {
        sideCounts[1]++
        return if (sideCounts[1] <= 2) "R${sideCounts[1]}" else "Ch ${channel + 1}"
    }
    return "Ch ${channel + 1}"
}

internal fun formatChannelScopeNoteName(note: Int, format: VisualizationNoteNameFormat): String? {
    if (note <= 0) return null
    val idx = (note - 1) % 12
    val octave = (note - 1) / 12
    val names = if (format == VisualizationNoteNameFormat.International) {
        arrayOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
    } else {
        arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }
    return "${names[idx]}$octave"
}

internal fun formatChannelScopeVolume(volume: Int): String {
    return "V" + volume.coerceIn(0, 999).toString().padStart(3, '0')
}

internal fun formatChannelScopeEffect(effectLetterAscii: Int, effectParam: Int): String {
    if (effectLetterAscii <= 0 || effectParam < 0) return "---"
    if (effectLetterAscii >= 0x100) {
        val codeHex = (effectLetterAscii and 0xFF).toString(16).uppercase().padStart(2, '0')
        val paramHex = effectParam.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
        return codeHex + paramHex
    }
    val effectChar = effectLetterAscii.toChar()
    val paramHex = effectParam.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    return "$effectChar$paramHex"
}

internal fun formatChannelScopeInstrumentOrSample(
    state: ChannelScopeChannelTextState?,
    instrumentNamesByIndex: Map<Int, String>,
    sampleNamesByIndex: Map<Int, String>,
    showInstrument: Boolean,
    showSample: Boolean
): String? {
    if (state == null) return null
    val parts = ArrayList<String>(2)
    if (showInstrument && state.instrumentIndex > 0) {
        val name = instrumentNamesByIndex[state.instrumentIndex].orEmpty()
        parts += if (name.isNotBlank()) "I#${state.instrumentIndex} $name" else "I#${state.instrumentIndex}"
    }
    if (showSample && state.sampleIndex > 0) {
        val name = sampleNamesByIndex[state.sampleIndex].orEmpty()
        parts += if (name.isNotBlank()) "S#${state.sampleIndex} $name" else "S#${state.sampleIndex}"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

internal fun formatChannelScopeChipName(
    channel: Int,
    state: ChannelScopeChannelTextState?,
    chipNamesByChannelIndex: Map<Int, String>
): String? {
    val preferredIndex = state?.channelIndex ?: channel
    return (chipNamesByChannelIndex[preferredIndex] ?: chipNamesByChannelIndex[channel])?.takeIf { it.isNotBlank() }
}

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
    return when (pluginName) {
        com.flopster101.siliconplayer.DecoderNames.LIB_OPEN_MPT -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.openMptInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.openMptSampleNames())
        )
        com.flopster101.siliconplayer.DecoderNames.LIBXMP -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.xmpInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.xmpSampleNames())
        )
        com.flopster101.siliconplayer.DecoderNames.AYFLY,
        com.flopster101.siliconplayer.DecoderNames.GAME_MUSIC_EMU,
        com.flopster101.siliconplayer.DecoderNames.C_RSID,
        com.flopster101.siliconplayer.DecoderNames.LIB_SID_PLAY_FP,
        com.flopster101.siliconplayer.DecoderNames.VGM_PLAY,
        com.flopster101.siliconplayer.DecoderNames.LIB_UPSE,
        com.flopster101.siliconplayer.DecoderNames.VIOGSF -> ChannelScopeNameMaps(
            chipNamesByChannelIndex = source.decoderToggleChannelNames()
                .mapIndexed { index, name -> index to name }
                .toMap()
        )
        com.flopster101.siliconplayer.DecoderNames.FURNACE -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.furnaceInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.furnaceSampleNames()),
            chipNamesByChannelIndex = source.decoderToggleChannelNames()
                .mapIndexed { index, name -> index to name }
                .toMap()
        )
        com.flopster101.siliconplayer.DecoderNames.KLYSTRACK -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.klystrackInstrumentNames())
        )
        com.flopster101.siliconplayer.DecoderNames.LIB_DN_FAMITRACKER -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.dnfamitrackerInstrumentNames()),
            sampleNamesByIndex = parseChannelScopeIndexedNames(source.dnfamitrackerSampleNames()),
            chipNamesByChannelIndex = source.decoderToggleChannelNames()
                .mapIndexed { index, name -> index to name }
                .toMap()
        )
        com.flopster101.siliconplayer.DecoderNames.HIVELY_TRACKER -> ChannelScopeNameMaps(
            instrumentNamesByIndex = parseChannelScopeIndexedNames(source.hivelyInstrumentNames())
        )
        else -> ChannelScopeNameMaps()
    }
}
