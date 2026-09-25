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
import com.flopster101.siliconplayer.readNetworkNodes
import com.flopster101.siliconplayer.writeNetworkNodes
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.data.FileRepository
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.ui.screens.FileBrowserScreen
import com.flopster101.siliconplayer.ui.screens.NetworkBrowserScreen
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.desktop.ui.DesktopPlaylistsScreen
import com.flopster101.siliconplayer.SettingsScreen
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.MainView
import com.flopster101.siliconplayer.SettingsRoute
import com.flopster101.siliconplayer.MainNavigationScaffold
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
    val session = remember { DesktopPlaybackSession() }
    val windowState = rememberWindowState(width = 1100.dp, height = 750.dp)

    var currentView by remember { mutableStateOf(MainView.Home) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var currentDirectory by remember {
        mutableStateOf(File(System.getProperty("user.home") ?: "/"))
    }
    var settingsRoute by remember { mutableStateOf(SettingsRoute.Root) }

    val recentFiles = remember { mutableStateListOf<RecentPathEntry>() }
    val recentFolders = remember { mutableStateListOf<RecentPathEntry>() }
    val pinnedEntries = remember { mutableStateListOf<HomePinnedEntry>() }

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

    Window(
        onCloseRequest = {
            session.dispose()
            exitApplication()
        },
        state = windowState,
        title = windowTitle
    ) {
        ProvideDesktopPlatformAdapters(
            windowWidthDp = windowState.size.width.value.toInt(),
            windowHeightDp = windowState.size.height.value.toInt()
        ) {
            SiliconPlayerBaseTheme {
                if (isPlayerExpanded) {
                    PlayerScreen(
                        file = session.currentFile,
                        onBack = { isPlayerExpanded = false },
                        isPlaying = session.isPlaying,
                        canResumeStoppedTrack = true,
                        onPlay = { session.play() },
                        onPause = { session.pause() },
                        onStopAndClear = { session.stop() },
                        canPreviousTrack = false,
                        canNextTrack = false,
                        durationSeconds = session.durationSeconds,
                        positionSeconds = session.positionSeconds,
                        positionSecondsProvider = { session.positionSeconds },
                        title = session.title,
                        artist = session.artist,
                        album = session.album,
                        sampleRateHz = session.sampleRateHz,
                        channelCount = session.channelCount,
                        bitDepthLabel = session.bitDepthLabel,
                        decoderName = session.decoderName,
                        artwork = null,
                        repeatMode = session.repeatMode,
                        canCycleRepeatMode = true,
                        canSeek = session.canSeek,
                        hasReliableDuration = session.hasReliableDuration,
                        onSeek = { seconds -> session.seekTo(seconds) },
                        onPreviousTrack = {},
                        onForcePreviousTrack = {},
                        onNextTrack = {},
                        onPreviousSubtune = { session.previousSubtune() },
                        onNextSubtune = { session.nextSubtune() },
                        onOpenSubtuneSelector = {},
                        canPreviousSubtune = session.subtuneCount > 1 && session.subtuneIndex > 0,
                        canNextSubtune = session.subtuneCount > 1 && session.subtuneIndex + 1 < session.subtuneCount,
                        canOpenSubtuneSelector = session.subtuneCount > 1,
                        canOpenPlaylistSelector = true,
                        onOpenPlaylistSelector = { openDesktopFileChooser { playFile(it) } },
                        currentSubtuneIndex = session.subtuneIndex,
                        subtuneCount = session.subtuneCount,
                        titleCurrentSubtuneIndex = session.subtuneIndex,
                        titleSubtuneCount = session.subtuneCount,
                        onCycleRepeatMode = { session.cycleRepeatMode() },
                        canOpenCoreSettings = false,
                        onOpenCoreSettings = {},
                        visualizationMode = VisualizationMode.Off,
                        availableVisualizationModes = listOf(VisualizationMode.Off),
                        onCycleVisualizationMode = {},
                        onSelectVisualizationMode = {},
                        onOpenVisualizationSettings = {},
                        onOpenSelectedVisualizationSettings = {},
                        visualizationBarCount = 40,
                        visualizationBarSmoothingPercent = 60,
                        visualizationBarRoundnessDp = 6,
                        visualizationBarOverlayArtwork = false,
                        visualizationBarUseThemeColor = true,
                        visualizationBarRenderBackend = VisualizationRenderBackend.OpenGlTexture,
                        visualizationOscStereo = true,
                        visualizationVuAnchor = VisualizationVuAnchor.Bottom,
                        visualizationVuUseThemeColor = true,
                        visualizationVuSmoothingPercent = 50,
                        visualizationVuRenderBackend = VisualizationRenderBackend.OpenGlTexture,
                        onOpenAudioEffects = {}
                    )
                } else {
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
                                            onOpenLibrary = { currentView = MainView.Browser },
                                            onOpenPlaylists = { currentView = MainView.Playlists },
                                            onOpenNetwork = { currentView = MainView.Network },
                                            onOpenPinnedFolder = { entry ->
                                                currentDirectory = File(entry.path)
                                                currentView = MainView.Browser
                                            },
                                            onPlayPinnedFile = { entry ->
                                                playSource(entry.path, entry.title, entry.artist)
                                            },
                                            onOpenRecentFolder = { entry ->
                                                currentDirectory = File(entry.path)
                                                currentView = MainView.Browser
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
                                                        currentDirectory = File(entry.path)
                                                        currentView = MainView.Browser
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
                                                        currentDirectory = f.parentFile ?: f
                                                        currentView = MainView.Browser
                                                    }
                                                }
                                            },
                                            onRecentFolderAction = { entry, action ->
                                                when (action) {
                                                    FolderEntryAction.DeleteFromRecents -> recentFolders.removeAll { it.path == entry.path }
                                                    FolderEntryAction.CopyPath -> {}
                                                    FolderEntryAction.OpenInBrowser -> {
                                                        currentDirectory = File(entry.path)
                                                        currentView = MainView.Browser
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
                                                        currentDirectory = f.parentFile ?: f
                                                        currentView = MainView.Browser
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
                                        FileBrowserScreen(
                                            repository = repository,
                                            initialDirectoryPath = currentDirectory.absolutePath,
                                            playingFile = session.currentFile,
                                            bottomContentPadding = bottomMargin,
                                            showPrimaryTopBar = false,
                                            backHandlingEnabled = true,
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
                                            onPinHomeEntry = { entry, isFolder ->
                                                if (pinnedEntries.none { it.path == entry.path }) {
                                                    pinnedEntries.add(
                                                        HomePinnedEntry(
                                                            path = entry.path,
                                                            isFolder = isFolder,
                                                            title = entry.title,
                                                            artist = entry.artist,
                                                            decoderName = entry.decoderName
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    MainView.Playlists -> {
                                        DesktopPlaylistsScreen(
                                            session = session,
                                            onFileSelected = { playFile(it) }
                                        )
                                    }

                                    MainView.Network -> {
                                        val prefs = LocalAppPreferences.current
                                        val networkNodes = remember(prefs) {
                                            mutableStateListOf<NetworkNode>().apply {
                                                addAll(readNetworkNodes(prefs))
                                            }
                                        }
                                        var currentNetworkFolderId by remember { mutableStateOf<Long?>(null) }
                                        NetworkBrowserScreen(
                                            bottomContentPadding = bottomMargin,
                                            backHandlingEnabled = true,
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
                                            onBrowseSmbSource = { rawInput, _ ->
                                                playSource(rawInput)
                                            },
                                            onBrowseHttpSource = { rawInput, _, _ ->
                                                playSource(rawInput)
                                            },
                                            pinnedHomeEntries = pinnedEntries,
                                            onPinHomeEntry = { entry, isFolder ->
                                                if (pinnedEntries.none { it.path == entry.path }) {
                                                    pinnedEntries.add(
                                                        HomePinnedEntry(
                                                            path = entry.path,
                                                            isFolder = isFolder,
                                                            title = entry.title,
                                                            artist = entry.artist,
                                                            decoderName = entry.decoderName
                                                        )
                                                    )
                                                }
                                            }
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
                            visible = session.currentFile != null,
                            modifier = Modifier.align(Alignment.BottomCenter),
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                        ) {
                            MiniPlayerBar(
                                file = session.currentFile,
                                title = session.title.ifBlank { session.currentFile?.name ?: "No title" },
                                artist = session.artist.ifBlank { "Unknown Artist" },
                                metadataTitleResolved = session.title.isNotBlank(),
                                artwork = null,
                                noArtworkIcon = Icons.Default.MusicNote,
                                artworkCornerRadiusDp = 12,
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
                    }
                }
            }
        }
    }
}
