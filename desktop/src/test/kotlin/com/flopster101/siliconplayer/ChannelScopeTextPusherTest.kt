package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextFields
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextPusher
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextSetters
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelScopeTextPusherTest {

    private class RecordingSetters : ChannelScopeTextSetters {
        val states = mutableListOf<IntArray>()

        override fun setChannelNames(payload: String) = Unit
        override fun setIdentifierNames(instruments: String, samples: String) = Unit
        override fun setTextFields(fields: ChannelScopeTextFields) = Unit
        override fun setTextStates(states: IntArray) {
            this.states += states
        }
        override fun setTextEnabled(enabled: Boolean) = Unit
    }

    private val fields = ChannelScopeTextFields(
        showChannel = true,
        showNote = true,
        showVolume = true,
        showEffectPrimary = true,
        showEffectSecondary = false,
        showChip = false,
        showInstrument = false,
        showSample = false,
        noteFormat = VisualizationNoteNameFormat.American
    )

    private fun push(pusher: ChannelScopeTextPusher, states: IntArray) {
        pusher.pushText(
            enabled = true,
            fields = fields,
            channelNamesByIndex = emptyMap(),
            instrumentNamesByIndex = emptyMap(),
            sampleNamesByIndex = emptyMap()
        )
        pusher.pushStates(states)
    }

    @Test
    fun ignoresEmptyPollAnswers() {
        val setters = RecordingSetters()
        val pusher = ChannelScopeTextPusher(setters)

        push(pusher, intArrayOf(0, 61, 100))
        push(pusher, IntArray(0))
        push(pusher, intArrayOf(0, 61, 100))

        assertEquals(1, setters.states.size)
    }

    @Test
    fun forwardsChangedStatesOnce() {
        val setters = RecordingSetters()
        val pusher = ChannelScopeTextPusher(setters)

        push(pusher, intArrayOf(0, 61, 100))
        push(pusher, intArrayOf(0, 61, 100))
        push(pusher, intArrayOf(0, 62, 100))

        assertEquals(2, setters.states.size)
        assertEquals(62, setters.states[1][1])
    }
}
