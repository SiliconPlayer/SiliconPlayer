package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun VisualizationTrackTickerRouteContent() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }

    var masterEnabled by remember {
        mutableStateOf(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED, true))
    }
    var durationSeconds by remember {
        mutableIntStateOf(
            prefs.getInt(AppPreferenceKeys.VISUALIZATION_TICKER_DURATION_SECONDS, 5).coerceIn(2, 15)
        )
    }

    PlayerSettingToggleCard(
        title = "Show track ticker",
        description = "Show the new track's title, artist and format when the song changes in fullscreen.",
        checked = masterEnabled,
        onCheckedChange = { enabled ->
            masterEnabled = enabled
            prefs.edit().putBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED, enabled).apply()
        }
    )
    SettingsRowSpacer()

    var showDurationDialog by remember { mutableStateOf(false) }
    SettingsItemCard(
        title = "Ticker duration",
        description = "$durationSeconds seconds",
        icon = Icons.Default.Timer,
        onClick = { if (masterEnabled) showDurationDialog = true }
    )
    if (showDurationDialog) {
        SettingsSingleChoiceDialog(
            title = "Ticker duration",
            selectedValue = durationSeconds,
            options = listOf(2, 3, 5, 8, 10, 15).map { seconds ->
                ChoiceDialogOption(
                    value = seconds,
                    label = if (seconds == 5) "5 seconds (default)" else "$seconds seconds"
                )
            },
            onSelected = { seconds ->
                durationSeconds = seconds
                prefs.edit()
                    .putInt(AppPreferenceKeys.VISUALIZATION_TICKER_DURATION_SECONDS, seconds)
                    .apply()
                showDurationDialog = false
            },
            onDismiss = { showDurationDialog = false }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Per visualizer")
    SettingsRowSpacer()

    val perVisualizer = listOf(
        Triple("Bars", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_BARS, "visualization_ticker_enabled_bars"),
        Triple("Oscilloscope", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_OSCILLOSCOPE, "visualization_ticker_enabled_oscilloscope"),
        Triple("VU meters", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_VU_METERS, "visualization_ticker_enabled_vu_meters"),
        Triple("Channel scope", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_CHANNEL_SCOPE, "visualization_ticker_enabled_channel_scope"),
        Triple("Starfield", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_STARFIELD, "visualization_ticker_enabled_starfield"),
        Triple("projectM", AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_PROJECTM, "visualization_ticker_enabled_projectm")
    )
    perVisualizer.forEachIndexed { index, (label, key, _) ->
        var enabledForVis by remember(key) {
            mutableStateOf(prefs.getBoolean(key, true))
        }
        PlayerSettingToggleCard(
            title = label,
            description = "Show the track ticker in fullscreen $label.",
            checked = enabledForVis,
            enabled = masterEnabled,
            onCheckedChange = { enabled ->
                enabledForVis = enabled
                prefs.edit().putBoolean(key, enabled).apply()
            }
        )
        if (index < perVisualizer.lastIndex) SettingsRowSpacer()
    }
}
