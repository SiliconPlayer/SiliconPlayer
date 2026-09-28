package com.flopster101.siliconplayer.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import java.io.File
import java.net.URI

internal const val DROP_UNSUPPORTED_MESSAGE = "Drop supported audio files or a folder to browse"

internal sealed interface DroppedItemsAction {
    data class PlayAudio(val file: File) : DroppedItemsAction
    data class BrowseDirectory(val directory: File) : DroppedItemsAction
    data object Unsupported : DroppedItemsAction
}

// CMP readFiles() hands back file: URIs (percent-encoded), not paths.
private fun droppedFileFor(value: String): File =
    if (value.startsWith("file:", ignoreCase = true)) {
        runCatching { File(URI(value)) }.getOrElse { File(value) }
    } else {
        File(value)
    }

internal fun resolveDroppedItemsAction(
    paths: List<String>,
    supportedExtensions: Set<String>
): DroppedItemsAction {
    val files = paths.map { droppedFileFor(it) }
    files.firstOrNull { it.isFile && fileMatchesSupportedExtensions(it, supportedExtensions) }
        ?.let { return DroppedItemsAction.PlayAudio(it) }
    files.firstOrNull { it.isDirectory }?.let { return DroppedItemsAction.BrowseDirectory(it) }
    return DroppedItemsAction.Unsupported
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
internal fun Modifier.desktopFileDropTarget(
    supportedExtensions: Set<String>,
    onPlayFile: (File) -> Unit,
    onBrowseDirectory: (File) -> Unit,
    onUnsupported: () -> Unit
): Modifier = dragAndDropTarget(
    shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
    target = object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val paths = (event.dragData() as? DragData.FilesList)?.readFiles() ?: return false
            when (val action = resolveDroppedItemsAction(paths, supportedExtensions)) {
                is DroppedItemsAction.PlayAudio -> onPlayFile(action.file)
                is DroppedItemsAction.BrowseDirectory -> onBrowseDirectory(action.directory)
                DroppedItemsAction.Unsupported -> onUnsupported()
            }
            return true
        }
    }
)
