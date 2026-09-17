package com.flopster101.siliconplayer.ui.dialogs

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flopster101.siliconplayer.library.LibraryTrackEntity
import com.flopster101.siliconplayer.library.LibraryRepository
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.StorageDescriptor
import com.flopster101.siliconplayer.detectStorageDescriptors
import com.flopster101.siliconplayer.ui.screens.formatFileSize
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.LinearProgressIndicator
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkNodeType
import com.flopster101.siliconplayer.NetworkSourceKind
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.resolveNetworkNodeOpenInput
import com.flopster101.siliconplayer.resolveNetworkNodeDisplayTitle
import com.flopster101.siliconplayer.listSmbDirectoryEntries
import com.flopster101.siliconplayer.listSmbHostShareEntries
import com.flopster101.siliconplayer.buildSmbEntrySourceSpec
import com.flopster101.siliconplayer.buildSmbRequestUri
import com.flopster101.siliconplayer.joinSmbRelativePath
import com.flopster101.siliconplayer.SmbBrowserEntry
import com.flopster101.siliconplayer.SmbSourceSpec
import androidx.compose.ui.res.painterResource
import com.flopster101.siliconplayer.DecoderArtworkHint
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.buildDecoderExtensionArtworkHintMap
import com.flopster101.siliconplayer.isSupportedPlaylistFileName
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.resolveDecoderArtworkHintForFileName
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.ui.screens.NetworkIcons
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bottom sheet offering multi-source options for adding tracks into a playlist:
 * - From indexed Library
 * - From File / SMB Storage
 * - From Direct Network URL
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddTracksSourceSheet(
    playlistTitle: String,
    currentTrack: PlaylistTrackEntry? = null,
    onAddCurrentTrack: () -> Unit = {},
    onSelectLibrary: () -> Unit,
    onSelectStorage: () -> Unit,
    onSelectNetwork: () -> Unit,
    onSelectDirectUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AddTracksSourceSheetContent(
                playlistTitle = playlistTitle,
                currentTrack = currentTrack,
                onAddCurrentTrack = onAddCurrentTrack,
                onSelectLibrary = onSelectLibrary,
                onSelectStorage = onSelectStorage,
                onSelectNetwork = onSelectNetwork,
                onSelectDirectUrl = onSelectDirectUrl,
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
                        .fillMaxHeight(0.65f)
                        .padding(top = 48.dp)
                ) {
                    AddTracksSourceSheetContent(
                        playlistTitle = playlistTitle,
                        currentTrack = currentTrack,
                        onAddCurrentTrack = onAddCurrentTrack,
                        onSelectLibrary = onSelectLibrary,
                        onSelectStorage = onSelectStorage,
                        onSelectNetwork = onSelectNetwork,
                        onSelectDirectUrl = onSelectDirectUrl,
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTracksSourceSheetContent(
    playlistTitle: String,
    currentTrack: PlaylistTrackEntry?,
    onAddCurrentTrack: () -> Unit,
    onSelectLibrary: () -> Unit,
    onSelectStorage: () -> Unit,
    onSelectNetwork: () -> Unit,
    onSelectDirectUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentTrackFile = currentTrack?.let {
        resolvePlaylistEntryLocalFile(it.source) ?: File(it.source.substringBefore('?').substringAfterLast('/'))
    }
    val currentTrackArtworkHint = currentTrackFile?.name?.let {
        resolveDecoderArtworkHintForFileName(it, buildDecoderExtensionArtworkHintMap())
    }
    val currentTrackDescription = if (currentTrack != null) {
        if (!currentTrack.artist.isNullOrBlank()) {
            "${currentTrack.artist} • ${currentTrack.title}"
        } else {
            currentTrack.title
        }
    } else {
        "No track currently playing or queued"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add tracks",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Add into $playlistTitle",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        SourceOptionRow(
            icon = Icons.Default.LibraryMusic,
            title = "Library",
            description = "Search indexed tracks, albums, and artists",
            onClick = {
                onDismiss()
                onSelectLibrary()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SourceOptionRow(
            icon = Icons.Default.Folder,
            title = "Files & Storage",
            description = "Browse local device storage",
            onClick = {
                onDismiss()
                onSelectStorage()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SourceOptionRow(
            icon = Icons.Default.Public,
            title = "Network & SMB",
            description = "Browse network shares and SMB servers",
            onClick = {
                onDismiss()
                onSelectNetwork()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SourceOptionRow(
            icon = Icons.Default.Link,
            title = "Direct URL",
            description = "Stream or audio link from the web",
            onClick = {
                onDismiss()
                onSelectDirectUrl()
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        val currentTrackIconContent: @Composable () -> Unit = {
            val alpha = if (currentTrack != null) 1f else 0.38f
            val iconTint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            val iconSize = Modifier.size(24.dp)
            when {
                currentTrack == null -> {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = iconSize
                    )
                }
                currentTrackArtworkHint == DecoderArtworkHint.TrackedFile -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_tracked),
                        contentDescription = "Tracked file",
                        tint = iconTint,
                        modifier = iconSize
                    )
                }
                currentTrackArtworkHint == DecoderArtworkHint.GameFile -> {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_game),
                        contentDescription = "Game file",
                        tint = iconTint,
                        modifier = iconSize
                    )
                }
                else -> {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = "Audio file",
                        tint = iconTint,
                        modifier = iconSize
                    )
                }
            }
        }
        SourceOptionRow(
            title = "Currently playing",
            description = currentTrackDescription,
            enabled = currentTrack != null,
            iconContent = currentTrackIconContent,
            onClick = {
                onDismiss()
                onAddCurrentTrack()
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SourceOptionRow(
    title: String,
    description: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 1f else 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (iconContent != null) {
                    iconContent()
                } else if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}

/**
 * Dialog to add a direct audio link or network stream.
 */
@Composable
internal fun AddDirectUrlDialog(
    onConfirm: (url: String, title: String?, artist: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }

    FloatingActionDialog(
        title = "Add direct URL",
        onDismiss = onDismiss,
        confirmText = "Add",
        confirmEnabled = url.isNotBlank(),
        onConfirm = {
            onConfirm(
                url.trim(),
                title.trim().takeUnless { it.isEmpty() },
                artist.trim().takeUnless { it.isEmpty() }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Stream or Audio URL") },
                placeholder = { Text("https://example.com/stream.mp3") },
                singleLine = true,
                trailingIcon = if (url.isNotEmpty()) {
                    {
                        IconButton(onClick = { url = "" }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Artist (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Multi-select track picker sheet from indexed library tracks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFromLibraryPickerSheet(
    onConfirm: (List<LibraryTrackEntity>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var allTracks by remember { mutableStateOf<List<LibraryTrackEntity>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val selectedTracks = remember { mutableStateMapOf<String, LibraryTrackEntity>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val collections = LibraryRepository.collections(context)
            allTracks = collections.tracks
            isLoading = false
        }
    }

    val filteredTracks = remember(allTracks, query) {
        if (query.isBlank()) {
            allTracks
        } else {
            val q = query.trim()
            allTracks.filter { track ->
                track.title.contains(q, ignoreCase = true) ||
                    track.artist.contains(q, ignoreCase = true) ||
                    track.album.contains(q, ignoreCase = true)
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ) {
            AddFromLibraryPickerSheetContent(
                tracks = filteredTracks,
                query = query,
                onQueryChange = { query = it },
                selectedTracks = selectedTracks,
                isLoading = isLoading,
                onConfirm = { onConfirm(selectedTracks.values.toList()) },
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
                    AddFromLibraryPickerSheetContent(
                        tracks = filteredTracks,
                        query = query,
                        onQueryChange = { query = it },
                        selectedTracks = selectedTracks,
                        isLoading = isLoading,
                        onConfirm = { onConfirm(selectedTracks.values.toList()) },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFromLibraryPickerSheetContent(
    tracks: List<LibraryTrackEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTracks: MutableMap<String, LibraryTrackEntity>,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add from Library",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = selectedTracks.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FilledTonalButton(
                    onClick = onConfirm,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Add (${selectedTracks.size})")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Find tracks, artists, albums") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            } else null,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
        )
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
            if (isLoading && tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                }
            } else if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isNotBlank()) "No matching tracks" else "No indexed tracks in library",
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
                        items = tracks,
                        key = { it.path }
                    ) { track ->
                        val isChecked = selectedTracks.containsKey(track.path)
                        LibraryTrackPickerRow(
                            track = track,
                            isChecked = isChecked,
                            onToggle = {
                                if (isChecked) {
                                    selectedTracks.remove(track.path)
                                } else {
                                    selectedTracks[track.path] = track
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTrackPickerRow(
    track: LibraryTrackEntity,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtext = listOfNotNull(
                track.artist.takeIf { it.isNotBlank() },
                track.album.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (subtext.isNotBlank()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() }
        )
    }
}

/**
 * Multi-select track picker sheet from storage / file browser.
 */
@Composable
internal fun AddFromStoragePickerSheet(
    onConfirm: (List<File>) -> Unit,
    onDismiss: () -> Unit
) {
    val supportedExtensions = remember {
        try {
            NativeBridge.getSupportedExtensions().toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }
    StorageFilePickerSheet(
        title = "Add from Storage",
        singleSelect = false,
        fileFilter = { fileMatchesSupportedExtensions(it, supportedExtensions) },
        fileIcon = Icons.Default.MusicNote,
        emptyText = "No folders or supported audio files",
        onConfirmFiles = onConfirm,
        onDismiss = onDismiss
    )
}

/**
 * Multi-select track picker sheet from saved Network shares / SMB servers / streams.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddFromNetworkPickerSheet(
    networkNodes: List<NetworkNode>,
    onConfirm: (List<PlaylistTrackEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    val content = @Composable {
        AddFromNetworkPickerSheetContent(
            networkNodes = networkNodes,
            onConfirm = onConfirm,
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

private data class SmbBrowseTarget(
    val node: NetworkNode,
    val spec: SmbSourceSpec,
    val initialHasShare: Boolean,
    val currentShare: String?,
    val currentPath: String = ""
)

private fun parentSmbPath(path: String): String {
    val trimmed = path.trim().replace('\\', '/').trim('/')
    val lastSlash = trimmed.lastIndexOf('/')
    return if (lastSlash >= 0) trimmed.substring(0, lastSlash) else ""
}

@Composable
private fun AddFromNetworkPickerSheetContent(
    networkNodes: List<NetworkNode>,
    onConfirm: (List<PlaylistTrackEntry>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentFolderId by remember { mutableStateOf<Long?>(null) }
    var activeSmbTarget by remember { mutableStateOf<SmbBrowseTarget?>(null) }
    val selectedItems = remember { mutableStateMapOf<String, PlaylistTrackEntry>() }

    var smbEntries by remember { mutableStateOf<List<SmbBrowserEntry>>(emptyList()) }
    var isLoadingSmb by remember { mutableStateOf(false) }
    var smbErrorMessage by remember { mutableStateOf<String?>(null) }

    val supportedExtensions = remember {
        try {
            NativeBridge.getSupportedExtensions().toSet()
        } catch (_: Throwable) {
            emptySet()
        }
    }

    val canGoUp = activeSmbTarget != null || currentFolderId != null

    val navigateUp: () -> Unit = {
        val target = activeSmbTarget
        if (target != null) {
            if (target.currentPath.isNotEmpty()) {
                activeSmbTarget = target.copy(currentPath = parentSmbPath(target.currentPath))
            } else if (!target.initialHasShare && target.currentShare != null) {
                activeSmbTarget = target.copy(currentShare = null, currentPath = "")
            } else {
                activeSmbTarget = null
                smbEntries = emptyList()
                smbErrorMessage = null
            }
        } else if (currentFolderId != null) {
            val currentFolder = networkNodes.firstOrNull { it.id == currentFolderId }
            currentFolderId = currentFolder?.parentId
        }
    }

    BackHandler(enabled = canGoUp) {
        navigateUp()
    }

    LaunchedEffect(activeSmbTarget?.currentShare, activeSmbTarget?.currentPath) {
        val target = activeSmbTarget ?: return@LaunchedEffect
        isLoadingSmb = true
        smbErrorMessage = null
        withContext(Dispatchers.IO) {
            if (target.currentShare.isNullOrBlank()) {
                val result = listSmbHostShareEntries(target.spec)
                result.fold(
                    onSuccess = { entries ->
                        smbEntries = entries
                        isLoadingSmb = false
                    },
                    onFailure = { err ->
                        smbEntries = emptyList()
                        smbErrorMessage = err.localizedMessage ?: "Failed to list SMB shares"
                        isLoadingSmb = false
                    }
                )
            } else {
                val result = listSmbDirectoryEntries(
                    spec = target.spec.copy(share = target.currentShare.orEmpty()),
                    pathInsideShare = target.currentPath
                )
                result.fold(
                    onSuccess = { entries ->
                        smbEntries = entries
                        isLoadingSmb = false
                    },
                    onFailure = { err ->
                        smbEntries = emptyList()
                        smbErrorMessage = err.localizedMessage ?: "Failed to list SMB directory"
                        isLoadingSmb = false
                    }
                )
            }
        }
    }

    val currentTarget = activeSmbTarget
    val isInSmb = currentTarget != null
    val isInsideShare = isInSmb && !currentTarget.currentShare.isNullOrBlank()

    val currentAudioFilesInShare = remember(smbEntries, supportedExtensions, isInsideShare) {
        if (!isInsideShare) emptyList()
        else smbEntries.filter {
            !it.isDirectory && !it.name.startsWith(".") &&
            (fileMatchesSupportedExtensions(File(it.name), supportedExtensions) || isSupportedPlaylistFileName(it.name))
        }
    }

    val allInDirSelected = remember(currentAudioFilesInShare, selectedItems, currentTarget) {
        if (currentAudioFilesInShare.isEmpty() || currentTarget == null) false
        else currentAudioFilesInShare.all { entry ->
            val targetSpec = buildSmbEntrySourceSpec(
                rootSpec = currentTarget.spec.copy(share = currentTarget.currentShare.orEmpty()),
                pathInsideShare = joinSmbRelativePath(currentTarget.currentPath, entry.name)
            )
            selectedItems.containsKey(buildSmbRequestUri(targetSpec))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Add from Network",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AnimatedVisibility(
                visible = selectedItems.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FilledTonalButton(
                    onClick = { onConfirm(selectedItems.values.toList()) },
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text("Add (${selectedItems.size})")
                }
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
                .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = navigateUp,
                enabled = canGoUp
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                val titleText = when {
                    currentTarget != null -> {
                        if (currentTarget.currentShare == null) {
                            currentTarget.node.title.ifBlank { currentTarget.spec.host }
                        } else if (currentTarget.currentPath.isBlank()) {
                            currentTarget.currentShare
                        } else {
                            currentTarget.currentPath.substringAfterLast('/')
                        }
                    }
                    currentFolderId != null -> {
                        networkNodes.firstOrNull { it.id == currentFolderId }?.title ?: "Folder"
                    }
                    else -> "Saved Network Sources"
                }

                val subtitleText = when {
                    currentTarget != null -> {
                        if (currentTarget.currentShare == null) {
                            "smb://${currentTarget.spec.host}"
                        } else if (currentTarget.currentPath.isBlank()) {
                            "smb://${currentTarget.spec.host}/${currentTarget.currentShare}"
                        } else {
                            "smb://${currentTarget.spec.host}/${currentTarget.currentShare}/${currentTarget.currentPath}"
                        }
                    }
                    currentFolderId != null -> "Network Folder"
                    else -> "Shares, servers, and streams"
                }

                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (currentAudioFilesInShare.isNotEmpty() && currentTarget != null) {
                TextButton(
                    onClick = {
                        if (allInDirSelected) {
                            currentAudioFilesInShare.forEach { entry ->
                                val targetSpec = buildSmbEntrySourceSpec(
                                    rootSpec = currentTarget.spec.copy(share = currentTarget.currentShare.orEmpty()),
                                    pathInsideShare = joinSmbRelativePath(currentTarget.currentPath, entry.name)
                                )
                                selectedItems.remove(buildSmbRequestUri(targetSpec))
                            }
                        } else {
                            currentAudioFilesInShare.forEach { entry ->
                                val targetSpec = buildSmbEntrySourceSpec(
                                    rootSpec = currentTarget.spec.copy(share = currentTarget.currentShare.orEmpty()),
                                    pathInsideShare = joinSmbRelativePath(currentTarget.currentPath, entry.name)
                                )
                                val uri = buildSmbRequestUri(targetSpec)
                                selectedItems[uri] = PlaylistTrackEntry(
                                    id = java.util.UUID.randomUUID().toString(),
                                    source = uri,
                                    title = inferredDisplayTitleForName(entry.name),
                                    addedAtMs = System.currentTimeMillis()
                                )
                            }
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
            if (isLoadingSmb) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (currentTarget != null) {
                // Inside SMB browse target
                if (smbErrorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = smbErrorMessage ?: "Error",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = {
                                activeSmbTarget = currentTarget.copy()
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                } else if (isLoadingSmb && smbEntries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                } else if (currentTarget.currentShare.isNullOrBlank()) {
                    // Listing shares
                    val shares = smbEntries.filter { it.isDirectory && !it.isHidden }
                    if (shares.isEmpty() && !isLoadingSmb) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No shares found on this host",
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
                                items = shares,
                                key = { it.name }
                            ) { share ->
                                SmbShareRow(
                                    name = share.name,
                                    onClick = {
                                        activeSmbTarget = currentTarget.copy(currentShare = share.name, currentPath = "")
                                    }
                                )
                            }
                        }
                    }
                } else {
                    val dirs = smbEntries.filter { it.isDirectory && !it.name.startsWith(".") }
                    val audioFiles = currentAudioFilesInShare
                    if (dirs.isEmpty() && audioFiles.isEmpty() && !isLoadingSmb) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No folders or supported audio files",
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
                                items = dirs,
                                key = { "dir_${it.name}" }
                            ) { dir ->
                                SmbDirectoryRow(
                                    name = dir.name,
                                    onClick = {
                                        activeSmbTarget = currentTarget.copy(
                                            currentPath = joinSmbRelativePath(currentTarget.currentPath, dir.name)
                                        )
                                    }
                                )
                            }
                            items(
                                items = audioFiles,
                                key = { "file_${it.name}" }
                            ) { file ->
                                val targetSpec = buildSmbEntrySourceSpec(
                                    rootSpec = currentTarget.spec.copy(share = currentTarget.currentShare.orEmpty()),
                                    pathInsideShare = joinSmbRelativePath(currentTarget.currentPath, file.name)
                                )
                                val uri = buildSmbRequestUri(targetSpec)
                                val isChecked = selectedItems.containsKey(uri)
                                SmbFileRow(
                                    entry = file,
                                    isChecked = isChecked,
                                    onToggle = {
                                        if (isChecked) {
                                            selectedItems.remove(uri)
                                        } else {
                                            selectedItems[uri] = PlaylistTrackEntry(
                                                id = java.util.UUID.randomUUID().toString(),
                                                source = uri,
                                                title = inferredDisplayTitleForName(file.name),
                                                addedAtMs = System.currentTimeMillis()
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                val visibleNodes = remember(networkNodes, currentFolderId) {
                    val nodes = networkNodes.filter { it.parentId == currentFolderId }
                    nodes.sortedWith(
                        compareBy<NetworkNode> { it.type != NetworkNodeType.Folder }
                            .thenBy { it.title.lowercase(java.util.Locale.ROOT) }
                    )
                }

                if (visibleNodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = if (currentFolderId == null) "No saved network sources" else "Folder is empty",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (currentFolderId == null) {
                                Text(
                                    text = "Add SMB servers, shares, or network streams in the Network tab to browse and add them to playlists.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = visibleNodes,
                            key = { it.id }
                        ) { node ->
                            when (node.type) {
                                NetworkNodeType.Folder -> {
                                    NetworkFolderRow(
                                        title = node.title,
                                        onClick = { currentFolderId = node.id }
                                    )
                                }
                                NetworkNodeType.RemoteSource -> {
                                    val isSmb = node.sourceKind == NetworkSourceKind.Smb
                                    val smbSpec = if (isSmb) resolveNetworkNodeSmbSpec(node) else null
                                    val isSmbSingleFile = isSmb && node.smbPath?.let {
                                        fileMatchesSupportedExtensions(File(it), supportedExtensions) || isSupportedPlaylistFileName(it)
                                    } == true

                                    if (isSmb && !isSmbSingleFile && smbSpec != null) {
                                        NetworkSmbNodeRow(
                                            node = node,
                                            onClick = {
                                                val hasShare = !node.smbShare.isNullOrBlank()
                                                activeSmbTarget = SmbBrowseTarget(
                                                    node = node,
                                                    spec = smbSpec,
                                                    initialHasShare = hasShare,
                                                    currentShare = node.smbShare?.ifBlank { null },
                                                    currentPath = node.smbPath.orEmpty()
                                                )
                                            }
                                        )
                                    } else {
                                        val trackSource = resolveNetworkNodeOpenInput(node) ?: node.source.orEmpty()
                                        val isChecked = selectedItems.containsKey(trackSource)
                                        NetworkStreamNodeRow(
                                            node = node,
                                            isChecked = isChecked,
                                            onToggle = {
                                                if (isChecked) {
                                                    selectedItems.remove(trackSource)
                                                } else {
                                                    selectedItems[trackSource] = PlaylistTrackEntry(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        source = trackSource,
                                                        title = resolveNetworkNodeDisplayTitle(node),
                                                        artist = node.metadataArtist,
                                                        addedAtMs = System.currentTimeMillis()
                                                    )
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
        }
    }
}

@Composable
private fun SmbShareRow(
    name: String,
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
                    imageVector = NetworkIcons.SmbShare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = name,
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
private fun SmbDirectoryRow(
    name: String,
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
            text = name,
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
private fun SmbFileRow(
    entry: SmbBrowserEntry,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = inferredDisplayTitleForName(entry.name),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val ext = entry.name.substringAfterLast('.', "").uppercase()
            val size = formatFileSize(entry.sizeBytes)
            Text(
                text = if (ext.isNotEmpty()) "$ext • $size" else size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun NetworkFolderRow(
    title: String,
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NetworkSmbNodeRow(
    node: NetworkNode,
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
                    imageVector = NetworkIcons.SmbShare,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resolveNetworkNodeDisplayTitle(node),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtext = listOfNotNull(
                node.smbHost?.let { "smb://$it" },
                node.smbShare?.takeIf { it.isNotBlank() }
            ).joinToString("/")
            if (subtext.isNotBlank()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NetworkStreamNodeRow(
    node: NetworkNode,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
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
                    imageVector = if (node.sourceKind == NetworkSourceKind.Smb) Icons.Default.MusicNote else Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resolveNetworkNodeDisplayTitle(node),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = node.source?.takeIf { it.isNotBlank() } ?: node.metadataArtist
            if (!sub.isNullOrBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() }
        )
    }
}
