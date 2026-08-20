package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import android.opengl.GLES20
import com.flopster101.siliconplayer.VisualizationVuAnchor
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Palette colors (ARGB integers) for OpenGL channel scope text rendering.
 */
data class GlChannelScopeTextPalette(
    val channelArgb: Int,
    val noteArgb: Int,
    val volumeArgb: Int,
    val effectArgb: Int,
    val instrumentOrSampleArgb: Int,
    val separatorArgb: Int
)

/**
 * Frame parameters for channel scope text rendering in OpenGL.
 */
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

/**
 * High-performance batched OpenGL text and mini VU meter renderer for Channel Scope view.
 */
internal class GlChannelScopeTextRenderer(private val context: Context) {
    private var fontAtlas: GlFontAtlas? = null
    private var currentFont: VisualizationChannelScopeTextFont? = null
    private val textProgram = GlTextProgram()
    private val batchBuilder = GlTextBatchBuilder(1024)
    private var textVertexBuffer: FloatBuffer? = null
    private var vertexCount: Int = 0
    private var vuProgram = 0
    private var vuPositionLoc = -1
    private var vuColorLoc = -1
    private var currentFrame: GlChannelScopeTextFrame? = null

    fun onSurfaceCreated() {
        textProgram.init()
        vuProgram = GlSimplePrimitives.createProgram()
        vuPositionLoc = GLES20.glGetAttribLocation(vuProgram, "aPosition")
        vuColorLoc = GLES20.glGetUniformLocation(vuProgram, "uColor")
    }

    fun release() {
        textProgram.release()
        fontAtlas?.release()
        fontAtlas = null
        currentFont = null
        textVertexBuffer = null
        vertexCount = 0
        currentFrame = null
        if (vuProgram != 0) {
            GLES20.glDeleteProgram(vuProgram)
            vuProgram = 0
        }
    }

    private fun ensureAtlas(font: VisualizationChannelScopeTextFont) {
        if (fontAtlas != null && currentFont == font) return
        fontAtlas?.release()
        val typeface = resolveTypeface(context, font)
        val atlas = GlFontAtlas(typeface = typeface, baseFontSizePx = 32f)
        atlas.initGl()
        fontAtlas = atlas
        currentFont = font
    }

    fun buildGeometry(
        frame: GlChannelScopeTextFrame,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        currentFrame = frame
        val channels = frame.channelCount
        if (channels <= 0 || surfaceWidth <= 0f || surfaceHeight <= 0f) {
            vertexCount = 0
            return
        }

        ensureAtlas(frame.textFont)
        val atlas = fontAtlas ?: return

        val (columns, rows) = resolveChannelGrid(channels, frame.layoutStrategy)
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = surfaceWidth / safeCols.toFloat()
        val cellHeight = surfaceHeight / safeRows.toFloat()

        val density = frame.density.coerceAtLeast(1f)
        val cellWidthDp = cellWidth / density
        val paddingDp = (frame.paddingPx / density).coerceAtLeast(2f)
        val selectedTextSizeSp = frame.textSizeSp.coerceIn(6, 22)
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

        if (frame.hideWhenOverflow && !canRenderAtEffectiveSize) {
            vertexCount = 0
            return
        }

        val effectiveTextSizePx = effectiveTextSizeSp.toFloat() * density
        val scale = effectiveTextSizePx / atlas.baseFontSizePx
        val lineHeightPx = atlas.lineHeightPx * scale
        val slotScale = effectiveTextSizeSp.toFloat() / 8f
        val noteSlotWidth = 24f * slotScale * density
        val volumeSlotWidth = 30f * slotScale * density
        val effectSlotWidth = 20f * slotScale * density
        val itemSpacing = 2f * density
        val padding = frame.paddingPx.coerceAtLeast(2f)

        batchBuilder.clear()
        val sideCounts = IntArray(2)

        for (col in 0 until safeCols) {
            for (row in 0 until safeRows) {
                val channel = (col * safeRows) + row
                if (channel >= channels) continue

                val cellLeft = col * cellWidth
                val cellTop = row * cellHeight
                val cellRight = cellLeft + cellWidth
                val cellBottom = cellTop + cellHeight

                val fields = buildChannelFields(
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

                renderChannelCellText(
                    atlas = atlas,
                    fields = fields,
                    cellLeft = cellLeft,
                    cellTop = cellTop,
                    cellRight = cellRight,
                    cellBottom = cellBottom,
                    anchor = frame.anchor,
                    padding = padding,
                    scale = scale,
                    noteSlotWidth = noteSlotWidth,
                    volumeSlotWidth = volumeSlotWidth,
                    effectSlotWidth = effectSlotWidth,
                    itemSpacing = itemSpacing,
                    lineHeightPx = lineHeightPx,
                    palette = frame.palette,
                    shadow = frame.textShadowEnabled
                )
            }
        }

        vertexCount = batchBuilder.count
        if (vertexCount > 0) {
            textVertexBuffer = batchBuilder.uploadToBuffer(textVertexBuffer)
        }
    }

    fun drawVu(surfaceWidth: Float, surfaceHeight: Float) {
        val frame = currentFrame ?: return
        if (frame.vuEnabled && vuProgram != 0 && frame.channelHistories.isNotEmpty()) {
            drawVuBars(surfaceWidth, surfaceHeight, frame)
        }
    }

    fun drawText(surfaceWidth: Float, surfaceHeight: Float) {
        val atlas = fontAtlas ?: return
        val buffer = textVertexBuffer ?: return
        if (vertexCount <= 0 || !textProgram.isReady) return

        textProgram.draw(
            buffer = buffer,
            vertexCount = vertexCount,
            atlas = atlas,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight
        )
    }

    fun draw(surfaceWidth: Float, surfaceHeight: Float) {
        drawVu(surfaceWidth, surfaceHeight)
        drawText(surfaceWidth, surfaceHeight)
    }

    private fun drawVuBars(
        surfaceWidth: Float,
        surfaceHeight: Float,
        frame: GlChannelScopeTextFrame
    ) {
        val channels = frame.channelCount
        if (channels <= 0 || surfaceWidth <= 0f || surfaceHeight <= 0f) return
        val (columns, rows) = resolveChannelGrid(channels, frame.layoutStrategy)
        val safeCols = columns.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        val cellWidth = surfaceWidth / safeCols.toFloat()
        val cellHeight = surfaceHeight / safeRows.toFloat()
        val inset = frame.vuInsetPx.coerceAtLeast(1f)
        val h = frame.vuStripHeightPx.coerceAtLeast(1f)

        GLES20.glUseProgram(vuProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        for (col in 0 until safeCols) {
            for (row in 0 until safeRows) {
                val channel = (col * safeRows) + row
                if (channel >= channels) continue

                val cellLeft = col * cellWidth
                val cellTop = row * cellHeight
                val cellBottom = cellTop + cellHeight

                val usableWidth = (cellWidth - (inset * 2f)).coerceAtLeast(0f)
                val trackX = cellLeft + inset
                val trackY = if (frame.vuAnchor == VisualizationVuAnchor.Top) {
                    cellTop + inset
                } else {
                    (cellBottom - h - inset).coerceAtLeast(cellTop)
                }

                // 1. Draw track
                val trackVertices = GlSimplePrimitives.rectToTrianglesNdc(
                    x = trackX,
                    y = trackY,
                    w = usableWidth,
                    h = h,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight
                )
                GlSimplePrimitives.drawTriangles(trackVertices, frame.vuTrackColorArgb, vuPositionLoc, vuColorLoc)

                // 2. Draw level fill
                val history = frame.channelHistories.getOrNull(channel)
                val vuLevel = if (history != null) computeChannelScopeVuLevel(history) else 0f
                val fillWidth = (usableWidth * vuLevel).coerceAtLeast(0f)
                if (fillWidth > 0f) {
                    val fillVertices = GlSimplePrimitives.rectToTrianglesNdc(
                        x = trackX,
                        y = trackY,
                        w = fillWidth,
                        h = h,
                        surfaceWidth = surfaceWidth,
                        surfaceHeight = surfaceHeight
                    )
                    GlSimplePrimitives.drawTriangles(fillVertices, frame.vuColorArgb, vuPositionLoc, vuColorLoc)
                }
            }
        }
    }

    private fun computeChannelScopeVuLevel(history: FloatArray): Float {
        if (history.isEmpty()) return 0f
        var peak = 0f
        var i = 0
        val step = max(1, history.size / 64)
        while (i < history.size) {
            val sample = kotlin.math.abs(history[i])
            if (sample > peak) peak = sample
            i += step
        }
        return peak.coerceIn(0f, 1f)
    }

    private fun renderChannelCellText(
        atlas: GlFontAtlas,
        fields: GlChannelTextFields,
        cellLeft: Float,
        cellTop: Float,
        cellRight: Float,
        cellBottom: Float,
        anchor: VisualizationChannelScopeTextAnchor,
        padding: Float,
        scale: Float,
        noteSlotWidth: Float,
        volumeSlotWidth: Float,
        effectSlotWidth: Float,
        itemSpacing: Float,
        lineHeightPx: Float,
        palette: GlChannelScopeTextPalette,
        shadow: Boolean
    ) {
        val maxRight = cellRight - padding
        if (cellLeft + padding >= maxRight) return

        val paddingX = padding
        val paddingTop = (padding * 0.42f).coerceAtLeast(1f)
        val paddingBottom = padding

        val originY = when (anchor) {
            VisualizationChannelScopeTextAnchor.TopLeft,
            VisualizationChannelScopeTextAnchor.TopCenter,
            VisualizationChannelScopeTextAnchor.TopRight -> cellTop + paddingTop
            VisualizationChannelScopeTextAnchor.BottomLeft,
            VisualizationChannelScopeTextAnchor.BottomCenter,
            VisualizationChannelScopeTextAnchor.BottomRight -> cellBottom - lineHeightPx - paddingBottom
        }

        var cursorX = cellLeft + paddingX
        var hasPrevious = false

        fun drawSeparator() {
            if (!hasPrevious || cursorX >= maxRight) return
            val bullet = "•"
            val sepWidth = atlas.measureTextWidth(bullet, scale)
            if (cursorX + sepWidth + itemSpacing > maxRight) return
            val a = ((palette.separatorArgb ushr 24) and 0xFF) / 255f
            val r = ((palette.separatorArgb ushr 16) and 0xFF) / 255f
            val g = ((palette.separatorArgb ushr 8) and 0xFF) / 255f
            val b = (palette.separatorArgb and 0xFF) / 255f
            batchBuilder.addText(
                atlas = atlas,
                text = bullet,
                startX = cursorX,
                startY = originY,
                scale = scale,
                r = r, g = g, b = b, a = a,
                shadow = shadow,
                maxWidthPx = (maxRight - cursorX).coerceAtLeast(0f)
            )
            cursorX += sepWidth + itemSpacing
        }

        // 1. Channel
        if (fields.channel != null) {
            if (cursorX < maxRight) {
                val a = ((palette.channelArgb ushr 24) and 0xFF) / 255f
                val r = ((palette.channelArgb ushr 16) and 0xFF) / 255f
                val g = ((palette.channelArgb ushr 8) and 0xFF) / 255f
                val b = (palette.channelArgb and 0xFF) / 255f
                val drawnWidth = batchBuilder.addText(
                    atlas = atlas,
                    text = fields.channel,
                    startX = cursorX,
                    startY = originY,
                    scale = scale,
                    r = r, g = g, b = b, a = a,
                    shadow = shadow,
                    maxWidthPx = (maxRight - cursorX).coerceAtLeast(0f)
                )
                cursorX += drawnWidth + itemSpacing
                hasPrevious = true
            }
        }

        // 2. Note (Centered in noteSlotWidth)
        if (fields.note != null) {
            drawSeparator()
            if (cursorX + noteSlotWidth <= maxRight) {
                val a = ((palette.noteArgb ushr 24) and 0xFF) / 255f
                val r = ((palette.noteArgb ushr 16) and 0xFF) / 255f
                val g = ((palette.noteArgb ushr 8) and 0xFF) / 255f
                val b = (palette.noteArgb and 0xFF) / 255f
                val textW = atlas.measureTextWidth(fields.note, scale)
                val textX = cursorX + (noteSlotWidth - textW) * 0.5f
                batchBuilder.addText(
                    atlas = atlas,
                    text = fields.note,
                    startX = textX,
                    startY = originY,
                    scale = scale,
                    r = r, g = g, b = b, a = a,
                    shadow = shadow,
                    maxWidthPx = (maxRight - textX).coerceAtLeast(0f)
                )
                cursorX += noteSlotWidth + itemSpacing
                hasPrevious = true
            }
        }

        // 3. Volume (Centered in volumeSlotWidth)
        if (fields.volume != null) {
            drawSeparator()
            if (cursorX + volumeSlotWidth <= maxRight) {
                val a = ((palette.volumeArgb ushr 24) and 0xFF) / 255f
                val r = ((palette.volumeArgb ushr 16) and 0xFF) / 255f
                val g = ((palette.volumeArgb ushr 8) and 0xFF) / 255f
                val b = (palette.volumeArgb and 0xFF) / 255f
                val textW = atlas.measureTextWidth(fields.volume, scale)
                val textX = cursorX + (volumeSlotWidth - textW) * 0.5f
                batchBuilder.addText(
                    atlas = atlas,
                    text = fields.volume,
                    startX = textX,
                    startY = originY,
                    scale = scale,
                    r = r, g = g, b = b, a = a,
                    shadow = shadow,
                    maxWidthPx = (maxRight - textX).coerceAtLeast(0f)
                )
                cursorX += volumeSlotWidth + itemSpacing
                hasPrevious = true
            }
        }

        // 4. Effects (Each centered in effectSlotWidth)
        for (eff in fields.effects) {
            drawSeparator()
            if (cursorX + effectSlotWidth <= maxRight) {
                val a = ((palette.effectArgb ushr 24) and 0xFF) / 255f
                val r = ((palette.effectArgb ushr 16) and 0xFF) / 255f
                val g = ((palette.effectArgb ushr 8) and 0xFF) / 255f
                val b = (palette.effectArgb and 0xFF) / 255f
                val textW = atlas.measureTextWidth(eff, scale)
                val textX = cursorX + (effectSlotWidth - textW) * 0.5f
                batchBuilder.addText(
                    atlas = atlas,
                    text = eff,
                    startX = textX,
                    startY = originY,
                    scale = scale,
                    r = r, g = g, b = b, a = a,
                    shadow = shadow,
                    maxWidthPx = (maxRight - textX).coerceAtLeast(0f)
                )
                cursorX += effectSlotWidth + itemSpacing
                hasPrevious = true
            }
        }

        // 5. Chip name (Ellipsized to remaining width)
        if (fields.chip != null) {
            drawSeparator()
            val remainingW = maxRight - cursorX
            if (remainingW > 8f * scale) {
                val ellipsized = truncateWithEllipsis(fields.chip, atlas, scale, remainingW)
                if (ellipsized != null) {
                    val a = ((palette.channelArgb ushr 24) and 0xFF) / 255f
                    val r = ((palette.channelArgb ushr 16) and 0xFF) / 255f
                    val g = ((palette.channelArgb ushr 8) and 0xFF) / 255f
                    val b = (palette.channelArgb and 0xFF) / 255f
                    val drawnWidth = batchBuilder.addText(
                        atlas = atlas,
                        text = ellipsized,
                        startX = cursorX,
                        startY = originY,
                        scale = scale,
                        r = r, g = g, b = b, a = a,
                        shadow = shadow,
                        maxWidthPx = remainingW
                    )
                    cursorX += drawnWidth + itemSpacing
                    hasPrevious = true
                }
            }
        }

        // 6. Instrument / Sample (Ellipsized to remaining width)
        if (fields.instrumentOrSample != null) {
            drawSeparator()
            val remainingW = maxRight - cursorX
            if (remainingW > 8f * scale) {
                val ellipsized = truncateWithEllipsis(fields.instrumentOrSample, atlas, scale, remainingW)
                if (ellipsized != null) {
                    val a = ((palette.instrumentOrSampleArgb ushr 24) and 0xFF) / 255f
                    val r = ((palette.instrumentOrSampleArgb ushr 16) and 0xFF) / 255f
                    val g = ((palette.instrumentOrSampleArgb ushr 8) and 0xFF) / 255f
                    val b = (palette.instrumentOrSampleArgb and 0xFF) / 255f
                    batchBuilder.addText(
                        atlas = atlas,
                        text = ellipsized,
                        startX = cursorX,
                        startY = originY,
                        scale = scale,
                        r = r, g = g, b = b, a = a,
                        shadow = shadow,
                        maxWidthPx = remainingW
                    )
                }
            }
        }
    }

    private fun truncateWithEllipsis(
        text: String,
        atlas: GlFontAtlas,
        scale: Float,
        maxWidth: Float
    ): String? {
        if (maxWidth <= 0f) return null
        val fullWidth = atlas.measureTextWidth(text, scale)
        if (fullWidth <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = atlas.measureTextWidth(ellipsis, scale)
        if (ellipsisWidth > maxWidth) return null
        val availableForChars = maxWidth - ellipsisWidth
        var len = text.length - 1
        while (len > 0) {
            val sub = text.substring(0, len)
            if (atlas.measureTextWidth(sub, scale) <= availableForChars) {
                return sub + ellipsis
            }
            len--
        }
        return null
    }

    private fun computeAutoChannelScopeTextSizeSp(
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
        val selected = selectedTextSizeSp.coerceIn(6, 22)
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

    private fun estimateChannelScopeTextWidthDp(
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

    private data class GlChannelTextFields(
        val channel: String?,
        val note: String?,
        val volume: String?,
        val effects: List<String>,
        val chip: String?,
        val instrumentOrSample: String?
    )

    private fun buildChannelFields(
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
    ): GlChannelTextFields {
        val effects = ArrayList<String>(2)
        if (showEffectPrimary) {
            effects += formatEffect(
                state?.effectPrimaryLetterAscii ?: 0,
                state?.effectPrimaryParam ?: -1
            )
        }
        if (showEffectSecondary) {
            effects += formatEffect(
                state?.effectSecondaryLetterAscii ?: 0,
                state?.effectSecondaryParam ?: -1
            )
        }
        val channelLabel = if (showChannel) {
            resolveChannelLabel(channel, state, sideCounts, chipNamesByChannelIndex)
        } else null

        val chipLabel = if (showChip) {
            formatChipName(channel, state, chipNamesByChannelIndex)
        } else null

        return GlChannelTextFields(
            channel = channelLabel,
            note = if (showNote) (formatNoteName(state?.note ?: -1, noteFormat) ?: "--") else null,
            volume = if (showVolume) formatVolume(state?.volume ?: 0) else null,
            effects = effects,
            chip = chipLabel?.takeUnless { it == channelLabel },
            instrumentOrSample = if (showInstrument || showSample) {
                formatInstrumentOrSample(state, instrumentNamesByIndex, sampleNamesByIndex, showInstrument, showSample)
            } else null
        )
    }

    private fun resolveChannelLabel(
        channel: Int,
        state: ChannelScopeChannelTextState?,
        sideCounts: IntArray,
        channelNamesByChannelIndex: Map<Int, String>
    ): String {
        val preferredIndex = state?.channelIndex ?: channel
        val explicitName = channelNamesByChannelIndex[preferredIndex] ?: channelNamesByChannelIndex[channel]
        if (!explicitName.isNullOrBlank()) return explicitName

        val flags = state?.flags ?: 0
        val isLeft = (flags and NativeBridge.CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT) != 0
        val isRight = (flags and NativeBridge.CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT) != 0
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

    private fun formatNoteName(note: Int, format: VisualizationNoteNameFormat): String? {
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

    private fun formatVolume(volume: Int): String {
        return "V" + volume.coerceIn(0, 999).toString().padStart(3, '0')
    }

    private fun formatEffect(effectLetterAscii: Int, effectParam: Int): String {
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

    private fun formatInstrumentOrSample(
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

    private fun formatChipName(
        channel: Int,
        state: ChannelScopeChannelTextState?,
        chipNamesByChannelIndex: Map<Int, String>
    ): String? {
        val preferredIndex = state?.channelIndex ?: channel
        return (chipNamesByChannelIndex[preferredIndex] ?: chipNamesByChannelIndex[channel])?.takeIf { it.isNotBlank() }
    }

    private fun resolveChannelGrid(channels: Int, strategy: VisualizationChannelScopeLayout): Pair<Int, Int> {
        if (channels <= 1) return 1 to 1
        return when (strategy) {
            VisualizationChannelScopeLayout.ColumnFirst -> {
                val targetRows = 7
                val cols = if (channels <= 4) 1 else ceil(channels / targetRows.toDouble()).toInt().coerceAtLeast(2)
                val rows = ceil(channels / cols.toDouble()).toInt().coerceAtLeast(1)
                cols to rows
            }
            VisualizationChannelScopeLayout.BalancedTwoColumn -> {
                val cols = ceil(kotlin.math.sqrt(channels.toDouble())).toInt().coerceAtLeast(1)
                val rows = ceil(channels / cols.toDouble()).toInt().coerceAtLeast(1)
                cols to rows
            }
        }
    }

    private fun resolveTypeface(context: Context, font: VisualizationChannelScopeTextFont): Typeface {
        return when (font) {
            VisualizationChannelScopeTextFont.System -> Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RaccoonSerif -> ResourcesCompat.getFont(context, R.font.raccoon_serif_base) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RaccoonMono -> ResourcesCompat.getFont(context, R.font.raccoon_serif_mono) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RetroCuteMono -> ResourcesCompat.getFont(context, R.font.retro_pixel_cute_mono) ?: Typeface.MONOSPACE
            VisualizationChannelScopeTextFont.RetroThick -> ResourcesCompat.getFont(context, R.font.retro_pixel_thick) ?: Typeface.MONOSPACE
        }
    }
}
