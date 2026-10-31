package com.flopster101.siliconplayer.ui.visualization.gl

import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextFields
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeTextSetters
import com.flopster101.siliconplayer.ui.visualization.channel.noteNameFormatNativeValue

internal class AndroidChannelScopeTextSetters : ChannelScopeTextSetters {
    var handle: Long = 0L

    override fun setChannelNames(payload: String) {
        SiliconVisNativeBridge.nativeSetChannelScopeChannelNames(handle, payload)
    }

    override fun setIdentifierNames(instruments: String, samples: String) {
        SiliconVisNativeBridge.nativeSetChannelScopeIdentifierNames(handle, instruments, samples)
    }

    override fun setTextFields(fields: ChannelScopeTextFields) {
        SiliconVisNativeBridge.nativeSetChannelScopeTextFields(
            handle = handle,
            showChannel = fields.showChannel,
            showNote = fields.showNote,
            showVolume = fields.showVolume,
            showEffectPrimary = fields.showEffectPrimary,
            showEffectSecondary = fields.showEffectSecondary,
            showChip = fields.showChip,
            showInstrument = fields.showInstrument,
            showSample = fields.showSample,
            noteFormat = noteNameFormatNativeValue(fields.noteFormat)
        )
    }

    override fun setTextStates(states: IntArray) {
        SiliconVisNativeBridge.nativeSetChannelScopeTextStates(handle, states)
    }

    override fun setTextEnabled(enabled: Boolean) {
        SiliconVisNativeBridge.nativeSetChannelScopeTextEnabled(handle, enabled)
    }
}
