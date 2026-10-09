package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.ViogsfOptionKeys
import com.flopster101.siliconplayer.storedViogsfFilteringPercent
import com.flopster101.siliconplayer.storedViogsfInterpolation

internal class ViogsfSettings : PluginSettings {
    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        val prefs = LocalAppPreferences.current
        var interpolation by remember {
            mutableStateOf(storedViogsfInterpolation(prefs))
        }
        var filteringPercent by remember {
            mutableStateOf(storedViogsfFilteringPercent(prefs))
        }
        fun updateInterpolation(value: Boolean) {
            interpolation = value
            prefs.edit().putBoolean(CorePreferenceKeys.VIOGSF_INTERPOLATION, value).apply()
            NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.INTERPOLATION, value.toString())
        }
        fun updateFiltering(value: Int) {
            filteringPercent = value
            prefs.edit().putInt(CorePreferenceKeys.VIOGSF_FILTERING, value).apply()
            NativeBridge.setCoreOption(DecoderNames.VIOGSF, ViogsfOptionKeys.FILTERING, (value / 100.0).toString())
        }
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "PCM interpolation",
                    description = "Smooths Direct Sound output. Off sounds harsher.",
                    checked = interpolation,
                    onCheckedChange = { updateInterpolation(it) }
                )
            }
            spacer()
            custom {
                CoreDialogSliderCard(
                    title = "PCM filtering",
                    description = "Low-pass filter on Direct Sound output.",
                    value = filteringPercent,
                    valueRange = 0..100,
                    step = 5,
                    valueLabel = { "$it%" },
                    onValueChanged = { updateFiltering(it) }
                )
            }
        }
    }
}
