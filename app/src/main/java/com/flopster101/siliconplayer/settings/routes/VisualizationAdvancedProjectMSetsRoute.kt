package com.flopster101.siliconplayer

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSet
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import java.io.File

@Composable
internal fun VisualizationProjectMSetsRouteContent() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("silicon_player_settings", Context.MODE_PRIVATE)
    }

    var sets by remember { mutableStateOf(ProjectMPresetSets.allSets(context, prefs)) }
    var enabledIds by remember { mutableStateOf(ProjectMPresetSets.enabledSetIds(prefs)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<ProjectMPresetSet?>(null) }

    fun refresh() {
        sets = ProjectMPresetSets.allSets(context, prefs)
        enabledIds = ProjectMPresetSets.enabledSetIds(prefs)
    }

    fun toggle(set: ProjectMPresetSet, enabled: Boolean) {
        if (enabled) {
            if (ProjectMPresetSets.setEnabled(prefs, set.id, true)) refresh()
        } else if (enabledIds.size > 1) {
            // The last enabled pack can't be switched off.
            if (ProjectMPresetSets.setEnabled(prefs, set.id, false)) refresh()
        }
    }

    SettingsSectionLabel("Preset packs")
    SettingsRowSpacer()
    sets.forEachIndexed { index, set ->
        val enabled = enabledIds.contains(set.id)
        val isLastEnabled = enabled && enabledIds.size <= 1
        SettingsRowContainer(
            onClick = { toggle(set, !enabled) },
            enabled = !isLastEnabled
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = set.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = set.dir,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!set.isInternal) {
                IconButton(onClick = { pendingRemove = set }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove preset folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
            Switch(
                checked = enabled,
                onCheckedChange = { toggle(set, it) },
                enabled = !isLastEnabled
            )
        }
        if (index < sets.lastIndex) {
            SettingsRowSpacer()
        }
    }

    SettingsRowSpacer()
    SettingsItemCard(
        title = "Add preset folder",
        description = "Enter a path to a folder of MilkDrop presets.",
        icon = Icons.Default.Folder,
        onClick = { showAddDialog = true }
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "At least one pack must stay enabled. Removing a folder does not delete it from storage.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp)
    )

    if (showAddDialog) {
        SettingsTextInputDialog(
            title = "Add preset folder",
            fieldLabel = "Folder path",
            initialValue = "",
            placeholder = "/storage/emulated/0/Music/presets",
            supportingText = "The path must exist and be a directory.",
            onDismiss = { showAddDialog = false },
            onConfirm = { input ->
                val trimmed = input.trim()
                if (trimmed.isEmpty()) return@SettingsTextInputDialog false
                val dir = File(trimmed)
                if (!dir.isDirectory) return@SettingsTextInputDialog false
                ProjectMPresetSets.addUserSet(prefs, trimmed)
                refresh()
                true
            },
            confirmLabel = "Add"
        )
    }

    pendingRemove?.let { set ->
        SettingsConfirmDialog(
            title = "Remove preset folder?",
            message = "\"${set.label}\" will no longer be sampled. The folder is not deleted from storage.",
            confirmLabel = "Remove",
            onDismiss = { pendingRemove = null },
            onConfirm = {
                ProjectMPresetSets.removeUserSet(prefs, set.id)
                pendingRemove = null
                refresh()
            }
        )
    }
}
