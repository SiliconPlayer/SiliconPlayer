package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class UfmodSettings : PluginSettings {
    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        val context = LocalContext.current
        val prefs = remember(context) {
            context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        }
        var flags by remember {
            mutableIntStateOf(prefs.getInt(CorePreferenceKeys.UFMOD_QUIRKS, 0))
        }
        LaunchedEffect(Unit) {
            NativeBridge.setCoreOption(DecoderNames.UFMOD, "ufmod.quirks", flags.toString())
        }
        fun update(value: Int) {
            flags = value
            prefs.edit().putInt(CorePreferenceKeys.UFMOD_QUIRKS, value).apply()
            NativeBridge.setCoreOption(DecoderNames.UFMOD, "ufmod.quirks", value.toString())
        }
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "Unclamped global volume slide",
                    description = "Preserves volume-slide behavior above the normal XM range.",
                    checked = flags and 1 != 0,
                    onCheckedChange = { update(if (it) flags or 1 else flags and 1.inv()) }
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Persistent looping voices",
                    description = "Keeps looping voices active after their note volume reaches zero.",
                    checked = flags and 2 != 0,
                    onCheckedChange = { update(if (it) flags or 2 else flags and 2.inv()) }
                )
            }
        }
    }
}
