package com.flopster101.siliconplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.sharp.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.StarfieldPreset
import com.flopster101.siliconplayer.VisualizationChannelScopeBackgroundMode
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextColorMode
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTrackTransition
import com.flopster101.siliconplayer.VisualizationChannelScopeTriggerAlgorithm
import com.flopster101.siliconplayer.VisualizationChannelScopeAntialiasMethod
import com.flopster101.siliconplayer.VisualizationChannelScopeWaveRenderMode
import com.flopster101.siliconplayer.VisualizationFullscreenMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationOscTriggerMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.isAdvancedVisualizationMode
import com.flopster101.siliconplayer.isBasicVisualizationMode
import com.flopster101.siliconplayer.isVisualizationModeSelectable
import com.flopster101.siliconplayer.parseEnabledVisualizationModes
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.readChannelScopeVisibleElementSelection
import com.flopster101.siliconplayer.resolveEffectiveVisualizationFullscreenMode
import com.flopster101.siliconplayer.selectableVisualizationModes
import com.flopster101.siliconplayer.serializeEnabledVisualizationModes
import kotlinx.coroutines.delay

private val FullscreenScrim = Color.Black.copy(alpha = 0.28f)
private val FullscreenBottomScrimBrush = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.3f to Color.Black.copy(alpha = 0.38f),
    1.0f to Color.Black.copy(alpha = 0.65f)
)

private fun visualizationModeIcon(mode: VisualizationMode): ImageVector {
    return when (mode) {
        VisualizationMode.Off -> Icons.Default.VisibilityOff
        VisualizationMode.Bars -> Icons.Default.GraphicEq
        VisualizationMode.Oscilloscope -> Icons.Default.MonitorHeart
        VisualizationMode.VuMeters -> Icons.Default.Equalizer
        VisualizationMode.ChannelScope -> Icons.Default.MonitorHeart
        VisualizationMode.Starfield -> Icons.Default.Star
        VisualizationMode.ProjectM -> Icons.Default.AutoAwesome
    }
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
                prefs.getString(AppPreferenceKeys.VISUALIZATION_CHANNEL_SCOPE_WAVE_RENDER_MODE, VisualizationChannelScopeWaveRenderMode.Antialiased.storageValue)
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

internal data class VisualizationUiState(
    val mode: VisualizationMode,
    val enabledModes: Set<VisualizationMode>,
    val availableModes: List<VisualizationMode>,
    val onCycleMode: () -> Unit,
    val onSelectMode: (VisualizationMode) -> Unit,
    val onSetEnabledModes: (Set<VisualizationMode>) -> Unit
)

@Composable
internal fun rememberVisualizationUiState(
    prefs: AppPreferences,
    activeCoreName: String?,
    isPlayerSurfaceVisible: Boolean
): VisualizationUiState {
    var currentMode by remember {
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
            )
        )
    }
    var requestedMode by remember {
        val storedCurrent = VisualizationMode.fromStorage(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
        )
        mutableStateOf(
            VisualizationMode.fromStorage(
                prefs.getString(
                    AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE,
                    storedCurrent.storageValue
                )
            )
        )
    }
    var lastBasicMode by remember {
        val storedCurrent = VisualizationMode.fromStorage(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_MODE, VisualizationMode.Off.storageValue)
        )
        mutableStateOf(
            prefs.getString(AppPreferenceKeys.VISUALIZATION_LAST_BASIC_MODE, null)
                ?.let(VisualizationMode::fromStorage)
                ?.takeIf { it.isBasicVisualizationMode() }
                ?: storedCurrent.takeIf { it.isBasicVisualizationMode() }
        )
    }
    var enabledModes by remember {
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
        val migrated = when {
            projectMMigrated && starfieldMigrated -> parsed
            else -> {
                prefs.edit()
                    .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_PROJECTM_MIGRATED, true)
                    .putBoolean(AppPreferenceKeys.VISUALIZATION_ENABLED_MODES_STARFIELD_MIGRATED, true)
                    .apply()
                var result = parsed
                if (!projectMMigrated) result = result + VisualizationMode.ProjectM
                if (!starfieldMigrated) result = result + VisualizationMode.Starfield
                result
            }
        }
        mutableStateOf(migrated)
    }

    LaunchedEffect(currentMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_MODE, currentMode.storageValue)
            .apply()
    }
    LaunchedEffect(requestedMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_REQUESTED_MODE, requestedMode.storageValue)
            .apply()
    }
    LaunchedEffect(lastBasicMode) {
        prefs.edit()
            .putString(AppPreferenceKeys.VISUALIZATION_LAST_BASIC_MODE, lastBasicMode?.storageValue)
            .apply()
    }
    LaunchedEffect(enabledModes) {
        prefs.edit()
            .putString(
                AppPreferenceKeys.VISUALIZATION_ENABLED_MODES,
                serializeEnabledVisualizationModes(enabledModes)
            )
            .apply()
    }

    val availableModes = remember(enabledModes, activeCoreName) {
        listOf(VisualizationMode.Off) + selectableVisualizationModes.filter { mode ->
            isVisualizationModeSelectable(mode, enabledModes, activeCoreName)
        }
    }

    val resolvedMode = remember(enabledModes, activeCoreName, requestedMode, lastBasicMode) {
        if (requestedMode == VisualizationMode.Off) {
            VisualizationMode.Off
        } else if (isVisualizationModeSelectable(requestedMode, enabledModes, activeCoreName)) {
            requestedMode
        } else if (requestedMode.isAdvancedVisualizationMode()) {
            val basicFallback = lastBasicMode?.takeIf {
                it.isBasicVisualizationMode() && isVisualizationModeSelectable(it, enabledModes, activeCoreName)
            }
            basicFallback ?: VisualizationMode.Off
        } else {
            lastBasicMode?.takeIf {
                it.isBasicVisualizationMode() && isVisualizationModeSelectable(it, enabledModes, activeCoreName)
            } ?: VisualizationMode.Off
        }
    }

    LaunchedEffect(resolvedMode, currentMode) {
        if (currentMode != resolvedMode) {
            currentMode = resolvedMode
        }
    }

    val currentCorePluginName = pluginNameForCoreName(activeCoreName)
    LaunchedEffect(activeCoreName, currentCorePluginName, currentMode, isPlayerSurfaceVisible) {
        if (currentCorePluginName != DecoderNames.LIB_SID_PLAY_FP &&
            currentCorePluginName != DecoderNames.C_RSID &&
            currentCorePluginName != DecoderNames.GAME_MUSIC_EMU &&
            currentCorePluginName != DecoderNames.SC68) {
            return@LaunchedEffect
        }
        val coreName = activeCoreName?.trim().takeIf { !it.isNullOrEmpty() } ?: return@LaunchedEffect
        val channelScopeActive = isPlayerSurfaceVisible && currentMode == VisualizationMode.ChannelScope
        NativeBridge.setCoreOption(
            coreName,
            "visualization.channel_scope_active",
            if (channelScopeActive) "true" else "false"
        )
    }

    return remember(currentMode, enabledModes, availableModes) {
        VisualizationUiState(
            mode = currentMode,
            enabledModes = enabledModes,
            availableModes = availableModes,
            onCycleMode = {
                val currentIndex = availableModes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
                val nextMode = availableModes[(currentIndex + 1) % availableModes.size]
                requestedMode = nextMode
                if (nextMode.isBasicVisualizationMode()) {
                    lastBasicMode = nextMode
                }
            },
            onSelectMode = { sel ->
                if (availableModes.contains(sel)) {
                    requestedMode = sel
                    if (sel.isBasicVisualizationMode()) {
                        lastBasicMode = sel
                    }
                }
            },
            onSetEnabledModes = { set ->
                val normalized = set.intersect(selectableVisualizationModes.toSet())
                enabledModes = normalized
            }
        )
    }
}

@Composable
internal fun FullscreenToggleAffordance(
    onToggle: () -> Unit,
    show: Boolean
) {
    AnimatedVisibility(
        visible = show,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Surface(
            onClick = onToggle,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier.size(40.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Enter fullscreen",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FullscreenVisualizerSwitcher(
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onVisualizerAction: () -> Unit,
    onVisualizerLongPress: (() -> Unit)? = null,
    onInteraction: () -> Unit = {},
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val modes = remember(availableVisualizationModes) {
        if (availableVisualizationModes.isNotEmpty()) {
            availableVisualizationModes
        } else {
            VisualizationMode.entries.toList()
        }
    }
    val currentIndex = modes.indexOf(visualizationMode)
    val onPrev = {
        val prevIndex = if (currentIndex <= 0) modes.size - 1 else currentIndex - 1
        onSelectVisualizationMode(modes[prevIndex])
    }
    val onNext = {
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % modes.size
        onSelectVisualizationMode(modes[nextIndex])
    }

    if (compact) {
        Box(
            modifier = modifier
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = {
                        onInteraction()
                        onCycleVisualizationMode()
                    },
                    onLongClick = {
                        onInteraction()
                        onVisualizerAction()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = visualizationModeIcon(visualizationMode),
                contentDescription = "Visualization mode",
                modifier = Modifier.size(22.dp),
                tint = Color.White
            )
        }
    } else {
        Surface(
            color = FullscreenScrim,
            contentColor = Color.White,
            shape = RoundedCornerShape(percent = 50),
            modifier = modifier
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        onInteraction()
                        onPrev()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous visualization",
                        modifier = Modifier.size(22.dp)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .combinedClickable(
                            onClick = {
                                onInteraction()
                                onVisualizerAction()
                            },
                            onLongClick = {
                                onInteraction()
                                onVisualizerLongPress?.invoke()
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = visualizationModeIcon(visualizationMode),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = visualizationMode.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                IconButton(
                    onClick = {
                        onInteraction()
                        onNext()
                    },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next visualization",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenTransportControls(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    modifier: Modifier = Modifier,
    showExtras: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.None,
    onStopAndClear: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    canCycleRepeatMode: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        if (showExtras) {
            IconButton(
                onClick = onStopAndClear,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                )
            ) {
                Icon(Icons.Sharp.Stop, contentDescription = "Stop", modifier = Modifier.size(24.dp))
            }
        }

        FilledTonalIconButton(
            onClick = onPreviousTrack,
            enabled = canPreviousTrack,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(24.dp))
        }

        FilledIconButton(
            onClick = { if (isPlaying) onPause() else onPlay() },
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }

        FilledTonalIconButton(
            onClick = onNextTrack,
            enabled = canNextTrack,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                disabledContentColor = Color.White.copy(alpha = 0.38f)
            )
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(24.dp))
        }

        if (showExtras) {
            val repeatActive = repeatMode != RepeatMode.None
            val modeBadgeText = when (repeatMode) {
                RepeatMode.Track -> "1"
                RepeatMode.Subtune -> "ST"
                RepeatMode.LoopPoint -> "LP"
                else -> ""
            }
            val modeBadgeIcon = if (repeatMode == RepeatMode.Playlist) {
                Icons.AutoMirrored.Filled.List
            } else {
                null
            }
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onCycleRepeatMode,
                    enabled = canCycleRepeatMode,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (repeatActive) Color.White else Color.White.copy(alpha = 0.38f),
                        disabledContentColor = Color.White.copy(alpha = 0.38f)
                    )
                ) {
                    Icon(Icons.Default.Loop, contentDescription = "Repeat mode", modifier = Modifier.size(24.dp))
                }
                if (repeatActive && (modeBadgeText.isNotEmpty() || modeBadgeIcon != null)) {
                    Surface(
                        color = Color.White,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 8.dp, y = (-8).dp)
                    ) {
                        if (modeBadgeIcon != null) {
                            Icon(
                                imageVector = modeBadgeIcon,
                                contentDescription = null,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp).size(12.dp)
                            )
                        } else {
                            Text(
                                text = modeBadgeText,
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenSeekBar(
    positionSecondsProvider: () -> Double,
    durationSeconds: Double,
    canSeek: Boolean,
    onSeek: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    if (durationSeconds <= 0.0) return
    val positionSeconds = positionSecondsProvider()
    if (!canSeek) {
        val progress = (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.25f)
        )
        return
    }

    var sliderPosition by remember(durationSeconds) { mutableStateOf(positionSeconds) }
    var isSeeking by remember { mutableStateOf(false) }
    val displayPos = if (isSeeking) sliderPosition else positionSeconds
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatTime(displayPos),
            color = Color.White.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelSmall
        )
        LineageStyleSeekBar(
            value = displayPos.toFloat(),
            maxValue = durationSeconds.toFloat(),
            enabled = true,
            seekInProgress = isSeeking,
            layoutScale = 0.9f,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.30f),
            thumbColor = Color.White,
            forceMonochromeWhite = true,
            onSeekInteractionChanged = {},
            onValueChange = { v ->
                isSeeking = true
                sliderPosition = v.toDouble()
            },
            onValueChangeFinished = {
                isSeeking = false
                onSeek(sliderPosition)
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(36.dp)
        )
        Text(
            text = formatTime(durationSeconds),
            color = Color.White.copy(alpha = 0.80f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun FullscreenBottomControls(
    displayTitle: String,
    displayArtist: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    positionSecondsProvider: () -> Double,
    durationSeconds: Double,
    canSeek: Boolean,
    onSeek: (Double) -> Unit,
    repeatMode: RepeatMode,
    onStopAndClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    canCycleRepeatMode: Boolean,
    effectiveMode: VisualizationFullscreenMode,
    switcherContent: (@Composable () -> Unit)? = null
) {
    val scrimModifier = Modifier
        .fillMaxWidth()
        .background(FullscreenBottomScrimBrush)
        .padding(horizontal = 16.dp, vertical = 12.dp)

    when (effectiveMode) {
        VisualizationFullscreenMode.SuperCompact -> Unit

        VisualizationFullscreenMode.Compact -> {
            Column(
                modifier = scrimModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (switcherContent != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        switcherContent()
                    }
                }
                Text(
                    text = if (displayArtist.isNotBlank()) "$displayArtist — $displayTitle" else displayTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                FullscreenTransportControls(
                    isPlaying = isPlaying,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    modifier = Modifier.fillMaxWidth()
                )
                if (durationSeconds > 0.0) {
                    val progress = (positionSecondsProvider() / durationSeconds).toFloat().coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        VisualizationFullscreenMode.Complete -> {
            val windowSize = LocalWindowSizeInfo.current
            val isLandscape = windowSize.screenWidthDp >= windowSize.screenHeightDp
            Column(
                modifier = scrimModifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (switcherContent != null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        contentAlignment = if (isLandscape) Alignment.CenterStart else Alignment.Center
                    ) {
                        switcherContent()
                    }
                }
                if (isLandscape) {
                    FullscreenSeekBar(
                        positionSecondsProvider = positionSecondsProvider,
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        onSeek = onSeek
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (displayArtist.isNotBlank()) {
                                Text(
                                    text = displayArtist,
                                    color = Color.White.copy(alpha = 0.80f),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        FullscreenTransportControls(
                            isPlaying = isPlaying,
                            onPlay = onPlay,
                            onPause = onPause,
                            onPreviousTrack = onPreviousTrack,
                            onNextTrack = onNextTrack,
                            canPreviousTrack = canPreviousTrack,
                            canNextTrack = canNextTrack,
                            showExtras = true,
                            repeatMode = repeatMode,
                            onStopAndClear = onStopAndClear,
                            onCycleRepeatMode = onCycleRepeatMode,
                            canCycleRepeatMode = canCycleRepeatMode
                        )
                    }
                } else {
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (displayArtist.isNotBlank()) {
                        Text(
                            text = displayArtist,
                            color = Color.White.copy(alpha = 0.80f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FullscreenSeekBar(
                        positionSecondsProvider = positionSecondsProvider,
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        onSeek = onSeek
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FullscreenTransportControls(
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        modifier = Modifier.fillMaxWidth(),
                        showExtras = true,
                        repeatMode = repeatMode,
                        onStopAndClear = onStopAndClear,
                        onCycleRepeatMode = onCycleRepeatMode,
                        canCycleRepeatMode = canCycleRepeatMode
                    )
                }
            }
        }
    }
}

@Composable
private fun FullscreenTrackTicker(
    trackKey: String?,
    title: String,
    artist: String,
    formatLabel: String?,
    trackDurationSeconds: Double,
    trackDurationReliable: Boolean,
    holdSeconds: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(trackKey, enabled, holdSeconds) {
        visible = false
        if (enabled && trackKey != null) {
            visible = true
            delay(holdSeconds.coerceIn(2, 15) * 1000L)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotBlank()) {
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.80f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!formatLabel.isNullOrBlank() || trackDurationSeconds > 0.0 || !trackDurationReliable) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!formatLabel.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ) {
                                Text(
                                    text = formatLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (trackDurationSeconds > 0.0 || !trackDurationReliable) {
                            if (!formatLabel.isNullOrBlank()) Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (trackDurationReliable) {
                                    formatTime(trackDurationSeconds)
                                } else {
                                    "${formatTime(trackDurationSeconds)}?"
                                },
                                color = Color.White.copy(alpha = 0.70f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullscreenVisualizationOverlay(
    isFullscreen: Boolean,
    onExitFullscreen: () -> Unit,
    visualizationContent: @Composable () -> Unit,
    displayTitle: String,
    displayArtist: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSwipePreviousTrack: () -> Unit = onPreviousTrack,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    positionSecondsProvider: () -> Double,
    durationSeconds: Double,
    canSeek: Boolean = true,
    onSeek: (Double) -> Unit = {},
    repeatMode: RepeatMode = RepeatMode.None,
    onStopAndClear: () -> Unit = {},
    onCycleRepeatMode: () -> Unit = {},
    canCycleRepeatMode: Boolean = false,
    visualizationMode: VisualizationMode = VisualizationMode.Off,
    availableVisualizationModes: List<VisualizationMode> = emptyList(),
    onCycleVisualizationMode: () -> Unit = {},
    onSelectVisualizationMode: (VisualizationMode) -> Unit = {},
    onVisualizerAction: () -> Unit = {},
    fullscreenModePref: VisualizationFullscreenMode,
    hasReliableDuration: Boolean = true,
    tickerEnabled: Boolean = false,
    tickerDurationSeconds: Int = 5,
    tickerTrackKey: String? = null,
    tickerFormatLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (!isFullscreen) return

    val effectiveMode = resolveEffectiveVisualizationFullscreenMode(fullscreenModePref, isWatch = false)
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteractionTick by remember { mutableIntStateOf(0) }
    var fullscreenSwipeDelta by remember { mutableFloatStateOf(0f) }
    val windowSize = LocalWindowSizeInfo.current
    val fullscreenSwipeThresholdPx = with(LocalDensity.current) {
        (windowSize.screenWidthDp * 0.32f).dp.toPx()
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(controlsVisible, isFullscreen, controlsInteractionTick) {
        if (controlsVisible && isFullscreen) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && keyEvent.key == Key.Escape) {
                    onExitFullscreen()
                    true
                } else {
                    false
                }
            }
            .pointerInput(canPreviousTrack, canNextTrack, onSwipePreviousTrack, onNextTrack) {
                detectHorizontalDragGestures(
                    onDragStart = { fullscreenSwipeDelta = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        fullscreenSwipeDelta += dragAmount
                        change.consume()
                    },
                    onDragEnd = {
                        when {
                            fullscreenSwipeDelta <= -fullscreenSwipeThresholdPx && canNextTrack -> {
                                onNextTrack()
                            }
                            fullscreenSwipeDelta >= fullscreenSwipeThresholdPx && canPreviousTrack -> {
                                onSwipePreviousTrack()
                            }
                        }
                        fullscreenSwipeDelta = 0f
                    },
                    onDragCancel = { fullscreenSwipeDelta = 0f }
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { controlsVisible = !controlsVisible }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            visualizationContent()
        }

        FullscreenTrackTicker(
            trackKey = tickerTrackKey,
            title = displayTitle,
            artist = displayArtist,
            formatLabel = tickerFormatLabel,
            trackDurationSeconds = durationSeconds,
            trackDurationReliable = hasReliableDuration,
            holdSeconds = tickerDurationSeconds,
            enabled = tickerEnabled,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Surface(
                onClick = onExitFullscreen,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                contentColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit fullscreen",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            when (effectiveMode) {
                VisualizationFullscreenMode.SuperCompact -> {
                    FullscreenTransportControls(
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FullscreenBottomScrimBrush)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                VisualizationFullscreenMode.Compact,
                VisualizationFullscreenMode.Complete -> {
                    FullscreenBottomControls(
                        displayTitle = displayTitle,
                        displayArtist = displayArtist,
                        isPlaying = isPlaying,
                        onPlay = onPlay,
                        onPause = onPause,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        canPreviousTrack = canPreviousTrack,
                        canNextTrack = canNextTrack,
                        positionSecondsProvider = positionSecondsProvider,
                        durationSeconds = durationSeconds,
                        canSeek = canSeek,
                        onSeek = onSeek,
                        repeatMode = repeatMode,
                        onStopAndClear = onStopAndClear,
                        onCycleRepeatMode = onCycleRepeatMode,
                        canCycleRepeatMode = canCycleRepeatMode,
                        effectiveMode = effectiveMode,
                        switcherContent = {
                            FullscreenVisualizerSwitcher(
                                visualizationMode = visualizationMode,
                                availableVisualizationModes = availableVisualizationModes,
                                onCycleVisualizationMode = onCycleVisualizationMode,
                                onSelectVisualizationMode = onSelectVisualizationMode,
                                onVisualizerAction = onVisualizerAction,
                                onInteraction = { controlsInteractionTick++ },
                                compact = effectiveMode == VisualizationFullscreenMode.Compact
                            )
                        }
                    )
                }
            }
        }
    }
}
