package com.flopster101.siliconplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.unit.Dp
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import androidx.compose.ui.unit.dp

private const val SETTINGS_PAGE_NAV_DURATION_MS = 300
private val SETTINGS_PAGE_MAX_WIDTH = 640.dp
internal val SETTINGS_TWO_PANE_LIST_WIDTH = 360.dp
internal val SETTINGS_TWO_PANE_MIN_WIDTH = 900.dp

@Composable
internal fun SettingsRouteContentHost(
    route: SettingsRoute,
    bottomContentPadding: Dp,
    scaffoldPaddingValues: PaddingValues,
    state: SettingsScreenState,
    actions: SettingsScreenActions,
    pluginPriorityEditMode: Boolean,
    onRequestClearAllSettings: () -> Unit,
    onRequestClearPluginSettings: () -> Unit
) {
    val isWatch = com.flopster101.siliconplayer.platform.LocalIsWatchDevice.current
    val isRound = com.flopster101.siliconplayer.platform.LocalIsRoundScreen.current
    val extraBottomPadding = if (isWatch) (if (isRound) 56.dp else 24.dp) else 0.dp
    val platformContent = com.flopster101.siliconplayer.platform.LocalSettingsPlatformContent.current

    var previousRouteNow by remember { mutableStateOf<SettingsRoute?>(null) }
    //Scroll positions of pages we've left; a page only restores when we
    //navigate back to it from a child, so entering it anew always starts at top
    val pageScrollPositions = remember { mutableStateMapOf<SettingsRoute, Int>() }
    LaunchedEffect(route) {
        val last = previousRouteNow
        if (last != null && settingsRouteOrder(route) > settingsRouteOrder(last)) {
            pageScrollPositions.remove(route)
        }
        previousRouteNow = route
    }

    // Two-pane desktop layout: the root sections list stays visible on the
    // left while the selected route renders in the right pane.
    val windowInfo = LocalWindowSizeInfo.current
    val isTwoPane = !isWatch && route != SettingsRoute.Root &&
            windowInfo.screenWidthDp >= SETTINGS_TWO_PANE_MIN_WIDTH.value
    val routePane: @Composable (SettingsRoute) -> Unit = { currentRoute ->
        val scrollState = rememberScrollState()
        DisposableEffect(currentRoute) {
            onDispose { pageScrollPositions[currentRoute] = scrollState.value }
        }
        LaunchedEffect(currentRoute) {
            val saved = pageScrollPositions.remove(currentRoute) ?: 0
            if (saved > 0) {
                snapshotFlow { scrollState.maxValue }
                    .filter { it > 0 }
                    .first()
                scrollState.scrollTo(saved)
            }
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = SETTINGS_PAGE_MAX_WIDTH)
                .fillMaxSize()
                .verticalScroll(state = scrollState)
                .padding(
                    start = if (isWatch) (if (isRound) 14.dp else 8.dp) else 0.dp,
                    end = if (isWatch) (if (isRound) 14.dp else 8.dp) else 0.dp
                )
        ) {
            Spacer(modifier = Modifier.height(if (isWatch) 4.dp else 8.dp))

            SettingsRowsHost {
                when (currentRoute) {
                SettingsRoute.Root -> {
                    RootRouteContent(
                        actions = RootRouteActions(
                            onOpenAudioPlugins = actions.onOpenAudioPlugins,
                            onOpenGeneralAudio = actions.onOpenGeneralAudio,
                            onOpenLibrary = actions.onOpenLibrary,
                            onOpenLibraryScanner = actions.onOpenLibraryScanner,
                            onOpenPlayer = actions.onOpenPlayer,
                            onOpenHome = actions.onOpenHome,
                            onOpenFileBrowser = actions.onOpenFileBrowser,
                            onOpenNetwork = actions.onOpenNetwork,
                            onOpenVisualization = actions.onOpenVisualization,
                            onOpenUrlCache = actions.onOpenUrlCache,
                            onOpenMisc = actions.onOpenMisc,
                            onOpenUi = actions.onOpenUi,
                            onOpenAbout = actions.onOpenAbout,
                            onOpenSettingsPorting = actions.onOpenSettingsPorting,
                            onRequestClearAllSettings = onRequestClearAllSettings
                        )
                    )
                }

                SettingsRoute.AudioPlugins -> {
                    AudioPluginsRouteContent(
                        state = AudioPluginsRouteState(
                            pluginPriorityEditMode = pluginPriorityEditMode
                        ),
                        actions = AudioPluginsRouteActions(
                            onPluginSelected = actions.pluginCoreActions.onPluginSelected,
                            onPluginEnabledChanged = actions.pluginCoreActions.onPluginEnabledChanged,
                            onPluginPriorityOrderChanged = actions.pluginCoreActions.onPluginPriorityOrderChanged,
                            onRequestClearPluginSettings = onRequestClearPluginSettings
                        )
                    )
                }

                SettingsRoute.PluginDetail -> {
                    PluginDetailRouteContent(
                        state = state.toPluginDetailRouteState(),
                        actions = actions.toPluginDetailRouteActions()
                    )
                }

                SettingsRoute.PluginVgmPlayChipSettings -> {
                    PluginVgmPlayChipSettingsRouteContent(
                        state = PluginVgmPlayChipSettingsRouteState(
                            vgmPlayChipCoreSelections = state.pluginCore.vgmPlayChipCoreSelections
                        ),
                        actions = PluginVgmPlayChipSettingsRouteActions(
                            onVgmPlayChipCoreChanged = actions.pluginCoreActions.onVgmPlayChipCoreChanged
                        )
                    )
                }

                SettingsRoute.PluginFfmpeg -> {
                    PluginSampleRateRouteContent(
                        state = PluginSampleRateRouteState(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                            selectedHz = state.pluginCore.ffmpegSampleRateHz,
                            enabled = supportsCustomSampleRate(state.pluginCore.ffmpegCapabilities)
                        ),
                        actions = PluginSampleRateRouteActions(
                            onSelected = actions.pluginCoreActions.onFfmpegSampleRateChanged
                        )
                    )
                }

                SettingsRoute.PluginVgmPlay -> {
                    PluginSampleRateRouteContent(
                        state = PluginSampleRateRouteState(
                            title = "Render sample rate",
                            description = "Preferred internal render sample rate for this core. Audio is resampled to the active output stream rate.",
                            selectedHz = state.pluginCore.vgmPlaySampleRateHz,
                            enabled = supportsCustomSampleRate(state.pluginCore.vgmPlayCapabilities)
                        ),
                        actions = PluginSampleRateRouteActions(
                            onSelected = actions.pluginCoreActions.onVgmPlaySampleRateChanged
                        )
                    )
                }

                SettingsRoute.PluginOpenMpt -> {
                    PluginOpenMptRouteContent(
                        state = PluginOpenMptRouteState(
                            openMptSampleRateHz = state.pluginCore.openMptSampleRateHz,
                            openMptCapabilities = state.pluginCore.openMptCapabilities,
                            openMptStereoSeparationPercent = state.pluginCore.openMptStereoSeparationPercent,
                            openMptStereoSeparationAmigaPercent = state.pluginCore.openMptStereoSeparationAmigaPercent,
                            openMptInterpolationFilterLength = state.pluginCore.openMptInterpolationFilterLength,
                            openMptAmigaResamplerMode = state.pluginCore.openMptAmigaResamplerMode,
                            openMptAmigaResamplerApplyAllModules = state.pluginCore.openMptAmigaResamplerApplyAllModules,
                            openMptVolumeRampingStrength = state.pluginCore.openMptVolumeRampingStrength,
                            openMptFt2XmVolumeRamping = state.pluginCore.openMptFt2XmVolumeRamping,
                            openMptMasterGainMilliBel = state.pluginCore.openMptMasterGainMilliBel,
                            openMptSurroundEnabled = state.pluginCore.openMptSurroundEnabled
                        ),
                        actions = PluginOpenMptRouteActions(
                            onOpenMptStereoSeparationPercentChanged = actions.pluginCoreActions.onOpenMptStereoSeparationPercentChanged,
                            onOpenMptStereoSeparationAmigaPercentChanged = actions.pluginCoreActions.onOpenMptStereoSeparationAmigaPercentChanged,
                            onOpenMptInterpolationFilterLengthChanged = actions.pluginCoreActions.onOpenMptInterpolationFilterLengthChanged,
                            onOpenMptAmigaResamplerModeChanged = actions.pluginCoreActions.onOpenMptAmigaResamplerModeChanged,
                            onOpenMptAmigaResamplerApplyAllModulesChanged = actions.pluginCoreActions.onOpenMptAmigaResamplerApplyAllModulesChanged,
                            onOpenMptVolumeRampingStrengthChanged = actions.pluginCoreActions.onOpenMptVolumeRampingStrengthChanged,
                            onOpenMptFt2XmVolumeRampingChanged = actions.pluginCoreActions.onOpenMptFt2XmVolumeRampingChanged,
                            onOpenMptMasterGainMilliBelChanged = actions.pluginCoreActions.onOpenMptMasterGainMilliBelChanged,
                            onOpenMptSurroundEnabledChanged = actions.pluginCoreActions.onOpenMptSurroundEnabledChanged,
                            onOpenMptSampleRateChanged = actions.pluginCoreActions.onOpenMptSampleRateChanged
                        )
                    )
                }

                SettingsRoute.GeneralAudio -> {
                    GeneralAudioRouteContent(
                        state = GeneralAudioRouteState(
                            respondHeadphoneMediaButtons = state.respondHeadphoneMediaButtons,
                            pauseOnHeadphoneDisconnect = state.pauseOnHeadphoneDisconnect,
                            audioFocusInterrupt = state.audioFocusInterrupt,
                            audioDucking = state.audioDucking,
                            headphoneMediaButtonsAvailable = state.headphoneMediaButtonsAvailable,
                            pauseOnHeadphoneDisconnectAvailable = state.pauseOnHeadphoneDisconnectAvailable,
                            audioFocusInterruptAvailable = state.audioFocusInterruptAvailable,
                            audioDuckingAvailable = state.audioDuckingAvailable,
                            audioBackendPreference = state.audioBackendPreference,
                            audioPerformanceMode = state.audioPerformanceMode,
                            audioBufferPreset = state.audioBufferPreset,
                            audioResamplerPreference = state.audioResamplerPreference,
                            audioOutputLimiterEnabled = state.audioOutputLimiterEnabled,
                            lookaheadClipperMode = state.lookaheadClipperMode,
                            multiChannelOutputMode = state.multiChannelOutputMode,
                            audioAllowBackendFallback = state.audioAllowBackendFallback,
                            bitPerfectUsbAudio = state.bitPerfectUsbAudio
                        ),
                        actions = GeneralAudioRouteActions(
                            onRespondHeadphoneMediaButtonsChanged = actions.onRespondHeadphoneMediaButtonsChanged,
                            onPauseOnHeadphoneDisconnectChanged = actions.onPauseOnHeadphoneDisconnectChanged,
                            onAudioFocusInterruptChanged = actions.onAudioFocusInterruptChanged,
                            onAudioDuckingChanged = actions.onAudioDuckingChanged,
                            onOpenAudioEffects = actions.onOpenAudioEffects,
                            onClearAllAudioParameters = actions.onClearAllAudioParameters,
                            onClearPluginAudioParameters = actions.onClearPluginAudioParameters,
                            onClearSongAudioParameters = actions.onClearSongAudioParameters,
                            onAudioBackendPreferenceChanged = actions.onAudioBackendPreferenceChanged,
                            onAudioPerformanceModeChanged = actions.onAudioPerformanceModeChanged,
                            onAudioBufferPresetChanged = actions.onAudioBufferPresetChanged,
                            onAudioResamplerPreferenceChanged = actions.onAudioResamplerPreferenceChanged,
                            onAudioOutputLimiterEnabledChanged = actions.onAudioOutputLimiterEnabledChanged,
                            onLookaheadClipperModeChanged = actions.onLookaheadClipperModeChanged,
                            onMultiChannelOutputModeChanged = actions.onMultiChannelOutputModeChanged,
                            onAudioAllowBackendFallbackChanged = actions.onAudioAllowBackendFallbackChanged,
                            onBitPerfectUsbAudioChanged = actions.onBitPerfectUsbAudioChanged
                        )
                    )
                }

                SettingsRoute.Library -> {
                    platformContent.LibrarySettingsContent(
                        onOpenScanner = actions.onOpenLibraryScanner
                    )
                }

                SettingsRoute.LibraryScanner -> {
                    platformContent.LibraryScannerContent()
                }

                SettingsRoute.Home -> {
                    HomeRouteContent(
                        state = HomeRouteState(
                            recentFoldersLimit = state.recentFoldersLimit,
                            recentFilesLimit = state.recentFilesLimit,
                            pressBackTwiceToExit = state.pressBackTwiceToExit
                        ),
                        actions = HomeRouteActions(
                            onRecentFoldersLimitChanged = actions.onRecentFoldersLimitChanged,
                            onRecentFilesLimitChanged = actions.onRecentFilesLimitChanged,
                            onPressBackTwiceToExitChanged = actions.onPressBackTwiceToExitChanged
                        )
                    )
                }

                SettingsRoute.FileBrowser -> {
                    FileBrowserRouteContent(
                        state = FileBrowserRouteState(
                            rememberBrowserLocation = state.rememberBrowserLocation,
                            showParentDirectoryEntry = state.showParentDirectoryEntry,
                            showFileIconChipBackground = state.showFileIconChipBackground,
                            sortArchivesBeforeFiles = state.sortArchivesBeforeFiles,
                            browserNameSortMode = state.browserNameSortMode
                        ),
                        actions = FileBrowserRouteActions(
                            onRememberBrowserLocationChanged = actions.onRememberBrowserLocationChanged,
                            onShowParentDirectoryEntryChanged = actions.onShowParentDirectoryEntryChanged,
                            onShowFileIconChipBackgroundChanged = actions.onShowFileIconChipBackgroundChanged,
                            onSortArchivesBeforeFilesChanged = actions.onSortArchivesBeforeFilesChanged,
                            onBrowserNameSortModeChanged = actions.onBrowserNameSortModeChanged
                        )
                    )
                }

                SettingsRoute.Network -> {
                    NetworkRouteContent(
                        actions = NetworkRouteActions(
                            onClearSavedNetworkSources = actions.onClearSavedNetworkSources
                        )
                    )
                }

                SettingsRoute.Player -> {
                    PlayerRouteContent(
                        state = PlayerRouteState(
                            unknownTrackDurationSeconds = state.unknownTrackDurationSeconds,
                            endFadeDurationMs = state.endFadeDurationMs,
                            endFadeCurve = state.endFadeCurve,
                            endFadeApplyToAllTracks = state.endFadeApplyToAllTracks,
                            autoPlayOnTrackSelect = state.autoPlayOnTrackSelect,
                            openPlayerOnTrackSelect = state.openPlayerOnTrackSelect,
                            autoPlayNextTrackOnEnd = state.autoPlayNextTrackOnEnd,
                            preloadNextCachedRemoteTrack = state.preloadNextCachedRemoteTrack,
                            playlistWrapNavigation = state.playlistWrapNavigation,
                            previousRestartsAfterThreshold = state.previousRestartsAfterThreshold,
                            fadePauseResume = state.fadePauseResume,
                            openPlayerFromNotification = state.openPlayerFromNotification,
                            persistRepeatMode = state.persistRepeatMode,
                            keepScreenOn = state.keepScreenOn,
                            openPlayerFromNotificationAvailable = state.openPlayerFromNotificationAvailable,
                            keepScreenOnAvailable = state.keepScreenOnAvailable,
                            playerArtworkCornerRadiusDp = state.playerArtworkCornerRadiusDp,
                            showAudioOutputRouteChip = state.showAudioOutputRouteChip,
                            canvasTapToSeekSeconds = state.canvasTapToSeekSeconds,
                            filenameDisplayMode = state.filenameDisplayMode,
                            filenameOnlyWhenTitleMissing = state.filenameOnlyWhenTitleMissing
                        ),
                        actions = PlayerRouteActions(
                            onUnknownTrackDurationSecondsChanged = actions.onUnknownTrackDurationSecondsChanged,
                            onEndFadeDurationMsChanged = actions.onEndFadeDurationMsChanged,
                            onEndFadeCurveChanged = actions.onEndFadeCurveChanged,
                            onEndFadeApplyToAllTracksChanged = actions.onEndFadeApplyToAllTracksChanged,
                            onAutoPlayOnTrackSelectChanged = actions.onAutoPlayOnTrackSelectChanged,
                            onOpenPlayerOnTrackSelectChanged = actions.onOpenPlayerOnTrackSelectChanged,
                            onAutoPlayNextTrackOnEndChanged = actions.onAutoPlayNextTrackOnEndChanged,
                            onPreloadNextCachedRemoteTrackChanged = actions.onPreloadNextCachedRemoteTrackChanged,
                            onPlaylistWrapNavigationChanged = actions.onPlaylistWrapNavigationChanged,
                            onPreviousRestartsAfterThresholdChanged = actions.onPreviousRestartsAfterThresholdChanged,
                            onFadePauseResumeChanged = actions.onFadePauseResumeChanged,
                            onOpenPlayerFromNotificationChanged = actions.onOpenPlayerFromNotificationChanged,
                            onPersistRepeatModeChanged = actions.onPersistRepeatModeChanged,
                            onKeepScreenOnChanged = actions.onKeepScreenOnChanged,
                            onPlayerArtworkCornerRadiusDpChanged = actions.onPlayerArtworkCornerRadiusDpChanged,
                            onShowAudioOutputRouteChipChanged = actions.onShowAudioOutputRouteChipChanged,
                            onCanvasTapToSeekSecondsChanged = actions.onCanvasTapToSeekSecondsChanged,
                            onFilenameDisplayModeChanged = actions.onFilenameDisplayModeChanged,
                            onFilenameOnlyWhenTitleMissingChanged = actions.onFilenameOnlyWhenTitleMissingChanged
                        )
                    )
                }

                SettingsRoute.Visualization -> {
                    VisualizationRouteContent(
                        state = VisualizationRouteState(
                            visualizationMode = state.visualizationMode,
                            enabledVisualizationModes = state.enabledVisualizationModes,
                            visualizationPerformanceMode = state.visualizationPerformanceMode,
                            visualizationShowDebugInfo = state.visualizationShowDebugInfo,
                            visualizationKeepScreenOn = state.visualizationKeepScreenOn,
                            visualizationKeepScreenOnAvailable = state.visualizationKeepScreenOnAvailable
                        ),
                        actions = VisualizationRouteActions(
                            onVisualizationModeChanged = actions.onVisualizationModeChanged,
                            onEnabledVisualizationModesChanged = actions.onEnabledVisualizationModesChanged,
                            onVisualizationPerformanceModeChanged = actions.onVisualizationPerformanceModeChanged,
                            onVisualizationShowDebugInfoChanged = actions.onVisualizationShowDebugInfoChanged,
                            onVisualizationKeepScreenOnChanged = actions.onVisualizationKeepScreenOnChanged,
                            onOpenVisualizationBasic = actions.onOpenVisualizationBasic,
                            onOpenVisualizationAdvanced = actions.onOpenVisualizationAdvanced,
                            onOpenVisualizationTrackTicker = actions.onOpenVisualizationTrackTicker
                        )
                    )
                }

                SettingsRoute.VisualizationTrackTicker -> {
                    VisualizationTrackTickerRouteContent()
                }

                SettingsRoute.VisualizationBasic -> {
                    VisualizationBasicRouteContent(
                        actions = VisualizationBasicRouteActions(
                            onOpenVisualizationBasicBars = actions.onOpenVisualizationBasicBars,
                            onOpenVisualizationBasicOscilloscope = actions.onOpenVisualizationBasicOscilloscope,
                            onOpenVisualizationBasicVuMeters = actions.onOpenVisualizationBasicVuMeters
                        )
                    )
                }

                SettingsRoute.VisualizationAdvanced -> {
                    VisualizationAdvancedRouteContent(
                        actions = VisualizationAdvancedRouteActions(
                            onOpenVisualizationAdvancedChannelScope = actions.onOpenVisualizationAdvancedChannelScope,
                            onOpenVisualizationAdvancedStarfield = actions.onOpenVisualizationAdvancedStarfield,
                            onOpenVisualizationAdvancedProjectM = actions.onOpenVisualizationAdvancedProjectM
                        )
                    )
                }

                SettingsRoute.VisualizationBasicBars -> {
                    VisualizationBasicBarsRouteContent(
                        state = VisualizationBasicBarsRouteState(
                            visualizationBarCount = state.visualizationBarCount,
                            visualizationBarSmoothingPercent = state.visualizationBarSmoothingPercent,
                            visualizationBarRoundnessDp = state.visualizationBarRoundnessDp,
                            visualizationBarOverlayArtwork = state.visualizationBarOverlayArtwork,
                            visualizationBarUseThemeColor = state.visualizationBarUseThemeColor,
                            visualizationBarRenderBackend = state.visualizationBarRenderBackend
                        ),
                        actions = VisualizationBasicBarsRouteActions(
                            onVisualizationBarCountChanged = actions.onVisualizationBarCountChanged,
                            onVisualizationBarSmoothingPercentChanged = actions.onVisualizationBarSmoothingPercentChanged,
                            onVisualizationBarRoundnessDpChanged = actions.onVisualizationBarRoundnessDpChanged,
                            onVisualizationBarOverlayArtworkChanged = actions.onVisualizationBarOverlayArtworkChanged,
                            onVisualizationBarUseThemeColorChanged = actions.onVisualizationBarUseThemeColorChanged,
                            onVisualizationBarRenderBackendChanged = actions.onVisualizationBarRenderBackendChanged
                        )
                    )
                }

                SettingsRoute.VisualizationBasicOscilloscope -> {
                    VisualizationBasicOscilloscopeRouteContent(
                        visualizationOscStereo = state.visualizationOscStereo,
                        onVisualizationOscStereoChanged = actions.onVisualizationOscStereoChanged
                    )
                }

                SettingsRoute.VisualizationBasicVuMeters -> {
                    VisualizationBasicVuMetersRouteContent(
                        state = VisualizationBasicVuMetersRouteState(
                            visualizationVuAnchor = state.visualizationVuAnchor,
                            visualizationVuUseThemeColor = state.visualizationVuUseThemeColor,
                            visualizationVuSmoothingPercent = state.visualizationVuSmoothingPercent,
                            visualizationVuRenderBackend = state.visualizationVuRenderBackend
                        ),
                        actions = VisualizationBasicVuMetersRouteActions(
                            onVisualizationVuAnchorChanged = actions.onVisualizationVuAnchorChanged,
                            onVisualizationVuUseThemeColorChanged = actions.onVisualizationVuUseThemeColorChanged,
                            onVisualizationVuSmoothingPercentChanged = actions.onVisualizationVuSmoothingPercentChanged,
                            onVisualizationVuRenderBackendChanged = actions.onVisualizationVuRenderBackendChanged
                        )
                    )
                }

                SettingsRoute.VisualizationAdvancedChannelScope -> {
                    VisualizationAdvancedChannelScopeRouteContent(
                        waveRenderModeDefault = state.channelScopeWaveRenderModeDefault
                    )
                }

                SettingsRoute.VisualizationAdvancedStarfield -> {
                    VisualizationAdvancedStarfieldRouteContent()
                }

                SettingsRoute.VisualizationAdvancedProjectM -> {
                    platformContent.ProjectMRouteContent(
                        onOpenPresetPacks = actions.onOpenVisualizationProjectMPacks
                    )
                }

                SettingsRoute.VisualizationAdvancedProjectMPacks -> {
                    platformContent.ProjectMSetsRouteContent()
                }

                SettingsRoute.Misc -> {
                    MiscRouteContent(
                        actions = MiscRouteActions(
                            onClearRecentHistory = actions.onClearRecentHistory
                        )
                    )
                }

                SettingsRoute.UrlCache -> {
                    UrlCacheRouteContent(
                        state = UrlCacheRouteState(
                            fileCacheClearOnLaunch = state.fileCacheClearOnLaunch,
                            fileCacheMaxTracks = state.fileCacheMaxTracks,
                            fileCacheMaxBytes = state.fileCacheMaxBytes,
                            streamingCacheClearOnLaunch = state.streamingCacheClearOnLaunch,
                            streamingCacheMaxTracks = state.streamingCacheMaxTracks,
                            streamingCacheMaxBytes = state.streamingCacheMaxBytes,
                            archiveCacheClearOnLaunch = state.archiveCacheClearOnLaunch,
                            archiveCacheMaxMounts = state.archiveCacheMaxMounts,
                            archiveCacheMaxBytes = state.archiveCacheMaxBytes,
                            archiveCacheMaxAgeDays = state.archiveCacheMaxAgeDays
                        ),
                        actions = UrlCacheRouteActions(
                            onFileCacheClearOnLaunchChanged = actions.onFileCacheClearOnLaunchChanged,
                            onFileCacheMaxTracksChanged = actions.onFileCacheMaxTracksChanged,
                            onFileCacheMaxBytesChanged = actions.onFileCacheMaxBytesChanged,
                            onOpenFileCacheManager = actions.onOpenFileCacheManager,
                            onClearFileCacheNow = actions.onClearFileCacheNow,
                            onStreamingCacheClearOnLaunchChanged = actions.onStreamingCacheClearOnLaunchChanged,
                            onStreamingCacheMaxTracksChanged = actions.onStreamingCacheMaxTracksChanged,
                            onStreamingCacheMaxBytesChanged = actions.onStreamingCacheMaxBytesChanged,
                            onOpenStreamingCacheManager = actions.onOpenStreamingCacheManager,
                            onClearStreamingCacheNow = actions.onClearStreamingCacheNow,
                            onArchiveCacheClearOnLaunchChanged = actions.onArchiveCacheClearOnLaunchChanged,
                            onArchiveCacheMaxMountsChanged = actions.onArchiveCacheMaxMountsChanged,
                            onArchiveCacheMaxBytesChanged = actions.onArchiveCacheMaxBytesChanged,
                            onArchiveCacheMaxAgeDaysChanged = actions.onArchiveCacheMaxAgeDaysChanged,
                            onClearArchiveCacheNow = actions.onClearArchiveCacheNow
                        )
                    )
                }

                SettingsRoute.CacheManager -> {
                    CacheManagerSettingsRouteContent(
                        state = CacheManagerSettingsRouteState(
                            route = currentRoute,
                            cachedSourceFiles = state.fileCachedSourceFiles
                        ),
                        actions = CacheManagerSettingsRouteActions(
                            onRefreshCachedSourceFiles = actions.onRefreshCachedSourceFiles,
                            onDeleteCachedSourceFiles = actions.onDeleteCachedSourceFiles,
                            onExportCachedSourceFiles = actions.onExportCachedSourceFiles
                        )
                    )
                }

                SettingsRoute.StreamingCacheManager -> {
                    CacheManagerSettingsRouteContent(
                        state = CacheManagerSettingsRouteState(
                            route = currentRoute,
                            cachedSourceFiles = state.streamingCachedSourceFiles
                        ),
                        actions = CacheManagerSettingsRouteActions(
                            onRefreshCachedSourceFiles = actions.onRefreshStreamingCachedSourceFiles,
                            onDeleteCachedSourceFiles = actions.onDeleteStreamingCachedSourceFiles,
                            onExportCachedSourceFiles = actions.onExportStreamingCachedSourceFiles
                        )
                    )
                }

                SettingsRoute.Ui -> {
                    UiRouteContent(
                        state = UiRouteState(
                            themeMode = state.themeMode,
                            useMonet = state.useMonet,
                            monetAvailable = state.monetAvailable
                        ),
                        actions = UiRouteActions(
                            onThemeModeChanged = actions.onThemeModeChanged,
                            onUseMonetChanged = actions.onUseMonetChanged
                        )
                    )
                }

                SettingsRoute.About -> AboutSettingsBody(useMonet = state.useMonet)
                }
            }

            val totalBottom = bottomContentPadding + extraBottomPadding
            if (totalBottom > 0.dp) {
                Spacer(modifier = Modifier.height(totalBottom))
            }
        }
        }
    }

    AnimatedContent(
        targetState = isTwoPane,
        transitionSpec = {
            fadeIn(animationSpec = tween(durationMillis = 180)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 180))
        },
        label = "settingsTwoPaneMode"
    ) { layoutTwoPane ->
    if (layoutTwoPane) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPaddingValues)
        ) {
            SettingsTwoPaneRootList(
                pageScrollPositions = pageScrollPositions,
                actions = actions,
                onRequestClearAllSettings = onRequestClearAllSettings,
                bottomContentPadding = bottomContentPadding
            )
            VerticalDivider(
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            AnimatedContent(
                targetState = route,
                transitionSpec = {

                                val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                                val enter = slideInHorizontally(
                                    initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                                    animationSpec = tween(
                                        durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeIn(
                                    animationSpec = tween(
                                        durationMillis = 210,
                                        delayMillis = 60,
                                        easing = LinearOutSlowInEasing
                                    )
                                )
                                val exit = slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                                    animationSpec = tween(
                                        durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                                        easing = FastOutSlowInEasing
                                    )
                                ) + fadeOut(
                                    animationSpec = tween(
                                        durationMillis = 120,
                                        easing = FastOutLinearInEasing
                                    )
                                )
                                enter togetherWith exit
                },
                label = "settingsTwoPaneRouteTransition",
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(start = 8.dp)
            ) { currentRoute ->
                routePane(currentRoute)
            }
        }
    } else {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPaddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {

                            val forward = settingsRouteOrder(targetState) >= settingsRouteOrder(initialState)
                            val enter = slideInHorizontally(
                                initialOffsetX = { fullWidth -> if (forward) fullWidth else -fullWidth / 4 },
                                animationSpec = tween(
                                    durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeIn(
                                animationSpec = tween(
                                    durationMillis = 210,
                                    delayMillis = 60,
                                    easing = LinearOutSlowInEasing
                                )
                            )
                            val exit = slideOutHorizontally(
                                targetOffsetX = { fullWidth -> if (forward) -fullWidth / 4 else fullWidth / 4 },
                                animationSpec = tween(
                                    durationMillis = SETTINGS_PAGE_NAV_DURATION_MS,
                                    easing = FastOutSlowInEasing
                                )
                            ) + fadeOut(
                                animationSpec = tween(
                                    durationMillis = 120,
                                    easing = FastOutLinearInEasing
                                )
                            )
                            enter togetherWith exit
            },
            label = "settingsRouteTransition",
            modifier = Modifier
                .then(if (isWatch) Modifier else Modifier.widthIn(max = SETTINGS_PAGE_MAX_WIDTH))
                .fillMaxSize()
                .padding(start = if (isWatch) 0.dp else 16.dp, end = if (isWatch) 0.dp else 16.dp)
        ) { currentRoute ->
            routePane(currentRoute)
        }
    }
    }
    }
}

@Composable
private fun SettingsTwoPaneRootList(
    pageScrollPositions: SnapshotStateMap<SettingsRoute, Int>,
    actions: SettingsScreenActions,
    onRequestClearAllSettings: () -> Unit,
    bottomContentPadding: Dp
) {
    val scrollState = rememberScrollState()
    DisposableEffect(Unit) {
        onDispose { pageScrollPositions[SettingsRoute.Root] = scrollState.value }
    }
    LaunchedEffect(Unit) {
        val saved = pageScrollPositions.remove(SettingsRoute.Root) ?: 0
        if (saved > 0) {
            snapshotFlow { scrollState.maxValue }
                .filter { it > 0 }
                .first()
            scrollState.scrollTo(saved)
        }
    }
    Column(
        modifier = Modifier
            .width(SETTINGS_TWO_PANE_LIST_WIDTH)
            .fillMaxHeight()
            .verticalScroll(state = scrollState)
            .padding(start = 16.dp, end = 8.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SettingsRowsHost {
            RootRouteContent(
                actions = RootRouteActions(
                    onOpenAudioPlugins = actions.onOpenAudioPlugins,
                    onOpenGeneralAudio = actions.onOpenGeneralAudio,
                    onOpenLibrary = actions.onOpenLibrary,
                    onOpenLibraryScanner = actions.onOpenLibraryScanner,
                    onOpenPlayer = actions.onOpenPlayer,
                    onOpenHome = actions.onOpenHome,
                    onOpenFileBrowser = actions.onOpenFileBrowser,
                    onOpenNetwork = actions.onOpenNetwork,
                    onOpenVisualization = actions.onOpenVisualization,
                    onOpenUrlCache = actions.onOpenUrlCache,
                    onOpenMisc = actions.onOpenMisc,
                    onOpenUi = actions.onOpenUi,
                    onOpenAbout = actions.onOpenAbout,
                    onOpenSettingsPorting = actions.onOpenSettingsPorting,
                    onRequestClearAllSettings = onRequestClearAllSettings
                )
            )
        }

        if (bottomContentPadding > 0.dp) {
            Spacer(modifier = Modifier.height(bottomContentPadding))
        }
    }
}

internal fun settingsSecondaryTitle(route: SettingsRoute, selectedPluginName: String?): String? {
    return when (route) {
        SettingsRoute.Root -> null
        SettingsRoute.AudioPlugins -> "Audio cores"
        SettingsRoute.PluginDetail -> selectedPluginName?.let { "$it core settings" } ?: "Core settings"
        SettingsRoute.PluginVgmPlayChipSettings -> "VGMPlay chip settings"
        SettingsRoute.PluginFfmpeg -> "FFmpeg core settings"
        SettingsRoute.PluginOpenMpt -> "OpenMPT core settings"
        SettingsRoute.PluginVgmPlay -> "VGMPlay core settings"
        SettingsRoute.UrlCache -> "Cache settings"
        SettingsRoute.CacheManager -> "Manage cached files"
        SettingsRoute.StreamingCacheManager -> "Manage cached files"
        SettingsRoute.GeneralAudio -> "General audio"
        SettingsRoute.Library -> "Library"
        SettingsRoute.LibraryScanner -> "Storage scanner"
        SettingsRoute.Home -> "Home settings"
        SettingsRoute.FileBrowser -> "File browser settings"
        SettingsRoute.Network -> "Network settings"
        SettingsRoute.Player -> "Player settings"
        SettingsRoute.Visualization -> "Visualization settings"
        SettingsRoute.VisualizationTrackTicker -> "Track ticker"
        SettingsRoute.VisualizationBasic -> "Basic visualizations"
        SettingsRoute.VisualizationBasicBars -> "Bars settings"
        SettingsRoute.VisualizationBasicOscilloscope -> "Oscilloscope settings"
        SettingsRoute.VisualizationBasicVuMeters -> "VU meters settings"
        SettingsRoute.VisualizationAdvanced -> "Advanced visualizations"
        SettingsRoute.VisualizationAdvancedChannelScope -> "Channel scope settings"
        SettingsRoute.VisualizationAdvancedStarfield -> "Starfield settings"
        SettingsRoute.VisualizationAdvancedProjectM -> "projectM settings"
        SettingsRoute.VisualizationAdvancedProjectMPacks -> "Preset packs"
        SettingsRoute.Misc -> "Misc settings"
        SettingsRoute.Ui -> "UI settings"
        SettingsRoute.About -> "About"
    }
}
