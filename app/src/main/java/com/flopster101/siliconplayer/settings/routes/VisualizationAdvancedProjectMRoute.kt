package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VisualizationAdvancedProjectMRouteContent(
    onOpenPresetPacks: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()

    val enabledSets = remember(context, prefs) { ProjectMPresetSets.enabledSets(context, prefs) }
    var presetCount by remember { mutableStateOf<Int?>(null) }
    var indexing by remember { mutableStateOf(false) }

    fun scanCounts() {
        indexing = true
        scope.launch {
            val total = withContext(Dispatchers.Default) {
                enabledSets.sumOf { ProjectMPresetSets.presetCountFor(it) }
            }
            presetCount = total
            indexing = false
        }
    }

    LaunchedEffect(Unit) {
        scanCounts()
    }

    fun plural(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"

    val packsDescription = "${plural(enabledSets.size, "pack")} · ${presetCount?.let { plural(it, "preset") } ?: "… presets"}"
    val reindexDescription = if (indexing) "Re-indexing…" else "Rescan the enabled preset folders."

    Column {
        SettingsSectionLabel("Presets")
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Configure preset packs",
            description = packsDescription,
            icon = Icons.Default.FolderCopy,
            onClick = onOpenPresetPacks
        )
        SettingsRowSpacer()
        SettingsItemCard(
            title = "Re-index presets",
            description = reindexDescription,
            icon = Icons.Default.Refresh,
            onClick = { scanCounts() }
        )
    }
}
