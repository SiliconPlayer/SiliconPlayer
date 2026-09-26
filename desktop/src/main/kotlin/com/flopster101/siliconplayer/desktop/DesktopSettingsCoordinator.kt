package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.AudioBackendPreference
import com.flopster101.siliconplayer.AudioBufferPreset
import com.flopster101.siliconplayer.AudioPerformanceMode
import com.flopster101.siliconplayer.AudioResamplerPreference
import com.flopster101.siliconplayer.BrowserNameSortMode
import com.flopster101.siliconplayer.CachedSourceFile
import com.flopster101.siliconplayer.CorePreferenceKeys
import com.flopster101.siliconplayer.REMOTE_SOURCE_CACHE_DIR
import com.flopster101.siliconplayer.clearRemoteCacheFiles
import com.flopster101.siliconplayer.deleteDomainFile
import com.flopster101.siliconplayer.deleteStoreFile
import com.flopster101.siliconplayer.networkCredentialsFile
import com.flopster101.siliconplayer.networkNodesFile
import com.flopster101.siliconplayer.data.clearArchiveMountCache
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.deleteSpecificRemoteCacheFiles
import com.flopster101.siliconplayer.listCachedSourceFiles
import com.flopster101.siliconplayer.EndFadeCurve
import com.flopster101.siliconplayer.FilenameDisplayMode
import com.flopster101.siliconplayer.LookaheadClipperMode
import com.flopster101.siliconplayer.MultiChannelOutputMode
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.SettingsPluginCoreActions
import com.flopster101.siliconplayer.SettingsPluginCoreState
import com.flopster101.siliconplayer.SettingsRoute
import com.flopster101.siliconplayer.SettingsScreenActions
import com.flopster101.siliconplayer.SettingsScreenState
import com.flopster101.siliconplayer.ThemeMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationPerformanceMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.VgmPlayConfig
import com.flopster101.siliconplayer.audio.applyDspSettingsToNative
import com.flopster101.siliconplayer.audio.defaultDspSettings
import com.flopster101.siliconplayer.buildSettingsScreenState
import com.flopster101.siliconplayer.clearAllAudioParameterPrefs
import com.flopster101.siliconplayer.clearAllDecoderPluginVolumes
import com.flopster101.siliconplayer.parseEnabledVisualizationModes
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.resetVisualizationBarsSettings
import com.flopster101.siliconplayer.resetVisualizationChannelScopeSettings
import com.flopster101.siliconplayer.resetVisualizationOscilloscopeSettings
import com.flopster101.siliconplayer.resetVisualizationProjectMSettings
import com.flopster101.siliconplayer.resetVisualizationVuSettings
import com.flopster101.siliconplayer.serializeEnabledVisualizationModes
import com.flopster101.siliconplayer.platform.LocalAppCacheDir
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalFileExportHandler
import com.flopster101.siliconplayer.platform.LocalToastHandler
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun rememberDesktopSettings(
    currentRoute: SettingsRoute,
    onRouteChange: (SettingsRoute) -> Unit,
    onBackToMainView: () -> Unit,
    onOpenAudioEffects: () -> Unit = {},
    defaultScopeTextSizeSp: Int = 10,
    protectedCachePaths: Set<String> = emptySet()
): Pair<SettingsScreenState, SettingsScreenActions> {
    val prefs = LocalAppPreferences.current
    val cacheDir = LocalAppCacheDir.current
    val toastHandler = LocalToastHandler.current
    val fileExportHandler = LocalFileExportHandler.current
    val scope = rememberCoroutineScope()
    val remoteCacheRoot = remember(cacheDir) { File(cacheDir, REMOTE_SOURCE_CACHE_DIR) }

    var changeToken by remember { mutableIntStateOf(0) }
    val cachedSourceFiles = remember(changeToken, remoteCacheRoot) {
        listCachedSourceFiles(remoteCacheRoot)
    }
    fun refreshCachedSourceFiles() {
        changeToken++
    }
    DisposableEffect(prefs) {
        val listener = AppPreferences.OnChangeListener { _, _ ->
            changeToken++
        }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }

    var selectedPluginName by remember { mutableStateOf<String?>(null) }

    fun putBool(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        changeToken++
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        changeToken++
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
        changeToken++
    }

    // Build plugin core state
    val pluginCoreState = remember(changeToken) {
        SettingsPluginCoreState(
            ffmpegSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_FFMPEG, 0),
            ffmpegGaplessRepeatTrack = prefs.getBoolean(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK, false),
            ffmpegCapabilities = runCatching { NativeBridge.getCoreCapabilities(DecoderNames.FFMPEG) }.getOrDefault(0),
            openMptSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_OPENMPT, 0),
            openMptCapabilities = runCatching { NativeBridge.getCoreCapabilities(DecoderNames.LIB_OPEN_MPT) }.getOrDefault(0),
            vgmPlaySampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, 0),
            vgmPlayCapabilities = runCatching { NativeBridge.getCoreCapabilities(DecoderNames.VGM_PLAY) }.getOrDefault(0),
            gmeSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_GME, 0),
            crsidSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_CRSID, 0),
            sidPlayFpSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_SIDPLAYFP, 0),
            lazyUsf2SampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_LAZYUSF2, 0),
            adPlugSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_ADPLUG, 0),
            hivelyTrackerSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_HIVELYTRACKER, 0),
            klystrackSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_KLYSTRACK, 0),
            furnaceSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_FURNACE, 0),
            uadeSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_UADE, 0),
            xmpSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_XMP, 0),
            ufmodSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_UFMOD, 0),
            adPlugOplEngine = prefs.getInt(CorePreferenceKeys.ADPLUG_OPL_ENGINE, 0),
            xmpInterpolation = prefs.getInt(CorePreferenceKeys.XMP_INTERPOLATION, 0),
            xmpStereoSeparationPercent = prefs.getInt(CorePreferenceKeys.XMP_STEREO_SEPARATION_PERCENT, 100),
            xmpAmigaStereoSeparationPercent = prefs.getInt(CorePreferenceKeys.XMP_AMIGA_STEREO_SEPARATION_PERCENT, 100),
            xmpAmigaModel = prefs.getInt(CorePreferenceKeys.XMP_AMIGA_MODEL, 0),
            ayflyCoreSampleRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_AYFLY, 0),
            ayflyOversample = prefs.getInt(CorePreferenceKeys.AYFLY_OVERSAMPLE, 0),
            ayflyChipType = prefs.getInt(CorePreferenceKeys.AYFLY_CHIP_TYPE, 0),
            ayflyMixType = prefs.getInt(CorePreferenceKeys.AYFLY_MIX_TYPE, 0),
            ayflyIntFreq = prefs.getInt(CorePreferenceKeys.AYFLY_INT_FREQ, 0),
            lazyUsf2UseHleAudio = prefs.getBoolean(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO, true),
            vio2sfInterpolationQuality = prefs.getInt(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY, 0),
            sc68SamplingRateHz = prefs.getInt(CorePreferenceKeys.CORE_RATE_SC68, 0),
            sc68Asid = prefs.getInt(CorePreferenceKeys.SC68_ASID, 0),
            sc68DefaultTimeSeconds = prefs.getInt(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS, 0),
            sc68YmEngine = prefs.getInt(CorePreferenceKeys.SC68_YM_ENGINE, 0),
            sc68YmVolModel = prefs.getInt(CorePreferenceKeys.SC68_YM_VOLMODEL, 0),
            sc68AmigaFilter = prefs.getBoolean(CorePreferenceKeys.SC68_AMIGA_FILTER, false),
            sc68AmigaBlend = prefs.getInt(CorePreferenceKeys.SC68_AMIGA_BLEND, 0),
            sc68AmigaClock = prefs.getInt(CorePreferenceKeys.SC68_AMIGA_CLOCK, 0),
            uadeFilterEnabled = prefs.getBoolean(CorePreferenceKeys.UADE_FILTER_ENABLED, true),
            uadeNtscMode = prefs.getBoolean(CorePreferenceKeys.UADE_NTSC_MODE, false),
            uadePanningMode = prefs.getInt(CorePreferenceKeys.UADE_PANNING_MODE, 0),
            hivelyTrackerPanningMode = prefs.getInt(CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE, 0),
            hivelyTrackerMixGainPercent = prefs.getInt(CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT, 100),
            klystrackPlayerQuality = prefs.getInt(CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY, 0),
            furnaceYm2612Core = prefs.getInt(CorePreferenceKeys.FURNACE_YM2612_CORE, 0),
            furnaceSnCore = prefs.getInt(CorePreferenceKeys.FURNACE_SN_CORE, 0),
            furnaceNesCore = prefs.getInt(CorePreferenceKeys.FURNACE_NES_CORE, 0),
            furnaceC64Core = prefs.getInt(CorePreferenceKeys.FURNACE_C64_CORE, 0),
            furnaceGbQuality = prefs.getInt(CorePreferenceKeys.FURNACE_GB_QUALITY, 0),
            furnaceDsidQuality = prefs.getInt(CorePreferenceKeys.FURNACE_DSID_QUALITY, 0),
            furnaceAyCore = prefs.getInt(CorePreferenceKeys.FURNACE_AY_CORE, 0),
            crsidClockMode = prefs.getInt(CorePreferenceKeys.CRSID_CLOCK_MODE, 0),
            crsidSidModelMode = prefs.getInt(CorePreferenceKeys.CRSID_SID_MODEL_MODE, 0),
            crsidQualityMode = prefs.getInt(CorePreferenceKeys.CRSID_QUALITY_MODE, 0),
            crsidFilter6581Preset = prefs.getInt(CorePreferenceKeys.CRSID_FILTER_6581_PRESET, 0),
            sidPlayFpBackend = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_BACKEND, 0),
            sidPlayFpClockMode = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE, 0),
            sidPlayFpSidModelMode = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE, 0),
            sidPlayFpFilter6581Enabled = prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED, true),
            sidPlayFpFilter8580Enabled = prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED, true),
            sidPlayFpDigiBoost8580 = prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580, false),
            sidPlayFpFilterCurve6581Percent = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581, 50),
            sidPlayFpFilterRange6581Percent = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581, 50),
            sidPlayFpFilterCurve8580Percent = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580, 50),
            sidPlayFpReSidFpFastSampling = prefs.getBoolean(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING, false),
            sidPlayFpReSidFpCombinedWaveformsStrength = prefs.getInt(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH, 50),
            gmeTempoPercent = prefs.getInt(CorePreferenceKeys.GME_TEMPO_PERCENT, 100),
            gmeStereoSeparationPercent = prefs.getInt(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, 100),
            gmeEchoEnabled = prefs.getBoolean(CorePreferenceKeys.GME_ECHO_ENABLED, false),
            gmeAccuracyEnabled = prefs.getBoolean(CorePreferenceKeys.GME_ACCURACY_ENABLED, true),
            gmeEqTrebleDecibel = prefs.getInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, 0),
            gmeEqBassHz = prefs.getInt(CorePreferenceKeys.GME_EQ_BASS_HZ, 0),
            gmeSpcUseBuiltInFade = prefs.getBoolean(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, true),
            gmeSpcInterpolation = prefs.getInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, 0),
            gmeSpcUseNativeSampleRate = prefs.getBoolean(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, false),
            vgmPlayLoopCount = prefs.getInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, 2),
            vgmPlayAllowNonLoopingLoop = prefs.getBoolean(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, false),
            vgmPlayVsyncRate = prefs.getInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, 60),
            vgmPlayResampleMode = prefs.getInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, 0),
            vgmPlayChipSampleMode = prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, 0),
            vgmPlayChipSampleRate = prefs.getInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, 0),
            vgmPlayChipCoreSelections = VgmPlayConfig.defaultChipCoreSelections().mapValues { (chipKey, defaultValue) ->
                prefs.getInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), defaultValue)
            },
            openMptStereoSeparationPercent = prefs.getInt("openmpt.stereo_separation_percent", 100),
            openMptStereoSeparationAmigaPercent = prefs.getInt("openmpt.stereo_separation_amiga_percent", 100),
            openMptInterpolationFilterLength = prefs.getInt("openmpt.interpolation_filter_length", 8),
            openMptAmigaResamplerMode = prefs.getInt("openmpt.amiga_resampler_mode", 0),
            openMptAmigaResamplerApplyAllModules = prefs.getBoolean("openmpt.amiga_resampler_apply_all_modules", false),
            openMptVolumeRampingStrength = prefs.getInt("openmpt.volume_ramping_strength", -1),
            openMptFt2XmVolumeRamping = prefs.getBoolean("openmpt.ft2_xm_volume_ramping", false),
            openMptMasterGainMilliBel = prefs.getInt("openmpt.master_gain_millibel", 0),
            openMptSurroundEnabled = prefs.getBoolean("openmpt.surround_enabled", false)
        )
    }

    val enabledVisualizationModes = remember(changeToken) {
        val parsed = parseEnabledVisualizationModes(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, null)
        )
        val projectMMigrated = prefs.getBoolean(
            AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED,
            false
        )
        val starfieldMigrated = prefs.getBoolean(
            AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED,
            false
        )
        if (projectMMigrated && starfieldMigrated) {
            parsed
        } else {
            var result = parsed
            if (!projectMMigrated) result = result + VisualizationMode.ProjectM
            if (!starfieldMigrated) result = result + VisualizationMode.Starfield
            prefs.edit()
                .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED, true)
                .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED, true)
                .putString(
                    AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                    serializeEnabledVisualizationModes(result)
                )
                .apply()
            result
        }
    }

    val state = remember(changeToken, selectedPluginName) {
        buildSettingsScreenState(
            selectedPluginName = selectedPluginName,
            autoPlayOnTrackSelect = prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, true),
            openPlayerOnTrackSelect = prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, true),
            autoPlayNextTrackOnEnd = prefs.getBoolean(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, true),
            preloadNextCachedRemoteTrack = prefs.getBoolean(AppPreferenceKeys.PRELOAD_NEXT_CACHED_REMOTE_TRACK, true),
            playlistWrapNavigation = prefs.getBoolean(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, true),
            previousRestartsAfterThreshold = prefs.getBoolean(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true),
            fadePauseResume = prefs.getBoolean(AppPreferenceKeys.FADE_PAUSE_RESUME, true),
            respondHeadphoneMediaButtons = prefs.getBoolean(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, true),
            pauseOnHeadphoneDisconnect = prefs.getBoolean(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, true),
            audioFocusInterrupt = prefs.getBoolean(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, true),
            audioDucking = prefs.getBoolean(AppPreferenceKeys.AUDIO_DUCKING, true),
            audioBackendPreference = AudioBackendPreference.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE, "auto")),
            audioPerformanceMode = AudioPerformanceMode.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_PERFORMANCE_MODE, "none")),
            audioBufferPreset = AudioBufferPreset.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_BUFFER_PRESET, "medium")),
            audioResamplerPreference = AudioResamplerPreference.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE, "builtin")),
            audioOutputLimiterEnabled = prefs.getBoolean(AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED, false),
            lookaheadClipperMode = LookaheadClipperMode.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE, "soft")),
            multiChannelOutputMode = MultiChannelOutputMode.fromStorage(prefs.getString(AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE, "ffmpeg_only")),
            audioAllowBackendFallback = prefs.getBoolean(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, true),
            bitPerfectUsbAudio = prefs.getBoolean(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, false),
            openPlayerFromNotification = prefs.getBoolean(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, true),
            persistRepeatMode = prefs.getBoolean(AppPreferenceKeys.PERSIST_REPEAT_MODE, true),
            themeMode = ThemeMode.fromStorage(prefs.getString(AppPreferenceKeys.THEME_MODE, "auto")),
            useMonet = prefs.getBoolean(AppPreferenceKeys.THEME_USE_MONET, false),
            monetAvailable = false,
            rememberBrowserLocation = prefs.getBoolean(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true),
            showParentDirectoryEntry = prefs.getBoolean(AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY, true),
            showFileIconChipBackground = prefs.getBoolean(AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND, true),
            sortArchivesBeforeFiles = prefs.getBoolean(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, false),
            browserNameSortMode = BrowserNameSortMode.fromStorage(prefs.getString(AppPreferenceKeys.BROWSER_NAME_SORT_MODE, "natural")),
            recentFoldersLimit = prefs.getInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, 10),
            recentFilesLimit = prefs.getInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, 20),
            pressBackTwiceToExit = prefs.getBoolean(AppPreferenceKeys.PRESS_BACK_TWICE_TO_EXIT, false),
            urlCacheClearOnLaunch = prefs.getBoolean(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false),
            urlCacheMaxTracks = prefs.getInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, 200),
            urlCacheMaxBytes = prefs.getLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, 500L * 1024 * 1024),
            archiveCacheClearOnLaunch = prefs.getBoolean(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, false),
            archiveCacheMaxMounts = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, 10),
            archiveCacheMaxBytes = prefs.getLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, 200L * 1024 * 1024),
            archiveCacheMaxAgeDays = prefs.getInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, 7),
            cachedSourceFiles = cachedSourceFiles,
            keepScreenOn = prefs.getBoolean(AppPreferenceKeys.KEEP_SCREEN_ON, false),
            playerArtworkCornerRadiusDp = prefs.getInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, 16),
            showAudioOutputRouteChip = prefs.getBoolean(AppPreferenceKeys.PLAYER_SHOW_AUDIO_OUTPUT_CHIP, true),
            canvasTapToSeekSeconds = prefs.getInt(AppPreferenceKeys.CANVAS_TAP_TO_SEEK_SECONDS, 5),
            filenameDisplayMode = FilenameDisplayMode.fromStorage(prefs.getString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, "tracker_only")),
            filenameOnlyWhenTitleMissing = prefs.getBoolean(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, false),
            unknownTrackDurationSeconds = prefs.getInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, 180),
            endFadeApplyToAllTracks = prefs.getBoolean(AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS, false),
            endFadeDurationMs = prefs.getInt(AppPreferenceKeys.END_FADE_DURATION_MS, 4000),
            endFadeCurve = EndFadeCurve.fromStorage(prefs.getString(AppPreferenceKeys.END_FADE_CURVE, "linear")),
            visualizationMode = VisualizationMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, "bars")),
            enabledVisualizationModes = enabledVisualizationModes,
            visualizationPerformanceMode = VisualizationPerformanceMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PERFORMANCE_MODE, "auto")),
            visualizationShowDebugInfo = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO, false),
            visualizationKeepScreenOn = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, true),
            visualizationBarCount = prefs.getInt(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, 32),
            visualizationBarSmoothingPercent = prefs.getInt(AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT, 50),
            visualizationBarRoundnessDp = prefs.getInt(AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP, 4),
            visualizationBarOverlayArtwork = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK, true),
            visualizationBarUseThemeColor = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR, true),
            visualizationBarRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, AppDefaults.Visualization.Bars.renderBackend.storageValue),
                AppDefaults.Visualization.Bars.renderBackend
            ),
            visualizationOscStereo = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, false),
            visualizationVuAnchor = VisualizationVuAnchor.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, "bottom")),
            visualizationVuUseThemeColor = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR, true),
            visualizationVuSmoothingPercent = prefs.getInt(AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT, 50),
            visualizationVuRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND, AppDefaults.Visualization.Vu.renderBackend.storageValue),
                AppDefaults.Visualization.Vu.renderBackend
            ),
            pluginCore = pluginCoreState
        )
    }

    val actions = remember {
        SettingsScreenActions(
            onBack = {
                when (currentRoute) {
                    SettingsRoute.Root -> onBackToMainView()
                    SettingsRoute.VisualizationBasicBars,
                    SettingsRoute.VisualizationBasicOscilloscope,
                    SettingsRoute.VisualizationBasicVuMeters -> onRouteChange(SettingsRoute.VisualizationBasic)
                    SettingsRoute.VisualizationBasic -> onRouteChange(SettingsRoute.Visualization)
                    SettingsRoute.VisualizationAdvancedChannelScope,
                    SettingsRoute.VisualizationAdvancedStarfield,
                    SettingsRoute.VisualizationAdvancedProjectM,
                    SettingsRoute.VisualizationTrackTicker -> onRouteChange(SettingsRoute.VisualizationAdvanced)
                    SettingsRoute.VisualizationAdvancedProjectMPacks -> onRouteChange(SettingsRoute.VisualizationAdvancedProjectM)
                    SettingsRoute.VisualizationAdvanced -> onRouteChange(SettingsRoute.Visualization)
                    SettingsRoute.LibraryScanner -> onRouteChange(SettingsRoute.Library)
                    SettingsRoute.PluginDetail -> onRouteChange(SettingsRoute.AudioPlugins)
                    SettingsRoute.PluginVgmPlayChipSettings -> onRouteChange(SettingsRoute.PluginDetail)
                    else -> onRouteChange(SettingsRoute.Root)
                }
            },
            onOpenAudioPlugins = { onRouteChange(SettingsRoute.AudioPlugins) },
            onOpenGeneralAudio = { onRouteChange(SettingsRoute.GeneralAudio) },
            onOpenLibrary = { onRouteChange(SettingsRoute.Library) },
            onOpenLibraryScanner = { onRouteChange(SettingsRoute.LibraryScanner) },
            onOpenHome = { onRouteChange(SettingsRoute.Home) },
            onOpenFileBrowser = { onRouteChange(SettingsRoute.FileBrowser) },
            onOpenNetwork = { onRouteChange(SettingsRoute.Network) },
            onOpenAudioEffects = onOpenAudioEffects,
            onClearAllAudioParameters = {
                clearAllAudioParameterPrefs(prefs)
                DesktopSongVolumeStore.getInstance().resetAllSongVolumes()
                runCatching {
                    NativeBridge.setMasterGain(0f)
                    NativeBridge.setPluginGain(0f)
                    NativeBridge.setSongGain(0f)
                    NativeBridge.setForceMono(false)
                    applyDspSettingsToNative(defaultDspSettings())
                }
                changeToken++
            },
            onClearPluginAudioParameters = {
                clearAllDecoderPluginVolumes(prefs)
                runCatching { NativeBridge.setPluginGain(0f) }
                changeToken++
            },
            onClearSongAudioParameters = {
                DesktopSongVolumeStore.getInstance().resetAllSongVolumes()
                runCatching { NativeBridge.setSongGain(0f) }
                changeToken++
            },
            onOpenPlayer = { onRouteChange(SettingsRoute.Player) },
            onOpenVisualization = { onRouteChange(SettingsRoute.Visualization) },
            onOpenVisualizationBasic = { onRouteChange(SettingsRoute.VisualizationBasic) },
            onOpenVisualizationTrackTicker = { onRouteChange(SettingsRoute.VisualizationTrackTicker) },
            onOpenVisualizationBasicBars = { onRouteChange(SettingsRoute.VisualizationBasicBars) },
            onOpenVisualizationBasicOscilloscope = { onRouteChange(SettingsRoute.VisualizationBasicOscilloscope) },
            onOpenVisualizationBasicVuMeters = { onRouteChange(SettingsRoute.VisualizationBasicVuMeters) },
            onOpenVisualizationAdvanced = { onRouteChange(SettingsRoute.VisualizationAdvanced) },
            onOpenVisualizationAdvancedChannelScope = { onRouteChange(SettingsRoute.VisualizationAdvancedChannelScope) },
            onOpenVisualizationAdvancedStarfield = { onRouteChange(SettingsRoute.VisualizationAdvancedStarfield) },
            onOpenVisualizationAdvancedProjectM = { onRouteChange(SettingsRoute.VisualizationAdvancedProjectM) },
            onOpenVisualizationProjectMPacks = { onRouteChange(SettingsRoute.VisualizationAdvancedProjectMPacks) },
            onOpenMisc = { onRouteChange(SettingsRoute.Misc) },
            onOpenUrlCache = { onRouteChange(SettingsRoute.UrlCache) },
            onOpenCacheManager = { onRouteChange(SettingsRoute.CacheManager) },
            onOpenUi = { onRouteChange(SettingsRoute.Ui) },
            onOpenAbout = { onRouteChange(SettingsRoute.About) },
            pluginCoreActions = SettingsPluginCoreActions(
                onOpenVgmPlayChipSettings = { onRouteChange(SettingsRoute.PluginVgmPlayChipSettings) },
                onPluginSelected = { pluginName ->
                    selectedPluginName = pluginName
                    onRouteChange(SettingsRoute.PluginDetail)
                },
                onPluginEnabledChanged = { pluginName, enabled ->
                    runCatching { NativeBridge.setDecoderEnabled(pluginName, enabled) }
                    changeToken++
                },
                onPluginPriorityChanged = { pluginName, priority ->
                    runCatching { NativeBridge.setDecoderPriority(pluginName, priority) }
                    changeToken++
                },
                onPluginPriorityOrderChanged = { _ ->
                    changeToken++
                },
                onPluginExtensionsChanged = { pluginName, extensions ->
                    runCatching { NativeBridge.setDecoderEnabledExtensions(pluginName, extensions) }
                    changeToken++
                },
                onFfmpegSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_FFMPEG, it) },
                onFfmpegGaplessRepeatTrackChanged = { putBool(CorePreferenceKeys.FFMPEG_GAPLESS_REPEAT_TRACK, it) },
                onOpenMptSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_OPENMPT, it) },
                onVgmPlaySampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_VGMPLAY, it) },
                onGmeSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_GME, it) },
                onCrsidSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_CRSID, it) },
                onSidPlayFpSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_SIDPLAYFP, it) },
                onLazyUsf2SampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_LAZYUSF2, it) },
                onAdPlugSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_ADPLUG, it) },
                onHivelyTrackerSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_HIVELYTRACKER, it) },
                onKlystrackSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_KLYSTRACK, it) },
                onFurnaceSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_FURNACE, it) },
                onUadeSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_UADE, it) },
                onXmpSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_XMP, it) },
                onUfmodSampleRateChanged = { putInt(CorePreferenceKeys.CORE_RATE_UFMOD, it) },
                onAdPlugOplEngineChanged = { putInt(CorePreferenceKeys.ADPLUG_OPL_ENGINE, it) },
                onXmpInterpolationChanged = { putInt(CorePreferenceKeys.XMP_INTERPOLATION, it) },
                onXmpStereoSeparationPercentChanged = { putInt(CorePreferenceKeys.XMP_STEREO_SEPARATION_PERCENT, it) },
                onXmpAmigaStereoSeparationPercentChanged = { putInt(CorePreferenceKeys.XMP_AMIGA_STEREO_SEPARATION_PERCENT, it) },
                onXmpAmigaModelChanged = { putInt(CorePreferenceKeys.XMP_AMIGA_MODEL, it) },
                onAyflyCoreSampleRateHzChanged = { putInt(CorePreferenceKeys.CORE_RATE_AYFLY, it) },
                onAyflyOversampleChanged = { putInt(CorePreferenceKeys.AYFLY_OVERSAMPLE, it) },
                onAyflyChipTypeChanged = { putInt(CorePreferenceKeys.AYFLY_CHIP_TYPE, it) },
                onAyflyMixTypeChanged = { putInt(CorePreferenceKeys.AYFLY_MIX_TYPE, it) },
                onAyflyIntFreqChanged = { putInt(CorePreferenceKeys.AYFLY_INT_FREQ, it) },
                onLazyUsf2UseHleAudioChanged = { putBool(CorePreferenceKeys.LAZYUSF2_USE_HLE_AUDIO, it) },
                onVio2sfInterpolationQualityChanged = { putInt(CorePreferenceKeys.VIO2SF_INTERPOLATION_QUALITY, it) },
                onSc68SamplingRateHzChanged = { putInt(CorePreferenceKeys.CORE_RATE_SC68, it) },
                onSc68AsidChanged = { putInt(CorePreferenceKeys.SC68_ASID, it) },
                onSc68DefaultTimeSecondsChanged = { putInt(CorePreferenceKeys.SC68_DEFAULT_TIME_SECONDS, it) },
                onSc68YmEngineChanged = { putInt(CorePreferenceKeys.SC68_YM_ENGINE, it) },
                onSc68YmVolModelChanged = { putInt(CorePreferenceKeys.SC68_YM_VOLMODEL, it) },
                onSc68AmigaFilterChanged = { putBool(CorePreferenceKeys.SC68_AMIGA_FILTER, it) },
                onSc68AmigaBlendChanged = { putInt(CorePreferenceKeys.SC68_AMIGA_BLEND, it) },
                onSc68AmigaClockChanged = { putInt(CorePreferenceKeys.SC68_AMIGA_CLOCK, it) },
                onUadeFilterEnabledChanged = { putBool(CorePreferenceKeys.UADE_FILTER_ENABLED, it) },
                onUadeNtscModeChanged = { putBool(CorePreferenceKeys.UADE_NTSC_MODE, it) },
                onUadePanningModeChanged = { putInt(CorePreferenceKeys.UADE_PANNING_MODE, it) },
                onHivelyTrackerPanningModeChanged = { putInt(CorePreferenceKeys.HIVELYTRACKER_PANNING_MODE, it) },
                onHivelyTrackerMixGainPercentChanged = { putInt(CorePreferenceKeys.HIVELYTRACKER_MIX_GAIN_PERCENT, it) },
                onKlystrackPlayerQualityChanged = { putInt(CorePreferenceKeys.KLYSTRACK_PLAYER_QUALITY, it) },
                onFurnaceYm2612CoreChanged = { putInt(CorePreferenceKeys.FURNACE_YM2612_CORE, it) },
                onFurnaceSnCoreChanged = { putInt(CorePreferenceKeys.FURNACE_SN_CORE, it) },
                onFurnaceNesCoreChanged = { putInt(CorePreferenceKeys.FURNACE_NES_CORE, it) },
                onFurnaceC64CoreChanged = { putInt(CorePreferenceKeys.FURNACE_C64_CORE, it) },
                onFurnaceGbQualityChanged = { putInt(CorePreferenceKeys.FURNACE_GB_QUALITY, it) },
                onFurnaceDsidQualityChanged = { putInt(CorePreferenceKeys.FURNACE_DSID_QUALITY, it) },
                onFurnaceAyCoreChanged = { putInt(CorePreferenceKeys.FURNACE_AY_CORE, it) },
                onCrsidClockModeChanged = { putInt(CorePreferenceKeys.CRSID_CLOCK_MODE, it) },
                onCrsidSidModelModeChanged = { putInt(CorePreferenceKeys.CRSID_SID_MODEL_MODE, it) },
                onCrsidQualityModeChanged = { putInt(CorePreferenceKeys.CRSID_QUALITY_MODE, it) },
                onCrsidFilter6581PresetChanged = { putInt(CorePreferenceKeys.CRSID_FILTER_6581_PRESET, it) },
                onSidPlayFpBackendChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_BACKEND, it) },
                onSidPlayFpClockModeChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_CLOCK_MODE, it) },
                onSidPlayFpSidModelModeChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_SID_MODEL_MODE, it) },
                onSidPlayFpFilter6581EnabledChanged = { putBool(CorePreferenceKeys.SIDPLAYFP_FILTER_6581_ENABLED, it) },
                onSidPlayFpFilter8580EnabledChanged = { putBool(CorePreferenceKeys.SIDPLAYFP_FILTER_8580_ENABLED, it) },
                onSidPlayFpDigiBoost8580Changed = { putBool(CorePreferenceKeys.SIDPLAYFP_DIGI_BOOST_8580, it) },
                onSidPlayFpFilterCurve6581PercentChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_6581, it) },
                onSidPlayFpFilterRange6581PercentChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_RANGE_6581, it) },
                onSidPlayFpFilterCurve8580PercentChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_FILTER_CURVE_8580, it) },
                onSidPlayFpReSidFpFastSamplingChanged = { putBool(CorePreferenceKeys.SIDPLAYFP_RESIDFP_FAST_SAMPLING, it) },
                onSidPlayFpReSidFpCombinedWaveformsStrengthChanged = { putInt(CorePreferenceKeys.SIDPLAYFP_RESIDFP_COMBINED_WAVEFORMS_STRENGTH, it) },
                onGmeTempoPercentChanged = { putInt(CorePreferenceKeys.GME_TEMPO_PERCENT, it) },
                onGmeStereoSeparationPercentChanged = { putInt(CorePreferenceKeys.GME_STEREO_SEPARATION_PERCENT, it) },
                onGmeEchoEnabledChanged = { putBool(CorePreferenceKeys.GME_ECHO_ENABLED, it) },
                onGmeAccuracyEnabledChanged = { putBool(CorePreferenceKeys.GME_ACCURACY_ENABLED, it) },
                onGmeEqTrebleDecibelChanged = { putInt(CorePreferenceKeys.GME_EQ_TREBLE_DECIBEL, it) },
                onGmeEqBassHzChanged = { putInt(CorePreferenceKeys.GME_EQ_BASS_HZ, it) },
                onGmeSpcUseBuiltInFadeChanged = { putBool(CorePreferenceKeys.GME_SPC_USE_BUILTIN_FADE, it) },
                onGmeSpcInterpolationChanged = { putInt(CorePreferenceKeys.GME_SPC_INTERPOLATION, it) },
                onGmeSpcUseNativeSampleRateChanged = { putBool(CorePreferenceKeys.GME_SPC_USE_NATIVE_SAMPLE_RATE, it) },
                onVgmPlayLoopCountChanged = { putInt(CorePreferenceKeys.VGMPLAY_LOOP_COUNT, it) },
                onVgmPlayAllowNonLoopingLoopChanged = { putBool(CorePreferenceKeys.VGMPLAY_ALLOW_NON_LOOPING_LOOP, it) },
                onVgmPlayVsyncRateChanged = { putInt(CorePreferenceKeys.VGMPLAY_VSYNC_RATE, it) },
                onVgmPlayResampleModeChanged = { putInt(CorePreferenceKeys.VGMPLAY_RESAMPLE_MODE, it) },
                onVgmPlayChipSampleModeChanged = { putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_MODE, it) },
                onVgmPlayChipSampleRateChanged = { putInt(CorePreferenceKeys.VGMPLAY_CHIP_SAMPLE_RATE, it) },
                onVgmPlayChipCoreChanged = { chipKey, selectedValue ->
                    prefs.edit().putInt(CorePreferenceKeys.vgmPlayChipCoreKey(chipKey), selectedValue).apply()
                    changeToken++
                },
                onOpenMptStereoSeparationPercentChanged = { putInt("openmpt.stereo_separation_percent", it) },
                onOpenMptStereoSeparationAmigaPercentChanged = { putInt("openmpt.stereo_separation_amiga_percent", it) },
                onOpenMptInterpolationFilterLengthChanged = { putInt("openmpt.interpolation_filter_length", it) },
                onOpenMptAmigaResamplerModeChanged = { putInt("openmpt.amiga_resampler_mode", it) },
                onOpenMptAmigaResamplerApplyAllModulesChanged = { putBool("openmpt.amiga_resampler_apply_all_modules", it) },
                onOpenMptVolumeRampingStrengthChanged = { putInt("openmpt.volume_ramping_strength", it) },
                onOpenMptFt2XmVolumeRampingChanged = { putBool("openmpt.ft2_xm_volume_ramping", it) },
                onOpenMptMasterGainMilliBelChanged = { putInt("openmpt.master_gain_millibel", it) },
                onOpenMptSurroundEnabledChanged = { putBool("openmpt.surround_enabled", it) }
            ),
            onAutoPlayOnTrackSelectChanged = { putBool(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, it) },
            onOpenPlayerOnTrackSelectChanged = { putBool(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, it) },
            onAutoPlayNextTrackOnEndChanged = { putBool(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, it) },
            onPreloadNextCachedRemoteTrackChanged = { putBool(AppPreferenceKeys.PRELOAD_NEXT_CACHED_REMOTE_TRACK, it) },
            onPlaylistWrapNavigationChanged = { putBool(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, it) },
            onPreviousRestartsAfterThresholdChanged = { putBool(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, it) },
            onFadePauseResumeChanged = { putBool(AppPreferenceKeys.FADE_PAUSE_RESUME, it) },
            onRespondHeadphoneMediaButtonsChanged = { putBool(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, it) },
            onPauseOnHeadphoneDisconnectChanged = { putBool(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, it) },
            onAudioFocusInterruptChanged = { putBool(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, it) },
            onAudioDuckingChanged = { putBool(AppPreferenceKeys.AUDIO_DUCKING, it) },
            onAudioBackendPreferenceChanged = { putString(AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE, it.storageValue) },
            onAudioPerformanceModeChanged = { putString(AppPreferenceKeys.AUDIO_PERFORMANCE_MODE, it.storageValue) },
            onAudioBufferPresetChanged = { putString(AppPreferenceKeys.AUDIO_BUFFER_PRESET, it.storageValue) },
            onAudioResamplerPreferenceChanged = { putString(AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE, it.storageValue) },
            onAudioOutputLimiterEnabledChanged = {
                putBool(AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED, it)
                runCatching { NativeBridge.setOutputLimiterEnabled(it) }
            },
            onLookaheadClipperModeChanged = {
                putString(AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE, it.storageValue)
                runCatching { NativeBridge.setLookaheadClipperMode(it.nativeValue) }
            },
            onMultiChannelOutputModeChanged = {
                putString(AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE, it.storageValue)
                runCatching { NativeBridge.setMultiChannelOutputMode(it.nativeValue) }
            },
            onAudioAllowBackendFallbackChanged = { putBool(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, it) },
            onBitPerfectUsbAudioChanged = { putBool(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, it) },
            onOpenPlayerFromNotificationChanged = { putBool(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, it) },
            onPersistRepeatModeChanged = { putBool(AppPreferenceKeys.PERSIST_REPEAT_MODE, it) },
            onThemeModeChanged = { putString(AppPreferenceKeys.THEME_MODE, it.storageValue) },
            onUseMonetChanged = { putBool(AppPreferenceKeys.THEME_USE_MONET, it) },
            onRememberBrowserLocationChanged = { putBool(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, it) },
            onShowParentDirectoryEntryChanged = { putBool(AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY, it) },
            onShowFileIconChipBackgroundChanged = { putBool(AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND, it) },
            onSortArchivesBeforeFilesChanged = { putBool(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, it) },
            onBrowserNameSortModeChanged = { putString(AppPreferenceKeys.BROWSER_NAME_SORT_MODE, it.storageValue) },
            onRecentFoldersLimitChanged = { putInt(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, it) },
            onRecentFilesLimitChanged = { putInt(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, it) },
            onPressBackTwiceToExitChanged = { putBool(AppPreferenceKeys.PRESS_BACK_TWICE_TO_EXIT, it) },
            onUrlCacheClearOnLaunchChanged = { putBool(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, it) },
            onUrlCacheMaxTracksChanged = { putInt(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, it) },
            onUrlCacheMaxBytesChanged = { prefs.edit().putLong(AppPreferenceKeys.URL_CACHE_MAX_BYTES, it).apply(); changeToken++ },
            onArchiveCacheClearOnLaunchChanged = { putBool(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, it) },
            onArchiveCacheMaxMountsChanged = { putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, it) },
            onArchiveCacheMaxBytesChanged = { prefs.edit().putLong(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, it).apply(); changeToken++ },
            onArchiveCacheMaxAgeDaysChanged = { putInt(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, it) },
            onClearUrlCacheNow = {
                scope.launch(Dispatchers.IO) {
                    val result = clearRemoteCacheFiles(remoteCacheRoot, protectedCachePaths)
                    refreshCachedSourceFiles()
                    val suffix = if (result.skippedFiles > 0) " (${result.skippedFiles} protected)" else ""
                    toastHandler.showToast("Deleted ${result.deletedFiles} file(s)$suffix")
                }
            },
            onClearArchiveCacheNow = {
                scope.launch(Dispatchers.IO) {
                    val result = clearArchiveMountCache(cacheDir)
                    toastHandler.showToast("Deleted ${result.deletedMounts} mount(s)")
                }
            },
            onRefreshCachedSourceFiles = {
                scope.launch(Dispatchers.IO) {
                    listCachedSourceFiles(remoteCacheRoot)
                    refreshCachedSourceFiles()
                }
            },
            onDeleteCachedSourceFiles = { paths ->
                scope.launch(Dispatchers.IO) {
                    val result = deleteSpecificRemoteCacheFiles(remoteCacheRoot, paths.toSet(), protectedCachePaths)
                    refreshCachedSourceFiles()
                    val suffix = if (result.skippedFiles > 0) " (${result.skippedFiles} protected)" else ""
                    toastHandler.showToast("Deleted ${result.deletedFiles} file(s)$suffix")
                }
            },
            onExportCachedSourceFiles = { paths ->
                val files = paths.mapNotNull { path ->
                    File(path).takeIf { it.exists() && it.isFile }
                }
                if (files.isEmpty()) {
                    toastHandler.showToast("No files selected")
                } else {
                    fileExportHandler.exportFiles(files)
                }
            },
            onKeepScreenOnChanged = { putBool(AppPreferenceKeys.KEEP_SCREEN_ON, it) },
            onPlayerArtworkCornerRadiusDpChanged = { putInt(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, it) },
            onShowAudioOutputRouteChipChanged = { putBool(AppPreferenceKeys.PLAYER_SHOW_AUDIO_OUTPUT_CHIP, it) },
            onCanvasTapToSeekSecondsChanged = { putInt(AppPreferenceKeys.CANVAS_TAP_TO_SEEK_SECONDS, it) },
            onFilenameDisplayModeChanged = { putString(AppPreferenceKeys.FILENAME_DISPLAY_MODE, it.storageValue) },
            onFilenameOnlyWhenTitleMissingChanged = { putBool(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, it) },
            onUnknownTrackDurationSecondsChanged = { putInt(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, it) },
            onEndFadeApplyToAllTracksChanged = { putBool(AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS, it) },
            onEndFadeDurationMsChanged = { putInt(AppPreferenceKeys.END_FADE_DURATION_MS, it) },
            onEndFadeCurveChanged = { putString(AppPreferenceKeys.END_FADE_CURVE, it.storageValue) },
            onVisualizationModeChanged = { putString(AppPreferenceKeys.VISUALIZATION_MODE, it.storageValue) },
            onEnabledVisualizationModesChanged = { modes ->
                prefs.edit()
                    .putString(
                        AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                        serializeEnabledVisualizationModes(modes)
                    )
                    .apply()
                changeToken++
            },
            onVisualizationPerformanceModeChanged = { putString(AppPreferenceKeys.VISUALIZATION_PERFORMANCE_MODE, it.storageValue) },
            onVisualizationShowDebugInfoChanged = { putBool(AppPreferenceKeys.VISUALIZATION_SHOW_DEBUG_INFO, it) },
            onVisualizationKeepScreenOnChanged = { putBool(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, it) },
            onVisualizationBarCountChanged = { putInt(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, it) },
            onVisualizationBarSmoothingPercentChanged = { putInt(AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT, it) },
            onVisualizationBarRoundnessDpChanged = { putInt(AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP, it) },
            onVisualizationBarOverlayArtworkChanged = { putBool(AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK, it) },
            onVisualizationBarUseThemeColorChanged = { putBool(AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR, it) },
            onVisualizationBarRenderBackendChanged = { putString(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, it.storageValue) },
            onVisualizationOscStereoChanged = { putBool(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, it) },
            onVisualizationVuAnchorChanged = { putString(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, it.storageValue) },
            onVisualizationVuUseThemeColorChanged = { putBool(AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR, it) },
            onVisualizationVuSmoothingPercentChanged = { putInt(AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT, it) },
            onVisualizationVuRenderBackendChanged = { putString(AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND, it.storageValue) },
            onResetVisualizationBarsSettings = {
                resetVisualizationBarsSettings(prefs)
                changeToken++
            },
            onResetVisualizationOscilloscopeSettings = {
                resetVisualizationOscilloscopeSettings(prefs)
                changeToken++
            },
            onResetVisualizationVuSettings = {
                resetVisualizationVuSettings(prefs)
                changeToken++
            },
            onResetVisualizationChannelScopeSettings = {
                resetVisualizationChannelScopeSettings(prefs, defaultScopeTextSizeSp)
                changeToken++
            },
            onResetVisualizationProjectMSettings = {
                resetVisualizationProjectMSettings(prefs, setOf("internal_projectm_tests"))
                changeToken++
            },
            onClearRecentHistory = {
                val configDir = DesktopPaths.configDir()
                deleteDomainFile(configDir, AppPreferenceKeys.RECENT_FOLDERS)
                deleteDomainFile(configDir, AppPreferenceKeys.RECENT_PLAYED_FILES)
                prefs.edit()
                    .remove(AppPreferenceKeys.RECENT_FOLDERS)
                    .remove(AppPreferenceKeys.RECENT_PLAYED_FILES)
                    .apply()
                changeToken++
            },
            onClearSavedNetworkSources = {
                val configDir = DesktopPaths.configDir()
                deleteStoreFile(networkNodesFile(configDir))
                deleteStoreFile(networkCredentialsFile(configDir))
                prefs.edit()
                    .remove(AppPreferenceKeys.NETWORK_SAVED_NODES)
                    .remove(AppPreferenceKeys.NETWORK_CREDENTIALS_JSON)
                    .apply()
                changeToken++
            },
            onClearAllSettings = {
                prefs.edit().clear().apply()
                changeToken++
            },
            onClearAllPluginSettings = {
                changeToken++
            },
            onResetPluginSettings = { _ ->
                changeToken++
            }
        )
    }

    return Pair(state, actions)
}
