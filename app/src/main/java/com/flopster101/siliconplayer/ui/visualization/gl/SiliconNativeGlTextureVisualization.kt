package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.VisualizationOscFpsMode
import com.flopster101.siliconplayer.VisualizationProjectMResolutionMode
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flopster101.siliconplayer.R
import com.flopster101.siliconplayer.VisualizationChannelScopeLayout
import com.flopster101.siliconplayer.VisualizationChannelScopeTextAnchor
import com.flopster101.siliconplayer.VisualizationChannelScopeTextFont
import com.flopster101.siliconplayer.VisualizationNoteNameFormat
import com.flopster101.siliconplayer.VisualizationVuAnchor
import com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState
import java.nio.ByteBuffer
import java.nio.ByteOrder

private object VisualizerWarmCache {
    @Volatile private var cachedHandle: Long = 0L
    @Volatile private var cachedAt = 0L
    private const val DECAY_MS = 30_000L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val decayRunnable = Runnable {
        synchronized(this) {
            if (cachedHandle != 0L && android.os.SystemClock.elapsedRealtime() - cachedAt >= DECAY_MS) {
                try { SiliconVisNativeBridge.nativeDestroy(cachedHandle) } catch (_: Throwable) {}
                cachedHandle = 0L
            }
        }
    }
    fun take(): Long = synchronized(this) {
        if (cachedHandle != 0L) {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - cachedAt < DECAY_MS) {
                handler.removeCallbacks(decayRunnable)
                val h = cachedHandle
                cachedHandle = 0L
                return h
            } else {
                try { SiliconVisNativeBridge.nativeDestroy(cachedHandle) } catch (_: Throwable) {}
                cachedHandle = 0L
            }
        }
        0L
    }
    fun put(handle: Long) = synchronized(this) {
        if (cachedHandle != 0L) try { SiliconVisNativeBridge.nativeDestroy(cachedHandle) } catch (_: Throwable) {}
        cachedHandle = handle
        cachedAt = android.os.SystemClock.elapsedRealtime()
        handler.removeCallbacks(decayRunnable)
        handler.postDelayed(decayRunnable, DECAY_MS)
    }
}

data class SiliconNativeGlFrame(
    val mode: Int, // 1=Bars, 2=Osc, 3=VU, 4=ChannelScope, 100=projectM plugin
    val isPlaying: Boolean = true,
    val pcm: FloatArray? = null,
    val pcmFrames: Int = 0,
    val pcmChannels: Int = 0,
    val pcmSampleRate: Int = 0,
    val fft: FloatArray? = null,
    val vuLevels: FloatArray = floatArrayOf(0f, 0f),
    val channelHistories: List<FloatArray> = emptyList(),
    val channelScopeFlatData: FloatArray? = null,
    val channelScopeSamplesPerChannel: Int = 0,
    val channelTextStates: List<ChannelScopeChannelTextState> = emptyList(),
    val instrumentNamesByIndex: Map<Int, String> = emptyMap(),
    val sampleNamesByIndex: Map<Int, String> = emptyMap(),
    val chipNamesByChannelIndex: Map<Int, String> = emptyMap(),
    val artworkBitmap: Bitmap? = null,
    val placeholderIconResId: Int = 0,
    val showArtworkBackground: Boolean = true,
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
    val textPalette: GlChannelScopeTextPalette = GlChannelScopeTextPalette(
        channelArgb = 0xFFCCCCCC.toInt(),
        noteArgb = 0xFF80D8FF.toInt(),
        volumeArgb = 0xFFB9F6CA.toInt(),
        effectArgb = 0xFFFFD180.toInt(),
        instrumentOrSampleArgb = 0xFFEA80FC.toInt(),
        separatorArgb = 0x88FFFFFF.toInt()
    ),
    val shadowEnabled: Boolean = true,
    val hideWhenOverflow: Boolean = false,
    val channelScopeWindowMs: Int = 30,
    val channelScopeGainPercent: Int = 100,
    val channelScopeDcRemovalEnabled: Boolean = true,
    val channelScopeTriggerMode: Int = 0,
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
    val vuTopPlacement: Boolean = false,
    val vuSmoothingPercent: Int = 50,
    val vuFillColorArgb: Int = 0xFF76FF03.toInt(),
    val vuTrackColorArgb: Int = 0x40FFFFFF,
    val vuLabelColorArgb: Int = 0xFFCCCCCC.toInt()
)

data class SiliconNativeGlDynamicData(
    val pcm: FloatArray? = null,
    val pcmFrames: Int = 0,
    val pcmChannels: Int = 0,
    val pcmSampleRate: Int = 0,
    val fft: FloatArray? = null,
    val vuLevels: FloatArray = floatArrayOf(0f, 0f),
    val channelHistories: List<FloatArray> = emptyList(),
    val channelScopeFlatData: FloatArray? = null,
    val channelScopeSamplesPerChannel: Int = 0,
    val channelTextStates: List<ChannelScopeChannelTextState> = emptyList()
)

interface SiliconNativeGlDataConsumer {
    fun pushDynamicData(data: SiliconNativeGlDynamicData)
}

object SiliconNativeGlDataSink {
    @Volatile
    private var activeConsumer: SiliconNativeGlDataConsumer? = null

    fun register(consumer: SiliconNativeGlDataConsumer) {
        activeConsumer = consumer
    }

    fun unregister(consumer: SiliconNativeGlDataConsumer) {
        if (activeConsumer === consumer) {
            activeConsumer = null
        }
    }

    fun pushDynamicData(data: SiliconNativeGlDynamicData) {
        activeConsumer?.pushDynamicData(data)
    }
}

@Composable
fun SiliconNativeGlTextureVisualization(
    frame: SiliconNativeGlFrame,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    var glView by remember { mutableStateOf<SiliconNativeGlTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SiliconNativeGlTextureView(context, density).also { view ->
                glView = view
            }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
            view.updateFrame(frame)
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> glView?.setLifecyclePaused(true)
                Lifecycle.Event.ON_RESUME -> glView?.setLifecyclePaused(false)
                Lifecycle.Event.ON_DESTROY -> glView?.shutdown()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glView?.shutdown()
            glView = null
        }
    }
}

private class SiliconNativeGlTextureView(
    context: Context,
    private val density: Float
) : TextureView(context), TextureView.SurfaceTextureListener, SiliconNativeGlDataConsumer {
    private var renderThread: SiliconNativeTextureRenderThread? = null
    private var latestFrame: SiliconNativeGlFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = true
        SiliconNativeGlDataSink.register(this)
    }

    override fun pushDynamicData(data: SiliconNativeGlDynamicData) {
        renderThread?.setDynamicData(data)
    }

    fun updateFrame(frame: SiliconNativeGlFrame) {
        latestFrame = frame
        renderThread?.setFrameData(frame)
    }

    fun setLifecyclePaused(paused: Boolean) {
        lifecyclePaused = paused
        if (paused) {
            stopRenderThread()
        } else if (isAvailable) {
            startRenderThread(surfaceTexture, width, height)
        }
    }

    fun shutdown() {
        SiliconNativeGlDataSink.unregister(this)
        stopRenderThread()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!lifecyclePaused) {
            startRenderThread(surface, width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderThread?.setSurfaceSize(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun startRenderThread(surfaceTexture: SurfaceTexture?, width: Int, height: Int) {
        if (surfaceTexture == null) return
        stopRenderThread()
        SiliconNativeGlDataSink.register(this)
        val surface = Surface(surfaceTexture)
        val thread = SiliconNativeTextureRenderThread(
            context = context,
            outputSurface = surface,
            initialWidth = width.coerceAtLeast(1),
            initialHeight = height.coerceAtLeast(1),
            density = density,
            onFrameStats = { fps, frameMs ->
                post { onFrameStats?.invoke(fps, frameMs) }
            }
        )
        renderThread = thread
        thread.start()
        latestFrame?.let { thread.setFrameData(it) }
    }

    private fun stopRenderThread() {
        val thread = renderThread ?: return
        renderThread = null
        thread.requestStop()
        runCatching { thread.join(350L) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SiliconNativeGlDataSink.register(this)
    }

    override fun onDetachedFromWindow() {
        shutdown()
        super.onDetachedFromWindow()
    }
}

private class SiliconNativeTextureRenderThread(
    private val context: Context,
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val density: Float,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("SiliconNativeTextureRenderThread") {
    private val lock = Object()

    @Volatile
    private var running = true
    private var frameData: SiliconNativeGlFrame? = null
    private var dynamicData: SiliconNativeGlDynamicData? = null
    private var frameSequence: Long = 0L
    private var renderedFrameSequence: Long = -1L
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var visHandle: Long = 0L

    private var drawFrameCount: Long = 0L
    private var drawWindowStartNs: Long = System.nanoTime()
    private var lastHudPublishNs: Long = System.nanoTime()
    private var latestDrawFps: Int = 0
    private var latestFrameMs: Int = 0

    private var lastArtworkBitmap: Bitmap? = null
    private var lastIconResId: Int = 0
    private var lastIconTintArgb: Int = 0
    private var lastTextFontKey: String? = null
    private var iconDirectBuffer: ByteBuffer? = null
    private var artworkDirectBuffer: ByteBuffer? = null
    private val textRenderer = GlChannelScopeTextRenderer(context)

    // Per-tick render state (render thread only).
    private var localChannelCount = 0
    private var localChannelTextStates = emptyList<com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState>()
    private var localLastTextPollNs = 0L
    private var pausedFrameRendered = false
    private var lastTickNs = 0L
    private var projectMAttached = false

    // projectM frame-rate throttle (render thread only). The Choreographer loop
    // fires at vsync, so projectM must skip renders to honor the configured FPS;
    // projectm_set_fps only feeds the ctx.fps uniform and never gates rendering.
    private var projectMTargetFps = 0
    private var lastProjectMRenderNs = 0L

    fun setDynamicData(data: SiliconNativeGlDynamicData) {
        synchronized(lock) {
            dynamicData = data
            frameSequence += 1L
        }
    }

    fun setFrameData(frame: SiliconNativeGlFrame) {
        synchronized(lock) {
            frameData = frame
            frameSequence += 1L
        }
    }

    private data class LoopState(
        val frame: SiliconNativeGlFrame?,
        val dynamicData: SiliconNativeGlDynamicData?,
        val frameSequence: Long,
        val width: Int,
        val height: Int,
        val surfaceSizeChanged: Boolean
    )

    // SurfaceTexture BufferQueues run in async mode: eglSwapBuffers never
    // blocks, so frames must be scheduled against display vsync via
    // Choreographer rather than relying on swap backpressure or sleeps.
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) {
                Looper.myLooper()?.quitSafely()
                return
            }
            val nowNs = System.nanoTime()
            val gapMs = (nowNs - lastTickNs) / 1_000_000L
            if (lastTickNs != 0L && gapMs > 250L) {
                android.util.Log.i(
                    "SiliconVis",
                    "Vis render stall: ${gapMs} ms since previous frame"
                )
            }
            lastTickNs = nowNs
            try {
                renderTick()
            } finally {
                if (running) {
                    Choreographer.getInstance().postFrameCallback(this)
                } else {
                    Looper.myLooper()?.quitSafely()
                }
            }
        }
    }

    override fun run() {
        Looper.prepare()
        // Visualizer work must not be relegated to background-priority cores.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        if (!initEgl()) {
            releaseEgl()
            outputSurface.release()
            return
        }

        visHandle = VisualizerWarmCache.take()
        if (visHandle == 0L) {
            visHandle = SiliconVisNativeBridge.nativeCreate()
            if (visHandle != 0L) {
                com.flopster101.siliconplayer.NativeBridge.attachAudioEngineToVisualizer(visHandle)
            }
        }
        if (visHandle != 0L) {
            SiliconVisNativeBridge.nativeInitGl(visHandle)
            SiliconVisNativeBridge.nativeResize(visHandle, surfaceWidth, surfaceHeight, density)
        }

        try {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            Looper.loop()
        } finally {
            if (visHandle != 0L) {
                SiliconVisNativeBridge.nativeReleaseGl(visHandle)
                VisualizerWarmCache.put(visHandle)
                visHandle = 0L
            }
            releaseEgl()
            outputSurface.release()
        }
    }

    private fun renderTick() {
        val state = synchronized(lock) {
            LoopState(
                frame = frameData,
                dynamicData = dynamicData,
                frameSequence = frameSequence,
                width = surfaceWidth,
                height = surfaceHeight,
                surfaceSizeChanged = surfaceSizeChanged
            ).also {
                surfaceSizeChanged = false
            }
        }
        val frame = state.frame ?: return

        // Honor the configured projectM frame rate. The vsync-driven loop fires
        // faster than the target, so drop renders (and the swap) until the target
        // interval has elapsed. Paused frames and resizes always pass through.
        if (frame.mode == 100 && frame.isPlaying && projectMTargetFps > 0 && !state.surfaceSizeChanged) {
            val intervalNs = 1_000_000_000L / projectMTargetFps
            val nowNs = System.nanoTime()
            if (lastProjectMRenderNs != 0L && nowNs - lastProjectMRenderNs < intervalNs) {
                return
            }
            lastProjectMRenderNs = nowNs
        }

        if (!frame.isPlaying) {
            if (pausedFrameRendered && !state.surfaceSizeChanged) {
                return
            }
            pausedFrameRendered = true
        } else {
            pausedFrameRendered = false
        }

                if (visHandle != 0L) {
                    if (state.surfaceSizeChanged) {
                        SiliconVisNativeBridge.nativeResize(visHandle, state.width, state.height, density)
                    }

                    // 1. Set mode & artwork theme
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

                    if (frame.mode == 100 && !projectMAttached) {
                        val appContext = context.applicationContext
                        val prefs = appContext.getSharedPreferences(
                            "silicon_player_settings",
                            android.content.Context.MODE_PRIVATE
                        )
                        val enabledSets = ProjectMPresetSets.enabledSets(appContext, prefs)
                        if (enabledSets.isNotEmpty()) {
                            val setIds = enabledSets.map { it.id }.toTypedArray()
                            val setDirs = enabledSets.map { it.dir }.toTypedArray()
                            val randomStart = prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_RANDOM_START, true)
                            val (presetKeys, _) = try {
                                ProjectMPresetSets.indexedPresetKeys(appContext, prefs)
                            } catch (_: Throwable) {
                                emptyList<String>() to emptyList()
                            }
                            val savedPreset = prefs.getString("visualization_projectm_preset", null)
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
                            try {
                                val duration = prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_PRESET_DURATION_SECONDS, AppDefaults.Visualization.ProjectM.presetDurationSeconds.toString())?.toDoubleOrNull() ?: AppDefaults.Visualization.ProjectM.presetDurationSeconds
                                SiliconVisNativeBridge.nativeProjectMSetPresetDuration(duration)
                                SiliconVisNativeBridge.nativeProjectMSetHardCutEnabled(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_ENABLED, AppDefaults.Visualization.ProjectM.hardCutEnabled))
                                SiliconVisNativeBridge.nativeProjectMSetHardCutSensitivity(prefs.getFloat(AppPreferenceKeys.VISUALIZATION_PROJECTM_HARD_CUT_SENSITIVITY, AppDefaults.Visualization.ProjectM.hardCutSensitivity))
                                SiliconVisNativeBridge.nativeProjectMSetRotationRandom(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ROTATION_RANDOM, AppDefaults.Visualization.ProjectM.rotationRandom))
                                SiliconVisNativeBridge.nativeProjectMSetMeshSize(prefs.getInt(AppPreferenceKeys.VISUALIZATION_PROJECTM_MESH_SIZE, AppDefaults.Visualization.ProjectM.defaultMeshSize(appContext)))
                                SiliconVisNativeBridge.nativeProjectMSetAspectCorrection(prefs.getBoolean(AppPreferenceKeys.VISUALIZATION_PROJECTM_ASPECT_CORRECTION, AppDefaults.Visualization.ProjectM.aspectCorrection))
                                SiliconVisNativeBridge.nativeProjectMSetMaxResolution(
                                    VisualizationProjectMResolutionMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_RENDER_RESOLUTION, AppDefaults.Visualization.ProjectM.renderResolution.storageValue)).maxLongEdgePx
                                )
                                val fpsMode = VisualizationOscFpsMode.fromStorage(prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_FPS_MODE, AppDefaults.Visualization.ProjectM.fpsMode.storageValue))
                                val fps = when (fpsMode) {
                                    VisualizationOscFpsMode.Default -> 35
                                    VisualizationOscFpsMode.Fps60 -> 60
                                    VisualizationOscFpsMode.NativeRefresh -> 0
                                }
                                projectMTargetFps = fps
                                SiliconVisNativeBridge.nativeProjectMSetFps(fps)
                            } catch (_: Throwable) {}
                        }
                    }

                    // 1a. Font atlas (update when requested font or mode changes)
                    val fontKey = if (frame.mode == 3) "vumeters_sans" else "scope_${frame.textFont.storageValue}"
                    if (fontKey != lastTextFontKey) {
                        lastTextFontKey = fontKey
                        runCatching {
                            val tf = resolveTypeface(context, frame.mode, frame.textFont)
                            val uploadData = GlFontAtlas(typeface = tf).createAtlasUploadData()
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
                        }
                    }

                    // 1b. Artwork bitmap & Fallback icon
                    val art = frame.artworkBitmap
                    if (art !== lastArtworkBitmap) {
                        lastArtworkBitmap = art
                        if (art != null && !art.isRecycled) {
                            runCatching {
                                val safeArt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                    art.config == Bitmap.Config.HARDWARE
                                ) {
                                    art.copy(Bitmap.Config.ARGB_8888, false)
                                } else {
                                    art
                                } ?: art
                                if (!safeArt.isRecycled) {
                                    val size = safeArt.width * safeArt.height * 4
                                    var buf = artworkDirectBuffer
                                    if (buf == null || buf.capacity() < size) {
                                        buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                                        artworkDirectBuffer = buf
                                    }
                                    buf.clear()
                                    safeArt.copyPixelsToBuffer(buf)
                                    buf.flip()
                                    SiliconVisNativeBridge.nativeSetArtworkPixels(visHandle, buf, safeArt.width, safeArt.height)
                                }
                            }.onFailure {
                                SiliconVisNativeBridge.nativeClearArtwork(visHandle)
                            }
                        } else {
                            SiliconVisNativeBridge.nativeClearArtwork(visHandle)
                        }
                    }

                    if (frame.placeholderIconResId != 0 &&
                        (frame.placeholderIconResId != lastIconResId || frame.primaryColorArgb != lastIconTintArgb)
                    ) {
                        lastIconResId = frame.placeholderIconResId
                        lastIconTintArgb = frame.primaryColorArgb
                        val drawable = ContextCompat.getDrawable(context, frame.placeholderIconResId)
                        if (drawable != null) {
                            drawable.setTint(frame.primaryColorArgb)
                            val sizePx = 256
                            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                            val c = android.graphics.Canvas(bmp)
                            drawable.setBounds(0, 0, sizePx, sizePx)
                            drawable.draw(c)

                            val size = sizePx * sizePx * 4
                            var buf = iconDirectBuffer
                            if (buf == null || buf.capacity() < size) {
                                buf = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                                iconDirectBuffer = buf
                            }
                            buf.clear()
                            bmp.copyPixelsToBuffer(buf)
                            buf.flip()
                            bmp.recycle()
                            SiliconVisNativeBridge.nativeSetIconPixels(visHandle, buf, sizePx, sizePx)
                        }
                    }

                    // 2. Push audio / channels / VU levels
                    val d = state.dynamicData
                    val pcm = d?.pcm ?: frame.pcm
                    val pcmFrames = if (d != null && d.pcm != null) d.pcmFrames else frame.pcmFrames
                    val pcmChannels = if (d != null && d.pcm != null) d.pcmChannels else frame.pcmChannels
                    val pcmSampleRate = if (d != null && d.pcm != null) d.pcmSampleRate else frame.pcmSampleRate
                    val fft = d?.fft ?: frame.fft
                    val vuLevels = d?.vuLevels ?: frame.vuLevels
                    val flatData = d?.channelScopeFlatData ?: frame.channelScopeFlatData
                    val flatSamples = if (d != null && d.channelScopeFlatData != null) d.channelScopeSamplesPerChannel else frame.channelScopeSamplesPerChannel
                    val channelHistories = if (d != null && d.channelHistories.isNotEmpty()) d.channelHistories else frame.channelHistories
                    val channelTextStates = if (d != null && d.channelTextStates.isNotEmpty()) d.channelTextStates else frame.channelTextStates

                    pcm?.let {
                        SiliconVisNativeBridge.nativePushPcm(visHandle, it, pcmFrames, pcmChannels, pcmSampleRate)
                    }
                    fft?.let {
                        SiliconVisNativeBridge.nativePushFft(visHandle, it, it.size)
                    }
                    SiliconVisNativeBridge.nativeSetVuLevels(
                        visHandle,
                        vuLevels.getOrElse(0) { 0f },
                        vuLevels.getOrElse(1) { 0f }
                    )

                    if (flatData != null && flatSamples > 0) {
                        val channels = flatData.size / flatSamples
                        SiliconVisNativeBridge.nativePushChannelScopeAllHistories(
                            visHandle,
                            channels,
                            flatSamples,
                            flatData
                        )
                    } else if (channelHistories.isNotEmpty()) {
                        for (i in channelHistories.indices) {
                            val hist = channelHistories[i]
                            SiliconVisNativeBridge.nativePushChannelScopeHistory(visHandle, i, hist, hist.size)
                        }
                    }

                    // 3. Set options
                    when (frame.mode) {
                        4 -> { // Channel Scope
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
                                triggerMode = frame.channelScopeTriggerMode
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
                        3 -> { // VU Meters
                            SiliconVisNativeBridge.nativeSetVuMetersOptions(
                                handle = visHandle,
                                stereo = frame.vuStereo,
                                topPlacement = frame.vuTopPlacement,
                                smoothing = frame.vuSmoothingPercent / 100f,
                                fillColorArgb = frame.vuFillColorArgb,
                                trackColorArgb = frame.vuTrackColorArgb,
                                labelColorArgb = frame.vuLabelColorArgb
                            )
                        }
                    }

                    // 4. Render
                    val drawStartNs = System.nanoTime()
                    SiliconVisNativeBridge.nativeRender(visHandle)

                    // 4b. Draw GL Channel Scope text directly in OpenGL ES (100% GLES, zero Compose overlays!)
                    if (frame.mode == 4 && frame.channelScopeTextEnabled) {
                        val textNowNs = System.nanoTime()
                        if (textNowNs - localLastTextPollNs >= 100_000_000L || localChannelCount <= 0) {
                            localLastTextPollNs = textNowNs
                            val rawText = com.flopster101.siliconplayer.NativeBridge.getChannelScopeTextState(64)
                            if (rawText.isNotEmpty()) {
                                localChannelTextStates = com.flopster101.siliconplayer.ui.screens.parseChannelScopeTextStates(rawText)
                                localChannelCount = localChannelTextStates.size
                            }
                        }
                        if (localChannelCount > 0) {
                            val textFrame = GlChannelScopeTextFrame(
                                channelCount = localChannelCount,
                                channelTextStates = localChannelTextStates,
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
                                palette = frame.textPalette,
                                channelHistories = emptyList(),
                                vuEnabled = false
                            )
                            textRenderer.buildGeometry(textFrame, state.width.toFloat(), state.height.toFloat())
                            textRenderer.drawText(state.width.toFloat(), state.height.toFloat())
                        }
                    }

                    val drawEndNs = System.nanoTime()
                    val frameMs = ((drawEndNs - drawStartNs) / 1_000_000L).toInt().coerceAtLeast(0)
                    latestFrameMs = frameMs
                }

                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                val nowNs = System.nanoTime()
                drawFrameCount += 1
                val elapsedNs = nowNs - drawWindowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    latestDrawFps = ((drawFrameCount.toDouble() * 1_000_000_000.0) / elapsedNs.toDouble()).toInt().coerceAtLeast(0)
                    drawFrameCount = 0
                    drawWindowStartNs = nowNs
                }
                if (nowNs - lastHudPublishNs >= 350_000_000L) {
                    onFrameStats.invoke(latestDrawFps, latestFrameMs)
                    lastHudPublishNs = nowNs
                }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        synchronized(lock) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            surfaceSizeChanged = true
        }
    }

    fun requestStop() {
        running = false
    }

    private fun initEgl(): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] <= 0) {
            return false
        }
        val config = configs[0] ?: return false

        val contextAttribs = intArrayOf(
            // projectM requires a GLES 3 context; ES 2 content runs unmodified on it.
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            val fallbackAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, fallbackAttribs, 0)
        }
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, outputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false
        EGL14.eglSwapInterval(eglDisplay, 1)
        textRenderer.onSurfaceCreated()
        return true
    }

    private fun releaseEgl() {
        textRenderer.release()
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }
}

private fun resolveTypeface(context: Context, mode: Int, font: VisualizationChannelScopeTextFont): Typeface {
    if (mode == 3) {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL) ?: Typeface.SANS_SERIF
    }
    return when (font) {
        VisualizationChannelScopeTextFont.System -> Typeface.MONOSPACE
        VisualizationChannelScopeTextFont.RaccoonSerif -> ResourcesCompat.getFont(context, R.font.raccoon_serif_base) ?: Typeface.MONOSPACE
        VisualizationChannelScopeTextFont.RaccoonMono -> ResourcesCompat.getFont(context, R.font.raccoon_serif_mono) ?: Typeface.MONOSPACE
        VisualizationChannelScopeTextFont.RetroCuteMono -> ResourcesCompat.getFont(context, R.font.retro_pixel_cute_mono) ?: Typeface.MONOSPACE
        VisualizationChannelScopeTextFont.RetroThick -> ResourcesCompat.getFont(context, R.font.retro_pixel_thick) ?: Typeface.MONOSPACE
    }
}
