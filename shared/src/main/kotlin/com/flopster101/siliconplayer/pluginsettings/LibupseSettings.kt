package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.LibupseDefaults
import com.flopster101.siliconplayer.LibupseOptionKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.storedLibupseReverb

internal class LibupseSettings : PluginSettings {
    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        val prefs = LocalAppPreferences.current
        var reverb by remember {
            mutableStateOf(storedLibupseReverb(prefs))
        }
        fun update(value: Boolean) {
            reverb = value
            prefs.edit().putBoolean(CorePreferenceKeys.LIBUPSE_REVERB, value).apply()
            NativeBridge.setCoreOption(DecoderNames.LIB_UPSE, LibupseOptionKeys.REVERB, value.toString())
        }
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "SPU reverb",
                    description = "PlayStation sound-unit reverberation.",
                    checked = reverb,
                    onCheckedChange = { update(it) }
                )
            }
        }
    }
}
