package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flopster101.siliconplayer.LocalPlayerOverlayVisibility
import com.flopster101.siliconplayer.NativeBridge
import com.flopster101.siliconplayer.ui.visualization.channel.GlChannelScopeTextFrame
import com.flopster101.siliconplayer.ui.visualization.channel.parseChannelScopeTextStates
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder

private object VisualizerVkWarmCache {
    @Volatile private var cachedHandle: Long = 0L
    @Volatile private var cachedAt = 0L
    private const val DECAY_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private val decayRunnable = Runnable {
        synchronized(this) {
            if (cachedHandle != 0L && SystemClock.elapsedRealtime() - cachedAt >= DECAY_MS) {
                try { SiliconVisNativeBridge.nativeDestroy(cachedHandle) } catch (_: Throwable) {}
                cachedHandle = 0L
            }
        }
    }

    fun take(): Long = synchronized(this) {
        if (cachedHandle != 0L) {
            val now = SystemClock.elapsedRealtime()
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
        cachedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(decayRunnable)
        handler.postDelayed(decayRunnable, DECAY_MS)
    }
}

/**
 * SurfaceView Vulkan backend for visualizers, bypassing ANGLE and OpenGL ES
 * driver quirks by rendering and presenting directly to an ANativeWindow swapchain.
 */
@Composable
fun SiliconNativeVkSurfaceVisualization(
    frame: SiliconNativeGlFrame,
    cornerRadiusDp: Int = 0,
    veilColor: Color = Color.Black,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isVulkanSupported = remember {
        try {
            SiliconVisNativeBridge.nativeVulkanIsSupported()
        } catch (_: Throwable) {
            false
        }
    }

    if (!isVulkanSupported) {
        SiliconNativeGlSurfaceVisualization(
            frame = frame,
            cornerRadiusDp = cornerRadiusDp,
            veilColor = veilColor,
            onFrameStats = onFrameStats,
            modifier = modifier
        )
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val cornerRadiusPx = with(LocalDensity.current) {
        cornerRadiusDp.coerceAtLeast(0).dp.toPx()
    }
    var vkView by remember { mutableStateOf<SiliconNativeVkSurfaceView?>(null) }
    val overlayVisibility = LocalPlayerOverlayVisibility.current

    val isXclipse = remember { GpuDeviceDetector.isXclipse() }
    var surfaceMountAllowed by remember { mutableStateOf(!isXclipse) }

    LaunchedEffect(Unit) {
        if (isXclipse) {
            snapshotFlow { overlayVisibility() }.collect { v ->
                if (v < 0.05f) {
                    surfaceMountAllowed = false
                } else if (v >= 0.999f && !surfaceMountAllowed) {
                    delay(120)
                    surfaceMountAllowed = true
                }
            }
        }
    }

    LaunchedEffect(overlayVisibility) {
        snapshotFlow { overlayVisibility() }.collect { v ->
            vkView?.setMasterDim(1f - v.coerceIn(0f, 1f))
            vkView?.setDimColor(veilColor.red, veilColor.green, veilColor.blue)
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (!surfaceMountAllowed) {
                drawRect(veilColor)
                return@drawWithContent
            }
            val visibility = overlayVisibility().coerceIn(0f, 1f)
            if (cornerRadiusPx > 0f) {
                drawRoundRect(
                    Color.Transparent,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                    blendMode = BlendMode.Clear
                )
            } else {
                drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            }
            val veilAlpha = 1f - visibility
            if (veilAlpha > 0f) {
                drawRect(veilColor.copy(alpha = veilAlpha))
            }
        }
    ) {
        if (surfaceMountAllowed) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SiliconNativeVkSurfaceView(context, density, cornerRadiusPx).also { view ->
                        vkView = view
                    }
                },
                update = { view ->
                    view.cornerRadiusPx = cornerRadiusPx
                    view.onFrameStats = onFrameStats
                    view.updateFrame(frame)
                }
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> vkView?.setLifecyclePaused(true)
                Lifecycle.Event.ON_RESUME -> vkView?.setLifecyclePaused(false)
                Lifecycle.Event.ON_DESTROY -> vkView?.shutdown()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vkView?.shutdown()
            vkView = null
        }
    }
}

private class SiliconNativeVkSurfaceView(
    context: Context,
    private val density: Float,
    cornerRadiusPx: Float
) : SurfaceView(context), SurfaceHolder.Callback, SiliconNativeGlDataConsumer {
    private var renderThread: SiliconNativeVkRenderThread? = null
    private var latestFrame: SiliconNativeGlFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    @Volatile
    var cornerRadiusPx: Float = cornerRadiusPx
        set(value) {
            field = value
            updateOutline()
        }

    init {
        setZOrderOnTop(false)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        updateOutline()
        SiliconNativeGlDataSink.register(this)
    }

    private fun updateOutline() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (cornerRadiusPx > 0f) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                clipToOutline = true
            } else {
                clipToOutline = false
            }
        }
    }

    override fun pushDynamicData(data: SiliconNativeGlDynamicData) {
        renderThread?.setDynamicData(data)
    }

    fun updateFrame(frame: SiliconNativeGlFrame) {
        latestFrame = frame
        renderThread?.setFrameData(frame)
    }

    fun setMasterDim(dim: Float) {
        renderThread?.setMasterDim(dim)
    }

    fun setDimColor(red: Float, green: Float, blue: Float) {
        renderThread?.setDimColor(red, green, blue)
    }

    private var lastLifecyclePauseUptimeMs = 0L
    private var recreatedSincePause = false
    private var recreateInFlight = false
    private var lastRecreateUptimeMs = 0L

    fun setLifecyclePaused(paused: Boolean) {
        lifecyclePaused = paused
        if (paused) {
            lastLifecyclePauseUptimeMs = SystemClock.uptimeMillis()
            recreatedSincePause = false
            stopRenderThread()
        } else if (holder.surface.isValid) {
            startRenderThread(holder, width, height)
            postDelayed({ resyncSurfaceGeometry() }, 600)
        }
    }

    private fun resyncSurfaceGeometry(force: Boolean = false) {
        if (lifecyclePaused || !isAttachedToWindow || width <= 0 || height <= 0) return
        val now = SystemClock.uptimeMillis()
        if (now - lastRecreateUptimeMs < 800L) return
        val recentlyResumed = now - lastLifecyclePauseUptimeMs <= 5_000L
        if (!force && !(recentlyResumed && !recreatedSincePause)) return
        if (recentlyResumed) recreatedSincePause = true
        lastRecreateUptimeMs = now
        recreateInFlight = true
        scaleX = 1f
        scaleY = 1f
        visibility = GONE
        post { if (isAttachedToWindow) visibility = VISIBLE }
    }

    private var geometryCheckFrameCounter = 0
    private var geometryMismatchStreak = 0

    private val geometryWatchdog = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            if (++geometryCheckFrameCounter >= 20) {
                geometryCheckFrameCounter = 0
                val frame = holder.surfaceFrame
                if (!lifecyclePaused && width > 0 && height > 0 && holder.surface.isValid &&
                    (frame.width() != width || frame.height() != height)
                ) {
                    if (++geometryMismatchStreak >= 2) {
                        geometryMismatchStreak = 0
                        resyncSurfaceGeometry(force = true)
                    }
                } else {
                    geometryMismatchStreak = 0
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun shutdown() {
        SiliconNativeGlDataSink.unregister(this)
        stopRenderThread()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!lifecyclePaused) {
            startRenderThread(holder, width, height)
            if (!recreateInFlight) {
                postDelayed({ resyncSurfaceGeometry() }, 600)
                postDelayed({ resyncSurfaceGeometry() }, 1600)
            }
            recreateInFlight = false
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        updateOutline()
        renderThread?.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    private fun startRenderThread(holder: SurfaceHolder, width: Int, height: Int) {
        if (!holder.surface.isValid) return
        applySurfaceFrameRate(holder)
        if (!stopRenderThread()) return
        SiliconNativeGlDataSink.register(this)
        val thread = SiliconNativeVkRenderThread(
            context = context,
            outputSurface = holder.surface,
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

    private fun stopRenderThread(): Boolean {
        val thread = renderThread ?: return true
        renderThread = null
        clearSurfaceFrameRate(holder)
        thread.requestStop()
        runCatching { thread.join(2000L) }
        if (thread.isAlive) {
            Log.w("SiliconVisVk", "Vk render thread still alive after join")
            return false
        }
        return true
    }

    private fun applySurfaceFrameRate(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val refreshHz = display?.let { d ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                d.supportedModes.maxOfOrNull { it.refreshRate } ?: d.refreshRate
            } else {
                d.refreshRate
            }
        } ?: return
        runCatching {
            holder.surface.setFrameRate(refreshHz, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
        }
    }

    private fun clearSurfaceFrameRate(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            holder.surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SiliconNativeGlDataSink.register(this)
        Choreographer.getInstance().postFrameCallback(geometryWatchdog)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(geometryWatchdog)
        shutdown()
        super.onDetachedFromWindow()
    }
}

internal class SiliconNativeVkRenderThread(
    private val context: Context,
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val density: Float,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("SiliconNativeVkRenderThread") {
    private val lock = Object()

    @Volatile
    private var running = true

    private var frameData: SiliconNativeGlFrame? = null
    private var dynamicData: SiliconNativeGlDynamicData? = null
    private var frameSequence: Long = 0L
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

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
    private val textRenderer = GlChannelScopeTextRenderer(context, isGl = false)
    private var localChannelCount = 0
    private var localChannelTextStates = emptyList<com.flopster101.siliconplayer.ui.visualization.channel.ChannelScopeChannelTextState>()
    private var localLastTextPollNs = 0L
    private var pausedFrameRendered = false
    private var lastTickNs = 0L

    private var lastRenderedTrackKey: String? = null
    private var forceRenderUntilNs = 0L
    private var transitionActive = false
    private var transitionStartNs = 0L
    private var transitionPending = false
    private var transitionPendingSinceNs = 0L
    private var pendingDataSerial = -1L
    private var dataSerial = -1L
    private var capturedSerial = -1L
    private var dataChannelsAlive = false
    private var lastDataAlivePollNs = 0L
    private var scopeSceneHasLiveData = false
    private var hasRenderedAtLeastOneFrame = false

    @Volatile
    private var masterDim = 0f

    @Volatile
    private var dimRed = 0f

    @Volatile
    private var dimGreen = 0f

    @Volatile
    private var dimBlue = 0f

    fun setMasterDim(dim: Float) {
        masterDim = dim
    }

    fun setDimColor(red: Float, green: Float, blue: Float) {
        dimRed = red
        dimGreen = green
        dimBlue = blue
    }

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

    private data class LoopState(
        val frame: SiliconNativeGlFrame?,
        val dynamicData: SiliconNativeGlDynamicData?,
        val frameSequence: Long,
        val width: Int,
        val height: Int,
        val surfaceSizeChanged: Boolean
    )

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) {
                Looper.myLooper()?.quitSafely()
                return
            }
            val nowNs = System.nanoTime()
            val gapMs = (nowNs - lastTickNs) / 1_000_000L
            if (lastTickNs != 0L && gapMs > 250L) {
                Log.i("SiliconVisVk", "Vis vk render stall: ${gapMs} ms since previous frame")
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
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)

        visHandle = VisualizerVkWarmCache.take()
        if (visHandle == 0L) {
            visHandle = SiliconVisNativeBridge.nativeCreate()
            if (visHandle != 0L) {
                NativeBridge.attachAudioEngineToVisualizer(visHandle)
            }
        }

        if (visHandle == 0L) {
            outputSurface.release()
            return
        }

        val initOk = SiliconVisNativeBridge.nativeInitVulkan(visHandle, surfaceWidth, surfaceHeight, outputSurface)
        if (!initOk) {
            Log.w("SiliconVisVk", "nativeInitVulkan failed to initialize swapchain")
            SiliconVisNativeBridge.nativeDestroy(visHandle)
            visHandle = 0L
            outputSurface.release()
            return
        }

        SiliconVisNativeBridge.nativeResizeVulkan(visHandle, surfaceWidth, surfaceHeight, density)

        try {
            Choreographer.getInstance().postFrameCallback(frameCallback)
            Looper.loop()
        } finally {
            textRenderer.release()
            if (visHandle != 0L) {
                try {
                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                } catch (_: Throwable) {}
                SiliconVisNativeBridge.nativeReleaseVulkan(visHandle)
                VisualizerVkWarmCache.put(visHandle)
                visHandle = 0L
            }
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
        if (state.width <= 1 || state.height <= 1) return

        if (state.surfaceSizeChanged) {
            hasRenderedAtLeastOneFrame = false
            transitionActive = false
            transitionPending = false
            if (visHandle != 0L) {
                try {
                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                } catch (_: Throwable) {}
            }
        }

        val frameTrackKey = frame.trackKey
        val trackDetectNowNs = System.nanoTime()
        if (frame.mode == 4) {
            dataSerial = com.flopster101.siliconplayer.NativeBridge.getChannelScopeDataSerial()
            if (trackDetectNowNs - lastDataAlivePollNs >= 30_000_000L) {
                lastDataAlivePollNs = trackDetectNowNs
                dataChannelsAlive = runCatching {
                    com.flopster101.siliconplayer.NativeBridge.getChannelScopeTextState(1).isNotEmpty()
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
            frame.mode == 4 &&
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
                    if (visHandle != 0L) {
                        try {
                            SiliconVisNativeBridge.nativeTakeTransitionSnapshotVulkan(visHandle)
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
                    if (visHandle != 0L) {
                        try {
                            SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                        } catch (_: Throwable) {}
                    }
                    capturedSerial = dataSerial
                }
            }
        }

        if (!frame.isPlaying) {
            val isFadeMode = frame.mode == 1 || frame.mode == 2 || frame.mode == 3 || frame.mode == 5
            val fadeSettled = !isFadeMode || frame.visualAlpha <= 0.001f
            val transitionRendering =
                System.nanoTime() < forceRenderUntilNs || transitionActive || transitionPending
            if (pausedFrameRendered && !state.surfaceSizeChanged && fadeSettled && !transitionRendering) {
                return
            }
            pausedFrameRendered = true
        } else {
            pausedFrameRendered = false
        }

        if (visHandle != 0L) {
            if (state.surfaceSizeChanged) {
                SiliconVisNativeBridge.nativeResizeVulkan(visHandle, state.width, state.height, density)
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

            val art = frame.artworkBitmap
            if (art !== lastArtworkBitmap) {
                lastArtworkBitmap = art
                if (art != null && !art.isRecycled) {
                    runCatching {
                        val safeArt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
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

            when (frame.mode) {
                4 -> {
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
            }

            if (frame.mode == 4 && frame.channelScopeTextEnabled) {
                val textNowNs = System.nanoTime()
                if (textNowNs - localLastTextPollNs >= 20_000_000L || localChannelCount <= 0) {
                    localLastTextPollNs = textNowNs
                    val rawText = NativeBridge.getChannelScopeTextState(64)
                    if (rawText.isNotEmpty()) {
                        localChannelTextStates = parseChannelScopeTextStates(rawText)
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
                    val buf = textRenderer.vertexBuffer
                    val count = textRenderer.vertexCount
                    if (buf != null && count > 0) {
                        SiliconVisNativeBridge.nativeSetTextQuads(visHandle, buf, count)
                    } else {
                        SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                    }
                } else {
                    SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
                }
            } else {
                SiliconVisNativeBridge.nativeSetTextQuads(visHandle, null, 0)
            }

            var transitionOffsetX = 0f
            var transitionAlpha = 0f
            if (frame.mode == 4 && frame.channelScopeTrackTransition != 0) {
                if (transitionActive) {
                    val p = ((System.nanoTime() - transitionStartNs) / 750_000_000f).coerceIn(0f, 1f)
                    if (p >= 1f) {
                        transitionActive = false
                        capturedSerial = dataSerial
                        try {
                            SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                        } catch (_: Throwable) {}
                    } else {
                        val eased = p * p * (3f - 2f * p)
                        transitionOffsetX = if (frame.channelScopeTrackTransition == 1) {
                            -eased * state.width.toFloat()
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
                try {
                    SiliconVisNativeBridge.nativeReleaseTransitionSnapshotVulkan(visHandle)
                } catch (_: Throwable) {}
            }

            SiliconVisNativeBridge.nativeSetTransitionVulkan(visHandle, transitionOffsetX, transitionAlpha)

            val drawStartNs = System.nanoTime()
            SiliconVisNativeBridge.nativeRenderVulkan(visHandle)
            hasRenderedAtLeastOneFrame = true
            val drawEndNs = System.nanoTime()

            val frameMs = ((drawEndNs - drawStartNs) / 1_000_000L).toInt().coerceAtLeast(0)
            latestFrameMs = frameMs

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
    }
}
