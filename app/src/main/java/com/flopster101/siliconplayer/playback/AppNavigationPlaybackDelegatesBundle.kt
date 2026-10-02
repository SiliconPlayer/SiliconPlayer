package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import com.flopster101.siliconplayer.data.FileRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

internal data class AppNavigationPlaybackDelegatesBundle(
    val playbackStateDelegates: AppNavigationPlaybackStateDelegates,
    val trackLoadDelegates: AppNavigationTrackLoadDelegates,
    val playbackSessionCoordinator: PlaybackSessionCoordinator,
    val manualOpenDelegates: AppNavigationManualOpenDelegates
)

internal fun buildAppNavigationPlaybackDelegatesBundle(
    context: Context,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    repository: FileRepository,
    storageDescriptors: List<StorageDescriptor>,
    runtimeDelegates: AppNavigationRuntimeDelegates,
    selectedFileProvider: () -> File?,
    onSelectedFileChanged: (File?) -> Unit,
    currentPlaybackSourceIdProvider: () -> String?,
    onCurrentPlaybackSourceIdChanged: (String?) -> Unit,
    currentPlaybackRequestUrlProvider: () -> String?,
    onCurrentPlaybackRequestUrlChanged: (String?) -> Unit,
    isPlayingProvider: () -> Boolean,
    onIsPlayingChanged: (Boolean) -> Unit,
    lastBrowserLocationIdProvider: () -> String?,
    isLocalPlayableFile: (File?) -> Boolean,
    metadataTitleProvider: () -> String,
    metadataArtistProvider: () -> String,
    readNativeTrackSnapshot: () -> NativeTrackSnapshot,
    ignoreCoreVolumeForCurrentSongProvider: () -> Boolean,
    onLastUsedCoreNameChanged: (String?) -> Unit,
    onPluginVolumeDbChanged: (Float) -> Unit,
    onDurationChanged: (Double) -> Unit,
    onPositionChanged: (Double) -> Unit,
    onSeekInProgressChanged: (Boolean) -> Unit,
    onSeekUiBusyChanged: (Boolean) -> Unit,
    onSeekStartedAtMsChanged: (Long) -> Unit,
    onSeekRequestedAtMsChanged: (Long) -> Unit,
    onMetadataTitleChanged: (String) -> Unit,
    onMetadataArtistChanged: (String) -> Unit,
    onMetadataAlbumChanged: (String) -> Unit,
    onMetadataSampleRateChanged: (Int) -> Unit,
    onMetadataChannelCountChanged: (Int) -> Unit,
    onMetadataBitDepthLabelChanged: (String) -> Unit,
    onSubtuneCountChanged: (Int) -> Unit,
    onCurrentSubtuneIndexChanged: (Int) -> Unit,
    onSubtuneEntriesCleared: () -> Unit,
    onShowSubtuneSelectorDialogChanged: (Boolean) -> Unit,
    onRepeatModeCapabilitiesFlagsChanged: (Int) -> Unit,
    onPlaybackCapabilitiesFlagsChanged: (Int) -> Unit,
    onArtworkCleared: () -> Unit,
    onIgnoreCoreVolumeForSongChanged: (Boolean) -> Unit,
    onLastStoppedChanged: (File?, String?) -> Unit,
    addRecentPlayedTrackFromPlaybackContext: (String, String?, String?, String?) -> Unit,
    scheduleRecentTrackMetadataRefreshFromPlaybackContext: (String, String?) -> Unit,
    loadSongVolumeForFile: (String) -> Unit,
    onSongVolumeDbChanged: (Float) -> Unit,
    onActivePlaylistChanged: (StoredPlaylist?) -> Unit,
    onActivePlaylistEntryIdChanged: (String?) -> Unit,
    onActivePlaylistShuffleActiveChanged: (Boolean) -> Unit,
    onPendingPlaylistSubtuneSelectionChanged: (String?, Int?) -> Unit,
    onVisiblePlayableFilesChanged: (List<File>) -> Unit,
    onPlayerSurfaceVisibleChanged: (Boolean) -> Unit,
    isPlayerExpandedProvider: () -> Boolean,
    onPlayerExpandedChanged: (Boolean) -> Unit,
    onPlaybackStartInProgressChanged: (Boolean) -> Unit,
    onDeferredPlaybackSeekChanged: (DeferredPlaybackSeek?) -> Unit,
    openPlayerOnTrackSelectProvider: () -> Boolean,
    activeRepeatModeProvider: () -> RepeatMode,
    urlCacheMaxTracksProvider: () -> Int,
    urlCacheMaxBytesProvider: () -> Long,
    currentRemoteLoadJobProvider: () -> Job?,
    onRemoteLoadUiStateChanged: (RemoteLoadUiState?) -> Unit,
    onRemoteLoadJobChanged: (Job?) -> Unit,
    applyRepeatModeToNative: (RepeatMode) -> Unit,
    browserNavigator: BrowserNavigatorState,
    onCurrentViewChanged: (MainView) -> Unit
): AppNavigationPlaybackDelegatesBundle {
    val playbackStateDelegates = AppNavigationPlaybackStateDelegates(
        context = context,
        prefs = prefs,
        selectedFileProvider = selectedFileProvider,
        onSelectedFileChanged = onSelectedFileChanged,
        currentPlaybackSourceIdProvider = currentPlaybackSourceIdProvider,
        currentPlaybackRequestUrlProvider = currentPlaybackRequestUrlProvider,
        onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
        isPlayingProvider = isPlayingProvider,
        lastBrowserLocationIdProvider = lastBrowserLocationIdProvider,
        isLocalPlayableFile = isLocalPlayableFile,
        metadataTitleProvider = metadataTitleProvider,
        metadataArtistProvider = metadataArtistProvider,
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        addRecentPlayedTrack = addRecentPlayedTrackFromPlaybackContext,
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        readNativeTrackSnapshot = readNativeTrackSnapshot,
        ignoreCoreVolumeForCurrentSongProvider = ignoreCoreVolumeForCurrentSongProvider,
        onLastUsedCoreNameChanged = onLastUsedCoreNameChanged,
        onPluginVolumeDbChanged = onPluginVolumeDbChanged,
        onPluginGainChanged = { NativeBridge.setPluginGain(it) },
        onDurationChanged = onDurationChanged,
        onPositionChanged = onPositionChanged,
        onIsPlayingChanged = onIsPlayingChanged,
        onSeekInProgressChanged = onSeekInProgressChanged,
        onSeekUiBusyChanged = onSeekUiBusyChanged,
        onSeekStartedAtMsChanged = onSeekStartedAtMsChanged,
        onSeekRequestedAtMsChanged = onSeekRequestedAtMsChanged,
        onMetadataTitleChanged = onMetadataTitleChanged,
        onMetadataArtistChanged = onMetadataArtistChanged,
        onMetadataSampleRateChanged = onMetadataSampleRateChanged,
        onMetadataChannelCountChanged = onMetadataChannelCountChanged,
        onMetadataBitDepthLabelChanged = onMetadataBitDepthLabelChanged,
        onSubtuneCountChanged = onSubtuneCountChanged,
        onCurrentSubtuneIndexChanged = onCurrentSubtuneIndexChanged,
        onSubtuneEntriesCleared = onSubtuneEntriesCleared,
        onShowSubtuneSelectorDialogChanged = onShowSubtuneSelectorDialogChanged,
        onRepeatModeCapabilitiesFlagsChanged = onRepeatModeCapabilitiesFlagsChanged,
        onPlaybackCapabilitiesFlagsChanged = onPlaybackCapabilitiesFlagsChanged,
        onArtworkBitmapCleared = onArtworkCleared,
        onIgnoreCoreVolumeForSongChanged = onIgnoreCoreVolumeForSongChanged,
        onLastStoppedChanged = onLastStoppedChanged,
        onStopEngine = { NativeBridge.releaseCurrentDecoder() },
        onMetadataAlbumChanged = onMetadataAlbumChanged
    )

    val trackLoadDelegates = AppNavigationTrackLoadDelegates(
        appScope = appScope,
        context = context,
        prefs = prefs,
        repository = repository,
        cacheRootProvider = { File(context.cacheDir, REMOTE_SOURCE_CACHE_DIR) },
        lastBrowserLocationIdProvider = lastBrowserLocationIdProvider,
        isPlayingProvider = isPlayingProvider,
        onResetPlayback = {
            onDeferredPlaybackSeekChanged(null)
            playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = false)
        },
        onSelectedFileChanged = onSelectedFileChanged,
        onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
        onCurrentPlaybackRequestUrlChanged = onCurrentPlaybackRequestUrlChanged,
        onActivePlaylistChanged = onActivePlaylistChanged,
        onActivePlaylistEntryIdChanged = onActivePlaylistEntryIdChanged,
        onActivePlaylistShuffleActiveChanged = onActivePlaylistShuffleActiveChanged,
        onPendingPlaylistSubtuneSelectionChanged = onPendingPlaylistSubtuneSelectionChanged,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
        loadSongVolumeForFile = loadSongVolumeForFile,
        onSongVolumeDbChanged = onSongVolumeDbChanged,
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        onResolvedDecoderState = { decoderName ->
            playbackStateDelegates.applyResolvedDecoderState(decoderName)
        },
        readNativeTrackSnapshot = readNativeTrackSnapshot,
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = onPositionChanged,
        onArtworkBitmapCleared = onArtworkCleared,
        onIsPlayingChanged = onIsPlayingChanged,
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = addRecentPlayedTrackFromPlaybackContext,
        metadataTitleProvider = metadataTitleProvider,
        metadataArtistProvider = metadataArtistProvider,
        onStartEngine = { NativeBridge.startEngine() },
        scheduleRecentTrackMetadataRefresh = scheduleRecentTrackMetadataRefreshFromPlaybackContext,
        onPlayerExpandedChanged = onPlayerExpandedChanged,
        onPlaybackStartInProgressChanged = onPlaybackStartInProgressChanged,
        syncPlaybackService = { runtimeDelegates.syncPlaybackService() },
        onDeferredPlaybackSeekChanged = onDeferredPlaybackSeekChanged
    )

    val playbackSessionCoordinator = buildPlaybackSessionCoordinator(
        runtimeDelegates = runtimeDelegates,
        trackLoadDelegates = trackLoadDelegates
    )

    val manualOpenDelegates = AppNavigationManualOpenDelegates(
        context = context,
        appScope = appScope,
        repository = repository,
        storageDescriptors = storageDescriptors,
        openPlayerOnTrackSelectProvider = openPlayerOnTrackSelectProvider,
        isPlayerExpandedProvider = isPlayerExpandedProvider,
        activeRepeatModeProvider = activeRepeatModeProvider,
        selectedFileAbsolutePathProvider = { selectedFileProvider()?.absolutePath },
        urlCacheMaxTracksProvider = urlCacheMaxTracksProvider,
        urlCacheMaxBytesProvider = urlCacheMaxBytesProvider,
        currentRemoteLoadJobProvider = currentRemoteLoadJobProvider,
        onRemoteLoadUiStateChanged = onRemoteLoadUiStateChanged,
        onRemoteLoadJobChanged = onRemoteLoadJobChanged,
        onResetPlayback = { playbackStateDelegates.resetAndOptionallyKeepLastTrack(keepLastTrack = false) },
        onSelectedFileChanged = onSelectedFileChanged,
        onCurrentPlaybackSourceIdChanged = onCurrentPlaybackSourceIdChanged,
        onCurrentPlaybackRequestUrlChanged = onCurrentPlaybackRequestUrlChanged,
        onVisiblePlayableFilesChanged = onVisiblePlayableFilesChanged,
        onPlayerSurfaceVisibleChanged = onPlayerSurfaceVisibleChanged,
        onSongVolumeDbChanged = onSongVolumeDbChanged,
        onSongGainChanged = { NativeBridge.setSongGain(it) },
        onResolvedDecoderState = { decoderName ->
            playbackStateDelegates.applyResolvedDecoderState(decoderName)
        },
        applyNativeTrackSnapshot = { snapshot -> playbackStateDelegates.applyNativeTrackSnapshot(snapshot) },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        onPositionChanged = onPositionChanged,
        onArtworkBitmapCleared = onArtworkCleared,
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        onAddRecentPlayedTrack = addRecentPlayedTrackFromPlaybackContext,
        metadataTitleProvider = metadataTitleProvider,
        metadataArtistProvider = metadataArtistProvider,
        applyRepeatModeToNative = applyRepeatModeToNative,
        onStartEngine = { NativeBridge.startEngine() },
        onIsPlayingChanged = onIsPlayingChanged,
        scheduleRecentTrackMetadataRefresh = scheduleRecentTrackMetadataRefreshFromPlaybackContext,
        onPlayerExpandedChanged = onPlayerExpandedChanged,
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        onBrowserLaunchTargetChanged = { launchState ->
            var normalizedLaunchState = launchState
            val isArchiveLogicalLocation = resolveBrowserLocationModel(
                initialLocationId = launchState.locationId,
                initialDirectoryPath = launchState.directoryPath,
                initialSmbSourceNodeId = launchState.smbSourceNodeId,
                initialHttpSourceNodeId = launchState.httpSourceNodeId,
                initialHttpRootPath = launchState.httpRootPath
            ) is BrowserLocationModel.ArchiveLogical
            if (!isArchiveLogicalLocation) {
                normalizedLaunchState = normalizedLaunchState.copy(
                    smbSourceNodeId = null,
                    httpSourceNodeId = null,
                    httpRootPath = null
                )
            }
            browserNavigator.updateLaunchState(normalizedLaunchState)
        },
        onCurrentViewChanged = onCurrentViewChanged,
        onAddRecentFolder = { path, locationId, sourceNodeId ->
            runtimeDelegates.addRecentFolder(path, locationId, sourceNodeId)
        },
        onApplyTrackSelection = { file, autoStart, expandOverride, sourceIdOverride, initialSubtuneIndex ->
            trackLoadDelegates.applyTrackSelection(
                file = file,
                autoStart = autoStart,
                expandOverride = expandOverride,
                sourceIdOverride = sourceIdOverride,
                initialSubtuneIndex = initialSubtuneIndex
            )
        }
    )

    return AppNavigationPlaybackDelegatesBundle(
        playbackStateDelegates = playbackStateDelegates,
        trackLoadDelegates = trackLoadDelegates,
        playbackSessionCoordinator = playbackSessionCoordinator,
        manualOpenDelegates = manualOpenDelegates
    )
}
