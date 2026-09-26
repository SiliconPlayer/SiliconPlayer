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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import com.flopster101.siliconplayer.DomainStoreDirs
import com.flopster101.siliconplayer.HomeScreen
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.MiniPlayerBar
import com.flopster101.siliconplayer.resolveMiniPlayerArtist
import com.flopster101.siliconplayer.resolveMiniPlayerTitle
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.StoragePresentation
import com.flopster101.siliconplayer.FolderEntryAction
import com.flopster101.siliconplayer.SourceEntryAction
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkCredentialStore
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.LocalAppConfigDir
import com.flopster101.siliconplayer.ManualSourceType
import com.flopster101.siliconplayer.MANUAL_INPUT_INVALID_MESSAGE
import com.flopster101.siliconplayer.resolveManualSourceInput
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.resolveNetworkNodeHttpSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.readNetworkNodes
import com.flopster101.siliconplayer.writeNetworkNodes
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.data.compareFileNamesNatural
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.PlatformBackHandler
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.screens.rememberVisualizationUiState
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import com.flopster101.siliconplayer.ui.dialogs.PlaylistSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.TrackInfoDialog
import com.flopster101.siliconplayer.ui.dialogs.UrlOrPathDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
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
import com.flopster101.siliconplayer.FAVORITES_PLAYLIST_ID
import com.flopster101.siliconplayer.LookaheadClipperMode
import com.flopster101.siliconplayer.MultiChannelOutputMode
import com.flopster101.siliconplayer.audio.DspSettingsNamespace
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.defaultDspSettings
import com.flopster101.siliconplayer.audio.hasCoreDspOverrides
import com.flopster101.siliconplayer.audio.normalizeSurroundDelayMsPref
import com.flopster101.siliconplayer.audio.readCoreDspSettings
import com.flopster101.siliconplayer.audio.readCoreIgnoreGlobalDsp
import com.flopster101.siliconplayer.audio.readGlobalDspSettings
import com.flopster101.siliconplayer.audio.resolveEffectiveDspSettings
import com.flopster101.siliconplayer.audio.writeCoreDspSettings
import com.flopster101.siliconplayer.audio.writeGlobalDspSettings
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.playlistContainsTrack
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.shouldRestartCurrentTrackOnPrevious
import com.flopster101.siliconplayer.toPlaylistTrackEntry
import com.flopster101.siliconplayer.readPinnedHomeEntries
import com.flopster101.siliconplayer.readPluginVolumeForDecoder
import com.flopster101.siliconplayer.readRecentEntries
import com.flopster101.siliconplayer.upsertFavoriteTrack
import com.flopster101.siliconplayer.upsertFavoriteTracks
import com.flopster101.siliconplayer.writePluginVolumeForDecoder
import com.flopster101.siliconplayer.upsertStoredPlaylist
import com.flopster101.siliconplayer.writePinnedHomeEntries
import com.flopster101.siliconplayer.writeRecentEntries
import com.flopster101.siliconplayer.writePlaylistLibraryState
import com.flopster101.siliconplayer.SettingsScreen
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.MainView
import com.flopster101.siliconplayer.SettingsRoute
import com.flopster101.siliconplayer.BrowserRouteMode
import com.flopster101.siliconplayer.rememberBrowserRouteRenderState
import com.flopster101.siliconplayer.resolveBrowserRouteResolution
import com.flopster101.siliconplayer.MainNavigationScaffold
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
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
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

// Mini player docking: Android's MiniPlayerOverlayHost floats the bar 14.dp/6.dp inside the
// content area, which itself ends above the navigation bar. Desktop windows have no navigation
// bar, so DesktopNavigationBarInset supplies that gap explicitly.
private val MiniPlayerDockHorizontalPadding = 14.dp
private val MiniPlayerDockVerticalPadding = 6.dp
private val DesktopNavigationBarInset = 16.dp
private const val DesktopRecentFilesLimit = 20
private const val DesktopRecentFoldersLimit = 10

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
    DomainStoreDirs.configDir = DesktopPaths.configDir()
    val session = remember { DesktopPlaybackSession() }
    val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)
    val backDispatcher = remember { DesktopBackDispatcher() }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var miniDismissOffsetPx by remember { mutableFloatStateOf(0f) }
    val miniDismissSettle = remember { Animatable(0f) }
    val miniDismissScope = rememberCoroutineScope()
    var miniDismissWidthPx by remember { mutableFloatStateOf(0f) }
    var miniDismissSettling by remember { mutableStateOf(false) }
    val supportedExtensions = remember {
        runCatching { NativeBridge.getSupportedExtensions().toSet() }.getOrElse { emptySet() }
    }
    var previousRestartsAfterThreshold by remember { mutableStateOf(true) }
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
        if (recentFiles.size > DesktopRecentFilesLimit) {
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
            if (recentFolders.size > DesktopRecentFoldersLimit) {
                recentFolders.removeLast()
            }
        }
    }

    fun playFile(file: File) {
        if (session.loadFile(file)) {
            registerLoadedFile(file)
            isPlayerSurfaceVisible = true
        }
    }

    fun playSource(source: String, titleHint: String? = null, artistHint: String? = null) {
        val file = File(source)
        if (file.exists() && file.isFile) {
            playFile(file)
            return
        }
        if (session.loadSource(source, titleHint, artistHint)) {
            isPlayerSurfaceVisible = true
            val entry = RecentPathEntry(
                path = source,
                locationId = null,
                title = session.title.ifBlank { titleHint ?: source },
                artist = session.artist.ifBlank { artistHint ?: "Network" },
                decoderName = session.decoderName
            )
            recentFiles.removeAll { it.path == source }
            recentFiles.add(0, entry)
            if (recentFiles.size > DesktopRecentFilesLimit) {
                recentFiles.removeLast()
            }
        }
    }

    fun listSiblingTracks(anchor: File): List<File> {
        val siblings = anchor.parentFile?.listFiles()
            ?.filter { it.isFile && fileMatchesSupportedExtensions(it, supportedExtensions) }
            ?: return emptyList()
        return siblings.sortedWith { left, right -> compareFileNamesNatural(left.name, right.name) }
    }

    fun playAdjacentTrack(offset: Int, stopAtBoundary: Boolean): Boolean {
        val current = session.currentFile ?: return false
        val siblings = listSiblingTracks(current)
        if (siblings.isEmpty()) return false
        val index = siblings.indexOfFirst { samePath(it.absolutePath, current.absolutePath) }
        if (index < 0) return false
        val target = if (session.repeatMode != RepeatMode.None) {
            siblings[((index + offset) % siblings.size + siblings.size) % siblings.size]
        } else {
            siblings.getOrNull(index + offset)
        }
        if (target == null) {
            if (stopAtBoundary && offset > 0) {
                session.stop()
                return true
            }
            return false
        }
        playFile(target)
        return true
    }

    fun playPreviousTrackFromUi() {
        val current = session.currentFile ?: return
        if (shouldRestartCurrentTrackOnPrevious(previousRestartsAfterThreshold, true, session.positionSeconds)) {
            session.seekTo(0.0)
            return
        }
        if (playAdjacentTrack(-1, stopAtBoundary = false)) return
        session.seekTo(0.0)
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
    var dismissAudioEffectsDialogHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }
    var selectorImportEntries by remember { mutableStateOf<List<PlaylistTrackEntry>?>(null) }
    var selectorImportTitle by remember { mutableStateOf<String?>(null) }
    var selectorImportDialogTitle by remember { mutableStateOf("Add to playlist") }
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
                    if (dismissAudioEffectsDialogHandler?.invoke() == true) {
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
                    if (selectorImportEntries != null) {
                        selectorImportEntries = null
                        selectorImportTitle = null
                        return@Window true
                    }
                    if (showPlaylistSelectorDialog) {
                        showPlaylistSelectorDialog = false
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
            val configDir = LocalAppConfigDir.current
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

            var showUrlOrPathDialog by remember { mutableStateOf(false) }
            var urlOrPathInput by remember { mutableStateOf("") }
            var urlOrPathForceCaching by remember {
                mutableStateOf(prefs.getBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, false))
            }
            val toastHandler = LocalToastHandler.current
            val clipboardManager = LocalClipboardManager.current
            fun confirmUrlOrPathOpen() {
                showUrlOrPathDialog = false
                val resolved = resolveManualSourceInput(urlOrPathInput)
                when {
                    resolved == null -> toastHandler.showToast(MANUAL_INPUT_INVALID_MESSAGE)
                    resolved.type == ManualSourceType.LocalDirectory ->
                        resolved.directoryPath?.let { openLocalBrowser(File(it)) }
                    resolved.type == ManualSourceType.LocalFile ->
                        resolved.localFile?.let { playFile(it) }
                    else -> playSource(resolved.requestUrl)
                }
            }

            var playlistLibraryState by remember {
                mutableStateOf(readPlaylistLibraryState(configDir, prefs))
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
                writePlaylistLibraryState(configDir, updated)
            }
            val addEntriesToPlaylist: (List<PlaylistTrackEntry>, String?, String) -> Unit = { entries, playlistId, newTitle ->
                if (entries.isNotEmpty()) {
                    val target = playlistId?.let { id -> playlistLibraryState.playlists.firstOrNull { it.id == id } }
                    onPlaylistLibraryStateChanged(
                        when {
                            playlistId == FAVORITES_PLAYLIST_ID -> upsertFavoriteTracks(playlistLibraryState, entries)
                            target != null -> upsertStoredPlaylist(
                                playlistLibraryState,
                                target.copy(entries = target.entries + entries)
                            )
                            else -> upsertStoredPlaylist(
                                playlistLibraryState,
                                StoredPlaylist(title = newTitle.ifBlank { "New playlist" }, entries = entries)
                            )
                        }
                    )
                    toastHandler.showToast(
                        if (entries.size == 1) "Added to playlist" else "Added ${entries.size} tracks to playlist"
                    )
                }
            }
            fun buildCurrentTrackEntry(): PlaylistTrackEntry? {
                val path = session.currentFile?.absolutePath ?: return null
                return PlaylistTrackEntry(
                    id = java.util.UUID.randomUUID().toString(),
                    source = path,
                    title = session.title.ifBlank { session.currentFile?.nameWithoutExtension.orEmpty() },
                    artist = session.artist.takeUnless { it.isBlank() },
                    album = session.album.takeUnless { it.isBlank() },
                    subtuneIndex = session.subtuneIndex.takeIf { session.subtuneCount > 1 },
                    durationSecondsOverride = session.durationSeconds.takeIf { it > 0 },
                    addedAtMs = System.currentTimeMillis()
                )
            }
            fun togglePlaylistEntryFavorite(entry: PlaylistTrackEntry) {
                val existing = playlistLibraryState.favorites.firstOrNull { fav ->
                    samePath(fav.source, entry.source) && (fav.subtuneIndex ?: -1) == (entry.subtuneIndex ?: -1)
                }
                if (existing != null) {
                    onPlaylistLibraryStateChanged(removeFavoriteTrack(playlistLibraryState, existing.id))
                    toastHandler.showToast("Removed from favorites")
                } else {
                    onPlaylistLibraryStateChanged(upsertFavoriteTrack(playlistLibraryState, entry))
                    toastHandler.showToast("Added to favorites")
                }
            }
            fun removeSourceFromPlaylist(playlistId: String, source: String, subtuneIndex: Int?) {
                if (playlistId == FAVORITES_PLAYLIST_ID) {
                    val matching = playlistLibraryState.favorites.filter { entry ->
                        samePath(entry.source, source) &&
                            (entry.subtuneIndex == subtuneIndex || entry.subtuneIndex == null)
                    }
                    if (matching.isNotEmpty()) {
                        onPlaylistLibraryStateChanged(
                            playlistLibraryState.copy(
                                favorites = playlistLibraryState.favorites.filterNot { entry ->
                                    matching.any { it.id == entry.id }
                                }
                            )
                        )
                        toastHandler.showToast("Removed from favorites")
                    }
                } else {
                    val target = playlistLibraryState.playlists.firstOrNull { it.id == playlistId }
                    val entryId = target?.entries?.firstOrNull { entry ->
                        samePath(entry.source, source) &&
                            (entry.subtuneIndex == subtuneIndex || entry.subtuneIndex == null)
                    }?.id
                    if (entryId != null) {
                        onPlaylistLibraryStateChanged(
                            removeStoredPlaylistEntry(playlistLibraryState, playlistId, entryId)
                        )
                        toastHandler.showToast("Removed from playlist")
                    }
                }
            }
            val songVolumeStore = remember { DesktopSongVolumeStore.getInstance() }
            var showAudioEffectsDialog by remember { mutableStateOf(false) }
            var masterVolumeDb by remember {
                mutableStateOf(prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f))
            }
            var forceMono by remember {
                mutableStateOf(prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false))
            }
            var songVolumeDb by remember { mutableStateOf(0f) }
            var ignoreCoreVolumeForSong by remember { mutableStateOf(false) }
            var globalDspSettings by remember { mutableStateOf(readGlobalDspSettings(prefs)) }
            var coreDspSettings by remember(session.decoderName) {
                mutableStateOf(readCoreDspSettings(prefs, session.decoderName))
            }
            var coreDspHasOverrides by remember(session.decoderName) {
                mutableStateOf(hasCoreDspOverrides(prefs, session.decoderName))
            }
            var coreIgnoreGlobalDsp by remember(session.decoderName) {
                mutableStateOf(readCoreIgnoreGlobalDsp(prefs, session.decoderName))
            }
            var dspNamespaceSelection by remember {
                mutableStateOf(
                    when (prefs.getString(AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE, "global")) {
                        "core" -> DspSettingsNamespace.CurrentCore
                        else -> DspSettingsNamespace.Global
                    }
                )
            }
            if (session.decoderName == null && dspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                dspNamespaceSelection = DspSettingsNamespace.Global
            }
            var tempMasterVolumeDb by remember { mutableStateOf(masterVolumeDb) }
            var tempPluginVolumeDb by remember { mutableStateOf(0f) }
            var tempSongVolumeDb by remember { mutableStateOf(songVolumeDb) }
            var tempIgnoreCoreVolumeForSong by remember { mutableStateOf(ignoreCoreVolumeForSong) }
            var tempForceMono by remember { mutableStateOf(forceMono) }
            var tempGlobalDspSettings by remember { mutableStateOf(globalDspSettings) }
            var tempCoreDspSettings by remember { mutableStateOf(coreDspSettings) }
            var tempCoreIgnoreGlobalDsp by remember { mutableStateOf(coreIgnoreGlobalDsp) }
            var tempDspNamespaceSelection by remember { mutableStateOf(dspNamespaceSelection) }
            fun applyCurrentTempDspSettingsToNative() {
                val coreHasTempOverrides = coreDspHasOverrides ||
                    tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore
                applyDspSettingsToNative(
                    resolveEffectiveDspSettings(
                        coreName = session.decoderName,
                        global = tempGlobalDspSettings,
                        core = tempCoreDspSettings,
                        coreHasOverrides = coreHasTempOverrides,
                        ignoreGlobalForCore = tempCoreIgnoreGlobalDsp
                    )
                )
            }
            fun applyCommittedAudioParametersToNative() {
                NativeBridge.setMasterGain(masterVolumeDb)
                NativeBridge.setPluginGain(
                    if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, session.decoderName)
                )
                NativeBridge.setSongGain(songVolumeDb)
                NativeBridge.setForceMono(forceMono)
                applyDspSettingsToNative(
                    resolveEffectiveDspSettings(
                        coreName = session.decoderName,
                        global = globalDspSettings,
                        core = coreDspSettings,
                        coreHasOverrides = coreDspHasOverrides,
                        ignoreGlobalForCore = coreIgnoreGlobalDsp
                    )
                )
            }
            fun openAudioEffectsDialog() {
                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
                val path = session.currentFile?.absolutePath
                songVolumeDb = path?.let { songVolumeStore.getSongVolume(it) } ?: 0f
                ignoreCoreVolumeForSong = path?.let { songVolumeStore.getSongIgnoreCoreVolume(it) } ?: false
                globalDspSettings = readGlobalDspSettings(prefs)
                coreDspSettings = readCoreDspSettings(prefs, session.decoderName)
                coreDspHasOverrides = hasCoreDspOverrides(prefs, session.decoderName)
                coreIgnoreGlobalDsp = readCoreIgnoreGlobalDsp(prefs, session.decoderName)
                tempMasterVolumeDb = masterVolumeDb
                tempPluginVolumeDb = readPluginVolumeForDecoder(prefs, session.decoderName)
                tempSongVolumeDb = songVolumeDb
                tempIgnoreCoreVolumeForSong = ignoreCoreVolumeForSong
                tempForceMono = forceMono
                tempGlobalDspSettings = globalDspSettings
                tempCoreDspSettings = coreDspSettings
                tempCoreIgnoreGlobalDsp = coreIgnoreGlobalDsp
                tempDspNamespaceSelection = dspNamespaceSelection
                if (session.decoderName == null && tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                    tempDspNamespaceSelection = DspSettingsNamespace.Global
                }
                showAudioEffectsDialog = true
            }
            fun dismissAudioEffectsDialog() {
                applyCommittedAudioParametersToNative()
                showAudioEffectsDialog = false
            }
            SideEffect {
                dismissAudioEffectsDialogHandler = {
                    if (showAudioEffectsDialog) {
                        dismissAudioEffectsDialog()
                        true
                    } else {
                        false
                    }
                }
            }
            fun confirmAudioEffectsDialog() {
                masterVolumeDb = tempMasterVolumeDb
                forceMono = tempForceMono
                songVolumeDb = tempSongVolumeDb
                ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong
                dspNamespaceSelection = tempDspNamespaceSelection
                globalDspSettings = tempGlobalDspSettings
                coreDspSettings = tempCoreDspSettings
                coreIgnoreGlobalDsp = tempCoreIgnoreGlobalDsp
                val coreName = session.decoderName
                prefs.edit().apply {
                    putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                    putBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, tempForceMono)
                    putString(
                        AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE,
                        if (dspNamespaceSelection == DspSettingsNamespace.CurrentCore) "core" else "global"
                    )
                    writeGlobalDspSettings(this, tempGlobalDspSettings)
                    if (coreName != null) {
                        putBoolean(AppPreferenceKeys.audioDspCoreIgnoreGlobalKey(coreName), tempCoreIgnoreGlobalDsp)
                        if (dspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                            writeCoreDspSettings(this, coreName, tempCoreDspSettings)
                            coreDspHasOverrides = true
                        } else {
                            coreDspHasOverrides = hasCoreDspOverrides(prefs, coreName)
                        }
                    }
                    apply()
                }
                writePluginVolumeForDecoder(prefs, coreName, tempPluginVolumeDb)
                session.currentFile?.absolutePath?.let { path ->
                    songVolumeStore.setSongVolume(path, tempSongVolumeDb)
                    songVolumeStore.setSongIgnoreCoreVolume(path, tempIgnoreCoreVolumeForSong)
                }
                NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else tempPluginVolumeDb)
                applyCommittedAudioParametersToNative()
                showAudioEffectsDialog = false
            }
            LaunchedEffect(prefToken, prefs) {
                previousRestartsAfterThreshold =
                    prefs.getBoolean(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true)
            }
            LaunchedEffect(Unit) {
                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
                NativeBridge.setMasterGain(masterVolumeDb)
                NativeBridge.setPluginGain(0f)
                NativeBridge.setForceMono(forceMono)
                NativeBridge.setOutputLimiterEnabled(
                    prefs.getBoolean(
                        AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED,
                        AppDefaults.AudioProcessing.outputLimiterEnabled
                    )
                )
                NativeBridge.setLookaheadClipperMode(
                    LookaheadClipperMode.fromStorage(
                        prefs.getString(
                            AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE,
                            AppDefaults.AudioProcessing.lookaheadClipperMode.storageValue
                        )
                    ).nativeValue
                )
                NativeBridge.setMultiChannelOutputMode(
                    MultiChannelOutputMode.fromStorage(
                        prefs.getString(
                            AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE,
                            AppDefaults.OutputPipeline.multiChannelOutputMode.storageValue
                        )
                    ).nativeValue
                )
                applyDspSettingsToNative(readGlobalDspSettings(prefs))
            }
            LaunchedEffect(session.currentFile, session.decoderName) {
                val path = session.currentFile?.absolutePath
                songVolumeDb = path?.let { songVolumeStore.getSongVolume(it) } ?: 0f
                ignoreCoreVolumeForSong = path?.let { songVolumeStore.getSongIgnoreCoreVolume(it) } ?: false
                NativeBridge.setPluginGain(
                    if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, session.decoderName)
                )
                NativeBridge.setSongGain(songVolumeDb)
                applyDspSettingsToNative(
                    resolveEffectiveDspSettings(
                        coreName = session.decoderName,
                        global = readGlobalDspSettings(prefs),
                        core = readCoreDspSettings(prefs, session.decoderName),
                        coreHasOverrides = hasCoreDspOverrides(prefs, session.decoderName),
                        ignoreGlobalForCore = readCoreIgnoreGlobalDsp(prefs, session.decoderName)
                    )
                )
            }
            LaunchedEffect(prefs) {
                readRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, DesktopRecentFoldersLimit, prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        recentFolders.clear()
                        recentFolders.addAll(stored)
                    }
                readRecentEntries(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES, DesktopRecentFilesLimit, prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        recentFiles.clear()
                        recentFiles.addAll(stored)
                    }
                readPinnedHomeEntries(configDir, legacyPrefs = prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        pinnedEntries.clear()
                        pinnedEntries.addAll(stored)
                    }
                snapshotFlow { Triple(recentFiles.toList(), recentFolders.toList(), pinnedEntries.toList()) }
                    .distinctUntilChanged()
                    .collect { (files, folders, pinned) ->
                        writeRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, folders, DesktopRecentFoldersLimit)
                        writeRecentEntries(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES, files, DesktopRecentFilesLimit)
                        writePinnedHomeEntries(configDir, pinned)
                    }
            }
            val currentTrackPath = session.currentFile?.absolutePath
            val isCurrentTrackFavorited = currentTrackPath != null &&
                playlistLibraryState.favorites.any { it.source == currentTrackPath }

            val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
            val miniDismissMaxOffsetPx = with(LocalDensity.current) { 108.dp.toPx() }
            val visualizationUiState = rememberVisualizationUiState(
                prefs = prefs,
                activeCoreName = session.decoderName,
                isPlayerSurfaceVisible = isPlayerSurfaceVisible
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
                                isPlayerSurfaceVisible = true
                                isPlayerExpanded = true
                            },
                            onHomeRequested = { currentView = MainView.Home },
                            onOpenUrlOrPathRequested = { showUrlOrPathDialog = true },
                            onSettingsRequested = {
                                currentView = MainView.Settings
                                settingsRoute = SettingsRoute.Root
                            }
                        ) { mainPadding, targetView ->
                            val bottomMargin = if (isPlayerSurfaceVisible && !isPlayerExpanded) {
                                72.dp + DesktopNavigationBarInset
                            } else {
                                0.dp
                            }
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
                                                    FolderEntryAction.CopyPath -> {
                                                        clipboardManager.setText(AnnotatedString(entry.path))
                                                        toastHandler.showToast("Copied path")
                                                    }
                                                    FolderEntryAction.OpenInBrowser -> {
                                                        openLocalBrowser(File(entry.path))
                                                    }
                                                }
                                            },
                                            onPinnedFileAction = { entry, action ->
                                                when (action) {
                                                    SourceEntryAction.DeleteFromRecents -> pinnedEntries.removeAll { it.path == entry.path }
                                                    SourceEntryAction.ShareFile -> {}
                                                    SourceEntryAction.CopySource -> {
                                                        clipboardManager.setText(AnnotatedString(entry.path))
                                                        toastHandler.showToast("Copied URL/path")
                                                    }
                                                    SourceEntryAction.OpenInBrowser -> {
                                                        val f = File(entry.path)
                                                        openLocalBrowser(f.parentFile ?: f)
                                                    }
                                                }
                                            },
                                            onRecentFolderAction = { entry, action ->
                                                when (action) {
                                                    FolderEntryAction.DeleteFromRecents -> recentFolders.removeAll { it.path == entry.path }
                                                    FolderEntryAction.CopyPath -> {
                                                        clipboardManager.setText(AnnotatedString(entry.path))
                                                        toastHandler.showToast("Copied path")
                                                    }
                                                    FolderEntryAction.OpenInBrowser -> {
                                                        openLocalBrowser(File(entry.path))
                                                    }
                                                }
                                            },
                                            onRecentFileAction = { entry, action ->
                                                when (action) {
                                                    SourceEntryAction.DeleteFromRecents -> recentFiles.removeAll { it.path == entry.path }
                                                    SourceEntryAction.ShareFile -> {}
                                                    SourceEntryAction.CopySource -> {
                                                        clipboardManager.setText(AnnotatedString(entry.path))
                                                        toastHandler.showToast("Copied URL/path")
                                                    }
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
                                                isPlayerSurfaceVisible = true
                                                isPlayerExpanded = true
                                            },
                                            onOpenSettings = {
                                                currentView = MainView.Settings
                                                settingsRoute = SettingsRoute.Root
                                            },
                                            onOpenUrlOrPath = { showUrlOrPathDialog = true }
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
                                            onAddLibraryTracksToPlaylist = { tracks, playlistId, newTitle ->
                                                addEntriesToPlaylist(
                                                    tracks.map { it.toPlaylistTrackEntry() },
                                                    playlistId,
                                                    newTitle
                                                )
                                            },
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
                                        val configDir = LocalAppConfigDir.current
                                        if (networkNodes.isEmpty()) {
                                            networkNodes.addAll(readNetworkNodes(configDir, prefs))
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
                                                writeNetworkNodes(configDir, newNodes)
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
                                        val settingsCacheDir = LocalAppCacheDir.current
                                        val settingsProtectedCachePaths = remember(session.currentFile, settingsCacheDir) {
                                            val path = session.currentFile?.absolutePath
                                            val cachePrefix = File(settingsCacheDir, REMOTE_SOURCE_CACHE_DIR).absolutePath + File.separator
                                            if (path != null && path.startsWith(cachePrefix)) setOf(path) else emptySet()
                                        }
                                        val (desktopSettingsState, desktopSettingsActions) = rememberDesktopSettings(
                                            currentRoute = settingsRoute,
                                            onRouteChange = { settingsRoute = it },
                                            onOpenAudioEffects = { openAudioEffectsDialog() },
                                            protectedCachePaths = settingsProtectedCachePaths,
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

                        // A paused hide is the dismiss fling: leave the offset past the screen
                        // edge until the exit finishes or the bar re-shows, or it pops back.
                        LaunchedEffect(session.isPlaying, isPlayerSurfaceVisible) {
                            if (isPlayerSurfaceVisible || session.isPlaying) {
                                miniDismissOffsetPx = 0f
                            }
                        }

                        // Docked Mini Player
                        AnimatedVisibility(
                            visible = isPlayerSurfaceVisible && !isPlayerExpanded,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    horizontal = MiniPlayerDockHorizontalPadding,
                                    vertical = MiniPlayerDockVerticalPadding
                                )
                                .padding(bottom = DesktopNavigationBarInset),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            MiniPlayerBar(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val dragProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                                        alpha = 1f - dragProgress
                                        translationY = -miniPreviewLiftPx * dragProgress
                                        translationX = miniDismissOffsetPx
                                    }
                                    .onSizeChanged { miniDismissWidthPx = it.width.toFloat() }
                                    .pointerInput(session.isPlaying, miniDismissMaxOffsetPx) {
                                    // Direction-locked dismiss: decided in the Main pass before the inner
                                    // vertical expand detector sees the gesture, so horizontal drags engage
                                    // immediately instead of racing it. Vertical drags pass through untouched.
                                    val touchSlop = viewConfiguration.touchSlop
                                    awaitEachGesture {
                                        val pointerId = awaitFirstDown(requireUnconsumed = false).id
                                        var lockedHorizontal = false
                                        var slopX = 0f
                                        var slopY = 0f
                                        fun snapMiniDismissBack() {
                                            val releaseOffset = miniDismissOffsetPx
                                            miniDismissScope.launch {
                                                miniDismissSettle.snapTo(releaseOffset)
                                                miniDismissSettle.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 220,
                                                        easing = LinearOutSlowInEasing
                                                    )
                                                ) {
                                                    miniDismissOffsetPx = value
                                                }
                                            }
                                        }
                                        fun settleMiniDismiss() {
                                            val releaseOffset = miniDismissOffsetPx
                                            if (!session.isPlaying &&
                                                abs(releaseOffset) >= miniDismissMaxOffsetPx * 0.6f
                                            ) {
                                                // Fling off-screen toward the dragged side; the visibility
                                                // exit only slides vertically, so hiding here would sink it.
                                                miniDismissSettling = true
                                                val exitDistancePx =
                                                    if (miniDismissWidthPx > 0f) miniDismissWidthPx
                                                    else abs(releaseOffset) + 600f
                                                miniDismissScope.launch {
                                                    miniDismissSettle.snapTo(releaseOffset)
                                                    miniDismissSettle.animateTo(
                                                        targetValue = if (releaseOffset < 0f) -exitDistancePx else exitDistancePx,
                                                        animationSpec = tween(
                                                            durationMillis = 220,
                                                            easing = LinearOutSlowInEasing
                                                        )
                                                    ) {
                                                        miniDismissOffsetPx = value
                                                    }
                                                    isPlayerExpanded = false
                                                    isPlayerSurfaceVisible = false
                                                    miniDismissSettling = false
                                                }
                                            } else {
                                                snapMiniDismissBack()
                                            }
                                        }
                                        var finished = false
                                        while (!finished) {
                                            val event = awaitPointerEvent(PointerEventPass.Main)
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                            if (change == null) {
                                                if (lockedHorizontal && !miniDismissSettling) snapMiniDismissBack()
                                                finished = true
                                            } else if (!change.pressed) {
                                                if (lockedHorizontal && !miniDismissSettling) settleMiniDismiss()
                                                finished = true
                                            } else if (miniDismissSettling) {
                                                continue
                                            } else {
                                                val dx = change.position.x - change.previousPosition.x
                                                if (!lockedHorizontal) {
                                                    slopX += dx
                                                    slopY += change.position.y - change.previousPosition.y
                                                    if (maxOf(abs(slopX), abs(slopY)) <= touchSlop) continue
                                                    if (abs(slopY) >= abs(slopX)) {
                                                        finished = true
                                                        continue
                                                    }
                                                    lockedHorizontal = true
                                                }
                                                miniDismissOffsetPx = if (session.isPlaying) {
                                                    (miniDismissOffsetPx + dx)
                                                        .coerceIn(-miniDismissMaxOffsetPx, miniDismissMaxOffsetPx)
                                                } else {
                                                    miniDismissOffsetPx + dx
                                                }
                                                change.consume()
                                            }
                                        }
                                    }
                                },
                                file = session.currentFile,
                                title = resolveMiniPlayerTitle(session.title, session.currentFile),
                                artist = resolveMiniPlayerArtist(session.artist, session.currentFile),
                                metadataTitleResolved = session.title.isNotBlank(),
                                artwork = session.artwork,
                                noArtworkIcon = placeholderArtworkIconForFile(session.currentFile, session.decoderName),
                                artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                isPlaying = session.isPlaying,
                                playbackStartInProgress = false,
                                seekInProgress = false,
                                canResumeStoppedTrack = session.canResume(),
                                positionSeconds = session.positionSeconds,
                                durationSeconds = session.durationSeconds,
                                hasReliableDuration = session.hasReliableDuration,
                                previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                canPreviousTrack = session.currentFile != null,
                                canNextTrack = session.currentFile != null,
                                canPreviousSubtune = session.subtuneCount > 1 && session.subtuneIndex > 0,
                                canNextSubtune = session.subtuneCount > 1 && session.subtuneIndex + 1 < session.subtuneCount,
                                currentSubtuneIndex = session.subtuneIndex,
                                subtuneCount = session.subtuneCount,
                                onExpand = {
                                    miniExpandPreviewProgress = 0f
                                    isPlayerExpanded = true
                                },
                                onExpandDragProgress = { miniExpandPreviewProgress = it },
                                onExpandDragCommit = {
                                    miniExpandPreviewProgress = 0f
                                    isPlayerExpanded = true
                                },
                                onPreviousTrack = { playPreviousTrackFromUi() },
                                onForcePreviousTrack = { playAdjacentTrack(-1, stopAtBoundary = false) },
                                onNextTrack = { playAdjacentTrack(1, stopAtBoundary = true) },
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
                                    canResumeStoppedTrack = session.canResume(),
                                    onPlay = { session.play() },
                                    onPause = { session.pause() },
                                    onStopAndClear = { session.stop() },
                                    durationSeconds = session.durationSeconds,
                                    positionSeconds = session.positionSeconds,
                                    positionSecondsProvider = { session.positionSeconds },
                                    canPreviousTrack = session.currentFile != null,
                                    canNextTrack = session.currentFile != null,
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
                                    onPreviousTrack = { playPreviousTrackFromUi() },
                                    onForcePreviousTrack = { playAdjacentTrack(-1, stopAtBoundary = false) },
                                    onNextTrack = { playAdjacentTrack(1, stopAtBoundary = true) },
                                    onPreviousSubtune = { session.previousSubtune() },
                                    onNextSubtune = { session.nextSubtune() },
                                    onOpenSubtuneSelector = { showSubtuneSelectorDialog = true },
                                    canPreviousSubtune = session.subtuneCount > 1 && session.subtuneIndex > 0,
                                    canNextSubtune = session.subtuneCount > 1 && session.subtuneIndex + 1 < session.subtuneCount,
                                    canOpenSubtuneSelector = session.subtuneCount > 1,
                                    playlists = playlistLibraryState.playlists,
                                    onAddToPlaylist = { playlistId, newTitle ->
                                        buildCurrentTrackEntry()?.let { entry ->
                                            addEntriesToPlaylist(listOf(entry), playlistId, newTitle)
                                        }
                                    },
                                    onRemoveFromPlaylist = { playlistId ->
                                        val path = session.currentFile?.absolutePath
                                        if (path != null) {
                                            removeSourceFromPlaylist(
                                                playlistId,
                                                path,
                                                session.subtuneIndex.takeIf { session.subtuneCount > 1 }
                                            )
                                        }
                                    },
                                    canOpenPlaylistSelector = true,
                                    onOpenPlaylistSelector = { showPlaylistSelectorDialog = true },
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
                                    onOpenAudioEffects = { openAudioEffectsDialog() }
                                )
                            }
                        }
                    }

                    if (showUrlOrPathDialog) {
                        UrlOrPathDialog(
                            input = urlOrPathInput,
                            forceCaching = urlOrPathForceCaching,
                            onInputChange = { urlOrPathInput = it },
                            onForceCachingChange = { checked ->
                                urlOrPathForceCaching = checked
                                prefs.edit()
                                    .putBoolean(AppPreferenceKeys.URL_PATH_FORCE_CACHING, checked)
                                    .apply()
                            },
                            onDismiss = { showUrlOrPathDialog = false },
                            onOpen = { confirmUrlOrPathOpen() }
                        )
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

                    if (showPlaylistSelectorDialog) {
                        PlatformBackHandler(enabled = true) {
                            showPlaylistSelectorDialog = false
                        }
                        val selectorPlaylist = activePlaylist
                        PlaylistSelectorDialog(
                            title = "Playlist",
                            subtitle = selectorPlaylist?.title,
                            shuffleActive = false,
                            entries = selectorPlaylist?.entries ?: playlistLibraryState.favorites,
                            currentEntryId = activePlaylistEntryId,
                            onSelectEntry = { entry ->
                                activePlaylistEntryId = entry.id
                                resolvePlaylistEntryLocalFile(entry.source)?.let { playFile(it) }
                            },
                            onDismiss = { showPlaylistSelectorDialog = false },
                            onSaveAsPlaylist = selectorPlaylist?.takeIf { it.entries.isNotEmpty() }?.let { playlist ->
                                {
                                    selectorImportDialogTitle = "Save playlist"
                                    selectorImportTitle = playlist.title
                                    selectorImportEntries = playlist.entries
                                }
                            },
                            onEntryAddTrackToPlaylist = { entry ->
                                selectorImportDialogTitle = "Add to playlist"
                                selectorImportTitle = null
                                selectorImportEntries = listOf(entry)
                            },
                            onEntryToggleFavorite = { entry -> togglePlaylistEntryFavorite(entry) },
                            isEntryFavorite = { entry ->
                                playlistContainsTrack(playlistLibraryState.favorites, entry.source, entry.subtuneIndex)
                            }
                        )
                    }

                    selectorImportEntries?.let { importEntries ->
                        PlatformBackHandler(enabled = true) {
                            selectorImportEntries = null
                            selectorImportTitle = null
                        }
                        AddToPlaylistChooserDialog(
                            dialogTitle = selectorImportDialogTitle,
                            initialNewPlaylistTitle = selectorImportTitle,
                            playlists = playlistLibraryState.playlists,
                            pendingSources = importEntries.map { it.source }.toSet(),
                            onConfirm = { playlistId, newTitle ->
                                addEntriesToPlaylist(importEntries, playlistId, newTitle)
                                selectorImportEntries = null
                                selectorImportTitle = null
                            },
                            onRemoveFromPlaylist = { playlistId ->
                                val matchingSources = importEntries.map { it.source to it.subtuneIndex }.toSet()
                                if (playlistId == FAVORITES_PLAYLIST_ID) {
                                    val filtered = playlistLibraryState.favorites.filterNot { fav ->
                                        (fav.source to fav.subtuneIndex) in matchingSources
                                    }
                                    if (filtered.size != playlistLibraryState.favorites.size) {
                                        onPlaylistLibraryStateChanged(
                                            playlistLibraryState.copy(favorites = filtered)
                                        )
                                    }
                                } else {
                                    val target = playlistLibraryState.playlists.firstOrNull { it.id == playlistId }
                                    if (target != null) {
                                        val matching = target.entries.filter {
                                            (it.source to it.subtuneIndex) in matchingSources
                                        }
                                        var updated = playlistLibraryState
                                        matching.forEach { match ->
                                            updated = removeStoredPlaylistEntries(updated, playlistId, setOf(match.id))
                                        }
                                        onPlaylistLibraryStateChanged(updated)
                                    }
                                }
                            },
                            onDismiss = {
                                selectorImportEntries = null
                                selectorImportTitle = null
                            }
                        )
                    }

                    if (showAudioEffectsDialog) {
                        PlatformBackHandler(enabled = true) {
                            dismissAudioEffectsDialog()
                        }
                        val tempEditedDspSettings =
                            if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) tempCoreDspSettings
                            else tempGlobalDspSettings
                        AudioEffectsDialog(
                            masterVolumeDb = tempMasterVolumeDb,
                            pluginVolumeDb = tempPluginVolumeDb,
                            songVolumeDb = tempSongVolumeDb,
                            ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
                            forceMono = tempForceMono,
                            hasActiveCore = session.decoderName != null,
                            hasActiveSong = session.currentFile != null,
                            currentCoreName = session.decoderName,
                            onMasterVolumeChange = {
                                tempMasterVolumeDb = it
                                NativeBridge.setMasterGain(it)
                            },
                            onPluginVolumeChange = {
                                tempPluginVolumeDb = it
                                NativeBridge.setPluginGain(if (tempIgnoreCoreVolumeForSong) 0f else it)
                            },
                            onSongVolumeChange = {
                                tempSongVolumeDb = it
                                NativeBridge.setSongGain(it)
                            },
                            onIgnoreCoreVolumeForSongChange = {
                                tempIgnoreCoreVolumeForSong = it
                                NativeBridge.setPluginGain(if (it) 0f else tempPluginVolumeDb)
                            },
                            onForceMonoChange = {
                                tempForceMono = it
                                NativeBridge.setForceMono(it)
                            },
                            onDspNamespaceSelectionChange = { value ->
                                tempDspNamespaceSelection =
                                    if (value == "core" && session.decoderName != null) {
                                        DspSettingsNamespace.CurrentCore
                                    } else {
                                        DspSettingsNamespace.Global
                                    }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspIgnoreGlobalForCurrentCoreChange = {
                                tempCoreIgnoreGlobalDsp = it
                                applyCurrentTempDspSettingsToNative()
                            },
                            dspBassEnabled = tempEditedDspSettings.bassEnabled,
                            dspBassDepth = tempEditedDspSettings.bassDepth,
                            dspBassRange = tempEditedDspSettings.bassRange,
                            dspSurroundEnabled = tempEditedDspSettings.surroundEnabled,
                            dspSurroundDepth = tempEditedDspSettings.surroundDepth,
                            dspSurroundDelayMs = tempEditedDspSettings.surroundDelayMs,
                            dspReverbEnabled = tempEditedDspSettings.reverbEnabled,
                            dspReverbDepth = tempEditedDspSettings.reverbDepth,
                            dspReverbPreset = tempEditedDspSettings.reverbPreset,
                            dspBitCrushEnabled = tempEditedDspSettings.bitCrushEnabled,
                            dspBitCrushBits = tempEditedDspSettings.bitCrushBits,
                            dspNamespaceSelection =
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) "core" else "global",
                            dspIgnoreGlobalForCurrentCore = tempCoreIgnoreGlobalDsp,
                            hasActiveCurrentCoreDspParameters =
                                session.decoderName != null && (coreDspHasOverrides || coreIgnoreGlobalDsp),
                            onDspBassEnabledChange = {
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(bassEnabled = it)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(bassEnabled = it)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspBassDepthChange = {
                                val normalized = it.coerceIn(0, 4)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(bassDepth = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(bassDepth = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspBassRangeChange = {
                                val normalized = it.coerceIn(0, 4)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(bassRange = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(bassRange = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspSurroundEnabledChange = {
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(surroundEnabled = it)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundEnabled = it)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspSurroundDepthChange = {
                                val normalized = it.coerceIn(1, 16)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(surroundDepth = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundDepth = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspSurroundDelayMsChange = {
                                val normalized = normalizeSurroundDelayMsPref(it)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(surroundDelayMs = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(surroundDelayMs = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspReverbEnabledChange = {
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(reverbEnabled = it)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbEnabled = it)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspReverbDepthChange = {
                                val normalized = it.coerceIn(1, 16)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(reverbDepth = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbDepth = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspReverbPresetChange = {
                                val normalized = it.coerceIn(0, 28)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(reverbPreset = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(reverbPreset = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspBitCrushEnabledChange = {
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(bitCrushEnabled = it)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(bitCrushEnabled = it)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDspBitCrushBitsChange = {
                                val normalized = it.coerceIn(1, 24)
                                if (tempDspNamespaceSelection == DspSettingsNamespace.CurrentCore) {
                                    tempCoreDspSettings = tempCoreDspSettings.copy(bitCrushBits = normalized)
                                } else {
                                    tempGlobalDspSettings = tempGlobalDspSettings.copy(bitCrushBits = normalized)
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onResetVolumeTab = {
                                tempMasterVolumeDb = 0f
                                tempPluginVolumeDb = 0f
                                tempSongVolumeDb = 0f
                                tempIgnoreCoreVolumeForSong = false
                                tempForceMono = false
                                NativeBridge.setMasterGain(0f)
                                NativeBridge.setPluginGain(0f)
                                NativeBridge.setSongGain(0f)
                                NativeBridge.setForceMono(false)
                            },
                            onResetDspScope = { scope ->
                                val defaults = defaultDspSettings()
                                if (scope == "core" && session.decoderName != null) {
                                    tempCoreDspSettings = defaults
                                    tempCoreIgnoreGlobalDsp = false
                                } else {
                                    tempGlobalDspSettings = defaults
                                }
                                applyCurrentTempDspSettingsToNative()
                            },
                            onDismiss = { dismissAudioEffectsDialog() },
                            onConfirm = { confirmAudioEffectsDialog() }
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
