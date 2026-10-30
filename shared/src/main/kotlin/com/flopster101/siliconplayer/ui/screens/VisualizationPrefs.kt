package com.flopster101.siliconplayer.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationChannelScopeTriggerAlgorithm
import com.flopster101.siliconplayer.VisualizationChannelScopeTrackTransition
import com.flopster101.siliconplayer.VisualizationChannelScopeWaveRenderMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationOscTriggerMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.readChannelScopeVisibleElementSelection





data class ChannelScopePrefs(
    val windowMs: Int,
    val renderBackend: VisualizationRenderBackend,
    val dcRemovalEnabled: Boolean,
    val waveformClippingEnabled: Boolean = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.waveformClippingEnabled,
    val gainPercent: Int,
    val contrastBackdropEnabled: Boolean,
    val triggerModeNative: Int,
    val triggerAlgorithmNative: Int,
    val waveRenderMode: VisualizationChannelScopeWaveRenderMode,
    val antialiasMethod: VisualizationChannelScopeAntialiasMethod = com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.antialiasMethod,
    val trackTransition: VisualizationChannelScopeTrackTransition,
    val fpsMode: VisualizationOscFpsMode,
    val lineWidthDp: Int,
    val gridWidthDp: Int,
    val verticalGridEnabled: Boolean,
    val centerLineEnabled: Boolean,
    val layout: VisualizationChannelScopeLayout,
    val lineColorModeNoArtwork: VisualizationOscColorMode,
    val gridColorModeNoArtwork: VisualizationOscColorMode,
    val lineColorModeWithArtwork: VisualizationOscColorMode,
    val gridColorModeWithArtwork: VisualizationOscColorMode,
    val customLineColorArgb: Int,
    val customGridColorArgb: Int,
    val showArtworkBackground: Boolean,
    val backgroundMode: VisualizationChannelScopeBackgroundMode,
    val customBackgroundColorArgb: Int,
    val textEnabled: Boolean,
    val textAnchor: VisualizationChannelScopeTextAnchor,
    val textPaddingDp: Int,
    val textSizeSp: Int,
    val textHideWhenOverflow: Boolean,
    val textShadowEnabled: Boolean,
    val textFont: VisualizationChannelScopeTextFont,
    val textColorMode: VisualizationChannelScopeTextColorMode,
    val customTextColorArgb: Int,
    val textNoteFormat: VisualizationNoteNameFormat,
    val textShowChannel: Boolean,
    val textShowNote: Boolean,
    val textVisibleElementSelection: Set<String>,
    val textVuEnabled: Boolean,
    val textVuAnchor: VisualizationVuAnchor,
    val textVuColorMode: VisualizationChannelScopeTextColorMode,
    val textVuCustomColorArgb: Int
) {
    companion object
}

internal data class StarfieldPrefs(
    val renderBackend: VisualizationRenderBackend,
    val starCount: Int,
    val speed: Float,
    val fov: Float,
    val nearPlane: Float,
    val starColorArgb: Int,
    val baseSizePx: Float,
    val sizeGrowth: Float,
    val farDim: Float,
    val softness: Float,
    val beatGlow: Float,
    val glowSize: Float,
    val trailPersistence: Float,
    val streaks: Boolean,
    val streakLength: Float,
    val centerX: Float,
    val centerY: Float,
    val autoDrift: Boolean,
    val beatFollow: Boolean,
    val reactSpeed: Float,
    val flash: Float,
    val contrastBackdropEnabled: Boolean,
    val monochromeBackdrop: Boolean,
    val square: Boolean
) {
    companion object
}

internal data class FullscreenTickerPrefs(
    val masterEnabled: Boolean,
    val durationSeconds: Int,
    val perModeEnabled: Map<VisualizationMode, Boolean>
) {
    fun isEnabledFor(mode: VisualizationMode): Boolean {
        return masterEnabled && (perModeEnabled[mode] ?: true)
    }

    companion object {
        fun from(prefs: AppPreferences): FullscreenTickerPrefs {
            val perMode = mapOf(
                VisualizationMode.Bars to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_BARS, true),
                VisualizationMode.Oscilloscope to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_OSCILLOSCOPE, true),
                VisualizationMode.VuMeters to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_VU_METERS, true),
                VisualizationMode.ChannelScope to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_CHANNEL_SCOPE, true),
                VisualizationMode.Starfield to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_STARFIELD, true),
                VisualizationMode.ProjectM to prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED_PROJECTM, true)
            )
            return FullscreenTickerPrefs(
                masterEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_TICKER_ENABLED, true),
                durationSeconds = prefs.getInt(AppPreferenceKeys.VISUALIZATION_TICKER_DURATION_SECONDS, 5),
                perModeEnabled = perMode
            )
        }

        fun isTickerKey(key: String?): Boolean {
            return key?.startsWith("visualization_ticker_") == true
        }
    }
}

@Composable
internal fun rememberFullscreenTickerPrefs(prefs: AppPreferences): FullscreenTickerPrefs {
    var state by remember(prefs) { mutableStateOf(FullscreenTickerPrefs.from(prefs)) }
    DisposableEffect(prefs) {
        val listener = AppPreferences.OnChangeListener { p, key ->
            if (FullscreenTickerPrefs.isTickerKey(key)) {
                state = FullscreenTickerPrefs.from(p)
            }
        }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    return state
}

internal object ChannelScopePrefsSupport {
    fun isChannelScopeKey(key: String?): Boolean {
        return key?.startsWith("visualization_channel_scope_") == true
    }

    fun from(prefs: AppPreferences): ChannelScopePrefs {
        val d = AppDefaults.Visualization.ChannelScope
        return ChannelScopePrefs(
            windowMs = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WINDOW_MS, d.windowMs)
                .coerceIn(d.windowRangeMs.first, d.windowRangeMs.last),
            renderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_RENDER_BACKEND, d.renderBackend.storageValue),
                d.renderBackend
            ),
            dcRemovalEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_DC_REMOVAL_ENABLED, d.dcRemovalEnabled),
            waveformClippingEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVEFORM_CLIPPING_ENABLED, d.waveformClippingEnabled),
            gainPercent = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GAIN_PERCENT, d.gainPercent)
                .coerceIn(d.gainRangePercent.first, d.gainRangePercent.last),
            contrastBackdropEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CONTRAST_BACKDROP_ENABLED, d.contrastBackdropEnabled),
            triggerModeNative = VisualizationOscTriggerMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_MODE, d.triggerMode.storageValue)
            ).nativeValue,
            triggerAlgorithmNative = VisualizationChannelScopeTriggerAlgorithm.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRIGGER_ALGORITHM, d.triggerAlgorithm.storageValue)
            ).nativeValue,
            // Desktop defaults to antialiased waves; mobile keeps Off for GPU reasons.
            waveRenderMode = VisualizationChannelScopeWaveRenderMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVE_RENDER_MODE, d.waveRenderMode.storageValue)
            ),
            antialiasMethod = VisualizationChannelScopeAntialiasMethod.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_ANTIALIAS_METHOD, d.antialiasMethod.storageValue)
            ),
            trackTransition = VisualizationChannelScopeTrackTransition.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TRACK_TRANSITION, d.trackTransition.storageValue)
            ),
            fpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_FPS_MODE, d.fpsMode.storageValue)
            ),
            lineWidthDp = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_WIDTH_DP, d.lineWidthDp)
                .coerceIn(d.lineWidthRangeDp.first, d.lineWidthRangeDp.last),
            gridWidthDp = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_WIDTH_DP, d.gridWidthDp)
                .coerceIn(d.gridWidthRangeDp.first, d.gridWidthRangeDp.last),
            verticalGridEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_VERTICAL_GRID_ENABLED, d.verticalGridEnabled),
            centerLineEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CENTER_LINE_ENABLED, d.centerLineEnabled),
            layout = VisualizationChannelScopeLayout.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LAYOUT, d.layout.storageValue)
            ),
            lineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_NO_ARTWORK, d.lineColorModeNoArtwork.storageValue),
                d.lineColorModeNoArtwork
            ),
            gridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_NO_ARTWORK, d.gridColorModeNoArtwork.storageValue),
                d.gridColorModeNoArtwork
            ),
            lineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_LINE_COLOR_MODE_WITH_ARTWORK, d.lineColorModeWithArtwork.storageValue),
                d.lineColorModeWithArtwork
            ),
            gridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_GRID_COLOR_MODE_WITH_ARTWORK, d.gridColorModeWithArtwork.storageValue),
                d.gridColorModeWithArtwork
            ),
            customLineColorArgb = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_LINE_COLOR_ARGB, d.customLineColorArgb),
            customGridColorArgb = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_GRID_COLOR_ARGB, d.customGridColorArgb),
            showArtworkBackground = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_SHOW_ARTWORK_BACKGROUND, d.showArtworkBackground),
            backgroundMode = VisualizationChannelScopeBackgroundMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_BACKGROUND_MODE, d.backgroundMode.storageValue)
            ),
            customBackgroundColorArgb = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_BACKGROUND_COLOR_ARGB, d.customBackgroundColorArgb),
            textEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ENABLED, d.textEnabled),
            textAnchor = VisualizationChannelScopeTextAnchor.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_ANCHOR, d.textAnchor.storageValue)
            ),
            textPaddingDp = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_PADDING_DP, d.textPaddingDp)
                .coerceIn(d.textPaddingRangeDp.first, d.textPaddingRangeDp.last),
            textSizeSp = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SIZE_SP, d.textSizeSp)
                .coerceIn(d.textSizeRangeSp.first, d.textSizeRangeSp.last),
            textHideWhenOverflow = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_HIDE_WHEN_OVERFLOW, d.textHideWhenOverflow),
            textShadowEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHADOW_ENABLED, d.textShadowEnabled),
            textFont = VisualizationChannelScopeTextFont.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_FONT, d.textFont.storageValue)
            ),
            textColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_COLOR_MODE, d.textColorMode.storageValue)
            ),
            customTextColorArgb = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_CUSTOM_TEXT_COLOR_ARGB, d.customTextColorArgb),
            textNoteFormat = VisualizationNoteNameFormat.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_NOTE_FORMAT, d.textNoteFormat.storageValue)
            ),
            textShowChannel = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_CHANNEL, d.textShowChannel),
            textShowNote = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_SHOW_NOTE, d.textShowNote),
            textVisibleElementSelection = readChannelScopeVisibleElementSelection(prefs),
            textVuEnabled = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ENABLED, d.textVuEnabled),
            textVuAnchor = VisualizationVuAnchor.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_ANCHOR, d.textVuAnchor.storageValue)
            ),
            textVuColorMode = VisualizationChannelScopeTextColorMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_COLOR_MODE, d.textVuColorMode.storageValue)
            ),
            textVuCustomColorArgb = prefs.getInt(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_TEXT_VU_CUSTOM_COLOR_ARGB, d.textVuCustomColorArgb)
        )
    }
}

@Composable
internal fun rememberChannelScopePrefs(prefs: AppPreferences): ChannelScopePrefs {
    var state by remember(prefs) { mutableStateOf(ChannelScopePrefsSupport.from(prefs)) }
    DisposableEffect(prefs) {
        val listener = AppPreferences.OnChangeListener { p, key ->
            if (ChannelScopePrefsSupport.isChannelScopeKey(key)) {
                state = ChannelScopePrefsSupport.from(p)
            }
        }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    return state
}

internal object StarfieldPrefsSupport {
    fun isStarfieldKey(key: String?): Boolean {
        return key?.startsWith("visualization_starfield_") == true
    }

    fun from(prefs: AppPreferences): StarfieldPrefs {
        val d = AppDefaults.Visualization.Starfield
        val activePreset = starfieldActivePreset(prefs)
        val t = starfieldPresetTuneFor(activePreset)
        val k = starfieldKeysFor(activePreset)
        return StarfieldPrefs(
            renderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_STARFIELD_RENDER_BACKEND,
                    d.renderBackend.storageValue
                ),
                d.renderBackend
            ),
            starCount = prefs.getInt(k.starCount, t.starCount)
                .coerceIn(d.starCountRange.first, d.starCountRange.last),
            speed = prefs.getInt(k.speedCenti, t.speedCenti)
                .coerceIn(d.speedRangeCenti.first, d.speedRangeCenti.last) / 100f,
            fov = prefs.getInt(k.fovCenti, t.fovCenti)
                .coerceIn(d.fovRangeCenti.first, d.fovRangeCenti.last) / 100f,
            nearPlane = prefs.getInt(k.nearMilli, t.nearMilli)
                .coerceIn(d.nearRangeMilli.first, d.nearRangeMilli.last) / 1000f,
            starColorArgb = prefs.getInt(k.starColorArgb, t.starColorArgb),
            baseSizePx = prefs.getInt(k.baseSizeDeci, t.baseSizeDeci)
                .coerceIn(d.baseSizeRangeDeci.first, d.baseSizeRangeDeci.last) / 10f,
            sizeGrowth = prefs.getInt(k.sizeGrowthCenti, t.sizeGrowthCenti)
                .coerceIn(d.sizeGrowthRangeCenti.first, d.sizeGrowthRangeCenti.last) / 100f,
            farDim = prefs.getInt(k.farDimPercent, t.farDimPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last) / 100f,
            softness = prefs.getInt(k.softnessPercent, t.softnessPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last) / 100f,
            beatGlow = prefs.getInt(k.beatGlowPercent, t.beatGlowPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last) / 100f,
            glowSize = prefs.getInt(k.glowSizeDeci, t.glowSizeDeci)
                .coerceIn(d.glowSizeRangeDeci.first, d.glowSizeRangeDeci.last) / 10f,
            trailPersistence = prefs.getInt(k.trailPercent, t.trailPercent)
                .coerceIn(d.trailRangePercent.first, d.trailRangePercent.last) / 100f,
            streaks = prefs.getBoolean(k.streaksEnabled, t.streaksEnabled),
            streakLength = prefs.getInt(k.streakLengthCenti, t.streakLengthCenti)
                .coerceIn(d.streakLengthRangeCenti.first, d.streakLengthRangeCenti.last) / 100f,
            centerX = prefs.getInt(k.centerXCenti, t.centerXCenti)
                .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last) / 100f,
            centerY = prefs.getInt(k.centerYCenti, t.centerYCenti)
                .coerceIn(d.centerRangeCenti.first, d.centerRangeCenti.last) / 100f,
            autoDrift = prefs.getBoolean(k.autoDriftEnabled, t.autoDriftEnabled),
            beatFollow = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_STARFIELD_BEAT_FOLLOW_ENABLED, d.beatFollowEnabled),
            reactSpeed = prefs.getInt(k.reactSpeedCenti, t.reactSpeedCenti)
                .coerceIn(d.reactSpeedRangeCenti.first, d.reactSpeedRangeCenti.last) / 100f,
            flash = prefs.getInt(k.flashPercent, t.flashPercent)
                .coerceIn(d.percentRange.first, d.percentRange.last) / 100f,
            contrastBackdropEnabled = prefs.getBoolean(k.contrastBackdropEnabled, t.contrastBackdropEnabled),
            monochromeBackdrop = prefs.getBoolean(
                AppPreferenceKeys.VISUALIZATION_STARFIELD_MONOCHROME_BACKDROP_ENABLED,
                d.monochromeBackdropEnabled
            ),
            square = prefs.getBoolean(k.squareEnabled, t.squarePixelsEnabled)
        )
    }
}

@Composable
internal fun rememberStarfieldPrefs(prefs: AppPreferences): StarfieldPrefs {
    var state by remember(prefs) { mutableStateOf(StarfieldPrefsSupport.from(prefs)) }
    DisposableEffect(prefs) {
        val listener = AppPreferences.OnChangeListener { p, key ->
            if (StarfieldPrefsSupport.isStarfieldKey(key)) {
                state = StarfieldPrefsSupport.from(p)
            }
        }
        prefs.addListener(listener)
        onDispose { prefs.removeListener(listener) }
    }
    return state
}

internal fun starfieldActivePreset(prefs: AppPreferences): StarfieldPreset {
    return StarfieldPreset.fromStorage(
        prefs.getString(
            AppPreferenceKeys.VISUALIZATION_STARFIELD_ACTIVE_PRESET,
            StarfieldPreset.ClassicAmiga.storageValue
        )
    )
}

internal fun writeStarfieldFactoryTune(prefs: AppPreferences, preset: StarfieldPreset) {
    val factory = starfieldPresetTuneFor(preset)
    val slotKeys = starfieldKeysFor(preset)
    prefs.edit()
        .putInt(slotKeys.starCount, factory.starCount)
        .putInt(slotKeys.speedCenti, factory.speedCenti)
        .putInt(slotKeys.fovCenti, factory.fovCenti)
        .putInt(slotKeys.nearMilli, factory.nearMilli)
        .putInt(slotKeys.starColorArgb, factory.starColorArgb)
        .putInt(slotKeys.baseSizeDeci, factory.baseSizeDeci)
        .putInt(slotKeys.sizeGrowthCenti, factory.sizeGrowthCenti)
        .putInt(slotKeys.farDimPercent, factory.farDimPercent)
        .putInt(slotKeys.softnessPercent, factory.softnessPercent)
        .putInt(slotKeys.beatGlowPercent, factory.beatGlowPercent)
        .putInt(slotKeys.glowSizeDeci, factory.glowSizeDeci)
        .putInt(slotKeys.trailPercent, factory.trailPercent)
        .putBoolean(slotKeys.streaksEnabled, factory.streaksEnabled)
        .putBoolean(slotKeys.squareEnabled, factory.squarePixelsEnabled)
        .putInt(slotKeys.streakLengthCenti, factory.streakLengthCenti)
        .putInt(slotKeys.centerXCenti, factory.centerXCenti)
        .putInt(slotKeys.centerYCenti, factory.centerYCenti)
        .putBoolean(slotKeys.autoDriftEnabled, factory.autoDriftEnabled)
        .putInt(slotKeys.reactSpeedCenti, factory.reactSpeedCenti)
        .putInt(slotKeys.flashPercent, factory.flashPercent)
        .putBoolean(slotKeys.contrastBackdropEnabled, factory.contrastBackdropEnabled)
        .apply()
}
