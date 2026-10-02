package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeNameSource

internal object DesktopChannelScopeNameSource : ChannelScopeNameSource {
    override fun openMptInstrumentNames(): String = NativeBridge.getOpenMptInstrumentNames()
    override fun openMptSampleNames(): String = NativeBridge.getOpenMptSampleNames()
    override fun xmpInstrumentNames(): String = NativeBridge.getXmpInstrumentNames()
    override fun xmpSampleNames(): String = NativeBridge.getXmpSampleNames()
    override fun furnaceInstrumentNames(): String = NativeBridge.getFurnaceInstrumentNames()
    override fun furnaceSampleNames(): String = NativeBridge.getFurnaceSampleNames()
    override fun klystrackInstrumentNames(): String = NativeBridge.getKlystrackInstrumentNames()
    override fun hivelyInstrumentNames(): String = NativeBridge.getHivelyInstrumentNames()
    override fun dnfamitrackerInstrumentNames(): String = NativeBridge.getDnfamitrackerInstrumentNames()
    override fun dnfamitrackerSampleNames(): String = NativeBridge.getDnfamitrackerSampleNames()
    override fun decoderToggleChannelNames(): Array<String> = NativeBridge.getDecoderToggleChannelNames()
}
