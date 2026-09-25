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
import com.flopster101.siliconplayer.HomePinnedEntry
import com.flopster101.siliconplayer.MiniPlayerBar
import com.flopster101.siliconplayer.RecentPathEntry
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.desktop.ui.DesktopFileBrowserScreen
import com.flopster101.siliconplayer.desktop.ui.DesktopHomeScreen
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
                                        DesktopHomeScreen(
                                            recentFiles = recentFiles,
                                            recentFolders = recentFolders,
                                            pinnedEntries = pinnedEntries,
                                            currentTrackPath = session.currentFile?.absolutePath,
                                            isPlaying = session.isPlaying,
                                            onOpenFile = { playFile(it) },
                                            onOpenFolder = { folder ->
                                                currentDirectory = folder
                                                currentView = MainView.Browser
                                            },
                                            onNavigateToView = { view -> currentView = view },
                                            onOpenSystemFileChooser = { openDesktopFileChooser { playFile(it) } }
                                        )
                                    }

                                    MainView.Browser -> {
                                        DesktopFileBrowserScreen(
                                            currentDirectory = currentDirectory,
                                            onDirectoryChanged = { currentDirectory = it },
                                            currentPlayingFile = session.currentFile,
                                            isPlaying = session.isPlaying,
                                            onFileSelected = { playFile(it) }
                                        )
                                    }

                                    MainView.Playlists -> {
                                        DesktopPlaylistsScreen(
                                            session = session,
                                            onFileSelected = { playFile(it) }
                                        )
                                    }

                                    MainView.Network -> {
                                        DesktopFileBrowserScreen(
                                            currentDirectory = currentDirectory,
                                            onDirectoryChanged = { currentDirectory = it },
                                            currentPlayingFile = session.currentFile,
                                            isPlaying = session.isPlaying,
                                            onFileSelected = { playFile(it) }
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
