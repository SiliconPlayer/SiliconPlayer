package com.flopster101.siliconplayer.pluginsettings

import androidx.compose.runtime.Composable
import com.flopster101.siliconplayer.PlayerSettingToggleCard

internal class DnfamitrackerSettings(
    private val seekExact: Boolean,
    private val scopeDcBlock: Boolean,
    private val onSeekExactChanged: (Boolean) -> Unit,
    private val onScopeDcBlockChanged: (Boolean) -> Unit
) : PluginSettings {

    @Composable
    override fun buildSettings(builder: PluginSettingsBuilder) {
        builder.coreOptions {
            custom {
                PlayerSettingToggleCard(
                    title = "Exact seeking",
                    description = "Emulate every tick when seeking. Sample-accurate but takes seconds on long songs.",
                    checked = seekExact,
                    onCheckedChange = onSeekExactChanged
                )
            }
            spacer()
            custom {
                PlayerSettingToggleCard(
                    title = "Scope DC blocker",
                    description = "Center channel scope waveforms with a persistent DC blocker.",
                    checked = scopeDcBlock,
                    onCheckedChange = onScopeDcBlockChanged
                )
            }
        }
    }
}
