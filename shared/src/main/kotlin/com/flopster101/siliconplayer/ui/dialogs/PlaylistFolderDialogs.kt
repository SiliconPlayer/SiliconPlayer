package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistFolder
import com.flopster101.siliconplayer.resolveFolderPath

@Composable
internal fun NewFolderDialog(
    existingTitles: Set<String>,
    dialogTitle: String = "New folder",
    confirmText: String = "Create",
    initialTitle: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultTitle = remember(existingTitles, initialTitle) {
        val base = initialTitle?.trim()?.takeIf { it.isNotBlank() } ?: "New folder"
        var candidate = base
        var suffix = 2
        while (candidate in existingTitles) {
            candidate = "$base $suffix"
            suffix += 1
        }
        candidate
    }
    var title by remember(existingTitles, initialTitle) { mutableStateOf(initialTitle?.trim().orEmpty()) }
    FloatingActionDialog(
        title = dialogTitle,
        onDismiss = onDismiss,
        confirmText = confirmText,
        confirmEnabled = true,
        onConfirm = { onConfirm(title.trim().ifBlank { defaultTitle }) }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text(defaultTitle) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun RenameFolderDialog(
    currentTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    val isRenamed = title.isNotBlank() && title.trim() != currentTitle.trim()
    FloatingActionDialog(
        title = "Rename folder",
        onDismiss = onDismiss,
        confirmText = "Rename",
        confirmEnabled = isRenamed,
        onConfirm = { onConfirm(title.trim()) }
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun DeleteFolderDialog(
    folderTitle: String,
    hasContents: Boolean,
    onConfirm: (deletePlaylists: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deletePlaylists by remember { mutableStateOf(false) }

    FloatingActionDialog(
        title = "Delete folder",
        onDismiss = onDismiss,
        confirmText = "Delete",
        confirmIsDestructive = true,
        onConfirm = { onConfirm(deletePlaylists) }
    ) {
        if (!hasContents) {
            Text(
                text = "Are you sure you want to delete \"$folderTitle\"?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Text(
                text = "Choose what to do with the playlists inside \"$folderTitle\":",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSelectableCard(
                    label = "Keep playlists",
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    isSelected = !deletePlaylists,
                    isEnabled = true,
                    subtitle = "Move contained playlists out to the parent level.",
                    onClick = { deletePlaylists = false }
                )
                DialogSelectableCard(
                    label = "Delete folder and playlists",
                    icon = Icons.Default.DeleteForever,
                    isSelected = deletePlaylists,
                    isEnabled = true,
                    subtitle = "Permanently delete the folder and all playlists inside.",
                    onClick = { deletePlaylists = true }
                )
            }
        }
    }
}

@Composable
internal fun MoveToFolderDialog(
    itemTitle: String,
    currentFolderId: String?,
    allFolders: List<PlaylistFolder>,
    disallowedFolderIds: Set<String> = emptySet(),
    onSelectFolder: (targetFolderId: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val folderEntries = remember(allFolders) {
        val rootFolders = allFolders.filter { it.parentFolderId == null }
            .sortedWith(compareByDescending<PlaylistFolder> { it.isPinned }.thenBy { it.title.lowercase() })

        fun buildTree(parentId: String, depth: Int): List<Pair<PlaylistFolder, Int>> {
            val children = allFolders.filter { it.parentFolderId == parentId }
                .sortedWith(compareByDescending<PlaylistFolder> { it.isPinned }.thenBy { it.title.lowercase() })
            val result = mutableListOf<Pair<PlaylistFolder, Int>>()
            for (child in children) {
                result.add(child to depth)
                result.addAll(buildTree(child.id, depth + 1))
            }
            return result
        }

        val result = mutableListOf<Pair<PlaylistFolder, Int>>()
        for (root in rootFolders) {
            result.add(root to 0)
            result.addAll(buildTree(root.id, 1))
        }
        result
    }

    FloatingActionDialog(
        title = "Move to folder",
        onDismiss = onDismiss,
        confirmText = null
    ) {
        Text(
            text = "Select destination for \"$itemTitle\":",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val isRootSelected = currentFolderId == null
            DialogSelectableCard(
                label = "Root (No folder)",
                icon = Icons.Default.Home,
                isSelected = isRootSelected,
                isEnabled = true,
                subtitle = if (isRootSelected) "Current location" else "Move to the main playlists list",
                onClick = {
                    onSelectFolder(null)
                    onDismiss()
                }
            )

            for ((folder, depth) in folderEntries) {
                val isSelected = folder.id == currentFolderId
                val isDisabled = folder.id in disallowedFolderIds
                val path = resolveFolderPath(allFolders, folder.id).map { it.title }.joinToString(" / ")
                val prefix = if (depth > 0) "— ".repeat(depth) else ""
                DialogSelectableCard(
                    label = "$prefix${folder.title}",
                    icon = if (isSelected) Icons.Default.FolderOpen else Icons.Default.Folder,
                    isSelected = isSelected,
                    isEnabled = !isDisabled,
                    subtitle = when {
                        isDisabled -> "Cannot move into self or subfolder"
                        isSelected -> "Current location"
                        else -> path
                    },
                    onClick = {
                        if (!isDisabled) {
                            onSelectFolder(folder.id)
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}
