package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.CoreChoiceSelectorCard
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.CoreDialogSliderCard
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.NezplugppConfig
import com.flopster101.siliconplayer.NezplugppDefaults
import com.flopster101.siliconplayer.NezplugppOptionKeys
import com.flopster101.siliconplayer.storedNezplugppFilter
import com.flopster101.siliconplayer.storedNezplugppVolumeDb

internal class NezplugppSettings : PluginSettings {
    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        val prefs = LocalAppPreferences.current
        var filter by remember {
            mutableStateOf(storedNezplugppFilter(prefs))
        }
        fun updateFilter(value: Int) {
            filter = value
            prefs.edit().putInt(CorePreferenceKeys.NEZPLUGPP_FILTER, value).apply()
            NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, NezplugppOptionKeys.FILTER, value.toString())
        }
        builder.coreOptions {
            custom {
                CoreChoiceSelectorCard(
                    title = "Output filter",
                    description = "Post-mix filter on the emulated output.",
                    selectedValue = filter,
                    options = NezplugppConfig.filterChoices,
                    onSelected = { updateFilter(it) }
                )
            }
            volumeSlider("KSS volume", "Trim applied to KSS and KSCC songs.", CorePreferenceKeys.NEZPLUGPP_VOLUME_KSS_DB, NezplugppDefaults.volumeKssDb, NezplugppOptionKeys.VOLUME_KSS)
            volumeSlider("NSF volume", "Trim applied to NES songs. NSF rips tend to run quiet.", CorePreferenceKeys.NEZPLUGPP_VOLUME_NSF_DB, NezplugppDefaults.volumeNsfDb, NezplugppOptionKeys.VOLUME_NSF)
            volumeSlider("GBS volume", "Trim applied to Game Boy songs. GBS rips tend to run quiet.", CorePreferenceKeys.NEZPLUGPP_VOLUME_GBS_DB, NezplugppDefaults.volumeGbsDb, NezplugppOptionKeys.VOLUME_GBS)
            volumeSlider("HES volume", "Trim applied to PC Engine songs.", CorePreferenceKeys.NEZPLUGPP_VOLUME_HES_DB, NezplugppDefaults.volumeHesDb, NezplugppOptionKeys.VOLUME_HES)
            volumeSlider("SGC volume", "Trim applied to Master System, Game Gear, and ColecoVision songs.", CorePreferenceKeys.NEZPLUGPP_VOLUME_SGC_DB, NezplugppDefaults.volumeSgcDb, NezplugppOptionKeys.VOLUME_SGC)
            volumeSlider("NSD volume", "Trim applied to NSD songs.", CorePreferenceKeys.NEZPLUGPP_VOLUME_NSD_DB, NezplugppDefaults.volumeNsdDb, NezplugppOptionKeys.VOLUME_NSD)
            volumeSlider("AY volume", "Trim applied to ZX Spectrum songs.", CorePreferenceKeys.NEZPLUGPP_VOLUME_AY_DB, NezplugppDefaults.volumeAyDb, NezplugppOptionKeys.VOLUME_AY)
        }
    }

    private fun PluginSettingsSectionBuilder.volumeSlider(
        title: String,
        description: String,
        prefKey: String,
        defaultDb: Int,
        optionKey: String
    ) {
        spacer()
        custom {
            val prefs = LocalAppPreferences.current
            var db by remember(prefKey) {
                mutableStateOf(storedNezplugppVolumeDb(prefs, prefKey, defaultDb))
            }
            fun update(value: Int) {
                db = value
                prefs.edit().putInt(prefKey, value).apply()
                NativeBridge.setCoreOption(DecoderNames.NEZPLUGPP, optionKey, value.toString())
            }
            CoreDialogSliderCard(
                title = title,
                description = description,
                value = db,
                valueRange = -20..20,
                step = 1,
                valueLabel = { "${if (it > 0) "+" else ""}${it} dB" },
                onValueChanged = { update(it) }
            )
        }
    }
}
