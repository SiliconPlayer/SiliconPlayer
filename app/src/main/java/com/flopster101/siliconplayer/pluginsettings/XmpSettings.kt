package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.XmpConfig

internal class XmpSettings(
    private val interpolation: Int,
    private val stereoSeparationPercent: Int,
    private val amigaStereoSeparationPercent: Int,
    private val amigaMixing: Boolean,
    private val onInterpolationChanged: (Int) -> Unit,
    private val onStereoSeparationPercentChanged: (Int) -> Unit,
    private val onAmigaStereoSeparationPercentChanged: (Int) -> Unit,
    private val onAmigaMixingChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Interpolation",
                    description = "Sample interpolation used by the mixer.",
                    selectedValue = interpolation,
                    options = XmpConfig.interpolationChoices,
                    onSelected = onInterpolationChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Stereo separation",
                    description = "Sets mixer stereo separation. Negative values reverse stereo.",
                    value = stereoSeparationPercent,
                    valueRange = -100..100,
                    step = 5,
                    valueLabel = { "$it%" },
                    onValueChanged = onStereoSeparationPercentChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Amiga 500 mixing",
                    description = "Uses the Amiga 500 Paula mixer model for Amiga MODs, including the LED lowpass filter when the module enables it.",
                    checked = amigaMixing,
                    onCheckedChange = onAmigaMixingChanged
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "Amiga stereo separation",
                    description = "Stereo separation used specifically for Amiga modules.",
                    value = amigaStereoSeparationPercent,
                    valueRange = -100..100,
                    step = 5,
                    valueLabel = { "$it%" },
                    onValueChanged = onAmigaStereoSeparationPercentChanged
                )
            }
        }
    }
}
