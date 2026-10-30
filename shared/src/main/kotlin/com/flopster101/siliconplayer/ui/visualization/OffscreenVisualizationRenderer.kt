package com.flopster101.siliconplayer.ui.visualization

import com.flopster101.siliconplayer.ChannelScopeTextCapability
import com.flopster101.siliconplayer.ChannelScopeVisibleElementId
import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationOscTriggerMode
import com.flopster101.siliconplayer.channelScopeTextCapabilities
import com.flopster101.siliconplayer.isChannelScopeVisibleElementEnabled
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.ui.screens.ChannelScopePrefs
import com.flopster101.siliconplayer.ui.screens.ChannelScopePrefsSupport
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameSource
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import com.flopster101.siliconplayer.ui.visualization.channel.loadChannelScopeNameMaps
import com.flopster101.siliconplayer.ui.visualization.channel.parseChannelScopeTextStates
import java.nio.ByteBuffer

class OffscreenVisualizationRenderer(
    private val mode: VisualizationMode,
    private val prefs: AppPreferences,
    private val decoderName: String,
    private val width: Int,
    private val height: Int,
    private val surface: OffscreenVisualizationSurface,
    private val bridge: OffscreenVisualizationBridge,
    private val nameSource: ChannelScopeNameSource? = null,
    channelScopeAntialiasMethod: VisualizationChannelScopeAntialiasMethod? = null,
    channelScopeTriggerMode: VisualizationOscTriggerMode? = null,
    private val asyncReadback: Boolean = false
) : AutoCloseable {
    private val density = height / 720f
    private val channelScopePrefs = ChannelScopePrefsSupport.from(prefs).let {
        val withAntialias = channelScopeAntialiasMethod?.let { method -> it.copy(antialiasMethod = method) } ?: it
        channelScopeTriggerMode?.let { mode -> withAntialias.copy(triggerModeNative = mode.nativeValue) }
            ?: withAntialias
    }
    private val nameMaps by lazy {
        loadChannelScopeNameMaps(pluginNameForCoreName(decoderName), requireNotNull(nameSource))
    }
    private var visualizerHandle = 0L
    private var surfaceHandle = 0L
    private var textEmitter: ScopeTextQuadEmitter? = null

    init {
        try {
            bridge.setVisualizationActive(mode, decoderName, true)
            visualizerHandle = bridge.createVisualizer()
            check(visualizerHandle != 0L) { "Could not create the visualizer." }
            bridge.attachAudioEngine(visualizerHandle)
            bridge.configureVisualization(visualizerHandle, mode, prefs, channelScopePrefs, density)
            surfaceHandle = surface.initialize(visualizerHandle)
            check(surfaceHandle != 0L) { "Could not initialize an offscreen visualization surface." }
            if (mode == VisualizationMode.ChannelScope && channelScopePrefs.textEnabled) {
                textEmitter = surface.createTextEmitter(surfaceHandle, channelScopePrefs.textFont)
                check(textEmitter != null) { "Could not create the visualization text renderer." }
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun renderFrame(pixels: ByteBuffer, captureOutput: Boolean = true): Boolean {
        if (mode == VisualizationMode.ChannelScope) updateChannelScopeText()
        pixels.clear()
        return surface.renderFrame(
            surfaceHandle, visualizerHandle, width, height, density, pixels, captureOutput, asyncReadback
        )
    }

    override fun close() {
        if (surfaceHandle != 0L && visualizerHandle != 0L) {
            surface.close(surfaceHandle, visualizerHandle)
            surfaceHandle = 0L
        }
        if (visualizerHandle != 0L) {
            bridge.destroyVisualizer(visualizerHandle)
            visualizerHandle = 0L
        }
        bridge.setVisualizationActive(mode, decoderName, false)
    }

    private fun updateChannelScopeText() {
        if (!channelScopePrefs.textEnabled || textEmitter == null) {
            surface.setTextQuads(surfaceHandle, null)
            return
        }
        val states = parseChannelScopeTextStates(bridge.getChannelScopeTextState(64))
        if (states.isEmpty()) {
            surface.setTextQuads(surfaceHandle, null)
            return
        }
        val capabilities = channelScopeTextCapabilities(decoderName)
        val frame = GlChannelScopeTextFrame(
            channelCount = states.size,
            channelTextStates = states,
            instrumentNamesByIndex = nameMaps.instrumentNamesByIndex,
            sampleNamesByIndex = nameMaps.sampleNamesByIndex,
            chipNamesByChannelIndex = nameMaps.chipNamesByChannelIndex,
            layoutStrategy = channelScopePrefs.layout,
            anchor = channelScopePrefs.textAnchor,
            paddingPx = channelScopePrefs.textPaddingDp * density,
            textSizeSp = channelScopePrefs.textSizeSp,
            density = density,
            hideWhenOverflow = channelScopePrefs.textHideWhenOverflow,
            textShadowEnabled = channelScopePrefs.textShadowEnabled,
            textFont = channelScopePrefs.textFont,
            noteFormat = channelScopePrefs.textNoteFormat,
            showChannel = channelScopePrefs.textShowChannel,
            showNote = channelScopePrefs.textShowNote && capabilities and ChannelScopeTextCapability.NOTE != 0,
            showVolume = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Volume
            ),
            showEffectPrimary = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.EffectPrimary
            ),
            showEffectSecondary = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.EffectSecondary
            ),
            showChip = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Chip
            ),
            showInstrument = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Instrument
            ),
            showSample = isChannelScopeVisibleElementEnabled(
                channelScopePrefs.textVisibleElementSelection, decoderName, ChannelScopeVisibleElementId.Sample
            ),
            palette = textPalette(
                channelScopePrefs.textColorMode, channelScopePrefs.customTextColorArgb
            )
        )
        val emitter = textEmitter ?: return
        val layout = layoutChannelScopeText(frame, width.toFloat(), height.toFloat(), emitter.measurer)
        val quads = layout?.let { emitter.emitRuns(it.runs, it.textSizePx, it.shadowEnabled) }
        surface.setTextQuads(surfaceHandle, quads)
    }
}

fun interface OffscreenVisualizationRendererFactory {
    fun create(
        mode: VisualizationMode,
        prefs: AppPreferences,
        decoderName: String,
        width: Int,
        height: Int,
        channelScopeAntialiasMethod: VisualizationChannelScopeAntialiasMethod?,
        channelScopeTriggerMode: VisualizationOscTriggerMode?
    ): OffscreenVisualizationRenderer
}

interface OffscreenVisualizationSurface {
    fun initialize(visualizerHandle: Long): Long
    fun renderFrame(
        surfaceHandle: Long,
        visualizerHandle: Long,
        width: Int,
        height: Int,
        density: Float,
        pixels: ByteBuffer,
        captureOutput: Boolean,
        asyncReadback: Boolean
    ): Boolean

    fun close(surfaceHandle: Long, visualizerHandle: Long)
    fun uploadTextAtlas(
        surfaceHandle: Long,
        pixels: ByteBuffer,
        width: Int,
        height: Int,
        baseFontSizePx: Float,
        lineHeightPx: Float,
        glyphs: ByteBuffer,
        glyphCount: Int
    ): Boolean

    fun setTextQuads(surfaceHandle: Long, quads: FloatArray?)
    fun createTextEmitter(
        surfaceHandle: Long,
        font: VisualizationChannelScopeTextFont
    ): ScopeTextQuadEmitter?
}

interface OffscreenVisualizationBridge {
    fun setVisualizationActive(mode: VisualizationMode, decoderName: String, active: Boolean)
    fun createVisualizer(): Long
    fun attachAudioEngine(visualizerHandle: Long)
    fun configureVisualization(
        visualizerHandle: Long,
        mode: VisualizationMode,
        prefs: AppPreferences,
        channelScopePrefs: ChannelScopePrefs,
        density: Float
    )

    fun getChannelScopeTextState(maxChannels: Int): IntArray
    fun destroyVisualizer(visualizerHandle: Long)
}

private fun textPalette(
    mode: com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode,
    custom: Int
): GlChannelScopeTextPalette = when (mode) {
    com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode.White -> GlChannelScopeTextPalette(
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()
    )
    com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode.Custom -> GlChannelScopeTextPalette(
        custom, custom, custom, custom, custom, custom
    )
    com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode.Monet -> GlChannelScopeTextPalette(
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()
    )
    com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode.OpenMptInspired -> GlChannelScopeTextPalette()
}
