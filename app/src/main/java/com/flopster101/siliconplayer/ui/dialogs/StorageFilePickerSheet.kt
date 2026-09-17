package com.flopster101.siliconplayer.ui.dialogs

import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flopster101.siliconplayer.StorageDescriptor
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.detectStorageDescriptors
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.ui.screens.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageFilePickerSheet(
    title: String = "Select File",
    initialDirectory: File? = null,
    singleSelect: Boolean = true,
    fileFilter: (File) -> Boolean = { true },
    fileIcon: ImageVector = Icons.Default.MusicNote,
    emptyText: String = "No folders or supported files",
    onConfirmFiles: (List<File>) -> Unit,
    onDismiss: () -> Unit
) {
    val content = @Composable {
        StorageFilePickerContent(
            title = title,
            initialDirectory = initialDirectory,
            singleSelect = singleSelect,
            fileFilter = fileFilter,
            fileIcon = fileIcon,
            emptyText = emptyText,
            onConfirmFiles = onConfirmFiles,
            onDismiss = onDismiss
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.85f)
                        .padding(top = 48.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun StorageFilePickerDialog(
    title: String = "Select File",
    initialDirectory: File? = null,
    singleSelect: Boolean = true,
    fileFilter: (File) -> Boolean = { true },
    fileIcon: ImageVector = Icons.Default.MusicNote,
    emptyText: String = "No folders or supported files",
    onConfirmFiles: (List<File>) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = adaptiveDialogProperties()
    ) {
        Card(
            modifier = adaptiveDialogModifier()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp)
        ) {
            StorageFilePickerContent(
                title = title,
                initialDirectory = initialDirectory,
                singleSelect = singleSelect,
                fileFilter = fileFilter,
                fileIcon = fileIcon,
                emptyText = emptyText,
                onConfirmFiles = onConfirmFiles,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun StorageFilePickerContent(
    title: String,
    initialDirectory: File?,
    singleSelect: Boolean,
    fileFilter: (File) -> Boolean,
    fileIcon: ImageVector,
    emptyText: String,
    onConfirmFiles: (List<File>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val descriptors = remember(context) { detectStorageDescriptors(context) }
    val initialDir = remember(descriptors, initialDirectory) {
        if (initialDirectory != null && initialDirectory.exists() && initialDirectory.canRead()) {
            if (initialDirectory.isDirectory) initialDirectory else initialDirectory.parentFile ?: initialDirectory
        } else {
            val preferred = descriptors.firstOrNull { it.rootPath != "/" }?.rootPath
                ?: Environment.getExternalStorageDirectory().absolutePath
            val file = File(preferred)
            if (file.exists() && file.canRead()) file else Environment.getExternalStorageDirectory()
        }
    }

    var currentDirectory by remember { mutableStateOf(initialDir) }
    val selectedFiles = remember { mutableStateMapOf<String, File>() }
    var directoryFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val canGoUp = currentDirectory.parentFile != null && currentDirectory.absolutePath != "/"

    BackHandler(enabled = canGoUp) {
        val parent = currentDirectory.parentFile
        if (parent != null && parent.canRead()) {
            currentDirectory = parent
        }
    }

    LaunchedEffect(currentDirectory, fileFilter) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val rawFiles = try {
                currentDirectory.listFiles().orEmpty()
            } catch (_: Exception) {
                emptyArray()
            }
            val dirs = rawFiles.filter { it.isDirectory && !it.name.startsWith(".") }
                .sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            val matchingFiles = rawFiles.filter {
                !it.isDirectory && !it.name.startsWith(".") && fileFilter(it)
            }.sortedWith { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
            directoryFiles = dirs + matchingFiles
            isLoading = false
        }
    }

    val matchingFilesInDir = remember(directoryFiles) { directoryFiles.filter { !it.isDirectory } }
    val allInDirSelected = matchingFilesInDir.isNotEmpty() && matchingFilesInDir.all { selectedFiles.containsKey(it.absolutePath) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!singleSelect) {
                AnimatedVisibility(
                    visible = selectedFiles.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FilledTonalButton(
                        onClick = { onConfirmFiles(selectedFiles.values.toList()) },
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Select (${selectedFiles.size})")
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }

        if (descriptors.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                descriptors.forEach { desc ->
                    val isSelected = currentDirectory.absolutePath.startsWith(desc.rootPath)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val rootFile = File(desc.rootPath)
                            if (rootFile.exists() && rootFile.canRead()) {
                                currentDirectory = rootFile
                            }
                        },
                        label = { Text(desc.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = desc.icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val parent = currentDirectory.parentFile
                    if (parent != null && parent.canRead()) {
                        currentDirectory = parent
                    }
                },
                enabled = canGoUp
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Up to parent folder"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentDirectory.name.ifBlank { currentDirectory.absolutePath },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentDirectory.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!singleSelect && matchingFilesInDir.isNotEmpty()) {
                TextButton(
                    onClick = {
                        if (allInDirSelected) {
                            matchingFilesInDir.forEach { selectedFiles.remove(it.absolutePath) }
                        } else {
                            matchingFilesInDir.forEach { selectedFiles[it.absolutePath] = it }
                        }
                    }
                ) {
                    Text(if (allInDirSelected) "Deselect folder" else "Select all")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading && directoryFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else if (directoryFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = directoryFiles,
                        key = { it.absolutePath }
                    ) { file ->
                        if (file.isDirectory) {
                            StoragePickerDirectoryRow(
                                directory = file,
                                onClick = {
                                    if (file.canRead()) {
                                        currentDirectory = file
                                    }
                                }
                            )
                        } else {
                            val isChecked = selectedFiles.containsKey(file.absolutePath)
                            StoragePickerFileRow(
                                file = file,
                                icon = fileIcon,
                                singleSelect = singleSelect,
                                isChecked = isChecked,
                                onClick = {
                                    if (singleSelect) {
                                        onConfirmFiles(listOf(file))
                                    } else {
                                        if (isChecked) {
                                            selectedFiles.remove(file.absolutePath)
                                        } else {
                                            selectedFiles[file.absolutePath] = file
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePickerDirectoryRow(
    directory: File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = directory.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StoragePickerFileRow(
    file: File,
    icon: ImageVector,
    singleSelect: Boolean,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = inferredDisplayTitleForName(file.name),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val ext = file.name.substringAfterLast('.', "").uppercase()
            val size = formatFileSize(file.length())
            Text(
                text = if (ext.isNotEmpty()) "$ext • $size" else size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!singleSelect) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onClick() }
            )
        }
    }
}
