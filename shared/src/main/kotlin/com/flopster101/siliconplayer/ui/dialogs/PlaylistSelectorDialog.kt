package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.platform.LocalArtworkCacheSupport
import com.flopster101.siliconplayer.platform.LocalArtworkThumbnailLoader
import com.flopster101.siliconplayer.platform.isWatchDevice
import com.flopster101.siliconplayer.rememberDialogLazyListScrollbarAlpha
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.ui.screens.BrowserLazyListScrollbar

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistSelectorDialog(
    title: String,
    subtitle: String?,
    shuffleActive: Boolean,
    entries: List<PlaylistTrackEntry>,
    currentEntryId: String?,
    onSelectEntry: (PlaylistTrackEntry) -> Unit,
    onDismiss: () -> Unit,
    onSaveAsPlaylist: (() -> Unit)? = null,
    onEntryAddTrackToPlaylist: ((PlaylistTrackEntry) -> Unit)? = null,
    onEntryToggleFavorite: ((PlaylistTrackEntry) -> Unit)? = null,
    isEntryFavorite: ((PlaylistTrackEntry) -> Boolean)? = null
) {
    val resolvedPrimaryTitle = subtitle
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: title
    val resolvedSecondaryTitle = title.takeIf { resolvedPrimaryTitle != title }
    if (isWatchDevice()) {
        WatchDialogContainer(
            title = resolvedPrimaryTitle,
            onDismissRequest = onDismiss
        ) {
            resolvedSecondaryTitle?.let { secondaryTitle ->
                Text(
                    text = secondaryTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (entries.isEmpty()) {
                Text("No playlist entries available.", textAlign = TextAlign.Center)
            } else {
                entries.forEachIndexed { index, entry ->
                    val isCurrent = entry.id == currentEntryId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable {
                                onSelectEntry(entry)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlaylistSelectorArtworkChip(
                            entry = entry,
                            isCurrent = isCurrent
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playlistSelectorSubtitle(entry),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            if (shuffleActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Shuffle active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (onSaveAsPlaylist != null) {
                FilledTonalButton(
                    onClick = {
                        onSaveAsPlaylist()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save playlist")
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Close")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = resolvedPrimaryTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                    )
                    resolvedSecondaryTitle?.let { secondaryTitle ->
                        Text(
                            text = secondaryTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (entries.isEmpty()) {
                        Text("No playlist entries available.")
                    } else {
                        val currentIndex = remember(entries, currentEntryId) {
                            if (currentEntryId != null) {
                                entries.indexOfFirst { it.id == currentEntryId }
                            } else {
                                -1
                            }
                        }
                        val initialIndex = remember(currentIndex) {
                            if (currentIndex > 0) currentIndex - 1 else currentIndex.coerceAtLeast(0)
                        }
                        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
                        LaunchedEffect(currentIndex) {
                            if (currentIndex >= 0) {
                                listState.scrollToItem(if (currentIndex > 0) currentIndex - 1 else currentIndex)
                            }
                        }
                        val scrollbarAlpha = rememberDialogLazyListScrollbarAlpha(
                            enabled = entries.size > 1,
                            listState = listState,
                            flashKey = entries.size to currentEntryId,
                            label = "playlistSelectorScrollbarAlpha"
                        )
                        val scrollbarHeld = remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(
                                    count = entries.size,
                                    key = { index -> entries[index].id }
                                ) { index ->
                                    val entry = entries[index]
                                    val isCurrent = entry.id == currentEntryId
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(MaterialTheme.shapes.large)
                                                .background(
                                                    if (isCurrent) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                                    } else {
                                                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                                                    }
                                                )
                                                .clickable { onSelectEntry(entry) }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlaylistSelectorArtworkChip(
                                                entry = entry,
                                                isCurrent = isCurrent
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = entry.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isCurrent) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = playlistSelectorSubtitle(entry),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (isCurrent) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isCurrent) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                if (onEntryAddTrackToPlaylist != null || onEntryToggleFavorite != null) {
                                                    var rowMenuExpanded by remember { mutableStateOf(false) }
                                                    Box {
                                                        IconButton(
                                                            onClick = { rowMenuExpanded = true },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.MoreVert,
                                                                contentDescription = "Track options",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                        DropdownMenu(
                                                            expanded = rowMenuExpanded,
                                                            onDismissRequest = { rowMenuExpanded = false }
                                                        ) {
                                                            if (onEntryAddTrackToPlaylist != null) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Add to playlist…") },
                                                                    leadingIcon = {
                                                                        Icon(
                                                                            imageVector = Icons.Default.PlaylistAdd,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        rowMenuExpanded = false
                                                                        onEntryAddTrackToPlaylist(entry)
                                                                    }
                                                                )
                                                            }
                                                            if (onEntryToggleFavorite != null) {
                                                                val isFav = isEntryFavorite?.invoke(entry) == true
                                                                DropdownMenuItem(
                                                                    text = { Text(if (isFav) "Remove from favorites" else "Add to favorites") },
                                                                    leadingIcon = {
                                                                        Icon(
                                                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                                                            contentDescription = null,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                    },
                                                                    onClick = {
                                                                        rowMenuExpanded = false
                                                                        onEntryToggleFavorite(entry)
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (index < entries.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(start = 58.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
                                }
                            }
                            BrowserLazyListScrollbar(
                                listState = listState,
                                onDragActiveChanged = { isActive -> scrollbarHeld.value = isActive },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxSize()
                                    .graphicsLayer(alpha = if (scrollbarHeld.value) 1f else scrollbarAlpha)
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = if (shuffleActive) "Shuffle active" else "Shuffle inactive",
                            tint = if (shuffleActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onSaveAsPlaylist != null) {
                        FilledTonalButton(
                            onClick = onSaveAsPlaylist,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Save playlist")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        )
    }
}

private fun playlistSelectorSubtitle(entry: PlaylistTrackEntry): String {
    val details = buildList {
        entry.artist
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::add)
        entry.subtuneIndex?.let { add("Subtune ${it + 1}") }
        entry.album
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !contains(it) }
            ?.let(::add)
    }
    if (details.isNotEmpty()) {
        return details.joinToString(" • ")
    }
    return resolvePlaylistEntryLocalFile(entry.source)?.name ?: entry.source
}

@Composable
private fun PlaylistSelectorArtworkChip(
    entry: PlaylistTrackEntry,
    isCurrent: Boolean
) {
    val artworkCache = LocalArtworkCacheSupport.current
    val artworkThumbnailLoader = LocalArtworkThumbnailLoader.current
    val fallbackIcon = placeholderArtworkIconForFile(
        file = resolvePlaylistEntryLocalFile(entry.source),
        decoderName = null,
        allowCurrentDecoderFallback = false
    )
    val artworkThumbnailCacheKey by produceState<String?>(
        initialValue = entry.artworkThumbnailCacheKey,
        key1 = entry.id,
        key2 = entry.source,
        key3 = entry.artworkThumbnailCacheKey
    ) {
        if (!entry.artworkThumbnailCacheKey.isNullOrBlank()) {
            value = entry.artworkThumbnailCacheKey
            return@produceState
        }
        value = artworkCache.ensureThumbnailCached(entry.source, entry.requestUrlHint)
    }
    val cacheRevision by artworkThumbnailLoader.revision.collectAsState()
    val artwork by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = artworkThumbnailLoader.peek(artworkThumbnailCacheKey),
        key1 = artworkThumbnailCacheKey,
        key2 = cacheRevision
    ) {
        val loaded = artworkThumbnailLoader.load(artworkThumbnailCacheKey)
        if (loaded != null) {
            value = loaded
            return@produceState
        }
        value = artworkCache.loadArtworkForSource(entry.source, entry.requestUrlHint)
    }
    Surface(
        modifier = Modifier.size(46.dp),
        shape = MaterialTheme.shapes.large,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = if (isCurrent) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp)
            )
            artwork?.let { resolvedArtwork ->
                Image(
                    bitmap = resolvedArtwork,
                    contentDescription = "Album artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
