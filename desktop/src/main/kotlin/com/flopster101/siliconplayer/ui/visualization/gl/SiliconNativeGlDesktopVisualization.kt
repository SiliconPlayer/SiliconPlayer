package com.flopster101.siliconplayer.ui.visualization.gl

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationProjectMResolutionMode
import com.flopster101.siliconplayer.VisualizationRenderBackend
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.desktop.DesktopProjectMPresetSets
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextPalette
import com.flopster101.siliconplayer.ui.visualization.channel.layoutChannelScopeText
import com.flopster101.siliconplayer.ui.visualization.channel.parseChannelScopeTextStates
import java.awt.Font
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

data class SiliconNativeGlFrame(
    val mode: Int, // 1=Bars, 2=Osc, 3=VU, 4=ChannelScope, 5=Starfield, 100=projectM plugin
    val backend: VisualizationRenderBackend = VisualizationRenderBackend.OpenGlTexture,
    val isPlaying: Boolean = true,
    val trackKey: String? = null,
    val artworkImage: ImageBitmap? = null,
    val placeholderIconImage: ImageBitmap? = null,
    val showArtworkBackground: Boolean = true,
    val monochromeBackdrop: Boolean = false,
    val visualAlpha: Float = 1f,
    val primaryColorArgb: Int = 0xFFFFFFFF.toInt(),
    val surfaceColorArgb: Int = 0xFF121212.toInt(),
    val placeholderIconType: Int = 1,
    val contrastMode: Int = 0,
    val contrastScrimColorArgb: Int = 0xFF000000.toInt(),
    // Channel scope options
    val channelLayout: Int = 0,
    val textAnchor: Int = 0,
    val vuAnchor: Int = 0,
    val channelLayoutStrategy: VisualizationChannelScopeLayout = VisualizationChannelScopeLayout.ColumnFirst,
    val channelTextAnchor: VisualizationChannelScopeTextAnchor = VisualizationChannelScopeTextAnchor.TopLeft,
    val channelVuAnchor: VisualizationVuAnchor = VisualizationVuAnchor.Bottom,
    val channelScopeTextEnabled: Boolean = true,
    val showChannel: Boolean = true,
    val showNote: Boolean = true,
    val showVolume: Boolean = true,
    val showEffectPrimary: Boolean = true,
    val showEffectSecondary: Boolean = false,
    val showChip: Boolean = true,
    val showInstrument: Boolean = true,
    val showSample: Boolean = true,
    val vuEnabled: Boolean = true,
    val textSizeSp: Int = 8,
    val textFont: VisualizationChannelScopeTextFont = VisualizationChannelScopeTextFont.System,
    val noteFormat: VisualizationNoteNameFormat = VisualizationNoteNameFormat.American,
    val paddingPx: Float = 6f,
    val gridColorArgb: Int = 0x66FFFFFF,
    val gridWidthPx: Float = 1f,
    val lineColorArgb: Int = 0xFF80D8FF.toInt(),
    val lineWidthPx: Float = 1.5f,
    val vuColorArgb: Int = 0xFF76FF03.toInt(),
    val textPalette: GlChannelScopeTextPalette = GlChannelScopeTextPalette(),
    val instrumentNamesByIndex: Map<Int, String> = emptyMap(),
    val sampleNamesByIndex: Map<Int, String> = emptyMap(),
    val chipNamesByChannelIndex: Map<Int, String> = emptyMap(),
    val shadowEnabled: Boolean = true,
    val hideWhenOverflow: Boolean = false,
    val channelScopeWindowMs: Int = 30,
    val channelScopeGainPercent: Int = 100,
    val channelScopeDcRemovalEnabled: Boolean = true,
    val channelScopeWaveformClippingEnabled: Boolean = true,
    val channelScopeTriggerMode: Int = 0,
    val channelScopeWaveRenderMode: Int = 1,
    val channelScopeAntialiasMethod: Int = 0,
    val channelScopeTrackTransition: Int = 1,
    // Oscilloscope options
    val oscStereo: Boolean = false,
    val oscWindowMs: Int = 30,
    val oscTriggerMode: Int = 0,
    val oscWaveColorArgb: Int = 0xFF80D8FF.toInt(),
    val oscLineWidthPx: Float = 2f,
    val oscGridColorArgb: Int = 0x40FFFFFF,
    val oscGridWidthPx: Float = 1f,
    val oscShowCenterLine: Boolean = true,
    val oscShowGrid: Boolean = true,
    // Bars options
    val barCount: Int = 32,
    val barSmoothingPercent: Int = 50,
    val barStartColorArgb: Int = 0xFF80D8FF.toInt(),
    val barEndColorArgb: Int = 0xFF40C4FF.toInt(),
    val barCornerRadiusPx: Float = 4f,
    val barShowFrequencyGuide: Boolean = false,
    val barGuideColorArgb: Int = 0x40FFFFFF,
    // VU meters options
    val vuStereo: Boolean = true,
    val vuMetersAnchor: Int = 2,
    val vuSmoothingPercent: Int = 50,
    val vuFillColorArgb: Int = 0xFF76FF03.toInt(),
    val vuTrackColorArgb: Int = 0x40FFFFFF,
    val vuLabelColorArgb: Int = 0xFFCCCCCC.toInt(),
    // Starfield options
    val starfieldStarCount: Int = 350,
    val starfieldSpeed: Float = 0.30f,
    val starfieldFov: Float = 1.0f,
    val starfieldNearPlane: Float = 0.06f,
    val starfieldStarColorArgb: Int = 0xFFFFFFFF.toInt(),
    val starfieldBaseSizePx: Float = 6.0f,
    val starfieldSizeGrowth: Float = 1.2f,
    val starfieldFarDim: Float = 0.6f,
    val starfieldSoftness: Float = 0.25f,
    val starfieldBeatGlow: Float = 0.0f,
    val starfieldGlowSize: Float = 3.0f,
    val starfieldTrailPersistence: Float = 0.55f,
    val starfieldStreaks: Boolean = false,
    val starfieldStreakLength: Float = 1.0f,
    val starfieldCenterX: Float = 0f,
    val starfieldCenterY: Float = 0f,
    val starfieldAutoDrift: Boolean = false,
    val starfieldBeatFollow: Boolean = false,
    val starfieldReactSpeed: Float = 0.6f,
    val starfieldFlash: Float = 0.1f,
    val starfieldSquarePixels: Boolean = false
)

private class SiliconNativeDesktopRenderThread(
    private val prefs: AppPreferences,
    private val density: Float,
    private val onFrameAvailable: (ImageBitmap) -> Unit,
    private val onFrameStats: ((fps: Int, frameMs: Int) -> Unit)?
) : Thread(null, null, "SiliconNativeDesktopRenderThread", 8L * 1024 * 1024) {

    @Volatile
    private var running = true

    @Volatile
    private var targetDisplayHz = 0
    private val lock = Any()
    private var currentFrame: SiliconNativeGlFrame? = null
    private var targetWidth = 256
    private var targetHeight = 256

    fun updateSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        synchronized(lock) {
            targetWidth = width
            targetHeight = height
        }
    }

    fun updateDisplayHz(hz: Int) {
        targetDisplayHz = hz
    }

    fun updateFrame(frame: SiliconNativeGlFrame) {
        synchronized(lock) {
            currentFrame = frame
        }
    }

    fun requestStop() {
        running = false
        interrupt()
    }

    override fun run() {
        val visHandle = SiliconVisNativeBridge.nativeCreate()
        if (visHandle == 0L) return

        NativeBridge.attachAudioEngineToVisualizer(visHandle)

        var hostHandle = 0L
        var vulkanActive = false
        var scopeTextFloatBuffer: java.nio.FloatBuffer? = null

        var lastFrameTimeNs = System.nanoTime()
        var fpsFrameCount = 0
        var fpsTimerNs = System.nanoTime()
        var lastHudPublishNs = 0L
        var latestDrawFps = 0

        var lastArtwork: ImageBitmap? = null
        var lastPlaceholderIcon: ImageBitmap? = null
        var lastTextFontKey = ""
        var lastScopeFontKey = ""
        var scopeEmitter: ScopeTextQuadEmitter? = null
        var lastScopeFrame: GlChannelScopeTextFrame? = null
        var lastScopeStates: List<ChannelScopeChannelTextState> = emptyList()
        var lastScopePollNs = 0L
        var lastScopeW = 0
        var lastScopeH = 0

        var projectMAttached = false
        var projectMSawStopped = false
        var projectMStoppedTrackEmpty = false
        var projectMTargetFps = 30

        var pausedFrameRendered = false
        var forceRenderUntilNs = 0L
        var lastRenderedTrackKey: String? = null
        var lastDataAlivePollNs = 0L
        var dataChannelsAlive = false
        var dataSerial = -1L
        var capturedSerial = -1L
        var pendingDataSerial = -1L
        var scopeSceneHasLiveData = false
        var transitionActive = false
        var transitionPending = false
        var transitionStartNs = 0L
        var transitionPendingSinceNs = 0L
        var hasRenderedAtLeastOneFrame = false

        var currentBufW = 0
        var currentBufH = 0
        var directBuffer: ByteBuffer? = null
        var pixelByteArray: ByteArray? = null
        // Double-buffered presenting bitmaps, allocated once per size. The UI
        // draws one slot while the render thread fills the other, so frames
        // never tear and no native image is allocated per frame.
        val skiaBitmaps = arrayOfNulls<Bitmap>(2)
        val composeBitmaps = arrayOfNulls<ImageBitmap>(2)
        var backSlot = 0

        val supportsVulkan = SiliconVisNativeBridge.nativeVulkanIsSupported()
        try {
            while (running) {
                val frameStartNs = System.nanoTime()
                val (w, h, frame) = synchronized(lock) {
                    Triple(targetWidth.coerceAtLeast(16), targetHeight.coerceAtLeast(16), currentFrame)
                }
                // Measured vsync rate of the monitor hosting the window; 60 until known.
                val displayFrameTimeNs = if (targetDisplayHz in 30..240) {
                    1_000_000_000L / targetDisplayHz
                } else {
                    16_666_667L
                }

                val isVulkanRequested = frame?.backend == VisualizationRenderBackend.VulkanSurface &&
                    supportsVulkan &&
                    (frame?.mode == 1 || frame?.mode == 4 || frame?.mode == 5)

                if (isVulkanRequested) {
                    if (hostHandle != 0L) {
                        if (transitionActive || transitionPending) {
                            transitionActive = false
                            transitionPending = false
                            try { DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle) } catch (_: Throwable) {}
                        }
                        DesktopGlSurface.nativeDestroy(hostHandle, visHandle)
                        hostHandle = 0L
                        lastTextFontKey = ""
                        lastScopeFontKey = ""
                        lastScopeFrame = null
                    }
                    if (!vulkanActive) {
                        vulkanActive = SiliconVisNativeBridge.nativeInitVulkan(visHandle, w, h, null)
                        if (vulkanActive) {
                            lastTextFontKey = ""
                            lastScopeFontKey = ""
                            lastScopeFrame = null
                        }
                    }
                    if (!vulkanActive && hostHandle == 0L) {
                        hostHandle = DesktopGlSurface.nativeInit(visHandle)
                        lastTextFontKey = ""
                        lastScopeFontKey = ""
                        lastScopeFrame = null
                    }
                } else {
                    if (vulkanActive) {
                        try { SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0) } catch (_: Throwable) {}
                        SiliconVisNativeBridge.nativeReleaseVulkan(visHandle)
                        vulkanActive = false
                        lastTextFontKey = ""
                        lastScopeFontKey = ""
                        lastScopeFrame = null
                    }
                    if (hostHandle == 0L) {
                        hostHandle = DesktopGlSurface.nativeInit(visHandle)
                        lastTextFontKey = ""
                        lastScopeFontKey = ""
                        lastScopeFrame = null
                    }
                }

                if (!vulkanActive && hostHandle == 0L) {
                    try {
                        sleep(16)
                    } catch (_: InterruptedException) {
                        if (!running) break
                    }
                    continue
                }

                val surfaceSizeChanged = currentBufW != w || currentBufH != h || directBuffer == null
                if (surfaceSizeChanged) {
                    if (vulkanActive) {
                        SiliconVisNativeBridge.nativeResizeVulkan(visHandle, w, h, density)
                    }
                    if (transitionActive || transitionPending) {
                        transitionActive = false
                        transitionPending = false
                        capturedSerial = dataSerial
                        if (vulkanActive && visHandle != 0L) {
                            try {
                                SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                            } catch (_: Throwable) {}
                        } else if (hostHandle != 0L) {
                            try {
                                DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle)
                            } catch (_: Throwable) {}
                        }
                    }
                    currentBufW = w
                    currentBufH = h
                    directBuffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
                    pixelByteArray = ByteArray(w * h * 4)
                    val info = ImageInfo(
                        ColorInfo(ColorType.RGBA_8888, ColorAlphaType.PREMUL, ColorSpace.sRGB),
                        w,
                        h
                    )
                    for (slot in 0..1) {
                        val bitmap = Bitmap().also { it.allocPixels(info) }
                        skiaBitmaps[slot] = bitmap
                        composeBitmaps[slot] = bitmap.asImageBitmap()
                    }
                }

                val frameTrackKey = frame?.trackKey
                val trackDetectNowNs = System.nanoTime()
                if (frame?.mode == 4) {
                    dataSerial = NativeBridge.getChannelScopeDataSerial()
                    if (trackDetectNowNs - lastDataAlivePollNs >= 30_000_000L) {
                        lastDataAlivePollNs = trackDetectNowNs
                        dataChannelsAlive = runCatching {
                            NativeBridge.getChannelScopeTextState(1).isNotEmpty()
                        }.getOrDefault(false)
                    }
                } else {
                    scopeSceneHasLiveData = false
                }
                val uiTrackFlipped = frameTrackKey != lastRenderedTrackKey
                val hadRenderedTrack = lastRenderedTrackKey != null
                if (uiTrackFlipped) {
                    if (frameTrackKey != null) {
                        lastRenderedTrackKey = frameTrackKey
                    }
                    if (frameTrackKey != null && hadRenderedTrack) {
                        forceRenderUntilNs = trackDetectNowNs + 1_600_000_000L
                    }
                }

                if (
                    frame?.mode == 4 &&
                    frame.channelScopeTrackTransition != 0 &&
                    hasRenderedAtLeastOneFrame
                ) {
                    if (!transitionActive && !transitionPending && frameTrackKey != null) {
                        val dataFlipped = capturedSerial >= 0L && dataSerial != capturedSerial
                        if ((dataFlipped || (uiTrackFlipped && hadRenderedTrack)) && scopeSceneHasLiveData) {
                            transitionPending = true
                            transitionPendingSinceNs = trackDetectNowNs
                            pendingDataSerial = capturedSerial
                            forceRenderUntilNs = trackDetectNowNs + 1_600_000_000L
                            if (vulkanActive && visHandle != 0L) {
                                try {
                                    SiliconVisNativeBridge.nativeTakeTransitionSnapshotVulkan(visHandle)
                                } catch (_: Throwable) {}
                            } else if (hostHandle != 0L) {
                                try {
                                    DesktopGlSurface.nativeTakeTransitionSnapshot(hostHandle)
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                    if (transitionPending) {
                        val newDataAlive = dataSerial != pendingDataSerial && dataChannelsAlive
                        if (newDataAlive) {
                            transitionPending = false
                            transitionActive = true
                            transitionStartNs = trackDetectNowNs
                        } else if (trackDetectNowNs - transitionPendingSinceNs > 250_000_000L) {
                            transitionPending = false
                            if (vulkanActive && visHandle != 0L) {
                                try {
                                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                                } catch (_: Throwable) {}
                            } else if (hostHandle != 0L) {
                                try {
                                    DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle)
                                } catch (_: Throwable) {}
                            }
                            capturedSerial = dataSerial
                        }
                    }
                }

                if (frame != null && !frame.isPlaying) {
                    val isFadeMode = frame.mode == 1 || frame.mode == 2 || frame.mode == 3 || frame.mode == 5
                    val fadeSettled = !isFadeMode || frame.visualAlpha <= 0.001f
                    val transitionRendering =
                        System.nanoTime() < forceRenderUntilNs || transitionActive || transitionPending
                    if (pausedFrameRendered && !surfaceSizeChanged && fadeSettled && !transitionRendering) {
                        val targetFrameTimeNs = if (frame.mode == 100 && projectMTargetFps > 0) {
                            1_000_000_000L / projectMTargetFps
                        } else {
                            displayFrameTimeNs
                        }
                        val elapsedNs = System.nanoTime() - frameStartNs
                        val sleepNs = (targetFrameTimeNs - elapsedNs).coerceAtLeast(10_000_000L)
                        try {
                            sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                        } catch (_: InterruptedException) {
                            if (!running) break
                        }
                        continue
                    }
                    pausedFrameRendered = true
                } else {
                    pausedFrameRendered = false
                }

                if (frame != null) {
                    val fontKey = if (frame.mode == 3) "vumeters_sans" else "scope_${frame.textFont.storageValue}"
                    if (fontKey != lastTextFontKey) {
                        lastTextFontKey = fontKey
                        try {
                            val fontName = if (frame.mode == 3) {
                                Font.SANS_SERIF
                            } else {
                                when (frame.textFont) {
                                    VisualizationChannelScopeTextFont.System -> Font.SANS_SERIF
                                    else -> Font.MONOSPACED
                                }
                            }
                            val uploadData = if (frame.mode == 3) {
                                DesktopGlFontAtlas.createAtlasUploadData(
                                    fontName = Font.SANS_SERIF,
                                    fontResourcePath = "/fonts/vumeters/roboto_medium.ttf",
                                    baseFontSizePx = 32f,
                                    bold = false
                                )
                            } else {
                                DesktopGlFontAtlas.createAtlasUploadData(fontName = fontName, baseFontSizePx = 32f)
                            }
                            SiliconVisNativeBridge.nativeSetFontAtlas(
                                handle = visHandle,
                                byteBuffer = uploadData.pixelBuffer,
                                width = uploadData.width,
                                height = uploadData.height,
                                baseFontSizePx = uploadData.baseFontSizePx,
                                lineHeightPx = uploadData.lineHeightPx,
                                glyphBuffer = uploadData.glyphBuffer,
                                glyphCount = uploadData.glyphCount
                            )
                        } catch (_: Throwable) {}
                    }

                    if (frame.artworkImage !== lastArtwork) {
                        lastArtwork = frame.artworkImage
                        val art = frame.artworkImage
                        if (art != null) {
                            try {
                                val pixelMap = art.toPixelMap()
                                val artW = pixelMap.width
                                val artH = pixelMap.height
                                val intBuf = pixelMap.buffer
                                val buf = ByteBuffer.allocateDirect(artW * artH * 4).order(ByteOrder.nativeOrder())
                                for (pixel in intBuf) {
                                    buf.put((pixel ushr 16 and 0xFF).toByte())
                                    buf.put((pixel ushr 8 and 0xFF).toByte())
                                    buf.put((pixel and 0xFF).toByte())
                                    buf.put((pixel ushr 24 and 0xFF).toByte())
                                }
                                buf.flip()
                                SiliconVisNativeBridge.nativeSetArtworkPixels(visHandle, buf, artW, artH)
                            } catch (_: Throwable) {
                                SiliconVisNativeBridge.nativeClearArtwork(visHandle)
                            }
                        } else {
                            SiliconVisNativeBridge.nativeClearArtwork(visHandle)
                        }
                    }

                    if (frame.placeholderIconImage !== lastPlaceholderIcon) {
                        lastPlaceholderIcon = frame.placeholderIconImage
                        val icon = frame.placeholderIconImage
                        if (icon != null) {
                            try {
                                val pixelMap = icon.toPixelMap()
                                val iconW = pixelMap.width
                                val iconH = pixelMap.height
                                val intBuf = pixelMap.buffer
                                val buf = ByteBuffer.allocateDirect(iconW * iconH * 4).order(ByteOrder.nativeOrder())
                                for (pixel in intBuf) {
                                    buf.put((pixel ushr 16 and 0xFF).toByte())
                                    buf.put((pixel ushr 8 and 0xFF).toByte())
                                    buf.put((pixel and 0xFF).toByte())
                                    buf.put((pixel ushr 24 and 0xFF).toByte())
                                }
                                buf.flip()
                                SiliconVisNativeBridge.nativeSetIconPixels(visHandle, buf, iconW, iconH)
                            } catch (_: Throwable) {
                                SiliconVisNativeBridge.nativeClearIcon(visHandle)
                            }
                        } else {
                            SiliconVisNativeBridge.nativeClearIcon(visHandle)
                        }
                    }

                    if (frame.mode == 100 && !projectMAttached) {
                        val enabledSets = DesktopProjectMPresetSets.enabledSets(prefs)
                        if (enabledSets.isNotEmpty()) {
                            val setIds = enabledSets.map { it.id }.toTypedArray()
                            val setDirs = enabledSets.map { it.dir }.toTypedArray()
                            val randomStart = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)
                            val (presetKeys, _) = try {
                                DesktopProjectMPresetSets.indexedPresetKeys(prefs)
                            } catch (_: Throwable) {
                                emptyList<String>() to emptyList()
                            }
                            val savedPreset = prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET, null)
                            val startPreset = if (randomStart && presetKeys.isNotEmpty()) {
                                try { SiliconVisNativeBridge.nativeClearProjectMLastPreset() } catch (_: Throwable) {}
                                presetKeys.random()
                            } else savedPreset
                            if (presetKeys.isNotEmpty()) {
                                SiliconVisNativeBridge.nativeAttachProjectMWithKeys(
                                    visHandle, setIds, setDirs, presetKeys.toTypedArray(), startPreset
                                )
                            } else {
                                SiliconVisNativeBridge.nativeAttachProjectM(visHandle, setIds, setDirs, startPreset)
                            }
                            projectMAttached = true
                            projectMSawStopped = false
                            projectMStoppedTrackEmpty = false
                            try {
                                val duration = prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString())?.toDoubleOrNull() ?: AppDefaults.Visualization.ProjectM.presetDurationSeconds
                                SiliconVisNativeBridge.nativeProjectMSetPresetDuration(duration)
                                SiliconVisNativeBridge.nativeProjectMSetHardCutEnabled(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled))
                                SiliconVisNativeBridge.nativeProjectMSetHardCutSensitivity(prefs.getFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity))
                                SiliconVisNativeBridge.nativeProjectMSetRotationRandom(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom))
                                SiliconVisNativeBridge.nativeProjectMSetMeshSize(prefs.getInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, AppDefaults.Visualization.ProjectM.meshSize))
                                SiliconVisNativeBridge.nativeProjectMSetAspectCorrection(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, AppDefaults.Visualization.ProjectM.aspectCorrection))
                                SiliconVisNativeBridge.nativeProjectMSetMaxResolution(
                                    VisualizationProjectMResolutionMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_RENDER_RESOLUTION, AppDefaults.Visualization.ProjectM.renderResolution.storageValue)).maxLongEdgePx
                                )
                                val fpsMode = VisualizationOscFpsMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, AppDefaults.Visualization.ProjectM.fpsMode.storageValue))
                                val fps = when (fpsMode) {
                                    VisualizationOscFpsMode.Default -> 30
                                    VisualizationOscFpsMode.Fps60 -> 60
                                    VisualizationOscFpsMode.NativeRefresh -> 0
                                }
                                projectMTargetFps = fps
                                SiliconVisNativeBridge.nativeProjectMSetFps(fps)
                            } catch (_: Throwable) {}
                        }
                    } else if (frame.mode != 100 && projectMAttached) {
                        try {
                            SiliconVisNativeBridge.nativeDetachProjectM(visHandle)
                        } catch (_: Throwable) {}
                        projectMAttached = false
                    }

                    if (frame.mode == 100) {
                        if (!frame.isPlaying) {
                            projectMSawStopped = true
                            projectMStoppedTrackEmpty = frame.trackKey == null
                        } else if (projectMSawStopped && projectMAttached) {
                            projectMSawStopped = false
                            val wasStoppedEmpty = projectMStoppedTrackEmpty
                            projectMStoppedTrackEmpty = false
                            if (wasStoppedEmpty) {
                                if (prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)) {
                                    val presetKeys = try {
                                        DesktopProjectMPresetSets.indexedPresetKeys(prefs).first
                                    } catch (_: Throwable) {
                                        emptyList<String>()
                                    }
                                    if (presetKeys.isNotEmpty()) {
                                        try { SiliconVisNativeBridge.nativeClearProjectMLastPreset() } catch (_: Throwable) {}
                                        SiliconVisNativeBridge.nativeProjectMLoadPreset(presetKeys.random(), true)
                                    }
                                }
                            }
                        }
                    }

                    SiliconVisNativeBridge.nativeSetMode(visHandle, frame.mode)
                    SiliconVisNativeBridge.nativeSetArtworkTheme(
                        visHandle,
                        frame.primaryColorArgb,
                        frame.surfaceColorArgb,
                        frame.placeholderIconType
                    )
                    SiliconVisNativeBridge.nativeSetContrastMode(visHandle, frame.contrastMode)
                    SiliconVisNativeBridge.nativeSetContrastScrim(visHandle, frame.contrastScrimColorArgb)
                    SiliconVisNativeBridge.nativeSetShowArtworkBackground(visHandle, frame.showArtworkBackground)
                    SiliconVisNativeBridge.nativeSetBackdropMonochrome(visHandle, frame.monochromeBackdrop)
                    SiliconVisNativeBridge.nativeSetVisualAlpha(visHandle, frame.visualAlpha)
                    SiliconVisNativeBridge.nativeSetChannelScopeAntialiasMethod(visHandle, frame.channelScopeAntialiasMethod)

                    when (frame.mode) {
                        1 -> { // Bars
                            SiliconVisNativeBridge.nativeSetBarsOptions(
                                handle = visHandle,
                                barCount = frame.barCount,
                                smoothing = frame.barSmoothingPercent / 100f,
                                startColorArgb = frame.barStartColorArgb,
                                endColorArgb = frame.barEndColorArgb,
                                cornerRadiusPx = frame.barCornerRadiusPx,
                                showFrequencyGuide = frame.barShowFrequencyGuide,
                                guideColorArgb = frame.barGuideColorArgb
                            )
                        }
                        2 -> { // Oscilloscope
                            SiliconVisNativeBridge.nativeSetOscilloscopeOptions(
                                handle = visHandle,
                                stereo = frame.oscStereo,
                                windowMs = frame.oscWindowMs,
                                triggerMode = frame.oscTriggerMode,
                                waveColorArgb = frame.oscWaveColorArgb,
                                lineWidthPx = frame.oscLineWidthPx,
                                gridColorArgb = frame.oscGridColorArgb,
                                gridWidthPx = frame.oscGridWidthPx,
                                showCenterLine = frame.oscShowCenterLine,
                                showGrid = frame.oscShowGrid
                            )
                        }
                        3 -> { // VU meters
                            SiliconVisNativeBridge.nativeSetVuMetersOptions(
                                handle = visHandle,
                                stereo = frame.vuStereo,
                                anchor = frame.vuMetersAnchor,
                                smoothing = frame.vuSmoothingPercent / 100f,
                                fillColorArgb = frame.vuFillColorArgb,
                                trackColorArgb = frame.vuTrackColorArgb,
                                labelColorArgb = frame.vuLabelColorArgb
                            )
                        }
                        4 -> { // Channel scope
                            SiliconVisNativeBridge.nativeSetChannelScopeOptions(
                                handle = visHandle,
                                layout = frame.channelLayout,
                                anchor = frame.textAnchor,
                                vuAnchor = frame.vuAnchor,
                                vuEnabled = frame.vuEnabled,
                                textSizeSp = frame.textSizeSp,
                                paddingPx = frame.paddingPx,
                                gridColorArgb = frame.gridColorArgb,
                                gridWidthPx = frame.gridWidthPx,
                                lineColorArgb = frame.lineColorArgb,
                                lineWidthPx = frame.lineWidthPx,
                                vuColorArgb = frame.vuColorArgb,
                                chArgb = frame.textPalette.channelArgb,
                                noteArgb = frame.textPalette.noteArgb,
                                volArgb = frame.textPalette.volumeArgb,
                                effArgb = frame.textPalette.effectArgb,
                                instArgb = frame.textPalette.instrumentOrSampleArgb,
                                sepArgb = frame.textPalette.separatorArgb,
                                shadowEnabled = frame.shadowEnabled,
                                hideWhenOverflow = frame.hideWhenOverflow,
                                windowMs = frame.channelScopeWindowMs,
                                gainPercent = frame.channelScopeGainPercent,
                                dcRemovalEnabled = frame.channelScopeDcRemovalEnabled,
                                triggerMode = frame.channelScopeTriggerMode,
                                waveRenderMode = frame.channelScopeWaveRenderMode,
                                waveformClipping = frame.channelScopeWaveformClippingEnabled
                            )
                        }
                        5 -> { // Starfield
                            SiliconVisNativeBridge.nativeSetStarfieldOptions(
                                handle = visHandle,
                                starCount = frame.starfieldStarCount,
                                speed = frame.starfieldSpeed,
                                fov = frame.starfieldFov,
                                nearPlane = frame.starfieldNearPlane,
                                starColorArgb = frame.starfieldStarColorArgb,
                                baseSizePx = frame.starfieldBaseSizePx,
                                sizeGrowth = frame.starfieldSizeGrowth,
                                farDim = frame.starfieldFarDim,
                                softness = frame.starfieldSoftness,
                                beatGlow = frame.starfieldBeatGlow,
                                glowSize = frame.starfieldGlowSize,
                                trailPersistence = frame.starfieldTrailPersistence,
                                streaks = frame.starfieldStreaks,
                                streakLength = frame.starfieldStreakLength,
                                centerX = frame.starfieldCenterX,
                                centerY = frame.starfieldCenterY,
                                autoDrift = frame.starfieldAutoDrift,
                                beatFollow = frame.starfieldBeatFollow,
                                reactSpeed = frame.starfieldReactSpeed,
                                flash = frame.starfieldFlash,
                                squareStars = frame.starfieldSquarePixels
                            )
                        }
                    }

                    if (frame.mode == 4 && frame.channelScopeTextEnabled) {
                        val overlayKey = "scope_" + frame.textFont.storageValue
                        if (overlayKey != lastScopeFontKey) {
                            lastScopeFontKey = overlayKey
                            scopeEmitter = try {
                                val resourcePath = when (frame.textFont) {
                                    VisualizationChannelScopeTextFont.System -> null
                                    VisualizationChannelScopeTextFont.RaccoonSerif -> "/fonts/scope/raccoon_serif_base.ttf"
                                    VisualizationChannelScopeTextFont.RaccoonMono -> "/fonts/scope/raccoon_serif_mono.ttf"
                                    VisualizationChannelScopeTextFont.RetroCuteMono -> "/fonts/scope/retro_pixel_cute_mono.ttf"
                                    VisualizationChannelScopeTextFont.RetroThick -> "/fonts/scope/retro_pixel_thick.ttf"
                                }
                                val uploadData = DesktopGlFontAtlas.createAtlasUploadData(
                                    fontName = Font.SANS_SERIF,
                                    fontResourcePath = resourcePath,
                                    baseFontSizePx = 32f
                                )
                                val uploaded = if (hostHandle != 0L) {
                                    DesktopGlSurface.nativeUploadScopeAtlas(
                                        hostHandle,
                                        uploadData.pixelBuffer,
                                        uploadData.width,
                                        uploadData.height,
                                        uploadData.baseFontSizePx,
                                        uploadData.lineHeightPx,
                                        uploadData.glyphBuffer,
                                        uploadData.glyphCount
                                    )
                                } else if (vulkanActive) {
                                    SiliconVisNativeBridge.nativeSetFontAtlas(
                                        visHandle,
                                        uploadData.pixelBuffer,
                                        uploadData.width,
                                        uploadData.height,
                                        uploadData.baseFontSizePx,
                                        uploadData.lineHeightPx,
                                        uploadData.glyphBuffer,
                                        uploadData.glyphCount
                                    )
                                    true
                                } else {
                                    false
                                }
                                if (uploaded) {
                                    ScopeTextQuadEmitter(uploadData.glyphBuffer, uploadData.baseFontSizePx, uploadData.lineHeightPx)
                                } else {
                                    null
                                }
                            } catch (_: Throwable) {
                                null
                            }
                            lastScopeFrame = null
                        }
                        val nowNs = System.nanoTime()
                        if (nowNs - lastScopePollNs >= 20_000_000L) {
                            lastScopePollNs = nowNs
                            val raw = runCatching { NativeBridge.getChannelScopeTextState(64) }.getOrDefault(IntArray(0))
                            // Hold the last good states through empty polls; clearing here
                            // blinked the labels whenever the engine had no fresh row ready.
                            if (raw.isNotEmpty()) lastScopeStates = parseChannelScopeTextStates(raw)
                        }
                        val emitter = scopeEmitter
                        if (emitter != null && lastScopeStates.isNotEmpty()) {
                            val candidate = GlChannelScopeTextFrame(
                                channelCount = lastScopeStates.size,
                                channelTextStates = lastScopeStates,
                                instrumentNamesByIndex = frame.instrumentNamesByIndex,
                                sampleNamesByIndex = frame.sampleNamesByIndex,
                                chipNamesByChannelIndex = frame.chipNamesByChannelIndex,
                                layoutStrategy = frame.channelLayoutStrategy,
                                anchor = frame.channelTextAnchor,
                                paddingPx = frame.paddingPx,
                                textSizeSp = frame.textSizeSp,
                                density = density,
                                hideWhenOverflow = frame.hideWhenOverflow,
                                textShadowEnabled = frame.shadowEnabled,
                                textFont = frame.textFont,
                                noteFormat = frame.noteFormat,
                                showChannel = frame.showChannel,
                                showNote = frame.showNote,
                                showVolume = frame.showVolume,
                                showEffectPrimary = frame.showEffectPrimary,
                                showEffectSecondary = frame.showEffectSecondary,
                                showChip = frame.showChip,
                                showInstrument = frame.showInstrument,
                                showSample = frame.showSample,
                                palette = frame.textPalette
                            )
                            if (candidate != lastScopeFrame || w != lastScopeW || h != lastScopeH) {
                                lastScopeFrame = candidate
                                lastScopeW = w
                                lastScopeH = h
                                val layout = layoutChannelScopeText(candidate, w.toFloat(), h.toFloat(), emitter.measurer)
                                val quads = if (layout == null) {
                                    FloatArray(0)
                                } else {
                                    emitter.emitRuns(layout.runs, layout.textSizePx, layout.shadowEnabled)
                                }
                                if (vulkanActive) {
                                    if (quads.isNotEmpty()) {
                                        val vertexCount = quads.size / 8
                                        var fBuf = scopeTextFloatBuffer
                                        if (fBuf == null || fBuf.capacity() < quads.size) {
                                            fBuf = ByteBuffer.allocateDirect(quads.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                                            scopeTextFloatBuffer = fBuf
                                        }
                                        fBuf.clear()
                                        fBuf.put(quads)
                                        fBuf.flip()
                                        SiliconVisNativeBridge.nativeSetTextQuads(visHandle, fBuf, vertexCount)
                                    } else {
                                        SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                                    }
                                } else if (hostHandle != 0L) {
                                    try {
                                        DesktopGlSurface.nativeSetScopeTextQuads(hostHandle, quads, quads.size)
                                    } catch (_: Throwable) {}
                                }
                            }
                        } else if (lastScopeFrame != null) {
                            lastScopeFrame = null
                            if (vulkanActive) {
                                SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                            } else if (hostHandle != 0L) {
                                try {
                                    DesktopGlSurface.nativeSetScopeTextQuads(hostHandle, null, 0)
                                } catch (_: Throwable) {}
                            }
                        }
                    } else if (lastScopeFrame != null) {
                        lastScopeFrame = null
                        if (vulkanActive) {
                            SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                        } else if (hostHandle != 0L) {
                            try {
                                DesktopGlSurface.nativeSetScopeTextQuads(hostHandle, null, 0)
                            } catch (_: Throwable) {}
                        }
                    }
                }

                var transitionOffsetX = 0f
                var transitionAlpha = 0f
                if (frame?.mode == 4 && frame.channelScopeTrackTransition != 0) {
                    if (transitionActive) {
                        val p = ((System.nanoTime() - transitionStartNs) / 750_000_000f).coerceIn(0f, 1f)
                        if (p >= 1f) {
                            transitionActive = false
                            capturedSerial = dataSerial
                            if (vulkanActive && visHandle != 0L) {
                                try {
                                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                                } catch (_: Throwable) {}
                            } else if (hostHandle != 0L) {
                                try {
                                    DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle)
                                } catch (_: Throwable) {}
                            }
                        } else {
                            val eased = p * p * (3f - 2f * p)
                            transitionOffsetX = if (frame.channelScopeTrackTransition == 1) {
                                -eased * w.toFloat()
                            } else {
                                0f
                            }
                            transitionAlpha = 1f - eased
                        }
                    } else if (transitionPending) {
                        transitionOffsetX = 0f
                        transitionAlpha = 1f
                    } else {
                        if (frameTrackKey != null) {
                            capturedSerial = dataSerial
                            scopeSceneHasLiveData = dataChannelsAlive
                        }
                    }
                } else if (transitionActive || transitionPending) {
                    transitionActive = false
                    transitionPending = false
                    capturedSerial = dataSerial
                    if (vulkanActive && visHandle != 0L) {
                        try {
                            SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                        } catch (_: Throwable) {}
                    } else if (hostHandle != 0L) {
                        try {
                            DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle)
                        } catch (_: Throwable) {}
                    }
                }

                directBuffer.clear()
                val ok = if (vulkanActive) {
                    SiliconVisNativeBridge.nativeSetTransitionVulkan(visHandle, transitionOffsetX, transitionAlpha)
                    SiliconVisNativeBridge.nativeRenderVulkan(visHandle)
                    SiliconVisNativeBridge.nativeReadbackVulkan(visHandle, directBuffer)
                } else if (hostHandle != 0L) {
                    DesktopGlSurface.nativeRenderFrame(
                        hostHandle,
                        visHandle,
                        w,
                        h,
                        density,
                        directBuffer,
                        transitionOffsetX,
                        transitionAlpha
                    )
                } else {
                    false
                }
                if (ok) {
                    hasRenderedAtLeastOneFrame = true
                }

                val target = skiaBitmaps[backSlot]
                val ready = composeBitmaps[backSlot]
                if (ok && pixelByteArray != null && target != null && ready != null) {
                    directBuffer.position(0)
                    directBuffer.get(pixelByteArray)
                    val info = ImageInfo(
                        ColorInfo(
                            ColorType.RGBA_8888,
                            ColorAlphaType.PREMUL,
                            ColorSpace.sRGB
                        ),
                        w,
                        h
                    )
                    // The raster owns fresh native pixels per frame; copy into
                    // the reused slot bitmap and release synchronously so
                    // nothing waits on GC cleaners (that lag ballooned RSS
                    // into the gigabytes at 60 fps).
                    Image.makeRaster(info, pixelByteArray, w * 4).use { raster ->
                        raster.readPixels(target)
                    }
                    backSlot = backSlot xor 1
                    onFrameAvailable(ready)
                }

                val frameElapsedNs = System.nanoTime() - frameStartNs
                val targetFrameTimeNs = if (frame?.mode == 100 && projectMTargetFps > 0) {
                    1_000_000_000L / projectMTargetFps
                } else {
                    displayFrameTimeNs
                }
                val sleepNs = targetFrameTimeNs - frameElapsedNs
                if (sleepNs > 1_000_000L) {
                    try {
                        sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    } catch (_: InterruptedException) {
                        if (!running) break
                    }
                }

                val renderMs = (frameElapsedNs / 1_000_000L).toInt().coerceAtLeast(0)
                fpsFrameCount++
                val nowNs = System.nanoTime()
                if (nowNs - fpsTimerNs >= 1_000_000_000L) {
                    val elapsedSec = (nowNs - fpsTimerNs).toDouble() / 1_000_000_000.0
                    latestDrawFps = if (elapsedSec > 0.0) ((fpsFrameCount.toDouble() / elapsedSec).toInt().coerceAtLeast(0)) else fpsFrameCount
                    fpsFrameCount = 0
                    fpsTimerNs = nowNs
                }
                if (nowNs - lastHudPublishNs >= 350_000_000L) {
                    onFrameStats?.invoke(latestDrawFps, renderMs)
                    lastHudPublishNs = nowNs
                }
                lastFrameTimeNs = nowNs
            }
        } finally {
            if (projectMAttached) {
                try {
                    SiliconVisNativeBridge.nativeDetachProjectM(visHandle)
                } catch (_: Throwable) {}
            }
            if (vulkanActive) {
                try {
                    SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                } catch (_: Throwable) {}
                try {
                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                } catch (_: Throwable) {}
                try {
                    SiliconVisNativeBridge.nativeReleaseVulkan(visHandle)
                } catch (_: Throwable) {}
                vulkanActive = false
            }
            if (hostHandle != 0L) {
                try {
                    DesktopGlSurface.nativeReleaseTransitionSnapshot(hostHandle)
                } catch (_: Throwable) {}
                DesktopGlSurface.nativeDestroy(hostHandle, visHandle)
                hostHandle = 0L
            }
            SiliconVisNativeBridge.nativeDestroy(visHandle)
        }
    }
}

@Composable
fun SiliconNativeGlDesktopVisualization(
    frame: SiliconNativeGlFrame,
    modifier: Modifier = Modifier,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null
) {
    val prefs = LocalAppPreferences.current
    val density = LocalDensity.current.density
    var surfaceSize by remember { mutableStateOf(IntSize.Zero) }
    var renderedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // The thread hands back reused bitmap instances, which never compare as
    // changed on their own; the tick forces a redraw of their new pixels.
    var frameTick by remember { mutableIntStateOf(0) }
    val currentOnFrameStats by rememberUpdatedState(onFrameStats)

    val renderThread = remember(prefs) {
        SiliconNativeDesktopRenderThread(
            prefs = prefs,
            density = density,
            onFrameAvailable = { bmp ->
                renderedBitmap = bmp
                frameTick++
            },
            onFrameStats = { fps, frameMs -> currentOnFrameStats?.invoke(fps, frameMs) }
        ).also { it.start() }
    }

    DisposableEffect(renderThread) {
        onDispose {
            renderThread.requestStop()
        }
    }

    LaunchedEffect(frame, surfaceSize) {
        renderThread.updateSize(surfaceSize.width, surfaceSize.height)
        renderThread.updateFrame(frame)
    }

    // The window's monitor is the only vsync that matters, and Compose already
    // ticks on it: derive the pace from real frame intervals, on any backend.
    var displayHz by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val deltas = ArrayDeque<Long>(256)
        var lastNs = 0L
        var sinceReport = 0
        while (true) {
            withFrameNanos { nowNs ->
                if (lastNs != 0L) {
                    deltas.addLast(nowNs - lastNs)
                    if (deltas.size > 240) deltas.removeFirst()
                    if (++sinceReport >= 60 && deltas.size >= 60) {
                        sinceReport = 0
                        val sorted = deltas.sorted()
                        val hz = (1_000_000_000.0 / sorted[sorted.size / 2]).roundToInt().coerceIn(30, 240)
                        if (hz != displayHz) {
                            println("SiliconVis desktop display rate: $hz Hz")
                            displayHz = hz
                        }
                    }
                }
                lastNs = nowNs
            }
        }
    }
    LaunchedEffect(displayHz) {
        renderThread.updateDisplayHz(displayHz)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { surfaceSize = it }
    ) {
        val bmp = renderedBitmap
        if (bmp != null) {
            key(frameTick) {
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
