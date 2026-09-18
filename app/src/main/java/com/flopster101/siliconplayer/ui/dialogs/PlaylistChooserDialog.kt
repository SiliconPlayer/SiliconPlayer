package com.flopster101.siliconplayer.ui.dialogs

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.AddCircle
import com.flopster101.siliconplayer.FAVORITES_PLAYLIST_ID
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.favoritesAsStoredPlaylist
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.readPlaylistLibraryState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flopster101.siliconplayer.PlaylistSortMode
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.sortStoredPlaylists
import com.flopster101.siliconplayer.ui.screens.PlaylistCoverArt

/**
 * Spotify-style add-to-playlist bottom sheet: Cancel/Done header, an
 * optional "Saved in" section for single-track adds (tap toggles the
 * track back out), an inline New-playlist row, a filter field, and
 * cover rows with an add affordance. MD3 ModalBottomSheet on API 24+,
 * bottom-anchored dialog card below (same split as the visualizer
 * mode picker).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddToPlaylistChooserDialog(
    playlists: List<StoredPlaylist>,
    favorites: List<PlaylistTrackEntry> = emptyList(),
    showFavorites: Boolean = false,
    pendingSources: Set<String>,
    dialogTitle: String = "Add to playlist",
    initialNewPlaylistTitle: String? = null,
    onConfirm: (playlistId: String?, newTitle: String) -> Unit,
    onRemoveFromPlaylist: (playlistId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val effectiveShowFavorites = remember(showFavorites) {
        if (showFavorites) {
            true
        } else {
            val p = context.getSharedPreferences(
                AppPreferenceKeys.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            p.getBoolean(
                AppPreferenceKeys.LIBRARY_SHOW_FAVORITES_IN_PLAYLIST_CHOOSER,
                false
            )
        }
    }
    val effectiveFavorites = remember(favorites, effectiveShowFavorites) {
        if (!effectiveShowFavorites) {
            emptyList()
        } else if (favorites.isNotEmpty()) {
            favorites
        } else {
            val p = context.getSharedPreferences(
                AppPreferenceKeys.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )
            readPlaylistLibraryState(p).favorites
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            AddToPlaylistSheetContent(
                playlists = playlists,
                favorites = effectiveFavorites,
                showFavorites = effectiveShowFavorites,
                pendingSources = pendingSources,
                dialogTitle = dialogTitle,
                initialNewPlaylistTitle = initialNewPlaylistTitle,
                onConfirm = onConfirm,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
                onDismiss = onDismiss
            )
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
                    AddToPlaylistSheetContent(
                        playlists = playlists,
                        favorites = effectiveFavorites,
                        showFavorites = effectiveShowFavorites,
                        pendingSources = pendingSources,
                        dialogTitle = dialogTitle,
                        initialNewPlaylistTitle = initialNewPlaylistTitle,
                        onConfirm = onConfirm,
                        onRemoveFromPlaylist = onRemoveFromPlaylist,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistSheetContent(
    playlists: List<StoredPlaylist>,
    favorites: List<PlaylistTrackEntry>,
    showFavorites: Boolean,
    pendingSources: Set<String>,
    dialogTitle: String = "Add to playlist",
    initialNewPlaylistTitle: String? = null,
    onConfirm: (playlistId: String?, newTitle: String) -> Unit,
    onRemoveFromPlaylist: (playlistId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var selectedSortMode by rememberSaveable { mutableStateOf(PlaylistSortMode.RecentlyUpdated) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var pendingDuplicateConfirmPlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
    val effectivePlaylists = remember(playlists, favorites, showFavorites) {
        if (showFavorites) {
            listOf(favoritesAsStoredPlaylist(favorites)) + playlists
        } else {
            playlists
        }
    }
    fun sortWithFavoritesFirst(list: List<StoredPlaylist>, mode: PlaylistSortMode): List<StoredPlaylist> {
        val fav = list.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
        val rest = list.filterNot { it.id == FAVORITES_PLAYLIST_ID }
        val sortedRest = sortStoredPlaylists(rest, mode)
        return if (fav != null) listOf(fav) + sortedRest else sortedRest
    }
    val singleSource = pendingSources.singleOrNull()
    val savedIn = remember(effectivePlaylists, singleSource, selectedSortMode) {
        if (singleSource == null) {
            emptyList()
        } else {
            val matching = effectivePlaylists.filter { playlist ->
                playlist.entries.any { entry ->
                    samePath(entry.source, singleSource)
                }
            }
            sortWithFavoritesFirst(matching, selectedSortMode)
        }
    }
    val savedInIds = remember(savedIn) { savedIn.map { it.id }.toSet() }
    val filteredPlaylists = remember(effectivePlaylists, query, selectedSortMode) {
        val base = if (query.isBlank()) {
            effectivePlaylists
        } else {
            effectivePlaylists.filter { it.title.contains(query, ignoreCase = true) }
        }
        sortWithFavoritesFirst(base, selectedSortMode)
    }
    val otherPlaylists = remember(filteredPlaylists, savedInIds) {
        filteredPlaylists.filter { it.id !in savedInIds }
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add to playlist",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showNewPlaylistDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New")
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Find playlist") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                } else null,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = "Sort playlists"
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    PlaylistSortMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            },
                            leadingIcon = if (mode == selectedSortMode) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else null,
                            contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            onClick = {
                                sortMenuExpanded = false
                                selectedSortMode = mode
                            }
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (effectivePlaylists.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No playlists yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (query.isNotBlank()) {
                if (filteredPlaylists.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching playlists",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredPlaylists.forEach { playlist ->
                        val contained = singleSource != null && playlist.id in savedInIds
                        key(playlist.id) {
                            ChooserPlaylistRow(
                                playlist = playlist,
                                contained = contained,
                                onClick = {
                                    if (contained) {
                                        onRemoveFromPlaylist(playlist.id)
                                    } else {
                                        val hasDuplicates = playlist.entries.any { entry ->
                                            pendingSources.any { samePath(entry.source, it) }
                                        }
                                        if (hasDuplicates) {
                                            pendingDuplicateConfirmPlaylist = playlist
                                        } else {
                                            onConfirm(playlist.id, "")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = savedIn.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(180)) +
                        expandVertically(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(150)) +
                        shrinkVertically(animationSpec = tween(180))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Saved in",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        savedIn.forEach { playlist ->
                            key(playlist.id) {
                                ChooserPlaylistRow(
                                    playlist = playlist,
                                    contained = true,
                                    onClick = { onRemoveFromPlaylist(playlist.id) }
                                )
                            }
                        }
                    }
                }

                if (savedIn.isNotEmpty() && otherPlaylists.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = "Other playlists",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }

                otherPlaylists.forEach { playlist ->
                    key(playlist.id) {
                        ChooserPlaylistRow(
                            playlist = playlist,
                            contained = false,
                            onClick = {
                                val hasDuplicates = playlist.entries.any { entry ->
                                    pendingSources.any { samePath(entry.source, it) }
                                }
                                if (hasDuplicates) {
                                    pendingDuplicateConfirmPlaylist = playlist
                                } else {
                                    onConfirm(playlist.id, "")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    pendingDuplicateConfirmPlaylist?.let { targetPlaylist ->
        val duplicates = targetPlaylist.entries.filter { entry ->
            pendingSources.any { samePath(entry.source, it) }
        }
        val isSingle = pendingSources.size == 1
        AlertDialog(
            onDismissRequest = { pendingDuplicateConfirmPlaylist = null },
            title = { Text(if (isSingle) "Already in playlist" else "Duplicate tracks") },
            text = {
                Text(
                    if (isSingle) {
                        "This track is already in \"${targetPlaylist.title}\". Do you want to add it again?"
                    } else {
                        "${duplicates.size} of the ${pendingSources.size} tracks are already in \"${targetPlaylist.title}\". Do you want to add them anyway?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val playlistId = targetPlaylist.id
                        pendingDuplicateConfirmPlaylist = null
                        onConfirm(playlistId, "")
                    }
                ) {
                    Text("Add anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuplicateConfirmPlaylist = null }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (showNewPlaylistDialog) {
        NewPlaylistDialog(
            existingTitles = remember(effectivePlaylists) { effectivePlaylists.map { it.title }.toSet() },
            onConfirm = { title ->
                showNewPlaylistDialog = false
                onConfirm(null, title)
            },
            onDismiss = { showNewPlaylistDialog = false }
        )
    }
}

@Composable
private fun ChooserPlaylistRow(
    playlist: StoredPlaylist,
    contained: Boolean,
    onClick: () -> Unit
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    AnimatedVisibility(
        visible = entered,
        enter = fadeIn(animationSpec = tween(180)) +
            expandVertically(animationSpec = tween(220))
    ) {
        Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCoverArt(
            entries = playlist.entries,
            customArtworkUri = playlist.customArtworkUri,
            iconTintArgb = playlist.iconTintArgb,
            heroIcon = if (playlist.id == FAVORITES_PLAYLIST_ID) Icons.Default.Star else Icons.Default.LibraryMusic,
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(12.dp),
            iconSize = 30.dp,
            autoMosaicEnabled = playlist.autoGenerateCover
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (playlist.entries.size) {
                    0 -> "No tracks yet"
                    1 -> "1 track"
                    else -> "${playlist.entries.size} tracks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClick) {
            AnimatedContent(
                targetState = contained,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(150)) +
                        scaleIn(animationSpec = tween(150))) togetherWith
                        (fadeOut(animationSpec = tween(150)) +
                            scaleOut(animationSpec = tween(150)))
                },
                label = "chooserRowContainedSwap"
            ) { isContained ->
                if (isContained) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Remove from playlist",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.AddCircle,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}
}
