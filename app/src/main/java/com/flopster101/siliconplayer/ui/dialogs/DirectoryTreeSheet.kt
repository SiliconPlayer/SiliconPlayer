package com.flopster101.siliconplayer.ui.dialogs

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private sealed interface DirChildren {
    data object Loading : DirChildren
    data class Ready(val dirs: List<File>) : DirChildren
}

// One visible row: an ancestor of the current directory, or an expanded child.
private data class DirRow(
    val directory: File,
    val depth: Int,
    val isCurrent: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DirectoryTreeSheet(
    root: File,
    currentDirectory: File,
    onNavigate: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val content = @Composable {
        DirectoryTreeContent(
            root = root,
            currentDirectory = currentDirectory,
            onNavigate = onNavigate,
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
        Dialog(onDismissRequest = onDismiss, properties = adaptiveDialogProperties()) {
            Card(
                modifier = adaptiveDialogModifier().fillMaxHeight(0.7f),
                shape = RoundedCornerShape(24.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun DirectoryTreeContent(
    root: File,
    currentDirectory: File,
    onNavigate: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val currentPath = currentDirectory.absolutePath
    // Directories that lie on the path to the current directory stay expanded so
    // the current entry is visible; the user can expand any other directory.
    val autoExpanded = remember(root, currentDirectory) {
        buildAncestorPaths(root, currentDirectory).toSet()
    }
    val manuallyToggled = remember { mutableStateOf(setOf<String>()) }
    val childrenByPath = remember { mutableStateMapOf<String, DirChildren>() }
    val listState = rememberLazyListState()

    val isExpanded: (String) -> Boolean = { path ->
        (path in autoExpanded) xor (path in manuallyToggled.value)
    }

    // Recursively flatten the visible tree from the root, honoring expansion.
    fun flatten(dir: File, depth: Int, out: MutableList<DirRow>) {
        out += DirRow(dir, depth, isCurrent = dir.absolutePath == currentPath)
        val path = dir.absolutePath
        if (isExpanded(path)) {
            when (val state = childrenByPath[path]) {
                is DirChildren.Ready -> state.dirs.forEach { flatten(it, depth + 1, out) }
                else -> Unit
            }
        }
    }
    val rows = remember(
        root, currentPath, manuallyToggled.value, childrenByPath.toMap()
    ) {
        mutableListOf<DirRow>().also { flatten(root, 0, it) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Directory tree",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(items = rows, key = { it.directory.absolutePath }) { row ->
                DirectoryTreeRow(
                    row = row,
                    childrenState = childrenByPath[row.directory.absolutePath],
                    isExpanded = isExpanded(row.directory.absolutePath),
                    onNavigate = {
                        onNavigate(row.directory)
                        onDismiss()
                    },
                    onToggleExpand = {
                        val path = row.directory.absolutePath
                        manuallyToggled.value = if (path in manuallyToggled.value) {
                            manuallyToggled.value - path
                        } else {
                            manuallyToggled.value + path
                        }
                    }
                )
            }
        }
    }

    // Lazily load children for every expanded directory not yet fetched.
    val expandedNow = rows.filter { isExpanded(it.directory.absolutePath) }
        .map { it.directory.absolutePath }
    LaunchedEffect(expandedNow) {
        expandedNow.forEach { path ->
            if (childrenByPath[path] == null) {
                childrenByPath[path] = DirChildren.Loading
                val dirs = withContext(Dispatchers.IO) {
                    File(path).listFiles()
                        .orEmpty()
                        .filter { it.isDirectory && !it.name.startsWith(".") }
                        .sortedBy { it.name.lowercase() }
                }
                childrenByPath[path] = DirChildren.Ready(dirs)
            }
        }
    }

    // Scroll to the current directory once every auto-expanded ancestor has
    // loaded its children, so the row index is stable, and animate rather than
    // jump. Runs once per sheet open.
    val ancestorsReady = autoExpanded.all { childrenByPath[it] is DirChildren.Ready }
    var scrolledToCurrent by remember(currentPath) { mutableStateOf(false) }
    LaunchedEffect(ancestorsReady) {
        if (scrolledToCurrent || !ancestorsReady) return@LaunchedEffect
        val currentIndex = rows.indexOfFirst { it.isCurrent }
        if (currentIndex > 0) {
            listState.animateScrollToItem(currentIndex)
            scrolledToCurrent = true
        }
    }
}

@Composable
private fun DirectoryTreeRow(
    row: DirRow,
    childrenState: DirChildren?,
    isExpanded: Boolean,
    onNavigate: () -> Unit,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onNavigate)
            .padding(
                start = (row.depth * 20).dp,
                top = 6.dp,
                bottom = 6.dp,
                end = 4.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleExpand),
            contentAlignment = Alignment.Center
        ) {
            when (childrenState) {
                is DirChildren.Loading -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                else -> Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.ExpandMore
                    } else {
                        Icons.Default.ChevronRight
                    },
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = if (row.isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = row.directory.name.ifBlank { row.directory.absolutePath },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (row.isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (row.isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Paths from [root] down to [currentDirectory]'s parent chain (inclusive). */
private fun buildAncestorPaths(root: File, currentDirectory: File): List<String> {
    val rootPath = root.absolutePath.trimEnd('/')
    val currentPath = currentDirectory.absolutePath
    val segments = if (currentPath.startsWith("$rootPath/")) {
        currentPath.removePrefix("$rootPath/").split("/").filter { it.isNotBlank() }
    } else {
        emptyList()
    }
    val paths = mutableListOf(rootPath)
    var running = rootPath
    segments.forEach { segment ->
        running = "$running/$segment"
        paths += running
    }
    return paths
}
