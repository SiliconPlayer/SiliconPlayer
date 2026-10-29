package com.flopster101.siliconplayer.settings.porting

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.AudioResamplerPreference
import com.flopster101.siliconplayer.GmeDefaults
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.PlaylistSortMode
import com.flopster101.siliconplayer.RECENT_FILES_LIMIT_DEFAULT
import com.flopster101.siliconplayer.RECENT_FOLDERS_LIMIT_DEFAULT
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.SOURCE_CACHE_MAX_BYTES_DEFAULT
import com.flopster101.siliconplayer.SOURCE_CACHE_MAX_TRACKS_DEFAULT
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.ThemeMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.isAvailableOnCurrentPlatform
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.usb.DirectUacVolumeMode

private fun generalAudioKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.AUDIO_DUCKING, true),
    boolV(AppPreferenceKeys.AUDIO_FOCUS_INTERRUPT, true),
    boolV(AppPreferenceKeys.AUDIO_FORCE_MONO, false),
    stringV(AppPreferenceKeys.AUDIO_LOOKAHEAD_CLIPPER_MODE, AppDefaults.AudioProcessing.lookaheadClipperMode.storageValue),
    stringV(AppPreferenceKeys.AUDIO_MULTI_CHANNEL_OUTPUT_MODE, AppDefaults.OutputPipeline.multiChannelOutputMode.storageValue),
    boolV(AppPreferenceKeys.AUDIO_OUTPUT_LIMITER_ENABLED, AppDefaults.AudioProcessing.outputLimiterEnabled),
    stringV(AppPreferenceKeys.AUDIO_RESAMPLER_PREFERENCE, AudioResamplerPreference.BuiltIn.storageValue)
)

private fun generalVolumeKeys(): List<TypedPrefValue> = listOf(
    floatV(AppPreferenceKeys.AUDIO_MASTER_VOLUME_DB, 0f)
)

private fun audioBackendKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.AUDIO_ALLOW_BACKEND_FALLBACK, true),
    stringV(AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE, "auto"),
    stringV(AppPreferenceKeys.AUDIO_BUFFER_PRESET, "", onlyWhenSet = true),
    stringV(AppPreferenceKeys.BIT_PERFECT_DRIVER_METHOD, "", onlyWhenSet = true),
    boolV(AppPreferenceKeys.BIT_PERFECT_USB_AUDIO, false),
    floatV(AppPreferenceKeys.DIRECT_UAC_MANUAL_VOLUME, 1.0f),
    stringV(AppPreferenceKeys.DIRECT_UAC_VOLUME_MODE, DirectUacVolumeMode.System.storageValue),
    boolV(AppPreferenceKeys.PLATFORM_DOLBY_DECODER, true),
    boolV(AppPreferenceKeys.PLATFORM_DOLBY_PARALLEL_VIS, true),
    intV(AppPreferenceKeys.PLATFORM_DOLBY_VIS_SOURCE, 0),
    boolV(AppPreferenceKeys.UAC_SETTLE_PILOT_TONE, false)
)

private fun audioBackendEntry(): PortingNode = holder(
    "audio.backend",
    "Backend configuration",
    audioBackendKeys(),
    defaultSelected = false,
    isKeyImportable = { key, _, value, _ ->
        key != AppPreferenceKeys.AUDIO_BACKEND_PREFERENCE ||
            com.flopster101.siliconplayer.AudioBackendPreference
                .fromStorage(value as? String)
                .isAvailableOnCurrentPlatform()
    }
)

private fun sectionRoot(
    id: String,
    label: String,
    children: List<PortingNode>
): PortingNode.EntryHolder = holder(
    id = id,
    label = label,
    keys = emptyList(),
    children = children.filterIsInstance<PortingNode.EntryHolder>()
)

private fun generalAudioEffectsKeys(): List<TypedPrefValue> = listOf(
    intV(AppPreferenceKeys.AUDIO_DSP_BASS_DEPTH, AppDefaults.AudioProcessing.Dsp.bassDepth),
    boolV(AppPreferenceKeys.AUDIO_DSP_BASS_ENABLED, AppDefaults.AudioProcessing.Dsp.bassEnabled),
    intV(AppPreferenceKeys.AUDIO_DSP_BASS_RANGE, AppDefaults.AudioProcessing.Dsp.bassRange),
    intV(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_BITS, AppDefaults.AudioProcessing.Dsp.bitCrushBits),
    boolV(AppPreferenceKeys.AUDIO_DSP_BITCRUSH_ENABLED, AppDefaults.AudioProcessing.Dsp.bitCrushEnabled),
    intV(AppPreferenceKeys.AUDIO_DSP_REVERB_DEPTH, AppDefaults.AudioProcessing.Dsp.reverbDepth),
    boolV(AppPreferenceKeys.AUDIO_DSP_REVERB_ENABLED, AppDefaults.AudioProcessing.Dsp.reverbEnabled),
    intV(AppPreferenceKeys.AUDIO_DSP_REVERB_PRESET, AppDefaults.AudioProcessing.Dsp.reverbPreset),
    intV(AppPreferenceKeys.AUDIO_DSP_SURROUND_DELAY_MS, AppDefaults.AudioProcessing.Dsp.surroundDelayMs),
    intV(AppPreferenceKeys.AUDIO_DSP_SURROUND_DEPTH, AppDefaults.AudioProcessing.Dsp.surroundDepth),
    boolV(AppPreferenceKeys.AUDIO_DSP_SURROUND_ENABLED, AppDefaults.AudioProcessing.Dsp.surroundEnabled)
)

private fun librarySettingKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.LIBRARY_SHOW_FAVORITES_IN_PLAYLIST_CHOOSER, false)
)

private fun libraryLayoutKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.FAVORITES_SORT_MODE, "", onlyWhenSet = true),
    stringV(AppPreferenceKeys.LIBRARY_PLAYLIST_SORT_MODE, PlaylistSortMode.RecentlyUpdated.storageValue),
    boolV(AppPreferenceKeys.PLAYLIST_AUTO_MOSAIC, true),
    stringV(AppPreferenceKeys.PLAYLIST_COVER_GENERATION_MODE, "", onlyWhenSet = true)
)

private fun playerKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.AUTO_PLAY_NEXT_TRACK_ON_END, true),
    boolV(AppPreferenceKeys.AUTO_PLAY_ON_TRACK_SELECT, true),
    intV(AppPreferenceKeys.CANVAS_TAP_TO_SEEK_SECONDS, AppDefaults.Player.canvasTapToSeekSeconds),
    boolV(AppPreferenceKeys.KEEP_SCREEN_ON, AppDefaults.Player.keepScreenOn),
    boolV(AppPreferenceKeys.OPEN_PLAYER_FROM_NOTIFICATION, true),
    boolV(AppPreferenceKeys.OPEN_PLAYER_ON_TRACK_SELECT, true),
    boolV(AppPreferenceKeys.PAUSE_ON_HEADPHONE_DISCONNECT, true),
    boolV(AppPreferenceKeys.PERSIST_REPEAT_MODE, true),
    boolV(AppPreferenceKeys.PLAY_WITH_EXTERNAL_OPEN_DIALOG, true),
    boolV(AppPreferenceKeys.PLAY_WITH_SHOW_DISABLED_CORES, true),
    boolV(AppPreferenceKeys.PLAYLIST_WRAP_NAVIGATION, true),
    stringV(AppPreferenceKeys.PREFERRED_REPEAT_MODE, RepeatMode.None.storageValue),
    boolV(AppPreferenceKeys.PRELOAD_NEXT_CACHED_REMOTE_TRACK, AppDefaults.Player.preloadNextCachedRemoteTrack),
    boolV(AppPreferenceKeys.PRESS_BACK_TWICE_TO_EXIT, AppDefaults.Home.pressBackTwiceToExit),
    boolV(AppPreferenceKeys.PREVIOUS_RESTART_AFTER_THRESHOLD, true),
    boolV(AppPreferenceKeys.RESPOND_HEADPHONE_MEDIA_BUTTONS, true),
    boolV(AppPreferenceKeys.END_FADE_APPLY_TO_ALL_TRACKS, AppDefaults.Player.endFadeApplyToAllTracks),
    stringV(AppPreferenceKeys.END_FADE_CURVE, AppDefaults.Player.endFadeCurve.storageValue),
    intV(AppPreferenceKeys.END_FADE_DURATION_MS, AppDefaults.Player.endFadeDurationMs),
    boolV(AppPreferenceKeys.FADE_PAUSE_RESUME, true),
    intV(AppPreferenceKeys.UNKNOWN_TRACK_DURATION_SECONDS, GmeDefaults.unknownDurationSeconds),
    stringV(AppPreferenceKeys.PLAYLIST_LIBRARY_JSON, "", onlyWhenSet = true)
)

private fun homeSettingKeys(): List<TypedPrefValue> = listOf(
    intV(AppPreferenceKeys.RECENT_FOLDERS_LIMIT, RECENT_FOLDERS_LIMIT_DEFAULT),
    intV(AppPreferenceKeys.RECENT_PLAYED_FILES_LIMIT, RECENT_FILES_LIMIT_DEFAULT),
    boolV(AppPreferenceKeys.RECENTS_PER_SUBTUNE_ROWS, false)
)

private fun browserKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.BROWSER_NAME_SORT_MODE, AppDefaults.Browser.nameSortMode.storageValue),
    boolV(AppPreferenceKeys.BROWSER_SHOW_FILE_ICON_CHIP_BACKGROUND, AppDefaults.Browser.showFileIconChipBackground),
    boolV(AppPreferenceKeys.BROWSER_SHOW_HIDDEN_FILES_AND_FOLDERS, AppDefaults.Browser.showHiddenFilesAndFolders),
    boolV(AppPreferenceKeys.BROWSER_SHOW_LOCAL_THUMBNAIL_PREVIEWS, AppDefaults.Browser.showLocalThumbnailPreviews),
    boolV(AppPreferenceKeys.BROWSER_SHOW_PARENT_DIRECTORY_ENTRY, AppDefaults.Browser.showParentDirectoryEntry),
    boolV(AppPreferenceKeys.BROWSER_SHOW_PREVIEW_FILES, AppDefaults.Browser.showPreviewFiles),
    boolV(AppPreferenceKeys.BROWSER_SHOW_UNSUPPORTED_FILES, AppDefaults.Browser.showUnsupportedFiles),
    boolV(AppPreferenceKeys.BROWSER_SORT_ARCHIVES_BEFORE_FILES, false),
    boolV(AppPreferenceKeys.REMEMBER_BROWSER_LOCATION, true)
)

private fun browserLastKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.BROWSER_LAST_DIRECTORY_PATH, "", onlyWhenSet = true),
    stringV(AppPreferenceKeys.BROWSER_LAST_LOCATION_ID, "", onlyWhenSet = true),
    stringV(AppPreferenceKeys.BROWSER_LAST_LOCATION_STATE_JSON, "", onlyWhenSet = true)
)

private fun networkSourcesKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.NETWORK_SAVED_NODES, "", onlyWhenSet = true)
)

private fun vizSettingKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.VISUALIZATION_FULLSCREEN_MODE, "", onlyWhenSet = true),
    boolV(AppPreferenceKeys.VISUALIZATION_KEEP_SCREEN_ON, AppDefaults.Visualization.keepScreenOn),
    stringV(AppPreferenceKeys.VISUALIZATION_PERFORMANCE_MODE, AppDefaults.Visualization.performanceMode.storageValue)
)

private fun barVizKeys(): List<TypedPrefValue> = listOf(
    intV(AppPreferenceKeys.VISUALIZATION_BAR_COUNT, AppDefaults.Visualization.Bars.count),
    boolV(AppPreferenceKeys.VISUALIZATION_BAR_OVERLAY_ARTWORK, AppDefaults.Visualization.Bars.overlayArtwork),
    stringV(AppPreferenceKeys.VISUALIZATION_BAR_RENDER_BACKEND, AppDefaults.Visualization.Bars.renderBackend.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_BAR_ROUNDNESS_DP, AppDefaults.Visualization.Bars.roundnessDp),
    intV(AppPreferenceKeys.VISUALIZATION_BAR_SMOOTHING_PERCENT, AppDefaults.Visualization.Bars.smoothingPercent),
    boolV(AppPreferenceKeys.VISUALIZATION_BAR_USE_THEME_COLOR, AppDefaults.Visualization.Bars.useThemeColor)
)

private fun oscilloscopeVizKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.VISUALIZATION_OSC_STEREO, AppDefaults.Visualization.Oscilloscope.stereo)
)

private fun vuVizKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.VISUALIZATION_VU_ANCHOR, AppDefaults.Visualization.Vu.anchor.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_VU_RENDER_BACKEND, AppDefaults.Visualization.Vu.renderBackend.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_VU_SMOOTHING_PERCENT, AppDefaults.Visualization.Vu.smoothingPercent),
    boolV(AppPreferenceKeys.VISUALIZATION_VU_USE_THEME_COLOR, AppDefaults.Visualization.Vu.useThemeColor)
)

private fun channelScopeVizKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_ANTIALIAS_METHOD, AppDefaults.Visualization.ChannelScope.antialiasMethod.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE, AppDefaults.Visualization.ChannelScope.backgroundMode.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED, AppDefaults.Visualization.ChannelScope.centerLineEnabled),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CONTRAST_BACKDROP_ENABLED, AppDefaults.Visualization.ChannelScope.contrastBackdropEnabled),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB, AppDefaults.Visualization.ChannelScope.customBackgroundColorArgb),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB, AppDefaults.Visualization.ChannelScope.customGridColorArgb),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB, AppDefaults.Visualization.ChannelScope.customLineColorArgb),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_TEXT_COLOR_ARGB, AppDefaults.Visualization.ChannelScope.customTextColorArgb),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED, AppDefaults.Visualization.ChannelScope.dcRemovalEnabled),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_FPS_MODE, AppDefaults.Visualization.ChannelScope.fpsMode.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT, AppDefaults.Visualization.ChannelScope.gainPercent),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK, AppDefaults.Visualization.ChannelScope.gridColorModeNoArtwork.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK, AppDefaults.Visualization.ChannelScope.gridColorModeWithArtwork.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP, AppDefaults.Visualization.ChannelScope.gridWidthDp),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LAYOUT, AppDefaults.Visualization.ChannelScope.layout.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK, AppDefaults.Visualization.ChannelScope.lineColorModeNoArtwork.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK, AppDefaults.Visualization.ChannelScope.lineColorModeWithArtwork.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP, AppDefaults.Visualization.ChannelScope.lineWidthDp),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND, AppDefaults.Visualization.ChannelScope.renderBackend.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND, AppDefaults.Visualization.ChannelScope.showArtworkBackground),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR, AppDefaults.Visualization.ChannelScope.textAnchor.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_COLOR_MODE, AppDefaults.Visualization.ChannelScope.textColorMode.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED, AppDefaults.Visualization.ChannelScope.textEnabled),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_FONT, AppDefaults.Visualization.ChannelScope.textFont.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_HIDE_WHEN_OVERFLOW, AppDefaults.Visualization.ChannelScope.textHideWhenOverflow),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT, AppDefaults.Visualization.ChannelScope.textNoteFormat.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP, AppDefaults.Visualization.ChannelScope.textPaddingDp),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHADOW_ENABLED, AppDefaults.Visualization.ChannelScope.textShadowEnabled),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL, AppDefaults.Visualization.ChannelScope.textShowChannel),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE, AppDefaults.Visualization.ChannelScope.textShowNote),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP, AppDefaults.Visualization.ChannelScope.textSizeSp),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ANCHOR, AppDefaults.Visualization.ChannelScope.textVuAnchor.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_COLOR_MODE, AppDefaults.Visualization.ChannelScope.textVuColorMode.storageValue),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_CUSTOM_COLOR_ARGB, AppDefaults.Visualization.ChannelScope.textVuCustomColorArgb),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ENABLED, AppDefaults.Visualization.ChannelScope.textVuEnabled),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRACK_TRANSITION, AppDefaults.Visualization.ChannelScope.trackTransition.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_ALGORITHM, AppDefaults.Visualization.ChannelScope.triggerAlgorithm.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE, AppDefaults.Visualization.ChannelScope.triggerMode.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED, AppDefaults.Visualization.ChannelScope.verticalGridEnabled),
    stringV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVE_RENDER_MODE, AppDefaults.Visualization.ChannelScope.waveRenderMode.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVEFORM_CLIPPING_ENABLED, AppDefaults.Visualization.ChannelScope.waveformClippingEnabled),
    intV(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS, AppDefaults.Visualization.ChannelScope.windowMs)
)

private fun starfieldVizKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET, StarfieldPreset.ClassicAmiga.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, AppDefaults.Visualization.Starfield.beatFollowEnabled),
    boolV(AppPreferenceKeys.VISUALIZATION_STARFIELD_MONOCHROME_BACKDROP_ENABLED, AppDefaults.Visualization.Starfield.monochromeBackdropEnabled),
    stringV(AppPreferenceKeys.VISUALIZATION_STARFIELD_RENDER_BACKEND, AppDefaults.Visualization.Starfield.renderBackend.storageValue)
)

private fun projectMVizKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, AppDefaults.Visualization.ProjectM.aspectCorrection),
    stringSetV(AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS, onlyWhenSet = true),
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, AppDefaults.Visualization.ProjectM.fpsMode.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled),
    floatV(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity),
    intV(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, AppDefaults.Visualization.ProjectM.meshSize),
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET, "", onlyWhenSet = true),
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString()),
    boolV(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true),
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_RENDER_BACKEND, AppDefaults.Visualization.ProjectM.renderBackend.storageValue),
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_RENDER_RESOLUTION, AppDefaults.Visualization.ProjectM.renderResolution.storageValue),
    boolV(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom)
)

private fun projectMPresetPathKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.VISUALIZATION_PROJECTM_USER_PRESET_PATHS, "", onlyWhenSet = true)
)

private fun cacheKeys(): List<TypedPrefValue> = listOf(
    boolV(AppPreferenceKeys.ARCHIVE_CACHE_CLEAR_ON_LAUNCH, false),
    intV(AppPreferenceKeys.ARCHIVE_CACHE_MAX_AGE_DAYS, com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_AGE_DAYS_DEFAULT),
    longV(AppPreferenceKeys.ARCHIVE_CACHE_MAX_BYTES, com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_BYTES_DEFAULT),
    intV(AppPreferenceKeys.ARCHIVE_CACHE_MAX_MOUNTS, com.flopster101.siliconplayer.data.ARCHIVE_CACHE_MAX_MOUNTS_DEFAULT),
    boolV(AppPreferenceKeys.FILE_CACHE_CLEAR_ON_LAUNCH, false),
    longV(AppPreferenceKeys.FILE_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT),
    intV(AppPreferenceKeys.FILE_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT),
    boolV(AppPreferenceKeys.STREAMING_CACHE_CLEAR_ON_LAUNCH, false),
    longV(AppPreferenceKeys.STREAMING_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT),
    intV(AppPreferenceKeys.STREAMING_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT),
    boolV(AppPreferenceKeys.URL_CACHE_CLEAR_ON_LAUNCH, false),
    longV(AppPreferenceKeys.URL_CACHE_MAX_BYTES, SOURCE_CACHE_MAX_BYTES_DEFAULT),
    intV(AppPreferenceKeys.URL_CACHE_MAX_TRACKS, SOURCE_CACHE_MAX_TRACKS_DEFAULT),
    boolV(AppPreferenceKeys.URL_PATH_FORCE_CACHING, false)
)

private fun uiKeys(): List<TypedPrefValue> = listOf(
    stringV(AppPreferenceKeys.FILENAME_DISPLAY_MODE, AppDefaults.Player.filenameDisplayMode.storageValue),
    boolV(AppPreferenceKeys.FILENAME_ONLY_WHEN_TITLE_MISSING, false),
    intV(AppPreferenceKeys.PLAYER_ARTWORK_CORNER_RADIUS_DP, AppDefaults.Player.artworkCornerRadiusDp),
    boolV(AppPreferenceKeys.PLAYER_SHOW_AUDIO_OUTPUT_CHIP, AppDefaults.Player.showAudioOutputRouteChip),
    stringV(AppPreferenceKeys.THEME_MODE, ThemeMode.Auto.storageValue),
    boolV(AppPreferenceKeys.THEME_USE_MONET, false)
)

private fun visualizationLastUsedEntry(): PortingNode = holder(
    "viz.last",
    "Last used mode",
    listOf(
        stringV(AppPreferenceKeys.VISUALIZATION_ADVANCED_FALLBACK_MODE, VisualizationMode.Bars.storageValue),
        stringV(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES, "", onlyWhenSet = true),
        boolV(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED, false),
        boolV(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED, false),
        stringV(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue),
        stringV(AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE, "", onlyWhenSet = true)
    ),
    defaultSelected = false
)

private fun tickerEntry(): PortingNode = holder(
    "viz.ticker",
    "Ticker",
    listOf(
        intV(AppPreferenceKeys.VISUALIZATION_TICKER_DURATION_SECONDS, 5),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_BARS, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_CHANNEL_SCOPE, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_OSCILLOSCOPE, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_PROJECTM, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_STARFIELD, true),
        boolV(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_VU_METERS, true)
    )
)
/**
 * The full export/import tree, rebuilt per invocation so dynamically
 * registered cores, per-core keys and per-core visualization keys can
 * never go stale.
 */
internal fun buildSettingsPortingCatalog(prefs: AppPreferences): List<PortingSection> {
    val decoders = NativeBridge.getRegisteredDecoderNames().toList()

    val coreSettingsChildren = coreSettingsPortingDecoders(prefs).map { (name, keys) ->
        holder(
            "core.settings.${sanitizeDecoderId(name)}",
            name,
            keys,
            isKeyImportable = { _, _, _, platform ->
                isDecoderSupportedOnPortingPlatform(name, platform)
            }
        )
    }

    val audioChildren = mutableListOf<PortingNode>(
        PortingNode.Label("Audio"),
        holder("audio.general", "General audio settings", generalAudioKeys(), defaultSelected = false),
        holder("audio.volume", "Saved volume parameters", generalVolumeKeys()),
        audioBackendEntry(),
        holder("audio.effects", "Audio effects", generalAudioEffectsKeys())
    )
    audioChildren += perCoreDspPortingEntries(prefs)
    audioChildren += perCoreVolumePortingEntries(decoders)

    val audioCoresPriorityRoot = holder(
        "audio.cores.priority",
        "Audio cores priority",
        emptyList(),
        children = listOf(
            coresOrderEntry(decoders) as PortingNode.EntryHolder,
            coresExtensionsEntry(decoders) as PortingNode.EntryHolder
        )
    )
    val coreSettingsRoot = holder(
        "core.settings",
        "Core settings",
        emptyList(),
        children = coreSettingsChildren
    )
    val audioCoresRoot = holder(
        "audio.cores",
        "Audio cores",
        emptyList(),
        children = listOf(
            coresEnabledStateEntry(decoders) as PortingNode.EntryHolder,
            coreSettingsRoot
        )
    )

    val vizChildren = mutableListOf<PortingNode>(
        PortingNode.Label("Visualizations"),
        visualizationLastUsedEntry(),
        tickerEntry(),
        holder("viz.settings", "Performance and shared", vizSettingKeys()),
        holder("viz.bars", "Bars", barVizKeys()),
        holder("viz.osc", "Oscilloscope", oscilloscopeVizKeys()),
        holder("viz.vu", "VU meters", vuVizKeys()),
        holder("viz.chscope", "Channel scope", channelScopeVizKeys())
    )
    vizChildren += perCoreVizTextVisibilityEntries(prefs)
    vizChildren += listOf(
        holder("viz.starfield", "Starfield", starfieldVizKeys()),
        holder("viz.projectm", "projectM", projectMVizKeys()),
        holder(
            "viz.projectm.paths",
            "projectM preset paths",
            projectMPresetPathKeys(),
            defaultSelected = false
        )
    )

    val browserChildren = mutableListOf<PortingNode>(
        PortingNode.Label("File browser"),
        holder("browser.settings", "File browser settings", browserKeys()),
        holder(
            "browser.last",
            "Remembered location",
            browserLastKeys(),
            defaultSelected = false
        )
    )

    val networkChildren = mutableListOf<PortingNode>(
        PortingNode.Label("Network"),
        holder(
            "network.sources",
            "Saved sources",
            networkSourcesKeys(),
            defaultSelected = false
        )
    )

    return listOf(
        PortingSection(
            id = "audioCoresPriority",
            label = "Audio cores priority",
            children = listOf(audioCoresPriorityRoot)
        ),
        PortingSection(
            id = "audioCores",
            label = "Audio cores",
            children = listOf(audioCoresRoot)
        ),
        PortingSection(
            id = "audio",
            label = "Audio",
            children = listOf(sectionRoot("audio", "Audio", audioChildren))
        ),
        PortingSection(
            id = "library",
            label = "Library",
            children = listOf(
                sectionRoot(
                    "library",
                    "Library",
                    listOf(
                        holder("library.settings", "Library settings", librarySettingKeys()),
                        holder("library.layout", "Library layout", libraryLayoutKeys())
                    )
                )
            )
        ),
        PortingSection(
            id = "player",
            label = "Player",
            children = listOf(
                sectionRoot(
                    "player",
                    "Player",
                    listOf(holder("player.settings", "Player settings", playerKeys()))
                )
            )
        ),
        PortingSection(
            id = "home",
            label = "Home",
            children = listOf(
                sectionRoot(
                    "home",
                    "Home",
                    listOf(holder("home.settings", "Home settings", homeSettingKeys()))
                )
            )
        ),
        PortingSection(
            id = "fileBrowser",
            label = "File browser",
            children = listOf(sectionRoot("fileBrowser", "File browser", browserChildren))
        ),
        PortingSection(
            id = "network",
            label = "Network",
            children = listOf(sectionRoot("network", "Network", networkChildren))
        ),
        PortingSection(
            id = "visualizations",
            label = "Visualizations",
            children = listOf(sectionRoot("visualizations", "Visualizations", vizChildren))
        ),
        PortingSection(
            id = "cache",
            label = "Cache",
            children = listOf(
                sectionRoot(
                    "cache",
                    "Cache",
                    listOf(holder("cache.settings", "Cache settings", cacheKeys()))
                )
            )
        ),
        PortingSection(
            id = "ui",
            label = "UI",
            children = listOf(
                sectionRoot("ui", "UI", listOf(holder("ui.settings", "UI settings", uiKeys())))
            )
        )
    )
}

private const val CHANNEL_SCOPE_TEXT_VISIBLE_PREFIX = "visualization_channel_scope_text_visible_"

internal fun perCoreVizTextVisibilityEntries(prefs: AppPreferences): List<PortingNode> {
    val nodes = mutableListOf<PortingNode>()
    prefs.allKeys().filter { it.startsWith(CHANNEL_SCOPE_TEXT_VISIBLE_PREFIX) }
        .groupBy { it.removePrefix(CHANNEL_SCOPE_TEXT_VISIBLE_PREFIX).substringBefore('_') }
        .forEach { (coreToken, keys) ->
            nodes.add(
                holder(
                    "viz.chscope.visibility.$coreToken",
                    "Channel scope text visibility: $coreToken",
                    keys.map { boolV(it, false) },
                    defaultSelected = false
                )
            )
        }
    return nodes
}

internal fun sanitizeDecoderId(name: String): String = buildString {
    name.forEach { c -> if (c.isLetterOrDigit()) append(c.lowercaseChar()) }
}.ifEmpty { "unknown" }

private fun priorityKey(key: String) = TypedPrefValue(key, PrefType.Int, { 0 }, true)

internal fun coresOrderEntry(decoders: List<String>): PortingNode =
    holder(
        "cores.order",
        "Audio cores order",
        decoders.map { priorityKey(AppPreferenceKeys.decoderPriorityKey(it)) }
    )

internal fun coresExtensionsEntry(decoders: List<String>): PortingNode =
    holder(
        "cores.extensions",
        "Audio cores extensions",
        decoders.map {
            stringSetV(
                AppPreferenceKeys.decoderEnabledExtensionsKey(it),
                onlyWhenSet = true
            )
        }
    )

internal fun longV(key: String, default: Long, onlyWhenSet: Boolean = false) =
    TypedPrefValue(key, PrefType.Long, { default }, onlyWhenSet)

/**
 * Re-pushes decoder state (enabled/priority/extensions) into the native
 * engine after an import touched those preference keys.
 */
internal fun pushDecoderStateToEngine(prefs: AppPreferences) {
    for (name in NativeBridge.getRegisteredDecoderNames()) {
        val enabledKey = AppPreferenceKeys.decoderEnabledKey(name)
        if (prefs.contains(enabledKey)) {
            NativeBridge.setDecoderEnabled(name, prefs.getBoolean(enabledKey, true))
        }
        val priorityKey = AppPreferenceKeys.decoderPriorityKey(name)
        if (prefs.contains(priorityKey)) {
            NativeBridge.setDecoderPriority(name, prefs.getInt(priorityKey, 0))
        }
        val extensionsKey = AppPreferenceKeys.decoderEnabledExtensionsKey(name)
        if (prefs.contains(extensionsKey)) {
            val set = prefs.getStringSet(extensionsKey, emptySet()) ?: emptySet()
            NativeBridge.setDecoderEnabledExtensions(name, set.toTypedArray())
        }
    }
}

internal fun persistDecoderStateAfterImport(prefs: AppPreferences) {
    com.flopster101.siliconplayer.persistAllPluginConfigurations(prefs)
}
