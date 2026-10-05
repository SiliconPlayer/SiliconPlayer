package com.flopster101.siliconplayer

import android.content.Context
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AppNavigationPlaylistActionHandlers(
    val openParsedPlaylistDocument: (ParsedPlaylistDocument, String?) -> Unit,
    val handlePlaylistFileSelection: (File, String?) -> Unit,
    val openPlaylistFileImmediately: (File, String?) -> Unit,
    val playPendingBrowserPlaylist: () -> Unit,
    val openPendingBrowserPlaylistEntry: (PlaylistTrackEntry) -> Unit,
    val dismissPendingBrowserPlaylist: () -> Unit,
    val browsePendingBrowserPlaylist: () -> Unit,
    val playPlaylistEntry: (PlaylistTrackEntry, StoredPlaylist?, Boolean?) -> Unit
)

internal fun buildAppNavigationPlaylistActionHandlers(
    context: Context,
    appScope: CoroutineScope,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    pendingBrowserPlaylistDocumentProvider: () -> ParsedPlaylistDocument?,
    onPendingBrowserPlaylistDocumentChanged: (ParsedPlaylistDocument?) -> Unit,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onActivePlaylistShuffleActiveChanged: (Boolean) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
    onShowPlaylistOpenActionDialogChanged: (Boolean) -> Unit,
    onShowPlaylistPreviewDialogChanged: (Boolean) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit
): AppNavigationPlaylistActionHandlers {
    val openParsedPlaylistDocumentAction: (ParsedPlaylistDocument, String?) -> Unit = { document, entryId ->
        openPlaylistDocument(
            context = context,
            document = document,
            trackLoadDelegates = trackLoadDelegates,
            manualOpenDelegates = manualOpenDelegates,
            autoPlayOnTrackSelect = autoPlayOnTrackSelect,
            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
            onActivePlaylistChanged = {
                onActivePlaylistChanged(it)
                onActivePlaylistShuffleActiveChanged(false)
            },
            onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
            onShowPlaylistSelectorDialogChanged = onShowPlaylistSelectorDialogChanged,
            onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged,
            selectedEntryId = entryId
        )
    }

    val handlePlaylistFileSelectionAction: (File, String?) -> Unit = { file, sourceIdHint ->
        // Playlist reads can stall (network-backed files): never on the tap.
        appScope.launch(Dispatchers.IO) {
            val parsed = parsePlaylistFileDocument(file, sourceIdHint)
            withContext(Dispatchers.Main) {
                if (parsed == null || parsed.entries.isEmpty()) {
                    Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
                } else {
                    onPendingBrowserPlaylistDocumentChanged(parsed)
                    onShowPlaylistPreviewDialogChanged(false)
                    onShowPlaylistOpenActionDialogChanged(true)
                }
            }
        }
    }

    val openPlaylistFileImmediatelyAction: (File, String?) -> Unit = { file, sourceIdHint ->
        appScope.launch(Dispatchers.IO) {
            val parsed = parsePlaylistFileDocument(file, sourceIdHint)
            withContext(Dispatchers.Main) {
                if (parsed == null || parsed.entries.isEmpty()) {
                    Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
                } else {
                    openParsedPlaylistDocumentAction(parsed, null)
                    onPendingBrowserPlaylistDocumentChanged(null)
                    onShowPlaylistOpenActionDialogChanged(false)
                    onShowPlaylistPreviewDialogChanged(false)
                }
            }
        }
    }

    val playPendingBrowserPlaylistAction: () -> Unit = {
        pendingBrowserPlaylistDocumentProvider()?.let { document ->
            openParsedPlaylistDocumentAction(document, null)
            onPendingBrowserPlaylistDocumentChanged(null)
            onShowPlaylistOpenActionDialogChanged(false)
            onShowPlaylistPreviewDialogChanged(false)
        }
    }

    val openPendingBrowserPlaylistEntryAction: (PlaylistTrackEntry) -> Unit = { entry ->
        pendingBrowserPlaylistDocumentProvider()?.let { document ->
            openParsedPlaylistDocumentAction(document, entry.id)
            onPendingBrowserPlaylistDocumentChanged(null)
            onShowPlaylistOpenActionDialogChanged(false)
            onShowPlaylistPreviewDialogChanged(false)
        }
    }

    val dismissPendingBrowserPlaylistAction: () -> Unit = {
        onPendingBrowserPlaylistDocumentChanged(null)
        onShowPlaylistOpenActionDialogChanged(false)
        onShowPlaylistPreviewDialogChanged(false)
    }

    val browsePendingBrowserPlaylistAction: () -> Unit = {
        if (pendingBrowserPlaylistDocumentProvider() == null) {
            onShowPlaylistPreviewDialogChanged(false)
        } else {
            onShowPlaylistPreviewDialogChanged(true)
        }
    }

    val playPlaylistEntryAction: (PlaylistTrackEntry, StoredPlaylist?, Boolean?) -> Unit = { entry, playlist, expandOverride ->
        openPlaylistEntry(
            context = context,
            entry = entry,
            playlist = playlist,
            trackLoadDelegates = trackLoadDelegates,
            manualOpenDelegates = manualOpenDelegates,
            autoPlayOnTrackSelect = autoPlayOnTrackSelect,
            openPlayerOnTrackSelect = openPlayerOnTrackSelect,
            expandOverride = expandOverride ?: openPlayerOnTrackSelect,
            onActivePlaylistChanged = onActivePlaylistChanged,
            onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
            onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
        )
    }

    return AppNavigationPlaylistActionHandlers(
        openParsedPlaylistDocument = openParsedPlaylistDocumentAction,
        handlePlaylistFileSelection = handlePlaylistFileSelectionAction,
        openPlaylistFileImmediately = openPlaylistFileImmediatelyAction,
        playPendingBrowserPlaylist = playPendingBrowserPlaylistAction,
        openPendingBrowserPlaylistEntry = openPendingBrowserPlaylistEntryAction,
        dismissPendingBrowserPlaylist = dismissPendingBrowserPlaylistAction,
        browsePendingBrowserPlaylist = browsePendingBrowserPlaylistAction,
        playPlaylistEntry = playPlaylistEntryAction
    )
}
