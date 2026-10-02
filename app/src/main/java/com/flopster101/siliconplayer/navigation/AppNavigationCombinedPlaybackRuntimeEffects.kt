package com.flopster101.siliconplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppNavigationCombinedPlaybackRuntimeEffects(
    context: Context,
    prefs: SharedPreferences,
    appScope: CoroutineScope,
    settingsStates: AppNavigationSettingsStates,
    runtimeDelegates: AppNavigationRuntimeDelegates,
    playbackStateDelegates: AppNavigationPlaybackStateDelegates,
    playbackSessionCoordinator: PlaybackSessionCoordinator,
    selectedFile: File?,
    currentPlaybackRequestUrl: String?,
    isPlaying: Boolean,
    playerTransitionIsAnyAnimating: Boolean,
    deferredPlaybackSeek: DeferredPlaybackSeek?,
    seekInProgress: Boolean,
    seekStartedAtMs: Long,
    seekRequestedAtMs: Long,
    seekUiBusyThresholdMs: Long,
    effectiveDuration: Double,
    playlistDurationOverride: Double?,
    subtuneCount: Int,
    currentSubtuneIndex: Int,
    activeRepeatMode: RepeatMode,
    preferredRepeatMode: RepeatMode,
    persistRepeatMode: Boolean,
    nextPlatformHandoffPath: () -> String?,
    metadataTitle: String,
    metadataArtist: String,
    metadataAlbum: String,
    effectiveMetadataTitle: String,
    effectiveMetadataArtist: String,
    lastBrowserLocationId: String?,
    isLocalPlayableFile: (File?) -> Boolean,
    isPlayerSurfaceVisible: Boolean,
    autoPlayOnTrackSelect: Boolean,
    openPlayerOnTrackSelect: Boolean,
    autoPlayNextTrackOnEnd: Boolean,
    preloadNextCachedRemoteTrack: Boolean,
    playlistWrapNavigation: Boolean,
    previousRestartsAfterThreshold: Boolean,
    fadePauseResume: Boolean,
    pressBackTwiceToExit: Boolean,
    rememberBrowserLocation: Boolean,
    showParentDirectoryEntry: Boolean,
    showFileIconChipBackground: Boolean,
    sortArchivesBeforeFiles: Boolean,
    browserNameSortMode: BrowserNameSortMode,
    artworkReloadToken: Int,
    unknownTrackDurationSeconds: Int,
    notificationOpenSignal: Int,
    readNativeTrackSnapshot: () -> NativeTrackSnapshot,
    addRecentPlayedTrackFromPlaybackContext: (String, String?, String?, String?) -> Unit,
    playAdjacentActivePlaylistEntryAction: (Int, Boolean?, Boolean) -> Boolean,
    applyNetworkSourceMetadata: (String, String?, String?) -> Unit,
    onSeekInProgressChanged: (Boolean) -> Unit,
    onDeferredPlaybackSeekCleared: () -> Unit,
    onSeekStartedAtMsChanged: (Long) -> Unit,
    onSeekRequestedAtMsChanged: (Long) -> Unit,
    onSeekUiBusyChanged: (Boolean) -> Unit,
    onDurationChanged: (Double) -> Unit,
    onPositionChanged: (Double) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onMetadataTitleChanged: (String) -> Unit,
    onMetadataArtistChanged: (String) -> Unit,
    onMetadataAlbumChanged: (String) -> Unit,
    onMetadataSampleRateChanged: (Int) -> Unit,
    onMetadataChannelCountChanged: (Int) -> Unit,
    onMetadataBitDepthLabelChanged: (String) -> Unit,
    onLastUsedCoreNameChanged: (String?) -> Unit,
    onSubtuneCountChanged: (Int) -> Unit,
    onCurrentSubtuneIndexChanged: (Int) -> Unit,
    onRepeatModeCapabilitiesFlagsChanged: (Int) -> Unit,
    onPlaybackCapabilitiesFlagsChanged: (Int) -> Unit,
    onArtworkBitmapChanged: (ImageBitmap?) -> Unit,
    onArtworkResolvedTrackKeyChanged: (String?) -> Unit,
    resetSubtuneUiState: () -> Unit,
    onRememberBrowserLocationCleared: () -> Unit
) {
    AppNavigationPlaybackPollEffects(
        selectedFile = selectedFile,
        isPlayingProvider = { isPlaying },
        selectedFileProvider = { selectedFile },
        isAnimatingProvider = { playerTransitionIsAnyAnimating },
        deferredPlaybackSeekProvider = { deferredPlaybackSeek },
        seekInProgress = seekInProgress,
        seekStartedAtMs = seekStartedAtMs,
        seekRequestedAtMs = seekRequestedAtMs,
        seekUiBusyThresholdMs = seekUiBusyThresholdMs,
        duration = effectiveDuration,
        durationOverrideSeconds = playlistDurationOverride,
        subtuneCountProvider = { subtuneCount },
        currentSubtuneIndexProvider = { currentSubtuneIndex },
        activeRepeatModeProvider = { activeRepeatMode },
        nextTrackPathProvider = nextPlatformHandoffPath,
        currentPlaybackSourceIdProvider = { settingsStates.currentPlaybackSourceId.value },
        playbackWatchPath = settingsStates.playbackWatchPath.value,
        metadataTitleProvider = { metadataTitle },
        metadataArtistProvider = { metadataArtist },
        lastBrowserLocationId = lastBrowserLocationId,
        onSeekInProgressChanged = { inProgress ->
            onSeekInProgressChanged(inProgress)
            if (!inProgress) onDeferredPlaybackSeekCleared()
        },
        onSeekStartedAtMsChanged = onSeekStartedAtMsChanged,
        onSeekRequestedAtMsChanged = onSeekRequestedAtMsChanged,
        onSeekUiBusyChanged = onSeekUiBusyChanged,
        onDurationChanged = onDurationChanged,
        onPositionChanged = onPositionChanged,
        onIsPlayingChanged = onIsPlayingChanged,
        onPlaybackWatchPathChanged = { settingsStates.playbackWatchPath.value = it },
        onMetadataTitleChanged = onMetadataTitleChanged,
        onMetadataArtistChanged = onMetadataArtistChanged,
        onSubtuneCursorChanged = { _ ->
            playbackStateDelegates.applyNativeTrackSnapshot(readNativeTrackSnapshot())
            runtimeDelegates.refreshSubtuneState()
            runtimeDelegates.refreshRepeatModeForTrack()
        },
        onAddRecentPlayedTrack = addRecentPlayedTrackFromPlaybackContext,
        onPlayAdjacentTrack = playAdjacentActivePlaylistEntryAction,
        onRestartCurrentTrack = {
            onPositionChanged(0.0)
            appScope.launch {
                withContext(Dispatchers.PlaybackIo) {
                    NativeBridge.seekTo(0.0)
                }
                runtimeDelegates.syncPlaybackService()
            }
        },
        onStopPlaybackAndUnload = {
            stopAndEmptyTrackAction(context, playbackStateDelegates)
        },
        isLocalPlayableFile = isLocalPlayableFile,
        onMetadataAlbumChanged = onMetadataAlbumChanged,
        metadataAlbumProvider = { metadataAlbum },
        onMetadataSampleRateChanged = onMetadataSampleRateChanged,
        onMetadataChannelCountChanged = onMetadataChannelCountChanged,
        onMetadataBitDepthLabelChanged = onMetadataBitDepthLabelChanged,
        onLastUsedCoreNameChanged = onLastUsedCoreNameChanged,
        onSubtuneCountChanged = onSubtuneCountChanged,
        onCurrentSubtuneIndexChanged = onCurrentSubtuneIndexChanged,
        onRepeatModeCapabilitiesFlagsChanged = onRepeatModeCapabilitiesFlagsChanged,
        onPlaybackCapabilitiesFlagsChanged = onPlaybackCapabilitiesFlagsChanged,
    )

    AppNavigationTrackPreferenceEffects(
        context = context,
        prefs = prefs,
        selectedFile = selectedFile,
        currentPlaybackSourceId = settingsStates.currentPlaybackSourceId.value,
        currentPlaybackRequestUrl = currentPlaybackRequestUrl,
        artworkReloadToken = artworkReloadToken,
        preferredRepeatMode = preferredRepeatMode,
        isPlayerSurfaceVisible = isPlayerSurfaceVisible,
        autoPlayOnTrackSelect = autoPlayOnTrackSelect,
        openPlayerOnTrackSelect = openPlayerOnTrackSelect,
        autoPlayNextTrackOnEnd = autoPlayNextTrackOnEnd,
        preloadNextCachedRemoteTrack = preloadNextCachedRemoteTrack,
        playlistWrapNavigation = playlistWrapNavigation,
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        fadePauseResume = fadePauseResume,
        pressBackTwiceToExit = pressBackTwiceToExit,
        rememberBrowserLocation = rememberBrowserLocation,
        showParentDirectoryEntry = showParentDirectoryEntry,
        showFileIconChipBackground = showFileIconChipBackground,
        sortArchivesBeforeFiles = sortArchivesBeforeFiles,
        browserNameSortMode = browserNameSortMode,
        onArtworkBitmapChanged = onArtworkBitmapChanged,
        onArtworkResolvedTrackKeyChanged = onArtworkResolvedTrackKeyChanged,
        refreshRepeatModeForTrack = { runtimeDelegates.refreshRepeatModeForTrack() },
        refreshSubtuneState = { runtimeDelegates.refreshSubtuneState() },
        resetSubtuneUiState = resetSubtuneUiState,
        onRememberBrowserLocationCleared = onRememberBrowserLocationCleared
    )

    AppNavigationCoreEffectsFromSettingsStates(
        prefs = prefs,
        settingsStates = settingsStates,
        unknownTrackDurationSeconds = unknownTrackDurationSeconds,
        applyCoreOptionWithPolicyFn = { coreName, optionName, optionValue, policy, optionLabel ->
            playbackStateDelegates.applyCoreOptionWithPolicy(
                coreName = coreName,
                optionName = optionName,
                optionValue = optionValue,
                policy = policy,
                optionLabel = optionLabel
            )
        }
    )

    AppNavigationPlaybackEffects(
        context = context,
        prefs = prefs,
        respondHeadphoneMediaButtons = settingsStates.respondHeadphoneMediaButtons.value,
        pauseOnHeadphoneDisconnect = settingsStates.pauseOnHeadphoneDisconnect.value,
        audioBackendPreference = settingsStates.audioBackendPreference.value,
        audioPerformanceMode = settingsStates.audioPerformanceMode.value,
        audioBufferPreset = settingsStates.audioBufferPreset.value,
        audioResamplerPreference = settingsStates.audioResamplerPreference.value,
        audioOutputLimiterEnabled = settingsStates.audioOutputLimiterEnabled.value,
        lookaheadClipperMode = settingsStates.lookaheadClipperMode.value,
        multiChannelOutputMode = settingsStates.multiChannelOutputMode.value,
        audioAllowBackendFallback = settingsStates.audioAllowBackendFallback.value,
        bitPerfectUsbAudio = settingsStates.bitPerfectUsbAudio.value,
        pendingSoxExperimentalDialog = settingsStates.pendingSoxExperimentalDialog.value,
        onPendingSoxExperimentalDialogChanged = { settingsStates.pendingSoxExperimentalDialog.value = it },
        onShowSoxExperimentalDialogChanged = { settingsStates.showSoxExperimentalDialog.value = it },
        openPlayerFromNotification = settingsStates.openPlayerFromNotification.value,
        persistRepeatMode = persistRepeatMode,
        preferredRepeatMode = preferredRepeatMode,
        selectedFile = selectedFile,
        currentPlaybackSourceId = settingsStates.currentPlaybackSourceId.value,
        isPlaying = isPlaying,
        metadataTitle = effectiveMetadataTitle,
        metadataArtist = effectiveMetadataArtist,
        duration = effectiveDuration,
        notificationOpenSignal = notificationOpenSignal,
        syncPlaybackService = playbackSessionCoordinator.syncPlaybackService,
        restorePlayerStateFromSessionAndNative = playbackSessionCoordinator.restorePlayerStateFromSessionAndNative
    )

    LaunchedEffect(settingsStates.currentPlaybackSourceId.value, metadataTitle, metadataArtist) {
        val sourceId = settingsStates.currentPlaybackSourceId.value ?: return@LaunchedEffect
        applyNetworkSourceMetadata(sourceId, metadataTitle, metadataArtist)
    }
}
