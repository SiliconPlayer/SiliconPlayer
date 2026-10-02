package com.flopster101.siliconplayer

import android.content.Context
import android.widget.Toast
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AppNavigationPlaybackNavigationActions(
    val playAdjacentActivePlaylistEntry: (Int, Boolean?, Boolean) -> Boolean,
    val playAdjacentTrackFromUi: (Int, Boolean) -> Boolean,
    val playPreviousTrackFromUi: () -> Boolean,
    val nextPlatformHandoffPath: () -> String?
)

internal fun buildAppNavigationPlaybackNavigationActions(
    context: Context,
    appScope: CoroutineScope,
    activeRepeatModeProvider: () -> RepeatMode,
    activePlaylistProvider: () -> StoredPlaylist?,
    currentPlaylistNavigationEntryIdProvider: () -> String?,
    usesSelfContainedPlaylistQueueProvider: () -> Boolean,
    playlistWrapNavigationProvider: () -> Boolean,
    previousRestartsAfterThresholdProvider: () -> Boolean,
    selectedFileProvider: () -> File?,
    visiblePlayableFilesProvider: () -> List<File>,
    positionProvider: () -> Double,
    onPositionChanged: (Double) -> Unit,
    repository: com.flopster101.siliconplayer.data.FileRepository,
    playlistLibraryStateProvider: () -> PlaylistLibraryState,
    onPlaylistLibraryStateChanged: (PlaylistLibraryState) -> Unit,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onShowPlaylistSelectorDialogChanged: (Boolean) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (PendingPlaylistSubtuneSelection?) -> Unit,
    trackLoadDelegates: AppNavigationTrackLoadDelegates,
    manualOpenDelegates: AppNavigationManualOpenDelegates,
    trackNavDelegates: com.flopster101.siliconplayer.playback.AppNavigationTrackNavDelegates,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates,
    syncPlaybackService: () -> Unit,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    isPlayerExpandedProvider: () -> Boolean,
    currentPlaybackSourceIdProvider: () -> String?
): AppNavigationPlaybackNavigationActions {
    val playAdjacentActivePlaylistEntryAction: (Int, Boolean?, Boolean) -> Boolean =
        { offset, wrapOverride, notifyWrap ->
            val currentPlaylistEntryId = currentPlaylistNavigationEntryIdProvider()
            val activePlaylist = activePlaylistProvider()
            val playlistWrapNavigation = playlistWrapNavigationProvider()
            val isPlayerExpanded = isPlayerExpandedProvider()
            if (
                activePlaylist?.entries?.isNotEmpty() == true &&
                    !currentPlaylistEntryId.isNullOrBlank()
            ) {
                if (usesSelfContainedPlaylistQueueProvider()) {
                    playAdjacentPlaylistEntry(
                        context = context,
                        activePlaylist = activePlaylist,
                        currentEntryId = currentPlaylistEntryId,
                        offset = offset,
                        wrapOverride = wrapOverride,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = notifyWrap,
                        expandOverride = isPlayerExpanded,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        onActivePlaylistChanged = onActivePlaylistChanged,
                        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
                        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
                    )
                } else {
                    playAdjacentBrowserFileFromAnchor(
                        context = context,
                        anchorPath = activePlaylist?.sourceIdHint,
                        offset = offset,
                        wrapOverride = wrapOverride,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = notifyWrap,
                        activePlaylist = activePlaylist,
                        repository = repository,
                        visiblePlayableFiles = visiblePlayableFilesProvider(),
                        playlistLibraryState = playlistLibraryStateProvider(),
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        expandOverride = isPlayerExpanded,
                        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                        onActivePlaylistChanged = onActivePlaylistChanged,
                        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
                        onShowPlaylistSelectorDialogChanged = onShowPlaylistSelectorDialogChanged,
                        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
                    ) || trackNavDelegates.playAdjacentTrack(
                        offset = offset,
                        notifyWrap = notifyWrap,
                        wrapOverride = wrapOverride
                    )
                }
            } else {
                trackNavDelegates.playAdjacentTrack(
                    offset = offset,
                    notifyWrap = notifyWrap,
                    wrapOverride = wrapOverride
                )
            }
        }

    val playAdjacentTrackFromUiAction: (Int, Boolean) -> Boolean = { offset, stopAtBoundary ->
        val activeRepeatMode = activeRepeatModeProvider()
        val wrapAtBoundary = activeRepeatMode != RepeatMode.None
        val activePlaylist = activePlaylistProvider()
        val currentPlaylistEntryId = currentPlaylistNavigationEntryIdProvider()
        val playlistWrapNavigation = playlistWrapNavigationProvider()
        val isPlayerExpanded = isPlayerExpandedProvider()
        val visiblePlayableFiles = visiblePlayableFilesProvider()
        val playlistLibraryState = playlistLibraryStateProvider()
        val moved = if (
            activePlaylist?.entries?.isNotEmpty() == true &&
                !currentPlaylistEntryId.isNullOrBlank()
        ) {
            playAdjacentPlaylistEntry(
                context = context,
                activePlaylist = activePlaylist,
                currentEntryId = currentPlaylistEntryId,
                offset = offset,
                wrapOverride = false,
                playlistWrapNavigation = playlistWrapNavigation,
                notifyWrap = false,
                expandOverride = isPlayerExpanded,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                autoPlayOnTrackSelect = autoPlayOnTrackSelect,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                onActivePlaylistChanged = onActivePlaylistChanged,
                onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
                onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
            ) || playAdjacentBrowserFileFromAnchor(
                context = context,
                anchorPath = activePlaylist?.sourceIdHint,
                offset = offset,
                wrapOverride = wrapAtBoundary,
                playlistWrapNavigation = playlistWrapNavigation,
                notifyWrap = true,
                activePlaylist = activePlaylist,
                repository = repository,
                visiblePlayableFiles = visiblePlayableFiles,
                playlistLibraryState = playlistLibraryState,
                trackLoadDelegates = trackLoadDelegates,
                manualOpenDelegates = manualOpenDelegates,
                openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                expandOverride = isPlayerExpanded,
                onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                onActivePlaylistChanged = onActivePlaylistChanged,
                onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
                onShowPlaylistSelectorDialogChanged = onShowPlaylistSelectorDialogChanged,
                onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
            )
        } else {
            val selectedFile = selectedFileProvider()
            val activeSourceId = currentPlaybackSourceIdProvider() ?: selectedFile?.absolutePath
            if (isRemoteQueuePlaybackSource(activeSourceId)) {
                false
            } else {
                val localAnchorPath = selectedFile?.absolutePath
                if (localAnchorPath != null) {
                    playAdjacentBrowserFileFromAnchor(
                        context = context,
                        anchorPath = localAnchorPath,
                        offset = offset,
                        wrapOverride = wrapAtBoundary,
                        playlistWrapNavigation = playlistWrapNavigation,
                        notifyWrap = true,
                        activePlaylist = null,
                        repository = repository,
                        visiblePlayableFiles = visiblePlayableFiles,
                        playlistLibraryState = playlistLibraryState,
                        trackLoadDelegates = trackLoadDelegates,
                        manualOpenDelegates = manualOpenDelegates,
                        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
                        expandOverride = isPlayerExpanded,
                        onPlaylistLibraryStateChanged = onPlaylistLibraryStateChanged,
                        onActivePlaylistChanged = onActivePlaylistChanged,
                        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
                        onShowPlaylistSelectorDialogChanged = onShowPlaylistSelectorDialogChanged,
                        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged
                    )
                } else {
                    false
                }
            }
        } || trackNavDelegates.playAdjacentTrack(
            offset = offset,
            notifyWrap = true,
            wrapOverride = wrapAtBoundary
        )
        if (!moved && stopAtBoundary && offset > 0 && !wrapAtBoundary) {
            stopAndEmptyTrackAction(context, playbackStateDelegates)
            true
        } else {
            moved
        }
    }

    val playPreviousTrackFromUiAction: () -> Boolean = {
        val restartCurrentSelection = {
            onPositionChanged(0.0)
            appScope.launch {
                withContext(Dispatchers.PlaybackIo) {
                    NativeBridge.seekTo(0.0)
                }
                syncPlaybackService()
            }
        }
        val currentEntryId = currentPlaylistNavigationEntryIdProvider()
        val activePlaylist = activePlaylistProvider()
        val playlistEntries = activePlaylist?.entries
        val usePlaylistNavigation = playlistEntries?.isNotEmpty() == true &&
            !currentEntryId.isNullOrBlank()
        if (!usePlaylistNavigation) {
            trackNavDelegates.handlePreviousTrackAction()
        } else {
            val currentIndex = playlistEntries
                ?.indexOfFirst { entry -> entry.id == currentEntryId }
                ?: -1
            val playlistWrapNavigation = playlistWrapNavigationProvider()
            val hasPreviousTrack = if (playlistWrapNavigation) {
                currentIndex >= 0 && playlistEntries.isNotEmpty()
            } else {
                currentIndex > 0 && playlistEntries.isNotEmpty()
            }
            val selectedFile = selectedFileProvider()
            when (
                resolvePreviousTrackAction(
                    previousRestartsAfterThreshold = previousRestartsAfterThresholdProvider(),
                    hasTrackLoaded = selectedFile != null,
                    positionSeconds = positionProvider(),
                    hasPreviousTrack = hasPreviousTrack
                )
            ) {
                PreviousTrackAction.RestartCurrent -> {
                    restartCurrentSelection()
                    true
                }

                PreviousTrackAction.PlayPreviousTrack -> {
                    val moved = playAdjacentTrackFromUiAction(-1, false)
                    if (moved) {
                        true
                    } else if (selectedFile != null) {
                        restartCurrentSelection()
                        true
                    } else {
                        false
                    }
                }

                PreviousTrackAction.NoAction -> {
                    false
                }
            }
        }
    }

    val nextPlatformHandoffPath: () -> String? = {
        val wrap = playlistWrapNavigationProvider() || activeRepeatModeProvider() == RepeatMode.Playlist
        val activePlaylist = activePlaylistProvider()
        val entries = activePlaylist?.entries
        val entryId = currentPlaylistNavigationEntryIdProvider()
        val usesSelfContainedPlaylistQueue = usesSelfContainedPlaylistQueueProvider()
        val visiblePlayableFiles = visiblePlayableFilesProvider()
        val selectedFile = selectedFileProvider()
        if (usesSelfContainedPlaylistQueue && !entries.isNullOrEmpty() && !entryId.isNullOrBlank()) {
            val index = entries.indexOfFirst { it.id == entryId }
            val size = entries.size
            val next = if (index >= 0) {
                if (wrap) entries[(index + 1) % size] else entries.getOrNull(index + 1)
            } else null
            next?.source?.let { src -> File(src).takeIf { it.isFile }?.absolutePath }
        } else {
            val index = currentTrackIndexForList(selectedFile, visiblePlayableFiles)
            val size = visiblePlayableFiles.size
            if (index >= 0 && size > 0) {
                val target = if (wrap) visiblePlayableFiles[(index + 1) % size] else visiblePlayableFiles.getOrNull(index + 1)
                target?.absolutePath
            } else null
        }
    }

    return AppNavigationPlaybackNavigationActions(
        playAdjacentActivePlaylistEntry = playAdjacentActivePlaylistEntryAction,
        playAdjacentTrackFromUi = playAdjacentTrackFromUiAction,
        playPreviousTrackFromUi = playPreviousTrackFromUiAction,
        nextPlatformHandoffPath = nextPlatformHandoffPath
    )
}
