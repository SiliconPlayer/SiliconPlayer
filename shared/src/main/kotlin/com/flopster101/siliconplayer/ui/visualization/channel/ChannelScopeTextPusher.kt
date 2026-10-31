package com.flopster101.siliconplayer.ui.visualization.channel

import com.flopster101.siliconplayer.VisualizationNoteNameFormat

interface ChannelScopeTextSetters {
    fun setChannelNames(payload: String)
    fun setIdentifierNames(instruments: String, samples: String)
    fun setTextFields(fields: ChannelScopeTextFields)
    fun setTextStates(states: IntArray)
    fun setTextEnabled(enabled: Boolean)
}

data class ChannelScopeTextFields(
    val showChannel: Boolean,
    val showNote: Boolean,
    val showVolume: Boolean,
    val showEffectPrimary: Boolean,
    val showEffectSecondary: Boolean,
    val showChip: Boolean,
    val showInstrument: Boolean,
    val showSample: Boolean,
    val noteFormat: VisualizationNoteNameFormat
)

// Native label slots are positional, so index gaps stay as empty entries.
fun channelScopeIndexedNamesPayload(namesByIndex: Map<Int, String>): String {
    if (namesByIndex.isEmpty()) return ""
    val lastIndex = namesByIndex.keys.max()
    if (lastIndex < 0) return ""
    return (0..lastIndex).joinToString("\n") { index ->
        namesByIndex[index].orEmpty().replace('\n', ' ').replace('\r', ' ')
    }
}

// Native note naming: 0 = American, 1 = International.
fun noteNameFormatNativeValue(format: VisualizationNoteNameFormat): Int =
    if (format == VisualizationNoteNameFormat.International) 1 else 0

// Holds the last payload sent so unchanged labels cost nothing per frame.
class ChannelScopeTextPusher(private val setters: ChannelScopeTextSetters) {
    private var channelNames: String? = null
    private var instruments: String? = null
    private var samples: String? = null
    private var fields: ChannelScopeTextFields? = null
    private var states: IntArray? = null
    private var enabled: Boolean? = null

    fun pushText(
        enabled: Boolean,
        fields: ChannelScopeTextFields,
        channelNamesByIndex: Map<Int, String>,
        instrumentNamesByIndex: Map<Int, String>,
        sampleNamesByIndex: Map<Int, String>
    ) {
        if (enabled != this.enabled) {
            this.enabled = enabled
            setters.setTextEnabled(enabled)
        }
        if (fields != this.fields) {
            this.fields = fields
            setters.setTextFields(fields)
        }
        val names = channelScopeIndexedNamesPayload(channelNamesByIndex)
        if (names != channelNames) {
            channelNames = names
            setters.setChannelNames(names)
        }
        val instrumentPayload = channelScopeIndexedNamesPayload(instrumentNamesByIndex)
        val samplePayload = channelScopeIndexedNamesPayload(sampleNamesByIndex)
        if (instrumentPayload != instruments || samplePayload != samples) {
            instruments = instrumentPayload
            samples = samplePayload
            setters.setIdentifierNames(instrumentPayload, samplePayload)
        }
    }

    fun reset() {
        channelNames = null
        instruments = null
        samples = null
        fields = null
        states = null
        enabled = null
    }

    fun pushStates(states: IntArray) {
        if (states.contentEquals(this.states)) return
        this.states = states
        setters.setTextStates(states)
    }
}
