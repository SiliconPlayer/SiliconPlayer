package com.flopster101.siliconplayer.ui.screens

import com.flopster101.siliconplayer.onGloballyPositionedDeferred
import com.flopster101.siliconplayer.onSizeChangedDeferred
import com.flopster101.siliconplayer.isRoundScreenCompat
import com.flopster101.siliconplayer.VisualizationPerformanceMode
import com.flopster101.siliconplayer.VerticalScrollbarTrack
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.formatDisplayArtist
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import android.content.pm.PackageManager
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.rounded.Stop
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.view.MotionEvent
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AnimatedMetadataPlaceholderLine
import com.flopster101.siliconplayer.ArtworkSwipePreviewState
import com.flopster101.siliconplayer.DecoderNames
import com.flopster101.siliconplayer.inferredDisplayTitleForName
import com.flopster101.siliconplayer.inferredPrimaryExtensionForName
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.RepeatMode
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.VisualizationFullscreenMode
import com.flopster101.siliconplayer.VisualizationMode
import com.flopster101.siliconplayer.ui.visualization.gl.ProjectMPresetSets
import com.flopster101.siliconplayer.ui.visualization.gl.SiliconVisNativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationOscColorMode
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.isWatchDevice
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.decodePercentEncodedForDisplay
import com.flopster101.siliconplayer.formatByteCount
import com.flopster101.siliconplayer.pluginNameForCoreName
import com.flopster101.siliconplayer.RemoteLoadPhase
import com.flopster101.siliconplayer.RemoteLoadUiState
import com.flopster101.siliconplayer.RemoteLoadUiStateHolder
import com.flopster101.siliconplayer.RemotePreloadUiStateHolder
import com.flopster101.siliconplayer.rememberDialogScrollbarAlpha
import com.flopster101.siliconplayer.rememberScrollStateScrollbarDragHandler
import com.flopster101.siliconplayer.sanitizeRemoteCachedMetadataTitle
import com.flopster101.siliconplayer.shouldRestartCurrentTrackOnPrevious
import com.flopster101.siliconplayer.stripRemoteCacheHashPrefix
import com.flopster101.siliconplayer.tvKeyLongPress
import com.flopster101.siliconplayer.ui.dialogs.dialogScrollableContentNavigation
import com.flopster101.siliconplayer.ui.dialogs.AudioOutputDetailsDialog
import com.flopster101.siliconplayer.ui.dialogs.AudioOutputDeviceDialog
import com.flopster101.siliconplayer.ui.dialogs.DialogResetButton
import com.flopster101.siliconplayer.ui.dialogs.DialogSectionLabel
import com.flopster101.siliconplayer.ui.dialogs.FloatingActionDialog
import com.flopster101.siliconplayer.ui.dialogs.VisualizationModePickerDialog
import com.flopster101.siliconplayer.ui.dialogs.VisualizationOptionsSheet
import com.flopster101.siliconplayer.ui.visualization.basic.BasicVisualizationOverlay
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.pow
import com.flopster101.siliconplayer.PlaybackIo
import com.flopster101.siliconplayer.readCurrentFormatName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import androidx.compose.foundation.text.selection.SelectionContainer

internal val LocalPlayerFocusIndicatorsEnabled = compositionLocalOf { true }
private val LocalPlayerMarqueeClockState = compositionLocalOf<State<Long>> { mutableLongStateOf(0L) }

private const val PREF_KEY_VIS_OSC_WINDOW_MS = "visualization_osc_window_ms"
private const val PREF_KEY_VIS_OSC_TRIGGER_MODE = "visualization_osc_trigger_mode"
private const val PREF_KEY_VIS_OSC_LINE_WIDTH_DP = "visualization_osc_line_width_dp"
private const val PREF_KEY_VIS_OSC_GRID_WIDTH_DP = "visualization_osc_grid_width_dp"
private const val PREF_KEY_VIS_OSC_FPS_MODE = "visualization_osc_fps_mode"
private const val PREF_KEY_VIS_OSC_RENDER_BACKEND = "visualization_osc_render_backend"
private const val PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED = "visualization_osc_vertical_grid_enabled"
private const val PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED = "visualization_osc_center_line_enabled"
private const val PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK = "visualization_osc_line_color_mode_no_artwork"
private const val PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK = "visualization_osc_grid_color_mode_no_artwork"
private const val PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK = "visualization_osc_line_color_mode_with_artwork"
private const val PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK = "visualization_osc_grid_color_mode_with_artwork"
private const val PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR = "visualization_osc_custom_line_color_argb"
private const val PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR = "visualization_osc_custom_grid_color_argb"
private const val PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED = "visualization_osc_contrast_backdrop_enabled"
private const val PREF_KEY_VIS_BAR_RENDER_BACKEND = "visualization_bar_render_backend"
private const val PREF_KEY_VIS_BAR_FPS_MODE = "visualization_bar_fps_mode"
private const val PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED = "visualization_bar_frequency_grid_enabled"
private const val PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED = "visualization_bar_contrast_backdrop_enabled"
private const val PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK = "visualization_bar_color_mode_no_artwork"
private const val PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK = "visualization_bar_color_mode_with_artwork"
private const val PREF_KEY_VIS_BAR_CUSTOM_COLOR = "visualization_bar_custom_color_argb"
private const val PREF_KEY_VIS_VU_RENDER_BACKEND = "visualization_vu_render_backend"
private const val PREF_KEY_VIS_VU_FPS_MODE = "visualization_vu_fps_mode"
private const val PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED = "visualization_vu_contrast_backdrop_enabled"
private const val PREF_KEY_VIS_VU_COLOR_NO_ARTWORK = "visualization_vu_color_mode_no_artwork"
private const val PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK = "visualization_vu_color_mode_with_artwork"
private const val PREF_KEY_VIS_VU_CUSTOM_COLOR = "visualization_vu_custom_color_argb"

private class PlayerVisualizationPreferenceState(
    oscWindowMs: Int,
    oscTriggerModeNative: Int,
    oscFpsMode: VisualizationOscFpsMode,
    oscRenderBackend: VisualizationRenderBackend,
    oscLineWidthDp: Int,
    oscGridWidthDp: Int,
    oscVerticalGridEnabled: Boolean,
    oscCenterLineEnabled: Boolean,
    oscLineColorModeNoArtwork: VisualizationOscColorMode,
    oscGridColorModeNoArtwork: VisualizationOscColorMode,
    oscLineColorModeWithArtwork: VisualizationOscColorMode,
    oscGridColorModeWithArtwork: VisualizationOscColorMode,
    oscCustomLineColorArgb: Int,
    oscCustomGridColorArgb: Int,
    oscContrastBackdropEnabled: Boolean,
    barColorModeNoArtwork: VisualizationOscColorMode,
    barColorModeWithArtwork: VisualizationOscColorMode,
    barCustomColorArgb: Int,
    barFrequencyGridEnabled: Boolean,
    barContrastBackdropEnabled: Boolean,
    barFpsMode: VisualizationOscFpsMode,
    barRuntimeRenderBackend: VisualizationRenderBackend,
    vuColorModeNoArtwork: VisualizationOscColorMode,
    vuColorModeWithArtwork: VisualizationOscColorMode,
    vuCustomColorArgb: Int,
    vuContrastBackdropEnabled: Boolean,
    vuFpsMode: VisualizationOscFpsMode,
    vuRuntimeRenderBackend: VisualizationRenderBackend
) {
    var oscWindowMs by mutableIntStateOf(oscWindowMs)
    var oscTriggerModeNative by mutableIntStateOf(oscTriggerModeNative)
    var oscFpsMode by mutableStateOf(oscFpsMode)
    var oscRenderBackend by mutableStateOf(oscRenderBackend)
    var oscLineWidthDp by mutableIntStateOf(oscLineWidthDp)
    var oscGridWidthDp by mutableIntStateOf(oscGridWidthDp)
    var oscVerticalGridEnabled by mutableStateOf(oscVerticalGridEnabled)
    var oscCenterLineEnabled by mutableStateOf(oscCenterLineEnabled)
    var oscLineColorModeNoArtwork by mutableStateOf(oscLineColorModeNoArtwork)
    var oscGridColorModeNoArtwork by mutableStateOf(oscGridColorModeNoArtwork)
    var oscLineColorModeWithArtwork by mutableStateOf(oscLineColorModeWithArtwork)
    var oscGridColorModeWithArtwork by mutableStateOf(oscGridColorModeWithArtwork)
    var oscCustomLineColorArgb by mutableIntStateOf(oscCustomLineColorArgb)
    var oscCustomGridColorArgb by mutableIntStateOf(oscCustomGridColorArgb)
    var oscContrastBackdropEnabled by mutableStateOf(oscContrastBackdropEnabled)
    var barColorModeNoArtwork by mutableStateOf(barColorModeNoArtwork)
    var barColorModeWithArtwork by mutableStateOf(barColorModeWithArtwork)
    var barCustomColorArgb by mutableIntStateOf(barCustomColorArgb)
    var barFrequencyGridEnabled by mutableStateOf(barFrequencyGridEnabled)
    var barContrastBackdropEnabled by mutableStateOf(barContrastBackdropEnabled)
    var barFpsMode by mutableStateOf(barFpsMode)
    var barRuntimeRenderBackend by mutableStateOf(barRuntimeRenderBackend)
    var vuColorModeNoArtwork by mutableStateOf(vuColorModeNoArtwork)
    var vuColorModeWithArtwork by mutableStateOf(vuColorModeWithArtwork)
    var vuCustomColorArgb by mutableIntStateOf(vuCustomColorArgb)
    var vuContrastBackdropEnabled by mutableStateOf(vuContrastBackdropEnabled)
    var vuFpsMode by mutableStateOf(vuFpsMode)
    var vuRuntimeRenderBackend by mutableStateOf(vuRuntimeRenderBackend)
}

private fun parseOscTriggerModeNative(value: String?): Int {
    return when (value) {
        "rising" -> 1
        "falling" -> 2
        else -> 0
    }
}

@Composable
private fun rememberPlayerVisualizationPreferenceState(
    prefs: SharedPreferences,
    defaultBarRenderBackend: VisualizationRenderBackend,
    defaultVuRenderBackend: VisualizationRenderBackend
): PlayerVisualizationPreferenceState {
    val state = remember(prefs, defaultBarRenderBackend, defaultVuRenderBackend) {
        PlayerVisualizationPreferenceState(
            oscWindowMs = prefs.getInt(PREF_KEY_VIS_OSC_WINDOW_MS, 40).coerceIn(5, 200),
            oscTriggerModeNative = parseOscTriggerModeNative(
                prefs.getString(PREF_KEY_VIS_OSC_TRIGGER_MODE, "rising")
            ),
            oscFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(PREF_KEY_VIS_OSC_FPS_MODE, VisualizationOscFpsMode.Default.storageValue)
            ),
            oscRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_RENDER_BACKEND,
                    AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                ),
                AppDefaults.Visualization.Oscilloscope.renderBackend
            ),
            oscLineWidthDp = prefs.getInt(PREF_KEY_VIS_OSC_LINE_WIDTH_DP, 3).coerceIn(1, 12),
            oscGridWidthDp = prefs.getInt(PREF_KEY_VIS_OSC_GRID_WIDTH_DP, 2).coerceIn(1, 8),
            oscVerticalGridEnabled = prefs.getBoolean(PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED, false),
            oscCenterLineEnabled = prefs.getBoolean(PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED, false),
            oscLineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            oscGridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            oscLineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            oscGridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            oscCustomLineColorArgb = prefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR, 0xFF6BD8FF.toInt()),
            oscCustomGridColorArgb = prefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR, 0x66FFFFFF),
            oscContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Oscilloscope.contrastBackdropEnabled
            ),
            barColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            barColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            barCustomColorArgb = prefs.getInt(PREF_KEY_VIS_BAR_CUSTOM_COLOR, 0xFF6BD8FF.toInt()),
            barFrequencyGridEnabled = prefs.getBoolean(
                PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED,
                AppDefaults.Visualization.Bars.frequencyGridEnabled
            ),
            barContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Bars.contrastBackdropEnabled
            ),
            barFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_BAR_FPS_MODE,
                    AppDefaults.Visualization.Bars.fpsMode.storageValue
                )
            ),
            barRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(PREF_KEY_VIS_BAR_RENDER_BACKEND, defaultBarRenderBackend.storageValue),
                defaultBarRenderBackend
            ),
            vuColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_COLOR_NO_ARTWORK,
                    VisualizationOscColorMode.Monet.storageValue
                ),
                VisualizationOscColorMode.Monet
            ),
            vuColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK,
                    VisualizationOscColorMode.Artwork.storageValue
                ),
                VisualizationOscColorMode.Artwork
            ),
            vuCustomColorArgb = prefs.getInt(PREF_KEY_VIS_VU_CUSTOM_COLOR, 0xFF6BD8FF.toInt()),
            vuContrastBackdropEnabled = prefs.getBoolean(
                PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED,
                AppDefaults.Visualization.Vu.contrastBackdropEnabled
            ),
            vuFpsMode = VisualizationOscFpsMode.fromStorage(
                prefs.getString(
                    PREF_KEY_VIS_VU_FPS_MODE,
                    AppDefaults.Visualization.Vu.fpsMode.storageValue
                )
            ),
            vuRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                prefs.getString(PREF_KEY_VIS_VU_RENDER_BACKEND, defaultVuRenderBackend.storageValue),
                defaultVuRenderBackend
            )
        )
    }
    DisposableEffect(prefs, defaultBarRenderBackend, defaultVuRenderBackend) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                PREF_KEY_VIS_OSC_WINDOW_MS -> {
                    state.oscWindowMs = sharedPrefs.getInt(PREF_KEY_VIS_OSC_WINDOW_MS, 40).coerceIn(5, 200)
                }
                PREF_KEY_VIS_OSC_TRIGGER_MODE -> {
                    state.oscTriggerModeNative = parseOscTriggerModeNative(
                        sharedPrefs.getString(PREF_KEY_VIS_OSC_TRIGGER_MODE, "rising")
                    )
                }
                PREF_KEY_VIS_OSC_FPS_MODE -> {
                    state.oscFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_FPS_MODE,
                            VisualizationOscFpsMode.Default.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_OSC_RENDER_BACKEND -> {
                    state.oscRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_RENDER_BACKEND,
                            AppDefaults.Visualization.Oscilloscope.renderBackend.storageValue
                        ),
                        AppDefaults.Visualization.Oscilloscope.renderBackend
                    )
                }
                PREF_KEY_VIS_OSC_LINE_WIDTH_DP -> {
                    state.oscLineWidthDp = sharedPrefs.getInt(PREF_KEY_VIS_OSC_LINE_WIDTH_DP, 3).coerceIn(1, 12)
                }
                PREF_KEY_VIS_OSC_GRID_WIDTH_DP -> {
                    state.oscGridWidthDp = sharedPrefs.getInt(PREF_KEY_VIS_OSC_GRID_WIDTH_DP, 2).coerceIn(1, 8)
                }
                PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED -> {
                    state.oscVerticalGridEnabled =
                        sharedPrefs.getBoolean(PREF_KEY_VIS_OSC_VERTICAL_GRID_ENABLED, false)
                }
                PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED -> {
                    state.oscCenterLineEnabled =
                        sharedPrefs.getBoolean(PREF_KEY_VIS_OSC_CENTER_LINE_ENABLED, false)
                }
                PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK -> {
                    state.oscLineColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_LINE_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK -> {
                    state.oscGridColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_GRID_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK -> {
                    state.oscLineColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_LINE_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK -> {
                    state.oscGridColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_OSC_GRID_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR -> {
                    state.oscCustomLineColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_LINE_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR -> {
                    state.oscCustomGridColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_OSC_CUSTOM_GRID_COLOR, 0x66FFFFFF)
                }
                PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED -> {
                    state.oscContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_OSC_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Oscilloscope.contrastBackdropEnabled
                    )
                }
                PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK -> {
                    state.barColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK -> {
                    state.barColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_BAR_CUSTOM_COLOR -> {
                    state.barCustomColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_BAR_CUSTOM_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED -> {
                    state.barFrequencyGridEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_BAR_FREQUENCY_GRID_ENABLED,
                        AppDefaults.Visualization.Bars.frequencyGridEnabled
                    )
                }
                PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED -> {
                    state.barContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_BAR_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Bars.contrastBackdropEnabled
                    )
                }
                PREF_KEY_VIS_BAR_FPS_MODE -> {
                    state.barFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_FPS_MODE,
                            AppDefaults.Visualization.Bars.fpsMode.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_BAR_RENDER_BACKEND -> {
                    state.barRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_BAR_RENDER_BACKEND,
                            defaultBarRenderBackend.storageValue
                        ),
                        defaultBarRenderBackend
                    )
                }
                PREF_KEY_VIS_VU_COLOR_NO_ARTWORK -> {
                    state.vuColorModeNoArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_COLOR_NO_ARTWORK,
                            VisualizationOscColorMode.Monet.storageValue
                        ),
                        VisualizationOscColorMode.Monet
                    )
                }
                PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK -> {
                    state.vuColorModeWithArtwork = VisualizationOscColorMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_COLOR_WITH_ARTWORK,
                            VisualizationOscColorMode.Artwork.storageValue
                        ),
                        VisualizationOscColorMode.Artwork
                    )
                }
                PREF_KEY_VIS_VU_CUSTOM_COLOR -> {
                    state.vuCustomColorArgb =
                        sharedPrefs.getInt(PREF_KEY_VIS_VU_CUSTOM_COLOR, 0xFF6BD8FF.toInt())
                }
                PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED -> {
                    state.vuContrastBackdropEnabled = sharedPrefs.getBoolean(
                        PREF_KEY_VIS_VU_CONTRAST_BACKDROP_ENABLED,
                        AppDefaults.Visualization.Vu.contrastBackdropEnabled
                    )
                }
                PREF_KEY_VIS_VU_FPS_MODE -> {
                    state.vuFpsMode = VisualizationOscFpsMode.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_FPS_MODE,
                            AppDefaults.Visualization.Vu.fpsMode.storageValue
                        )
                    )
                }
                PREF_KEY_VIS_VU_RENDER_BACKEND -> {
                    state.vuRuntimeRenderBackend = VisualizationRenderBackend.fromStorage(
                        sharedPrefs.getString(
                            PREF_KEY_VIS_VU_RENDER_BACKEND,
                            defaultVuRenderBackend.storageValue
                        ),
                        defaultVuRenderBackend
                    )
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    return state
}

@Composable
private fun rememberPlayerMarqueeClockState(resetKey: Any?): State<Long> {
    val clockState = remember { mutableLongStateOf(0L) }
    LaunchedEffect(resetKey) {
        val startTimeMs = withFrameMillis { it }
        clockState.longValue = 0L
        while (true) {
            clockState.longValue = withFrameMillis { it - startTimeMs }
        }
    }
    return clockState
}

private fun playerMarqueeMotionFadeAlpha(
    elapsedMs: Int,
    segmentDurationMs: Int,
    fadeInMs: Int,
    fadeOutMs: Int
): Float {
    if (segmentDurationMs <= 0) return 0f
    val fadeInProgress = (elapsedMs.toFloat() / fadeInMs.coerceAtLeast(1)).coerceIn(0f, 1f)
    val fadeOutProgress = (
        (segmentDurationMs - elapsedMs).toFloat() / fadeOutMs.coerceAtLeast(1)
        ).coerceIn(0f, 1f)
    return minOf(fadeInProgress, fadeOutProgress)
}

private class PlayerLayoutConstraints(val maxWidth: Dp, val maxHeight: Dp)

/**
 * Layout box whose content reads the measured size instead of constraints.
 * Avoids a measure-time subcomposition (BoxWithConstraints), which is a
 * crash hazard for measure-driven recomposition on slow devices.
 */
@Composable
private fun PlayerLayoutBox(
    modifier: Modifier,
    content: @Composable PlayerLayoutConstraints.() -> Unit
) {
    var deferredSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(modifier.onSizeChangedDeferred { deferredSize = it }) {
        with(density) {
            content(PlayerLayoutConstraints(deferredSize.width.toDp(), deferredSize.height.toDp()))
        }
    }
}

private enum class CanvasSeekSide {
    Backward,
    Forward
}

private data class CanvasSeekFeedbackState(
    val side: CanvasSeekSide,
    val totalSeconds: Int,
    val token: Long = 0L
)

@Composable
private fun CanvasSeekFeedbackOverlay(
    feedbackState: CanvasSeekFeedbackState?,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = feedbackState != null,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(120)) +
                scaleIn(initialScale = 0.82f, animationSpec = androidx.compose.animation.core.tween(120)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(220)),
        modifier = modifier
    ) {
        if (feedbackState != null) {
            val isBackward = feedbackState.side == CanvasSeekSide.Backward
            val alignment = if (isBackward) Alignment.CenterStart else Alignment.CenterEnd
            val text = if (isBackward) "-${feedbackState.totalSeconds}s" else "+${feedbackState.totalSeconds}s"
            val icon = if (isBackward) Icons.Default.KeyboardDoubleArrowLeft else Icons.Default.KeyboardDoubleArrowRight

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = alignment
            ) {
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isBackward) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White
                            )
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(
    file: File?,
    onBack: () -> Unit,
    onCollapseBySwipe: () -> Unit = onBack,
    enableCollapseGesture: Boolean = true,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean = false,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStopAndClear: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    durationSeconds: Double,
    positionSeconds: Double,
    title: String,
    artist: String,
    album: String,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    playbackSourceLabel: String? = null,
    pathOrUrl: String? = null,
    playlistTitle: String? = null,
    playlistFormatLabel: String? = null,
    playlistTrackCount: Int = 0,
    playlistPathOrUrl: String? = null,
    artwork: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState = ArtworkSwipePreviewState(),
    noArtworkIcon: ImageVector = Icons.Default.MusicNote,
    repeatMode: RepeatMode,
    canCycleRepeatMode: Boolean,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    playbackStartInProgress: Boolean = false,
    seekInProgress: Boolean = false,
    previousRestartsAfterThreshold: Boolean = true,
    onSeek: (Double) -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    canOpenPlaylistSelector: Boolean = false,
    onOpenPlaylistSelector: () -> Unit = {},
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    titleCurrentSubtuneIndex: Int = 0,
    titleSubtuneCount: Int = 0,
    subtuneTitleClickable: Boolean = false,
    onCycleRepeatMode: () -> Unit,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    visualizationMode: VisualizationMode,
    availableVisualizationModes: List<VisualizationMode>,
    onCycleVisualizationMode: () -> Unit,
    onSelectVisualizationMode: (VisualizationMode) -> Unit,
    onOpenVisualizationSettings: () -> Unit,
    onOpenSelectedVisualizationSettings: () -> Unit,
    visualizationBarCount: Int,
    visualizationBarSmoothingPercent: Int,
    visualizationBarRoundnessDp: Int,
    visualizationBarOverlayArtwork: Boolean,
    visualizationBarUseThemeColor: Boolean,
    visualizationBarRenderBackend: VisualizationRenderBackend,
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    visualizationVuSmoothingPercent: Int,
    visualizationVuRenderBackend: VisualizationRenderBackend,
    visualizationPerformanceMode: VisualizationPerformanceMode = com.flopster101.siliconplayer.AppDefaults.Visualization.performanceMode,
    visualizationShowDebugInfo: Boolean = false,
    artworkCornerRadiusDp: Int = 3,
    canvasTapToSeekSeconds: Int = com.flopster101.siliconplayer.AppDefaults.Player.canvasTapToSeekSeconds,
    isTrackFavorited: Boolean = false,
    onToggleFavoriteTrack: () -> Unit = {},
    onOpenAudioEffects: () -> Unit,
    showAudioOutputRouteChip: Boolean = com.flopster101.siliconplayer.AppDefaults.Player.showAudioOutputRouteChip,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode = com.flopster101.siliconplayer.AppDefaults.Player.filenameDisplayMode,
    filenameOnlyWhenTitleMissing: Boolean = false,
    externalTrackInfoDialogRequestToken: Int = 0,
    playbackCapabilitiesFlags: Int = 0,
    bitPerfectUsbAudio: Boolean = false,
    onBitPerfectUsbAudioChanged: (Boolean) -> Unit = {},
    onCollapseDragProgressChanged: (Boolean) -> Unit = {}
) {
    var sliderPosition by remember(file?.absolutePath, durationSeconds) {
        mutableDoubleStateOf(positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0)))
    }
    var isSeeking by remember { mutableStateOf(false) }
    var isTimelineTouchActive by remember { mutableStateOf(false) }
    var downwardDragPx by remember { mutableFloatStateOf(0f) }
    var isDraggingDown by remember { mutableStateOf(false) }
    var collapseAnimatingOut by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showVisualizationPickerDialog by remember { mutableStateOf(false) }
    var showVisualizationOptionsSheet by remember { mutableStateOf(false) }
    var showChannelControlDialog by remember { mutableStateOf(false) }
    var showAudioOutputDetailsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showVisualizationModeBadge by remember { mutableStateOf(false) }
    var visualizationModeBadgeText by remember { mutableStateOf(visualizationMode.label) }
    var lastVisualizationModeForBadge by remember { mutableStateOf<VisualizationMode?>(null) }
    var isVisualizationFullscreen by remember { mutableStateOf(false) }
    // Only one GL pipeline may render at a time. When fullscreen is active the
    // player-body visualizer is put to Off so the fullscreen overlay owns the
    // sole projectM/GL pipeline (its view unmounts and warms the handle cache).
    // projectM tears down immediately to keep that exclusion strict; the other
    // visualizers defer teardown by one frame so the dispose does not land in
    // the same measure pass that mounts the fullscreen overlay.
    var deferBodyVisOff by remember { mutableStateOf(false) }
    LaunchedEffect(isVisualizationFullscreen, visualizationMode) {
        if (isVisualizationFullscreen && visualizationMode != VisualizationMode.ProjectM) {
            withFrameNanos { }
            deferBodyVisOff = true
        } else {
            deferBodyVisOff = false
        }
    }
    val bodyVisualizationMode =
        if (isVisualizationFullscreen &&
            (visualizationMode == VisualizationMode.ProjectM || deferBodyVisOff)
        ) {
            VisualizationMode.Off
        } else {
            visualizationMode
        }
    var showFullscreenAffordance by remember { mutableStateOf(false) }
    var affordanceInteractionTick by remember { mutableIntStateOf(0) }
    var seekFeedbackState by remember { mutableStateOf<CanvasSeekFeedbackState?>(null) }
    var pendingSingleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapSide by remember { mutableStateOf<CanvasSeekSide?>(null) }
    var accumulatedSeekSeconds by remember { mutableIntStateOf(0) }
    var activeSeekTargetPosition by remember { mutableStateOf<Double?>(null) }
    var feedbackToken by remember { mutableLongStateOf(0L) }
    val canvasGestureScope = rememberCoroutineScope()

    val latestPositionSeconds by rememberUpdatedState(positionSeconds)
    val latestDurationSeconds by rememberUpdatedState(durationSeconds)
    val latestCanSeek by rememberUpdatedState(canSeek)
    val latestCanvasTapToSeekSeconds by rememberUpdatedState(canvasTapToSeekSeconds)
    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestFile by rememberUpdatedState(file)
    val latestIsVisualizationFullscreen by rememberUpdatedState(isVisualizationFullscreen)
    val latestIsSeeking by rememberUpdatedState(isSeeking)

    val handleCanvasTap: (androidx.compose.ui.geometry.Offset, Float) -> Unit = { offset, boxWidth ->
        if (!latestIsVisualizationFullscreen && latestFile != null) {
            val seekStep = latestCanvasTapToSeekSeconds
            val seekAvailable = latestCanSeek && seekStep > 0
            if (!seekAvailable || boxWidth <= 0f) {
                pendingSingleTapJob?.cancel()
                pendingSingleTapJob = null
                lastTapSide = null
                lastTapTimestamp = 0L
                accumulatedSeekSeconds = 0
                activeSeekTargetPosition = null
                showFullscreenAffordance = true
            } else {
                val leftThreshold = boxWidth * 0.35f
                val rightThreshold = boxWidth * 0.65f

                if (offset.x in leftThreshold..rightThreshold) {
                    // Center tap -> immediately toggle affordance
                    pendingSingleTapJob?.cancel()
                    pendingSingleTapJob = null
                    lastTapSide = null
                    lastTapTimestamp = 0L
                    accumulatedSeekSeconds = 0
                    activeSeekTargetPosition = null
                    showFullscreenAffordance = true
                } else {
                    val side = if (offset.x < leftThreshold) CanvasSeekSide.Backward else CanvasSeekSide.Forward
                    val now = android.os.SystemClock.elapsedRealtime()
                    val isMultiTap = (lastTapSide == side && (now - lastTapTimestamp) <= 400L) ||
                            (seekFeedbackState?.side == side && (now - lastTapTimestamp) <= 750L)

                    if (isMultiTap) {
                        // Multi-tap seek!
                        pendingSingleTapJob?.cancel()
                        pendingSingleTapJob = null
                        showFullscreenAffordance = false
                        lastTapTimestamp = now

                        val currentAccum = if (accumulatedSeekSeconds <= 0) seekStep else accumulatedSeekSeconds + seekStep
                        accumulatedSeekSeconds = currentAccum

                        val delta = if (side == CanvasSeekSide.Backward) -seekStep.toDouble() else seekStep.toDouble()
                        val currentPlaybackPos = if (latestIsSeeking) sliderPosition else latestPositionSeconds
                        val basePos = activeSeekTargetPosition ?: currentPlaybackPos
                        val maxDuration = latestDurationSeconds.coerceAtLeast(0.0)
                        val targetPos = if (maxDuration > 0.0) {
                            (basePos + delta).coerceIn(0.0, maxDuration)
                        } else {
                            (basePos + delta).coerceAtLeast(0.0)
                        }
                        activeSeekTargetPosition = targetPos
                        sliderPosition = targetPos
                        latestOnSeek(targetPos)

                        val token = ++feedbackToken
                        seekFeedbackState = CanvasSeekFeedbackState(
                            side = side,
                            totalSeconds = currentAccum,
                            token = token
                        )
                        canvasGestureScope.launch {
                            kotlinx.coroutines.delay(700L)
                            if (feedbackToken == token) {
                                seekFeedbackState = null
                                accumulatedSeekSeconds = 0
                                activeSeekTargetPosition = null
                                lastTapSide = null
                                lastTapTimestamp = 0L
                            }
                        }
                    } else {
                        // First tap on this side -> wait debounce timeout
                        pendingSingleTapJob?.cancel()
                        lastTapSide = side
                        lastTapTimestamp = now
                        accumulatedSeekSeconds = 0
                        activeSeekTargetPosition = null
                        pendingSingleTapJob = canvasGestureScope.launch {
                            kotlinx.coroutines.delay(280L)
                            if (!latestIsVisualizationFullscreen && latestFile != null) {
                                showFullscreenAffordance = true
                            }
                            lastTapSide = null
                            lastTapTimestamp = 0L
                            accumulatedSeekSeconds = 0
                            activeSeekTargetPosition = null
                        }
                    }
                }
            }
        }
    }
    val currentHandleCanvasTap by rememberUpdatedState(handleCanvasTap)
    val prefs = remember {
        context.getSharedPreferences(AppPreferenceKeys.PREFS_NAME, Context.MODE_PRIVATE)
    }
    var fullscreenModePref by remember {
        mutableStateOf(
            VisualizationFullscreenMode.fromStorage(
                prefs.getString(AppPreferenceKeys.VISUALIZATION_FULLSCREEN_MODE, null)
            )
        )
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AppPreferenceKeys.VISUALIZATION_FULLSCREEN_MODE) {
                fullscreenModePref = VisualizationFullscreenMode.fromStorage(
                    prefs.getString(key, null)
                )
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val visualizationPrefsState = rememberPlayerVisualizationPreferenceState(
        prefs = prefs,
        defaultBarRenderBackend = visualizationBarRenderBackend,
        defaultVuRenderBackend = visualizationVuRenderBackend
    )
    val channelScopePrefs = rememberChannelScopePrefs(prefs)
    val onVisualizerAction: () -> Unit = {
        when (visualizationMode) {
            VisualizationMode.ProjectM ->
                SiliconVisNativeBridge.nativeProjectMNextPreset(true)
            VisualizationMode.ChannelScope ->
                prefs.edit()
                    .putBoolean(
                        "visualization_channel_scope_text_enabled",
                        !channelScopePrefs.textEnabled
                    )
                    .apply()
            else -> Unit
        }
    }
    val trackGainPrefs = remember {
        context.getSharedPreferences("silicon_player_channel_scope_track_gains", Context.MODE_PRIVATE)
    }
    val currentTrackKey = file?.absolutePath ?: playlistPathOrUrl.orEmpty()
    var trackInputGain by remember(currentTrackKey) {
        mutableIntStateOf(
            if (currentTrackKey.isNotEmpty()) trackGainPrefs.getInt(currentTrackKey, 100) else 100
        )
    }
    val effectiveChannelScopeGainPercent = ((channelScopePrefs.gainPercent * trackInputGain) / 100).coerceIn(1, 10000)
    val effectiveChannelScopePrefs = remember(channelScopePrefs, effectiveChannelScopeGainPercent) {
        channelScopePrefs.copy(gainPercent = effectiveChannelScopeGainPercent)
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isTabletLike = configuration.smallestScreenWidthDp >= 600
    val portraitDeviceAspectRatio = if (!isLandscape && configuration.screenWidthDp > 0) {
        configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
    } else {
        2f
    }
    val shortPortraitDevice = !isLandscape && (
        portraitDeviceAspectRatio < 1.9f ||
            configuration.screenHeightDp < 640
    )
    val collapseThresholdPx = with(density) { 128.dp.toPx() }
    val collapseDecisionThresholdPx = with(density) { 96.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val maxCollapseDragPx = screenHeightPx
    val uiScope = rememberCoroutineScope()
    val latestOnCollapseBySwipe by rememberUpdatedState(onCollapseBySwipe)

    LaunchedEffect(isDraggingDown, collapseAnimatingOut) {
        onCollapseDragProgressChanged(isDraggingDown || collapseAnimatingOut)
    }
    DisposableEffect(Unit) {
        onDispose {
            onCollapseDragProgressChanged(false)
        }
    }
    LaunchedEffect(externalTrackInfoDialogRequestToken) {
        if (externalTrackInfoDialogRequestToken > 0) {
            showTrackInfoDialog = true
        }
    }

    LaunchedEffect(positionSeconds, isSeeking) {
        if (!isSeeking) {
            sliderPosition = positionSeconds.coerceIn(0.0, durationSeconds.coerceAtLeast(0.0))
        }
    }
    val panelOffsetAnim = remember { Animatable(0f) }
    LaunchedEffect(isDraggingDown, downwardDragPx) {
        if (isDraggingDown) {
            panelOffsetAnim.snapTo(downwardDragPx)
        }
    }
    val panelFadeDenom = collapseThresholdPx * 1.4f
    val topArrowFocusRequester = remember { FocusRequester() }
    val primaryContentFocusRequester = remember { FocusRequester() }
    var showRemainingTime by rememberSaveable { mutableStateOf(false) }
    val playerMarqueeClockState = rememberPlayerMarqueeClockState(file?.absolutePath)

    val hasTrack = file != null
    val remoteLoadUiState = RemoteLoadUiStateHolder.current
    val sanitizedTitle = sanitizeRemoteCachedMetadataTitle(title, file)
    val showMetadataLoadingPlaceholder = hasTrack &&
        (playbackStartInProgress || remoteLoadUiState != null) &&
        sanitizedTitle.isBlank()
    val displayTitle = sanitizedTitle.ifBlank {
        when {
            file == null -> "No track loaded"
            showMetadataLoadingPlaceholder -> ""
            else -> inferredDisplayTitleForName(file.name)
        }
    }
    val displayArtist = remember(artist, hasTrack) {
        val parsed = formatDisplayArtist(artist)
        parsed.ifBlank { if (hasTrack) "Unknown Artist" else "Unknown" }
    }
    val displayAlbum = album.ifBlank { if (hasTrack) "Unknown Album" else "" }
    val displayFilename = file?.let { toDisplayFilename(it) }.orEmpty()
    val fileSizeBytes = file?.length() ?: 0L
    val formatLabel by produceState<String>(
        initialValue = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "EMPTY",
        hasTrack,
        decoderName,
        file?.absolutePath
    ) {
        value = if (hasTrack && decoderName != null) {
            withContext(Dispatchers.PlaybackIo) {
                readCurrentFormatName(decoderName)
            } ?: file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "UNKNOWN"
        } else {
            file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "EMPTY"
        }
    }
    val trackBitrateOrSize by produceState<String?>(
        initialValue = null,
        hasTrack,
        file?.absolutePath,
        decoderName,
        fileSizeBytes,
        sampleRateHz
    ) {
        value = when {
            !hasTrack -> null
            decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true) -> {
                var resolved: String? = null
                repeat(8) { attempt ->
                    val bitrate = withContext(Dispatchers.PlaybackIo) { NativeBridge.getTrackBitrate() }
                    val isVBR = withContext(Dispatchers.PlaybackIo) { NativeBridge.isTrackVBR() }
                    if (bitrate > 0) {
                        resolved = formatBitrate(bitrate, isVBR)
                        return@repeat
                    }
                    if (attempt < 7) {
                        delay(120L)
                    }
                }
                resolved
            }
            fileSizeBytes > 0 -> formatFileSize(fileSizeBytes)
            else -> null
        }
    }
    val extensionLabel = remember(file) {
        file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: ""
    }
    val trackTechnicalInfo = remember(
        formatLabel,
        extensionLabel,
        trackBitrateOrSize,
        sampleRateHz,
        channelCount,
        bitDepthLabel,
        decoderName,
        hasTrack
    ) {
        buildTrackTechnicalInfo(
            formatLabel = formatLabel,
            extensionLabel = extensionLabel,
            bitrateOrSize = trackBitrateOrSize,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            decoderName = decoderName,
            hasTrack = hasTrack
        )
    }
    LaunchedEffect(visualizationMode) {
        val previous = lastVisualizationModeForBadge
        lastVisualizationModeForBadge = visualizationMode
        if (previous == null || previous == visualizationMode) return@LaunchedEffect
        visualizationModeBadgeText = visualizationMode.label
        showVisualizationModeBadge = true
        delay(1200)
        showVisualizationModeBadge = false
    }
    val transportAnchorFocusRequester = remember { FocusRequester() }
    val actionStripFirstFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                // Only handle key down events to avoid double-triggering
                if (keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                handlePlayerGlobalKeyDown(
                    keyEvent = keyEvent,
                    hasTrack = hasTrack,
                    canResumeStoppedTrack = canResumeStoppedTrack,
                    isPlaying = isPlaying,
                    canPreviousSubtune = canPreviousSubtune,
                    canNextSubtune = canNextSubtune,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    canSeek = canSeek,
                    durationSeconds = durationSeconds,
                    canCycleRepeatMode = canCycleRepeatMode,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousSubtune = onPreviousSubtune,
                    onNextSubtune = onNextSubtune,
                    onPreviousTrack = onPreviousTrack,
                    onNextTrack = onNextTrack,
                    onSeek = onSeek,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onStopAndClear = onStopAndClear
                )
            }
            .graphicsLayer {
                val px = panelOffsetAnim.value
                translationY = px
                val drag = (px / panelFadeDenom).coerceIn(0f, 1f)
                alpha = 1f - (0.22f * drag)
            }
            .then(
                if (enableCollapseGesture) {
                    Modifier.pointerInput(collapseThresholdPx, isTimelineTouchActive) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                if (isTimelineTouchActive || collapseAnimatingOut) return@detectVerticalDragGestures
                                val next = (downwardDragPx + dragAmount).coerceIn(0f, maxCollapseDragPx)
                                if (next > 0f || downwardDragPx > 0f) {
                                    isDraggingDown = true
                                    downwardDragPx = next
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (isTimelineTouchActive || collapseAnimatingOut) return@detectVerticalDragGestures
                                val shouldCollapse = downwardDragPx >= collapseDecisionThresholdPx
                                if (shouldCollapse) {
                                    val releaseOffsetPx = downwardDragPx.coerceIn(0f, maxCollapseDragPx)
                                    isDraggingDown = false
                                    collapseAnimatingOut = true
                                    val collapseSettleTargetPx = screenHeightPx
                                    uiScope.launch {
                                        panelOffsetAnim.snapTo(releaseOffsetPx)
                                        val remainingDistancePx =
                                            (collapseSettleTargetPx - releaseOffsetPx).coerceAtLeast(0f)
                                        if (remainingDistancePx > 1f) {
                                            val remainingRatio =
                                                (remainingDistancePx / screenHeightPx).coerceIn(0f, 1f)
                                            val settleDurationMs =
                                                (130f + (190f * remainingRatio)).toInt()
                                            panelOffsetAnim.animateTo(
                                                targetValue = collapseSettleTargetPx,
                                                animationSpec = tween(
                                                    durationMillis = settleDurationMs,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            )
                                        }
                                        latestOnCollapseBySwipe()
                                    }
                                } else {
                                    val releaseOffsetPx = downwardDragPx
                                    isDraggingDown = false
                                    uiScope.launch {
                                        panelOffsetAnim.snapTo(releaseOffsetPx)
                                        val settleDurationMs = (
                                            190f + (170f * (releaseOffsetPx / collapseDecisionThresholdPx).coerceIn(0f, 1f))
                                            ).toInt()
                                        panelOffsetAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = tween(
                                                durationMillis = settleDurationMs,
                                                easing = LinearOutSlowInEasing
                                            )
                                        )
                                        downwardDragPx = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                if (isTimelineTouchActive || collapseAnimatingOut) return@detectVerticalDragGestures
                                val releaseOffsetPx = downwardDragPx
                                isDraggingDown = false
                                uiScope.launch {
                                    panelOffsetAnim.snapTo(releaseOffsetPx)
                                    val settleDurationMs = (
                                        190f + (170f * (releaseOffsetPx / collapseDecisionThresholdPx).coerceIn(0f, 1f))
                                        ).toInt()
                                    panelOffsetAnim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            durationMillis = settleDurationMs,
                                            easing = LinearOutSlowInEasing
                                        )
                                    )
                                    downwardDragPx = 0f
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        CompositionLocalProvider(LocalPlayerMarqueeClockState provides playerMarqueeClockState) {
            val isWatchDevice = remember(context) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
            }

            Scaffold(
                contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
                topBar = {
                    if (!isWatchDevice) {
                        PlayerTopBar(
                            isLandscape = isLandscape,
                            isTabletLike = isTabletLike,
                            compactPortraitHeader = shortPortraitDevice,
                            onBack = onBack,
                            enableCollapseGesture = enableCollapseGesture,
                            focusRequester = topArrowFocusRequester,
                            downFocusRequester = if (canSeek && durationSeconds > 0.0) {
                                primaryContentFocusRequester
                            } else {
                                transportAnchorFocusRequester
                            },
                            canOpenCoreSettings = canOpenCoreSettings,
                            onOpenCoreSettings = onOpenCoreSettings,
                            onOpenTrackInfo = { showTrackInfoDialog = true },
                            onOpenAudioEffects = onOpenAudioEffects,
                            onOpenChannelControls = { showChannelControlDialog = true },
                            showAudioOutputRouteChip = showAudioOutputRouteChip,
                            canOpenPlaylistSelector = canOpenPlaylistSelector,
                            onOpenPlaylistSelector = onOpenPlaylistSelector,
                            onOpenVisualizationPicker = { showVisualizationPickerDialog = true },
                            onOpenAudioOutputDetails = { showAudioOutputDetailsDialog = true }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isWatchDevice) PaddingValues(0.dp) else innerPadding)
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                                )
                            )
                        )
                ) {
            if (isWatchDevice) {
                WearPlayerContent(
                    file = file,
                    displayTitle = displayTitle,
                    displayArtist = displayArtist,
                    decoderName = decoderName,
                    titleCurrentSubtuneIndex = titleCurrentSubtuneIndex,
                    titleSubtuneCount = titleSubtuneCount,
                    subtuneTitleClickable = subtuneTitleClickable,
                    onOpenSubtuneSelector = onOpenSubtuneSelector,
                    trackTechnicalInfo = trackTechnicalInfo,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    playbackStartInProgress = playbackStartInProgress,
                    seekInProgress = seekInProgress,
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    showRemainingTime = showRemainingTime,
                    canSeek = canSeek,
                    hasReliableDuration = hasReliableDuration,
                    onToggleDurationDisplayMode = {
                        showRemainingTime = !showRemainingTime
                    },
                    onSeek = onSeek,
                    onPlay = onPlay,
                    onPause = onPause,
                    onPreviousTrack = onPreviousTrack,
                    onForcePreviousTrack = onForcePreviousTrack,
                    onNextTrack = onNextTrack,
                    onPreviousSubtune = onPreviousSubtune,
                    onNextSubtune = onNextSubtune,
                    canPreviousTrack = canPreviousTrack,
                    canNextTrack = canNextTrack,
                    canPreviousSubtune = canPreviousSubtune,
                    canNextSubtune = canNextSubtune,
                    canCycleRepeatMode = canCycleRepeatMode,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onStopAndClear = onStopAndClear,
                    artwork = artwork,
                    artworkSwipePreviewState = artworkSwipePreviewState,
                    noArtworkIcon = noArtworkIcon,
                    isVisualizationFullscreen = isVisualizationFullscreen,
                    visualizationMode = visualizationMode,
                    visualizationModeBadgeText = visualizationModeBadgeText,
                    visualizationPrefsState = visualizationPrefsState,
                    visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                    visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                    visualizationBarCount = visualizationBarCount,
                    visualizationBarRoundnessDp = visualizationBarRoundnessDp,
                    visualizationBarOverlayArtwork = visualizationBarOverlayArtwork,
                    visualizationBarUseThemeColor = visualizationBarUseThemeColor,
                    visualizationPerformanceMode = visualizationPerformanceMode,
                    visualizationOscStereo = visualizationOscStereo,
                    visualizationVuAnchor = visualizationVuAnchor,
                    visualizationVuUseThemeColor = visualizationVuUseThemeColor,
                    channelScopePrefs = effectiveChannelScopePrefs,
                    artworkCornerRadiusDp = artworkCornerRadiusDp,
                    availableVisualizationModes = availableVisualizationModes,
                    onSelectVisualizationMode = onSelectVisualizationMode,
                    onCycleVisualizationMode = onCycleVisualizationMode,
                    isTrackFavorited = isTrackFavorited,
                    onToggleFavoriteTrack = onToggleFavoriteTrack,
                    canToggleFavoriteTrack = pathOrUrl != null,
                    onOpenAudioEffects = onOpenAudioEffects,
                    onOpenChannelControls = { showChannelControlDialog = true },
                    onBack = onBack,
                    onOpenTrackInfo = { showTrackInfoDialog = true }
                )
            } else if (isLandscape) {
                PlayerLayoutBox(modifier = Modifier.fillMaxSize()) {
                    val landscapeWidthScale = normalizedScale(maxWidth, compactDp = 640.dp, roomyDp = 1280.dp)
                    val landscapeHeightScale = normalizedScale(maxHeight, compactDp = 320.dp, roomyDp = 720.dp)
                    val landscapeLayoutScale = (landscapeHeightScale * 0.65f + landscapeWidthScale * 0.35f)
                        .coerceIn(0f, 1f)
                    val horizontalPadding = lerpDp(10.dp, 16.dp, landscapeLayoutScale)
                    val verticalPadding = lerpDp(6.dp, 12.dp, landscapeLayoutScale)
                    val paneGap = lerpDp(16.dp, 32.dp, landscapeLayoutScale)
                    val artPaneWeight = lerpFloat(0.38f, 0.48f, landscapeLayoutScale)
                    val rightPaneWeight = 1f - artPaneWeight
                    val landscapeTitleScaleBoost = lerpFloat(2.0f, 4f, landscapeLayoutScale)
                    val landscapeSupportingScaleBoost = lerpFloat(1f, 2.2f, landscapeLayoutScale)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                        horizontalArrangement = Arrangement.spacedBy(paneGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(artPaneWeight)
                                .fillMaxHeight()
                                .padding(lerpDp(24.dp, 32.dp, landscapeLayoutScale))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                        .pointerInput(Unit) {
                                            detectTapGestures(onTap = { offset ->
                                                currentHandleCanvasTap(offset, size.width.toFloat())
                                            })
                                        }
                                ) {
                                    AlbumArtPlaceholder(
                                file = file,
                                isPlaying = isPlaying && !seekInProgress,
                                decoderName = decoderName,
                                sampleRateHz = sampleRateHz,
                                artwork = artwork,
                                artworkSwipePreviewState = artworkSwipePreviewState,
                                placeholderIcon = noArtworkIcon,
                                visualizationModeBadgeText = visualizationModeBadgeText,
                                showVisualizationModeBadge = showVisualizationModeBadge,
                                visualizationMode = bodyVisualizationMode,
                                visualizationPerformanceMode = visualizationPerformanceMode,
                                visualizationShowDebugInfo = visualizationShowDebugInfo,
                                visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                                visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                                visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                                visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                                visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                                visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                barCount = visualizationBarCount,
                                barRoundnessDp = visualizationBarRoundnessDp,
                                barOverlayArtwork = visualizationBarOverlayArtwork,
                                barUseThemeColor = visualizationBarUseThemeColor,
                                barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                                barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                                barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                                barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                                barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                                oscStereo = visualizationOscStereo,
                                oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                                oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                                oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                                oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                                oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                                oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                                oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                                oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                                oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                                oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                                oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                                vuAnchor = visualizationVuAnchor,
                                vuUseThemeColor = visualizationVuUseThemeColor,
                                vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                                barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                                channelScopePrefs = effectiveChannelScopePrefs,
                                artworkCornerRadiusDp = artworkCornerRadiusDp,
                                onSwipePreviousTrack = onForcePreviousTrack,
                                onSwipeNextTrack = onNextTrack,
                                modifier = Modifier.fillMaxSize()
                            )
                                    Box(modifier = Modifier.align(Alignment.Center)) {
                                        FullscreenToggleAffordance(
                                            onToggle = { isVisualizationFullscreen = true },
                                            show = showFullscreenAffordance
                                        )
                                    }
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showFullscreenAffordance,
                                            enter = fadeIn(),
                                            exit = fadeOut(),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 12.dp)
                                        ) {
                                            FullscreenVisualizerSwitcher(
                                                visualizationMode = visualizationMode,
                                                availableVisualizationModes = availableVisualizationModes,
                                                onCycleVisualizationMode = onCycleVisualizationMode,
                                                onSelectVisualizationMode = onSelectVisualizationMode,
                                                onVisualizerAction = onVisualizerAction,
                                                onVisualizerLongPress = {
                                                    if (!isVisualizationFullscreen) {
                                                        showVisualizationPickerDialog = true
                                                    }
                                                },
                                                onInteraction = { affordanceInteractionTick++ },
                                                compact = false
                                            )
                                        }
                                        CanvasSeekFeedbackOverlay(
                                            feedbackState = seekFeedbackState,
                                            modifier = Modifier.matchParentSize()
                                        )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(rightPaneWeight)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 480.dp)
                                    .padding(horizontal = 32.dp)
                                    .wrapContentHeight(),
                                verticalArrangement = Arrangement.Top,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PortraitTrackMetadataBlock(
                                    title = displayTitle,
                                    artist = displayArtist,
                                    album = "",
                                    showLoadingPlaceholder = showMetadataLoadingPlaceholder,
                                    filename = displayFilename,
                                    filenameDisplayMode = filenameDisplayMode,
                                    decoderName = decoderName,
                                    filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                    currentSubtuneIndex = titleCurrentSubtuneIndex,
                                    subtuneCount = titleSubtuneCount,
                                    subtuneTitleClickable = subtuneTitleClickable,
                                    onOpenSubtuneSelector = onOpenSubtuneSelector,
                                    layoutScale = landscapeLayoutScale,
                                    titleScaleBoost = landscapeTitleScaleBoost,
                                    supportingScaleBoost = landscapeSupportingScaleBoost,
                                    fullTechLine = trackTechnicalInfo.fullLine,
                                    fallbackTechLine = trackTechnicalInfo.fallbackLine,
                                    showFavorite = pathOrUrl != null,
                                    favoriteIndicator = {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .focusProperties {
                                                    down = transportAnchorFocusRequester
                                                }
                                                .clip(CircleShape)
                                                .playerFocusHalo(enabled = pathOrUrl != null, shape = CircleShape)
                                                .focusable(enabled = pathOrUrl != null)
                                                .clickable(
                                                    enabled = pathOrUrl != null,
                                                    onClick = onToggleFavoriteTrack
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(
                                                    id = if (isTrackFavorited) {
                                                        R.drawable.ic_star_filled
                                                    } else {
                                                        R.drawable.ic_star_outline
                                                    }
                                                ),
                                                contentDescription = if (isTrackFavorited) {
                                                    "Remove from favorites"
                                                } else {
                                                    "Add to favorites"
                                                },
                                                tint = if (isTrackFavorited) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(Modifier.height(lerpDp(12.dp, 16.dp, landscapeLayoutScale)))

                                TimelineSection(
                                    sliderPosition = if (isSeeking) sliderPosition else positionSeconds,
                                    elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                    durationSeconds = durationSeconds,
                                    showRemainingTime = showRemainingTime,
                                    canSeek = canSeek,
                                    hasReliableDuration = hasReliableDuration,
                                    seekInProgress = seekInProgress,
                                    focusRequester = primaryContentFocusRequester,
                                    upFocusRequester = topArrowFocusRequester,
                                    layoutScale = landscapeLayoutScale,
                                    onToggleDurationDisplayMode = {
                                        showRemainingTime = !showRemainingTime
                                    },
                                    onSeekInteractionChanged = { isTimelineTouchActive = it },
                                    onSliderValueChange = { value ->
                                        isSeeking = true
                                        val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                        sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                                    },
                                    onSliderValueChangeFinished = {
                                        isSeeking = false
                                        if (canSeek && durationSeconds > 0.0) {
                                            onSeek(sliderPosition)
                                        }
                                    }
                                )

                                Spacer(Modifier.height(lerpDp(16.dp, 20.dp, landscapeLayoutScale)))

                                TransportControls(
                                    hasTrack = hasTrack,
                                    isPlaying = isPlaying,
                                    canResumeStoppedTrack = canResumeStoppedTrack,
                                    repeatMode = repeatMode,
                                    playbackStartInProgress = playbackStartInProgress,
                                    remoteLoadUiState = remoteLoadUiState,
                                    seekInProgress = seekInProgress,
                                    positionSeconds = positionSeconds,
                                    previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                    onRestartCurrentSelection = { onSeek(0.0) },
                                    canPreviousTrack = canPreviousTrack,
                                    canNextTrack = canNextTrack,
                                    canCycleRepeatMode = canCycleRepeatMode,
                                    onPlayPause = {
                                        if (isPlaying) {
                                            onPause()
                                        } else {
                                            onPlay()
                                        }
                                    },
                                    onPreviousTrack = onPreviousTrack,
                                    onForcePreviousTrack = onForcePreviousTrack,
                                    onNextTrack = onNextTrack,
                                    onPreviousSubtune = onPreviousSubtune,
                                    onNextSubtune = onNextSubtune,
                                    onOpenSubtuneSelector = onOpenSubtuneSelector,
                                    currentSubtuneIndex = currentSubtuneIndex,
                                    subtuneCount = subtuneCount,
                                    canPreviousSubtune = canPreviousSubtune,
                                    canNextSubtune = canNextSubtune,
                                    canOpenSubtuneSelector = canOpenSubtuneSelector,
                                    onStopAndClear = onStopAndClear,
                                    onCycleRepeatMode = onCycleRepeatMode,
                                    layoutScale = landscapeLayoutScale,
                                    transportAnchorFocusRequester = transportAnchorFocusRequester,
                                    actionStripFirstFocusRequester = actionStripFirstFocusRequester,
                                    spacedByRow = true
                                )

                            }
                        }
                    }
                }
            } else {
                PlayerLayoutBox(modifier = Modifier.fillMaxSize()) {
                    val portraitWidthScale = normalizedScale(maxWidth, compactDp = 320.dp, roomyDp = 840.dp)
                    val portraitHeightScale = normalizedScale(maxHeight, compactDp = 560.dp, roomyDp = 1100.dp)
                    val shortPortraitHeightScale = normalizedScale(maxHeight, compactDp = 520.dp, roomyDp = 760.dp)
                    val portraitAspectRatio = if (maxWidth > 0.dp) {
                        (maxHeight.value / maxWidth.value).coerceAtLeast(1f)
                    } else {
                        2f
                    }
                    val compactPortraitLayout = portraitAspectRatio < 2.02f || shortPortraitHeightScale < 0.78f
                    val shortPortraitLayout = portraitAspectRatio < 1.9f || shortPortraitHeightScale < 0.62f
                    val portraitLayoutScale = (portraitHeightScale * 0.7f + portraitWidthScale * 0.3f)
                        .coerceIn(0f, 1f)
                    val portraitTimelineScale = if (shortPortraitLayout) {
                        (shortPortraitHeightScale * 0.62f + portraitWidthScale * 0.10f).coerceIn(0.38f, 0.62f)
                    } else if (compactPortraitLayout) {
                        (shortPortraitHeightScale * 0.72f).coerceIn(0.42f, 0.72f)
                    } else {
                        (
                            portraitLayoutScale * 0.55f + shortPortraitHeightScale * 0.45f
                        ).coerceIn(0f, 1f)
                    }
                    val portraitTransportScale = if (shortPortraitLayout) {
                        lerpFloat(0.86f, 0.94f, shortPortraitHeightScale)
                    } else if (compactPortraitLayout) {
                        lerpFloat(0.92f, 1f, shortPortraitHeightScale)
                    } else {
                        lerpFloat(0.84f, 1f, shortPortraitHeightScale)
                    }
                    val horizontalPadding = lerpDp(16.dp, 20.dp, portraitLayoutScale)
                    val verticalPadding = lerpDp(8.dp, 12.dp, portraitLayoutScale)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                    ) {
                        val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val minArtworkSize = lerpDp(128.dp, 240.dp, portraitLayoutScale)
                        val contentAvailableHeight = (
                            maxHeight - verticalPadding * 2 - navBarInset
                            ).coerceAtLeast(minArtworkSize)
                        val portraitSectionSpacingScale = (
                            portraitLayoutScale * 0.55f +
                                normalizedScale(contentAvailableHeight, compactDp = 300.dp, roomyDp = 620.dp) * 0.45f
                            ).coerceIn(0f, 1f)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .padding(bottom = lerpDp(16.dp, 20.dp, portraitSectionSpacingScale)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = true)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                            .pointerInput(Unit) {
                                                detectTapGestures(onTap = { offset ->
                                                    currentHandleCanvasTap(offset, size.width.toFloat())
                                                })
                                            }
                                    ) {
                                        AlbumArtPlaceholder(
                                        file = file,
                                        isPlaying = isPlaying && !seekInProgress,
                                        decoderName = decoderName,
                                        sampleRateHz = sampleRateHz,
                                        artwork = artwork,
                                        artworkSwipePreviewState = artworkSwipePreviewState,
                                        placeholderIcon = noArtworkIcon,
                                        visualizationModeBadgeText = visualizationModeBadgeText,
                                        showVisualizationModeBadge = showVisualizationModeBadge,
                                        visualizationMode = bodyVisualizationMode,
                                        visualizationPerformanceMode = visualizationPerformanceMode,
                                        visualizationShowDebugInfo = visualizationShowDebugInfo,
                                        visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                                        visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                                        visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                                        visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                                        visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                                        visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                                        visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                                        visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                                        barCount = visualizationBarCount,
                                        barRoundnessDp = visualizationBarRoundnessDp,
                                        barOverlayArtwork = visualizationBarOverlayArtwork,
                                        barUseThemeColor = visualizationBarUseThemeColor,
                                        barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                                        barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                                        barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                                        barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                                        barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                                        oscStereo = visualizationOscStereo,
                                        oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                                        oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                                        oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                                        oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                                        oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                                        oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                                        oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                                        oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                                        oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                                        oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                                        oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                                        vuAnchor = visualizationVuAnchor,
                                        vuUseThemeColor = visualizationVuUseThemeColor,
                                        vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                                        vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                                        vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                                        vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                                        vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                                        barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                                        channelScopePrefs = effectiveChannelScopePrefs,
                                        artworkCornerRadiusDp = artworkCornerRadiusDp,
                                        onSwipePreviousTrack = onForcePreviousTrack,
                                        onSwipeNextTrack = onNextTrack,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                        Box(modifier = Modifier.align(Alignment.Center)) {
                                            FullscreenToggleAffordance(
                                                onToggle = { isVisualizationFullscreen = true },
                                                show = showFullscreenAffordance
                                            )
                                        }
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = showFullscreenAffordance,
                                            enter = fadeIn(),
                                            exit = fadeOut(),
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 12.dp)
                                        ) {
                                            FullscreenVisualizerSwitcher(
                                                visualizationMode = visualizationMode,
                                                availableVisualizationModes = availableVisualizationModes,
                                                onCycleVisualizationMode = onCycleVisualizationMode,
                                                onSelectVisualizationMode = onSelectVisualizationMode,
                                                onVisualizerAction = onVisualizerAction,
                                                onVisualizerLongPress = {
                                                    if (!isVisualizationFullscreen) {
                                                        showVisualizationPickerDialog = true
                                                    }
                                                },
                                                onInteraction = { affordanceInteractionTick++ },
                                                compact = false
                                            )
                                        }
                                        CanvasSeekFeedbackOverlay(
                                            feedbackState = seekFeedbackState,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                            }
                            Spacer(Modifier.height(16.dp))
                            Column(
                                modifier = Modifier
                                    .wrapContentHeight()
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                    PortraitTrackMetadataBlock(
                                        title = displayTitle,
                                        artist = displayArtist,
                                        album = album,
                                        showLoadingPlaceholder = showMetadataLoadingPlaceholder,
                                        filename = displayFilename,
                                        filenameDisplayMode = filenameDisplayMode,
                                        decoderName = decoderName,
                                        filenameOnlyWhenTitleMissing = filenameOnlyWhenTitleMissing,
                                        currentSubtuneIndex = titleCurrentSubtuneIndex,
                                        subtuneCount = titleSubtuneCount,
                                        subtuneTitleClickable = subtuneTitleClickable,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        layoutScale = portraitLayoutScale,
                                        fullTechLine = trackTechnicalInfo.fullLine,
                                        fallbackTechLine = trackTechnicalInfo.fallbackLine,
                                        showFavorite = pathOrUrl != null,
                                        favoriteIndicator = {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .focusProperties {
                                                        down = transportAnchorFocusRequester
                                                    }
                                                    .clip(CircleShape)
                                                    .playerFocusHalo(enabled = pathOrUrl != null, shape = CircleShape)
                                                    .focusable(enabled = pathOrUrl != null)
                                                    .clickable(
                                                        enabled = pathOrUrl != null,
                                                        onClick = onToggleFavoriteTrack
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        id = if (isTrackFavorited) {
                                                            R.drawable.ic_star_filled
                                                        } else {
                                                            R.drawable.ic_star_outline
                                                        }
                                                    ),
                                                    contentDescription = if (isTrackFavorited) {
                                                        "Remove from favorites"
                                                    } else {
                                                        "Add to favorites"
                                                    },
                                                    tint = if (isTrackFavorited) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        TimelineSection(
                                            sliderPosition = if (isSeeking) sliderPosition else positionSeconds,
                                            elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                            durationSeconds = durationSeconds,
                                            showRemainingTime = showRemainingTime,
                                            canSeek = canSeek,
                                            hasReliableDuration = hasReliableDuration,
                                            seekInProgress = seekInProgress,
                                            focusRequester = primaryContentFocusRequester,
                                            upFocusRequester = topArrowFocusRequester,
                                            layoutScale = portraitTimelineScale,
                                            onToggleDurationDisplayMode = {
                                                showRemainingTime = !showRemainingTime
                                            },
                                            onSeekInteractionChanged = { isTimelineTouchActive = it },
                                            onSliderValueChange = { value ->
                                                isSeeking = true
                                                val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                                sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                                            },
                                            onSliderValueChangeFinished = {
                                                isSeeking = false
                                                if (canSeek && durationSeconds > 0.0) {
                                                    onSeek(sliderPosition)
                                                }
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(lerpDp(12.dp, 16.dp, portraitSectionSpacingScale)))
                                    TransportControls(
                                        hasTrack = hasTrack,
                                        isPlaying = isPlaying,
                                        canResumeStoppedTrack = canResumeStoppedTrack,
                                        repeatMode = repeatMode,
                                        playbackStartInProgress = playbackStartInProgress,
                                        remoteLoadUiState = remoteLoadUiState,
                                        seekInProgress = seekInProgress,
                                        positionSeconds = positionSeconds,
                                        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
                                        onRestartCurrentSelection = { onSeek(0.0) },
                                        canPreviousTrack = canPreviousTrack,
                                        canNextTrack = canNextTrack,
                                        canCycleRepeatMode = canCycleRepeatMode,
                                        onPlayPause = {
                                            if (isPlaying) {
                                                onPause()
                                            } else {
                                                onPlay()
                                            }
                                        },
                                        onPreviousTrack = onPreviousTrack,
                                        onForcePreviousTrack = onForcePreviousTrack,
                                        onNextTrack = onNextTrack,
                                        onPreviousSubtune = onPreviousSubtune,
                                        onNextSubtune = onNextSubtune,
                                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                                        currentSubtuneIndex = currentSubtuneIndex,
                                        subtuneCount = subtuneCount,
                                        canPreviousSubtune = canPreviousSubtune,
                                        canNextSubtune = canNextSubtune,
                                        canOpenSubtuneSelector = canOpenSubtuneSelector,
                                        onStopAndClear = onStopAndClear,
                                        onCycleRepeatMode = onCycleRepeatMode,
                                        compactPortraitMode = shortPortraitLayout,
                                        layoutScale = portraitTransportScale,
                                        transportAnchorFocusRequester = transportAnchorFocusRequester,
                                        actionStripFirstFocusRequester = actionStripFirstFocusRequester
                                    )
                            }
                        }

                    }
                }
            }
        }
    }
    }
    }
    if (showTrackInfoDialog) {
        TrackInfoDetailsDialog(
            file = file,
            title = displayTitle,
            artist = displayArtist,
            decoderName = decoderName,
            isDialogVisible = showTrackInfoDialog,
            playbackSourceLabel = playbackSourceLabel,
            pathOrUrl = pathOrUrl,
            playlistTitle = playlistTitle,
            playlistFormatLabel = playlistFormatLabel,
            playlistTrackCount = playlistTrackCount,
            playlistPathOrUrl = playlistPathOrUrl,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            durationSeconds = durationSeconds,
            hasReliableDuration = hasReliableDuration,
            onDismiss = { showTrackInfoDialog = false }
        )
    }
    LaunchedEffect(showFullscreenAffordance, affordanceInteractionTick) {
        if (showFullscreenAffordance) {
            kotlinx.coroutines.delay(2500)
            showFullscreenAffordance = false
        }
    }
    BackHandler(enabled = isVisualizationFullscreen) {
        isVisualizationFullscreen = false
    }
    FullscreenVisualizationOverlay(
        isFullscreen = isVisualizationFullscreen,
        onExitFullscreen = { isVisualizationFullscreen = false },
        displayTitle = displayTitle,
        displayArtist = displayArtist,
        isPlaying = isPlaying,
        onPlay = onPlay,
        onPause = onPause,
        onPreviousTrack = onPreviousTrack,
        onNextTrack = onNextTrack,
        canPreviousTrack = canPreviousTrack,
        canNextTrack = canNextTrack,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        canSeek = canSeek && durationSeconds > 0.0,
        onSeek = onSeek,
        repeatMode = repeatMode,
        onStopAndClear = onStopAndClear,
        onCycleRepeatMode = onCycleRepeatMode,
        canCycleRepeatMode = canCycleRepeatMode,
        visualizationMode = visualizationMode,
        availableVisualizationModes = availableVisualizationModes,
        onCycleVisualizationMode = onCycleVisualizationMode,
        onSelectVisualizationMode = onSelectVisualizationMode,
        onVisualizerAction = onVisualizerAction,
        fullscreenModePref = fullscreenModePref,
        visualizationContent = {
            AlbumArtPlaceholder(
                file = file,
                isPlaying = isPlaying && !seekInProgress,
                decoderName = decoderName,
                sampleRateHz = sampleRateHz,
                artwork = artwork,
                artworkSwipePreviewState = artworkSwipePreviewState,
                placeholderIcon = noArtworkIcon,
                visualizationModeBadgeText = visualizationModeBadgeText,
                showVisualizationModeBadge = false,
                visualizationMode = visualizationMode,
                visualizationPerformanceMode = visualizationPerformanceMode,
                visualizationShowDebugInfo = visualizationShowDebugInfo,
                visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                barCount = visualizationBarCount,
                barRoundnessDp = visualizationBarRoundnessDp,
                barOverlayArtwork = visualizationBarOverlayArtwork,
                barUseThemeColor = visualizationBarUseThemeColor,
                barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                oscStereo = visualizationOscStereo,
                oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                vuAnchor = visualizationVuAnchor,
                vuUseThemeColor = visualizationVuUseThemeColor,
                vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                channelScopePrefs = effectiveChannelScopePrefs,
                artworkCornerRadiusDp = 0,
                enableSwipe = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    )

    if (showVisualizationPickerDialog) {
        VisualizationModePickerDialog(
            availableModes = availableVisualizationModes,
            selectedMode = visualizationMode,
            onSelectMode = onSelectVisualizationMode,
            onOpenSelectedVisualizationSettings = onOpenSelectedVisualizationSettings,
            onOpenVisualizationSettings = onOpenVisualizationSettings,
            onOpenOptions = {
                showVisualizationPickerDialog = false
                showVisualizationOptionsSheet = true
            },
            onDismiss = { showVisualizationPickerDialog = false }
        )
    }
    if (showVisualizationOptionsSheet) {
        var savedProjectMPreset by remember {
            mutableStateOf(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET, null))
        }
        VisualizationOptionsSheet(
            mode = visualizationMode,
            globalInputGain = channelScopePrefs.gainPercent,
            onGlobalInputGainChange = { newGain ->
                prefs.edit().putInt("visualization_channel_scope_gain_percent", newGain).apply()
            },
            trackInputGain = trackInputGain,
            onTrackInputGainChange = { newTrackGain ->
                trackInputGain = newTrackGain
                if (currentTrackKey.isNotEmpty()) {
                    if (newTrackGain == 100) {
                        trackGainPrefs.edit().remove(currentTrackKey).apply()
                    } else {
                        trackGainPrefs.edit().putInt(currentTrackKey, newTrackGain).apply()
                    }
                }
            },
            showChannelLabels = channelScopePrefs.textEnabled,
            onShowChannelLabelsChange = { enabled ->
                prefs.edit().putBoolean("visualization_channel_scope_text_enabled", enabled).apply()
            },
            savedProjectMPreset = savedProjectMPreset,
            onProjectMPresetSelected = { presetKey ->
                prefs.edit()
                    .putString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET, presetKey)
                    .apply()
                savedProjectMPreset = presetKey
            },
            presetSetLabels = remember(context, prefs) {
                ProjectMPresetSets.enabledSets(context, prefs)
                    .associate { it.id to it.label }
            },
            onResetDefaults = {
                when (visualizationMode) {
                    VisualizationMode.ChannelScope -> {
                        prefs.edit()
                            .putInt(
                                "visualization_channel_scope_gain_percent",
                                com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.gainPercent
                            )
                            .putBoolean(
                                "visualization_channel_scope_text_enabled",
                                com.flopster101.siliconplayer.AppDefaults.Visualization.ChannelScope.textEnabled
                            )
                            .apply()
                        trackGainPrefs.edit().clear().apply()
                        trackInputGain = 100
                    }
                    VisualizationMode.ProjectM -> {
                        prefs.edit().remove(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET).apply()
                        savedProjectMPreset = null
                    }
                    else -> Unit
                }
            },
            onDismiss = {
                showVisualizationOptionsSheet = false
                showVisualizationPickerDialog = true
            }
        )
    }
    if (showChannelControlDialog) {
        ChannelControlDialog(
            onDismiss = { showChannelControlDialog = false }
        )
    }
    if (showAudioOutputDetailsDialog) {
        val outputRouteInfo = rememberAudioOutputRouteInfo()
        val effectiveCaps = if (playbackCapabilitiesFlags != 0) {
            playbackCapabilitiesFlags
        } else {
            NativeBridge.getCoreCapabilities(decoderName ?: "")
        }
        AudioOutputDetailsDialog(
            routeInfo = outputRouteInfo,
            displayFile = file,
            sourceId = pathOrUrl,
            requestUrl = pathOrUrl,
            decoderName = decoderName,
            trackSampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitDepthLabel = bitDepthLabel,
            isPlaying = isPlaying,
            playbackCapabilitiesFlags = effectiveCaps,
            bitPerfectEnabled = bitPerfectUsbAudio,
            onBitPerfectToggled = onBitPerfectUsbAudioChanged,
            onRestartTrack = {
                onSeek(0.0)
            },
            onOpenAudioSettings = {
                showAudioOutputDetailsDialog = false
                openAudioOutputSwitcher(context)
            },
            onDismiss = { showAudioOutputDetailsDialog = false }
        )
    }
}

private fun handlePlayerGlobalKeyDown(
    keyEvent: androidx.compose.ui.input.key.KeyEvent,
    hasTrack: Boolean,
    canResumeStoppedTrack: Boolean,
    isPlaying: Boolean,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canSeek: Boolean,
    durationSeconds: Double,
    canCycleRepeatMode: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onSeek: (Double) -> Unit,
    onCycleRepeatMode: () -> Unit,
    onStopAndClear: () -> Unit
): Boolean {
    return when (keyEvent.key) {
        Key.Spacebar -> {
            if (hasTrack || canResumeStoppedTrack) {
                if (isPlaying) onPause() else onPlay()
                true
            } else false
        }
        Key.DirectionLeft -> {
            if (keyEvent.isCtrlPressed && canPreviousSubtune) {
                onPreviousSubtune()
                true
            } else false
        }
        Key.DirectionRight -> {
            if (keyEvent.isCtrlPressed && canNextSubtune) {
                onNextSubtune()
                true
            } else false
        }
        Key.PageUp -> {
            if (hasTrack && canPreviousTrack) {
                onPreviousTrack()
                true
            } else false
        }
        Key.PageDown -> {
            if (hasTrack && canNextTrack) {
                onNextTrack()
                true
            } else false
        }
        Key.MoveHome -> {
            if (canSeek && durationSeconds > 0.0) {
                onSeek(0.0)
                true
            } else false
        }
        Key.R -> {
            if (canCycleRepeatMode) {
                onCycleRepeatMode()
                true
            } else false
        }
        Key.Backspace -> {
            onStopAndClear()
            true
        }
        else -> false
    }
}

@Composable
private fun PlayerTopBar(
    isLandscape: Boolean,
    isTabletLike: Boolean,
    compactPortraitHeader: Boolean = false,
    onBack: () -> Unit,
    enableCollapseGesture: Boolean,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    canOpenCoreSettings: Boolean,
    onOpenCoreSettings: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    onOpenAudioEffects: () -> Unit,
    onOpenChannelControls: () -> Unit,
    showAudioOutputRouteChip: Boolean = true,
    canOpenPlaylistSelector: Boolean = true,
    onOpenPlaylistSelector: () -> Unit = {},
    onOpenVisualizationPicker: () -> Unit = {},
    onOpenAudioOutputDetails: () -> Unit = {}
) {
    val context = LocalContext.current
    val outputRouteInfo = rememberAudioOutputRouteInfo()
    var showMoreMenu by remember { mutableStateOf(false) }
    val routePillFocusRequester = remember { FocusRequester() }
    val playlistFocusRequester = remember { FocusRequester() }
    val moreOptionsFocusRequester = remember { FocusRequester() }

    val compactLandscapeHeader = isLandscape && !isTabletLike
    val headerHeight = when {
        compactLandscapeHeader -> 38.dp
        compactPortraitHeader -> 44.dp
        else -> 52.dp
    }
    val navButtonSize = when {
        compactLandscapeHeader -> 28.dp
        compactPortraitHeader -> 30.dp
        else -> 32.dp
    }
    val navIconSize = when {
        compactLandscapeHeader -> 22.dp
        compactPortraitHeader -> 22.dp
        else -> 24.dp
    }
    val horizontalInset = when {
        compactLandscapeHeader -> 7.dp
        compactPortraitHeader -> 7.dp
        else -> 9.dp
    }
    val topInset = when {
        compactLandscapeHeader -> 10.dp
        compactPortraitHeader -> 10.dp
        else -> 14.dp
    }
    val statusBarTopInset = 0.dp
    val totalHeaderHeight = statusBarTopInset + headerHeight
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(totalHeaderHeight),
        contentAlignment = Alignment.TopStart
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .padding(start = horizontalInset, top = statusBarTopInset + topInset)
                .size(navButtonSize)
                .focusProperties {
                    right = if (showAudioOutputRouteChip) routePillFocusRequester else moreOptionsFocusRequester
                    if (downFocusRequester != null) {
                        down = downFocusRequester
                    }
                }
                .clip(CircleShape)
                .playerFocusHighlight(
                    enabled = enableCollapseGesture,
                    shape = CircleShape,
                    activeAlpha = 0.14f
                )
                .clickable(
                    enabled = enableCollapseGesture,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
                .focusable(enabled = enableCollapseGesture),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize player",
                modifier = Modifier.size(navIconSize)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = horizontalInset, top = statusBarTopInset + topInset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showAudioOutputRouteChip) {
                AudioOutputRoutePill(
                    routeInfo = outputRouteInfo,
                    onClick = onOpenAudioOutputDetails,
                    compactLayout = compactLandscapeHeader || compactPortraitHeader,
                    maxPillWidth = when {
                        compactLandscapeHeader -> 160.dp
                        compactPortraitHeader -> 180.dp
                        else -> 210.dp
                    },
                    focusRequester = routePillFocusRequester,
                    leftFocusRequester = focusRequester,
                    rightFocusRequester = playlistFocusRequester,
                    downFocusRequester = downFocusRequester
                )
            }

            Box(
                modifier = Modifier
                    .size(navButtonSize)
                    .focusRequester(playlistFocusRequester)
                    .focusProperties {
                        if (showAudioOutputRouteChip) {
                            left = routePillFocusRequester
                        } else if (focusRequester != null) {
                            left = focusRequester
                        }
                        right = moreOptionsFocusRequester
                        if (downFocusRequester != null) {
                            down = downFocusRequester
                        }
                    }
                    .clip(CircleShape)
                    .playerFocusHighlight(
                        shape = CircleShape,
                        activeAlpha = 0.14f
                    )
                    .clickable(
                        enabled = canOpenPlaylistSelector,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenPlaylistSelector
                    )
                    .focusable(enabled = canOpenPlaylistSelector),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Open current playlist",
                    modifier = Modifier.size(navIconSize),
                    tint = if (canOpenPlaylistSelector) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(navButtonSize)
                        .focusRequester(moreOptionsFocusRequester)
                        .focusProperties {
                            left = playlistFocusRequester
                            if (downFocusRequester != null) {
                                down = downFocusRequester
                            }
                        }
                        .clip(CircleShape)
                        .playerFocusHighlight(
                            shape = CircleShape,
                            activeAlpha = 0.14f
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showMoreMenu = true }
                        )
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(navIconSize)
                    )
                }

                MaterialTheme(
                    shapes = MaterialTheme.shapes.copy(
                        extraSmall = RoundedCornerShape(16.dp)
                    )
                ) {
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Audio effects",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            showMoreMenu = false
                            onOpenAudioEffects()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Channel controls",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_airwave),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            showMoreMenu = false
                            onOpenChannelControls()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Visualizations",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            showMoreMenu = false
                            onOpenVisualizationPicker()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Track and decoder info",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            showMoreMenu = false
                            onOpenTrackInfo()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Decoder settings",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_settings_applications),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        contentPadding = PaddingValues(start = 14.dp, end = 18.dp),
                        enabled = canOpenCoreSettings,
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        ),
                        onClick = {
                            showMoreMenu = false
                            onOpenCoreSettings()
                        }
                    )
                }
            }
        }
    }
}
}

internal enum class AudioOutputRouteType {
    Speaker,
    Headphones,
    Usb,
    Bluetooth
}

internal data class AudioOutputRouteInfo(
    val type: AudioOutputRouteType,
    val name: String
)

@Composable
private fun rememberAudioOutputRouteInfo(): AudioOutputRouteInfo {
    val context = LocalContext.current
    var routeInfo by remember { mutableStateOf(resolveCurrentAudioOutputRoute(context)) }

    DisposableEffect(context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    routeInfo = resolveCurrentAudioOutputRoute(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    routeInfo = resolveCurrentAudioOutputRoute(context)
                }
            }
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
            audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                routeInfo = resolveCurrentAudioOutputRoute(context)
            }
        }
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            true
        }.getOrElse {
            runCatching {
                context.registerReceiver(receiver, filter)
                true
            }.getOrDefault(false)
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                audioManager?.unregisterAudioDeviceCallback(callback)
            }
            if (registered) {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }
    }

    return routeInfo
}

internal fun resolveCurrentAudioOutputRoute(context: Context): AudioOutputRouteInfo {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        ?: return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // 1. Bluetooth devices (A2DP, BLE Headset, BLE Speaker, SCO, Hearing Aid)
        val bluetoothDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && device.type == AudioDeviceInfo.TYPE_HEARING_AID)
        }
        if (bluetoothDevice != null) {
            val name = bluetoothDevice.productName?.toString()?.trim()
            val displayName = if (!name.isNullOrBlank()) name else "Bluetooth"
            return AudioOutputRouteInfo(AudioOutputRouteType.Bluetooth, displayName)
        }

        // 2. USB Headset / USB Audio Device
        val usbDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
            device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usbDevice != null) {
            val rawName = usbDevice.productName?.toString()?.trim()
            val name = rawName
                ?.removePrefix("USB-Audio - ")
                ?.removePrefix("USB-Audio-")
                ?.removePrefix("USB Audio - ")
                ?.trim()
            val displayName = if (!name.isNullOrBlank()) name else "USB Audio"
            return AudioOutputRouteInfo(AudioOutputRouteType.Usb, displayName)
        }

        // 3. 3.5mm Wired Headset / Headphones / Line Out / HDMI
        val wiredDevice = devices.firstOrNull { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            device.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
            device.type == AudioDeviceInfo.TYPE_AUX_LINE ||
            device.type == AudioDeviceInfo.TYPE_HDMI ||
            device.type == AudioDeviceInfo.TYPE_HDMI_ARC
        }
        if (wiredDevice != null) {
            return AudioOutputRouteInfo(AudioOutputRouteType.Headphones, "Wired Headset")
        }

        // 4. Built-in Speaker
        return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")
    } else {
        @Suppress("DEPRECATION")
        return when {
            audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn ->
                AudioOutputRouteInfo(AudioOutputRouteType.Bluetooth, "Bluetooth")
            audioManager.isWiredHeadsetOn ->
                AudioOutputRouteInfo(AudioOutputRouteType.Headphones, "Wired Headset")
            else ->
                AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Speaker")
        }
    }
}

internal fun openAudioOutputSwitcher(context: Context) {
    runCatching {
        val panelIntent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
            putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(panelIntent)
    }.onFailure {
        runCatching {
            val btIntent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(btIntent)
        }.onFailure {
            runCatching {
                val soundIntent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(soundIntent)
            }
        }
    }
}

@Composable
private fun AudioOutputRoutePill(
    routeInfo: AudioOutputRouteInfo,
    onClick: () -> Unit,
    compactLayout: Boolean,
    maxPillWidth: Dp,
    focusRequester: FocusRequester? = null,
    leftFocusRequester: FocusRequester? = null,
    rightFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val pillHeight = if (compactLayout) 26.dp else 28.dp
    val iconSize = if (compactLayout) 14.dp else 15.dp

    Surface(
        onClick = onClick,
        modifier = modifier
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .height(pillHeight)
            .widthIn(max = maxPillWidth)
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
                if (rightFocusRequester != null) right = rightFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            }
            .playerFocusHighlight(
                shape = CircleShape,
                activeAlpha = 0.14f
            )
            .focusable(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = when (routeInfo.type) {
                    AudioOutputRouteType.Bluetooth -> Icons.Default.Bluetooth
                    AudioOutputRouteType.Headphones -> Icons.Default.Headphones
                    AudioOutputRouteType.Usb -> Icons.Default.Usb
                    AudioOutputRouteType.Speaker -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = routeInfo.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun toDisplayFilename(file: File): String {
    val stripped = stripRemoteCacheHashPrefix(file.name)
    return decodePercentEncodedForDisplay(stripped) ?: stripped
}

private fun normalizedScale(valueDp: Dp, compactDp: Dp, roomyDp: Dp): Float {
    if (roomyDp <= compactDp) return 1f
    return ((valueDp.value - compactDp.value) / (roomyDp.value - compactDp.value))
        .coerceIn(0f, 1f)
}

private fun playerTransportRowWidth(maxWidth: Dp, layoutScale: Float): Dp {
    val tabletWidthScale = normalizedScale(maxWidth, compactDp = 560.dp, roomyDp = 980.dp)
    val auxiliaryButtonSize = lerpDp(48.dp, 58.dp, tabletWidthScale)
    val sideButtonSize = lerpDp(54.dp, 68.dp, tabletWidthScale)
    val playButtonSize = lerpDp(82.dp, 108.dp, tabletWidthScale)
    val occupiedWidth = (auxiliaryButtonSize.value * 2f + sideButtonSize.value * 2f + playButtonSize.value).dp
    val minGap = 8.dp
    val maxGap = lerpDp(12.dp, 24.dp, tabletWidthScale)
    val minWidth = occupiedWidth + minGap * 4f
    val maxComfortWidth = occupiedWidth + maxGap * 4f
    val preferredWidth = maxWidth * lerpFloat(0.94f, 1.0f, layoutScale)
    return minOf(maxWidth, maxComfortWidth, preferredWidth.coerceAtLeast(minWidth))
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    return start + (end - start) * t
}

private fun lerpDp(start: Dp, end: Dp, fraction: Float): Dp {
    val t = fraction.coerceIn(0f, 1f)
    return (start.value + (end.value - start.value) * t).dp
}

private fun lerpSp(start: TextUnit, end: TextUnit, fraction: Float): TextUnit {
    val t = fraction.coerceIn(0f, 1f)
    return (start.value + (end.value - start.value) * t).sp
}

private fun scaledDp(value: Dp, factor: Float): Dp {
    return (value.value * factor).dp
}

private fun Modifier.playerFocusHalo(
    enabled: Boolean = true,
    shape: Shape = CircleShape
): Modifier = composed {
    val focusIndicatorsEnabled = LocalPlayerFocusIndicatorsEnabled.current
    var isFocused by remember { mutableStateOf(false) }
    val haloAlpha by animateFloatAsState(
        targetValue = if (enabled && focusIndicatorsEnabled && isFocused) 0.7f else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "playerFocusHaloAlpha"
    )
    this
        .onFocusChanged { isFocused = it.isFocused }
        .border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = haloAlpha),
            shape = shape
        )
}

private fun Modifier.playerFocusHighlight(
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    activeAlpha: Float = 0.22f
): Modifier = composed {
    val focusIndicatorsEnabled = LocalPlayerFocusIndicatorsEnabled.current
    var isFocused by remember { mutableStateOf(false) }
    val highlightAlpha by animateFloatAsState(
        targetValue = if (enabled && focusIndicatorsEnabled && isFocused) activeAlpha else 0f,
        animationSpec = tween(durationMillis = 140),
        label = "playerFocusHighlightAlpha"
    )
    this
        .onFocusChanged { isFocused = it.isFocused }
        .background(
            color = MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha),
            shape = shape
        )
}

@Composable
private fun TrackInfoDetailsDialog(
    file: File?,
    title: String,
    artist: String,
    decoderName: String?,
    isDialogVisible: Boolean,
    playbackSourceLabel: String?,
    pathOrUrl: String?,
    playlistTitle: String?,
    playlistFormatLabel: String?,
    playlistTrackCount: Int,
    playlistPathOrUrl: String?,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    durationSeconds: Double,
    hasReliableDuration: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val liveMetadata = rememberTrackInfoLiveMetadata(
        filePath = file?.absolutePath,
        decoderName = decoderName,
        isDialogVisible = isDialogVisible
    )
    val detailsScrollState = rememberScrollState()
    val detailsFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }
    val copyButtonFocusRequester = remember { FocusRequester() }
    var detailsViewportHeightPx by remember { mutableIntStateOf(0) }
    val detailsScrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = true,
        scrollState = detailsScrollState,
        label = "trackInfoDetailsScrollbarAlpha"
    )
    val fileSizeBytes = file?.length() ?: 0L
    val filename = file?.name ?: "No file loaded"
    val extension = file?.name?.let(::inferredPrimaryExtensionForName)?.uppercase() ?: "UNKNOWN"
    val decoderLabel = decoderName?.ifBlank { "Unknown" } ?: "Unknown"
    val bitrateLabel = if (liveMetadata.bitrate > 0L) {
        "${formatBitrate(liveMetadata.bitrate, liveMetadata.isVbr)} (${if (liveMetadata.isVbr) "VBR" else "CBR"})"
    } else {
        "Unavailable"
    }
    val audioBackendLabel = liveMetadata.audioBackendLabel.ifBlank { "(inactive)" }
    val lengthLabel = if (durationSeconds > 0.0) {
        if (hasReliableDuration) formatTime(durationSeconds) else "${formatTime(durationSeconds)}?"
    } else {
        "Unavailable"
    }
    val channelsLabel = if (channelCount > 0) "$channelCount channels" else "Unknown"
    val depthLabel = bitDepthLabel.ifBlank { "Unknown" }
    val playlistCountLabel = when {
        playlistTrackCount <= 0 -> null
        playlistTrackCount == 1 -> "1 track"
        else -> "$playlistTrackCount tracks"
    }
    val trackRateLabel = if (liveMetadata.hasNativeSampleRate) {
        formatSampleRateForDetails(sampleRateHz)
    } else {
        "N/A"
    }
    val sampleRateChain =
        "$trackRateLabel -> " +
            "${formatSampleRateForDetails(liveMetadata.renderRateHz)} -> " +
            formatSampleRateForDetails(liveMetadata.outputRateHz)
    val pathOrUrlLabel = pathOrUrl?.ifBlank { "Unavailable" } ?: "Unavailable"
    val isWatch = isWatchDevice()
    LaunchedEffect(isDialogVisible, isWatch) {
        if (isDialogVisible && !isWatch) {
            runCatching { detailsFocusRequester.requestFocus() }
        }
    }
    val copyAllText = buildString {
        fun row(label: String, value: String) {
            append(label).append(": ").append(value).append('\n')
        }

        row("Filename", filename)
        row("Title", title)
        row("Artist", artist)
        if (liveMetadata.composer.isNotBlank()) row("Composer", liveMetadata.composer)
        if (liveMetadata.genre.isNotBlank()) row("Genre", liveMetadata.genre)
        if (liveMetadata.album.isNotBlank()) row("Album", liveMetadata.album)
        if (liveMetadata.year.isNotBlank()) row("Year", liveMetadata.year)
        if (liveMetadata.date.isNotBlank()) row("Date", liveMetadata.date)
        if (liveMetadata.copyrightText.isNotBlank()) row("Copyright", liveMetadata.copyrightText)
        if (liveMetadata.comment.isNotBlank()) row("Comment", liveMetadata.comment)
        row("Format", extension)
        row("Decoder", decoderLabel)
        playbackSourceLabel?.takeIf { it.isNotBlank() }?.let { row("Playback source", it) }
        row("File size", if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable")
        row("Sample rate chain", sampleRateChain)
        row("Bitrate", bitrateLabel)
        row("Length", lengthLabel)
        row("Audio channels", channelsLabel)
        row("Bit depth", depthLabel)
        row("Audio backend", audioBackendLabel)
        row("Path / URL", pathOrUrlLabel)
        playlistTitle?.takeIf { it.isNotBlank() }?.let { row("Playlist", it) }
        playlistFormatLabel?.takeIf { it.isNotBlank() }?.let { row("Playlist format", it) }
        playlistCountLabel?.let { row("Playlist tracks", it) }
        playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let { row("Playlist path / URL", it) }
        appendCoreTrackInfoCopyRows(
            builder = this,
            decoderName = decoderName,
            sampleRateHz = sampleRateHz,
            metadata = liveMetadata
        )
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Track and decoder info",
            onDismissRequest = onDismiss
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrackInfoDetailsRow("Filename", filename)
                TrackInfoDetailsRow("Title", title)
                TrackInfoDetailsRow("Artist", artist)
                if (liveMetadata.composer.isNotBlank()) {
                    TrackInfoDetailsRow("Composer", liveMetadata.composer)
                }
                if (liveMetadata.genre.isNotBlank()) {
                    TrackInfoDetailsRow("Genre", liveMetadata.genre)
                }
                if (liveMetadata.album.isNotBlank()) {
                    TrackInfoDetailsRow("Album", liveMetadata.album)
                }
                if (liveMetadata.year.isNotBlank()) {
                    TrackInfoDetailsRow("Year", liveMetadata.year)
                }
                if (liveMetadata.date.isNotBlank()) {
                    TrackInfoDetailsRow("Date", liveMetadata.date)
                }
                if (liveMetadata.copyrightText.isNotBlank()) {
                    TrackInfoDetailsRow("Copyright", liveMetadata.copyrightText)
                }
                if (liveMetadata.comment.isNotBlank()) {
                    TrackInfoDetailsRow("Comment", liveMetadata.comment)
                }
                TrackInfoDetailsRow("Format", extension)
                TrackInfoDetailsRow("Decoder", decoderLabel)
                playbackSourceLabel?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playback source", it)
                }
                TrackInfoDetailsRow(
                    "File size",
                    if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                )
                TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                TrackInfoDetailsRow("Bitrate", bitrateLabel)
                TrackInfoDetailsRow("Length", lengthLabel)
                TrackInfoDetailsRow("Audio channels", channelsLabel)
                TrackInfoDetailsRow("Bit depth", depthLabel)
                TrackInfoDetailsRow("Audio backend", audioBackendLabel)
                TrackInfoDetailsRow("Path / URL", pathOrUrlLabel)
                playlistTitle?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist", it)
                }
                playlistFormatLabel?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist format", it)
                }
                playlistCountLabel?.let {
                    TrackInfoDetailsRow("Playlist tracks", it)
                }
                playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let {
                    TrackInfoDetailsRow("Playlist path / URL", it)
                }
                TrackInfoCoreSections(
                    decoderName = decoderName,
                    sampleRateHz = sampleRateHz,
                    metadata = liveMetadata
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(copyAllText.trim()))
                    Toast.makeText(context, "Copied track and decoder info", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Copy all")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text("Track and decoder info") },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChangedDeferred { detailsViewportHeightPx = it.height }
                            .dialogScrollableContentNavigation(
                                scrollState = detailsScrollState,
                                focusRequester = detailsFocusRequester,
                                viewportHeightPx = detailsViewportHeightPx,
                                actionFocusRequester = closeButtonFocusRequester
                            )
                            .padding(end = 10.dp)
                            .verticalScroll(detailsScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SelectionContainer {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TrackInfoDetailsRow("Filename", filename)
                                TrackInfoDetailsRow("Title", title)
                                TrackInfoDetailsRow("Artist", artist)
                                if (liveMetadata.composer.isNotBlank()) {
                                    TrackInfoDetailsRow("Composer", liveMetadata.composer)
                                }
                                if (liveMetadata.genre.isNotBlank()) {
                                    TrackInfoDetailsRow("Genre", liveMetadata.genre)
                                }
                                if (liveMetadata.album.isNotBlank()) {
                                    TrackInfoDetailsRow("Album", liveMetadata.album)
                                }
                                if (liveMetadata.year.isNotBlank()) {
                                    TrackInfoDetailsRow("Year", liveMetadata.year)
                                }
                                if (liveMetadata.date.isNotBlank()) {
                                    TrackInfoDetailsRow("Date", liveMetadata.date)
                                }
                                if (liveMetadata.copyrightText.isNotBlank()) {
                                    TrackInfoDetailsRow("Copyright", liveMetadata.copyrightText)
                                }
                                if (liveMetadata.comment.isNotBlank()) {
                                    TrackInfoDetailsRow("Comment", liveMetadata.comment)
                                }
                                TrackInfoDetailsRow("Format", extension)
                                TrackInfoDetailsRow("Decoder", decoderLabel)
                                playbackSourceLabel?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playback source", it)
                                }
                                TrackInfoDetailsRow(
                                    "File size",
                                    if (fileSizeBytes > 0L) formatFileSize(fileSizeBytes) else "Unavailable"
                                )
                                TrackInfoDetailsRow("Sample rate chain", sampleRateChain)
                                TrackInfoDetailsRow("Bitrate", bitrateLabel)
                                TrackInfoDetailsRow("Length", lengthLabel)
                                TrackInfoDetailsRow("Audio channels", channelsLabel)
                                TrackInfoDetailsRow("Bit depth", depthLabel)
                                TrackInfoDetailsRow("Audio backend", audioBackendLabel)
                                TrackInfoDetailsRow("Path / URL", pathOrUrlLabel)
                                playlistTitle?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist", it)
                                }
                                playlistFormatLabel?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist format", it)
                                }
                                playlistCountLabel?.let {
                                    TrackInfoDetailsRow("Playlist tracks", it)
                                }
                                playlistPathOrUrl?.takeIf { it.isNotBlank() }?.let {
                                    TrackInfoDetailsRow("Playlist path / URL", it)
                                }
                                TrackInfoCoreSections(
                                    decoderName = decoderName,
                                    sampleRateHz = sampleRateHz,
                                    metadata = liveMetadata
                                )
                            }
                        }
                    }
                    if (detailsScrollState.maxValue > 0 && detailsViewportHeightPx > 0) {
                        TrackInfoDetailsScrollbar(
                            scrollState = detailsScrollState,
                            viewportHeightPx = detailsViewportHeightPx,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(6.dp)
                                .graphicsLayer(alpha = detailsScrollbarAlpha)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier
                        .focusRequester(copyButtonFocusRequester)
                        .focusProperties {
                            up = detailsFocusRequester
                            left = closeButtonFocusRequester
                        },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(copyAllText.trim()))
                        Toast.makeText(context, "Copied track and decoder info", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Copy all")
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier
                        .focusRequester(closeButtonFocusRequester)
                        .focusProperties {
                            up = detailsFocusRequester
                            right = copyButtonFocusRequester
                        },
                    onClick = onDismiss
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun TrackInfoDetailsScrollbar(
    scrollState: androidx.compose.foundation.ScrollState,
    viewportHeightPx: Int,
    modifier: Modifier = Modifier
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0 || viewportHeightPx <= 0) return

    val viewport = viewportHeightPx.toFloat()
    val content = viewport + maxScroll.toFloat()
    val thumbFraction = (viewport / content).coerceIn(0f, 1f)
    val offsetFraction = if (maxScroll > 0) {
        scrollState.value.toFloat() / maxScroll.toFloat()
    } else {
        0f
    }
    val dragToFraction = rememberScrollStateScrollbarDragHandler(scrollState)

    VerticalScrollbarTrack(
        thumbFraction = thumbFraction,
        offsetFraction = offsetFraction,
        modifier = modifier,
        minThumbHeight = 24.dp,
        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
        thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.68f),
        onDragFractionChanged = dragToFraction
    )
}

@Composable
private fun TrackInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    chipScale: Float,
    tabletWidthScale: Float
) {
    val iconSize = lerpDp(11.dp, 16.5.dp, chipScale) + lerpDp(0.dp, 4.dp, tabletWidthScale)
    val iconSlot = lerpDp(12.dp, 16.5.dp, chipScale) + lerpDp(0.dp, 4.dp, tabletWidthScale)
    val sideInset = lerpDp(3.dp, 8.dp, chipScale) + lerpDp(0.dp, 3.dp, tabletWidthScale)
    val minHeight = lerpDp(22.dp, 36.dp, chipScale) + lerpDp(0.dp, 14.dp, tabletWidthScale)
    val iconTextGap = lerpDp(1.dp, 5.dp, chipScale) + lerpDp(0.dp, 2.5f.dp, tabletWidthScale)
    val textStartPadding = iconSlot + iconTextGap
    val textEndPadding = lerpDp(1.dp, 3.dp, chipScale) + lerpDp(0.dp, 2.dp, tabletWidthScale)
    val baseTextStyle = when {
        tabletWidthScale > 0.28f && chipScale > 0.58f && text.length <= 10 -> MaterialTheme.typography.labelLarge
        chipScale < 0.35f -> MaterialTheme.typography.labelSmall
        chipScale < 0.55f && text.length >= 10 -> MaterialTheme.typography.labelSmall
        else -> MaterialTheme.typography.labelMedium
    }
    val compactTextScale = lerpFloat(0.90f, 1f, (chipScale * 0.80f + tabletWidthScale * 0.20f).coerceIn(0f, 1f))
    val textStyle = baseTextStyle.copy(
        fontSize = if (baseTextStyle.fontSize != TextUnit.Unspecified) {
            (baseTextStyle.fontSize.value * compactTextScale).sp
        } else {
            baseTextStyle.fontSize
        },
        lineHeight = if (baseTextStyle.lineHeight != TextUnit.Unspecified) {
            (baseTextStyle.lineHeight.value * compactTextScale).sp
        } else {
            baseTextStyle.lineHeight
        }
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(percent = 50)
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = minHeight)
                .padding(horizontal = sideInset),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = text,
                style = textStyle,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                softWrap = false,
                modifier = Modifier.padding(start = textStartPadding, end = textEndPadding)
            )
        }
    }
}

@Composable
private fun PlayerMarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    color: Color = Color.Unspecified,
    expandToAvailableWidth: Boolean = true
) {
    val containerModifier = if (expandToAvailableWidth) {
        modifier.fillMaxWidth()
    } else {
        modifier
    }
    BoxWithConstraints(
        modifier = containerModifier
    ) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val measuredText = remember(text, style) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
        val marqueeTrailingGap = 18.dp
        val marqueeEdgeFade = 14.dp
        val marqueeTrailingGapPx = with(density) { marqueeTrailingGap.roundToPx() }
        val marqueeEdgeFadePx = with(density) { marqueeEdgeFade.toPx() }
        val overflowPx = (measuredText.size.width - maxWidthPx).coerceAtLeast(0)
        val sharedTimeMs = LocalPlayerMarqueeClockState.current.value
        val marqueeInstanceStartMs = remember(text, style, expandToAvailableWidth) {
            mutableLongStateOf(Long.MIN_VALUE)
        }
        SideEffect {
            if (marqueeInstanceStartMs.longValue == Long.MIN_VALUE) {
                marqueeInstanceStartMs.longValue = sharedTimeMs
            }
        }
        val instanceElapsedMs = if (marqueeInstanceStartMs.longValue == Long.MIN_VALUE) {
            0L
        } else {
            (sharedTimeMs - marqueeInstanceStartMs.longValue).coerceAtLeast(0L)
        }
        val startPauseMs = 1450
        val turnaroundPauseMs = 1050
        val resetPauseMs = 1850
        val fadeInMs = 180
        val fadeOutMs = 260
        val travelDistancePx = (overflowPx + marqueeTrailingGapPx).coerceAtLeast(0)
        val marqueeSpeedDpPerSecond = 56.dp
        val marqueeSpeedPxPerSecond = with(density) { marqueeSpeedDpPerSecond.toPx() }.coerceAtLeast(1f)
        val travelDurationMs = if (travelDistancePx > 0) {
            ((travelDistancePx / marqueeSpeedPxPerSecond) * 1000f).toInt().coerceAtLeast(1)
        } else {
            0
        }
        val forwardDurationMs = travelDurationMs
        val returnDurationMs = travelDurationMs
        val targetOffset = if (overflowPx > 0) -travelDistancePx.toFloat() else 0f
        val cycleDurationMs = startPauseMs + forwardDurationMs + turnaroundPauseMs + returnDurationMs + resetPauseMs
        val cyclePositionMs = if (overflowPx > 0 && cycleDurationMs > 0) {
            (instanceElapsedMs % cycleDurationMs.toLong()).toInt()
        } else {
            0
        }
        val marqueeOffsetPx = when {
            overflowPx <= 0 -> 0f
            cyclePositionMs < startPauseMs -> 0f
            cyclePositionMs < startPauseMs + forwardDurationMs -> {
                val forwardElapsedMs = cyclePositionMs - startPauseMs
                val forwardProgress = (forwardElapsedMs.toFloat() / forwardDurationMs).coerceIn(0f, 1f)
                targetOffset * forwardProgress
            }
            cyclePositionMs < startPauseMs + forwardDurationMs + turnaroundPauseMs -> targetOffset
            cyclePositionMs < startPauseMs + forwardDurationMs + turnaroundPauseMs + returnDurationMs -> {
                val returnElapsedMs = cyclePositionMs - startPauseMs - forwardDurationMs - turnaroundPauseMs
                val returnProgress = (returnElapsedMs.toFloat() / returnDurationMs).coerceIn(0f, 1f)
                targetOffset * (1f - returnProgress)
            }
            else -> 0f
        }
        val marqueeFadeAlpha = when {
            overflowPx <= 0 -> 0f
            cyclePositionMs < startPauseMs -> 0f
            cyclePositionMs < startPauseMs + forwardDurationMs -> {
                val forwardElapsedMs = cyclePositionMs - startPauseMs
                playerMarqueeMotionFadeAlpha(
                    elapsedMs = forwardElapsedMs,
                    segmentDurationMs = forwardDurationMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs
                )
            }
            cyclePositionMs < startPauseMs + forwardDurationMs + turnaroundPauseMs -> 0f
            cyclePositionMs < startPauseMs + forwardDurationMs + turnaroundPauseMs + returnDurationMs -> {
                val returnElapsedMs = cyclePositionMs - startPauseMs - forwardDurationMs - turnaroundPauseMs
                playerMarqueeMotionFadeAlpha(
                    elapsedMs = returnElapsedMs,
                    segmentDurationMs = returnDurationMs,
                    fadeInMs = fadeInMs,
                    fadeOutMs = fadeOutMs
                )
            }
            else -> 0f
        }

        Box(
            modifier = Modifier
                .then(
                    if (expandToAvailableWidth) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    }
                )
                .clipToBounds()
                .then(
                    if (overflowPx > 0 && marqueeFadeAlpha > 0f) {
                        Modifier
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                val fadeWidthPx = marqueeEdgeFadePx.coerceAtMost(size.width / 2f)
                                if (fadeWidthPx > 0f) {
                                    val opaqueMaskAlpha = 1f - marqueeFadeAlpha
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = opaqueMaskAlpha),
                                                Color.Black
                                            ),
                                            startX = 0f,
                                            endX = fadeWidthPx
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(fadeWidthPx, size.height),
                                        blendMode = BlendMode.DstIn
                                    )
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black,
                                                Color.Black.copy(alpha = opaqueMaskAlpha)
                                            ),
                                            startX = size.width - fadeWidthPx,
                                            endX = size.width
                                        ),
                                        topLeft = Offset(size.width - fadeWidthPx, 0f),
                                        size = Size(fadeWidthPx, size.height),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (overflowPx > 0) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = marqueeOffsetPx }
                ) {
                    Text(
                        text = text,
                        style = style,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Start
                    )
                    Spacer(Modifier.width(marqueeTrailingGap))
                }
            } else {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = if (expandToAvailableWidth) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

@Composable
private fun PlayerTitleWithOptionalSubtuneBadge(
    title: String,
    titleTextStyle: TextStyle,
    subtuneBadge: String?,
    subtuneTitleClickable: Boolean,
    subtuneTitleFlashAlpha: Float,
    onOpenSubtuneSelector: () -> Unit,
    textAlign: TextAlign,
    centered: Boolean,
    showFavoriteIndicator: Boolean = false,
    favoriteIndicator: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val badgeTextStyle = MaterialTheme.typography.bodySmall
    val badgeTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val badgeSpacing = 4.dp
    val badgeStartPadding = if (centered) 4.dp else 0.dp
    val badgeEndPadding = if (centered) 10.dp else 6.dp
    val badgeVerticalPadding = if (centered) 4.dp else 3.dp
    val density = LocalDensity.current
    val titleRowMinHeight = with(density) {
        (titleTextStyle.lineHeight.toDp() + (badgeVerticalPadding * 2)).coerceAtLeast(1.dp)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = titleRowMinHeight),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        val showFavorite = showFavoriteIndicator && !centered
        val favoriteWidth = if (showFavorite) 40.dp else 0.dp
        val contentMaxWidth = maxWidth - favoriteWidth
        if (subtuneBadge == null) {
            AnimatedContent(
                targetState = title,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 35)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 120))
                },
                label = if (centered) "centeredTrackTitleSwap" else "portraitTrackTitleSwap"
            ) { animatedTitle ->
                Box(modifier = Modifier.width(contentMaxWidth)) {
                    PlayerMarqueeText(
                        text = animatedTitle,
                        style = titleTextStyle,
                        textAlign = textAlign
                    )
                }
            }
        } else {
            val textMeasurer = rememberTextMeasurer()
            val badgeWidth = remember(subtuneBadge, badgeTextStyle, badgeStartPadding, badgeEndPadding) {
                with(density) {
                    textMeasurer.measure(
                        text = AnnotatedString(subtuneBadge),
                        style = badgeTextStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    ).size.width.toDp() + badgeStartPadding + badgeSpacing + badgeEndPadding
                }
            }
            val titleMaxWidth = (contentMaxWidth - badgeWidth).coerceAtLeast(48.dp)
            Row(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = subtuneTitleFlashAlpha)
                    )
                    .then(
                        if (subtuneTitleClickable) {
                            Modifier.clickable(onClick = onOpenSubtuneSelector)
                        } else {
                            Modifier
                        }
                    )
                    .padding(
                        start = badgeStartPadding,
                        end = badgeEndPadding,
                        top = badgeVerticalPadding,
                        bottom = badgeVerticalPadding
                    )
                    .animateContentSize(
                        animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 35)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    },
                    label = if (centered) "centeredSubtuneTrackTitleSwap" else "portraitSubtuneTrackTitleSwap"
                ) { animatedTitle ->
                    PlayerMarqueeText(
                        text = animatedTitle,
                        style = titleTextStyle,
                        textAlign = textAlign,
                        modifier = Modifier.width(titleMaxWidth),
                        expandToAvailableWidth = false
                    )
                }
                Spacer(modifier = Modifier.width(badgeSpacing))
                Text(
                    text = subtuneBadge,
                    style = badgeTextStyle,
                    color = badgeTextColor,
                    maxLines = 1
                )
            }
        }
        if (showFavorite) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                favoriteIndicator()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PortraitTrackMetadataBlock(
    title: String,
    artist: String,
    album: String,
    showLoadingPlaceholder: Boolean,
    filename: String,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode,
    decoderName: String?,
    filenameOnlyWhenTitleMissing: Boolean,
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    subtuneTitleClickable: Boolean = false,
    onOpenSubtuneSelector: () -> Unit = {},
    layoutScale: Float = 1f,
    titleScaleBoost: Float = 0f,
    supportingScaleBoost: Float = 0f,
    fullTechLine: String? = null,
    fallbackTechLine: String? = null,
    showFavorite: Boolean = false,
    favoriteIndicator: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val effectiveTitleScale = layoutScale.coerceIn(0f, 1f)
    val effectiveSupportingScale = layoutScale.coerceIn(0f, 1f)
    val titleFontBoost = titleScaleBoost.coerceAtLeast(0f).sp
    val titleLineBoost = (titleScaleBoost.coerceAtLeast(0f) * 1.25f).sp
    val supportingFontBoost = supportingScaleBoost.coerceAtLeast(0f).sp
    val supportingLineBoost = (supportingScaleBoost.coerceAtLeast(0f) * 1.33f).sp
    val titleTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = (lerpSp(20.sp, 28.sp, effectiveTitleScale).value + titleFontBoost.value).sp,
        lineHeight = (lerpSp(25.sp, 34.sp, effectiveTitleScale).value + titleLineBoost.value).sp,
        fontWeight = FontWeight.Bold
    )
    val artistTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = (lerpSp(12.5.sp, 15.sp, effectiveSupportingScale).value + supportingFontBoost.value).sp,
        lineHeight = (lerpSp(15.sp, 19.sp, effectiveSupportingScale).value + supportingLineBoost.value).sp,
        fontWeight = FontWeight.Normal
    )
    val filenameTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = (lerpSp(11.sp, 14.sp, effectiveSupportingScale).value + supportingFontBoost.value).sp,
        lineHeight = (lerpSp(14.sp, 18.sp, effectiveSupportingScale).value + supportingLineBoost.value).sp
    )
    val technicalSummaryTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = (lerpSp(10.5.sp, 13.sp, effectiveSupportingScale).value + supportingFontBoost.value).sp,
        lineHeight = (lerpSp(13.sp, 17.sp, effectiveSupportingScale).value + supportingLineBoost.value).sp
    )
    val albumTextStyle = artistTextStyle
    val shouldShowFilename = remember(filename, filenameDisplayMode, decoderName, title, filenameOnlyWhenTitleMissing) {
        if (filename.isBlank()) {
            false
        } else {
            when (filenameDisplayMode) {
                com.flopster101.siliconplayer.FilenameDisplayMode.Always -> {
                    if (filenameOnlyWhenTitleMissing) title.isBlank() else true
                }
                com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
                com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                    val decoder = decoderName?.lowercase() ?: ""
                    val isTracker = decoder.contains("openmpt") || decoder.contains("libopenmpt")
                    if (isTracker && filenameOnlyWhenTitleMissing) title.isBlank() else isTracker
                }
            }
        }
    }
    val subtuneBadge = remember(currentSubtuneIndex, subtuneCount) {
        if (subtuneCount > 1) {
            val shownIndex = (currentSubtuneIndex + 1).coerceIn(1, subtuneCount)
            "[$shownIndex/$subtuneCount]"
        } else {
            null
        }
    }
    val subtuneTitleFlashAlpha = remember { Animatable(0f) }
    var lastFlashedSubtuneSong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(filename, subtuneTitleClickable) {
        if (!subtuneTitleClickable) {
            lastFlashedSubtuneSong = null
            subtuneTitleFlashAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (filename == lastFlashedSubtuneSong) return@LaunchedEffect
        lastFlashedSubtuneSong = filename
        subtuneTitleFlashAlpha.snapTo(0f)
        repeat(2) {
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0.18f,
                animationSpec = tween(durationMillis = 280)
            )
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 340)
            )
            if (it == 0) delay(140)
        }
    }
    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
        ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        AnimatedContent(
            targetState = showLoadingPlaceholder,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 20)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 120))
            },
            label = "portraitTrackMetadataLoadingSwap"
        ) { loading ->
            if (loading) {
                val titlePlaceholderHeight = with(LocalDensity.current) {
                    titleTextStyle.lineHeight.toDp() * 0.68f
                }
                val supportingPlaceholderHeight = with(LocalDensity.current) {
                    artistTextStyle.lineHeight.toDp() * 0.62f
                }
                val detailPlaceholderHeight = with(LocalDensity.current) {
                    technicalSummaryTextStyle.lineHeight.toDp() * 0.6f
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top
                ) {
                    AnimatedMetadataPlaceholderLine(
                        widthFraction = 0.74f,
                        height = titlePlaceholderHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(lerpDp(6.dp, 10.dp, layoutScale)))
                    AnimatedMetadataPlaceholderLine(
                        widthFraction = 0.46f,
                        height = supportingPlaceholderHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(lerpDp(6.dp, 10.dp, layoutScale)))
                    AnimatedMetadataPlaceholderLine(
                        widthFraction = 0.58f,
                        height = detailPlaceholderHeight,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Top
                ) {
                    PlayerTitleWithOptionalSubtuneBadge(
                        title = title,
                        titleTextStyle = titleTextStyle,
                        subtuneBadge = subtuneBadge,
                        subtuneTitleClickable = subtuneTitleClickable,
                        subtuneTitleFlashAlpha = subtuneTitleFlashAlpha.value,
                        onOpenSubtuneSelector = onOpenSubtuneSelector,
                        textAlign = TextAlign.Start,
                        centered = false,
                        showFavoriteIndicator = showFavorite,
                        favoriteIndicator = favoriteIndicator
                    )
                    Spacer(modifier = Modifier.height(lerpDp(2.dp, 5.dp, layoutScale)))
                    val formattedArtist = remember(artist) { formatDisplayArtist(artist) }
                    AnimatedContent(
                        targetState = if (album.isNotBlank()) "$formattedArtist • $album" else formattedArtist,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 25)) togetherWith
                                fadeOut(animationSpec = tween(durationMillis = 110))
                        },
                        label = "portraitTrackArtistAlbumSwap"
                    ) { animatedArtistAlbum ->
                        Text(
                            text = animatedArtistAlbum,
                            style = artistTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AnimatedVisibility(
                        visible = shouldShowFilename,
                        enter = fadeIn(animationSpec = tween(durationMillis = 180)) + expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
                            animationSpec = tween(durationMillis = 220),
                            shrinkTowards = Alignment.Top
                        )
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(lerpDp(2.dp, 4.dp, layoutScale)))
                            AnimatedContent(
                                targetState = filename,
                                transitionSpec = {
                                    fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 20)) togetherWith
                                        fadeOut(animationSpec = tween(durationMillis = 110))
                                },
                                label = "portraitTrackFilenameSwap"
                            ) { animatedFilename ->
                                Text(
                                    text = animatedFilename,
                                    style = filenameTextStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !fullTechLine.isNullOrBlank(),
                        enter = fadeIn(animationSpec = tween(durationMillis = 180)) + expandVertically(
                            animationSpec = tween(durationMillis = 220),
                            expandFrom = Alignment.Top
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
                            animationSpec = tween(durationMillis = 220),
                            shrinkTowards = Alignment.Top
                        )
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(lerpDp(2.dp, 4.dp, layoutScale)))
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val textMeasurer = rememberTextMeasurer()
                                val density = LocalDensity.current
                                val maxWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
                                val fullText = fullTechLine.orEmpty()
                                val fallbackText = fallbackTechLine.orEmpty()

                                val textToDisplay = remember(fullText, fallbackText, technicalSummaryTextStyle, maxWidthPx) {
                                    if (fullText.isBlank()) {
                                        ""
                                    } else if (fallbackText.isBlank() || fullText == fallbackText) {
                                        fullText
                                    } else {
                                        val measured = textMeasurer.measure(
                                            text = AnnotatedString(fullText),
                                            style = technicalSummaryTextStyle,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Clip
                                        )
                                        if (measured.size.width > maxWidthPx) {
                                            fallbackText
                                        } else {
                                            fullText
                                        }
                                    }
                                }

                                AnimatedContent(
                                    targetState = textToDisplay,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 20)) togetherWith
                                            fadeOut(animationSpec = tween(durationMillis = 110))
                                    },
                                    label = "portraitTrackTechInfoSwap"
                                ) { animatedLine ->
                                    Text(
                                        text = animatedLine,
                                        style = technicalSummaryTextStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TrackMetadataBlock(
    title: String,
    artist: String,
    filename: String,
    filenameDisplayMode: com.flopster101.siliconplayer.FilenameDisplayMode,
    decoderName: String?,
    filenameOnlyWhenTitleMissing: Boolean,
    showFilename: Boolean = true,
    centerSupportingMetadata: Boolean = false,
    currentSubtuneIndex: Int = 0,
    subtuneCount: Int = 0,
    onOpenSubtuneSelector: () -> Unit = {},
    layoutScale: Float = 1f
) {
    val titleTextStyle = MaterialTheme.typography.headlineSmall.copy(
        fontSize = lerpSp(20.sp, 30.sp, layoutScale),
        lineHeight = lerpSp(26.sp, 36.sp, layoutScale),
        fontWeight = FontWeight.SemiBold
    )
    val artistTextStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = lerpSp(13.sp, 18.sp, layoutScale),
        lineHeight = lerpSp(17.sp, 24.sp, layoutScale),
        fontWeight = FontWeight.Medium
    )
    val titleArtistSpacer = lerpDp(3.dp, 8.dp, layoutScale)
    val artistFilenameSpacer = lerpDp(1.dp, 6.dp, layoutScale)
    val shouldShowFilename = remember(filename, filenameDisplayMode, decoderName, title, filenameOnlyWhenTitleMissing) {
        if (filename.isBlank()) {
            false
        } else {
            when (filenameDisplayMode) {
                com.flopster101.siliconplayer.FilenameDisplayMode.Always -> {
                    // If "only when title missing" is enabled, check if title is blank
                    if (filenameOnlyWhenTitleMissing) {
                        title.isBlank()
                    } else {
                        true
                    }
                }
                com.flopster101.siliconplayer.FilenameDisplayMode.Never -> false
                com.flopster101.siliconplayer.FilenameDisplayMode.TrackerOnly -> {
                    val decoder = decoderName?.lowercase() ?: ""
                    val isTracker = decoder.contains("openmpt") || decoder.contains("libopenmpt")
                    // If tracker format, apply the "only when title missing" logic
                    if (isTracker && filenameOnlyWhenTitleMissing) {
                        title.isBlank()
                    } else {
                        isTracker
                    }
                }
            }
        }
    }

    val subtuneBadge = remember(currentSubtuneIndex, subtuneCount) {
        if (subtuneCount > 1) {
            val shownIndex = (currentSubtuneIndex + 1).coerceIn(1, subtuneCount)
            "[$shownIndex/$subtuneCount]"
        } else {
            null
        }
    }
    val subtuneTitleClickable = subtuneCount > 1
    val subtuneTitleFlashAlpha = remember { Animatable(0f) }
    var lastFlashedSubtuneSong by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(filename, subtuneTitleClickable) {
        if (!subtuneTitleClickable) {
            lastFlashedSubtuneSong = null
            subtuneTitleFlashAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (filename == lastFlashedSubtuneSong) return@LaunchedEffect
        lastFlashedSubtuneSong = filename
        subtuneTitleFlashAlpha.snapTo(0f)
        repeat(2) {
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0.18f,
                animationSpec = tween(durationMillis = 280)
            )
            subtuneTitleFlashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 340)
            )
            if (it == 0) {
                delay(140)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
            ),
        horizontalAlignment = if (centerSupportingMetadata) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        PlayerTitleWithOptionalSubtuneBadge(
            title = title,
            titleTextStyle = titleTextStyle,
            subtuneBadge = subtuneBadge,
            subtuneTitleClickable = subtuneTitleClickable,
            subtuneTitleFlashAlpha = subtuneTitleFlashAlpha.value,
            onOpenSubtuneSelector = onOpenSubtuneSelector,
            textAlign = if (centerSupportingMetadata) TextAlign.Center else TextAlign.Start,
            centered = centerSupportingMetadata
        )
        Spacer(modifier = Modifier.height(titleArtistSpacer))
        val formattedArtist = remember(artist) { formatDisplayArtist(artist) }
        AnimatedContent(
            targetState = formattedArtist,
            transitionSpec = {
                fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30)) togetherWith
                    fadeOut(animationSpec = tween(durationMillis = 110))
            },
            label = "trackArtistSwap"
        ) { animatedArtist ->
            Text(
                text = animatedArtist,
                style = artistTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        AnimatedVisibility(
            visible = showFilename && shouldShowFilename,
            enter = fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 120))
        ) {
            Column {
                Spacer(modifier = Modifier.height(artistFilenameSpacer))
                val filenameTextStyle = MaterialTheme.typography.bodySmall
                val filenameColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.86f)
                Text(
                    text = filename,
                    style = filenameTextStyle,
                    color = filenameColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TransportControls(
    hasTrack: Boolean,
    isPlaying: Boolean,
    canResumeStoppedTrack: Boolean,
    repeatMode: RepeatMode,
    playbackStartInProgress: Boolean,
    remoteLoadUiState: RemoteLoadUiState?,
    seekInProgress: Boolean,
    positionSeconds: Double,
    previousRestartsAfterThreshold: Boolean,
    onRestartCurrentSelection: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canCycleRepeatMode: Boolean,
    onPlayPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    onOpenSubtuneSelector: () -> Unit,
    currentSubtuneIndex: Int,
    subtuneCount: Int,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canOpenSubtuneSelector: Boolean,
    onStopAndClear: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    maxClusterWidth: Dp? = null,
    edgeAlignedWidth: Dp? = null,
    separateEdgeButtons: Boolean = false,
    compactPortraitMode: Boolean = false,
    layoutScale: Float = 1f,
    transportAnchorFocusRequester: FocusRequester? = null,
    actionStripFirstFocusRequester: FocusRequester? = null,
    spacedByRow: Boolean = false
) {
    val remoteLoadActive = remoteLoadUiState != null
    val remotePreloadUiState = RemotePreloadUiStateHolder.current
    val showLoadingIndicator = playbackStartInProgress || remoteLoadActive
    val controlsBusy = seekInProgress || playbackStartInProgress
    val useSubtuneTransport = subtuneCount > 1
    val hasSubtuneBefore = useSubtuneTransport && currentSubtuneIndex > 0 && canPreviousSubtune
    val hasSubtuneAfter = useSubtuneTransport && currentSubtuneIndex < (subtuneCount - 1) && canNextSubtune
    val restartCurrentBeforePrevious = useSubtuneTransport && shouldRestartCurrentTrackOnPrevious(
        previousRestartsAfterThreshold = previousRestartsAfterThreshold,
        hasTrackLoaded = hasTrack,
        positionSeconds = positionSeconds
    )
    val previousTransportTapAction = when {
        restartCurrentBeforePrevious -> onRestartCurrentSelection
        hasSubtuneBefore -> onPreviousSubtune
        else -> onPreviousTrack
    }
    val nextTransportTapAction = if (hasSubtuneAfter) onNextSubtune else onNextTrack
    val previousTransportEnabled = if (useSubtuneTransport) hasTrack else hasTrack && canPreviousTrack
    val nextTransportEnabled = if (useSubtuneTransport) hasTrack else hasTrack && canNextTrack
    val canFocusPreviousTrack = previousTransportEnabled
    val canFocusRepeatMode = canCycleRepeatMode && !controlsBusy
    val canFocusPlayPause = (hasTrack || canResumeStoppedTrack) && !controlsBusy
    val canFocusStop = true
    val canFocusNextTrack = nextTransportEnabled

    val previousTrackFocusRequester = remember { FocusRequester() }
    val repeatModeFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = transportAnchorFocusRequester ?: remember { FocusRequester() }
    val stopFocusRequester = remember { FocusRequester() }
    val nextTrackFocusRequester = remember { FocusRequester() }
    var initialTransportFocusAssigned by remember { mutableStateOf(false) }
    fun firstAvailableRequester(vararg options: Pair<Boolean, FocusRequester>): FocusRequester? {
        return options.firstOrNull { it.first }?.second
    }
    LaunchedEffect(
        canFocusPlayPause,
        canFocusStop,
        canFocusPreviousTrack,
        canFocusRepeatMode,
        canFocusNextTrack
    ) {
        if (initialTransportFocusAssigned) return@LaunchedEffect
        delay(90)
        val requester = firstAvailableRequester(
            canFocusPlayPause to playPauseFocusRequester,
            canFocusStop to stopFocusRequester,
            canFocusPreviousTrack to previousTrackFocusRequester,
            canFocusRepeatMode to repeatModeFocusRequester,
            canFocusNextTrack to nextTrackFocusRequester
        )
        requester?.requestFocus()
        if (requester != null) {
            initialTransportFocusAssigned = true
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val transportClusterWidth = maxClusterWidth?.let { minOf(maxWidth, it) }
            ?: playerTransportRowWidth(maxWidth, layoutScale)
        val portraitTransportSizing = maxClusterWidth != null
        val compactPortraitTransport = portraitTransportSizing && compactPortraitMode
        val availableWidth = maxClusterWidth?.let { minOf(maxWidth, it) } ?: maxWidth
        val widthScale = normalizedScale(availableWidth, compactDp = 280.dp, roomyDp = 560.dp).coerceIn(0f, 1f)
        val playButtonSize = 82.dp
        val playIconSize = 39.dp
        val playIndicatorSize = 32.dp
        val sideButtonSize = 56.dp
        val sideTransportIconSize = 27.dp
        val auxiliaryButtonSize = 48.dp
        val auxiliaryIconSize = 24.dp
        val stopIconSize = 23.dp
        val subtuneButtonMax = lerpDp(58.dp, 80.dp, widthScale)
        val subtuneButtonSize = scaledDp(sideButtonSize, 1.03f).coerceIn(48.dp, subtuneButtonMax)
        val repeatIconSize = auxiliaryIconSize
        val effectiveRepeatIconSize = repeatIconSize
        val repeatBadgeCenterOffsetX = scaledDp(auxiliaryButtonSize, 0.22f)
        val repeatBadgeCenterOffsetY = scaledDp(auxiliaryButtonSize, -0.20f)
        val repeatBadgeHorizontalPadding = scaledDp(auxiliaryButtonSize, 0.08f).coerceIn(3.dp, 5.dp)
        val repeatBadgeVerticalPadding = scaledDp(auxiliaryButtonSize, 0.03f).coerceIn(1.dp, 2.dp)
        val repeatBadgeTextSize = (auxiliaryButtonSize.value * 0.18f).coerceIn(8f, 10f).sp
        val loadingSpacer = if (compactPortraitTransport) {
            scaledDp(sideButtonSize, 0.10f).coerceIn(3.dp, 8.dp)
        } else {
            scaledDp(sideButtonSize, 0.14f).coerceIn(4.dp, lerpDp(8.dp, 12.dp, widthScale))
        }
        val subtuneRowTopSpacer = (
            scaledDp(sideButtonSize, 0.1f) + lerpDp(0.dp, 8.dp, layoutScale)
        ).coerceIn(3.dp, lerpDp(14.dp, 24.dp, widthScale))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = if (spacedByRow) Modifier.wrapContentWidth() else Modifier.fillMaxWidth(),
                    horizontalArrangement = if (spacedByRow) Arrangement.spacedBy(18.dp) else Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                IconButton(
                    onClick = onStopAndClear,
                    modifier = Modifier
                        .focusRequester(stopFocusRequester)
                        .size(auxiliaryButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester
                            ) ?: stopFocusRequester
                            right = firstAvailableRequester(
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusStop to stopFocusRequester
                            ) ?: stopFocusRequester
                            down = firstAvailableRequester(
                                (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: stopFocusRequester)
                            ) ?: stopFocusRequester
                        }
                        .playerFocusHalo()
                        .focusable(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        modifier = Modifier.size(stopIconSize)
                    )
                }
                Box(
                    modifier = Modifier.size(sideButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalIconButton(
                        onClick = previousTransportTapAction,
                        enabled = previousTransportEnabled,
                        modifier = Modifier
                            .focusRequester(previousTrackFocusRequester)
                            .matchParentSize()
                            .tvKeyLongPress(
                                if (useSubtuneTransport && previousTransportEnabled) {
                                    onForcePreviousTrack
                                } else {
                                    null
                                }
                            )
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusStop to stopFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester
                                ) ?: previousTrackFocusRequester
                                down = firstAvailableRequester(
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: previousTrackFocusRequester)
                                ) ?: previousTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = previousTransportEnabled)
                            .focusable(enabled = previousTransportEnabled),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (useSubtuneTransport) {
                                Icons.Default.KeyboardDoubleArrowLeft
                            } else {
                                Icons.Default.SkipPrevious
                            },
                            contentDescription = if (useSubtuneTransport) {
                                "Previous subtune"
                            } else {
                                "Previous track"
                            },
                            modifier = Modifier.size(sideTransportIconSize)
                        )
                    }
                    if (useSubtuneTransport) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = previousTransportEnabled,
                                    onClick = previousTransportTapAction,
                                    onLongClick = onForcePreviousTrack
                                )
                        )
                    }
                }

                FilledIconButton(
                    onClick = onPlayPause,
                    enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy,
                    modifier = Modifier
                        .size(playButtonSize)
                        .focusRequester(playPauseFocusRequester)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            right = firstAvailableRequester(
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester
                            ) ?: playPauseFocusRequester
                            down = firstAvailableRequester(
                                (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: playPauseFocusRequester)
                            ) ?: playPauseFocusRequester
                        }
                        .playerFocusHalo(enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy)
                        .focusable(enabled = (hasTrack || canResumeStoppedTrack) && !controlsBusy),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (showLoadingIndicator) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(playIndicatorSize),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        AnimatedContent(
                            targetState = isPlaying,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "playerPlayPauseIcon"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(playIconSize)
                            )
                        }
                    }
                }


                Box(
                    modifier = Modifier.size(sideButtonSize),
                    contentAlignment = Alignment.Center
                ) {
                    FilledTonalIconButton(
                        onClick = nextTransportTapAction,
                        enabled = nextTransportEnabled,
                        modifier = Modifier
                            .focusRequester(nextTrackFocusRequester)
                            .matchParentSize()
                            .tvKeyLongPress(
                                if (useSubtuneTransport && nextTransportEnabled) {
                                    onNextTrack
                                } else {
                                    null
                                }
                            )
                            .focusProperties {
                                left = firstAvailableRequester(
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                right = firstAvailableRequester(
                                    canFocusRepeatMode to repeatModeFocusRequester,
                                    canFocusStop to stopFocusRequester,
                                    canFocusPreviousTrack to previousTrackFocusRequester,
                                    canFocusPlayPause to playPauseFocusRequester,
                                    canFocusNextTrack to nextTrackFocusRequester
                                ) ?: nextTrackFocusRequester
                                down = firstAvailableRequester(
                                    (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: nextTrackFocusRequester)
                                ) ?: nextTrackFocusRequester
                            }
                            .playerFocusHalo(enabled = nextTransportEnabled)
                            .focusable(enabled = nextTransportEnabled),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = if (useSubtuneTransport) {
                                Icons.Default.KeyboardDoubleArrowRight
                            } else {
                                Icons.Default.SkipNext
                            },
                            contentDescription = if (useSubtuneTransport) {
                                "Next subtune"
                            } else {
                                "Next track"
                            },
                            modifier = Modifier.size(sideTransportIconSize)
                        )
                    }
                    if (useSubtuneTransport) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = nextTransportEnabled,
                                    onClick = nextTransportTapAction,
                                    onLongClick = onNextTrack
                                )
                        )
                    }
                    if (remotePreloadUiState != null) {
                        val preloadPercent = remotePreloadUiState.percent
                            ?.takeIf { it in 0..100 }
                            ?.div(100f)
                        val preloadDeterminate =
                            remotePreloadUiState.phase != RemoteLoadPhase.Connecting &&
                                remotePreloadUiState.indeterminate != true &&
                                preloadPercent != null
                        if (preloadDeterminate) {
                            CircularProgressIndicator(
                                progress = { preloadPercent ?: 0f },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 16.dp)
                                    .size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 16.dp)
                                    .size(12.dp),
                                strokeWidth = 1.5.dp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCycleRepeatMode,
                    enabled = canCycleRepeatMode && !controlsBusy,
                    modifier = Modifier
                        .focusRequester(repeatModeFocusRequester)
                        .size(auxiliaryButtonSize)
                        .focusProperties {
                            left = firstAvailableRequester(
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusStop to stopFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester
                            ) ?: repeatModeFocusRequester
                            right = firstAvailableRequester(
                                canFocusStop to stopFocusRequester,
                                canFocusPreviousTrack to previousTrackFocusRequester,
                                canFocusPlayPause to playPauseFocusRequester,
                                canFocusNextTrack to nextTrackFocusRequester,
                                canFocusRepeatMode to repeatModeFocusRequester
                            ) ?: repeatModeFocusRequester
                            down = firstAvailableRequester(
                                (actionStripFirstFocusRequester != null) to (actionStripFirstFocusRequester ?: repeatModeFocusRequester)
                            ) ?: repeatModeFocusRequester
                        }
                        .playerFocusHalo(enabled = canCycleRepeatMode && !controlsBusy)
                        .focusable(enabled = canCycleRepeatMode && !controlsBusy),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (repeatMode != RepeatMode.None) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                ) {
                    val repeatModeIcon = Icons.Default.Loop
                    val modeBadgeText = when (repeatMode) {
                        RepeatMode.None -> ""
                        RepeatMode.Track -> "1"
                        RepeatMode.Subtune -> "ST"
                        RepeatMode.Playlist -> ""
                        RepeatMode.LoopPoint -> "LP"
                    }
                    val modeBadgeIcon = when (repeatMode) {
                        RepeatMode.Playlist -> Icons.AutoMirrored.Filled.List
                        else -> null
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = repeatModeIcon,
                            contentDescription = "Repeat mode: ${repeatMode.label}",
                            modifier = Modifier.size(effectiveRepeatIconSize)
                        )
                        if (!compactPortraitTransport && (modeBadgeText.isNotEmpty() || modeBadgeIcon != null)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = RoundedCornerShape(percent = 50),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(
                                        x = repeatBadgeCenterOffsetX,
                                        y = repeatBadgeCenterOffsetY
                                    )
                            ) {
                                if (modeBadgeIcon != null) {
                                    Icon(
                                        imageVector = modeBadgeIcon,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .padding(
                                                horizontal = repeatBadgeHorizontalPadding,
                                                vertical = repeatBadgeVerticalPadding
                                            )
                                            .size(repeatBadgeTextSize.value.dp + 2.dp)
                                    )
                                } else {
                                    Text(
                                        text = modeBadgeText,
                                        fontSize = repeatBadgeTextSize,
                                        lineHeight = repeatBadgeTextSize,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = repeatBadgeHorizontalPadding,
                                            vertical = repeatBadgeVerticalPadding
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }

            AnimatedVisibility(
                visible = showLoadingIndicator,
                enter = fadeIn(animationSpec = tween(durationMillis = 180)) + expandVertically(
                    animationSpec = tween(durationMillis = 220),
                    expandFrom = Alignment.Top
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)) + shrinkVertically(
                    animationSpec = tween(durationMillis = 220),
                    shrinkTowards = Alignment.Top
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(loadingSpacer))
                    Text(
                        text = remoteLoadProgressLabel(remoteLoadUiState),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

        }
    }
}

private fun remoteLoadProgressLabel(remoteLoadUiState: RemoteLoadUiState?): String {
    if (remoteLoadUiState == null) return "Loading track..."
    val phaseLabel = when (remoteLoadUiState.phase) {
        RemoteLoadPhase.Connecting -> "Connecting..."
        RemoteLoadPhase.Downloading -> "Downloading..."
        RemoteLoadPhase.Opening -> "Opening..."
    }
    if (remoteLoadUiState.phase == RemoteLoadPhase.Connecting) return phaseLabel
    val downloadedLabel = formatByteCount(remoteLoadUiState.downloadedBytes)
    val sizeLabel = remoteLoadUiState.totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> "$downloadedLabel / ${formatByteCount(total)}" }
        ?: downloadedLabel
    val percentLabel = remoteLoadUiState.percent
        ?.takeIf { it in 0..100 }
        ?.let { percent -> " • $percent%" }
        .orEmpty()
    return "$phaseLabel $sizeLabel$percentLabel"
}

private data class ChannelControlItem(
    val name: String,
    val channelIndex: Int,
    val muted: Boolean,
    val available: Boolean = true
)

private fun sortChannelControlsForDisplay(
    items: List<ChannelControlItem>
): List<ChannelControlItem> {
    val paulaRegex = Regex("^Paula ([LR])(\\d+)$")
    if (items.isEmpty() || items.any { !paulaRegex.matches(it.name) }) {
        return items
    }
    return items.sortedWith(
        compareBy<ChannelControlItem> { item ->
            val match = paulaRegex.matchEntire(item.name)
            match?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE
        }.thenBy { item ->
            val match = paulaRegex.matchEntire(item.name)
            val side = match?.groupValues?.get(1)
            if (side == "L") 0 else 1
        }
    )
}

@Composable
private fun ChannelControlDialog(
    onDismiss: () -> Unit
) {
    var masterChannels by remember {
        mutableStateOf(
            listOf(
                ChannelControlItem(name = "Left", channelIndex = 0, muted = false, available = true),
                ChannelControlItem(name = "Right", channelIndex = 1, muted = false, available = true)
            )
        )
    }
    var decoderChannels by remember { mutableStateOf(emptyList<ChannelControlItem>()) }

    fun loadMasterState() {
        masterChannels = masterChannels.map { channel ->
            channel.copy(muted = NativeBridge.getMasterChannelMute(channel.channelIndex))
        }
    }

    fun loadDecoderState() {
        val names = NativeBridge.getDecoderToggleChannelNames().toList()
        val availability = NativeBridge.getDecoderToggleChannelAvailability()
        val rawItems = names.mapIndexed { index, name ->
            ChannelControlItem(
                name = name,
                channelIndex = index,
                muted = NativeBridge.getDecoderToggleChannelMuted(index),
                available = availability.getOrElse(index) { true }
            )
        }
        decoderChannels = sortChannelControlsForDisplay(rawItems)
    }

    fun clearMasterSoloFlags() {
        masterChannels.forEach { channel ->
            NativeBridge.setMasterChannelSolo(channel.channelIndex, false)
        }
    }

    LaunchedEffect(Unit) {
        loadMasterState()
        while (true) {
            coroutineContext.ensureActive()
            loadDecoderState()
            delay(500)
        }
    }

    val toggleMasterMute: (ChannelControlItem) -> Unit = { item ->
        clearMasterSoloFlags()
        NativeBridge.setMasterChannelMute(item.channelIndex, !item.muted)
        masterChannels = masterChannels.map { existing ->
            if (existing.channelIndex == item.channelIndex) {
                existing.copy(muted = !existing.muted)
            } else {
                existing
            }
        }
    }

    val soloMasterChannel: (ChannelControlItem) -> Unit = { item ->
        clearMasterSoloFlags()
        val activeCount = masterChannels.count { !it.muted }
        val isOnlyActive = !item.muted && activeCount == 1
        if (isOnlyActive) {
            masterChannels.forEach { channel ->
                NativeBridge.setMasterChannelMute(channel.channelIndex, false)
            }
            masterChannels = masterChannels.map { it.copy(muted = false) }
        } else {
            masterChannels.forEach { channel ->
                NativeBridge.setMasterChannelMute(
                    channel.channelIndex,
                    channel.channelIndex != item.channelIndex
                )
            }
            masterChannels = masterChannels.map { channel ->
                channel.copy(muted = channel.channelIndex != item.channelIndex)
            }
        }
    }

    val toggleDecoderMute: (ChannelControlItem) -> Unit = { item ->
        if (item.available) {
            NativeBridge.setDecoderToggleChannelMuted(item.channelIndex, !item.muted)
            decoderChannels = decoderChannels.map { existing ->
                if (existing.channelIndex == item.channelIndex) {
                    existing.copy(muted = !existing.muted)
                } else {
                    existing
                }
            }
        }
    }

    val soloDecoderChannel: (ChannelControlItem) -> Unit = { item ->
        if (item.available) {
            val availableChannels = decoderChannels.filter { it.available }
            val activeCount = availableChannels.count { !it.muted }
            val isOnlyActive = !item.muted && activeCount == 1
            if (isOnlyActive) {
                availableChannels.forEach { channel ->
                    NativeBridge.setDecoderToggleChannelMuted(channel.channelIndex, false)
                }
                decoderChannels = decoderChannels.map { channel ->
                    if (channel.available) {
                        channel.copy(muted = false)
                    } else {
                        channel
                    }
                }
            } else {
                availableChannels.forEach { channel ->
                    NativeBridge.setDecoderToggleChannelMuted(
                        channel.channelIndex,
                        channel.channelIndex != item.channelIndex
                    )
                }
                decoderChannels = decoderChannels.map { channel ->
                    if (channel.available) {
                        channel.copy(muted = channel.channelIndex != item.channelIndex)
                    } else {
                        channel
                    }
                }
            }
        }
    }

    val unmuteAllChannels: () -> Unit = {
        clearMasterSoloFlags()
        masterChannels.forEach { channel ->
            NativeBridge.setMasterChannelMute(channel.channelIndex, false)
        }
        masterChannels = masterChannels.map { it.copy(muted = false) }
        NativeBridge.clearDecoderToggleChannelMutes()
        decoderChannels = decoderChannels.map { it.copy(muted = false) }
    }

    val contentText: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Master channels",
                style = MaterialTheme.typography.titleSmall
            )
            ChannelControlGrid(
                items = masterChannels,
                onToggleMute = toggleMasterMute,
                onSoloHold = soloMasterChannel
            )
            if (decoderChannels.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Core channels",
                    style = MaterialTheme.typography.titleSmall
                )
                ChannelControlGrid(
                    items = decoderChannels,
                    showScrollbar = true,
                    onToggleMute = toggleDecoderMute,
                    onSoloHold = soloDecoderChannel
                )
            }
            HorizontalDivider()
            Text(
                text = "Tap: mute/unmute. Long press: solo this channel (mutes others).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Unavailable channels are greyed out and update while this dialog is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Core-specific channel groups will be added per decoder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = unmuteAllChannels,
                modifier = Modifier.align(Alignment.Start),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Unmute all")
            }
        }
    }

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = "Channel controls",
            onDismissRequest = onDismiss
        ) {
            contentText()
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Close")
            }
        }
    } else {
        FloatingActionDialog(
            title = "Channel controls",
            onDismiss = onDismiss,
            confirmText = "Done",
            confirmIcon = Icons.Default.Check,
            onConfirm = onDismiss
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DialogSectionLabel(text = "Master channels")
                ChannelControlGrid(
                    items = masterChannels,
                    pickerStyle = true,
                    onToggleMute = toggleMasterMute,
                    onSoloHold = soloMasterChannel
                )
            }
            if (decoderChannels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DialogSectionLabel(text = "Core channels")
                    ChannelControlGrid(
                        items = decoderChannels,
                        showScrollbar = true,
                        pickerStyle = true,
                        onToggleMute = toggleDecoderMute,
                        onSoloHold = soloDecoderChannel
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DialogResetButton(text = "Unmute all", onClick = unmuteAllChannels)
                Text(
                    text = "Tap: mute/unmute. Long press: solo this channel (mutes others).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Unavailable channels are greyed out and update while this dialog is open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Core-specific channel groups will be added per decoder.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChannelControlGrid(
    items: List<ChannelControlItem>,
    showScrollbar: Boolean = false,
    pickerStyle: Boolean = false,
    onToggleMute: (ChannelControlItem) -> Unit,
    onSoloHold: (ChannelControlItem) -> Unit
) {
    val isPaulaSet = items.isNotEmpty() && items.all { it.name.startsWith("Paula ") }
    val columns = when {
        isPaulaSet && items.size == 4 -> 2
        items.size <= 2 -> items.size.coerceAtLeast(1)
        else -> 3
    }
    val rows = items.chunked(columns)
    val gridScrollState = rememberScrollState()
    var gridViewportHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val gridViewportHeightDp = with(density) { gridViewportHeightPx.toDp() }
    val scrollbarAlpha = rememberDialogScrollbarAlpha(
        enabled = showScrollbar,
        scrollState = gridScrollState,
        label = "channelGridScrollbarAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 176.dp)
            .onSizeChangedDeferred { gridViewportHeightPx = it.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(gridScrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { item ->
                        val isActive = item.available && !item.muted
                        val backgroundColor = when {
                            !pickerStyle && !item.available -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            !pickerStyle && item.muted -> MaterialTheme.colorScheme.surfaceVariant
                            !pickerStyle -> MaterialTheme.colorScheme.primary
                            isActive -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val contentColor = when {
                            !pickerStyle && !item.available -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            !pickerStyle && item.muted -> MaterialTheme.colorScheme.onSurfaceVariant
                            !pickerStyle -> MaterialTheme.colorScheme.onPrimary
                            isActive -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val tileShape = if (pickerStyle) RoundedCornerShape(16.dp) else MaterialTheme.shapes.large
                        val tileModifier = if (pickerStyle) {
                            Modifier
                                .alpha(if (item.available) 1f else 0.45f)
                                .border(
                                    width = if (isActive) 1.5.dp else 0.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = tileShape
                                )
                        } else {
                            Modifier
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(tileShape)
                                .then(tileModifier)
                                .tvKeyLongPress(if (item.available) { { onSoloHold(item) } } else null)
                                .combinedClickable(
                                    enabled = item.available,
                                    onClick = { onToggleMute(item) },
                                    onLongClick = { onSoloHold(item) }
                                ),
                            shape = tileShape,
                            color = backgroundColor
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompositionLocalProvider(
                                    LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(
                                        color = contentColor,
                                        fontWeight = when {
                                            pickerStyle && isActive -> FontWeight.Bold
                                            pickerStyle -> FontWeight.Medium
                                            else -> null
                                        }
                                    )
                                ) {
                                    AutoSizeChipLabel(item.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showScrollbar && gridViewportHeightPx > 0 && gridScrollState.maxValue > 0) {
            TrackInfoDetailsScrollbar(
                scrollState = gridScrollState,
                viewportHeightPx = gridViewportHeightPx,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(vertical = 2.dp)
                    .width(4.dp)
                    .height(gridViewportHeightDp)
                    .graphicsLayer(alpha = scrollbarAlpha)
            )
        }
    }
}

@Composable
private fun AutoSizeChipLabel(
    text: String
) {
    val maxSize = 14.sp
    val minSize = 9.sp
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseTextStyle = LocalTextStyle.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val safetyPaddingPx = with(density) { 2.dp.roundToPx() }
        val availableWidthPx = (maxWidthPx - safetyPaddingPx).coerceAtLeast(1)
        val resolvedFontSize = remember(text, availableWidthPx, baseTextStyle) {
            var low = minSize.value
            var high = maxSize.value
            var best = minSize.value
            repeat(7) {
                val mid = (low + high) * 0.5f
                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = baseTextStyle.copy(fontSize = mid.sp),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    constraints = Constraints(maxWidth = availableWidthPx)
                )
                if (layoutResult.hasVisualOverflow) {
                    high = mid - 0.1f
                } else {
                    best = mid
                    low = mid + 0.1f
                }
            }
            best.sp
        }
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            fontSize = resolvedFontSize,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TimelineSection(
    sliderPosition: Double,
    elapsedPositionSeconds: Double,
    durationSeconds: Double,
    showRemainingTime: Boolean,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    seekInProgress: Boolean,
    focusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    layoutScale: Float = 1f,
    onToggleDurationDisplayMode: () -> Unit,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onSliderValueChange: (Float) -> Unit,
    onSliderValueChangeFinished: () -> Unit
) {
    val sliderMax = durationSeconds.coerceAtLeast(0.0).toFloat()
    val normalizedValue = sliderPosition.toFloat().coerceIn(0f, sliderMax)
    val seekEnabled = canSeek && durationSeconds > 0.0
    val durationText = if (showRemainingTime) {
        when {
            durationSeconds <= 0.0 -> "-:--"
            elapsedPositionSeconds > durationSeconds -> "-:--"
            else -> {
                val remainingTimeText = formatTime(durationSeconds - elapsedPositionSeconds)
                if (hasReliableDuration) "-$remainingTimeText" else "-$remainingTimeText?"
            }
        }
    } else {
        if (durationSeconds > 0.0) {
            if (hasReliableDuration) formatTime(durationSeconds) else "${formatTime(durationSeconds)}?"
        } else {
            "-:--"
        }
    }
    val sliderHeight = lerpDp(36.dp, 48.dp, layoutScale)
    val timeTextStyle = (if (layoutScale < 0.35f) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }).copy(fontFeatureSettings = "tnum")
    Column(modifier = Modifier.fillMaxWidth()) {
        LineageStyleSeekBar(
            value = normalizedValue,
            maxValue = sliderMax,
            enabled = seekEnabled,
            seekInProgress = seekInProgress,
            layoutScale = layoutScale,
            onSeekInteractionChanged = onSeekInteractionChanged,
            onValueChange = onSliderValueChange,
            onValueChangeFinished = onSliderValueChangeFinished,
            modifier = Modifier
                .then(
                    if (focusRequester != null) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }
                )
                .focusProperties {
                    if (upFocusRequester != null) {
                        up = upFocusRequester
                    }
                }
                .fillMaxWidth()
                .height(sliderHeight)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (seekInProgress) {
                Text(
                    text = "Seeking...",
                    style = timeTextStyle,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = formatTime(elapsedPositionSeconds),
                    style = timeTextStyle
                )
            }
            Text(
                text = durationText,
                style = timeTextStyle,
                modifier = Modifier.clickable(onClick = onToggleDurationDisplayMode)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun LineageStyleSeekBar(
    value: Float,
    maxValue: Float,
    enabled: Boolean,
    seekInProgress: Boolean,
    layoutScale: Float = 1f,
    activeColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    inactiveColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    forceMonochromeWhite: Boolean = false,
    onSeekInteractionChanged: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveActiveColor = if (forceMonochromeWhite) androidx.compose.ui.graphics.Color.White else activeColor
    val effectiveInactiveColor = if (forceMonochromeWhite) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.30f) else inactiveColor
    val effectiveThumbColor = if (forceMonochromeWhite) androidx.compose.ui.graphics.Color.White else thumbColor
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val trackHeightPx = with(density) { lerpDp(8.dp, 10.dp, layoutScale).toPx() }
    val thumbWidthPx = with(density) { lerpDp(4.dp, 5.dp, layoutScale).toPx() }
    val thumbHeightPx = with(density) { lerpDp(28.dp, 36.dp, layoutScale).toPx() }
    val cutoutGapPx = with(density) { lerpDp(2.5.dp, 3.dp, layoutScale).toPx() }
    val thumbGrabRadiusPx = with(density) { lerpDp(18.dp, 22.dp, layoutScale).toPx() }
    val tapLaneHalfHeightPx = with(density) { lerpDp(18.dp, 24.dp, layoutScale).toPx() }
    var barWidthPx by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(0f) }
    var draggingThumb by remember { mutableStateOf(false) }
    var thumbPressed by remember { mutableStateOf(false) }
    var thumbHovered by remember { mutableStateOf(false) }
    val seekFlowPhase = if (seekInProgress) {
        val seekFlowTransition = rememberInfiniteTransition(label = "seekFlowTransition")
        seekFlowTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing)
            ),
            label = "seekFlowPhase"
        ).value
    } else {
        0f
    }

    fun xToValue(x: Float): Float {
        if (barWidthPx <= 0f || maxValue <= 0f) return 0f
        val trackStartX = thumbWidthPx / 2f
        val trackEndX = (barWidthPx - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
        if (trackWidth <= 0f) return 0f
        val clampedX = x.coerceIn(trackStartX, trackEndX)
        val ratio = ((clampedX - trackStartX) / trackWidth).coerceIn(0f, 1f)
        return ratio * maxValue
    }

    Canvas(
        modifier = modifier
            .playerFocusHalo(enabled = true, shape = RoundedCornerShape(10.dp))
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (
                    !enabled ||
                    maxValue <= 0f ||
                    keyEvent.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN
                ) {
                    return@onPreviewKeyEvent false
                }
                when (keyEvent.key) {
                    Key.DirectionLeft -> {
                        onValueChange((value - 5f).coerceIn(0f, maxValue))
                        onValueChangeFinished()
                        true
                    }
                    Key.DirectionRight -> {
                        onValueChange((value + 5f).coerceIn(0f, maxValue))
                        onValueChangeFinished()
                        true
                    }
                    else -> false
                }
            }
            .pointerInteropFilter { event ->
                if (!enabled || barWidthPx <= 0f || maxValue <= 0f) return@pointerInteropFilter false
                val centerY = barHeightPx / 2f
                val valueRatio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
                val trackStartX = thumbWidthPx / 2f
                val trackEndX = (barWidthPx - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
                val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
                val thumbCenterX = trackStartX + trackWidth * valueRatio
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        thumbHovered = false
                        val nearTrackLane = kotlin.math.abs(event.y - centerY) <= tapLaneHalfHeightPx
                        if (!nearTrackLane) return@pointerInteropFilter false
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        return@pointerInteropFilter if (nearThumb) {
                            draggingThumb = true
                            thumbPressed = true
                            onSeekInteractionChanged(true)
                            onValueChange(xToValue(event.x))
                            true
                        } else {
                            onValueChange(xToValue(event.x))
                            onValueChangeFinished()
                            true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        onValueChange(xToValue(event.x))
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (!draggingThumb) return@pointerInteropFilter false
                        draggingThumb = false
                        thumbPressed = false
                        onSeekInteractionChanged(false)
                        onValueChangeFinished()
                        true
                    }
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                        val nearThumb = kotlin.math.abs(event.x - thumbCenterX) <= thumbGrabRadiusPx
                        thumbHovered = nearThumb
                        false
                    }
                    MotionEvent.ACTION_HOVER_EXIT -> {
                        thumbHovered = false
                        false
                    }
                    else -> false
                }
            }
            .onSizeChangedDeferred { canvasSize ->
                barWidthPx = canvasSize.width.toFloat()
                barHeightPx = canvasSize.height.toFloat()
            }
    ) {
        val centerY = size.height / 2f
        val top = centerY - trackHeightPx / 2f
        val trackCorner = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)
        val activeColor = colorScheme.primary
        val inactiveColor = colorScheme.surfaceVariant
        val ratio = if (maxValue > 0f) (value / maxValue).coerceIn(0f, 1f) else 0f
        val trackStartX = thumbWidthPx / 2f
        val trackEndX = (size.width - thumbWidthPx / 2f).coerceAtLeast(trackStartX)
        val trackWidth = (trackEndX - trackStartX).coerceAtLeast(0f)
        val thumbX = (trackStartX + trackWidth * ratio).coerceIn(trackStartX, trackEndX)
        val thumbHalfWidth = thumbWidthPx / 2f
        val thumbLeft = (thumbX - thumbHalfWidth).coerceIn(0f, size.width - thumbWidthPx)
        val thumbRight = thumbLeft + thumbWidthPx

        // Active track (left of thumb) with cutout gap
        val activeStart = trackStartX
        val activeEnd = (thumbLeft - cutoutGapPx).coerceAtLeast(activeStart)
        val activeWidth = (activeEnd - activeStart).coerceAtLeast(0f)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = effectiveActiveColor,
                topLeft = Offset(activeStart, top),
                size = Size(activeWidth, trackHeightPx),
                cornerRadius = trackCorner
            )
        }

        // Inactive track (right of thumb) with cutout gap
        val inactiveStart = (thumbRight + cutoutGapPx).coerceAtMost(trackEndX)
        val inactiveEnd = trackEndX
        val inactiveWidth = (inactiveEnd - inactiveStart).coerceAtLeast(0f)
        if (inactiveWidth > 0f) {
            drawRoundRect(
                color = effectiveInactiveColor,
                topLeft = Offset(inactiveStart, top),
                size = Size(inactiveWidth, trackHeightPx),
                cornerRadius = trackCorner
            )
        }

        // Seeking animation (within active track)
        if (seekInProgress && activeWidth > 0f) {
            val bandWidth = activeWidth * 0.35f
            val travel = activeWidth + bandWidth
            val bandLeft = (seekFlowPhase * travel) - bandWidth
            val drawLeft = (activeStart + bandLeft).coerceAtLeast(activeStart)
            val drawRight = (activeStart + bandLeft + bandWidth).coerceAtMost(activeEnd)
            if (drawRight > drawLeft) {
                drawRoundRect(
                    color = effectiveActiveColor.copy(alpha = 0.36f),
                    topLeft = Offset(drawLeft, top),
                    size = Size(drawRight - drawLeft, trackHeightPx),
                    cornerRadius = trackCorner
                )
            }
        }

        if (thumbHovered || thumbPressed || draggingThumb) {
            drawCircle(
                color = effectiveThumbColor.copy(alpha = 0.22f),
                radius = with(density) { 14.dp.toPx() },
                center = Offset(thumbX, centerY)
            )
        }
        val thumbTop = centerY - thumbHeightPx / 2f
        drawRoundRect(
            color = effectiveThumbColor,
            topLeft = Offset(thumbLeft, thumbTop),
            size = Size(thumbWidthPx, thumbHeightPx),
            cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
        )
    }
}

internal fun formatTime(seconds: Double): String {
    val safeSeconds = seconds.coerceAtLeast(0.0).roundToInt()
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun formatBitrate(bitrateInBitsPerSecond: Long, isVBR: Boolean): String {
    val kbps = bitrateInBitsPerSecond / 1000.0
    val prefix = if (isVBR) "~" else ""

    return when {
        kbps >= 1000 -> String.format(java.util.Locale.US, "%s%.1f Mbps", prefix, kbps / 1000.0)
        else -> String.format(java.util.Locale.US, "%s%.0f kbps", prefix, kbps)
    }
}

private data class TrackTechnicalInfo(
    val fullLine: String,
    val fallbackLine: String
)

private fun buildTrackTechnicalInfo(
    formatLabel: String,
    extensionLabel: String,
    bitrateOrSize: String?,
    sampleRateHz: Int,
    channelCount: Int,
    bitDepthLabel: String,
    decoderName: String?,
    hasTrack: Boolean
): TrackTechnicalInfo {
    if (!hasTrack) {
        return TrackTechnicalInfo(fullLine = "", fallbackLine = "")
    }

    val bitrateLabel = bitrateOrSize?.ifBlank { "--" } ?: "--"
    val sampleRateLabel = if (sampleRateHz > 0) {
        formatSampleRateForDetails(sampleRateHz)
    } else {
        "-- kHz"
    }
    val showBitDepth = decoderName.equals(DecoderNames.FFMPEG, ignoreCase = true)
    val depthDisplay = bitDepthLabel.ifBlank { "Unknown" }
    val channelsAndDepth = when {
        channelCount > 0 && showBitDepth -> "${channelCount} ch / $depthDisplay"
        channelCount > 0 -> "${channelCount} ch"
        showBitDepth -> depthDisplay
        else -> "-- ch"
    }

    val specsList = listOf(
        bitrateLabel,
        sampleRateLabel,
        channelsAndDepth
    )

    val validFormat = formatLabel.takeIf { it.isNotBlank() && it != "EMPTY" && it != "UNKNOWN" }
    val validExt = extensionLabel.takeIf { it.isNotBlank() && it != "EMPTY" && it != "UNKNOWN" }
        ?: validFormat

    val fullSpecs = (listOfNotNull(validFormat ?: validExt) + specsList).joinToString(" • ")
    val fallbackSpecs = (listOfNotNull(validExt ?: validFormat) + specsList).joinToString(" • ")

    return TrackTechnicalInfo(
        fullLine = fullSpecs,
        fallbackLine = fallbackSpecs
    )
}

private fun formatSampleRateForDetails(rateHz: Int): String {
    if (rateHz <= 0) return "Unknown"
    return if (rateHz % 1000 == 0) {
        "${rateHz / 1000} kHz"
    } else {
        String.format(java.util.Locale.US, "%.1f kHz", rateHz / 1000.0)
    }
}

internal fun formatFileSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble().coerceAtLeast(0.0)
    var unitIndex = 0

    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }

    return if (unitIndex == 0) {
        String.format(java.util.Locale.US, "%.0f %s", size, units[unitIndex])
    } else {
        String.format(java.util.Locale.US, "%.1f %s", size, units[unitIndex])
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WearPlayerContent(
    file: java.io.File?,
    displayTitle: String,
    displayArtist: String,
    decoderName: String?,
    titleCurrentSubtuneIndex: Int,
    titleSubtuneCount: Int,
    subtuneTitleClickable: Boolean,
    onOpenSubtuneSelector: () -> Unit,
    trackTechnicalInfo: TrackTechnicalInfo,
    isPlaying: Boolean,
    repeatMode: RepeatMode,
    playbackStartInProgress: Boolean,
    seekInProgress: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    showRemainingTime: Boolean,
    canSeek: Boolean,
    hasReliableDuration: Boolean,
    onToggleDurationDisplayMode: () -> Unit,
    onSeek: (Double) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPreviousTrack: () -> Unit,
    onForcePreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onPreviousSubtune: () -> Unit,
    onNextSubtune: () -> Unit,
    canPreviousTrack: Boolean,
    canNextTrack: Boolean,
    canPreviousSubtune: Boolean,
    canNextSubtune: Boolean,
    canCycleRepeatMode: Boolean,
    onCycleRepeatMode: () -> Unit,
    onStopAndClear: () -> Unit,
    artwork: ImageBitmap?,
    artworkSwipePreviewState: ArtworkSwipePreviewState,
    noArtworkIcon: ImageVector,
    isVisualizationFullscreen: Boolean,
    visualizationMode: VisualizationMode,
    visualizationModeBadgeText: String,
    visualizationPrefsState: PlayerVisualizationPreferenceState,
    visualizationBarSmoothingPercent: Int,
    visualizationVuSmoothingPercent: Int,
    visualizationBarCount: Int,
    visualizationBarRoundnessDp: Int,
    visualizationBarOverlayArtwork: Boolean,
    visualizationBarUseThemeColor: Boolean,
    visualizationPerformanceMode: VisualizationPerformanceMode = com.flopster101.siliconplayer.AppDefaults.Visualization.performanceMode,
    visualizationOscStereo: Boolean,
    visualizationVuAnchor: VisualizationVuAnchor,
    visualizationVuUseThemeColor: Boolean,
    channelScopePrefs: ChannelScopePrefs,
    artworkCornerRadiusDp: Int,
    availableVisualizationModes: List<VisualizationMode> = emptyList(),
    onSelectVisualizationMode: (VisualizationMode) -> Unit = {},
    onCycleVisualizationMode: () -> Unit,
    isTrackFavorited: Boolean,
    onToggleFavoriteTrack: () -> Unit,
    canToggleFavoriteTrack: Boolean,
    onOpenAudioEffects: () -> Unit,
    onOpenChannelControls: () -> Unit,
    onBack: () -> Unit,
    onOpenTrackInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isRound = configuration.isRoundScreenCompat
    val pagerState = rememberPagerState(pageCount = { 2 })
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableDoubleStateOf(0.0) }
    var isTimelineTouchActive by remember { mutableStateOf(false) }
    var showMoreMenuDialog by remember { mutableStateOf(false) }

    val modes = remember(availableVisualizationModes) {
        if (availableVisualizationModes.isNotEmpty()) {
            availableVisualizationModes
        } else {
            VisualizationMode.entries.toList()
        }
    }
    val onSwipeNextVisualization = {
        val currentIndex = modes.indexOf(visualizationMode)
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % modes.size
        onSelectVisualizationMode(modes[nextIndex])
    }
    val onSwipePreviousVisualization = {
        val currentIndex = modes.indexOf(visualizationMode)
        val prevIndex = if (currentIndex <= 0) modes.size - 1 else currentIndex - 1
        onSelectVisualizationMode(modes[prevIndex])
    }

    val rawProgress = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    val seekProgressFraction = if (durationSeconds > 0.0) {
        (sliderPosition / durationSeconds).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        val pageWidth = maxWidth
        val isVerySmallScreen = pageHeight < 190.dp

        // Insets based on circular vs non-circular screen
        val topBezelInset = if (isRound) 16.dp else 4.dp
        val bottomBezelInset = if (isRound) 16.dp else 4.dp
        val topHeaderHorizontalInset = if (isRound) 46.dp else 10.dp
        val contentHorizontalInset = if (isRound) 24.dp else 8.dp
        val timelineHorizontalInset = if (isRound) 44.dp else 10.dp
        val actionsHorizontalInset = if (isRound) 38.dp else 10.dp

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page == 0) {
                // ==================== PAGE 0: NOW PLAYING DECK ====================
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Circular Edge Progress indicator along the outer bezel (Standard MD3, no separate thumb)
                    if (isRound) {
                        CircularEdgeProgressBar(
                            progress = rawProgress,
                            isSeeking = isSeeking,
                            seekProgress = seekProgressFraction,
                            canSeek = canSeek && durationSeconds > 0.0,
                            onSeekStarted = { isSeeking = true },
                            onSeekProgressChanged = { frac ->
                                isSeeking = true
                                sliderPosition = frac.toDouble() * durationSeconds.coerceAtLeast(0.0)
                            },
                            onSeekFinished = {
                                isSeeking = false
                                if (canSeek && durationSeconds > 0.0) {
                                    onSeek(sliderPosition)
                                }
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = topBezelInset,
                                bottom = bottomBezelInset,
                                start = 4.dp,
                                end = 4.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Top Header Row (Track Info button)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = topHeaderHorizontalInset),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = onOpenTrackInfo,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Track Info",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Track Title & Artist Block
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = contentHorizontalInset),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            PlayerMarqueeText(
                                text = displayTitle,
                                style = if (isVerySmallScreen) {
                                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                } else {
                                    MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                },
                                textAlign = TextAlign.Center
                            )
                            if (displayArtist.isNotBlank()) {
                                Spacer(modifier = Modifier.height(1.dp))
                                PlayerMarqueeText(
                                    text = displayArtist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Transport Row (Prev, Play/Pause, Next)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val prevNextSize = if (isVerySmallScreen) 36.dp else 40.dp
                            val playSize = if (isVerySmallScreen) 46.dp else 52.dp
                            val prevNextIconSize = if (isVerySmallScreen) 18.dp else 20.dp
                            val playIconSize = if (isVerySmallScreen) 24.dp else 28.dp

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FilledTonalIconButton(
                                    onClick = onPreviousTrack,
                                    enabled = canPreviousTrack,
                                    modifier = Modifier.size(prevNextSize),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous",
                                        modifier = Modifier.size(prevNextIconSize)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    onClick = {
                                        if (isPlaying) onPause() else onPlay()
                                    },
                                    modifier = Modifier.size(playSize),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (playbackStartInProgress) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(playIconSize)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledTonalIconButton(
                                    onClick = onNextTrack,
                                    enabled = canNextTrack,
                                    modifier = Modifier.size(prevNextSize),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next",
                                        modifier = Modifier.size(prevNextIconSize)
                                    )
                                }
                            }

                            // Optional subtune stepper if multi-subtune track
                            if (titleSubtuneCount > 1) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.wrapContentWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = onPreviousSubtune,
                                        enabled = canPreviousSubtune,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Prev Subtune",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = "${titleCurrentSubtuneIndex + 1}/$titleSubtuneCount",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .clickable(enabled = subtuneTitleClickable) { onOpenSubtuneSelector() }
                                            .padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = onNextSubtune,
                                        enabled = canNextSubtune,
                                        modifier = Modifier.size(22.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Subtune",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // On Circular Displays: Show clean centered timestamp
                        // On Rectangular Displays: Show standard TimelineSection
                        if (isRound) {
                            val currentPos = if (isSeeking) sliderPosition else positionSeconds
                            val timeString = if (seekInProgress || isSeeking) {
                                "Seeking ${formatTime(currentPos)}"
                            } else if (showRemainingTime) {
                                val rem = (durationSeconds - currentPos).coerceAtLeast(0.0)
                                val remStr = formatTime(rem)
                                if (hasReliableDuration) "-$remStr" else "-$remStr?"
                            } else {
                                val curStr = formatTime(currentPos)
                                val durStr = if (durationSeconds > 0.0) {
                                    if (hasReliableDuration) formatTime(durationSeconds) else "${formatTime(durationSeconds)}?"
                                } else {
                                    "-:--"
                                }
                                "$curStr / $durStr"
                            }

                            Text(
                                text = timeString,
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggleDurationDisplayMode() }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = timelineHorizontalInset)
                            ) {
                                TimelineSection(
                                    sliderPosition = if (isSeeking) sliderPosition else positionSeconds,
                                    elapsedPositionSeconds = if (isSeeking) sliderPosition else positionSeconds,
                                    durationSeconds = durationSeconds,
                                    showRemainingTime = showRemainingTime,
                                    canSeek = canSeek,
                                    hasReliableDuration = hasReliableDuration,
                                    seekInProgress = seekInProgress,
                                    layoutScale = 0f,
                                    onToggleDurationDisplayMode = onToggleDurationDisplayMode,
                                    onSeekInteractionChanged = { isTimelineTouchActive = it },
                                    onSliderValueChange = { value ->
                                        isSeeking = true
                                        val sliderMax = durationSeconds.coerceAtLeast(0.0)
                                        sliderPosition = value.toDouble().coerceIn(0.0, sliderMax)
                                    },
                                    onSliderValueChangeFinished = {
                                        isSeeking = false
                                        if (canSeek && durationSeconds > 0.0) {
                                            onSeek(sliderPosition)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // ==================== PAGE 1: ARTWORK & SECONDARY CONTROLS ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = topBezelInset,
                            bottom = bottomBezelInset,
                            start = 2.dp,
                            end = 2.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Album Art / Visualizer Thumbnail
                    val artSize = minOf(
                        pageWidth * if (isRound) 0.48f else 0.58f,
                        pageHeight * 0.42f,
                        104.dp
                    )

                    var visSwipeDeltaX by remember { mutableFloatStateOf(0f) }

                    Box(
                        modifier = Modifier
                            .size(artSize)
                            .clip(RoundedCornerShape(artworkCornerRadiusDp.dp))
                            .pointerInput(modes, visualizationMode) {
                                detectHorizontalDragGestures(
                                    onDragStart = { visSwipeDeltaX = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        visSwipeDeltaX += dragAmount
                                    },
                                    onDragEnd = {
                                        val threshold = 22.dp.toPx()
                                        if (visSwipeDeltaX < -threshold) {
                                            onSwipeNextVisualization()
                                        } else if (visSwipeDeltaX > threshold) {
                                            onSwipePreviousVisualization()
                                        }
                                    },
                                    onDragCancel = {
                                        visSwipeDeltaX = 0f
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (isPlaying) onPause() else onPlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AlbumArtPlaceholder(
                            file = file,
                            isPlaying = isPlaying && !seekInProgress,
                            decoderName = decoderName,
                            sampleRateHz = 0,
                            artwork = artwork,
                            artworkSwipePreviewState = ArtworkSwipePreviewState(),
                            placeholderIcon = noArtworkIcon,
                            visualizationModeBadgeText = visualizationModeBadgeText,
                            showVisualizationModeBadge = false,
                            visualizationMode = if (isVisualizationFullscreen) VisualizationMode.Off else visualizationMode,
                            visualizationPerformanceMode = visualizationPerformanceMode,
                            visualizationShowDebugInfo = false,
                            visualizationOscWindowMs = visualizationPrefsState.oscWindowMs,
                            visualizationOscTriggerModeNative = visualizationPrefsState.oscTriggerModeNative,
                            visualizationOscFpsMode = visualizationPrefsState.oscFpsMode,
                            visualizationBarFpsMode = visualizationPrefsState.barFpsMode,
                            visualizationVuFpsMode = visualizationPrefsState.vuFpsMode,
                            visualizationOscRenderBackend = visualizationPrefsState.oscRenderBackend,
                            visualizationBarSmoothingPercent = visualizationBarSmoothingPercent,
                            visualizationVuSmoothingPercent = visualizationVuSmoothingPercent,
                            barCount = visualizationBarCount,
                            barRoundnessDp = visualizationBarRoundnessDp,
                            barOverlayArtwork = visualizationBarOverlayArtwork,
                            barUseThemeColor = visualizationBarUseThemeColor,
                            barFrequencyGridEnabled = visualizationPrefsState.barFrequencyGridEnabled,
                            barRenderBackend = visualizationPrefsState.barRuntimeRenderBackend,
                            barColorModeNoArtwork = visualizationPrefsState.barColorModeNoArtwork,
                            barColorModeWithArtwork = visualizationPrefsState.barColorModeWithArtwork,
                            barCustomColorArgb = visualizationPrefsState.barCustomColorArgb,
                            oscStereo = visualizationOscStereo,
                            oscLineWidthDp = visualizationPrefsState.oscLineWidthDp,
                            oscGridWidthDp = visualizationPrefsState.oscGridWidthDp,
                            oscVerticalGridEnabled = visualizationPrefsState.oscVerticalGridEnabled,
                            oscCenterLineEnabled = visualizationPrefsState.oscCenterLineEnabled,
                            oscLineColorModeNoArtwork = visualizationPrefsState.oscLineColorModeNoArtwork,
                            oscGridColorModeNoArtwork = visualizationPrefsState.oscGridColorModeNoArtwork,
                            oscLineColorModeWithArtwork = visualizationPrefsState.oscLineColorModeWithArtwork,
                            oscGridColorModeWithArtwork = visualizationPrefsState.oscGridColorModeWithArtwork,
                            oscCustomLineColorArgb = visualizationPrefsState.oscCustomLineColorArgb,
                            oscCustomGridColorArgb = visualizationPrefsState.oscCustomGridColorArgb,
                            oscContrastBackdropEnabled = visualizationPrefsState.oscContrastBackdropEnabled,
                            vuAnchor = visualizationVuAnchor,
                            vuUseThemeColor = visualizationVuUseThemeColor,
                            vuRenderBackend = visualizationPrefsState.vuRuntimeRenderBackend,
                            vuColorModeNoArtwork = visualizationPrefsState.vuColorModeNoArtwork,
                            vuColorModeWithArtwork = visualizationPrefsState.vuColorModeWithArtwork,
                            vuCustomColorArgb = visualizationPrefsState.vuCustomColorArgb,
                            vuContrastBackdropEnabled = visualizationPrefsState.vuContrastBackdropEnabled,
                            barContrastBackdropEnabled = visualizationPrefsState.barContrastBackdropEnabled,
                            channelScopePrefs = channelScopePrefs,
                            artworkCornerRadiusDp = artworkCornerRadiusDp,
                            onSwipePreviousTrack = {},
                            onSwipeNextTrack = {},
                            modifier = Modifier.size(artSize)
                        )

                        PlayPauseOverlayBadge(
                            isPlaying = isPlaying && !playbackStartInProgress,
                            hasActiveTrack = file != null
                        )
                    }

                    // Technical Info Line (format, channels, samplerate)
                    if (trackTechnicalInfo.fallbackLine.isNotBlank()) {
                        Text(
                            text = trackTechnicalInfo.fallbackLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isRound) 32.dp else 8.dp)
                        )
                    }

                    // Secondary Actions Row (Stop, Repeat, Favorite, Effects)
                    val actionBtnSize = if (isVerySmallScreen) 30.dp else 34.dp
                    val actionIconSize = if (isVerySmallScreen) 16.dp else 18.dp

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = actionsHorizontalInset),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onStopAndClear,
                            modifier = Modifier.size(actionBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(actionIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onCycleRepeatMode,
                            enabled = canCycleRepeatMode,
                            modifier = Modifier.size(actionBtnSize)
                        ) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.Track, RepeatMode.Subtune, RepeatMode.LoopPoint -> Icons.Default.RepeatOne
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = "Repeat",
                                modifier = Modifier.size(actionIconSize),
                                tint = if (repeatMode != RepeatMode.None) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        IconButton(
                            onClick = onToggleFavoriteTrack,
                            enabled = canToggleFavoriteTrack,
                            modifier = Modifier.size(actionBtnSize)
                        ) {
                            Icon(
                                imageVector = if (isTrackFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                modifier = Modifier.size(actionIconSize),
                                tint = if (isTrackFavorited) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        IconButton(
                            onClick = { showMoreMenuDialog = true },
                            modifier = Modifier.size(actionBtnSize)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                modifier = Modifier.size(actionIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (showMoreMenuDialog) {
            WatchDialogContainer(
                title = "More options",
                onDismissRequest = { showMoreMenuDialog = false }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            showMoreMenuDialog = false
                            onOpenAudioEffects()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Audio Effects",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable {
                            showMoreMenuDialog = false
                            onOpenChannelControls()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_airwave),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Channel Controls",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayPauseOverlayBadge(
    isPlaying: Boolean,
    hasActiveTrack: Boolean,
    modifier: Modifier = Modifier
) {
    if (!hasActiveTrack) return

    var isVisible by remember(hasActiveTrack) { mutableStateOf(false) }
    var hasPlayedAtLeastOnce by remember(hasActiveTrack) { mutableStateOf(isPlaying) }
    var displayedIcon by remember { mutableStateOf(if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause) }
    val overlayAlpha = remember { Animatable(0f) }
    val overlayScale = remember { Animatable(1f) }

    LaunchedEffect(isPlaying, hasActiveTrack) {
        if (!hasActiveTrack) {
            isVisible = false
            overlayAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        if (isPlaying) {
            hasPlayedAtLeastOnce = true
            displayedIcon = Icons.Default.PlayArrow
            if (isVisible) {
                overlayScale.snapTo(1f)
                overlayAlpha.snapTo(1f)
                delay(300L)
                launch {
                    overlayScale.animateTo(1.25f, animationSpec = tween(380, easing = FastOutSlowInEasing))
                }
                overlayAlpha.animateTo(0f, animationSpec = tween(380, easing = LinearEasing))
                isVisible = false
            }
        } else {
            if (hasPlayedAtLeastOnce) {
                displayedIcon = Icons.Default.Pause
                isVisible = true
                overlayScale.snapTo(0.85f)
                overlayAlpha.snapTo(0f)
                launch {
                    overlayScale.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                }
                launch {
                    overlayAlpha.animateTo(1f, animationSpec = tween(200))
                }
            }
        }
    }

    if (isVisible || overlayAlpha.value > 0.01f) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    alpha = overlayAlpha.value
                    scaleX = overlayScale.value
                    scaleY = overlayScale.value
                }
                .wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            // Subtle drop shadow for contrast against light artwork/visualizers
            Icon(
                imageVector = displayedIcon,
                contentDescription = null,
                modifier = Modifier
                    .offset(x = 0.75.dp, y = 1.dp)
                    .size(24.dp),
                tint = Color.Black.copy(alpha = 0.50f)
            )
            // Main clean white icon
            Icon(
                imageVector = displayedIcon,
                contentDescription = if (displayedIcon == Icons.Default.Pause) "Paused" else "Playing",
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun CircularEdgeProgressBar(
    progress: Float,
    isSeeking: Boolean,
    seekProgress: Float,
    canSeek: Boolean,
    onSeekStarted: () -> Unit,
    onSeekProgressChanged: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f)
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 4.5.dp.toPx() }

    val displayProgress = if (isSeeking) seekProgress else progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(canSeek) {
                if (!canSeek) return@pointerInput
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = minOf(size.width, size.height) / 2f
                val touchInnerRadius = radius - 36.dp.toPx()

                detectDragGestures(
                    onDragStart = { offset ->
                        val dist = (offset - center).getDistance()
                        if (dist >= touchInnerRadius) {
                            onSeekStarted()
                            val angleDeg = Math.toDegrees(
                                kotlin.math.atan2(
                                    (offset.y - center.y).toDouble(),
                                    (offset.x - center.x).toDouble()
                                )
                            ).toFloat()
                            val fraction = ((angleDeg + 90f).mod(360f) / 360f).coerceIn(0f, 1f)
                            onSeekProgressChanged(fraction)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val offset = change.position
                        val angleDeg = Math.toDegrees(
                            kotlin.math.atan2(
                                (offset.y - center.y).toDouble(),
                                (offset.x - center.x).toDouble()
                            )
                        ).toFloat()
                        val fraction = ((angleDeg + 90f).mod(360f) / 360f).coerceIn(0f, 1f)
                        onSeekProgressChanged(fraction)
                    },
                    onDragEnd = {
                        onSeekFinished()
                    },
                    onDragCancel = {
                        onSeekFinished()
                    }
                )
            }
    ) {
        val diameter = minOf(size.width, size.height) - strokeWidthPx - 6.dp.toPx()
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f
        )

        // 1. Subtle Outer Track
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx)
        )

        // 2. Active Progress Arc with MD3 standard rounded pill cap (no separate thumb)
        val sweep = displayProgress * 360f
        if (sweep > 0.5f) {
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
    }
}
