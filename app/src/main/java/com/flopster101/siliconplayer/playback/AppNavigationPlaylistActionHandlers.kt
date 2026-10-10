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
    val openPlaylistFileImmediately: (File, String?) -> Unit,
    val playPlaylistEntry: (PlaylistTrackEntry, StoredPlaylist?, Boolean?) -> Unit
)

internal fun buildAppNavigationPlaylistActionHandlers(
    context: Context,
    appScope: CoroutineScope,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onActivePlaylistShuffleActiveChanged: (Boolean) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
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

    val openPlaylistFileImmediatelyAction: (File, String?) -> Unit = { file, sourceIdHint ->
        appScope.launch(Dispatchers.IO) {
            val parsed = parsePlaylistFileDocument(file, sourceIdHint)
            withContext(Dispatchers.Main) {
                if (parsed == null || parsed.entries.isEmpty()) {
                    Toast.makeText(context, "Unable to open playlist", Toast.LENGTH_SHORT).show()
                } else {
                    openParsedPlaylistDocumentAction(parsed, null)
                }
            }
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
        openPlaylistFileImmediately = openPlaylistFileImmediatelyAction,
        playPlaylistEntry = playPlaylistEntryAction
    )
}
