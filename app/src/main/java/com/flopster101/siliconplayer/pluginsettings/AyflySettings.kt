package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.AyflyConfig
import com.flopster101.siliconplayer.CoreChoiceSelectorCard

internal class AyflySettings(
    private val oversample: Int,
    private val chipType: Int,
    private val mixType: Int,
    private val intFreq: Int,
    private val onOversampleChanged: (Int) -> Unit,
    private val onChipTypeChanged: (Int) -> Unit,
    private val onMixTypeChanged: (Int) -> Unit,
    private val onIntFreqChanged: (Int) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Chip model",
                    description = "Auto keeps the song's chip type; an explicit choice forces the level table.",
                    selectedValue = chipType,
                    options = AyflyConfig.chipChoices,
                    onSelected = onChipTypeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Stereo mix order",
                    description = "Auto keeps the song's channel placement; an explicit choice forces the A/B/C order.",
                    selectedValue = mixType,
                    options = AyflyConfig.mixChoices,
                    onSelected = onMixTypeChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Oversampling",
                    description = "Chip clock oversampling before the output low-pass filter. Higher is cleaner and costs CPU.",
                    selectedValue = oversample,
                    options = AyflyConfig.oversampleChoices,
                    onSelected = onOversampleChanged
                )
            }
            spacer()
            custom {
                CoreChoiceSelectorCard(
                    title = "Interrupt frequency",
                    description = "Frame IRQ rate; Auto follows the song (library default 50 Hz). Override for NTSC or 100 Hz rips; the timeline follows it.",
                    selectedValue = intFreq,
                    options = AyflyConfig.intFreqChoices,
                    onSelected = onIntFreqChanged
                )
            }
        }
    }
}
