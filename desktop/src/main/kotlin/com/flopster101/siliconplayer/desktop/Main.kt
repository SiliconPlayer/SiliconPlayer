package com.flopster101.siliconplayer.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
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
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import com.flopster101.siliconplayer.DomainStoreDirs
import com.flopster101.siliconplayer.HomeScreen
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.MiniPlayerBar
import com.flopster101.siliconplayer.miniPlayerHiddenForExpand
import com.flopster101.siliconplayer.playerDragPreviewVisible
import com.flopster101.siliconplayer.playerPreviewAlpha
import com.flopster101.siliconplayer.playerPreviewOffsetPx
import com.flopster101.siliconplayer.resolveMiniPlayerArtist
import com.flopster101.siliconplayer.resolveMiniPlayerTitle
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.StoragePresentation
import com.flopster101.siliconplayer.FolderEntryAction
import com.flopster101.siliconplayer.SourceEntryAction
import com.flopster101.siliconplayer.NetworkNode
import com.flopster101.siliconplayer.NetworkCredentialStore
import com.flopster101.siliconplayer.NetworkNodesHolder
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.LocalAppConfigDir
import com.flopster101.siliconplayer.ManualSourceType
import com.flopster101.siliconplayer.MANUAL_INPUT_INVALID_MESSAGE
import com.flopster101.siliconplayer.resolveManualSourceInput
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.RemoteLoadUiStateHolder
import com.flopster101.siliconplayer.platform.rememberRemoteSourceExportSupport
import com.flopster101.siliconplayer.formatSourceIdForDisplay
import com.flopster101.siliconplayer.resolvePlaybackSourceLabel
import com.flopster101.siliconplayer.storagePresentationForPath
import com.flopster101.siliconplayer.resolveNetworkNodeHttpSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.readNetworkNodes
import com.flopster101.siliconplayer.writeNetworkNodes
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.CoreOptionApplyPolicy
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.AdPlugOptionKeys
import com.flopster101.siliconplayer.AyflyOptionKeys
import com.flopster101.siliconplayer.CrsidOptionKeys
import com.flopster101.siliconplayer.FfmpegOptionKeys
import com.flopster101.siliconplayer.FurnaceOptionKeys
import com.flopster101.siliconplayer.GmeOptionKeys
import com.flopster101.siliconplayer.HivelyTrackerOptionKeys
import com.flopster101.siliconplayer.KlystrackOptionKeys
import com.flopster101.siliconplayer.LazyUsf2OptionKeys
import com.flopster101.siliconplayer.Sc68OptionKeys
import com.flopster101.siliconplayer.SidPlayFpOptionKeys
import com.flopster101.siliconplayer.UadeOptionKeys
import com.flopster101.siliconplayer.VgmPlayConfig
import com.flopster101.siliconplayer.VgmPlayOptionKeys
import com.flopster101.siliconplayer.Vio2sfOptionKeys
import com.flopster101.siliconplayer.XmpConfig
import com.flopster101.siliconplayer.XmpOptionKeys
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.data.compareFileNamesNatural
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.mergeRecentPlayedTrackArtworkCacheKey
import com.flopster101.siliconplayer.platform.LocalArtworkCacheSupport
import com.flopster101.siliconplayer.platform.LocalLibraryRepository
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.ToastHandler
import com.flopster101.siliconplayer.platform.PlatformBackHandler
import com.flopster101.siliconplayer.recentArtworkCacheKeyForSource
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.HttpFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.SmbFileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.FilenameDisplayMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationPerformanceMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.screens.rememberVisualizationUiState
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import com.flopster101.siliconplayer.ui.dialogs.PlaylistSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.PlayWithDialog
import com.flopster101.siliconplayer.ui.dialogs.TrackInfoDialog
import com.flopster101.siliconplayer.ui.dialogs.UrlOrPathDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import com.flopster101.siliconplayer.ui.screens.PlaylistsScreen
import com.flopster101.siliconplayer.ui.screens.LibrarySurfaceState
import com.flopster101.siliconplayer.ui.screens.PlaylistsSurfaceDestination
import com.flopster101.siliconplayer.PlaylistEntrySortMode
import com.flopster101.siliconplayer.PlaylistLibraryState
import com.flopster101.siliconplayer.PlaylistStoredFormat
import com.flopster101.siliconplayer.PlaylistTrackEntry
import com.flopster101.siliconplayer.StoredPlaylist
import com.flopster101.siliconplayer.appendStoredPlaylistEntries
import com.flopster101.siliconplayer.library.LibraryAlbum
import com.flopster101.siliconplayer.library.LibraryAlbumDetail
import com.flopster101.siliconplayer.library.LibraryCollections
import com.flopster101.siliconplayer.library.libraryAlbumDetailForTracks
import com.flopster101.siliconplayer.moveFavoriteTrack
import com.flopster101.siliconplayer.moveStoredPlaylistEntry
import com.flopster101.siliconplayer.readPlaylistLibraryState
import com.flopster101.siliconplayer.removeFavoriteTrack
import com.flopster101.siliconplayer.removeFavoriteTracks
import com.flopster101.siliconplayer.removeStoredPlaylistEntries
import com.flopster101.siliconplayer.removeStoredPlaylistEntry
import com.flopster101.siliconplayer.renameStoredPlaylist
import com.flopster101.siliconplayer.resolveAdjacentIndex
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.setStoredPlaylistPinned
import com.flopster101.siliconplayer.FAVORITES_PLAYLIST_ID
import com.flopster101.siliconplayer.favoritesAsStoredPlaylist
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
import com.flopster101.siliconplayer.BrowserLaunchState
import com.flopster101.siliconplayer.BrowserOpenRequest
import com.flopster101.siliconplayer.BrowserOpenTarget
import com.flopster101.siliconplayer.browserOpenRequest
import com.flopster101.siliconplayer.clearRememberedBrowserLaunchState
import com.flopster101.siliconplayer.persistRememberedBrowserLaunchState
import com.flopster101.siliconplayer.readRecentEntries
import com.flopster101.siliconplayer.SessionResumeSnapshot
import com.flopster101.siliconplayer.hasValidPosition
import com.flopster101.siliconplayer.readRememberedBrowserLaunchState
import com.flopster101.siliconplayer.readSessionResumeSnapshot
import com.flopster101.siliconplayer.writeSessionResumeSnapshot
import com.flopster101.siliconplayer.upsertFavoriteTrack
import com.flopster101.siliconplayer.upsertFavoriteTracks
import com.flopster101.siliconplayer.writePluginVolumeForDecoder
import com.flopster101.siliconplayer.upsertStoredPlaylist
import com.flopster101.siliconplayer.writePinnedHomeEntries
import com.flopster101.siliconplayer.writeRecentEntries
import com.flopster101.siliconplayer.writePlaylistLibraryState
import com.flopster101.siliconplayer.SettingsScreen
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.MainView
import com.flopster101.siliconplayer.SettingsRoute
import com.flopster101.siliconplayer.buildSettingsNavigationCoordinator
import com.flopster101.siliconplayer.BrowserRouteMode
import com.flopster101.siliconplayer.buildDecoderExtensionArtworkHintMap
import com.flopster101.siliconplayer.rememberBrowserRouteRenderState
import com.flopster101.siliconplayer.resolveBrowserRouteResolution
import com.flopster101.siliconplayer.resolveBrowserFolderForRecentSource
import com.flopster101.siliconplayer.resolveBrowserParentForRecentFolder
import com.flopster101.siliconplayer.resolveFolderOpenRequest
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.flopster101.siliconplayer.canOpenCoreSettingsForDecoder
import com.flopster101.siliconplayer.canSeekPlayback
import com.flopster101.siliconplayer.formatShortDuration
import com.flopster101.siliconplayer.hasReliableDuration
import com.flopster101.siliconplayer.placeholderArtworkIconForFile
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.supportsLiveRepeatMode
import com.flopster101.siliconplayer.ui.screens.LocalPlayerFocusIndicatorsEnabled
import com.flopster101.siliconplayer.ui.screens.PlayerScreen
import com.flopster101.siliconplayer.ui.theme.SiliconPlayerBaseTheme
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
private const val MasterMuteGainDb = -90f
private const val KeyboardSeekStepSeconds = 5.0

private data class ToastItem(val id: Long, val message: String)

@Composable
private fun ToastOverlay(
    items: List<ToastItem>,
    onDismiss: (Long) -> Unit
) {
    if (items.isEmpty()) return
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 16.dp).padding(start = 16.dp, end = 16.dp)
        ) {
            items.forEach { item ->
                key(item.id) {
                    var visible by remember { mutableStateOf(true) }
                    LaunchedEffect(item.id) {
                        delay(2500)
                        visible = false
                        onDismiss(item.id)
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = item.message,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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
    applyDesktopCachePoliciesOnLaunch(
        prefs = DesktopPreferencesProvider().getPreferences(AppPreferenceKeys.PREFS_NAME),
        cacheDir = DesktopPaths.cacheDir(),
        configDir = DesktopPaths.configDir()
    )
    val session = remember { DesktopPlaybackSession() }
    val restoredGeometry = remember { loadDesktopWindowGeometry(DesktopPaths.configDir()) }
    val windowState = rememberWindowState(
        width = (restoredGeometry?.widthDp ?: DesktopWindowDefaultWidthDp).dp,
        height = (restoredGeometry?.heightDp ?: DesktopWindowDefaultHeightDp).dp,
        position = if (restoredGeometry?.xDp != null && restoredGeometry.yDp != null) {
            WindowPosition.Absolute(restoredGeometry.xDp.dp, restoredGeometry.yDp.dp)
        } else {
            WindowPosition.PlatformDefault
        }
    )
    val backDispatcher = remember { DesktopBackDispatcher() }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var expandFromMiniDrag by remember { mutableStateOf(false) }
    var dragExpandCommitInProgress by remember { mutableStateOf(false) }
    var miniDismissOffsetPx by remember { mutableFloatStateOf(0f) }
    val miniDismissSettle = remember { Animatable(0f) }
    val miniDismissScope = rememberCoroutineScope()
    var miniDismissWidthPx by remember { mutableFloatStateOf(0f) }
    var miniDismissSettling by remember { mutableStateOf(false) }
    val supportedExtensions = remember {
        runCatching { NativeBridge.getSupportedExtensions().toSet() }.getOrElse { emptySet() }
    }
    var previousRestartsAfterThreshold by remember { mutableStateOf(true) }
    // Live-synced from prefs (see LaunchedEffect below); read at use time like Android.
    var autoPlayOnTrackSelect by remember { mutableStateOf(true) }
    var openPlayerOnTrackSelect by remember { mutableStateOf(true) }
    var playlistWrapNavigation by remember { mutableStateOf(true) }
    // Repeat init mirrors Android (preferred + persist flag); values load in the pref effect below.
    var preferredRepeatMode by remember { mutableStateOf(RepeatMode.None) }
    var persistRepeatMode by remember { mutableStateOf(true) }
    var recentFilesLimit by remember { mutableIntStateOf(20) }
    var recentFoldersLimit by remember { mutableIntStateOf(10) }
    var currentDirectory by remember {
        mutableStateOf(File(System.getProperty("user.home") ?: "/"))
    }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }
    var settingsRouteHistory by remember { mutableStateOf<List<SettingsRoute>>(emptyList()) }
    var settingsReturnView by remember { mutableStateOf(MainView.Home) }
    var selectedPluginName by remember { mutableStateOf<String?>(null) }

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

    // Shared settings back-stack (route history + return view); mirrors Android.
    val settingsNavigationCoordinator = buildSettingsNavigationCoordinator(
        currentView = currentView,
        settingsRoute = settingsRoute,
        settingsRouteHistory = settingsRouteHistory,
        settingsReturnView = settingsReturnView,
        lastUsedCoreName = session.decoderName ?: session.lastUsedCoreName,
        setSettingsRoute = { settingsRoute = it },
        setSettingsRouteHistory = { settingsRouteHistory = it },
        setSettingsReturnView = { settingsReturnView = it },
        setCurrentView = { currentView = it },
        setSelectedPluginName = { selectedPluginName = it },
        setPlayerExpanded = { isPlayerExpanded = it }
    )

    fun enterSettings(targetRoute: SettingsRoute) {
        settingsReturnView = (if (currentView == MainView.Settings) settingsReturnView else currentView)
            .takeUnless { it == MainView.Settings }
            ?: MainView.Home
        settingsNavigationCoordinator.openSettingsRoute(targetRoute, true)
        currentView = MainView.Settings
    }

    val recentFiles = remember { mutableStateListOf<RecentPathEntry>() }
    val recentFolders = remember { mutableStateListOf<RecentPathEntry>() }
    val pinnedEntries = remember { mutableStateListOf<HomePinnedEntry>() }
    // Assigned once the platform artwork support is available below; persists the
    // shared recent-artwork thumbnail for a played source, mirroring Android's
    // scheduleRecentPlayedArtworkCacheBackfill.
    var scheduleRecentArtworkBackfill by remember { mutableStateOf<((String, String?) -> Unit)?>(null) }

    fun pinHomeEntry(entry: RecentPathEntry, isFolder: Boolean) {
        if (pinnedEntries.none { it.path == entry.path }) {
            pinnedEntries.add(
                HomePinnedEntry(
                    path = entry.path,
                    isFolder = isFolder,
                    title = entry.title,
                    artist = entry.artist,
                    sourceNodeId = entry.sourceNodeId,
                    artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey
                )
            )
        }
    }

    fun registerLoadedFile(file: File) {
        val ext = inferredPrimaryExtensionForName(file.name)?.uppercase(Locale.ROOT) ?: "FILE"
        val sourceId = session.currentSourceId
        val identity = sourceId ?: file.absolutePath
        val entry = RecentPathEntry(
            path = identity,
            locationId = null,
            title = session.title.ifBlank { file.name },
            artist = session.artist.ifBlank { ext },
            decoderName = session.decoderName,
            sourceNodeId = NetworkNodesHolder.current.firstOrNull { it.source == sourceId }?.id,
            artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(identity) ?: identity
        )
        recentFiles.removeAll { it.path == identity }
        recentFiles.add(0, entry)
        while (recentFiles.size > recentFilesLimit) {
            recentFiles.removeLast()
        }
        scheduleRecentArtworkBackfill?.invoke(identity, session.currentRequestUrl)

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
            while (recentFolders.size > recentFoldersLimit) {
                recentFolders.removeLast()
            }
        }
    }

    fun playFile(file: File) {
        if (session.loadFile(file, autoStart = autoPlayOnTrackSelect)) {
            registerLoadedFile(file)
            if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
        }
    }

    fun playSource(source: String, titleHint: String? = null, artistHint: String? = null) {
        val file = File(source)
        if (file.exists() && file.isFile) {
            playFile(file)
            return
        }
        if (session.loadSource(source, titleHint, artistHint, autoStart = autoPlayOnTrackSelect)) {
            if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
            val sourceId = session.currentSourceId
            val sourceNodeId = NetworkNodesHolder.current.firstOrNull { it.source == sourceId }?.id
            val identity = sourceId ?: source
            val entry = RecentPathEntry(
                path = identity,
                locationId = null,
                title = session.title.ifBlank { titleHint ?: source },
                artist = session.artist.ifBlank { artistHint ?: "Network" },
                decoderName = session.decoderName,
                sourceNodeId = sourceNodeId,
                artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(identity) ?: identity
            )
            recentFiles.removeAll { it.path == identity }
            recentFiles.add(0, entry)
            while (recentFiles.size > recentFilesLimit) {
                recentFiles.removeLast()
            }
            scheduleRecentArtworkBackfill?.invoke(identity, session.currentRequestUrl)
        }
    }

    fun listSiblingTracks(anchor: File): List<File> {
        val siblings = anchor.parentFile?.listFiles()
            ?.filter { it.isFile && fileMatchesSupportedExtensions(it, supportedExtensions) }
            ?: return emptyList()
        return siblings.sortedWith { left, right -> compareFileNamesNatural(left.name, right.name) }
    }

    LaunchedEffect(args) {
        if (args.isNotEmpty()) {
            val candidate = File(args[0])
            if (candidate.exists() && candidate.isFile) {
                playFile(candidate)
            }
        }
    }
    LaunchedEffect(isPlayerExpanded, miniExpandPreviewProgress) {
        if (!isPlayerExpanded && miniExpandPreviewProgress <= 0f) {
            dragExpandCommitInProgress = false
        }
    }
    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded && expandFromMiniDrag) {
            delay(350)
        }
        expandFromMiniDrag = false
    }

    val windowTitle = if (session.title.isNotBlank()) {
        if (session.artist.isNotBlank()) {
            "${session.artist} - ${session.title} - SiliconPlayer"
        } else {
            "${session.title} - SiliconPlayer"
        }
    } else {
        "SiliconPlayer Desktop"
    }

    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var dismissAudioEffectsDialogHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var toggleMasterMuteHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }
    var selectorImportEntries by remember { mutableStateOf<List<PlaylistTrackEntry>?>(null) }
    var selectorImportTitle by remember { mutableStateOf<String?>(null) }
    var selectorImportDialogTitle by remember { mutableStateOf("Add to playlist") }
    var externalTrackInfoDialogRequestToken by remember { mutableIntStateOf(0) }

    val toasts = remember { mutableStateListOf<ToastItem>() }
    var nextToastId by remember { mutableStateOf(0L) }
    Window(
        onCloseRequest = {
            saveDesktopWindowGeometry(
                DesktopPaths.configDir(),
                DesktopWindowGeometry(
                    widthDp = windowState.size.width.value,
                    heightDp = windowState.size.height.value,
                    xDp = (windowState.position as? WindowPosition.Absolute)?.x?.value,
                    yDp = (windowState.position as? WindowPosition.Absolute)?.y?.value
                )
            )
            session.dispose()
            exitApplication()
        },
        state = windowState,
        title = windowTitle,
        icon = painterResource("app_icon.webp"),
        onPreviewKeyEvent = { keyEvent ->
            // Capture-phase Escape: a focused child must never swallow back.
            if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape) {
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
                    if (!settingsNavigationCoordinator.popSettingsRoute()) {
                        settingsNavigationCoordinator.exitSettingsToReturnView()
                    }
                    return@Window true
                }
                if (currentView != MainView.Home) {
                    currentView = MainView.Home
                    return@Window true
                }
            }
            false
        },
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
                }
                if (keyEvent.key == Key.Spacebar) {
                    session.togglePlayPause()
                    return@Window true
                }
                if (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.DirectionRight) {
                    if (session.canSeek && session.durationSeconds > 0.0) {
                        val step = if (keyEvent.key == Key.DirectionLeft) {
                            -KeyboardSeekStepSeconds
                        } else {
                            KeyboardSeekStepSeconds
                        }
                        session.seekTo(session.positionSeconds + step)
                        return@Window true
                    }
                }
                if (keyEvent.key == Key.M) {
                    if (toggleMasterMuteHandler?.invoke() == true) {
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
            backDispatcher = backDispatcher,
            stopPlaybackForRefresh = { session.stop() },
            openAudioSettings = { enterSettings(SettingsRoute.GeneralAudio) },
            toastHandler = ToastHandler { msg -> toasts.add(ToastItem(nextToastId++, msg)) }
        ) {
            val prefs = LocalAppPreferences.current
            val configDir = LocalAppConfigDir.current
            var prefToken by remember { mutableIntStateOf(0) }
            DisposableEffect(prefs) {
                val listener = AppPreferences.OnChangeListener { _, _ -> prefToken++ }
                prefs.addListener(listener)
                onDispose { prefs.removeListener(listener) }
            }
            LaunchedEffect(prefs) { session.trackOptionsPrefs = prefs }
            // Decoder enable flags live in prefs; rebuild the browser icon hints on any prefs write.
            val decoderExtensionArtworkHints = remember(prefToken) {
                buildDecoderExtensionArtworkHintMap()
            }

            val themeMode = remember(prefToken, prefs) {
                ThemeMode.fromStorage(prefs.getString(AppPreferenceKeys.THEME_MODE, ThemeMode.Auto.storageValue))
            }
            val playerArtworkCornerRadiusDp = remember(prefToken, prefs) {
                prefs.getInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, AppDefaults.Player.artworkCornerRadiusDp)
            }
            // prefToken scope recomposes PlayerScreen below on any prefs write.
            val playerVisualizationPerformanceMode = remember(prefToken, prefs) {
                VisualizationPerformanceMode.fromStorage(
                    prefs.getString(
                        AppPreferenceKeys.VISUALIZATION_PERFORMANCE_MODE,
                        AppDefaults.Visualization.performanceMode.storageValue
                    )
                )
            }
            val playerVisualizationShowDebugInfo = remember(prefToken, prefs) {
                prefs.getBoolean(
                    AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO,
                    AppDefaults.Visualization.showDebugInfo
                )
            }
            val playerShowAudioOutputRouteChip = remember(prefToken, prefs) {
                prefs.getBoolean(
                    AppPreferenceKeys.PLAYER_SHOW_AUDIO_OUTPUT_CHIP,
                    AppDefaults.Player.showAudioOutputRouteChip
                )
            }
            val playerCanvasTapToSeekSeconds = remember(prefToken, prefs) {
                prefs.getInt(
                    AppPreferenceKeys.CANVAS_TAP_TO_SEEK_SECONDS,
                    AppDefaults.Player.canvasTapToSeekSeconds
                )
            }
            val playerFilenameDisplayMode = remember(prefToken, prefs) {
                FilenameDisplayMode.fromStorage(
                    prefs.getString(
                        AppPreferenceKeys.FILENAME_DISPLAY_MODE,
                        AppDefaults.Player.filenameDisplayMode.storageValue
                    )
                )
            }
            val playerFilenameOnlyWhenTitleMissing = remember(prefToken, prefs) {
                prefs.getBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, false)
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
            val appCacheDir = LocalAppCacheDir.current
            val remoteSourceExportSupport = rememberRemoteSourceExportSupport()
            val remoteCacheOpenScope = rememberCoroutineScope()
            var remoteCacheOpenJob by remember { mutableStateOf<Job?>(null) }
            val toastHandler = LocalToastHandler.current

    fun openBrowserRequest(request: BrowserOpenRequest) {
        val launchState = request.launchState
        val directoryPath = launchState.directoryPath.orEmpty()
        val isHttp = launchState.httpSourceNodeId != null ||
            directoryPath.startsWith("http://", ignoreCase = true) ||
            directoryPath.startsWith("https://", ignoreCase = true)
        val isRemote = isHttp || launchState.smbSourceNodeId != null ||
            directoryPath.startsWith("smb://", ignoreCase = true)
        if (!isRemote) {
            openLocalBrowser(File(directoryPath))
            return
        }
        if (isHttp) {
            openRemoteBrowser(
                input = directoryPath,
                smbSourceNodeId = null,
                httpSourceNodeId = launchState.httpSourceNodeId,
                httpRootPath = launchState.httpRootPath,
                allowHostShareNavigation = false
            )
            return
        }
        val allowHostShareNavigation = launchState.smbSourceNodeId
            ?.let { sourceNodeId -> networkNodes.firstOrNull { it.id == sourceNodeId } }
            ?.let(::resolveNetworkNodeSmbSpec)
            ?.share
            ?.trim()
            ?.isEmpty() == true
        openRemoteBrowser(
            input = directoryPath,
            smbSourceNodeId = launchState.smbSourceNodeId,
            httpSourceNodeId = null,
            httpRootPath = null,
            allowHostShareNavigation = allowHostShareNavigation
        )
    }

    fun openResolvedBrowserTarget(target: BrowserOpenTarget?, failureMessage: String) {
        if (target == null) {
            toastHandler.showToast(failureMessage)
        } else {
            openBrowserRequest(
                browserOpenRequest(
                    locationId = target.locationId,
                    directoryPath = target.directoryPath,
                    smbSourceNodeId = target.smbSourceNodeId,
                    httpSourceNodeId = target.httpSourceNodeId
                )
            )
        }
    }

            val artworkCacheSupport = LocalArtworkCacheSupport.current
            val recentArtworkBackfillScope = rememberCoroutineScope()
            val recentArtworkBackfillJobs = remember { mutableMapOf<String, Job>() }
            LaunchedEffect(artworkCacheSupport) {
                scheduleRecentArtworkBackfill = { playedSourceId, requestUrlHint ->
                    recentArtworkBackfillJobs.remove(playedSourceId)?.cancel()
                    recentArtworkBackfillJobs[playedSourceId] =
                        recentArtworkBackfillScope.launch(Dispatchers.IO) {
                            try {
                                var cacheKey: String? = null
                                for (attempt in 0 until 10) {
                                    cacheKey = artworkCacheSupport.ensureThumbnailCached(playedSourceId, requestUrlHint)
                                    if (cacheKey != null) break
                                    delay(400L)
                                }
                                val resolved = cacheKey ?: return@launch
                                val merged = mergeRecentPlayedTrackArtworkCacheKey(
                                    recentFiles.toList(),
                                    playedSourceId,
                                    resolved
                                )
                                if (merged != recentFiles) {
                                    withContext(Dispatchers.Main) {
                                        recentFiles.clear()
                                        recentFiles.addAll(merged)
                                    }
                                }
                            } finally {
                                recentArtworkBackfillJobs.remove(playedSourceId)
                            }
                        }
                }
            }
            // CORE-OPTION PUSH: setters persist only; this single observer fans core
            // writes to the engine, mirroring Android AppNavigationCoreEffects.
            DisposableEffect(prefs, session) {
                val listener = AppPreferences.OnChangeListener { _, key ->
                    if (key != null) {
                        runCatching {
                            pushDesktopCorePrefToNative(
                                prefs = prefs,
                                key = key,
                                toast = toastHandler,
                                isPlaying = session.isPlaying,
                                hasCurrentTrack = session.currentFile != null,
                                activeDecoderName = session.decoderName
                            )
                        }
                    }
                }
                prefs.addListener(listener)
                onDispose { prefs.removeListener(listener) }
            }
            val clipboardManager = LocalClipboardManager.current
            fun registerRemoteSourceEntry(source: String) {
                val entry = RecentPathEntry(
                    path = source,
                    locationId = null,
                    title = session.title.ifBlank { source },
                    artist = session.artist.ifBlank { "Network" },
                    decoderName = session.decoderName,
                    artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(source) ?: source
                )
                recentFiles.removeAll { it.path == source }
                recentFiles.add(0, entry)
                while (recentFiles.size > recentFilesLimit) {
                    recentFiles.removeLast()
                }
                scheduleRecentArtworkBackfill?.invoke(source, session.currentRequestUrl)
            }

            // "Play as cached": download the remote source, then open the local copy.
            fun playRemoteSourceAsCached(source: String) {
                val resolved = resolveManualSourceInput(source)
                if (resolved == null ||
                    resolved.type == ManualSourceType.LocalFile ||
                    resolved.type == ManualSourceType.LocalDirectory
                ) {
                    toastHandler.showToast(MANUAL_INPUT_INVALID_MESSAGE)
                    return
                }
                remoteCacheOpenJob?.cancel()
                remoteCacheOpenJob = remoteCacheOpenScope.launch {
                    try {
                        val cachedFile = withContext(Dispatchers.IO) {
                            prepareRemotePlaybackCacheFile(
                                support = remoteSourceExportSupport,
                                resolution = resolved,
                                prefs = prefs,
                                cacheRoot = File(appCacheDir, REMOTE_SOURCE_CACHE_DIR),
                                onStatus = { RemoteLoadUiStateHolder.current = it }
                            )
                        }.getOrElse { error ->
                            if (error is CancellationException) return@launch
                            toastHandler.showToast("Failed to cache remote source: ${error.message ?: "unknown error"}")
                            return@launch
                        }
                        if (session.loadFile(cachedFile, autoStart = autoPlayOnTrackSelect)) {
                            registerRemoteSourceEntry(resolved.requestUrl)
                            if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
                        }
                    } finally {
                        RemoteLoadUiStateHolder.current = null
                    }
                }
            }

            fun confirmUrlOrPathOpen() {
                showUrlOrPathDialog = false
                val resolved = resolveManualSourceInput(urlOrPathInput)
                when {
                    resolved == null -> toastHandler.showToast(MANUAL_INPUT_INVALID_MESSAGE)
                    resolved.type == ManualSourceType.LocalDirectory ->
                        resolved.directoryPath?.let { openLocalBrowser(File(it)) }
                    resolved.type == ManualSourceType.LocalFile ->
                        resolved.localFile?.let { playFile(it) }
                    urlOrPathForceCaching -> playRemoteSourceAsCached(resolved.requestUrl)
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
            // Mirrors Android's LibraryDetailState; loads the album/artist behind
            // the detail destination so it never renders with a null detail.
            var selectedLibraryAlbumName by remember { mutableStateOf<String?>(null) }
            var selectedLibraryArtistName by remember { mutableStateOf<String?>(null) }
            var libraryAlbumDetail by remember { mutableStateOf<LibraryAlbumDetail?>(null) }
            var libraryArtistAlbums by remember { mutableStateOf<List<LibraryAlbum>?>(null) }
            val libraryRepository = LocalLibraryRepository.current
            LaunchedEffect(selectedLibraryAlbumName) {
                libraryAlbumDetail = selectedLibraryAlbumName?.let { name ->
                    libraryAlbumDetailForTracks(name, libraryRepository.albumTracks(name))
                }
            }
            LaunchedEffect(selectedLibraryArtistName) {
                libraryArtistAlbums = selectedLibraryArtistName?.let { artist ->
                    libraryRepository.artistAlbums(artist)
                }
            }
            var activePlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
            var activePlaylistEntryId by remember { mutableStateOf<String?>(null) }
            var activePlaylistShuffleActive by remember { mutableStateOf(false) }
            // Queue entry open: playlist context + per-entry subtune, mirrors Android openPlaylistEntry.
            fun openQueueEntry(queue: StoredPlaylist, entry: PlaylistTrackEntry, shuffleActive: Boolean): Boolean {
                val file = resolvePlaylistEntryLocalFile(entry.source) ?: return false
                activePlaylist = queue
                activePlaylistEntryId = entry.id
                activePlaylistShuffleActive = shuffleActive
                if (!session.loadFile(file, autoStart = autoPlayOnTrackSelect)) return false
                val subtune = entry.subtuneIndex
                if (subtune != null && subtune in 0 until session.subtuneCount) {
                    session.selectSubtune(subtune)
                }
                registerLoadedFile(file)
                if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
                return true
            }
            // Playlist-queue advance; falls through when the current track has no entry here.
            // Wrap follows the shared shouldWrap expression; callers pass it explicitly.
            fun playAdjacentPlaylistEntry(offset: Int, wrapOverride: Boolean?, notifyWrap: Boolean): Boolean {
                val playlist = activePlaylist ?: return false
                val entries = playlist.entries
                if (entries.isEmpty()) return false
                val currentIndex = entries.indexOfFirst { it.id == activePlaylistEntryId }
                if (currentIndex !in entries.indices) return false
                val shouldWrap = wrapOverride ?: playlistWrapNavigation
                val firstTarget = resolveAdjacentIndex(currentIndex, offset, entries.size, shouldWrap)
                    ?: return false
                if (shouldWrap && firstTarget != currentIndex + offset && notifyWrap) {
                    toastHandler.showToast(if (offset < 0) "Wrapped to last track" else "Wrapped to first track")
                }
                // Skip entries with no local file, continuing in the same direction.
                val step = if (offset < 0) -1 else 1
                for (probe in 0 until entries.size) {
                    val targetIndex = if (shouldWrap) {
                        (((firstTarget + probe * step) % entries.size) + entries.size) % entries.size
                    } else {
                        firstTarget + probe * step
                    }
                    val target = entries.getOrNull(targetIndex) ?: continue
                    val queue = activePlaylist ?: playlist
                    if (openQueueEntry(queue, target, activePlaylistShuffleActive)) return true
                }
                return false
            }
            fun playAdjacentSiblingTrack(offset: Int, wrapOverride: Boolean?): Boolean {
                val current = session.currentFile ?: return false
                val siblings = listSiblingTracks(current)
                if (siblings.isEmpty()) return false
                val index = siblings.indexOfFirst { samePath(it.absolutePath, current.absolutePath) }
                if (index < 0) return false
                val shouldWrap = wrapOverride ?: playlistWrapNavigation
                val targetIndex = resolveAdjacentIndex(index, offset, siblings.size, shouldWrap) ?: return false
                playFile(siblings[targetIndex])
                return true
            }
            // Playlist and browser queues take separate wrap flags, mirroring Android's
            // UI action (playlist never wraps on manual next) and poll path (passthrough).
            fun playQueueAdjacentTrack(
                offset: Int,
                stopAtBoundary: Boolean,
                playlistWrapOverride: Boolean?,
                browserWrapOverride: Boolean?,
                notifyWrap: Boolean = false
            ): Boolean {
                val moved = playAdjacentPlaylistEntry(offset, playlistWrapOverride, notifyWrap) ||
                    playAdjacentSiblingTrack(offset, browserWrapOverride)
                if (!moved && stopAtBoundary && offset > 0 &&
                    !(browserWrapOverride ?: playlistWrapNavigation)
                ) {
                    session.stop()
                    return true
                }
                return moved
            }
            fun playQueuePreviousTrack(
                playlistWrapOverride: Boolean?,
                browserWrapOverride: Boolean?,
                notifyWrap: Boolean = false
            ) {
                val current = session.currentFile ?: return
                if (shouldRestartCurrentTrackOnPrevious(previousRestartsAfterThreshold, true, session.positionSeconds)) {
                    session.seekTo(0.0)
                    return
                }
                if (playQueueAdjacentTrack(
                        -1,
                        stopAtBoundary = false,
                        playlistWrapOverride,
                        browserWrapOverride,
                        notifyWrap
                    )
                ) return
                session.seekTo(0.0)
            }
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
                val path = session.currentSourceId ?: return null
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
            var masterMuted by remember { mutableStateOf(false) }
            fun applyMasterGainToNative() {
                NativeBridge.setMasterGain(if (masterMuted) MasterMuteGainDb else masterVolumeDb)
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
                applyMasterGainToNative()
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
                val path = session.currentSourceId
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
            SideEffect {
                toggleMasterMuteHandler = {
                    masterMuted = !masterMuted
                    applyMasterGainToNative()
                    toasts.add(ToastItem(nextToastId++, if (masterMuted) "Muted" else "Unmuted"))
                    true
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
                session.currentSourceId?.let { path ->
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
                autoPlayOnTrackSelect =
                    prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, true)
                openPlayerOnTrackSelect =
                    prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, true)
                playlistWrapNavigation =
                    prefs.getBoolean(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, true)
                recentFilesLimit =
                    prefs.getInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, 20)
                recentFoldersLimit =
                    prefs.getInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, 10)
                session.fadePauseResume =
                    prefs.getBoolean(AppPreferenceKeys.FADE_PAUSE_RESUME, true)
                persistRepeatMode =
                    prefs.getBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, true)
                preferredRepeatMode = RepeatMode.fromStorage(
                    prefs.getString(
                        AppPreferenceKeys.PREFERRED_REPEAT_MODE,
                        prefs.getString(
                            AppPreferenceKeys.SESSION_CURRENT_REPEAT_MODE,
                            RepeatMode.None.storageValue
                        )
                    )
                )
            }
            // Repeat persist mirrors Android AppNavigationPlaybackEffects; the
            // preferred sync re-resolves so a raced first load still lands right.
            LaunchedEffect(persistRepeatMode) {
                val editor = prefs.edit().putBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, persistRepeatMode)
                if (!persistRepeatMode) {
                    editor.remove(AppPreferenceKeys.PREFERRED_REPEAT_MODE)
                }
                editor.apply()
            }
            LaunchedEffect(preferredRepeatMode, persistRepeatMode) {
                if (persistRepeatMode) {
                    prefs.edit()
                        .putString(AppPreferenceKeys.PREFERRED_REPEAT_MODE, preferredRepeatMode.storageValue)
                        .apply()
                }
            }
            LaunchedEffect(preferredRepeatMode) {
                session.preferredRepeatMode = preferredRepeatMode
                if (session.currentFile != null) {
                    session.refreshRepeatMode()
                }
            }
            LaunchedEffect(prefs) {
                session.onAdvanceQueue = { wrap ->
                    playQueueAdjacentTrack(
                        1,
                        stopAtBoundary = false,
                        playlistWrapOverride = wrap,
                        browserWrapOverride = wrap
                    )
                }
            }
            // Restore/save the local browser directory gated by REMEMBER_BROWSER_LOCATION.
            var browserLocationRestored by remember { mutableStateOf(false) }
            LaunchedEffect(prefs) {
                if (!browserLocationRestored) {
                    browserLocationRestored = true
                    if (prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)) {
                        readRememberedBrowserLaunchState(prefs).directoryPath?.let { path ->
                            val dir = File(path)
                            if (dir.exists() && dir.isDirectory) currentDirectory = dir
                        }
                    }
                }
            }
            LaunchedEffect(currentDirectory) {
                if (prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)) {
                    persistRememberedBrowserLaunchState(
                        prefs,
                        BrowserLaunchState(directoryPath = currentDirectory.absolutePath)
                    )
                }
            }
            val rememberBrowserLocation = remember(prefToken, prefs) {
                prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)
            }
            LaunchedEffect(rememberBrowserLocation) {
                if (!rememberBrowserLocation) clearRememberedBrowserLaunchState(prefs)
            }
            // SESSION RESTORE (§7.6): prefill the last track paused at its saved
            // position, never auto-play. Mirrors Android; remote/archive sources
            // are skipped until the desktop remote-cache path (§7.12) exists.
            val pendingSessionRestore = remember {
                if (args.isNotEmpty()) null else readSessionResumeSnapshot(configDir)
            }
            var sessionRestoreConsumed by remember { mutableStateOf(pendingSessionRestore == null) }
            var hadLoadedTrack by remember { mutableStateOf(false) }
            LaunchedEffect(pendingSessionRestore) {
                try {
                    val snapshot = pendingSessionRestore ?: return@LaunchedEffect
                    val file = resolvePlaylistEntryLocalFile(snapshot.sourceId)
                    if (file == null || !file.isFile) {
                        writeSessionResumeSnapshot(configDir, null)
                        return@LaunchedEffect
                    }
                    // Previous process died mid-load; drop instead of crash-looping.
                    if (prefs.getString(AppPreferenceKeys.SESSION_LOAD_CRASH_GUARD_PATH, null) ==
                        snapshot.sourceId
                    ) {
                        prefs.edit().remove(AppPreferenceKeys.SESSION_LOAD_CRASH_GUARD_PATH).apply()
                        writeSessionResumeSnapshot(configDir, null)
                        return@LaunchedEffect
                    }
                    if (!snapshot.playlistId.isNullOrBlank() && !snapshot.entryId.isNullOrBlank()) {
                        // Favorites live outside the playlist list, so a favorites
                        // id restores the track without playlist context.
                        val playlist = playlistLibraryState.playlists.firstOrNull {
                            it.id == snapshot.playlistId
                        }
                        val entry = playlist?.entries?.firstOrNull { it.id == snapshot.entryId }
                        if (playlist != null && entry != null &&
                            samePath(entry.source, snapshot.sourceId)
                        ) {
                            activePlaylist = playlist
                            activePlaylistEntryId = entry.id
                            if (snapshot.shuffleActive) {
                                // Shuffled order isn't persisted; resume shuffled from here.
                                activePlaylistShuffleActive = true
                                activePlaylist = playlist.copy(
                                    entries = listOf(entry) +
                                        playlist.entries.filter { it.id != entry.id }.shuffled()
                                )
                            }
                        }
                    }
                    session.preferredRepeatMode = preferredRepeatMode
                    if (session.loadFile(file, autoStart = false)) {
                        val subtune = activePlaylist?.entries
                            ?.firstOrNull { it.id == activePlaylistEntryId }
                            ?.subtuneIndex
                        if (subtune != null && subtune in 0 until session.subtuneCount) {
                            session.selectSubtune(subtune)
                        }
                        if (snapshot.hasValidPosition() && session.canSeek) {
                            session.seekTo(snapshot.positionSeconds)
                        }
                        registerLoadedFile(file)
                        isPlayerSurfaceVisible = true
                    } else {
                        writeSessionResumeSnapshot(configDir, null)
                    }
                } finally {
                    sessionRestoreConsumed = true
                }
            }
            // Checkpoint writer, bucketed like Android's 0.5s buckets. Gated until
            // restore runs so startup never deletes the snapshot before it is read.
            val resumePositionBucket = (session.positionSeconds * 2.0).toInt()
            val resumeDurationBucket = (session.durationSeconds * 2.0).toInt()
            val resumeSourcePath = session.currentFile?.absolutePath
            LaunchedEffect(
                resumeSourcePath,
                resumePositionBucket,
                resumeDurationBucket,
                activePlaylist?.id,
                activePlaylistEntryId,
                activePlaylistShuffleActive,
                sessionRestoreConsumed
            ) {
                if (!sessionRestoreConsumed) return@LaunchedEffect
                if (resumeSourcePath == null) {
                    if (hadLoadedTrack) writeSessionResumeSnapshot(configDir, null)
                    return@LaunchedEffect
                }
                hadLoadedTrack = true
                val playlist = activePlaylist
                val entryId = activePlaylistEntryId?.trim().takeUnless { it.isNullOrBlank() }
                val playlistId = if (playlist != null && entryId != null &&
                    playlist.entries.any { it.id == entryId }
                ) {
                    playlist.id
                } else {
                    null
                }
                writeSessionResumeSnapshot(
                    configDir,
                    SessionResumeSnapshot(
                        sourceId = resumeSourcePath,
                        positionSeconds = session.positionSeconds,
                        durationSeconds = session.durationSeconds,
                        playlistId = playlistId,
                        entryId = if (playlistId != null) entryId else null,
                        shuffleActive = activePlaylistShuffleActive
                    )
                )
            }
            LaunchedEffect(Unit) {
                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                forceMono = prefs.getBoolean(AppPreferenceKeys.AUDIO_FORCE_MONO, false)
                applyMasterGainToNative()
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
                // Stored track-open options (unknown-duration + end-fade) for cold start.
                pushStoredTrackOptionsToNative(prefs)
                pushAudioPipelineConfigToNative(prefs)
                applyDspSettingsToNative(readGlobalDspSettings(prefs))
            }
            LaunchedEffect(session.currentSourceId, session.decoderName) {
                val path = session.currentSourceId
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
                readRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, recentFoldersLimit, prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        recentFolders.clear()
                        recentFolders.addAll(stored)
                    }
                readRecentEntries(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES, recentFilesLimit, prefs)
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
                        writeRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, folders, recentFoldersLimit)
                        writeRecentEntries(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES, files, recentFilesLimit)
                        writePinnedHomeEntries(configDir, pinned)
                    }
            }
            val currentTrackPath = session.currentSourceId
            val isCurrentTrackFavorited = currentTrackPath != null &&
                playlistLibraryState.favorites.any { it.source == currentTrackPath }

            val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
            val playerPreviewScreenHeightPx = with(LocalDensity.current) { windowState.size.height.toPx() }
            val miniDismissMaxOffsetPx = with(LocalDensity.current) { 108.dp.toPx() }
            val visualizationUiState = rememberVisualizationUiState(
                prefs = prefs,
                activeCoreName = session.decoderName,
                isPlayerSurfaceVisible = isPlayerSurfaceVisible
            )

            SiliconPlayerBaseTheme(darkTheme = darkTheme) {
                // Drops are external opens; honor the same Play-with gate as Android.
                var pendingDropPlayWithFile by remember { mutableStateOf<File?>(null) }
                val dropPlayWithFile = pendingDropPlayWithFile
                if (dropPlayWithFile != null) {
                    PlayWithDialog(
                        file = dropPlayWithFile,
                        prefs = prefs,
                        showDontAskAgain = true,
                        onPlay = {
                            pendingDropPlayWithFile = null
                            playFile(dropPlayWithFile)
                        },
                        onDismiss = { pendingDropPlayWithFile = null }
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .desktopFileDropTarget(
                            supportedExtensions = supportedExtensions,
                            onPlayFile = { file ->
                                if (prefs.getBoolean(AppPreferenceKeys.PLAY_WITH_EXTERNAL_OPEN_DIALOG, true)) {
                                    pendingDropPlayWithFile = file
                                } else {
                                    playFile(file)
                                }
                            },
                            onBrowseDirectory = { openLocalBrowser(it) },
                            onUnsupported = { toastHandler.showToast(DROP_UNSUPPORTED_MESSAGE) }
                        ),
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
                                enterSettings(SettingsRoute.Root)
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
                                            currentTrackPath = session.currentSourceId,
                                            currentTrackTitle = session.title,
                                            currentTrackArtist = session.artist,
                                            pinnedHomeEntries = pinnedEntries,
                                            recentFolders = recentFolders,
                                            recentPlayedFiles = recentFiles,
                                            storagePresentationForEntry = {
                                                storagePresentationForPath(it.path)
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
                                                if (entry.path.startsWith("playlist://")) {
                                                    val playlistId = entry.path.removePrefix("playlist://")
                                                    librarySurfaceState.selectedTabIndexState.intValue = 0
                                                    if (playlistId == FAVORITES_PLAYLIST_ID) {
                                                        librarySurfaceState.destinationState.value = PlaylistsSurfaceDestination.Favorites
                                                        librarySurfaceState.selectedStoredPlaylistIdState.value = null
                                                    } else {
                                                        librarySurfaceState.destinationState.value = PlaylistsSurfaceDestination.StoredPlaylist
                                                        librarySurfaceState.selectedStoredPlaylistIdState.value = playlistId
                                                    }
                                                    currentView = MainView.Playlists
                                                } else {
                                                    openBrowserRequest(
                                                        resolveFolderOpenRequest(
                                                            entry = entry.asRecentPathEntry(),
                                                            networkNodes = networkNodes
                                                        )
                                                    )
                                                }
                                            },
                                            onPlayPinnedFile = { entry ->
                                                playSource(entry.path, entry.title, entry.artist)
                                            },
                                            onOpenRecentFolder = { entry ->
                                                openBrowserRequest(
                                                    resolveFolderOpenRequest(
                                                        entry = entry,
                                                        networkNodes = networkNodes
                                                    )
                                                )
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
                                                            locationId = entry.locationId,
                                                            title = entry.title,
                                                            artist = entry.artist,
                                                            sourceNodeId = entry.sourceNodeId,
                                                            artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey
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
                                                            locationId = entry.locationId,
                                                            title = entry.title,
                                                            artist = entry.artist,
                                                            decoderName = entry.decoderName,
                                                            sourceNodeId = entry.sourceNodeId,
                                                            artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey
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
                                                        openResolvedBrowserTarget(
                                                            target = resolveBrowserParentForRecentFolder(
                                                                entry = entry.asRecentPathEntry(),
                                                                networkNodes = networkNodes
                                                            ),
                                                            failureMessage = "Unable to open folder in browser"
                                                        )
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
                                                        openResolvedBrowserTarget(
                                                            target = resolveBrowserFolderForRecentSource(
                                                                entry = entry.asRecentPathEntry(),
                                                                networkNodes = networkNodes
                                                            ),
                                                            failureMessage = "This source cannot be opened in file browser"
                                                        )
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
                                                        openResolvedBrowserTarget(
                                                            target = resolveBrowserParentForRecentFolder(
                                                                entry = entry,
                                                                networkNodes = networkNodes
                                                            ),
                                                            failureMessage = "Unable to open folder in browser"
                                                        )
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
                                                        openResolvedBrowserTarget(
                                                            target = resolveBrowserFolderForRecentSource(
                                                                entry = entry,
                                                                networkNodes = networkNodes
                                                            ),
                                                            failureMessage = "This source cannot be opened in file browser"
                                                        )
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
                                                enterSettings(SettingsRoute.Root)
                                            },
                                            onOpenUrlOrPath = { showUrlOrPathDialog = true }
                                        )
                                    }

                                    MainView.Browser -> {
                                        val prefs = LocalAppPreferences.current
                                        val sortArchivesBeforeFiles = remember(prefToken, prefs) {
                                            prefs.getBoolean(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, false)
                                        }
                                        val browserNameSortMode = remember(prefToken, prefs) {
                                            BrowserNameSortMode.fromStorage(
                                                prefs.getString(
                                                    AppPreferenceKeys.BROWSER_NAME_SORT_MODE,
                                                    AppDefaults.Browser.nameSortMode.storageValue
                                                )
                                            )
                                        }
                                        val showParentDirectoryEntry = remember(prefToken, prefs) {
                                            prefs.getBoolean(
                                                AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY,
                                                AppDefaults.Browser.showParentDirectoryEntry
                                            )
                                        }
                                        val showFileIconChipBackground = remember(prefToken, prefs) {
                                            prefs.getBoolean(
                                                AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND,
                                                AppDefaults.Browser.showFileIconChipBackground
                                            )
                                        }
                                        val repository = remember(prefs, sortArchivesBeforeFiles, browserNameSortMode) {
                                            FileRepository(
                                                supportedExtensions = runCatching {
                                                    NativeBridge.getSupportedExtensions().toSet()
                                                }.getOrElse { emptySet() },
                                                prefs = prefs,
                                                sortArchivesBeforeFiles = sortArchivesBeforeFiles,
                                                nameSortMode = browserNameSortMode,
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
                                                    onOpenRemoteSourceAsCached = { source -> playRemoteSourceAsCached(source) },
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
                                                    onOpenRemoteSourceAsCached = { source -> playRemoteSourceAsCached(source) },
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
                                                showParentDirectoryEntry = showParentDirectoryEntry,
                                                showFileIconChipBackground = showFileIconChipBackground,
                                                decoderExtensionArtworkHints = decoderExtensionArtworkHints,
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
                                            libraryAlbumDetail = libraryAlbumDetail,
                                            libraryArtistAlbums = libraryArtistAlbums,
                                            selectedArtistName = selectedLibraryArtistName,
                                            onOpenLibraryAlbum = { albumName, _ ->
                                                selectedLibraryAlbumName = albumName
                                            },
                                            onOpenLibraryArtist = { artistName ->
                                                selectedLibraryArtistName = artistName
                                            },
                                            onPlayLibraryTracks = { tracks, startIndex, title ->
                                                val queue = StoredPlaylist(
                                                    title = title.ifBlank { "Library" },
                                                    entries = tracks.map { it.toPlaylistTrackEntry() }
                                                )
                                                queue.entries.getOrNull(startIndex)?.let { entry ->
                                                    openQueueEntry(queue, entry, false)
                                                }
                                            },
                                            onShuffleLibraryTracks = { tracks, title ->
                                                val queue = StoredPlaylist(
                                                    title = title.ifBlank { "Library" },
                                                    entries = tracks.map { it.toPlaylistTrackEntry() }.shuffled()
                                                )
                                                queue.entries.firstOrNull()?.let { entry ->
                                                    openQueueEntry(queue, entry, true)
                                                }
                                            },
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
                                                enterSettings(SettingsRoute.Library)
                                            },
                                            activePlaylist = activePlaylist,
                                            activePlaylistEntryId = activePlaylistEntryId,
                                            currentPlaybackSourceId = session.currentSourceId,
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
                                                playlist.entries.firstOrNull()?.let { entry ->
                                                    openQueueEntry(playlist, entry, false)
                                                }
                                            },
                                            onShuffleStoredPlaylist = { playlist ->
                                                val shuffledPlaylist = playlist.copy(entries = playlist.entries.shuffled())
                                                shuffledPlaylist.entries.firstOrNull()?.let { entry ->
                                                    openQueueEntry(shuffledPlaylist, entry, true)
                                                }
                                            },
                                            onOpenStoredPlaylistEntry = { entry, playlist ->
                                                openQueueEntry(playlist, entry, false)
                                            },
                                            onPlayFavoritePlaylist = {
                                                val queue = favoritesAsStoredPlaylist(playlistLibraryState.favorites)
                                                queue.entries.firstOrNull()?.let { entry ->
                                                    openQueueEntry(queue, entry, false)
                                                }
                                            },
                                            onShuffleFavoritePlaylist = {
                                                val queue = favoritesAsStoredPlaylist(
                                                    playlistLibraryState.favorites.shuffled()
                                                )
                                                queue.entries.firstOrNull()?.let { entry ->
                                                    openQueueEntry(queue, entry, true)
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
                                                openQueueEntry(playlist, entry, false)
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
                                                val localFile = resolvePlaylistEntryLocalFile(entry.source)
                                                    ?.takeIf { it.exists() && it.isFile }
                                                if (localFile == null) {
                                                    toastHandler.showToast("Location is only available for local files")
                                                } else {
                                                    val parentDirectory = localFile.parentFile
                                                    if (parentDirectory == null || !parentDirectory.exists()) {
                                                        toastHandler.showToast("Unable to resolve file location")
                                                    } else {
                                                        openLocalBrowser(parentDirectory)
                                                    }
                                                }
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
                                                    activePlaylistShuffleActive = false
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
                                            selectedPluginName = selectedPluginName,
                                            onSelectPluginName = { selectedPluginName = it },
                                            openSettingsRoute = { settingsNavigationCoordinator.openSettingsRoute(it, false) },
                                            popSettingsRoute = settingsNavigationCoordinator.popSettingsRoute,
                                            exitSettingsToReturnView = settingsNavigationCoordinator.exitSettingsToReturnView,
                                            onOpenAudioEffects = { openAudioEffectsDialog() },
                                            protectedCachePaths = settingsProtectedCachePaths,
                                            onSelectVisualizationMode = visualizationUiState.onSelectMode,
                                            onSetEnabledModes = visualizationUiState.onSetEnabledModes,
                                            onClearRecentsUiState = {
                                                recentFiles.clear()
                                                recentFolders.clear()
                                            },
                                            onClearNetworkNodesUiState = { networkNodes.clear() },
                                            onClearAllUiState = {
                                                // Settings-derived UI only; domain-backed lists keep their data.
                                                favoritesSortMode = PlaylistEntrySortMode.fromStorage(null)
                                                currentDirectory = File(System.getProperty("user.home") ?: "/")
                                            },
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

                        // Drag-up expand preview mirrors Android's ExpandedPlayerOverlayHost:
                        // the player is already visible during the drag, parked below the
                        // display edge, so it follows the finger instead of flinging in after release.
                        val playerDragPreviewVisible = playerDragPreviewVisible(
                            isPlayerSurfaceVisible,
                            isPlayerExpanded,
                            miniExpandPreviewProgress
                        )
                        val playerExpandedOverlayVisible = isPlayerSurfaceVisible && isPlayerExpanded
                        val playerOverlayVisible = playerDragPreviewVisible || playerExpandedOverlayVisible
                        val playerPreviewProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                        val playerPreviewMode = !playerExpandedOverlayVisible && playerPreviewProgress > 0f
                        val miniHiddenForExpand = miniPlayerHiddenForExpand(
                            dragExpandCommitInProgress,
                            expandFromMiniDrag,
                            isPlayerExpanded
                        )
                        var playerOverlayWasExpanded by remember { mutableStateOf(false) }
                        LaunchedEffect(playerExpandedOverlayVisible) {
                            if (playerExpandedOverlayVisible) playerOverlayWasExpanded = true
                        }
                        LaunchedEffect(playerOverlayVisible) {
                            if (!playerOverlayVisible && playerOverlayWasExpanded) {
                                delay(300)
                                playerOverlayWasExpanded = false
                            }
                        }
                        // Docked Mini Player
                        AnimatedVisibility(
                            visible = isPlayerSurfaceVisible && !isPlayerExpanded && !dragExpandCommitInProgress,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    horizontal = MiniPlayerDockHorizontalPadding,
                                    vertical = MiniPlayerDockVerticalPadding
                                )
                                .padding(bottom = DesktopNavigationBarInset),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = if (dragExpandCommitInProgress || expandFromMiniDrag) {
                                fadeOut(animationSpec = tween(durationMillis = 1))
                            } else {
                                slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            }
                        ) {
                            MiniPlayerBar(
                                modifier = Modifier
                                    .graphicsLayer {
                                        val dragProgress = miniExpandPreviewProgress.coerceIn(0f, 1f)
                                        alpha = if (miniHiddenForExpand) 0f else 1f - dragProgress
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
                                    expandFromMiniDrag = miniExpandPreviewProgress > 0f
                                    miniExpandPreviewProgress = 0f
                                    isPlayerExpanded = true
                                },
                                onExpandDragProgress = { miniExpandPreviewProgress = it },
                                onExpandDragCommit = {
                                    if (dragExpandCommitInProgress) {
                                        return@MiniPlayerBar
                                    }
                                    dragExpandCommitInProgress = true
                                    expandFromMiniDrag = true
                                    miniExpandPreviewProgress = 0f
                                    isPlayerExpanded = true
                                },
                                onPreviousTrack = {
                                    playQueuePreviousTrack(
                                        playlistWrapOverride = false,
                                        browserWrapOverride = session.repeatMode != RepeatMode.None,
                                        notifyWrap = true
                                    )
                                },
                                onForcePreviousTrack = {
                                    playQueueAdjacentTrack(
                                        -1,
                                        stopAtBoundary = false,
                                        playlistWrapOverride = false,
                                        browserWrapOverride = session.repeatMode != RepeatMode.None,
                                        notifyWrap = true
                                    )
                                },
                                onNextTrack = {
                                    playQueueAdjacentTrack(
                                        1,
                                        stopAtBoundary = true,
                                        playlistWrapOverride = false,
                                        browserWrapOverride = session.repeatMode != RepeatMode.None,
                                        notifyWrap = true
                                    )
                                },
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
                            visible = playerOverlayVisible,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (playerPreviewMode) {
                                        translationY = playerPreviewOffsetPx(
                                            playerPreviewProgress,
                                            playerPreviewScreenHeightPx
                                        )
                                        alpha = playerPreviewAlpha(playerPreviewProgress)
                                    }
                                }
                                .then(
                                    if (playerExpandedOverlayVisible) {
                                        Modifier.pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    awaitPointerEvent(PointerEventPass.Main).changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            enter = if (expandFromMiniDrag || playerDragPreviewVisible) {
                                EnterTransition.None
                            } else {
                                slideInVertically(
                                    initialOffsetY = { it },
                                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                ) + fadeIn(animationSpec = tween(durationMillis = 240))
                            },
                            exit = if (playerOverlayWasExpanded) {
                                slideOutVertically(
                                    targetOffsetY = { it },
                                    animationSpec = tween(durationMillis = 280, easing = LinearOutSlowInEasing)
                                ) + fadeOut(animationSpec = tween(durationMillis = 200))
                            } else {
                                fadeOut(animationSpec = tween(durationMillis = 1))
                            }
                        ) {
                            CompositionLocalProvider(
                                LocalPlayerFocusIndicatorsEnabled provides true,
                                LocalPlayerOverlayVisibility provides {
                                    if (playerPreviewMode) playerPreviewProgress else 1f
                                },
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
                                    playbackSourceLabel = resolvePlaybackSourceLabel(
                                        session.currentFile,
                                        session.currentSourceId,
                                        networkNodes
                                    ),
                                    pathOrUrl = formatSourceIdForDisplay(session.currentSourceId, networkNodes),
                                    playbackSourceId = session.currentSourceId,
                                    artwork = session.artwork,
                                    noArtworkIcon = placeholderArtworkIconForFile(session.currentFile, session.decoderName),
                                    requestInitialFocus = true,
                                    repeatMode = session.repeatMode,
                                    canCycleRepeatMode = supportsLiveRepeatMode(session.playbackCapabilitiesFlags),
                                    canSeek = session.canSeek,
                                    hasReliableDuration = session.hasReliableDuration,
                                    playbackCapabilitiesFlags = session.playbackCapabilitiesFlags,
                                    onSeek = { seconds -> session.seekTo(seconds) },
                                    onPreviousTrack = {
                                        playQueuePreviousTrack(
                                            playlistWrapOverride = false,
                                            browserWrapOverride = session.repeatMode != RepeatMode.None,
                                            notifyWrap = true
                                        )
                                    },
                                    onForcePreviousTrack = {
                                        playQueueAdjacentTrack(
                                            -1,
                                            stopAtBoundary = false,
                                            playlistWrapOverride = false,
                                            browserWrapOverride = session.repeatMode != RepeatMode.None,
                                            notifyWrap = true
                                        )
                                    },
                                    onNextTrack = {
                                        playQueueAdjacentTrack(
                                            1,
                                            stopAtBoundary = true,
                                            playlistWrapOverride = false,
                                            browserWrapOverride = session.repeatMode != RepeatMode.None,
                                            notifyWrap = true
                                        )
                                    },
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
                                        val path = session.currentSourceId
                                        if (path != null) {
                                            removeSourceFromPlaylist(
                                                playlistId,
                                                path,
                                                session.subtuneIndex.takeIf { session.subtuneCount > 1 }
                                            )
                                        }
                                    },
                                    canOpenPlaylistSelector = activePlaylist?.entries?.isNotEmpty() == true,
                                    onOpenPlaylistSelector = { showPlaylistSelectorDialog = true },
                                    currentSubtuneIndex = session.subtuneIndex,
                                    subtuneCount = session.subtuneCount,
                                    titleCurrentSubtuneIndex = session.subtuneIndex,
                                    titleSubtuneCount = session.subtuneCount,
                                    subtuneTitleClickable = session.subtuneCount > 1,
                                    onCycleRepeatMode = {
                                        session.cycleRepeatMode()?.let { next ->
                                            preferredRepeatMode = next
                                            toastHandler.showToast(next.label)
                                        }
                                    },
                                    canOpenCoreSettings = canOpenCoreSettingsForDecoder(
                                        session.decoderName ?: session.lastUsedCoreName
                                    ),
                                    onOpenCoreSettings = settingsNavigationCoordinator.openCurrentCoreSettings,
                                    visualizationMode = visualizationUiState.mode,
                                    availableVisualizationModes = visualizationUiState.availableModes,
                                    onCycleVisualizationMode = visualizationUiState.onCycleMode,
                                    onSelectVisualizationMode = visualizationUiState.onSelectMode,
                                    onOpenVisualizationSettings = {
                                        settingsNavigationCoordinator.openVisualizationSettings()
                                    },
                                    onOpenSelectedVisualizationSettings = {
                                        settingsNavigationCoordinator.openSelectedVisualizationSettings(
                                            visualizationUiState.mode
                                        )
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
                                    visualizationPerformanceMode = playerVisualizationPerformanceMode,
                                    visualizationShowDebugInfo = playerVisualizationShowDebugInfo,
                                    artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                    canvasTapToSeekSeconds = playerCanvasTapToSeekSeconds,
                                    showAudioOutputRouteChip = playerShowAudioOutputRouteChip,
                                    filenameDisplayMode = playerFilenameDisplayMode,
                                    filenameOnlyWhenTitleMissing = playerFilenameOnlyWhenTitleMissing,
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
                            playbackSourceLabel = resolvePlaybackSourceLabel(
                                session.currentFile,
                                session.currentSourceId,
                                networkNodes
                            ),
                            pathOrUrl = formatSourceIdForDisplay(session.currentSourceId, networkNodes),
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
                            shuffleActive = activePlaylistShuffleActive,
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
                                masterMuted = false
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
        ToastOverlay(items = toasts, onDismiss = {})
    }
}

// CORE-OPTION PUSH table. Normalization mirrors Android AppNavigationCoreEffects;
// restart policy resolves from the engine (1 = RequiresPlaybackRestart).
private fun pushDesktopCoreRate(coreName: String, rateHz: Int) {
    runCatching { NativeBridge.setCoreOutputSampleRate(coreName, rateHz) }
}

private fun applyDesktopCoreOption(
    coreName: String,
    optionName: String,
    optionValue: String,
    policy: CoreOptionApplyPolicy,
    optionLabel: String?,
    toast: ToastHandler,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
    activeDecoderName: String?
) {
    runCatching { NativeBridge.setCoreOption(coreName, optionName, optionValue) }
    val resolved = runCatching {
        if (NativeBridge.getCoreOptionApplyPolicy(coreName, optionName) == 1) {
            CoreOptionApplyPolicy.RequiresPlaybackRestart
        } else {
            CoreOptionApplyPolicy.Live
        }
    }.getOrDefault(policy)
    if (resolved != CoreOptionApplyPolicy.RequiresPlaybackRestart) return
    if (!isPlaying || !hasCurrentTrack) return
    if (!activeDecoderName.equals(coreName, ignoreCase = true)) return
    runCatching { toast.showToast("${optionLabel ?: "This option"} will apply after restarting playback") }
}

private fun pushDesktopCorePrefToNative(
    prefs: AppPreferences,
    key: String,
    toast: ToastHandler,
    isPlaying: Boolean,
    hasCurrentTrack: Boolean,
    activeDecoderName: String?
) {
    fun opt(
        coreName: String,
        optionName: String,
        optionValue: String,
        policy: CoreOptionApplyPolicy,
        optionLabel: String?
    ) = applyDesktopCoreOption(coreName, optionName, optionValue, policy, optionLabel, toast, isPlaying, hasCurrentTrack, activeDecoderName)
    // 0 = auto/native; coerce window matches Android (8000..192000).
    fun clampedRate(raw: Int): Int = if (raw <= 0) 0 else raw.coerceIn(8000, 192000)
    fun plainRate(raw: Int): Int = if (raw <= 0) 0 else raw
    fun percent2(raw: Int): String = String.format(Locale.US, "%.2f", raw / 100.0)
    when (key) {
        CorePreferenceKeys.CORE_RATE_FFMPEG -> pushDesktopCoreRate(DecoderNames.FFMPEG, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK ->
            opt(DecoderNames.FFMPEG, FfmpegOptionKeys.GAPLESS_REPEAT_TRACK, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Gapless repeat track")
        CorePreferenceKeys.CORE_RATE_OPENMPT -> pushDesktopCoreRate(DecoderNames.LIB_OPEN_MPT, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_VGMPLAY -> pushDesktopCoreRate(DecoderNames.VGM_PLAY, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_GME -> pushDesktopCoreRate(DecoderNames.GAME_MUSIC_EMU, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_CRSID -> pushDesktopCoreRate(DecoderNames.C_RSID, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_SIDPLAYFP -> pushDesktopCoreRate(DecoderNames.LIB_SID_PLAY_FP, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_LAZYUSF2 -> pushDesktopCoreRate(DecoderNames.LAZY_USF2, plainRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_ADPLUG -> pushDesktopCoreRate(DecoderNames.AD_PLUG, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_XMP -> pushDesktopCoreRate(DecoderNames.LIBXMP, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_UFMOD -> pushDesktopCoreRate(DecoderNames.UFMOD, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_AYFLY -> pushDesktopCoreRate(DecoderNames.AYFLY, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_HIVELYTRACKER -> pushDesktopCoreRate(DecoderNames.HIVELY_TRACKER, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_KLYSTRACK -> pushDesktopCoreRate(DecoderNames.KLYSTRACK, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_FURNACE -> pushDesktopCoreRate(DecoderNames.FURNACE, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_UADE -> pushDesktopCoreRate(DecoderNames.UADE, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.CORE_RATE_SC68 -> pushDesktopCoreRate(DecoderNames.SC68, clampedRate(prefs.getInt(key, 0)))
        CorePreferenceKeys.XMP_INTERPOLATION -> {
            val v = prefs.getInt(key, 0).coerceIn(0, 2)
            opt(DecoderNames.LIBXMP, XmpOptionKeys.INTERPOLATION, XmpConfig.interpolationOptionValue(v), CoreOptionApplyPolicy.Live, "Interpolation")
        }
        CorePreferenceKeys.XMP_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.STEREO_SEPARATION, prefs.getInt(key, 100).coerceIn(-100, 100).toString(), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.XMP_AMIGA_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.AMIGA_STEREO_SEPARATION, prefs.getInt(key, 100).coerceIn(-100, 100).toString(), CoreOptionApplyPolicy.Live, "Amiga stereo separation")
        CorePreferenceKeys.XMP_AMIGA_MODEL ->
            opt(DecoderNames.LIBXMP, XmpOptionKeys.AMIGA_MODEL, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.Live, "Amiga mixing")
        CorePreferenceKeys.AYFLY_OVERSAMPLE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.OVERSAMPLE, prefs.getInt(key, 0).coerceIn(1, 8).toString(), CoreOptionApplyPolicy.Live, "Oversampling")
        CorePreferenceKeys.AYFLY_CHIP_TYPE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.CHIP_TYPE, prefs.getInt(key, 0).coerceIn(-1, 1).toString(), CoreOptionApplyPolicy.Live, "Chip model")
        CorePreferenceKeys.AYFLY_MIX_TYPE ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.MIX_TYPE, prefs.getInt(key, 0).coerceIn(-1, 5).toString(), CoreOptionApplyPolicy.Live, "Stereo mix order")
        CorePreferenceKeys.AYFLY_INT_FREQ ->
            opt(DecoderNames.AYFLY, AyflyOptionKeys.INT_FREQ, prefs.getInt(key, 0).coerceIn(0, 1000).toString(), CoreOptionApplyPolicy.Live, "Interrupt frequency")
        CorePreferenceKeys.ADPLUG_OPL_ENGINE ->
            opt(DecoderNames.AD_PLUG, AdPlugOptionKeys.OPL_ENGINE, prefs.getInt(key, 0).coerceIn(0, 3).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Adlib core")
        CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO ->
            opt(DecoderNames.LAZY_USF2, LazyUsf2OptionKeys.USE_HLE_AUDIO, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Use HLE audio")
        CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY ->
            opt(DecoderNames.VIO2_SF, Vio2sfOptionKeys.INTERPOLATION_QUALITY, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.Live, "Interpolation quality")
        CorePreferenceKeys.SC68_ASID ->
            opt(DecoderNames.SC68, Sc68OptionKeys.ASID, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "aSID filter")
        CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS ->
            opt(DecoderNames.SC68, Sc68OptionKeys.DEFAULT_TIME_SECONDS, prefs.getInt(key, 0).coerceIn(0, 24 * 60 * 60 - 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Default track time")
        CorePreferenceKeys.SC68_YM_ENGINE ->
            opt(DecoderNames.SC68, Sc68OptionKeys.YM_ENGINE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM engine")
        CorePreferenceKeys.SC68_YM_VOLMODEL ->
            opt(DecoderNames.SC68, Sc68OptionKeys.YM_VOLMODEL, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM volume model")
        CorePreferenceKeys.SC68_AMIGA_FILTER ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_FILTER, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga filter")
        CorePreferenceKeys.SC68_AMIGA_BLEND ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_BLEND, prefs.getInt(key, 0).coerceIn(0, 255).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga blend")
        CorePreferenceKeys.SC68_AMIGA_CLOCK ->
            opt(DecoderNames.SC68, Sc68OptionKeys.AMIGA_CLOCK, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Amiga clock")
        CorePreferenceKeys.UADE_FILTER_ENABLED ->
            opt(DecoderNames.UADE, UadeOptionKeys.FILTER_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Paula filter")
        CorePreferenceKeys.UADE_NTSC_MODE ->
            opt(DecoderNames.UADE, UadeOptionKeys.NTSC_MODE, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "NTSC mode")
        CorePreferenceKeys.UADE_PANNING_MODE ->
            opt(DecoderNames.UADE, UadeOptionKeys.PANNING_MODE, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Panning")
        CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE ->
            opt(DecoderNames.HIVELY_TRACKER, HivelyTrackerOptionKeys.PANNING_MODE, prefs.getInt(key, 0).coerceIn(-1, 4).toString(), CoreOptionApplyPolicy.Live, "Stereo panning")
        CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT -> {
            val raw = prefs.getInt(key, 100)
            val v = if (raw < 0) -1 else raw.coerceIn(25, 300)
            opt(DecoderNames.HIVELY_TRACKER, HivelyTrackerOptionKeys.MIX_GAIN_PERCENT, v.toString(), CoreOptionApplyPolicy.Live, "Replay mix gain")
        }
        CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY ->
            opt(DecoderNames.KLYSTRACK, KlystrackOptionKeys.PLAYER_QUALITY, prefs.getInt(key, 0).coerceIn(0, 4).toString(), CoreOptionApplyPolicy.Live, "Replay quality")
        CorePreferenceKeys.FURNACE_YM2612_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.YM2612_CORE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "YM2612 core")
        CorePreferenceKeys.FURNACE_SN_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.SN_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SN76489 core")
        CorePreferenceKeys.FURNACE_NES_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.NES_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "NES core")
        CorePreferenceKeys.FURNACE_C64_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.C64_CORE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "C64 core")
        CorePreferenceKeys.FURNACE_GB_QUALITY ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.GB_QUALITY, prefs.getInt(key, 0).coerceIn(0, 5).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Game Boy quality")
        CorePreferenceKeys.FURNACE_DSID_QUALITY ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.DSID_QUALITY, prefs.getInt(key, 0).coerceIn(0, 5).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "dSID quality")
        CorePreferenceKeys.FURNACE_AY_CORE ->
            opt(DecoderNames.FURNACE, FurnaceOptionKeys.AY_CORE, prefs.getInt(key, 0).coerceIn(0, 1).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "AY core")
        CorePreferenceKeys.CRSID_CLOCK_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.CLOCK_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Timing standard")
        CorePreferenceKeys.CRSID_SID_MODEL_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.SID_MODEL_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SID model")
        CorePreferenceKeys.CRSID_QUALITY_MODE ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.QUALITY_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Emulation quality")
        CorePreferenceKeys.CRSID_FILTER_6581_PRESET ->
            opt(DecoderNames.C_RSID, CrsidOptionKeys.FILTER_6581_PRESET, prefs.getInt(key, 0).coerceIn(0, 3).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "6581 filter preset")
        CorePreferenceKeys.SIDPLAYFP_BACKEND -> {
            val v = prefs.getInt(key, 0).coerceIn(0, 2)
            val engine = when (v) { 1 -> "sidlite"; 2 -> "resid"; else -> "residfp" }
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.BACKEND, engine, CoreOptionApplyPolicy.RequiresPlaybackRestart, "Engine")
        }
        CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.CLOCK_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Timing standard")
        CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.SID_MODEL_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SID model")
        CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_6581_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Filter for MOS6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_8580_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "Filter for MOS8580")
        CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.DIGI_BOOST_8580, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Digi boost (8580)")
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_CURVE_6581, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter curve 6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_RANGE_6581, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter range 6581")
        CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580 ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.FILTER_CURVE_8580, percent2(prefs.getInt(key, 50).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Filter curve 8580")
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.RESIDFP_FAST_SAMPLING, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Fast sampling")
        CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH ->
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.RESIDFP_COMBINED_WAVEFORMS_STRENGTH, prefs.getInt(key, 50).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.Live, "Combined waveforms")
        CorePreferenceKeys.GME_TEMPO_PERCENT ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.TEMPO, percent2(prefs.getInt(key, 100).coerceIn(50, 200)), CoreOptionApplyPolicy.Live, "Tempo")
        CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.STEREO_SEPARATION, percent2(prefs.getInt(key, 100).coerceIn(0, 100)), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.GME_ECHO_ENABLED ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.ECHO_ENABLED, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "SPC echo")
        CorePreferenceKeys.GME_ACCURACY_ENABLED ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.ACCURACY_ENABLED, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.Live, "High accuracy emulation")
        CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.EQ_TREBLE_DB, prefs.getInt(key, 0).coerceIn(-50, 5).toString(), CoreOptionApplyPolicy.Live, "EQ treble")
        CorePreferenceKeys.GME_EQ_BASS_HZ ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.EQ_BASS_HZ, prefs.getInt(key, 0).coerceIn(1, 1000).toString(), CoreOptionApplyPolicy.Live, "EQ bass")
        CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_USE_BUILTIN_FADE, prefs.getBoolean(key, true).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SPC built-in fade")
        CorePreferenceKeys.GME_SPC_INTERPOLATION ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_INTERPOLATION, prefs.getInt(key, 0).coerceIn(-2, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "SPC interpolation")
        CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE ->
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.SPC_USE_NATIVE_SAMPLE_RATE, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Use native SPC sample rate")
        AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS -> {
            val v = prefs.getInt(key, 0).coerceIn(1, 86400).toString()
            opt(DecoderNames.GAME_MUSIC_EMU, GmeOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.LIB_SID_PLAY_FP, SidPlayFpOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.C_RSID, CrsidOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
            opt(DecoderNames.UADE, UadeOptionKeys.UNKNOWN_DURATION_SECONDS, v, CoreOptionApplyPolicy.Live, "Unknown track duration")
        }
        CorePreferenceKeys.VGMPLAY_LOOP_COUNT ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.LOOP_COUNT, prefs.getInt(key, 2).coerceIn(1, 99).toString(), CoreOptionApplyPolicy.Live, "Loop count")
        CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.ALLOW_NON_LOOPING_LOOP, prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Allow non-looping loop")
        CorePreferenceKeys.VGMPLAY_VSYNC_RATE -> {
            val raw = prefs.getInt(key, 60)
            val v = if (raw == 50 || raw == 60) raw else 0
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.VSYNC_RATE_HZ, v.toString(), CoreOptionApplyPolicy.Live, "VSync mode")
        }
        CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.RESAMPLE_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Resampling mode")
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_SAMPLE_MODE, prefs.getInt(key, 0).coerceIn(0, 2).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Chip sample mode")
        CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE ->
            opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_SAMPLE_RATE_HZ, prefs.getInt(key, 0).coerceIn(8000, 192000).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "Chip sample rate")
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_PERCENT ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.stereo_separation_percent", prefs.getInt(key, 100).toString(), CoreOptionApplyPolicy.Live, "Stereo separation")
        CorePreferenceKeys.OPENMPT_STEREO_SEPARATION_AMIGA_PERCENT ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.stereo_separation_amiga_percent", prefs.getInt(key, 100).toString(), CoreOptionApplyPolicy.Live, "Amiga stereo separation")
        CorePreferenceKeys.OPENMPT_INTERPOLATION_FILTER_LENGTH ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.interpolation_filter_length", prefs.getInt(key, 8).toString(), CoreOptionApplyPolicy.Live, "Interpolation filter")
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_MODE ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.amiga_resampler_mode", prefs.getInt(key, 0).toString(), CoreOptionApplyPolicy.Live, "Amiga resampler")
        CorePreferenceKeys.OPENMPT_AMIGA_RESAMPLER_APPLY_ALL_MODULES ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.amiga_resampler_apply_all_modules", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Apply Amiga resampler to all modules")
        CorePreferenceKeys.OPENMPT_VOLUME_RAMPING_STRENGTH ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.volume_ramping_strength", prefs.getInt(key, -1).toString(), CoreOptionApplyPolicy.Live, "Volume ramping strength")
        CorePreferenceKeys.OPENMPT_FT2_XM_VOLUME_RAMPING ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.ft2_xm_volume_ramping", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "FT2 5ms XM ramping")
        CorePreferenceKeys.OPENMPT_MASTER_GAIN_MILLIBEL ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.master_gain_millibel", prefs.getInt(key, 0).toString(), CoreOptionApplyPolicy.Live, "Master gain")
        CorePreferenceKeys.OPENMPT_SURROUND_ENABLED ->
            opt(DecoderNames.LIB_OPEN_MPT, "openmpt.surround_enabled", prefs.getBoolean(key, false).toString(), CoreOptionApplyPolicy.Live, "Enable surround sound")
        else -> {
            // VGMPlay per-chip emulator cores: pref vgmplay_chip_core_<chip>.
            val prefix = CorePreferenceKeys.vgmPlayChipCoreKey("")
            if (key.startsWith(prefix) && key.length > prefix.length) {
                val chipKey = key.removePrefix(prefix)
                val default = VgmPlayConfig.defaultChipCoreSelections()[chipKey] ?: 0
                opt(DecoderNames.VGM_PLAY, VgmPlayOptionKeys.CHIP_CORE_PREFIX + chipKey, prefs.getInt(key, default).toString(), CoreOptionApplyPolicy.RequiresPlaybackRestart, "$chipKey emulator core")
            }
        }
    }
}
