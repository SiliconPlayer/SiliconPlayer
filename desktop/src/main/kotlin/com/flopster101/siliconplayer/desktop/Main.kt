package com.flopster101.siliconplayer.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import com.flopster101.siliconplayer.HomeScreen
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.MiniPlayerBar
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.StoragePresentation
import com.flopster101.siliconplayer.FolderEntryAction
import com.flopster101.siliconplayer.SourceEntryAction
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkCredentialStore
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.resolveNetworkNodeHttpSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.readNetworkNodes
import com.flopster101.siliconplayer.writeNetworkNodes
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.PlatformBackHandler
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.screens.rememberVisualizationUiState
import com.flopster101.siliconplayer.ui.dialogs.TrackInfoDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.flopster101.siliconplayer.ui.screens.PlaylistsScreen
import com.flopster101.siliconplayer.ui.screens.LibrarySurfaceState
import com.flopster101.siliconplayer.PlaylistEntrySortMode
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistStoredFormat
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.appendStoredPlaylistEntries
import com.flopster101.siliconplayer.library.LibraryCollections
import com.flopster101.siliconplayer.moveFavoriteTrack
import com.flopster101.siliconplayer.moveStoredPlaylistEntry
import com.flopster101.siliconplayer.readPlaylistLibraryState
import com.flopster101.siliconplayer.removeFavoriteTrack
import com.flopster101.siliconplayer.removeFavoriteTracks
import com.flopster101.siliconplayer.removeStoredPlaylistEntries
import com.flopster101.siliconplayer.removeStoredPlaylistEntry
import com.flopster101.siliconplayer.renameStoredPlaylist
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.setStoredPlaylistPinned
import com.flopster101.siliconplayer.upsertFavoriteTrack
import com.flopster101.siliconplayer.upsertStoredPlaylist
import com.flopster101.siliconplayer.writePlaylistLibraryState
import com.flopster101.siliconplayer.SettingsScreen
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.MainView
import com.flopster101.siliconplayer.SettingsRoute
import com.flopster101.siliconplayer.BrowserRouteMode
import com.flopster101.siliconplayer.rememberBrowserRouteRenderState
import com.flopster101.siliconplayer.resolveBrowserRouteResolution
import com.flopster101.siliconplayer.MainNavigationScaffold
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.LocalPlayerExitSlideFraction
import com.flopster101.siliconplayer.LocalPlayerOverlayVisibility
import com.flopster101.siliconplayer.ThemeMode
import com.flopster101.siliconplayer.canSeekPlayback
import com.flopster101.siliconplayer.formatShortDuration
import com.flopster101.siliconplayer.hasReliableDuration
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.supportsLiveRepeatMode
import com.flopster101.siliconplayer.ui.screens.LocalPlayerFocusIndicatorsEnabled
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerBaseTheme
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

fun openDesktopFileChooser(onFileSelected: (File) -> Unit) {
    SwingUtilities.invokeLater {
        val chooser = JFileChooser()
        val result = chooser.showOpenDialog(null)
        if (result == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
            onFileSelected(chooser.selectedFile)
        }
    }
}

fun main(args: Array<String>) = application {
    DesktopPaths.install()
    val session = remember { DesktopPlaybackSession() }
    val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
    val backDispatcher = remember { DesktopBackDispatcher() }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var currentDirectory by remember {
        mutableStateOf(File(System.getProperty("user.home") ?: "/"))
    }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }

    val networkNodes = remember { mutableStateListOf<NetworkNode>() }
    var currentNetworkFolderId by remember { mutableStateOf<Long?>(null) }

    var remoteBrowserInput by remember { mutableStateOf<String?>(null) }
    var remoteSmbSourceNodeId by remember { mutableStateOf<Long?>(null) }
    var remoteHttpSourceNodeId by remember { mutableStateOf<Long?>(null) }
    var remoteHttpRootPath by remember { mutableStateOf<String?>(null) }
    var remoteSmbAllowHostShareNavigation by remember { mutableStateOf(false) }
    var browserReturnView by remember { mutableStateOf(MainView.Home) }

    fun openLocalBrowser(directory: File) {
        currentDirectory = directory
        remoteBrowserInput = null
        remoteSmbSourceNodeId = null
        remoteHttpSourceNodeId = null
        remoteHttpRootPath = null
        remoteSmbAllowHostShareNavigation = false
        browserReturnView = MainView.Home
        currentView = MainView.Browser
    }

    fun openRemoteBrowser(
        input: String,
        smbSourceNodeId: Long?,
        httpSourceNodeId: Long?,
        httpRootPath: String?,
        allowHostShareNavigation: Boolean
    ) {
        remoteBrowserInput = input
        remoteSmbSourceNodeId = smbSourceNodeId
        remoteHttpSourceNodeId = httpSourceNodeId
        remoteHttpRootPath = httpRootPath
        remoteSmbAllowHostShareNavigation = allowHostShareNavigation
        browserReturnView = MainView.Network
        currentView = MainView.Browser
    }

    val recentFiles = remember { mutableStateListOf<RecentPathEntry>() }
    val recentFolders = remember { mutableStateListOf<RecentPathEntry>() }
    val pinnedEntries = remember { mutableStateListOf<HomePinnedEntry>() }

    fun pinHomeEntry(entry: RecentPathEntry, isFolder: Boolean) {
        if (pinnedEntries.none { it.path == entry.path }) {
            pinnedEntries.add(
                HomePinnedEntry(
                    path = entry.path,
                    isFolder = isFolder,
                    title = entry.title,
                    artist = entry.artist,
                    sourceNodeId = entry.sourceNodeId
                )
            )
        }
    }

    fun registerLoadedFile(file: File) {
        val ext = inferredPrimaryExtensionForName(file.name)?.uppercase(Locale.ROOT) ?: "FILE"
        val entry = RecentPathEntry(
            path = file.absolutePath,
            locationId = null,
            title = session.title.ifBlank { file.name },
            artist = session.artist.ifBlank { ext },
            decoderName = session.decoderName
        )
        recentFiles.removeAll { it.path == file.absolutePath }
        recentFiles.add(0, entry)
        if (recentFiles.size > 20) {
            recentFiles.removeLast()
        }

        val parent = file.parentFile
        if (parent != null) {
            val folderEntry = RecentPathEntry(
                path = parent.absolutePath,
                locationId = null,
                title = parent.name,
                artist = parent.absolutePath
            )
            recentFolders.removeAll { it.path == parent.absolutePath }
            recentFolders.add(0, folderEntry)
            if (recentFolders.size > 10) {
                recentFolders.removeLast()
            }
        }
    }

    fun playFile(file: File) {
        if (session.loadFile(file)) {
            registerLoadedFile(file)
        }
    }

    fun playSource(source: String, titleHint: String? = null, artistHint: String? = null) {
        val file = File(source)
        if (file.exists() && file.isFile) {
            playFile(file)
            return
        }
        if (session.loadSource(source, titleHint, artistHint)) {
            val entry = RecentPathEntry(
                path = source,
                locationId = null,
                title = session.title.ifBlank { titleHint ?: source },
                artist = session.artist.ifBlank { artistHint ?: "Network" },
                decoderName = session.decoderName
            )
            recentFiles.removeAll { it.path == source }
            recentFiles.add(0, entry)
            if (recentFiles.size > 20) {
                recentFiles.removeLast()
            }
        }
    }

    LaunchedEffect(args) {
        if (args.isNotEmpty()) {
            val candidate = File(args[0])
            if (candidate.exists() && candidate.isFile) {
                playFile(candidate)
            }
        }
    }

    val windowTitle = if (session.title.isNotBlank()) {
        "${session.title} - SiliconPlayer"
    } else {
        "SiliconPlayer Desktop"
    }

    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var externalTrackInfoDialogRequestToken by remember { mutableIntStateOf(0) }

    Window(
        onCloseRequest = {
            session.dispose()
            exitApplication()
        },
        state = windowState,
        title = windowTitle,
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                if (keyEvent.key == Key.I) {
                    if (session.currentFile != null) {
                        if (isPlayerExpanded) {
                            externalTrackInfoDialogRequestToken += 1
                        } else {
                            showTrackInfoDialog = !showTrackInfoDialog
                        }
                        return@Window true
                    }
                } else if (keyEvent.key == Key.Escape) {
                    if (backDispatcher.onBackPressed()) {
                        return@Window true
                    }
                    if (showTrackInfoDialog) {
                        showTrackInfoDialog = false
                        return@Window true
                    }
                    if (showSubtuneSelectorDialog) {
                        showSubtuneSelectorDialog = false
                        return@Window true
                    }
                    if (isPlayerExpanded) {
                        isPlayerExpanded = false
                        return@Window true
                    }
                    if (currentView == MainView.Settings) {
                        if (settingsRoute != SettingsRoute.Root) {
                            settingsRoute = SettingsRoute.Root
                        } else {
                            currentView = MainView.Home
                        }
                        return@Window true
                    }
                    if (currentView != MainView.Home) {
                        currentView = MainView.Home
                        return@Window true
                    }
                }
            }
            false
        }
    ) {
        ProvideDesktopPlatformAdapters(
            windowWidthDp = windowState.size.width.value.toInt(),
            windowHeightDp = windowState.size.height.value.toInt(),
            backDispatcher = backDispatcher
        ) {
            val prefs = LocalAppPreferences.current
            var prefToken by remember { mutableIntStateOf(0) }
            DisposableEffect(prefs) {
                val listener = AppPreferences.OnChangeListener { _, _ -> prefToken++ }
                prefs.addListener(listener)
                onDispose { prefs.removeListener(listener) }
            }

            val themeMode = remember(prefToken, prefs) {
                ThemeMode.fromStorage(prefs.getString(AppPreferenceKeys.THEME_MODE, ThemeMode.Auto.storageValue))
            }
            val playerArtworkCornerRadiusDp = remember(prefToken, prefs) {
                prefs.getInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, AppDefaults.Player.artworkCornerRadiusDp)
            }
            val darkTheme = when (themeMode) {
                ThemeMode.Auto -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            var playlistLibraryState by remember {
                mutableStateOf(readPlaylistLibraryState(prefs))
            }
            var favoritesSortMode by remember {
                mutableStateOf(
                    PlaylistEntrySortMode.fromStorage(
                        prefs.getString(AppPreferenceKeys.FAVORITES_SORT_MODE, null)
                    )
                )
            }
            val librarySurfaceState = remember { LibrarySurfaceState() }
            var activePlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
            var activePlaylistEntryId by remember { mutableStateOf<String?>(null) }
            val onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit = { updated ->
                playlistLibraryState = updated
                writePlaylistLibraryState(prefs, updated)
            }
            val currentTrackPath = session.currentFile?.absolutePath
            val isCurrentTrackFavorited = currentTrackPath != null &&
                playlistLibraryState.favorites.any { it.source == currentTrackPath }

            val visualizationUiState = rememberVisualizationUiState(
                prefs = prefs,
                activeCoreName = session.decoderName,
                isPlayerSurfaceVisible = isPlayerExpanded
            )

            SiliconPlayerBaseTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigationScaffold(
                            currentView = currentView,
                            onOpenPlayerSurface = {
                                if (session.currentFile != null) {
                                    isPlayerExpanded = true
                                }
                            },
                            onHomeRequested = { currentView = MainView.Home },
                            onOpenUrlOrPathRequested = { openDesktopFileChooser { playFile(it) } },
                            onSettingsRequested = {
                                currentView = MainView.Settings
                                settingsRoute = SettingsRoute.Root
                            }
                        ) { mainPadding, targetView ->
                            val bottomMargin = if (session.currentFile != null) 72.dp else 0.dp
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(mainPadding)
                                    .padding(bottom = bottomMargin)
                            ) {
                                when (targetView) {
                                    MainView.Home -> {
                                        HomeScreen(
                                            currentTrackPath = session.currentFile?.absolutePath,
                                            currentTrackTitle = session.title,
                                            currentTrackArtist = session.artist,
                                            pinnedHomeEntries = pinnedEntries,
                                            recentFolders = recentFolders,
                                            recentPlayedFiles = recentFiles,
                                            storagePresentationForEntry = {
                                                StoragePresentation(
                                                    label = "Local",
                                                    icon = Icons.Default.Folder
                                                )
                                            },
                                            storagePresentationForPinnedEntry = { entry ->
                                                StoragePresentation(
                                                    label = "Local",
                                                    icon = if (entry.isFolder) Icons.Default.Folder else Icons.Default.InsertDriveFile
                                                )
                                            },
                                            bottomContentPadding = bottomMargin,
                                            onOpenLibrary = { openLocalBrowser(currentDirectory) },
                                            onOpenPlaylists = { currentView = MainView.Playlists },
                                            onOpenNetwork = { currentView = MainView.Network },
                                            onOpenPinnedFolder = { entry ->
                                                openLocalBrowser(File(entry.path))
                                            },
                                            onPlayPinnedFile = { entry ->
                                                playSource(entry.path, entry.title, entry.artist)
                                            },
                                            onOpenRecentFolder = { entry ->
                                                openLocalBrowser(File(entry.path))
                                            },
                                            onPlayRecentFile = { entry ->
                                                playSource(entry.path, entry.title, entry.artist)
                                            },
                                            onPinRecentFolder = { entry ->
                                                if (pinnedEntries.none { it.path == entry.path }) {
                                                    pinnedEntries.add(
                                                        HomePinnedEntry(
                                                            path = entry.path,
                                                            isFolder = true,
                                                            title = entry.title,
                                                            artist = entry.artist
                                                        )
                                                    )
                                                }
                                            },
                                            onPinRecentFile = { entry ->
                                                if (pinnedEntries.none { it.path == entry.path }) {
                                                    pinnedEntries.add(
                                                        HomePinnedEntry(
                                                            path = entry.path,
                                                            isFolder = false,
                                                            title = entry.title,
                                                            artist = entry.artist,
                                                            decoderName = entry.decoderName
                                                        )
                                                    )
                                                }
                                            },
                                            onPersistRecentFileMetadata = { entry, title, artist ->
                                                val idx = recentFiles.indexOfFirst { it.path == entry.path }
                                                if (idx >= 0) {
                                                    recentFiles[idx] = recentFiles[idx].copy(title = title, artist = artist)
                                                }
                                            },
                                            onPinnedFolderAction = { entry, action ->
                                                when (action) {
                                                    FolderEntryAction.DeleteFromRecents -> pinnedEntries.removeAll { it.path == entry.path }
                                                    FolderEntryAction.CopyPath -> {}
                                                    FolderEntryAction.OpenInBrowser -> {
                                                        openLocalBrowser(File(entry.path))
                                                    }
                                                }
                                            },
                                            onPinnedFileAction = { entry, action ->
                                                when (action) {
                                                    SourceEntryAction.DeleteFromRecents -> pinnedEntries.removeAll { it.path == entry.path }
                                                    SourceEntryAction.ShareFile -> {}
                                                    SourceEntryAction.CopySource -> {}
                                                    SourceEntryAction.OpenInBrowser -> {
                                                        val f = File(entry.path)
                                                        openLocalBrowser(f.parentFile ?: f)
                                                    }
                                                }
                                            },
                                            onRecentFolderAction = { entry, action ->
                                                when (action) {
                                                    FolderEntryAction.DeleteFromRecents -> recentFolders.removeAll { it.path == entry.path }
                                                    FolderEntryAction.CopyPath -> {}
                                                    FolderEntryAction.OpenInBrowser -> {
                                                        openLocalBrowser(File(entry.path))
                                                    }
                                                }
                                            },
                                            onRecentFileAction = { entry, action ->
                                                when (action) {
                                                    SourceEntryAction.DeleteFromRecents -> recentFiles.removeAll { it.path == entry.path }
                                                    SourceEntryAction.ShareFile -> {}
                                                    SourceEntryAction.CopySource -> {}
                                                    SourceEntryAction.OpenInBrowser -> {
                                                        val f = File(entry.path)
                                                        openLocalBrowser(f.parentFile ?: f)
                                                    }
                                                }
                                            },
                                            onClearPinnedEntries = { pinnedEntries.clear() },
                                            onClearRecentFolders = { recentFolders.clear() },
                                            onClearRecentPlayed = { recentFiles.clear() },
                                            canShareRecentFile = { false },
                                            canSharePinnedFile = { false },
                                            onOpenPlayerSurface = {
                                                if (session.currentFile != null) {
                                                    isPlayerExpanded = true
                                                }
                                            },
                                            onOpenSettings = {
                                                currentView = MainView.Settings
                                                settingsRoute = SettingsRoute.Root
                                            },
                                            onOpenUrlOrPath = {
                                                openDesktopFileChooser { playFile(it) }
                                            }
                                        )
                                    }

                                    MainView.Browser -> {
                                        val prefs = LocalAppPreferences.current
                                        val repository = remember(prefs) {
                                            FileRepository(
                                                supportedExtensions = runCatching {
                                                    NativeBridge.getSupportedExtensions().toSet()
                                                }.getOrElse { emptySet() },
                                                prefs = prefs,
                                                sortArchivesBeforeFiles = true,
                                                nameSortMode = BrowserNameSortMode.Natural,
                                                rootDirectoryProvider = { File(System.getProperty("user.home") ?: "/") }
                                            )
                                        }
                                        val browserResolution = remember(
                                            remoteBrowserInput,
                                            remoteSmbSourceNodeId,
                                            remoteHttpSourceNodeId,
                                            remoteHttpRootPath
                                        ) {
                                            resolveBrowserRouteResolution(
                                                initialLocationId = null,
                                                initialDirectoryPath = remoteBrowserInput,
                                                initialSmbSourceNodeId = remoteSmbSourceNodeId,
                                                initialHttpSourceNodeId = remoteHttpSourceNodeId,
                                                initialHttpRootPath = remoteHttpRootPath
                                            )
                                        }
                                        val browserRenderState = rememberBrowserRouteRenderState(browserResolution)
                                        if (browserRenderState.renderMode == BrowserRouteMode.Smb) {
                                            val smbSpec = browserRenderState.renderSmbSpec
                                            if (smbSpec != null) {
                                                SmbFileBrowserScreen(
                                                    sourceSpec = smbSpec,
                                                    bottomContentPadding = bottomMargin,
                                                    backHandlingEnabled = !isPlayerExpanded,
                                                    allowHostShareNavigation = remoteSmbAllowHostShareNavigation,
                                                    onExitBrowser = {
                                                        remoteBrowserInput = null
                                                        currentView = browserReturnView
                                                    },
                                                    onOpenRemoteSource = { source -> playSource(source) },
                                                    onOpenRemoteSourceAsCached = { source -> playSource(source) },
                                                    onRememberSmbCredentials = { nodeId, _, username, password ->
                                                        nodeId
                                                            ?.let { id -> networkNodes.firstOrNull { it.id == id } }
                                                            ?.let(::resolveNetworkNodeSmbSpec)
                                                            ?.let { spec ->
                                                                NetworkCredentialStore.remember(spec, username, password)
                                                            }
                                                    },
                                                    sourceNodeId = browserResolution.requestedSmbSourceNodeId,
                                                    onBrowserLocationChanged = {},
                                                    onPlaylistFileSelected = { file, _ -> playFile(file) },
                                                    pinnedHomeEntries = pinnedEntries,
                                                    onPinHomeEntry = ::pinHomeEntry,
                                                    playlists = emptyList(),
                                                    favoriteSourceIds = emptySet(),
                                                    networkNodes = networkNodes
                                                )
                                            }
                                        } else if (browserRenderState.renderMode == BrowserRouteMode.Http) {
                                            val httpSpec = browserRenderState.renderHttpSpec
                                            if (httpSpec != null) {
                                                HttpFileBrowserScreen(
                                                    sourceSpec = httpSpec,
                                                    browserRootPath = browserResolution.requestedHttpRootPath,
                                                    bottomContentPadding = bottomMargin,
                                                    backHandlingEnabled = !isPlayerExpanded,
                                                    onExitBrowser = {
                                                        remoteBrowserInput = null
                                                        currentView = browserReturnView
                                                    },
                                                    onOpenRemoteSource = { source -> playSource(source) },
                                                    onOpenRemoteSourceAsCached = { source -> playSource(source) },
                                                    onRememberHttpCredentials = { nodeId, _, username, password ->
                                                        nodeId
                                                            ?.let { id -> networkNodes.firstOrNull { it.id == id } }
                                                            ?.let(::resolveNetworkNodeHttpSpec)
                                                            ?.let { spec ->
                                                                NetworkCredentialStore.remember(spec, username, password)
                                                            }
                                                    },
                                                    sourceNodeId = browserResolution.requestedHttpSourceNodeId,
                                                    onBrowserLocationChanged = {},
                                                    onPlaylistFileSelected = { file, _ -> playFile(file) },
                                                    pinnedHomeEntries = pinnedEntries,
                                                    onPinHomeEntry = ::pinHomeEntry,
                                                    playlists = emptyList(),
                                                    favoriteSourceIds = emptySet(),
                                                    networkNodes = networkNodes
                                                )
                                            }
                                        } else {
                                            RemotePlayableSourceIdsHolder.current = emptyList()
                                            FileBrowserScreen(
                                                repository = repository,
                                                initialDirectoryPath = browserResolution.requestedLocalDirectoryPath
                                                    ?: currentDirectory.absolutePath,
                                                playingFile = session.currentFile,
                                                bottomContentPadding = bottomMargin,
                                                showPrimaryTopBar = false,
                                                backHandlingEnabled = !isPlayerExpanded,
                                                onExitBrowser = { currentView = MainView.Home },
                                                onFileSelected = { file, _ ->
                                                    currentDirectory = file.parentFile ?: currentDirectory
                                                    playFile(file)
                                                },
                                                onBrowserLocationChanged = { launchState ->
                                                    launchState.directoryPath?.let { path ->
                                                        currentDirectory = File(path)
                                                    }
                                                },
                                                pinnedHomeEntries = pinnedEntries,
                                                onPinHomeEntry = ::pinHomeEntry
                                            )
                                        }
                                    }

                                    MainView.Playlists -> {
                                        val playPlaylistEntry: (PlaylistTrackEntry) -> Unit = { entry ->
                                            resolvePlaylistEntryLocalFile(entry.source)?.let { file ->
                                                playFile(file)
                                            }
                                        }
                                        PlaylistsScreen(
                                            libraryState = playlistLibraryState,
                                            libraryCollections = LibraryCollections.Empty,
                                            libraryAlbumDetail = null,
                                            libraryArtistAlbums = null,
                                            selectedArtistName = null,
                                            onOpenLibraryAlbum = { _, _ -> },
                                            onOpenLibraryArtist = { },
                                            onPlayLibraryTracks = { _, _, _ -> },
                                            onShuffleLibraryTracks = { _, _ -> },
                                            onAddLibraryTracksToFavorites = { },
                                            onRemoveLibraryTracksFromFavorites = { },
                                            onAddLibraryTracksToPlaylist = { _, _, _ -> },
                                            onPinLibraryEntries = { },
                                            onUnpinLibraryPaths = { },
                                            pinnedHomeEntries = pinnedEntries,
                                            surfaceState = librarySurfaceState,
                                            onOpenLibrarySettings = {
                                                currentView = MainView.Settings
                                                settingsRoute = SettingsRoute.Library
                                            },
                                            activePlaylist = activePlaylist,
                                            activePlaylistEntryId = activePlaylistEntryId,
                                            currentPlaybackSourceId = session.currentFile?.absolutePath,
                                            currentPlaybackTitle = session.title,
                                            currentPlaybackArtist = session.artist,
                                            currentSubtuneIndex = session.subtuneIndex,
                                            bottomContentPadding = bottomMargin,
                                            favoritesSortMode = favoritesSortMode,
                                            networkNodes = networkNodes,
                                            backHandlingEnabled = !isPlayerExpanded,
                                            onBack = { currentView = MainView.Home },
                                            onFavoritesSortModeChange = { mode ->
                                                favoritesSortMode = mode
                                                prefs.edit()
                                                    .putString(AppPreferenceKeys.FAVORITES_SORT_MODE, mode.storageValue)
                                                    .apply()
                                            },
                                            onOpenFavorite = playPlaylistEntry,
                                            onPlayStoredPlaylist = { playlist ->
                                                activePlaylist = playlist
                                                activePlaylistEntryId = null
                                                playlist.entries.firstOrNull()?.let { entry ->
                                                    activePlaylistEntryId = entry.id
                                                    playPlaylistEntry(entry)
                                                }
                                            },
                                            onShuffleStoredPlaylist = { playlist ->
                                                activePlaylist = playlist
                                                playlist.entries.shuffled().firstOrNull()?.let { entry ->
                                                    activePlaylistEntryId = entry.id
                                                    playPlaylistEntry(entry)
                                                }
                                            },
                                            onOpenStoredPlaylistEntry = { entry, playlist ->
                                                activePlaylist = playlist
                                                activePlaylistEntryId = entry.id
                                                playPlaylistEntry(entry)
                                            },
                                            onPlayFavoritePlaylist = {
                                                activePlaylist = null
                                                playlistLibraryState.favorites.firstOrNull()?.let { entry ->
                                                    activePlaylistEntryId = entry.id
                                                    playPlaylistEntry(entry)
                                                }
                                            },
                                            onShuffleFavoritePlaylist = {
                                                activePlaylist = null
                                                playlistLibraryState.favorites.shuffled().firstOrNull()?.let { entry ->
                                                    activePlaylistEntryId = entry.id
                                                    playPlaylistEntry(entry)
                                                }
                                            },
                                            onDeleteAllFavorites = {
                                                onPlaylistLibraryStateChanged(
                                                    playlistLibraryState.copy(favorites = emptyList())
                                                )
                                            },
                                            onDeleteFavoriteTrack = { entry ->
                                                onPlaylistLibraryStateChanged(
                                                    removeFavoriteTrack(playlistLibraryState, entry.id)
                                                )
                                            },
                                            onMoveFavoriteTrack = { entry, offset ->
                                                onPlaylistLibraryStateChanged(
                                                    moveFavoriteTrack(playlistLibraryState, entry.id, offset)
                                                )
                                            },
                                            onPlayFavoriteTrackAsCached = playPlaylistEntry,
                                            onCreatePlaylist = { title ->
                                                val playlist = StoredPlaylist(
                                                    id = java.util.UUID.randomUUID().toString(),
                                                    title = title.trim(),
                                                    format = PlaylistStoredFormat.Internal,
                                                    sourceIdHint = null,
                                                    entries = emptyList(),
                                                    updatedAtMs = System.currentTimeMillis()
                                                )
                                                onPlaylistLibraryStateChanged(
                                                    upsertStoredPlaylist(playlistLibraryState, playlist)
                                                )
                                                playlist.id
                                            },
                                            onDeleteStoredPlaylistEntry = { entry, playlistId ->
                                                onPlaylistLibraryStateChanged(
                                                    removeStoredPlaylistEntry(playlistLibraryState, playlistId, entry.id)
                                                )
                                            },
                                            onMoveStoredPlaylistEntry = { entry, playlistId, offset ->
                                                onPlaylistLibraryStateChanged(
                                                    moveStoredPlaylistEntry(playlistLibraryState, playlistId, entry.id, offset)
                                                )
                                            },
                                            onDeleteAllStoredPlaylistEntries = { playlistId ->
                                                val entryIds = playlistLibraryState.playlists
                                                    .firstOrNull { it.id == playlistId }
                                                    ?.entries
                                                    ?.map { it.id }
                                                    ?.toSet()
                                                    .orEmpty()
                                                onPlaylistLibraryStateChanged(
                                                    removeStoredPlaylistEntries(playlistLibraryState, playlistId, entryIds)
                                                )
                                            },
                                            onPlayStoredPlaylistTrackAsCached = { entry, playlist ->
                                                activePlaylist = playlist
                                                activePlaylistEntryId = entry.id
                                                playPlaylistEntry(entry)
                                            },
                                            onRemoveSourceFromPlaylist = { source, playlistId ->
                                                val playlist = playlistLibraryState.playlists
                                                    .firstOrNull { it.id == playlistId }
                                                    ?: return@PlaylistsScreen
                                                onPlaylistLibraryStateChanged(
                                                    removeStoredPlaylistEntries(
                                                        playlistLibraryState,
                                                        playlistId,
                                                        playlist.entries
                                                            .filter { it.source == source }
                                                            .map { it.id }
                                                            .toSet()
                                                    )
                                                )
                                            },
                                            onOpenFavoriteTrackLocation = { entry ->
                                                resolvePlaylistEntryLocalFile(entry.source)
                                                    ?.parentFile
                                                    ?.let { openLocalBrowser(it) }
                                            },
                                            onShareFavoriteTrack = { },
                                            onCopyFavoriteTrackSource = { entry ->
                                                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                    java.awt.datatransfer.StringSelection(entry.source),
                                                    null
                                                )
                                            },
                                            onOpenFavoriteTrackInfo = playPlaylistEntry,
                                            onDeleteStoredPlaylist = { playlistId ->
                                                if (activePlaylist?.id == playlistId) {
                                                    activePlaylist = null
                                                    activePlaylistEntryId = null
                                                }
                                                onPlaylistLibraryStateChanged(
                                                    playlistLibraryState.copy(
                                                        playlists = playlistLibraryState.playlists.filterNot { it.id == playlistId }
                                                    )
                                                )
                                            },
                                            onTogglePinStoredPlaylist = { playlistId ->
                                                val playlist = playlistLibraryState.playlists.firstOrNull { it.id == playlistId }
                                                if (playlist != null) {
                                                    onPlaylistLibraryStateChanged(
                                                        setStoredPlaylistPinned(playlistLibraryState, playlistId, !playlist.isPinned)
                                                    )
                                                }
                                            },
                                            onRenameStoredPlaylist = { playlistId, title ->
                                                onPlaylistLibraryStateChanged(
                                                    renameStoredPlaylist(playlistLibraryState, playlistId, title)
                                                )
                                            },
                                            onOpenBrowser = { openLocalBrowser(currentDirectory) },
                                            onAppendStoredPlaylistEntries = { playlistId, entries ->
                                                onPlaylistLibraryStateChanged(
                                                    appendStoredPlaylistEntries(playlistLibraryState, playlistId, entries)
                                                )
                                            },
                                            onDeleteFavoriteTracks = { favoriteIds ->
                                                onPlaylistLibraryStateChanged(
                                                    removeFavoriteTracks(playlistLibraryState, favoriteIds)
                                                )
                                            },
                                            onDeleteStoredPlaylistEntries = { playlistId, entryIds ->
                                                onPlaylistLibraryStateChanged(
                                                    removeStoredPlaylistEntries(playlistLibraryState, playlistId, entryIds)
                                                )
                                            },
                                            onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged
                                        )
                                    }

                                    MainView.Network -> {
                                        val prefs = LocalAppPreferences.current
                                        if (networkNodes.isEmpty()) {
                                            networkNodes.addAll(readNetworkNodes(prefs))
                                        }
                                        NetworkBrowserScreen(
                                            bottomContentPadding = bottomMargin,
                                            backHandlingEnabled = !isPlayerExpanded,
                                            nodes = networkNodes,
                                            currentFolderId = currentNetworkFolderId,
                                            onExitNetwork = {
                                                if (currentNetworkFolderId != null) {
                                                    val parent = networkNodes.firstOrNull { it.id == currentNetworkFolderId }?.parentId
                                                    currentNetworkFolderId = parent
                                                } else {
                                                    currentView = MainView.Home
                                                }
                                            },
                                            onCurrentFolderIdChanged = { currentNetworkFolderId = it },
                                            onNodesChanged = { newNodes ->
                                                networkNodes.clear()
                                                networkNodes.addAll(newNodes)
                                                writeNetworkNodes(prefs, newNodes)
                                            },
                                            onResolveRemoteSourceMetadata = { _, callback -> callback() },
                                            onCancelPendingMetadataBackfill = {},
                                            onOpenRemoteSource = { source ->
                                                playSource(source)
                                            },
                                            onBrowseSmbSource = { rawInput, sourceNodeId ->
                                                val node = sourceNodeId?.let { id -> networkNodes.firstOrNull { it.id == id } }
                                                val allowHostShareNavigation = node
                                                    ?.let(::resolveNetworkNodeSmbSpec)
                                                    ?.share
                                                    ?.trim()
                                                    ?.isEmpty() == true
                                                openRemoteBrowser(
                                                    input = rawInput,
                                                    smbSourceNodeId = sourceNodeId,
                                                    httpSourceNodeId = null,
                                                    httpRootPath = null,
                                                    allowHostShareNavigation = allowHostShareNavigation
                                                )
                                            },
                                            onBrowseHttpSource = { rawInput, sourceNodeId, rootPath ->
                                                openRemoteBrowser(
                                                    input = rawInput,
                                                    smbSourceNodeId = null,
                                                    httpSourceNodeId = sourceNodeId,
                                                    httpRootPath = rootPath,
                                                    allowHostShareNavigation = false
                                                )
                                            },
                                            pinnedHomeEntries = pinnedEntries,
                                            onPinHomeEntry = ::pinHomeEntry
                                        )
                                    }

                                    MainView.Settings -> {
                                        val (desktopSettingsState, desktopSettingsActions) = rememberDesktopSettings(
                                            currentRoute = settingsRoute,
                                            onRouteChange = { settingsRoute = it },
                                            onBackToMainView = {
                                                if (settingsRoute != SettingsRoute.Root) {
                                                    settingsRoute = SettingsRoute.Root
                                                } else {
                                                    currentView = MainView.Home
                                                }
                                            }
                                        )
                                        SettingsScreen(
                                            route = settingsRoute,
                                            bottomContentPadding = bottomMargin,
                                            state = desktopSettingsState,
                                            actions = desktopSettingsActions
                                        )
                                    }
                                }
                            }
                        }

                        // Docked Mini Player
                        AnimatedVisibility(
                            visible = session.currentFile != null && !isPlayerExpanded,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            MiniPlayerBar(
                                file = session.currentFile,
                                title = session.title.ifBlank { session.currentFile?.name ?: "No title" },
                                artist = session.artist.ifBlank { "Unknown Artist" },
                                metadataTitleResolved = session.title.isNotBlank(),
                                artwork = session.artwork,
                                noArtworkIcon = placeholderArtworkIconForFile(session.currentFile, session.decoderName),
                                artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                isPlaying = session.isPlaying,
                                playbackStartInProgress = false,
                                seekInProgress = false,
                                canResumeStoppedTrack = true,
                                positionSeconds = session.positionSeconds,
                                durationSeconds = session.durationSeconds,
                                hasReliableDuration = session.hasReliableDuration,
                                previousRestartsAfterThreshold = true,
                                canPreviousTrack = false,
                                canNextTrack = false,
                                canPreviousSubtune = session.subtuneCount > 1 && session.subtuneIndex > 0,
                                canNextSubtune = session.subtuneCount > 1 && session.subtuneIndex + 1 < session.subtuneCount,
                                currentSubtuneIndex = session.subtuneIndex,
                                subtuneCount = session.subtuneCount,
                                onExpand = { isPlayerExpanded = true },
                                onExpandDragProgress = {},
                                onExpandDragCommit = { isPlayerExpanded = true },
                                onPreviousTrack = {},
                                onForcePreviousTrack = {},
                                onNextTrack = {},
                                onPreviousSubtune = { session.previousSubtune() },
                                onNextSubtune = { session.nextSubtune() },
                                onPlayPause = {
                                    if (session.isPlaying) session.pause() else session.play()
                                },
                                onStopAndClear = { session.stop() },
                                miniContainerFocusRequester = remember { FocusRequester() },
                                previousButtonFocusRequester = remember { FocusRequester() },
                                stopButtonFocusRequester = remember { FocusRequester() },
                                playPauseButtonFocusRequester = remember { FocusRequester() },
                                nextButtonFocusRequester = remember { FocusRequester() }
                            )
                        }

                        // Expanded Player Screen Overlay
                        AnimatedVisibility(
                            visible = isPlayerExpanded,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                                        }
                                    }
                                },
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(durationMillis = 240)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(durationMillis = 200))
                        ) {
                            CompositionLocalProvider(
                                LocalPlayerFocusIndicatorsEnabled provides true,
                                LocalPlayerOverlayVisibility provides { 1f },
                                LocalPlayerExitSlideFraction provides 0f
                            ) {
                                PlayerScreen(
                                    file = session.currentFile,
                                    onBack = { isPlayerExpanded = false },
                                    onCollapseBySwipe = { isPlayerExpanded = false },
                                    isPlaying = session.isPlaying,
                                    canResumeStoppedTrack = true,
                                    onPlay = { session.play() },
                                    onPause = { session.pause() },
                                    onStopAndClear = { session.stop() },
                                    durationSeconds = session.durationSeconds,
                                    positionSeconds = session.positionSeconds,
                                    positionSecondsProvider = { session.positionSeconds },
                                    canPreviousTrack = false,
                                    canNextTrack = false,
                                    title = session.title,
                                    artist = session.artist,
                                    album = session.album,
                                    sampleRateHz = session.sampleRateHz,
                                    channelCount = session.channelCount,
                                    bitDepthLabel = session.bitDepthLabel,
                                    decoderName = session.decoderName,
                                    playbackSourceLabel = "Local",
                                    pathOrUrl = session.currentFile?.absolutePath,
                                    playbackSourceId = session.currentFile?.absolutePath,
                                    artwork = session.artwork,
                                    noArtworkIcon = placeholderArtworkIconForFile(session.currentFile, session.decoderName),
                                    requestInitialFocus = true,
                                    repeatMode = session.repeatMode,
                                    canCycleRepeatMode = supportsLiveRepeatMode(session.playbackCapabilitiesFlags),
                                    canSeek = session.canSeek,
                                    hasReliableDuration = session.hasReliableDuration,
                                    playbackCapabilitiesFlags = session.playbackCapabilitiesFlags,
                                    onSeek = { seconds -> session.seekTo(seconds) },
                                    onPreviousTrack = {},
                                    onForcePreviousTrack = {},
                                    onNextTrack = {},
                                    onPreviousSubtune = { session.previousSubtune() },
                                    onNextSubtune = { session.nextSubtune() },
                                    onOpenSubtuneSelector = { showSubtuneSelectorDialog = true },
                                    canPreviousSubtune = session.subtuneCount > 1 && session.subtuneIndex > 0,
                                    canNextSubtune = session.subtuneCount > 1 && session.subtuneIndex + 1 < session.subtuneCount,
                                    canOpenSubtuneSelector = session.subtuneCount > 1,
                                    canOpenPlaylistSelector = true,
                                    onOpenPlaylistSelector = { openDesktopFileChooser { playFile(it) } },
                                    currentSubtuneIndex = session.subtuneIndex,
                                    subtuneCount = session.subtuneCount,
                                    titleCurrentSubtuneIndex = session.subtuneIndex,
                                    titleSubtuneCount = session.subtuneCount,
                                    subtuneTitleClickable = session.subtuneCount > 1,
                                    onCycleRepeatMode = { session.cycleRepeatMode() },
                                    canOpenCoreSettings = false,
                                    onOpenCoreSettings = {},
                                    visualizationMode = visualizationUiState.mode,
                                    availableVisualizationModes = visualizationUiState.availableModes,
                                    onCycleVisualizationMode = visualizationUiState.onCycleMode,
                                    onSelectVisualizationMode = visualizationUiState.onSelectMode,
                                    onOpenVisualizationSettings = {
                                        currentView = MainView.Settings
                                        settingsRoute = SettingsRoute.Visualization
                                    },
                                    onOpenSelectedVisualizationSettings = {
                                        currentView = MainView.Settings
                                        settingsRoute = when (visualizationUiState.mode) {
                                            VisualizationMode.Bars -> SettingsRoute.VisualizationBasicBars
                                            VisualizationMode.Oscilloscope -> SettingsRoute.VisualizationBasicOscilloscope
                                            VisualizationMode.VuMeters -> SettingsRoute.VisualizationBasicVuMeters
                                            VisualizationMode.ChannelScope -> SettingsRoute.VisualizationAdvancedChannelScope
                                            VisualizationMode.Starfield -> SettingsRoute.VisualizationAdvancedStarfield
                                            VisualizationMode.ProjectM -> SettingsRoute.VisualizationAdvancedProjectM
                                            else -> SettingsRoute.Visualization
                                        }
                                    },
                                    visualizationBarCount = prefs.getInt(
                                        AppPreferenceKeys.VISUALIZATION_BAR_COUNT,
                                        AppDefaults.Visualization.Bars.count
                                    ),
                                    visualizationBarSmoothingPercent = prefs.getInt(
                                        AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT,
                                        AppDefaults.Visualization.Bars.smoothingPercent
                                    ),
                                    visualizationBarRoundnessDp = prefs.getInt(
                                        AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP,
                                        AppDefaults.Visualization.Bars.roundnessDp
                                    ),
                                    visualizationBarOverlayArtwork = prefs.getBoolean(
                                        AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK,
                                        AppDefaults.Visualization.Bars.overlayArtwork
                                    ),
                                    visualizationBarUseThemeColor = prefs.getBoolean(
                                        AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR,
                                        AppDefaults.Visualization.Bars.useThemeColor
                                    ),
                                    visualizationBarRenderBackend = VisualizationRenderBackend.fromStorage(
                                        prefs.getString(
                                            AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND,
                                            AppDefaults.Visualization.Bars.renderBackend.storageValue
                                        ),
                                        AppDefaults.Visualization.Bars.renderBackend
                                    ),
                                    visualizationOscStereo = prefs.getBoolean(
                                        AppPreferenceKeys.VISUALIZATION_OSC_STEREO,
                                        AppDefaults.Visualization.Oscilloscope.stereo
                                    ),
                                    visualizationVuAnchor = VisualizationVuAnchor.fromStorage(
                                        prefs.getString(
                                            AppPreferenceKeys.VISUALIZATION_VU_ANCHOR,
                                            AppDefaults.Visualization.Vu.anchor.storageValue
                                        )
                                    ),
                                    visualizationVuUseThemeColor = prefs.getBoolean(
                                        AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR,
                                        AppDefaults.Visualization.Vu.useThemeColor
                                    ),
                                    visualizationVuSmoothingPercent = prefs.getInt(
                                        AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT,
                                        AppDefaults.Visualization.Vu.smoothingPercent
                                    ),
                                    visualizationVuRenderBackend = VisualizationRenderBackend.fromStorage(
                                        prefs.getString(
                                            AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND,
                                            AppDefaults.Visualization.Vu.renderBackend.storageValue
                                        ),
                                        AppDefaults.Visualization.Vu.renderBackend
                                    ),
                                    externalTrackInfoDialogRequestToken = externalTrackInfoDialogRequestToken,
                                    artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                    isTrackFavorited = isCurrentTrackFavorited,
                                    onToggleFavoriteTrack = {
                                        val path = session.currentFile?.absolutePath ?: return@PlayerScreen
                                        val existing = playlistLibraryState.favorites.firstOrNull { it.source == path }
                                        onPlaylistLibraryStateChanged(
                                            if (existing != null) {
                                                removeFavoriteTrack(playlistLibraryState, existing.id)
                                            } else {
                                                upsertFavoriteTrack(
                                                    playlistLibraryState,
                                                    PlaylistTrackEntry(
                                                        id = java.util.UUID.randomUUID().toString(),
                                                        source = path,
                                                        title = session.title.ifBlank { session.currentFile?.nameWithoutExtension.orEmpty() },
                                                        artist = session.artist.takeUnless { it.isBlank() },
                                                        album = session.album.takeUnless { it.isBlank() },
                                                        addedAtMs = System.currentTimeMillis()
                                                    )
                                                )
                                            }
                                        )
                                    },
                                    onOpenAudioEffects = {}
                                )
                            }
                        }
                    }

                    if (showTrackInfoDialog && session.currentFile != null) {
                        TrackInfoDialog(
                            file = session.currentFile,
                            title = session.title,
                            artist = session.artist,
                            decoderName = session.decoderName,
                            playbackSourceLabel = "Local",
                            pathOrUrl = session.currentFile?.absolutePath,
                            sampleRateHz = session.sampleRateHz,
                            channelCount = session.channelCount,
                            bitDepthLabel = session.bitDepthLabel,
                            durationSeconds = session.durationSeconds,
                            hasReliableDuration = session.hasReliableDuration,
                            onDismiss = { showTrackInfoDialog = false }
                        )
                    }

                    if (showSubtuneSelectorDialog && session.subtuneEntries.isNotEmpty()) {
                        PlatformBackHandler(enabled = true) {
                            showSubtuneSelectorDialog = false
                        }
                        AlertDialog(
                            onDismissRequest = { showSubtuneSelectorDialog = false },
                            title = { Text("Subtunes") },
                            text = {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(session.subtuneEntries.size) { index ->
                                        val entry = session.subtuneEntries[index]
                                        val isCurrent = entry.index == session.subtuneIndex
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    session.selectSubtune(entry.index)
                                                    showSubtuneSelectorDialog = false
                                                },
                                            shape = MaterialTheme.shapes.medium,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                                                Text(
                                                    text = "${entry.index + 1}. ${entry.title}",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (entry.artist.isNotBlank() || entry.durationSeconds > 0) {
                                                    Text(
                                                        text = listOfNotNull(
                                                            formatShortDuration(entry.durationSeconds).takeIf { entry.durationSeconds > 0 },
                                                            entry.artist.takeIf { it.isNotBlank() }
                                                        ).joinToString(" • "),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSubtuneSelectorDialog = false }) {
                                    Text("Close")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
