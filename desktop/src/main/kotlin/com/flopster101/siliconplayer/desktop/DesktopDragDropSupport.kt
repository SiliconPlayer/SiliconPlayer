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
import java.awt.Component
import java.awt.EventQueue
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.awt.dnd.DropTargetListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.net.URI
import javax.swing.RootPaneContainer

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

// Compose installs its own drop target on the window content panel while the
// window is being built and only accepts drags while one of its drag targets is
// eligible, which stops being the case once playback starts. Take that component
// over once the window is up, so its target is installed first and never removed.
internal fun attachDesktopFileDropTarget(
    window: Window,
    supportedExtensions: Set<String>,
    onPlayFile: (File) -> Unit,
    onBrowseDirectory: (File) -> Unit,
    onUnsupported: () -> Unit
): () -> Unit {
    var installedOn: Component? = null
    var installedTarget: DropTarget? = null
    fun install() {
        val contentPane = (window as? RootPaneContainer)?.contentPane
        val component = contentPane?.components?.firstOrNull { it.isShowing } ?: window
        if (component.dropTarget === installedTarget && installedTarget != null) return
        val target = DropTarget(
            component,
            DnDConstants.ACTION_COPY,
            fileDropListener(supportedExtensions, onPlayFile, onBrowseDirectory, onUnsupported),
            true
        )
        component.dropTarget = target
        installedOn = component
        installedTarget = target
    }
    if (window.isShowing) {
        install()
    } else {
        window.addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) {
                window.removeWindowListener(this)
                EventQueue.invokeLater { install() }
            }
        })
    }
    return {
        if (installedOn?.dropTarget === installedTarget) installedOn?.dropTarget = null
        installedOn = null
        installedTarget = null
    }
}

private fun fileDropListener(
    supportedExtensions: Set<String>,
    onPlayFile: (File) -> Unit,
    onBrowseDirectory: (File) -> Unit,
    onUnsupported: () -> Unit
) = object : DropTargetListener {
    override fun dragEnter(event: DropTargetDragEvent) = acceptFileList(event)
    override fun dragOver(event: DropTargetDragEvent) = acceptFileList(event)
    override fun dropActionChanged(event: DropTargetDragEvent) = acceptFileList(event)
    override fun dragExit(event: DropTargetEvent) {}
    override fun drop(event: DropTargetDropEvent) {
        // Accept before touching the transferable: on X11 the data request only
        // has a live drag session once the drop is accepted, and rejecting an
        // unsupported action would otherwise drop the event on the floor.
        val dropAction = usableAction(event.sourceActions, event.dropAction)
        if (dropAction == DnDConstants.ACTION_NONE) {
            event.rejectDrop()
            return
        }
        event.acceptDrop(dropAction)
        val files = runCatching { droppedFiles(event) }.getOrNull().orEmpty()
        if (files.isEmpty()) {
            event.dropComplete(false)
            return
        }
        val action = resolveDroppedItemsAction(files.map { it.absolutePath }, supportedExtensions)
        // DnD callbacks land on the EDT; keep Compose state writes there.
        EventQueue.invokeLater {
            when (action) {
                is DroppedItemsAction.PlayAudio -> onPlayFile(action.file)
                is DroppedItemsAction.BrowseDirectory -> onBrowseDirectory(action.directory)
                DroppedItemsAction.Unsupported -> onUnsupported()
            }
        }
        event.dropComplete(true)
    }
}

private fun acceptFileList(event: DropTargetDragEvent) {
    val action = usableAction(event.sourceActions, event.dropAction)
    if (action != DnDConstants.ACTION_NONE &&
        (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
            event.isDataFlavorSupported(DataFlavor.getTextPlainUnicodeFlavor()))
    ) {
        event.acceptDrag(action)
    } else {
        event.rejectDrag()
    }
}

// Sources may only offer move (e.g. file managers dragging inside a volume);
// accepting an action the source did not offer throws and kills the drop.
private fun usableAction(sourceActions: Int, preferred: Int): Int {
    if (sourceActions and preferred != 0) return preferred
    for (action in intArrayOf(DnDConstants.ACTION_COPY, DnDConstants.ACTION_MOVE, DnDConstants.ACTION_LINK)) {
        if (sourceActions and action != 0) return action
    }
    return DnDConstants.ACTION_NONE
}

private fun droppedFiles(event: DropTargetDropEvent): List<File> = runCatching {
    if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
        @Suppress("UNCHECKED_CAST")
        event.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
    } else {
        val text = event.transferable.getTransferData(DataFlavor.getTextPlainUnicodeFlavor()) as String
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { droppedFileFor(it) }.toList()
    }
}.getOrNull().orEmpty()

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
