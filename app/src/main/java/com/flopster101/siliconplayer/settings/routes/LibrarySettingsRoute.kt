package com.flopster101.siliconplayer.settings.routes

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.PlaybackService
import com.flopster101.siliconplayer.PlaylistMetadataRefreshStatus
import com.flopster101.siliconplayer.PlaylistMetadataRefresher
import com.flopster101.siliconplayer.PlayerSettingToggleCard
import com.flopster101.siliconplayer.SettingsItemCard
import com.flopster101.siliconplayer.SettingsRowContainer
import com.flopster101.siliconplayer.SettingsRowSpacer
import com.flopster101.siliconplayer.SettingsSectionLabel
import com.flopster101.siliconplayer.library.LibraryContract
import com.flopster101.siliconplayer.library.LibraryRepository
import com.flopster101.siliconplayer.library.LibraryScanRoot
import com.flopster101.siliconplayer.readPlaylistLibraryState
import com.flopster101.siliconplayer.writePlaylistLibraryState
import kotlinx.coroutines.launch

@Composable
internal fun LibrarySettingsRouteContent(
    onOpenScanner: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.getSharedPreferences(com.flopster101.siliconplayer.AppPreferenceKeys.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    var showFavoritesInPlaylistChooser by remember {
        mutableStateOf(
            prefs.getBoolean(
                com.flopster101.siliconplayer.AppPreferenceKeys.LIBRARY_SHOW_FAVORITES_IN_PLAYLIST_CHOOSER,
                false
            )
        )
    }
    var autoGenerateMosaics by remember {
        mutableStateOf(
            prefs.getBoolean(
                com.flopster101.siliconplayer.AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC,
                true
            )
        )
    }

    var sources by remember {
        mutableStateOf<List<com.flopster101.siliconplayer.library.LibrarySourceStatus>>(emptyList())
    }
    var deduplicateSources by remember { mutableStateOf(true) }
    val librarySyncState by LibraryRepository.scanState.collectAsState()
    val isScanning = librarySyncState.isScanning
    val isWatch = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) }
    val metadataRefreshState by PlaylistMetadataRefresher.state.collectAsState()
    var showRefreshConfirmDialog by remember { mutableStateOf(false) }
    var refreshLocalOnlyChoice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        sources = LibraryRepository.sourceStatuses(context)
        deduplicateSources = LibraryRepository.deduplicateSources(context)
    }
    var scanWasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(librarySyncState.isScanning) {
        if (scanWasRunning && !isScanning) {
            sources = LibraryRepository.sourceStatuses(context)
        }
        scanWasRunning = isScanning
    }

    // Rows register sequencer roles on first composition; the whole section
    // re-registers as one pass on any data change so the row sequencer
    // computes roles (corner grouping) from a clean section boundary.
    fun sourceLabel(id: String): String = when (id) {
        LibraryContract.SOURCE_MEDIASTORE -> "MediaStore"
        LibraryContract.SOURCE_SCANNER -> "Storage scanner"
        else -> id
    }

    fun sourceDescription(id: String): String = when (id) {
        LibraryContract.SOURCE_MEDIASTORE ->
            "System media index. Covers conventional formats with no configuration."
        LibraryContract.SOURCE_SCANNER ->
            "Scans your folders directly, including formats MediaStore cannot index."
        else -> ""
    }

    key(sources.map { it.id }) {
    SettingsSectionLabel("Sources")
    sources.forEachIndexed { index, source ->
        if (source.id == LibraryContract.SOURCE_SCANNER) {
            SettingsRowContainer(
                onClick = onOpenScanner
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sourceLabel(source.id),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sourceDescription(source.id),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (source.lastSyncMs > 0L) {
                            "${source.trackCount} tracks"
                        } else {
                            "Not scanned yet"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Switch(
                    checked = source.enabled,
                    onCheckedChange = { checked ->
                        coroutineScope.launch {
                            LibraryRepository.setSourceEnabled(context, source.id, checked)
                            sources = LibraryRepository.sourceStatuses(context)
                        }
                    }
                )
            }
        } else {
            PlayerSettingToggleCard(
                title = sourceLabel(source.id),
                description = sourceDescription(source.id),
                checked = source.enabled,
                onCheckedChange = { checked ->
                    coroutineScope.launch {
                        LibraryRepository.setSourceEnabled(context, source.id, checked)
                        sources = LibraryRepository.sourceStatuses(context)
                    }
                },
                badgeText = if (source.lastSyncMs > 0L) {
                    "${source.trackCount} tracks"
                } else {
                    "Not scanned yet"
                }
            )
        }
        if (index < sources.lastIndex) {
            SettingsRowSpacer()
        }
    }
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Duplicates")
    PlayerSettingToggleCard(
        title = "Deduplicate tracks",
        description = "List files found by both MediaStore and the storage scanner only once, keeping the scanner copy. Takes effect the next time the library loads.",
        checked = deduplicateSources,
        onCheckedChange = { checked ->
            deduplicateSources = checked
            coroutineScope.launch { LibraryRepository.setDeduplicateSources(context, checked) }
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Playlists")
    PlayerSettingToggleCard(
        title = "Show Favorites in playlist menu",
        description = "Include Favorites as the top entry in the Add to playlist menu.",
        checked = showFavoritesInPlaylistChooser,
        onCheckedChange = { checked ->
            showFavoritesInPlaylistChooser = checked
            prefs.edit()
                .putBoolean(
                    com.flopster101.siliconplayer.AppPreferenceKeys.LIBRARY_SHOW_FAVORITES_IN_PLAYLIST_CHOOSER,
                    checked
                )
                .apply()
        }
    )
    SettingsRowSpacer()
    PlayerSettingToggleCard(
        title = "Auto-generate playlist mosaics",
        description = "Create 2x2 cover collages for playlists with album artwork.",
        checked = autoGenerateMosaics,
        onCheckedChange = { checked ->
            autoGenerateMosaics = checked
            prefs.edit()
                .putBoolean(
                    com.flopster101.siliconplayer.AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC,
                    checked
                )
                .apply()
        }
    )

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Scanning")
    SettingsItemCard(
        title = "Scan now",
        description = if (isScanning) "Scanning…" else "Refresh all enabled sources now.",
        icon = Icons.Default.Refresh,
        onClick = {
            if (isScanning) return@SettingsItemCard
            LibraryRepository.requestScan(context)
        },
        enabled = !isScanning
    )
    SettingsRowSpacer()
    val isRunning = metadataRefreshState.status == PlaylistMetadataRefreshStatus.Running
    val refreshTitle = when (metadataRefreshState.status) {
        PlaylistMetadataRefreshStatus.Running -> "Refreshing metadata…"
        PlaylistMetadataRefreshStatus.Success -> "All metadata refreshed"
        PlaylistMetadataRefreshStatus.PartialSuccess -> "Metadata partially refreshed"
        PlaylistMetadataRefreshStatus.Failed -> "Metadata refresh failed"
        PlaylistMetadataRefreshStatus.Idle -> "Refresh all metadata"
    }
    val refreshDesc = when (metadataRefreshState.status) {
        PlaylistMetadataRefreshStatus.Running ->
            if (metadataRefreshState.total > 0) "Refreshing track ${metadataRefreshState.current} of ${metadataRefreshState.total}…"
            else "Scanning playlist tracks…"
        PlaylistMetadataRefreshStatus.Success ->
            "Successfully refreshed ${metadataRefreshState.succeededCount} tracks. Tap to clear."
        PlaylistMetadataRefreshStatus.PartialSuccess ->
            "${metadataRefreshState.succeededCount} refreshed, ${metadataRefreshState.failedCount} failed. Tap to clear."
        PlaylistMetadataRefreshStatus.Failed ->
            "Failed to refresh ${metadataRefreshState.failedCount} tracks. Tap to clear."
        PlaylistMetadataRefreshStatus.Idle ->
            "Probe and refresh tags and durations for all playlist tracks."
    }
    val refreshIcon = when (metadataRefreshState.status) {
        PlaylistMetadataRefreshStatus.Success -> Icons.Default.CheckCircle
        PlaylistMetadataRefreshStatus.PartialSuccess -> Icons.Default.Warning
        PlaylistMetadataRefreshStatus.Failed -> Icons.Default.Error
        else -> Icons.Default.Refresh
    }
    val refreshIconTint = when (metadataRefreshState.status) {
        PlaylistMetadataRefreshStatus.Success -> Color(0xFF4CAF50)
        PlaylistMetadataRefreshStatus.PartialSuccess -> Color(0xFFFF9800)
        PlaylistMetadataRefreshStatus.Failed -> MaterialTheme.colorScheme.error
        else -> null
    }
    SettingsItemCard(
        title = refreshTitle,
        description = refreshDesc,
        icon = refreshIcon,
        iconTint = refreshIconTint,
        onClick = {
            when (metadataRefreshState.status) {
                PlaylistMetadataRefreshStatus.Running -> Unit
                PlaylistMetadataRefreshStatus.Success,
                PlaylistMetadataRefreshStatus.PartialSuccess,
                PlaylistMetadataRefreshStatus.Failed -> {
                    PlaylistMetadataRefresher.resetState()
                }
                PlaylistMetadataRefreshStatus.Idle -> {
                    showRefreshConfirmDialog = true
                }
            }
        },
        enabled = !isRunning,
        leadingContent = if (isRunning) {
            {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isWatch) 20.dp else 24.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else null
    )

    if (showRefreshConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRefreshConfirmDialog = false },
            title = { Text("Refresh all metadata?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Refreshing metadata probes all playlist tracks to update titles, artists, albums, and track durations. It may take a while depending on playlist size, and music playback will be stopped while it happens.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { refreshLocalOnlyChoice = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !refreshLocalOnlyChoice,
                                onClick = { refreshLocalOnlyChoice = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "All tracks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Refreshes local tracks first, then remote streams",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { refreshLocalOnlyChoice = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = refreshLocalOnlyChoice,
                                onClick = { refreshLocalOnlyChoice = true }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Local-only tracks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Skips network and remote stream tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val localOnly = refreshLocalOnlyChoice
                        showRefreshConfirmDialog = false
                        coroutineScope.launch {
                            PlaylistMetadataRefresher.refreshAllPlaylists(
                                context = context,
                                localOnly = localOnly,
                                onStopPlayback = {
                                    context.startService(
                                        Intent(context, PlaybackService::class.java).setAction(PlaybackService.ACTION_STOP_CLEAR)
                                    )
                                },
                                playlistLibraryStateProvider = { readPlaylistLibraryState(prefs) },
                                onPlaylistLibraryStateChanged = { newState -> writePlaylistLibraryState(prefs, newState) }
                            )
                        }
                    }
                ) {
                    Text("Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRefreshConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun LibraryScannerRouteContent() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var roots by remember { mutableStateOf<List<LibraryScanRoot>>(emptyList()) }
    var extensions by remember { mutableStateOf("") }
    var autoScanEnabled by remember { mutableStateOf(true) }
    var showAddRootDialog by remember { mutableStateOf(false) }
    var showExtensionsDialog by remember { mutableStateOf(false) }
    var pathInput by remember { mutableStateOf("") }
    var pathError by remember { mutableStateOf<String?>(null) }
    var extensionsInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        roots = LibraryRepository.scanRoots(context)
        extensions = LibraryRepository.scannerExtensions(context).joinToString(", ")
        autoScanEnabled = LibraryRepository.autoScanEnabled(context)
    }

    fun reload() {
        coroutineScope.launch {
            roots = LibraryRepository.scanRoots(context)
            extensions = LibraryRepository.scannerExtensions(context).joinToString(", ")
            autoScanEnabled = LibraryRepository.autoScanEnabled(context)
        }
    }

    fun submitPath() {
        val trimmed = pathInput.trim()
        val directory = java.io.File(trimmed)
        when {
            trimmed.isEmpty() -> return
            !directory.isDirectory -> pathError = "Folder not found"
            roots.any { it.path == directory.absolutePath } ->
                pathError = "Folder already added"
            else -> {
                pathError = null
                pathInput = ""
                showAddRootDialog = false
                coroutineScope.launch {
                    LibraryRepository.setScanRoots(context, roots + LibraryScanRoot(directory.absolutePath))
                    LibraryRepository.setSourceEnabled(context, LibraryContract.SOURCE_SCANNER, true)
                    reload()
                }
            }
        }
    }

    fun toggleRoot(root: LibraryScanRoot) {
        coroutineScope.launch {
            LibraryRepository.setScanRoots(
                context,
                roots.map { if (it.path == root.path) it.copy(enabled = !it.enabled) else it }
            )
            reload()
        }
    }

    fun removeRoot(root: LibraryScanRoot) {
        coroutineScope.launch {
            LibraryRepository.setScanRoots(context, roots.filterNot { it.path == root.path })
            reload()
        }
    }

    fun addRoot(path: String) {
        coroutineScope.launch {
            LibraryRepository.setScanRoots(context, roots + LibraryScanRoot(path))
            LibraryRepository.setSourceEnabled(context, LibraryContract.SOURCE_SCANNER, true)
            reload()
        }
    }

    fun saveExtensions(parsed: Set<String>) {
        coroutineScope.launch {
            LibraryRepository.setScannerExtensions(context, parsed)
            reload()
        }
    }

    if (showAddRootDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddRootDialog = false
                pathInput = ""
                pathError = null
            },
            title = { Text("Add scanner folder") },
            text = {
                OutlinedTextField(
                    value = pathInput,
                    onValueChange = {
                        pathInput = it
                        pathError = null
                    },
                    label = { Text("Folder path") },
                    placeholder = { Text("/storage/emulated/0/Music") },
                    isError = pathError != null,
                    supportingText = pathError?.let { error -> { Text(error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = ::submitPath) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddRootDialog = false
                    pathInput = ""
                    pathError = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExtensionsDialog) {
        AlertDialog(
            onDismissRequest = { showExtensionsDialog = false },
            title = { Text("File types") },
            text = {
                OutlinedTextField(
                    value = extensionsInput,
                    onValueChange = { extensionsInput = it },
                    label = { Text("Extensions (comma separated)") },
                    placeholder = { Text("mp3, flac, ogg") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsed = extensionsInput.split(',', ' ', ';', '\n')
                            .map { it.trim().lowercase().removePrefix(".") }
                            .filter { it.isNotBlank() }
                            .toSet()
                        if (parsed.isNotEmpty()) {
                            saveExtensions(parsed)
                        }
                        showExtensionsDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExtensionsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    key(roots.map { it.path }) {
    SettingsSectionLabel("Folders")
    roots.forEach { root ->
        SettingsRowContainer(onClick = { toggleRoot(root) }) {
            Icon(
                imageVector = Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = root.path,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (root.enabled) "Included in scans" else "Excluded from scans",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { removeRoot(root) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SettingsRowSpacer()
    }
    SettingsItemCard(
        title = "Add folder",
        description = "Scan a folder and its subfolders.",
        icon = Icons.Default.Add,
        onClick = { showAddRootDialog = true }
    )
    }

    Spacer(modifier = Modifier.height(16.dp))
    SettingsSectionLabel("Scanning")
    PlayerSettingToggleCard(
        title = "Scan automatically",
        description = "Refresh the library when it opens if sources are stale.",
        checked = autoScanEnabled,
        onCheckedChange = { checked ->
            autoScanEnabled = checked
            coroutineScope.launch { LibraryRepository.setAutoScanEnabled(context, checked) }
        }
    )
    SettingsRowSpacer()
    SettingsItemCard(
        title = "File types",
        description = extensions.ifBlank { "Default set" },
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        onClick = {
            extensionsInput = extensions
            showExtensionsDialog = true
        }
    )
}
