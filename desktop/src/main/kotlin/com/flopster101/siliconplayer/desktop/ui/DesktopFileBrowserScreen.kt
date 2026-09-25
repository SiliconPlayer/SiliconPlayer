package com.flopster101.siliconplayer.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.data.FileItem
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

private val TRACKER_EXTENSIONS = setOf(
    "mod", "s3m", "xm", "it", "umx", "mo3", "mptm", "med", "okt", "669", "mtm", "far", "dsm", "amf"
)

private val CHIP_EXTENSIONS = setOf(
    "sid", "nsf", "nsfe", "spc", "vgm", "vgz", "gym", "ay", "gbs", "hes", "kss", "sap", "sndh", "ym", "mid", "midi"
)

private fun formatBadgeColor(extension: String): Color {
    val ext = extension.lowercase(Locale.ROOT)
    return when {
        ext in TRACKER_EXTENSIONS -> Color(0xFF4CAF50)
        ext in CHIP_EXTENSIONS -> Color(0xFFFF9800)
        ext == "flac" || ext == "wav" || ext == "alac" || ext == "aiff" -> Color(0xFF2196F3)
        ext == "mp3" || ext == "ogg" || ext == "m4a" || ext == "aac" || ext == "opus" -> Color(0xFF9C27B0)
        else -> Color(0xFF00BCD4)
    }
}

private fun formatBytesHumanReadable(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return "${DecimalFormat("#,##0.#").format(value)} ${units[digitGroups]}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DesktopFileBrowserScreen(
    currentDirectory: File,
    onDirectoryChanged: (File) -> Unit,
    currentPlayingFile: File?,
    isPlaying: Boolean,
    onFileSelected: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val rawFiles = remember(currentDirectory) {
        currentDirectory.listFiles()?.toList() ?: emptyList()
    }

    val items = remember(rawFiles, searchQuery) {
        val filtered = if (searchQuery.isBlank()) {
            rawFiles
        } else {
            rawFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        val dirs = filtered.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { file ->
                FileItem(
                    file = file,
                    name = file.name,
                    isDirectory = true,
                    size = 0L,
                    kind = FileItem.Kind.Directory
                )
            }

        val files = filtered.filter { it.isFile && !it.name.startsWith(".") }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            .map { file ->
                FileItem(
                    file = file,
                    name = file.name,
                    isDirectory = false,
                    size = file.length(),
                    kind = FileItem.Kind.AudioFile
                )
            }

        dirs + files
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val parentFile = currentDirectory.parentFile
                    IconButton(
                        onClick = {
                            if (parentFile != null && parentFile.canRead()) {
                                onDirectoryChanged(parentFile)
                            }
                        },
                        enabled = parentFile != null && parentFile.canRead()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Up one directory"
                        )
                    }

                    IconButton(
                        onClick = {
                            onDirectoryChanged(File(System.getProperty("user.home")))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "User Home"
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = currentDirectory.name.ifBlank { "/" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search files"
                        )
                    }

                    IconButton(
                        onClick = {
                            onDirectoryChanged(currentDirectory)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh directory"
                        )
                    }
                }

                // Path Breadcrumb display
                Text(
                    text = currentDirectory.absolutePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp)
                )

                // Search Bar Input
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        placeholder = { Text("Filter items in folder...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // Directory & File Listing
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matches for \"$searchQuery\"" else "Folder is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.file.absolutePath }) { item ->
                    val isCurrent = currentPlayingFile != null && currentPlayingFile.absolutePath == item.file.absolutePath
                    val extension = inferredPrimaryExtensionForName(item.file.name)?.uppercase(Locale.ROOT) ?: "FILE"

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (item.isDirectory) {
                                    if (item.file.canRead()) {
                                        onDirectoryChanged(item.file)
                                    }
                                } else {
                                    onFileSelected(item.file)
                                }
                            },
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon / Format Badge
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (item.isDirectory) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            formatBadgeColor(extension).copy(alpha = 0.16f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.isDirectory) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Folder",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = extension.take(4),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        color = formatBadgeColor(extension)
                                    )
                                }

                                if (isCurrent && isPlaying) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 2.dp, y = 2.dp)
                                            .size(16.dp)
                                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                                            .padding(1.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            // Name & Metadata
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (item.isDirectory) {
                                        val childCount = item.file.list()?.size ?: 0
                                        "$childCount items"
                                    } else {
                                        "$extension • ${formatBytesHumanReadable(item.size)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
