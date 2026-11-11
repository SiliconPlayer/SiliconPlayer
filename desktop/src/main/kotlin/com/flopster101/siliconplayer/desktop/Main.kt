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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import com.flopster101.siliconplayer.ui.theme.Radius
import com.flopster101.siliconplayer.DomainStoreDirs
import com.flopster101.siliconplayer.export.AudioExportPlaybackRequests
import com.flopster101.siliconplayer.HomeScreen
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.MiniPlayerBar
import com.flopster101.siliconplayer.loadPluginConfigurations
import com.flopster101.siliconplayer.miniPlayerHiddenForExpand
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
import com.flopster101.siliconplayer.PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.LocalAppConfigDir
import com.flopster101.siliconplayer.ManualSourceType
import com.flopster101.siliconplayer.MANUAL_INPUT_INVALID_MESSAGE
import com.flopster101.siliconplayer.resolveManualSourceInput
import com.flopster101.siliconplayer.RemotePlayableSourceIdsHolder
import com.flopster101.siliconplayer.RemoteLoadUiStateHolder
import com.flopster101.siliconplayer.loadSmbRecentFolderContext
import com.flopster101.siliconplayer.platform.rememberRemoteSourceExportSupport
import com.flopster101.siliconplayer.formatSourceIdForDisplay
import com.flopster101.siliconplayer.resolvePlaybackSourceLabel
import com.flopster101.siliconplayer.storagePresentationForPath
import com.flopster101.siliconplayer.resolveNetworkNodeHttpSpec
import com.flopster101.siliconplayer.resolveNetworkNodeSmbSpec
import com.flopster101.siliconplayer.readNetworkNodes
import com.flopster101.siliconplayer.writeNetworkNodes
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.mpris.MprisCommands
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
import com.flopster101.siliconplayer.ui.visualization.rememberVisualizationUiState
import com.flopster101.siliconplayer.buildImportedPlaylist
import com.flopster101.siliconplayer.ui.dialogs.AddToPlaylistChooserDialog
import com.flopster101.siliconplayer.ui.dialogs.BrowserPlaylistPromptDialogs
import com.flopster101.siliconplayer.ui.dialogs.rememberBrowserPlaylistPrompt
import com.flopster101.siliconplayer.ui.dialogs.AudioEffectsDialog
import com.flopster101.siliconplayer.ui.dialogs.PlaylistSelectorDialog
import com.flopster101.siliconplayer.ui.dialogs.PlayWithDialog
import com.flopster101.siliconplayer.ui.dialogs.TrackInfoDialog
import com.flopster101.siliconplayer.ui.dialogs.UrlOrPathDialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import com.flopster101.siliconplayer.buildUpdatedRecentPlayedTracks
import com.flopster101.siliconplayer.effectiveTrackMetadata
import com.flopster101.siliconplayer.matchPlaylistEntryByHint
import com.flopster101.siliconplayer.playlistEntryDurationOverride
import com.flopster101.siliconplayer.externalPlaylistRecentOverride
import com.flopster101.siliconplayer.isSupportedPlaylistFile
import com.flopster101.siliconplayer.parsePlaylistDocument
import com.flopster101.siliconplayer.isPlaylistSubtunePinned
import com.flopster101.siliconplayer.matchPlaylistEntryForPlayback
import com.flopster101.siliconplayer.pinnedTransportSubtuneCount
import com.flopster101.siliconplayer.playingPlaylistBrowserFile
import com.flopster101.siliconplayer.pinnedTransportSubtuneIndex
import com.flopster101.siliconplayer.resolveActivePlaylistMetadataEntry
import com.flopster101.siliconplayer.resolvePlaylistEntryLocalFile
import com.flopster101.siliconplayer.setStoredPlaylistPinned
import com.flopster101.siliconplayer.FAVORITES_PLAYLIST_ID
import com.flopster101.siliconplayer.favoritesAsStoredPlaylist
import com.flopster101.siliconplayer.LookaheadClipperMode
import com.flopster101.siliconplayer.MultiChannelOutputMode
import com.flopster101.siliconplayer.audio.EffectChain
import com.flopster101.siliconplayer.audio.EffectChainScopeTarget
import com.flopster101.siliconplayer.audio.activeDialogPreviewChainProvider
import com.flopster101.siliconplayer.audio.applyEffectChainToNative
import com.flopster101.siliconplayer.audio.queryEffectDescriptors
import com.flopster101.siliconplayer.audio.readBypassAllEffects
import com.flopster101.siliconplayer.audio.readCoreEffectChain
import com.flopster101.siliconplayer.audio.readEffectPresets
import com.flopster101.siliconplayer.audio.readGlobalEffectChain
import com.flopster101.siliconplayer.audio.readPlaylistEffectChain
import com.flopster101.siliconplayer.audio.readSongEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChain
import com.flopster101.siliconplayer.audio.resolveEffectiveEffectChainForPrefs
import com.flopster101.siliconplayer.audio.resolveInitialDialogScope
import com.flopster101.siliconplayer.audio.writeBypassAllEffects
import com.flopster101.siliconplayer.audio.writeCoreEffectChain
import com.flopster101.siliconplayer.audio.writeEffectPresets
import com.flopster101.siliconplayer.audio.writeGlobalEffectChain
import com.flopster101.siliconplayer.audio.writePlaylistEffectChain
import com.flopster101.siliconplayer.audio.writeSongEffectChain
import com.flopster101.siliconplayer.fileMatchesSupportedExtensions
import com.flopster101.siliconplayer.playlistContainsTrack
import com.flopster101.siliconplayer.samePath
import com.flopster101.siliconplayer.sameRecentTrack
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
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
private const val AwtVkPause = 19

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
            modifier = Modifier.padding(bottom = 16.dp).padding(start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            shape = Radius.Small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = item.message,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        offset = Offset(0f, 1f),
                                        blurRadius = 3f
                                    )
                                ),
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

// Load the engine before application{} pulls in Skiko: JNI_OnLoad warms the
// locale facet caches, and that warming only sticks if the engine (and its
// dlopen-time static init) wins the race. Class init runs before main().
private val initNativeEngineEarly = runCatching { NativeBridge.getSupportedExtensions() }

fun main(args: Array<String>) = application {
    DesktopPaths.install()
    DomainStoreDirs.configDir = DesktopPaths.configDir()
    val desktopPreferences = remember {
        DesktopPreferencesProvider().getPreferences(AppPreferenceKeys.PREFS_NAME)
    }
    applyDesktopCachePoliciesOnLaunch(
        prefs = desktopPreferences,
        cacheDir = DesktopPaths.cacheDir(),
        configDir = DesktopPaths.configDir()
    )
    val session = remember { DesktopPlaybackSession() }
    // Active queue context; hoisted early so every player header below,
    // including the window title, can prefer entry over decoder metadata.
    var activePlaylist by remember { mutableStateOf<StoredPlaylist?>(null) }
    var activePlaylistEntryId by remember { mutableStateOf<String?>(null) }
    // Queue entry whose load is still in flight; while set, openQueueEntry owns
    // the entry id and the re-match effect below stays out of the way.
    var pendingQueueEntryId by remember { mutableStateOf<String?>(null) }
    val textInputTracker = remember { com.flopster101.siliconplayer.TextInputTracker() }
    val restoredGeometry = remember { loadDesktopWindowGeometry(DesktopPaths.configDir()) }
    val startInFullscreen = remember(desktopPreferences) {
        desktopPreferences.getBoolean(AppPreferenceKeys.DESKTOP_START_IN_FULLSCREEN, false)
    }
    val windowState = rememberWindowState(
        width = (restoredGeometry?.widthDp ?: DesktopWindowDefaultWidthDp).dp,
        height = (restoredGeometry?.heightDp ?: DesktopWindowDefaultHeightDp).dp,
        position = if (restoredGeometry?.xDp != null && restoredGeometry.yDp != null) {
            WindowPosition.Absolute(restoredGeometry.xDp.dp, restoredGeometry.yDp.dp)
        } else {
            WindowPosition.PlatformDefault
        },
        placement = if (startInFullscreen) {
            WindowPlacement.Fullscreen
        } else if (restoredGeometry?.isMaximized == true) {
            WindowPlacement.Maximized
        } else {
            WindowPlacement.Floating
        }
    )
    val backDispatcher = remember { DesktopBackDispatcher() }
    var placementBeforeFullscreen by remember(startInFullscreen, restoredGeometry) {
        mutableStateOf(
            if (startInFullscreen) {
                if (restoredGeometry?.isMaximized == true) WindowPlacement.Maximized else WindowPlacement.Floating
            } else {
                null
            }
        )
    }
    var isApplicationFullscreen by remember(startInFullscreen) { mutableStateOf(startInFullscreen) }
    var restoreMaximizedAfterFullscreen by remember { mutableStateOf(false) }

    var currentView by remember { mutableStateOf(MainView.Home) }
    var browserRefreshToken by remember { mutableStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isPlayerSurfaceVisible by remember { mutableStateOf(false) }
    var miniExpandPreviewProgress by remember { mutableFloatStateOf(0f) }
    var miniExpandPreviewActive by remember { mutableStateOf(false) }
    var expandFromMiniDrag by remember { mutableStateOf(false) }
    var dragExpandCommitInProgress by remember { mutableStateOf(false) }
    var miniDismissOffsetPx by remember { mutableFloatStateOf(0f) }
    val miniDismissSettle = remember { Animatable(0f) }
    val miniDismissScope = rememberCoroutineScope()
    val desktopSmbFolderScope = rememberCoroutineScope()
    val playLoadScope = rememberCoroutineScope()
    // Guards async track loads: a superseded load never applies its state.
    val playRequestGeneration = remember { AtomicLong(0L) }
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
    // Synced from prefs in the prefs listener below; read at use time in the recents writers.
    var perSubtuneRecentRows by remember { mutableStateOf(false) }
    val pinnedEntries = remember { mutableStateListOf<HomePinnedEntry>() }
    // Compose short-circuits changed() on the same list instance, so in-place
    // mutations never reach remember() call chains; render from fresh snapshots.
    val recentFilesDisplay by remember { derivedStateOf { recentFiles.toList() } }
    val recentFoldersDisplay by remember { derivedStateOf { recentFolders.toList() } }
    val pinnedEntriesDisplay by remember { derivedStateOf { pinnedEntries.toList() } }
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
                    artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey,
                    subtuneIndex = entry.subtuneIndex
                )
            )
        }
    }

    // The playlist entry owns the metadata: entry fields first, live track text as fallback.
    fun playlistRowMetadata(entry: PlaylistTrackEntry?, playlistPath: String): Pair<String?, String?> {
        val meta = effectiveTrackMetadata(
            entry,
            session.title.ifBlank { File(playlistPath).name },
            session.artist,
            ""
        )
        return meta.title to meta.artist
    }

    fun registerLoadedFile(file: File) {
        val queueOverride = externalPlaylistRecentOverride(activePlaylist, activePlaylistEntryId)
        if (queueOverride != null) {
            val queueRowEntry =
                activePlaylist?.entries?.firstOrNull { it.id == activePlaylistEntryId }
            val (rowTitle, rowArtist) = playlistRowMetadata(queueRowEntry, queueOverride.playlistPath)
            // One atomic commit: a concurrent record must never see the cleared list.
            Snapshot.withMutableSnapshot {
                val updated = buildUpdatedRecentPlayedTracks(
                    current = recentFiles.toList(),
                    newPath = queueOverride.playlistPath,
                    locationId = null,
                    title = rowTitle,
                    artist = rowArtist,
                    decoderName = session.decoderName,
                    isPlaylist = true,
                    playlistSourceHint = queueOverride.sourceHint,
                    subtuneIndex = queueRowEntry?.subtuneIndex,
                    perSubtuneRows = perSubtuneRecentRows,
                    limit = recentFilesLimit
                ).filterNot { row ->
                    !row.isPlaylist &&
                        queueOverride.referencedSources.any { referenced -> samePath(row.path, referenced) }
                }
                recentFiles.clear()
                recentFiles.addAll(updated)
            }
            scheduleRecentArtworkBackfill?.invoke(queueOverride.playlistPath, session.currentRequestUrl)
        } else {
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
                artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(identity) ?: identity,
                subtuneIndex = session.subtuneIndex.takeIf { session.subtuneCount > 1 }
            )
            val hintedPlaylistRow = recentFiles.firstOrNull {
                it.isPlaylist && samePath(it.playlistSourceHint, identity)
            }
            if (hintedPlaylistRow != null) {
                // Played outside the queue, but a playlist entry claims this file:
                // bump the playlist row, never a raw file row.
                Snapshot.withMutableSnapshot {
                    recentFiles.removeAll { it.isPlaylist && samePath(it.path, hintedPlaylistRow.path) }
                    recentFiles.add(0, hintedPlaylistRow)
                    while (recentFiles.size > recentFilesLimit) {
                        recentFiles.removeLast()
                    }
                }
                scheduleRecentArtworkBackfill?.invoke(hintedPlaylistRow.path, session.currentRequestUrl)
            } else {
                val rowSubtune = entry.subtuneIndex
                recentFiles.removeAll {
                    if (perSubtuneRecentRows) sameRecentTrack(it.path, it.subtuneIndex, identity, rowSubtune)
                    else it.path == identity
                }
                recentFiles.add(0, entry)
                while (recentFiles.size > recentFilesLimit) {
                    recentFiles.removeLast()
                }
                scheduleRecentArtworkBackfill?.invoke(identity, session.currentRequestUrl)
            }
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
            while (recentFolders.size > recentFoldersLimit) {
                recentFolders.removeLast()
            }
        }
    }

    fun recordSubtuneSwitchInRecents() {
        val switchedFile = session.currentFile ?: return
        if (externalPlaylistRecentOverride(activePlaylist, activePlaylistEntryId) != null) {
            registerLoadedFile(switchedFile)
            return
        }
        val switchedIdentity = session.currentSourceId ?: switchedFile.absolutePath
        val subtune = session.subtuneIndex.takeIf { session.subtuneCount > 1 }
        val idx = recentFiles.indexOfFirst {
            if (perSubtuneRecentRows) sameRecentTrack(it.path, it.subtuneIndex, switchedIdentity, subtune)
            else it.path == switchedIdentity
        }
        if (idx >= 0) {
            recentFiles[idx] = recentFiles[idx].copy(subtuneIndex = subtune)
            recentFiles.add(0, recentFiles.removeAt(idx))
        } else {
            registerLoadedFile(switchedFile)
        }
    }

    fun playFile(file: File, subtuneIndex: Int? = null) {
        pendingQueueEntryId = null
        activePlaylist = null
        activePlaylistEntryId = null
        val generation = playRequestGeneration.incrementAndGet()
        playLoadScope.launch(Dispatchers.IO) {
            val loaded = session.loadFile(file, autoStart = autoPlayOnTrackSelect, initialSubtuneIndex = subtuneIndex)
            if (loaded && generation == playRequestGeneration.get()) {
                registerLoadedFile(file)
                if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
            }
        }
    }

    val exportedPlaybackFile = AudioExportPlaybackRequests.pendingFile
    LaunchedEffect(exportedPlaybackFile) {
        if (exportedPlaybackFile != null) {
            AudioExportPlaybackRequests.consume()
            playFile(exportedPlaybackFile)
        }
    }

    fun playSource(source: String, titleHint: String? = null, artistHint: String? = null, subtuneIndex: Int? = null) {
        pendingQueueEntryId = null
        activePlaylist = null
        activePlaylistEntryId = null
        val file = File(source)
        if (file.exists() && file.isFile) {
            playFile(file, subtuneIndex)
            return
        }
        val generation = playRequestGeneration.incrementAndGet()
        playLoadScope.launch(Dispatchers.IO) {
            if (!session.loadSource(source, titleHint, artistHint, autoStart = autoPlayOnTrackSelect, initialSubtuneIndex = subtuneIndex)) return@launch
            if (generation != playRequestGeneration.get()) return@launch
            if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
            val queueOverride = externalPlaylistRecentOverride(activePlaylist, activePlaylistEntryId)
            if (queueOverride != null) {
                val queueRowEntry =
                    activePlaylist?.entries?.firstOrNull { it.id == activePlaylistEntryId }
                val (rowTitle, rowArtist) = playlistRowMetadata(queueRowEntry, queueOverride.playlistPath)
                Snapshot.withMutableSnapshot {
                    val updated = buildUpdatedRecentPlayedTracks(
                        current = recentFiles.toList(),
                        newPath = queueOverride.playlistPath,
                        locationId = null,
                        title = rowTitle,
                        artist = rowArtist,
                        decoderName = session.decoderName,
                        isPlaylist = true,
                        playlistSourceHint = queueOverride.sourceHint,
                        subtuneIndex = queueRowEntry?.subtuneIndex,
                        perSubtuneRows = perSubtuneRecentRows,
                        limit = recentFilesLimit
                    ).filterNot { row ->
                        !row.isPlaylist &&
                            queueOverride.referencedSources.any { referenced -> samePath(row.path, referenced) }
                    }
                    recentFiles.clear()
                    recentFiles.addAll(updated)
                }
                scheduleRecentArtworkBackfill?.invoke(queueOverride.playlistPath, session.currentRequestUrl)
            } else {
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
                    artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(identity) ?: identity,
                    subtuneIndex = session.subtuneIndex.takeIf { session.subtuneCount > 1 }
                )
                val rowSubtune = entry.subtuneIndex
                recentFiles.removeAll {
                    if (perSubtuneRecentRows) sameRecentTrack(it.path, it.subtuneIndex, identity, rowSubtune)
                    else it.path == identity
                }
                recentFiles.add(0, entry)
                while (recentFiles.size > recentFilesLimit) {
                    recentFiles.removeLast()
                }
                scheduleRecentArtworkBackfill?.invoke(identity, session.currentRequestUrl)
            }
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
    LaunchedEffect(isPlayerExpanded, miniExpandPreviewActive) {
        if (!isPlayerExpanded && !miniExpandPreviewActive) {
            dragExpandCommitInProgress = false
        }
    }
    LaunchedEffect(isPlayerExpanded) {
        if (isPlayerExpanded && expandFromMiniDrag) {
            delay(350)
        }
        expandFromMiniDrag = false
    }

    // Effective queue-over-decoder metadata for every player header below.
    val playerQueueEntry = resolveActivePlaylistMetadataEntry(
        activePlaylist = activePlaylist,
        activePlaylistEntryId = activePlaylistEntryId,
        activeSourceId = session.currentSourceId ?: session.currentFile?.absolutePath,
        currentSubtuneIndex = session.subtuneIndex
    )
    // Pending tap target while its load is in flight, mirroring Android's
    // pendingPlaylistEntry fallback.
    val playerPendingEntry =
        pendingQueueEntryId?.let { pendingId -> activePlaylist?.entries?.firstOrNull { it.id == pendingId } }
    val playerActiveEntry = playerQueueEntry ?: playerPendingEntry
    val playerMetadata = effectiveTrackMetadata(
        playerActiveEntry,
        fallbackTitle = session.title,
        fallbackArtist = session.artist,
        fallbackAlbum = session.album
    )
    // Pinned while a queue entry claims the subtune: track buttons advance
    // the queue, never the raw subtune list (Android parity).
    // The M3U entry owns the length: its duration replaces the decoder fallback
    // in every player header below and ends the track in the session ticker.
    val queueDurationOverrideSeconds = playlistEntryDurationOverride(playerActiveEntry)
    val effectiveDurationSeconds = queueDurationOverrideSeconds ?: session.durationSeconds
    val effectiveHasReliableDuration =
        session.hasReliableDuration || queueDurationOverrideSeconds != null
    LaunchedEffect(queueDurationOverrideSeconds) {
        session.playlistDurationOverrideSeconds = queueDurationOverrideSeconds
    }
    val queueSubtunePinned = isPlaylistSubtunePinned(playerActiveEntry)
    val transportSubtuneCount = pinnedTransportSubtuneCount(session.subtuneCount, queueSubtunePinned)
    val transportCurrentSubtuneIndex =
        pinnedTransportSubtuneIndex(session.subtuneIndex, queueSubtunePinned)
    val windowTitle = if (playerMetadata.title.isNotBlank()) {
        if (playerMetadata.artist.isNotBlank()) {
            "${playerMetadata.artist} - ${playerMetadata.title} - SiliconPlayer"
        } else {
            "${playerMetadata.title} - SiliconPlayer"
        }
    } else {
        "SiliconPlayer Desktop"
    }

    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var dismissAudioEffectsDialogHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var toggleMasterMuteHandler by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var desktopFocusManager by remember { mutableStateOf<FocusManager?>(null) }
    var showSubtuneSelectorDialog by remember { mutableStateOf(false) }
    var showPlaylistSelectorDialog by remember { mutableStateOf(false) }
    var selectorImportEntries by remember { mutableStateOf<List<PlaylistTrackEntry>?>(null) }
    var selectorImportTitle by remember { mutableStateOf<String?>(null) }
    var selectorImportDialogTitle by remember { mutableStateOf("Add to playlist") }
    var externalTrackInfoDialogRequestToken by remember { mutableIntStateOf(0) }
    // Tracks whether the capture-phase Space handler ate the down-stroke,
    // so the up-stroke is only eaten when down was.
    var windowSpaceDownConsumed by remember { mutableStateOf(false) }

    val toasts = remember { mutableStateListOf<ToastItem>() }
    var nextToastId by remember { mutableStateOf(0L) }
    fun showFullscreenExitHint() {
        toasts.add(ToastItem(nextToastId++, "Press F11 again to exit fullscreen"))
    }
    LaunchedEffect(startInFullscreen) {
        if (startInFullscreen) showFullscreenExitHint()
    }

    fun requestApplicationExit() {
        // A maximized window reports the maximized size, so only persist
        // the live size when floating; otherwise keep the stored one.
        val maximized = if (isApplicationFullscreen) {
            placementBeforeFullscreen == WindowPlacement.Maximized
        } else {
            windowState.placement == WindowPlacement.Maximized || restoreMaximizedAfterFullscreen
        }
        val previous = if (maximized || isApplicationFullscreen) loadDesktopWindowGeometry(DesktopPaths.configDir()) else null
        saveDesktopWindowGeometry(
            DesktopPaths.configDir(),
            DesktopWindowGeometry(
                widthDp = previous?.widthDp ?: windowState.size.width.value,
                heightDp = previous?.heightDp ?: windowState.size.height.value,
                xDp = previous?.xDp
                    ?: (windowState.position as? WindowPosition.Absolute)?.x?.value,
                yDp = previous?.yDp
                    ?: (windowState.position as? WindowPosition.Absolute)?.y?.value,
                isMaximized = maximized
            )
        )
        session.dispose()
        exitApplication()
    }

    fun toggleApplicationFullscreen() {
        if (isApplicationFullscreen) {
            val previousPlacement = placementBeforeFullscreen ?: WindowPlacement.Floating
            windowState.placement = WindowPlacement.Floating
            restoreMaximizedAfterFullscreen = previousPlacement == WindowPlacement.Maximized
            placementBeforeFullscreen = null
            isApplicationFullscreen = false
        } else {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
            isApplicationFullscreen = true
            showFullscreenExitHint()
        }
    }

    Window(
        onCloseRequest = { requestApplicationExit() },
        state = windowState,
        title = windowTitle,
        icon = painterResource("app_icon.webp"),
        onPreviewKeyEvent = { keyEvent ->
            // Capture-phase Space: always play/pause when expanded, never
            // control activation. The player panel only covers part of the
            // layout (the header pill lives outside it), so this must sit
            // above every subtree. Only Enter activates focused controls.
            if (isPlayerExpanded && !textInputTracker.hasActiveInput && keyEvent.key == Key.Spacebar) {
                if (keyEvent.type == KeyEventType.KeyDown) {
                    windowSpaceDownConsumed = true
                    session.togglePlayPause()
                    return@Window true
                }
                val consumed = windowSpaceDownConsumed
                windowSpaceDownConsumed = false
                return@Window consumed
            }
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
                if (textInputTracker.hasActiveInput) return@Window false
                if (!isPlayerExpanded) {
                    // Miniplayer never drives playback: arrows move focus
                    // TV-style, Space/Enter fall through to the focused control.
                    val focusDirection = when (keyEvent.key) {
                        Key.DirectionLeft -> FocusDirection.Left
                        Key.DirectionRight -> FocusDirection.Right
                        Key.DirectionUp -> FocusDirection.Up
                        Key.DirectionDown -> FocusDirection.Down
                        else -> null
                    }
                    if (focusDirection != null) {
                        return@Window desktopFocusManager?.moveFocus(focusDirection) == true
                    }
                    return@Window false
                }
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
        LaunchedEffect(restoreMaximizedAfterFullscreen) {
            if (restoreMaximizedAfterFullscreen) {
                withFrameNanos {}
                windowState.placement = WindowPlacement.Maximized
                restoreMaximizedAfterFullscreen = false
            }
        }
        DisposableEffect(window) {
            val dispatcher = KeyEventDispatcher { event ->
                if (
                    event.id == AwtKeyEvent.KEY_PRESSED &&
                    event.keyCode == AwtKeyEvent.VK_F11 &&
                    event.component?.let { SwingUtilities.getWindowAncestor(it) } == window
                ) {
                    toggleApplicationFullscreen()
                    true
                } else {
                    false
                }
            }
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
            onDispose {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
            }
        }
        ProvideDesktopPlatformAdapters(
            windowWidthDp = windowState.size.width.value.toInt(),
            windowHeightDp = windowState.size.height.value.toInt(),
            backDispatcher = backDispatcher,
            stopPlaybackForRefresh = { session.stop() },
            openAudioSettings = { enterSettings(SettingsRoute.GeneralAudio) },
            toastHandler = ToastHandler { msg -> toasts.add(ToastItem(nextToastId++, msg)) },
            textInputTracker = textInputTracker
        ) {
            val prefs = LocalAppPreferences.current
            val configDir = LocalAppConfigDir.current
            var prefToken by remember { mutableIntStateOf(0) }
            DisposableEffect(prefs) {
                val listener = AppPreferences.OnChangeListener { _, _ ->
                    prefToken++
                    perSubtuneRecentRows = prefs.getBoolean(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, false)
                }
                perSubtuneRecentRows = prefs.getBoolean(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, false)
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
            var playerVisualizationShowDebugInfo by remember {
                mutableStateOf(AppDefaults.Visualization.showDebugInfo)
            }
            LaunchedEffect(Unit) {
                if (prefs.contains(AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO)) {
                    prefs.edit().remove(AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO).apply()
                }
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
                                    // Snapshot state is thread-safe; no Main hop (none exists on desktop).
                                    // Committed atomically so a concurrent record never sees the gap.
                                    Snapshot.withMutableSnapshot {
                                        if (merged != recentFiles.toList()) {
                                            recentFiles.clear()
                                            recentFiles.addAll(merged)
                                        }
                                    }
                                }
                            } finally {
                                recentArtworkBackfillJobs.remove(playedSourceId)
                            }
                        }
                }
            }
            // CORE-OPTION PUSH: setters persist only; this observer fans core writes
            // to the engine and sweeps stored keys once so cold starts apply them.
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
                prefs.allKeys().forEach { key ->
                    runCatching {
                        pushDesktopCorePrefToNative(
                            prefs = prefs,
                            key = key,
                            toast = toastHandler,
                            isPlaying = session.isPlaying,
                            hasCurrentTrack = session.currentFile != null,
                            activeDecoderName = session.decoderName,
                            silent = true
                        )
                    }
                }
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
                    artworkThumbnailCacheKey = recentArtworkCacheKeyForSource(source) ?: source,
                    subtuneIndex = session.subtuneIndex.takeIf { session.subtuneCount > 1 }
                )
                val rowSubtune = entry.subtuneIndex
                recentFiles.removeAll {
                    if (perSubtuneRecentRows) sameRecentTrack(it.path, it.subtuneIndex, source, rowSubtune)
                    else it.path == source
                }
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
            var activePlaylistShuffleActive by remember { mutableStateOf(false) }
            // Queue entry open: playlist context + per-entry subtune, mirrors Android openPlaylistEntry.
            fun openQueueEntry(queue: StoredPlaylist, entry: PlaylistTrackEntry, shuffleActive: Boolean): Boolean {
                val file = resolvePlaylistEntryLocalFile(entry.source) ?: return false
                activePlaylist = queue
                activePlaylistEntryId = entry.id
                activePlaylistShuffleActive = shuffleActive
                pendingQueueEntryId = entry.id
                val generation = playRequestGeneration.incrementAndGet()
                playLoadScope.launch(Dispatchers.IO) {
                    try {
                        if (!session.loadFile(file, autoStart = autoPlayOnTrackSelect)) return@launch
                        if (generation != playRequestGeneration.get()) return@launch
                        val subtune = entry.subtuneIndex
                        if (subtune != null && subtune in 0 until session.subtuneCount) {
                            session.selectSubtune(subtune)
                        }
                        registerLoadedFile(file)
                        if (openPlayerOnTrackSelect) isPlayerSurfaceVisible = true
                    } finally {
                        if (pendingQueueEntryId == entry.id) pendingQueueEntryId = null
                    }
                }
                return true
            }
            // Playlist recent tap: reopen the playlist at the hinted entry.
            fun openRecentPlaylistEntry(entry: RecentPathEntry) {
                playLoadScope.launch(Dispatchers.IO) {
                    val file = resolvePlaylistEntryLocalFile(entry.path)
                        ?.takeIf { it.exists() && it.isFile && isSupportedPlaylistFile(it) }
                    if (file == null) {
                        toastHandler.showToast("Playlist is not available")
                        return@launch
                    }
                    val document = runCatching { parsePlaylistDocument(file, entry.path) }.getOrNull()
                    if (document == null) {
                        toastHandler.showToast("Could not open playlist")
                        return@launch
                    }
                    val queue = buildImportedPlaylist(document)
                    val target = matchPlaylistEntryByHint(
                        queue.entries,
                        entry.playlistSourceHint,
                        entry.subtuneIndex
                    ) ?: queue.entries.firstOrNull()
                    if (target == null) {
                        toastHandler.showToast("Playlist is empty")
                        return@launch
                    }
                    openQueueEntry(queue, target, false)
                }
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
            fun playAdjacentSiblingTrack(
                offset: Int,
                wrapOverride: Boolean?,
                notifyWrap: Boolean = false
            ): Boolean {
                val activeSourceId = session.currentSourceId ?: session.currentFile?.path
                val remoteQueue = RemotePlayableSourceIdsHolder.resolvedCurrentOrLastForSource(activeSourceId)
                if (remoteQueue.isNotEmpty()) {
                    val index = remoteQueue.indexOfFirst { samePath(it, activeSourceId) }
                    if (index >= 0) {
                        val shouldWrap = wrapOverride ?: playlistWrapNavigation
                        val targetIndex = resolveAdjacentIndex(index, offset, remoteQueue.size, shouldWrap) ?: return false
                        if (shouldWrap && targetIndex != index + offset && notifyWrap) {
                            toastHandler.showToast(if (offset < 0) "Wrapped to last track" else "Wrapped to first track")
                        }
                        playSource(remoteQueue[targetIndex])
                        return true
                    }
                }
                val current = session.currentFile ?: return false
                // Directory listing blocks; resolve the sibling off thread.
                val generation = playRequestGeneration.incrementAndGet()
                playLoadScope.launch(Dispatchers.IO) {
                    val siblings = listSiblingTracks(current)
                    if (siblings.isEmpty()) return@launch
                    val index = siblings.indexOfFirst { samePath(it.absolutePath, current.absolutePath) }
                    if (index < 0) return@launch
                    val shouldWrap = wrapOverride ?: playlistWrapNavigation
                    val targetIndex = resolveAdjacentIndex(index, offset, siblings.size, shouldWrap) ?: return@launch
                    if (generation != playRequestGeneration.get()) return@launch
                    playFile(siblings[targetIndex])
                }
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
                    playAdjacentSiblingTrack(offset, browserWrapOverride, notifyWrap)
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
            // Queue entry follows playback: a subtune switch under the current
            // file re-resolves the entry, mirroring Android's re-match effect.
            // Skipped while a queue load is in flight (openQueueEntry owns it).
            LaunchedEffect(
                session.currentSourceId,
                session.subtuneIndex,
                activePlaylist,
                pendingQueueEntryId
            ) {
                if (pendingQueueEntryId != null) return@LaunchedEffect
                val matched = matchPlaylistEntryForPlayback(
                    playlist = activePlaylist,
                    activeSourceId = session.currentSourceId ?: session.currentFile?.absolutePath,
                    currentSubtuneIndex = session.subtuneIndex
                )
                if (matched != null && matched.id != activePlaylistEntryId) {
                    activePlaylistEntryId = matched.id
                }
            }
            // Shared M3U tap flow; save reuses the playlist chooser below.
            val browserPlaylistPrompt = rememberBrowserPlaylistPrompt(
                toast = { toastHandler.showToast(it) },
                onOpenDocument = { document, entryId ->
                    val queue = buildImportedPlaylist(document)
                    val target = if (entryId == null) {
                        queue.entries.firstOrNull()
                    } else {
                        queue.entries.firstOrNull { it.id == entryId }
                    }
                    target?.let { openQueueEntry(queue, it, false) }
                }
            )
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
            val availableEffects = remember { queryEffectDescriptors() }
            var showAudioEffectsDialog by remember { mutableStateOf(false) }
            var masterVolumeDb by remember {
                mutableStateOf(prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f))
            }
            var masterMuted by remember { mutableStateOf(false) }
            fun applyMasterGainToNative() {
                NativeBridge.setMasterGain(if (masterMuted) MasterMuteGainDb else masterVolumeDb)
            }
            var globalChain by remember { mutableStateOf(readGlobalEffectChain(prefs)) }
            var forceMono by remember {
                mutableStateOf(globalChain.master.mono)
            }
            var songVolumeDb by remember { mutableStateOf(0f) }
            var ignoreCoreVolumeForSong by remember { mutableStateOf(false) }
            var coreChain by remember(session.decoderName) {
                mutableStateOf(readCoreEffectChain(prefs, session.decoderName))
            }
            var bypassAllEffects by remember { mutableStateOf(readBypassAllEffects(prefs)) }
            var effectPresets by remember { mutableStateOf(readEffectPresets(prefs)) }
            var selectedScope by remember {
                mutableStateOf(
                    EffectChainScopeTarget.fromStorageKey(
                        prefs.getString(AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE, "global")
                    )
                )
            }
            fun currentPlaylistEffectChain(): EffectChain? =
                readPlaylistEffectChain(playlistLibraryState, activePlaylist?.id)
                    ?: readPlaylistEffectChain(activePlaylist)
            fun currentSongEffectChain(): EffectChain? =
                readSongEffectChain(songVolumeStore, session.currentSourceId)
            var tempMasterVolumeDb by remember { mutableStateOf(masterVolumeDb) }
            var tempPluginVolumeDb by remember { mutableStateOf(0f) }
            var tempSongVolumeDb by remember { mutableStateOf(songVolumeDb) }
            var tempIgnoreCoreVolumeForSong by remember { mutableStateOf(ignoreCoreVolumeForSong) }
            var tempSelectedScope by remember { mutableStateOf(selectedScope) }
            var tempGlobalChain by remember { mutableStateOf(globalChain) }
            var tempCoreChain by remember { mutableStateOf(coreChain) }
            var tempPlaylistChain by remember { mutableStateOf(currentPlaylistEffectChain()) }
            var tempSongChain by remember { mutableStateOf(currentSongEffectChain()) }
            var tempBypassAllEffects by remember { mutableStateOf(bypassAllEffects) }
            var tempEffectPresets by remember { mutableStateOf(effectPresets) }
            SideEffect {
                session.activePlaylistProvider = { activePlaylist }
                session.playlistLibraryStateProvider = { playlistLibraryState }
                session.songVolumeStoreProvider = { songVolumeStore }
            }
            fun resolveCurrentTempChain(): EffectChain =
                resolveEffectiveEffectChain(
                    global = tempGlobalChain,
                    core = if (session.decoderName != null) tempCoreChain else null,
                    playlist = if (activePlaylist != null) tempPlaylistChain else null,
                    song = if (session.currentSourceId != null) tempSongChain else null,
                    bypassAll = tempBypassAllEffects
                )
            fun applyCurrentTempChainToNative() {
                applyEffectChainToNative(resolveCurrentTempChain())
            }
            SideEffect {
                activeDialogPreviewChainProvider = if (showAudioEffectsDialog) {
                    { resolveCurrentTempChain() }
                } else {
                    null
                }
            }
            fun applyCommittedAudioParametersToNative() {
                applyMasterGainToNative()
                NativeBridge.setPluginGain(
                    if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, session.decoderName)
                )
                NativeBridge.setSongGain(songVolumeDb)
                applyEffectChainToNative(
                    resolveEffectiveEffectChain(
                        global = globalChain,
                        core = coreChain,
                        playlist = currentPlaylistEffectChain(),
                        song = currentSongEffectChain(),
                        bypassAll = bypassAllEffects
                    )
                )
            }
            fun openAudioEffectsDialog() {
                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                globalChain = readGlobalEffectChain(prefs)
                coreChain = readCoreEffectChain(prefs, session.decoderName)
                bypassAllEffects = readBypassAllEffects(prefs)
                effectPresets = readEffectPresets(prefs)
                val playlistChain = currentPlaylistEffectChain()
                val songChain = currentSongEffectChain()
                val path = session.currentSourceId
                songVolumeDb = path?.let { songVolumeStore.getSongVolume(it) } ?: 0f
                ignoreCoreVolumeForSong = path?.let { songVolumeStore.getSongIgnoreCoreVolume(it) } ?: false
                tempMasterVolumeDb = masterVolumeDb
                tempPluginVolumeDb = readPluginVolumeForDecoder(prefs, session.decoderName)
                tempSongVolumeDb = songVolumeDb
                tempIgnoreCoreVolumeForSong = ignoreCoreVolumeForSong
                tempGlobalChain = globalChain
                tempCoreChain = coreChain
                tempPlaylistChain = playlistChain
                tempSongChain = songChain
                tempBypassAllEffects = bypassAllEffects
                tempEffectPresets = effectPresets
                tempSelectedScope = resolveInitialDialogScope(
                    savedScope = selectedScope,
                    hasActiveCore = session.decoderName != null,
                    hasActivePlaylist = activePlaylist != null,
                    hasActiveSong = session.currentFile != null,
                    coreChain = coreChain,
                    playlistChain = playlistChain,
                    songChain = songChain
                )
                showAudioEffectsDialog = true
            }
            fun dismissAudioEffectsDialog() {
                activeDialogPreviewChainProvider = null
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
            val contentFocusManager = LocalFocusManager.current
            SideEffect {
                desktopFocusManager = contentFocusManager
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
                activeDialogPreviewChainProvider = null
                masterVolumeDb = tempMasterVolumeDb
                songVolumeDb = tempSongVolumeDb
                ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong
                selectedScope = tempSelectedScope
                globalChain = tempGlobalChain
                forceMono = globalChain.master.mono
                val coreName = session.decoderName
                coreChain = if (coreName != null) tempCoreChain else null
                bypassAllEffects = tempBypassAllEffects
                effectPresets = tempEffectPresets
                prefs.edit().apply {
                    putFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, tempMasterVolumeDb)
                    putString(
                        AppPreferenceKeys.AUDIO_DSP_EDITOR_NAMESPACE,
                        selectedScope.storageKey
                    )
                    writeBypassAllEffects(this, bypassAllEffects)
                    writeEffectPresets(this, effectPresets)
                    writeGlobalEffectChain(this, globalChain)
                    if (coreName != null) {
                        writeCoreEffectChain(this, coreName, coreChain)
                    }
                    apply()
                }
                writePluginVolumeForDecoder(prefs, coreName, tempPluginVolumeDb)
                activePlaylist?.let { playlist ->
                    val updatedPlaylistState = writePlaylistEffectChain(
                        state = playlistLibraryState,
                        playlistId = playlist.id,
                        chain = tempPlaylistChain
                    )
                    if (updatedPlaylistState != playlistLibraryState) {
                        onPlaylistLibraryStateChanged(updatedPlaylistState)
                    }
                    val normalizedJson = tempPlaylistChain?.toJson()
                    if (playlist.effectChain != normalizedJson) {
                        activePlaylist = playlist.copy(effectChain = normalizedJson)
                    }
                }
                session.currentSourceId?.let { path ->
                    songVolumeStore.setSongVolume(path, tempSongVolumeDb)
                    songVolumeStore.setSongIgnoreCoreVolume(path, tempIgnoreCoreVolumeForSong)
                    writeSongEffectChain(songVolumeStore, path, tempSongChain)
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
            // Push the saved decoder order and enables at startup like Android's
            // AppNavigationStartupEffects; without this the baked registry order
            // rules every launch and settings reorders never stick.
            LaunchedEffect(prefs) {
                withContext(Dispatchers.IO) { loadPluginConfigurations(prefs) }
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
                    if (activePlaylist == null && !snapshot.playlistSource.isNullOrBlank()) {
                        // Imported queue (an M3U opened from the browser, not the
                        // library): re-parse and re-match by entry source plus
                        // subtune, since parsing mints fresh entry ids.
                        val restored = withContext(Dispatchers.IO) {
                            val playlistFile = resolvePlaylistEntryLocalFile(snapshot.playlistSource)
                                ?.takeIf { it.exists() && it.isFile && isSupportedPlaylistFile(it) }
                                ?: return@withContext null
                            val queue = parsePlaylistDocument(playlistFile, null)
                                ?.let(::buildImportedPlaylist)
                                ?: return@withContext null
                            val queueEntry = queue.entries.firstOrNull {
                                samePath(it.source, snapshot.entrySource ?: snapshot.sourceId) &&
                                    it.subtuneIndex == snapshot.subtuneIndex
                            } ?: return@withContext null
                            if (!samePath(queueEntry.source, snapshot.sourceId)) {
                                return@withContext null
                            }
                            queue to queueEntry
                        }
                        if (restored != null) {
                            activePlaylist = restored.first
                            activePlaylistEntryId = restored.second.id
                            if (snapshot.shuffleActive) {
                                activePlaylistShuffleActive = true
                                activePlaylist = restored.first.copy(
                                    entries = listOf(restored.second) +
                                        restored.first.entries.filter { it.id != restored.second.id }.shuffled()
                                )
                            }
                        }
                    }
                    session.preferredRepeatMode = preferredRepeatMode
                    // Decoder open can grind (large modules, archives):
                    // never on first composition.
                    val restoreLoaded = withContext(Dispatchers.IO) {
                        session.loadFile(file, autoStart = false)
                    }
                    if (restoreLoaded) {
                        val subtune = activePlaylist?.entries
                            ?.firstOrNull { it.id == activePlaylistEntryId }
                            ?.subtuneIndex
                        if (subtune != null && subtune in 0 until session.subtuneCount) {
                            withContext(Dispatchers.IO) { session.selectSubtune(subtune) }
                        }
                        if (snapshot.hasValidPosition() && session.canSeek) {
                            val restorePosition = snapshot.positionSeconds
                            withContext(Dispatchers.IO) { session.seekTo(restorePosition) }
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
                val queueEntry = playlist?.entries?.firstOrNull { it.id == entryId }
                val playlistId = if (playlist != null && queueEntry != null) {
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
                        shuffleActive = activePlaylistShuffleActive,
                        playlistSource = if (playlistId != null) {
                            playlist?.sourceIdHint?.trim()?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        },
                        entrySource = if (playlistId != null) queueEntry?.source else null,
                        subtuneIndex = if (playlistId != null) queueEntry?.subtuneIndex else null
                    )
                )
            }
            LaunchedEffect(Unit) {
                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                globalChain = readGlobalEffectChain(prefs)
                forceMono = globalChain.master.mono
                applyMasterGainToNative()
                NativeBridge.setPluginGain(0f)
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
                applyEffectChainToNative(globalChain)
            }
            LaunchedEffect(
                showAudioEffectsDialog,
                tempGlobalChain,
                tempCoreChain,
                tempPlaylistChain,
                tempSongChain,
                tempBypassAllEffects,
                session.currentSourceId,
                session.decoderName,
                activePlaylist?.id
            ) {
                if (showAudioEffectsDialog) {
                    applyCurrentTempChainToNative()
                }
            }
            LaunchedEffect(
                session.currentSourceId,
                session.decoderName,
                activePlaylist?.id,
                activePlaylist?.effectChain,
                playlistLibraryState
            ) {
                val path = session.currentSourceId
                songVolumeDb = path?.let { songVolumeStore.getSongVolume(it) } ?: 0f
                ignoreCoreVolumeForSong = path?.let { songVolumeStore.getSongIgnoreCoreVolume(it) } ?: false
                NativeBridge.setPluginGain(
                    if (ignoreCoreVolumeForSong) 0f else readPluginVolumeForDecoder(prefs, session.decoderName)
                )
                NativeBridge.setSongGain(songVolumeDb)
                if (!showAudioEffectsDialog) {
                    val resolved = resolveEffectiveEffectChainForPrefs(
                        prefs = prefs,
                        coreName = session.decoderName,
                        playlist = activePlaylist,
                        playlistState = playlistLibraryState,
                        songVolumeStore = songVolumeStore,
                        songPath = path
                    )
                    forceMono = resolved.master.mono
                    applyEffectChainToNative(resolved)
                }
            }
            LaunchedEffect(prefs) {
                readRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, recentFoldersLimit, prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        recentFolders.clear()
                        recentFolders.addAll(stored)
                    }
                readRecentEntries(
                    configDir,
                    AppPreferenceKeys.RECENT_PLAYED_FILES,
                    recentFilesLimit,
                    prefs,
                    prefs.getBoolean(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, false)
                )
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        Snapshot.withMutableSnapshot {
                            recentFiles.clear()
                            recentFiles.addAll(stored)
                        }
                    }
                readPinnedHomeEntries(configDir, legacyPrefs = prefs)
                    .takeIf { it.isNotEmpty() }?.let { stored ->
                        pinnedEntries.clear()
                        pinnedEntries.addAll(stored)
                    }
            }
            // Track every playing-subtune change (transport, queue advance,
            // engine auto-advance, restore), not just recents-initiated opens.
            LaunchedEffect(
                session.currentSourceId,
                session.currentFile,
                session.subtuneIndex,
                session.subtuneCount,
                perSubtuneRecentRows
            ) {
                if (session.currentFile == null || session.currentSourceId == null || session.subtuneCount <= 1) {
                    return@LaunchedEffect
                }
                recordSubtuneSwitchInRecents()
            }
            LaunchedEffect(perSubtuneRecentRows, recentFilesLimit, recentFoldersLimit) {
                snapshotFlow { Triple(recentFiles.toList(), recentFolders.toList(), pinnedEntries.toList()) }
                    .distinctUntilChanged()
                    .collect { (files, folders, pinned) ->
                        writeRecentEntries(configDir, AppPreferenceKeys.RECENT_FOLDERS, folders, recentFoldersLimit)
                        writeRecentEntries(
                            configDir,
                            AppPreferenceKeys.RECENT_PLAYED_FILES,
                            files,
                            recentFilesLimit,
                            perSubtuneRecentRows
                        )
                        writePinnedHomeEntries(configDir, pinned)
                    }
            }
            val currentTrackPath = session.currentSourceId
            val isCurrentTrackFavorited = currentTrackPath != null &&
                playlistLibraryState.favorites.any { samePath(it.source, currentTrackPath) }

            val miniPreviewLiftPx = with(LocalDensity.current) { 28.dp.toPx() }
            val playerPreviewScreenHeightPx = with(LocalDensity.current) { windowState.size.height.toPx() }
            val miniDismissMaxOffsetPx = with(LocalDensity.current) { 108.dp.toPx() }
            val visualizationUiState = rememberVisualizationUiState(
                prefs = prefs,
                activeCoreName = session.decoderName,
                isPlayerSurfaceVisible = isPlayerSurfaceVisible
            )
            // Shared shortcut actions; the player buttons delegate to these
            // too, so shortcuts and UI can never diverge.
            fun goPreviousTrack() {
                playQueuePreviousTrack(
                    playlistWrapOverride = false,
                    browserWrapOverride = session.repeatMode != RepeatMode.None,
                    notifyWrap = true
                )
            }
            fun goNextTrack() {
                playQueueAdjacentTrack(
                    1,
                    stopAtBoundary = true,
                    playlistWrapOverride = false,
                    browserWrapOverride = session.repeatMode != RepeatMode.None,
                    notifyWrap = true
                )
            }
            fun seekKeyboardStep(stepSeconds: Double) {
                if (session.canSeek && session.durationSeconds > 0.0) {
                    session.seekTo(session.positionSeconds + stepSeconds)
                }
            }
            fun cycleRepeatModeWithToast() {
                session.cycleRepeatMode()?.let { next ->
                    preferredRepeatMode = next
                    toastHandler.showToast(next.label)
                }
            }
            fun toggleFavoriteTrack() {
                val entry = buildCurrentTrackEntry() ?: return
                togglePlaylistEntryFavorite(entry)
            }
            var lastNonOffVisualizationMode by remember { mutableStateOf<VisualizationMode?>(null) }
            fun toggleVisualization() {
                val current = visualizationUiState.mode
                if (current == VisualizationMode.Off) {
                    visualizationUiState.onSelectMode(
                        lastNonOffVisualizationMode?.takeIf { visualizationUiState.availableModes.contains(it) }
                            ?: visualizationUiState.availableModes.firstOrNull { it != VisualizationMode.Off }
                            ?: VisualizationMode.Off
                    )
                } else {
                    lastNonOffVisualizationMode = current
                    visualizationUiState.onSelectMode(VisualizationMode.Off)
                }
            }
            fun cycleVisualizationMode(step: Int) {
                val modes = visualizationUiState.availableModes
                if (modes.isEmpty()) return
                val index = modes.indexOf(visualizationUiState.mode).takeIf { it >= 0 } ?: 0
                visualizationUiState.onSelectMode(modes[(index + step + modes.size) % modes.size])
            }

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
                // MPRIS (headset/BT media buttons, DE media widget) drives the same
                // actions as the on-screen controls; the bridge reads the live session.
                val mprisBridge = remember { DesktopMprisBridge(session) }
                // Bind before start: the bus name must never be visible without wired actions,
                // or a client discovering us in between drops the player and never retries.
                DisposableEffect(Unit) {
                    mprisBridge.bind(
                        commands = MprisCommands(
                            playPause = { if (session.isPlaying) session.pause() else session.play() },
                            play = { session.play() },
                            pause = { session.pause() },
                            stop = { session.stop() },
                            next = {
                                playQueueAdjacentTrack(
                                    1,
                                    stopAtBoundary = true,
                                    playlistWrapOverride = false,
                                    browserWrapOverride = session.repeatMode != RepeatMode.None,
                                    notifyWrap = true
                                )
                            },
                            previous = {
                                playQueuePreviousTrack(
                                    playlistWrapOverride = false,
                                    browserWrapOverride = session.repeatMode != RepeatMode.None,
                                    notifyWrap = true
                                )
                            },
                            seekToMicroseconds = { micros -> session.seekTo(micros / 1_000_000.0) },
                            setVolume = { volume ->
                                masterVolumeDb = mprisVolumeToMasterGainDb(volume)
                                masterMuted = false
                                applyMasterGainToNative()
                            },
                            setLoopStatus = { loopStatus -> preferredRepeatMode = repeatModeForMprisLoopStatus(loopStatus) },
                            quit = { requestApplicationExit() },
                            raise = { bringDesktopWindowToFront() },
                            openUri = { uri ->
                                // Headless callers cannot answer a decoder picker;
                                // play headlessly like the in-app browser does.
                                val file = resolveDesktopMprisOpenUriFile(uri)
                                if (file == null) playSource(uri) else playFile(file)
                            }
                        ),
                        volume = { masterGainDbToMprisVolume(masterVolumeDb, masterMuted) }
                    )
                    mprisBridge.start()
                    onDispose { mprisBridge.stop() }
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                // Shortcuts yield to text input everywhere.
                                if (textInputTracker.hasActiveInput) return@onPreviewKeyEvent false
                                // Transport globals: work anywhere the window has focus.
                                if (keyEvent.isAltPressed && !keyEvent.isCtrlPressed) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> { goPreviousTrack(); return@onPreviewKeyEvent true }
                                        Key.DirectionRight -> { goNextTrack(); return@onPreviewKeyEvent true }
                                    }
                                }
                                if (keyEvent.isCtrlPressed && !keyEvent.isAltPressed) {
                                    when (keyEvent.key) {
                                        Key.DirectionLeft -> {
                                            seekKeyboardStep(-KeyboardSeekStepSeconds)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.DirectionRight -> {
                                            seekKeyboardStep(KeyboardSeekStepSeconds)
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.Home -> {
                                            if (session.currentFile != null) session.seekTo(0.0)
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                }
                                if (keyEvent.isShiftPressed && !keyEvent.isCtrlPressed && !keyEvent.isAltPressed) {
                                    if (isPlayerExpanded) {
                                        when (keyEvent.key) {
                                            Key.DirectionLeft -> {
                                                if (session.subtuneCount > 1 && session.subtuneIndex > 0) {
                                                    session.previousSubtune()
                                                    recordSubtuneSwitchInRecents()
                                                }
                                                return@onPreviewKeyEvent true
                                            }
                                            Key.DirectionRight -> {
                                                if (session.subtuneCount > 1 &&
                                                    session.subtuneIndex + 1 < session.subtuneCount
                                                ) {
                                                    session.nextSubtune()
                                                    recordSubtuneSwitchInRecents()
                                                }
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                }
                                if (!keyEvent.isCtrlPressed && !keyEvent.isAltPressed && !keyEvent.isShiftPressed) {
                                    if (keyEvent.key == Key.Break || keyEvent.key.nativeKeyCode == AwtVkPause) {
                                        session.togglePlayPause()
                                        return@onPreviewKeyEvent true
                                    }
                                    when (keyEvent.key) {
                                        Key.MediaPlayPause -> {
                                            session.togglePlayPause()
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.MediaPlay -> {
                                            session.play()
                                            return@onPreviewKeyEvent true
                                        }
                                        Key.MediaPause -> {
                                            session.pauseFromHardwareKey()
                                            return@onPreviewKeyEvent true
                                        }
                                    }
                                    // Browser state: only when the file explorer is visible.
                                    if (currentView == MainView.Browser && !isPlayerExpanded) {
                                        when (keyEvent.key) {
                                            Key.F5 -> {
                                                browserRefreshToken += 1
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                    // Player state: only when maximized.
                                    if (isPlayerExpanded) {
                                        when (keyEvent.key) {
                                            Key.R -> {
                                                cycleRepeatModeWithToast()
                                                return@onPreviewKeyEvent true
                                            }
                                            Key.F -> {
                                                toggleFavoriteTrack()
                                                return@onPreviewKeyEvent true
                                            }
                                            Key.V -> {
                                                toggleVisualization()
                                                return@onPreviewKeyEvent true
                                            }
                                            Key.PageUp -> {
                                                cycleVisualizationMode(-1)
                                                return@onPreviewKeyEvent true
                                            }
                                            Key.PageDown -> {
                                                cycleVisualizationMode(1)
                                                return@onPreviewKeyEvent true
                                            }
                                        }
                                    }
                                }
                                false
                            }
                    ) {
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
                                            recentRowsPerSubtune = perSubtuneRecentRows,
                                            currentTrackSubtuneIndex = session.subtuneIndex.takeIf { session.subtuneCount > 1 },
                                            currentTrackTitle = playerMetadata.title,
                                            currentTrackArtist = playerMetadata.artist,
                                            pinnedHomeEntries = pinnedEntriesDisplay,
                                            recentFolders = recentFoldersDisplay,
                                            recentPlayedFiles = recentFilesDisplay,
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
                                                loadSmbRecentFolderContext(
                                                    scope = desktopSmbFolderScope,
                                                    entry = entry.asRecentPathEntry(),
                                                    networkNodes = networkNodes,
                                                    showHiddenFilesAndFolders = prefs.getBoolean(
                                                        AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS,
                                                        AppDefaults.Browser.showHiddenFilesAndFolders
                                                    )
                                                )
                                                playSource(entry.path, entry.title, entry.artist, entry.subtuneIndex)
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
                                                if (entry.isPlaylist) {
                                                    openRecentPlaylistEntry(entry)
                                                    return@HomeScreen
                                                }
                                                loadSmbRecentFolderContext(
                                                    scope = desktopSmbFolderScope,
                                                    entry = entry,
                                                    networkNodes = networkNodes,
                                                    showHiddenFilesAndFolders = prefs.getBoolean(
                                                        AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS,
                                                        AppDefaults.Browser.showHiddenFilesAndFolders
                                                    )
                                                )
                                                playSource(entry.path, entry.title, entry.artist, entry.subtuneIndex)
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
                                                            artworkThumbnailCacheKey = entry.artworkThumbnailCacheKey,
                                                            subtuneIndex = entry.subtuneIndex
                                                        )
                                                    )
                                                }
                                            },
                                            onPersistRecentFileMetadata = { entry, title, artist ->
                                                val idx = recentFiles.indexOfFirst {
                                                    if (perSubtuneRecentRows) {
                                                        sameRecentTrack(it.path, it.subtuneIndex, entry.path, entry.subtuneIndex)
                                                    } else {
                                                        it.path == entry.path
                                                    }
                                                }
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
                                                    onPlaylistFileSelected = { file, hint -> browserPlaylistPrompt.onPlaylistFileSelected(file, hint) },
                                                    pinnedHomeEntries = pinnedEntries,
                                                    onPinHomeEntry = ::pinHomeEntry,
                                                    playlists = emptyList(),
                                                    favoriteSourceIds = emptySet(),
                                                    networkNodes = networkNodes,
                                                    refreshRequestToken = browserRefreshToken
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
                                                    onPlaylistFileSelected = { file, hint -> browserPlaylistPrompt.onPlaylistFileSelected(file, hint) },
                                                    pinnedHomeEntries = pinnedEntries,
                                                    onPinHomeEntry = ::pinHomeEntry,
                                                    refreshRequestToken = browserRefreshToken,
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
                                                playingPlaylistFile = playingPlaylistBrowserFile(
                                                    activePlaylist,
                                                    playerActiveEntry
                                                ),
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
                                                onPlaylistFileSelected = { file, hint -> browserPlaylistPrompt.onPlaylistFileSelected(file, hint) },
                                                onBrowserLocationChanged = { launchState ->
                                                    launchState.directoryPath?.let { path ->
                                                        currentDirectory = File(path)
                                                    }
                                                },
                                                pinnedHomeEntries = pinnedEntries,
                                                onPinHomeEntry = ::pinHomeEntry,
                                                refreshRequestToken = browserRefreshToken
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
                                            currentPlaybackTitle = playerMetadata.title,
                                            currentPlaybackArtist = playerMetadata.artist,
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
                                            val filePrefix = File(settingsCacheDir, REMOTE_SOURCE_CACHE_DIR).absolutePath + File.separator
                                            val progressivePrefix = File(settingsCacheDir, PROGRESSIVE_REMOTE_SOURCE_CACHE_DIR).absolutePath + File.separator
                                            if (path != null && (path.startsWith(filePrefix) || path.startsWith(progressivePrefix))) setOf(path) else emptySet()
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
                                            visualizationShowDebugInfo = playerVisualizationShowDebugInfo,
                                            onVisualizationShowDebugInfoChanged = { playerVisualizationShowDebugInfo = it },
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
                                            currentCoreName = session.decoderName,
                                            activePlaylist = activePlaylist,
                                            playlistLibraryState = playlistLibraryState,
                                            currentSongPath = session.currentSourceId,
                                            onAudioParametersCleared = {
                                                masterVolumeDb = prefs.getFloat(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
                                                globalChain = readGlobalEffectChain(prefs)
                                                coreChain = readCoreEffectChain(prefs, session.decoderName)
                                                val path = session.currentSourceId
                                                songVolumeDb = path?.let { songVolumeStore.getSongVolume(it) } ?: 0f
                                                ignoreCoreVolumeForSong = path?.let { songVolumeStore.getSongIgnoreCoreVolume(it) } ?: false
                                                val resolved = resolveEffectiveEffectChainForPrefs(
                                                    prefs = prefs,
                                                    coreName = session.decoderName,
                                                    playlist = activePlaylist,
                                                    playlistState = playlistLibraryState,
                                                    songVolumeStore = songVolumeStore,
                                                    songPath = path
                                                )
                                                forceMono = resolved.master.mono
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
                        val playerExpandedOverlayVisible = isPlayerSurfaceVisible && isPlayerExpanded
                        val playerDragPreviewActive = isPlayerSurfaceVisible && !isPlayerExpanded &&
                            miniExpandPreviewActive
                        val playerOverlayVisible = playerDragPreviewActive || playerExpandedOverlayVisible
                        val playerPreviewMode = !playerExpandedOverlayVisible && miniExpandPreviewActive
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
                                title = resolveMiniPlayerTitle(playerMetadata.title, session.currentFile),
                                artist = resolveMiniPlayerArtist(playerMetadata.artist, session.currentFile),
                                metadataTitleResolved = session.title.isNotBlank(),
                                artwork = session.artwork,
                                noArtworkIcon = placeholderArtworkIconForFile(session.currentFile, session.decoderName),
                                artworkCornerRadiusDp = playerArtworkCornerRadiusDp,
                                isPlaying = session.isPlaying,
                                playbackStartInProgress = false,
                                seekInProgress = false,
                                canResumeStoppedTrack = session.canResume(),
                                positionSeconds = session.positionSeconds,
                                durationSeconds = effectiveDurationSeconds,
                                hasReliableDuration = effectiveHasReliableDuration,
                                previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                canPreviousTrack = session.currentFile != null,
                                canNextTrack = session.currentFile != null,
                                canPreviousSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex > 0,
                                canNextSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex + 1 < transportSubtuneCount,
                                currentSubtuneIndex = transportCurrentSubtuneIndex,
                                subtuneCount = transportSubtuneCount,
                                onExpand = {
                                    expandFromMiniDrag = miniExpandPreviewActive
                                    miniExpandPreviewProgress = 0f
                                    miniExpandPreviewActive = false
                                    isPlayerExpanded = true
                                },
                                onExpandDragProgress = {
                                    miniExpandPreviewProgress = it
                                    miniExpandPreviewActive = it > 0f
                                },
                                onExpandDragCommit = {
                                    if (dragExpandCommitInProgress) {
                                        return@MiniPlayerBar
                                    }
                                    dragExpandCommitInProgress = true
                                    expandFromMiniDrag = true
                                    miniExpandPreviewProgress = 0f
                                    miniExpandPreviewActive = false
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
                                onPreviousSubtune = {
                                    session.previousSubtune()
                                    recordSubtuneSwitchInRecents()
                                },
                                onNextSubtune = {
                                    session.nextSubtune()
                                    recordSubtuneSwitchInRecents()
                                },
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
                                            miniExpandPreviewProgress,
                                            playerPreviewScreenHeightPx
                                        )
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
                            enter = if (expandFromMiniDrag || playerDragPreviewActive) {
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
                            val desktopOverlayVisibilityProvider = remember(playerPreviewMode) {
                                { 1f }
                            }

                            CompositionLocalProvider(
                                LocalPlayerFocusIndicatorsEnabled provides true,
                                LocalPlayerOverlayVisibility provides desktopOverlayVisibilityProvider,
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
                                    durationSeconds = effectiveDurationSeconds,
                                    positionSeconds = session.positionSeconds,
                                    positionSecondsProvider = { session.positionSeconds },
                                    canPreviousTrack = session.currentFile != null,
                                    canNextTrack = session.currentFile != null,
                                    title = playerMetadata.title,
                                    artist = playerMetadata.artist,
                                    album = playerMetadata.album,
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
                                    requestSeekBarInitialFocus = true,
                                    moveFocus = { direction -> desktopFocusManager?.moveFocus(direction) == true },
                                    repeatMode = session.repeatMode,
                                    canCycleRepeatMode = supportsLiveRepeatMode(session.playbackCapabilitiesFlags),
                                    canSeek = session.canSeek,
                                    hasReliableDuration = effectiveHasReliableDuration,
                                    playbackCapabilitiesFlags = session.playbackCapabilitiesFlags,
                                    onSeek = { seconds -> session.seekTo(seconds) },
                                    onPreviousTrack = { goPreviousTrack() },
                                    onForcePreviousTrack = {
                                        playQueueAdjacentTrack(
                                            -1,
                                            stopAtBoundary = false,
                                            playlistWrapOverride = false,
                                            browserWrapOverride = session.repeatMode != RepeatMode.None,
                                            notifyWrap = true
                                        )
                                    },
                                    onNextTrack = { goNextTrack() },
                                    onPreviousSubtune = {
                                        session.previousSubtune()
                                        recordSubtuneSwitchInRecents()
                                    },
                                    onNextSubtune = {
                                        session.nextSubtune()
                                        recordSubtuneSwitchInRecents()
                                    },
                                    onOpenSubtuneSelector = { showSubtuneSelectorDialog = true },
                                    canPreviousSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex > 0,
                                    canNextSubtune = transportSubtuneCount > 1 && transportCurrentSubtuneIndex + 1 < transportSubtuneCount,
                                    canOpenSubtuneSelector = !queueSubtunePinned && session.subtuneCount > 1,
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
                                    currentSubtuneIndex = transportCurrentSubtuneIndex,
                                    subtuneCount = transportSubtuneCount,
                                    titleCurrentSubtuneIndex = session.subtuneIndex,
                                    titleSubtuneCount = session.subtuneCount,
                                    subtuneTitleClickable = session.subtuneCount > 1,
                                    onCycleRepeatMode = { cycleRepeatModeWithToast() },
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
                                    onToggleFavoriteTrack = { toggleFavoriteTrack() },
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
                            durationSeconds = effectiveDurationSeconds,
                            hasReliableDuration = effectiveHasReliableDuration,
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
                                val queue = activePlaylist
                                if (queue != null) {
                                    openQueueEntry(queue, entry, activePlaylistShuffleActive)
                                } else {
                                    resolvePlaylistEntryLocalFile(entry.source)?.let { playFile(it, entry.subtuneIndex) }
                                }
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

                    if (browserPlaylistPrompt.showAction || browserPlaylistPrompt.showPreview) {
                        PlatformBackHandler(enabled = true) {
                            browserPlaylistPrompt.dismiss()
                        }
                    }
                    BrowserPlaylistPromptDialogs(
                        prompt = browserPlaylistPrompt,
                        onSave = { document ->
                            selectorImportDialogTitle = "Save playlist"
                            selectorImportTitle = document.title
                            selectorImportEntries = document.entries
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

                    if (showAudioEffectsDialog) {
                        PlatformBackHandler(enabled = true) {
                            dismissAudioEffectsDialog()
                        }
                        AudioEffectsDialog(
                            masterVolumeDb = tempMasterVolumeDb,
                            pluginVolumeDb = tempPluginVolumeDb,
                            songVolumeDb = tempSongVolumeDb,
                            ignoreCoreVolumeForSong = tempIgnoreCoreVolumeForSong,
                            hasActiveCore = session.decoderName != null,
                            hasActivePlaylist = activePlaylist != null,
                            hasActiveSong = session.currentFile != null,
                            currentCoreName = session.decoderName,
                            currentPlaylistName = activePlaylist?.title,
                            currentSongName = session.currentFile?.name,
                            selectedScope = tempSelectedScope,
                            globalChain = tempGlobalChain,
                            coreChain = tempCoreChain,
                            playlistChain = tempPlaylistChain,
                            songChain = tempSongChain,
                            availableEffects = availableEffects,
                            bypassAllEffects = tempBypassAllEffects,
                            presets = tempEffectPresets,
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
                            onSelectedScopeChange = { scope ->
                                tempSelectedScope = scope
                            },
                            onGlobalChainChange = { next ->
                                tempGlobalChain = next
                                applyCurrentTempChainToNative()
                            },
                            onCoreChainChange = { next ->
                                tempCoreChain = next
                                applyCurrentTempChainToNative()
                            },
                            onPlaylistChainChange = { next ->
                                tempPlaylistChain = next
                                applyCurrentTempChainToNative()
                            },
                            onSongChainChange = { next ->
                                tempSongChain = next
                                applyCurrentTempChainToNative()
                            },
                            onBypassAllEffectsChange = { next ->
                                tempBypassAllEffects = next
                                applyCurrentTempChainToNative()
                            },
                            onPresetsChange = { next ->
                                tempEffectPresets = next
                            },
                            onResetVolumeTab = {
                                tempMasterVolumeDb = 0f
                                tempPluginVolumeDb = 0f
                                tempSongVolumeDb = 0f
                                tempIgnoreCoreVolumeForSong = false
                                NativeBridge.setMasterGain(0f)
                                NativeBridge.setPluginGain(0f)
                                NativeBridge.setSongGain(0f)
                            },
                            onResetScopeChain = { scope ->
                                when (scope) {
                                    EffectChainScopeTarget.Global -> tempGlobalChain = EffectChain.Empty
                                    EffectChainScopeTarget.Core -> tempCoreChain = null
                                    EffectChainScopeTarget.Playlist -> tempPlaylistChain = null
                                    EffectChainScopeTarget.Song -> tempSongChain = null
                                }
                                applyCurrentTempChainToNative()
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
                                                    session.selectSubtuneAsync(entry.index)
                                                    showSubtuneSelectorDialog = false
                                                    session.currentFile?.let { recordSubtuneSwitchInRecents() }
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
            SiliconPlayerBaseTheme(darkTheme = darkTheme) {
                ToastOverlay(items = toasts, onDismiss = {})
            }
        }
    }
}
