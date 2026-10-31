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
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextFields
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextPusher
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextSetters
import com.flopster101.siliconplayer.ui.visualization.channel.loadChannelScopeNameMaps
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
    private var textPusher: ChannelScopeTextPusher? = null

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
                check(surface.prepareTextAtlas(surfaceHandle, visualizerHandle, channelScopePrefs.textFont)) {
                    "Could not prepare the visualization text atlas."
                }
                textPusher = ChannelScopeTextPusher(bridge.channelScopeTextSetters(visualizerHandle))
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
        val pusher = textPusher ?: return
        val capabilities = channelScopeTextCapabilities(decoderName)
        pusher.pushText(
            enabled = channelScopePrefs.textEnabled,
            fields = ChannelScopeTextFields(
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
                noteFormat = channelScopePrefs.textNoteFormat
            ),
            channelNamesByIndex = nameMaps.chipNamesByChannelIndex,
            instrumentNamesByIndex = nameMaps.instrumentNamesByIndex,
            sampleNamesByIndex = nameMaps.sampleNamesByIndex
        )
        if (channelScopePrefs.textEnabled) pusher.pushStates(bridge.getChannelScopeTextState(64))
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
    fun prepareTextAtlas(
        surfaceHandle: Long,
        visualizerHandle: Long,
        font: VisualizationChannelScopeTextFont
    ): Boolean
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
    fun channelScopeTextSetters(visualizerHandle: Long): ChannelScopeTextSetters
    fun destroyVisualizer(visualizerHandle: Long)
}
