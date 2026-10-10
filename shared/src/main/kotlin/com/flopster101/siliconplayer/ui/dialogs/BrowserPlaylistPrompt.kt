package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.ParsedPlaylistDocument
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.parsePlaylistDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shared M3U tap flow: parse off the tap, prompt (Play / List tracks / Save),
 * open the imported queue. Platforms only supply the toast, the document open,
 * and the save wiring; there is no Android or desktop copy of this flow.
 */
internal class BrowserPlaylistPrompt(
    private val scope: CoroutineScope,
    private val toast: (String) -> Unit,
    private val onOpenDocument: (ParsedPlaylistDocument, String?) -> Unit
) {
    var document by mutableStateOf<ParsedPlaylistDocument?>(null)
        private set
    var showAction by mutableStateOf(false)
        private set
    var showPreview by mutableStateOf(false)
        private set

    fun onPlaylistFileSelected(file: File, sourceIdHint: String?) {
        // Playlist reads can stall (network-backed files): never on the tap.
        // Snapshot state writes are thread-safe, so no Main hop is needed here.
        scope.launch(Dispatchers.IO) {
            val parsed = runCatching { parsePlaylistDocument(file, sourceIdHint) }.getOrNull()
            if (parsed == null || parsed.entries.isEmpty()) {
                toast("Unable to open playlist")
            } else {
                document = parsed
                showPreview = false
                showAction = true
            }
        }
    }

    fun playFromTop() {
        val target = document
        dismiss()
        if (target != null) onOpenDocument(target, null)
    }

    fun openEntry(entry: PlaylistTrackEntry) {
        val target = document
        dismiss()
        if (target != null) onOpenDocument(target, entry.id)
    }

    fun showEntryList() {
        if (document == null) {
            showPreview = false
        } else {
            showAction = false
            showPreview = true
        }
    }

    fun dismiss() {
        document = null
        showAction = false
        showPreview = false
    }
}

@Composable
internal fun rememberBrowserPlaylistPrompt(
    toast: (String) -> Unit,
    onOpenDocument: (ParsedPlaylistDocument, String?) -> Unit,
    scope: CoroutineScope = rememberCoroutineScope()
): BrowserPlaylistPrompt {
    val currentToast by rememberUpdatedState(toast)
    val currentOpenDocument by rememberUpdatedState(onOpenDocument)
    return remember(scope) {
        BrowserPlaylistPrompt(
            scope = scope,
            toast = { currentToast(it) },
            onOpenDocument = { document, entryId -> currentOpenDocument(document, entryId) }
        )
    }
}

@Composable
internal fun BrowserPlaylistPromptDialogs(
    prompt: BrowserPlaylistPrompt,
    onSave: (ParsedPlaylistDocument) -> Unit,
    onEntryAddTrackToPlaylist: ((PlaylistTrackEntry) -> Unit)? = null,
    onEntryToggleFavorite: ((PlaylistTrackEntry) -> Unit)? = null,
    isEntryFavorite: ((PlaylistTrackEntry) -> Boolean)? = null
) {
    val document = prompt.document
    if (prompt.showAction && document != null) {
        PlaylistOpenActionDialog(
            title = document.title,
            entryCount = document.entries.size,
            onPlayNow = { prompt.playFromTop() },
            onBrowseEntries = { prompt.showEntryList() },
            onDismiss = { prompt.dismiss() },
            onSaveAsPlaylist = { onSave(document) }
        )
    }
    if (prompt.showPreview && document != null) {
        PlaylistSelectorDialog(
            title = "Playlist",
            subtitle = document.title,
            shuffleActive = false,
            entries = document.entries,
            currentEntryId = null,
            onSelectEntry = { prompt.openEntry(it) },
            onDismiss = { prompt.dismiss() },
            onSaveAsPlaylist = { onSave(document) },
            onEntryAddTrackToPlaylist = onEntryAddTrackToPlaylist,
            onEntryToggleFavorite = onEntryToggleFavorite,
            isEntryFavorite = isEntryFavorite
        )
    }
}
