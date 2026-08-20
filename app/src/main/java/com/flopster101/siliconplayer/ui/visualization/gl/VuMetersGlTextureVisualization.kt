package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.view.Surface
import android.view.TextureView
import android.graphics.Typeface
import java.nio.FloatBuffer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flopster101.siliconplayer.VisualizationVuAnchor
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun VuMetersGlTextureVisualization(
    vuLevels: FloatArray,
    channelCount: Int,
    vuAnchor: VisualizationVuAnchor,
    vuColor: Color,
    vuLabelColor: Color,
    vuBackgroundColor: Color,
    backgroundFrame: GlArtworkBackgroundFrame? = null,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val horizontalPadPx = with(density) { 16.dp.toPx() }
    val verticalPadPx = with(density) { 12.dp.toPx() }
    val rowGapPx = with(density) { 8.dp.toPx() }
    val rowHeightPx = with(density) { 14.dp.toPx() }
    val labelWidthPx = with(density) { 44.dp.toPx() }
    val labelGapPx = with(density) { 4.dp.toPx() }
    var glView by remember { mutableStateOf<VuMetersGlTextureView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            VuMetersGlTextureView(context).also { glView = it }
        },
        update = { view ->
            view.onFrameStats = onFrameStats
            view.updateFrame(
                VuMetersGlTextureFrame(
                    vuLevels = vuLevels,
                    channelCount = channelCount,
                    anchor = vuAnchor,
                    vuColorArgb = vuColor.toArgb(),
                    labelColorArgb = vuLabelColor.toArgb(),
                    trackColorArgb = vuBackgroundColor.toArgb(),
                    horizontalPadPx = horizontalPadPx,
                    verticalPadPx = verticalPadPx,
                    rowGapPx = rowGapPx,
                    rowHeightPx = rowHeightPx,
                    labelWidthPx = labelWidthPx,
                    labelGapPx = labelGapPx,
                    density = density.density,
                    backgroundFrame = backgroundFrame
                )
            )
        }
    )

    DisposableEffect(lifecycleOwner, glView) {
        val view = glView
        if (view == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> view.setLifecyclePaused(false)
                    Lifecycle.Event.ON_PAUSE -> view.setLifecyclePaused(true)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                view.setLifecyclePaused(true)
                view.shutdown()
            }
        }
    }
}

private data class VuMetersGlTextureFrame(
    val vuLevels: FloatArray,
    val channelCount: Int,
    val anchor: VisualizationVuAnchor,
    val vuColorArgb: Int,
    val labelColorArgb: Int,
    val trackColorArgb: Int,
    val horizontalPadPx: Float,
    val verticalPadPx: Float,
    val rowGapPx: Float,
    val rowHeightPx: Float,
    val labelWidthPx: Float,
    val labelGapPx: Float,
    val density: Float,
    val backgroundFrame: GlArtworkBackgroundFrame? = null
)

private class VuMetersGlTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var renderThread: VuMetersTextureRenderThread? = null
    private var latestFrame: VuMetersGlTextureFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun updateFrame(frame: VuMetersGlTextureFrame) {
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
        stopRenderThread()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (!lifecyclePaused) startRenderThread(surface, width, height)
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
        val surface = Surface(surfaceTexture)
        val thread = VuMetersTextureRenderThread(
            outputSurface = surface,
            initialWidth = width.coerceAtLeast(1),
            initialHeight = height.coerceAtLeast(1),
            context = context,
            onFrameStats = { fps, frameMs -> post { onFrameStats?.invoke(fps, frameMs) } }
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
}

private class VuMetersTextureRenderThread(
    private val outputSurface: Surface,
    initialWidth: Int,
    initialHeight: Int,
    private val context: Context,
    private val onFrameStats: (fps: Int, frameMs: Int) -> Unit
) : Thread("VuMetersGlTextureRenderThread") {
    private val lock = Object()
    private var running = true
    private var frameData: VuMetersGlTextureFrame? = null
    private var frameSequence: Long = 0L
    private var renderedFrameSequence: Long = -1L
    private var surfaceWidth = initialWidth
    private var surfaceHeight = initialHeight
    private var surfaceSizeChanged = true

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private val renderer = VuMetersGlCoreRenderer(context)
    private data class LoopState(
        val frame: VuMetersGlTextureFrame?,
        val frameSequence: Long,
        val width: Int,
        val height: Int,
        val surfaceSizeChanged: Boolean,
        val shouldStop: Boolean
    )

    override fun run() {
        if (!initEgl()) {
            releaseEgl()
            outputSurface.release()
            return
        }
        try {
            while (true) {
                val state = synchronized(lock) {
                    while (
                        running &&
                        !surfaceSizeChanged &&
                        (frameData == null || frameSequence == renderedFrameSequence)
                    ) {
                        lock.wait()
                    }
                    if (!running) {
                        LoopState(
                            frame = null,
                            frameSequence = frameSequence,
                            width = 0,
                            height = 0,
                            surfaceSizeChanged = false,
                            shouldStop = true
                        )
                    } else {
                        LoopState(
                            frame = frameData,
                            frameSequence = frameSequence,
                            width = surfaceWidth,
                            height = surfaceHeight,
                            surfaceSizeChanged = surfaceSizeChanged,
                            shouldStop = false
                        ).also {
                            surfaceSizeChanged = false
                        }
                    }
                }
                if (state.shouldStop) break
                val frame = state.frame ?: continue
                if (state.surfaceSizeChanged) {
                    renderer.onSurfaceChanged(state.width, state.height)
                }
                renderer.drawFrame(frame)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                synchronized(lock) {
                    renderedFrameSequence = state.frameSequence
                }
            }
        } finally {
            releaseEgl()
            outputSurface.release()
        }
    }

    fun setFrameData(frame: VuMetersGlTextureFrame) {
        synchronized(lock) {
            frameData = frame
            frameSequence += 1L
            lock.notifyAll()
        }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        synchronized(lock) {
            surfaceWidth = width.coerceAtLeast(1)
            surfaceHeight = height.coerceAtLeast(1)
            surfaceSizeChanged = true
            lock.notifyAll()
        }
    }

    fun requestStop() {
        synchronized(lock) {
            running = false
            lock.notifyAll()
        }
    }

    private fun initEgl(): Boolean {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return false
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) return false
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)) return false
        val eglConfig = configs[0] ?: return false
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return false
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, outputSurface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) return false
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) return false
        EGL14.eglSwapInterval(eglDisplay, 1)
        renderer.onSurfaceCreated(onFrameStats)
        renderer.onSurfaceChanged(surfaceWidth, surfaceHeight)
        return true
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        }
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
        if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(eglDisplay)
    }
}

private class VuMetersGlCoreRenderer(private val context: Context) {
    private var program = 0
    private var positionHandle = -1
    private var colorHandle = -1
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    private val bgRenderer = GlArtworkBackgroundRenderer(context)
    private val fontAtlas = GlFontAtlas(typeface = Typeface.DEFAULT_BOLD, baseFontSizePx = 32f)
    private val textProgram = GlTextProgram()
    private val textBatch = GlTextBatchBuilder(256)
    private var textVertexBuffer: FloatBuffer? = null
    private var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null
    private var frameCount = 0
    private var frameWindowStartNs = 0L
    private var lastFrameNs = 0L

    fun onSurfaceCreated(onFrameStats: ((fps: Int, frameMs: Int) -> Unit)?) {
        this.onFrameStats = onFrameStats
        program = GlSimplePrimitives.createProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        bgRenderer.onSurfaceCreated()
        textProgram.init()
        fontAtlas.initGl()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    fun drawFrame(frame: VuMetersGlTextureFrame) {
        val width = surfaceWidth.toFloat()
        val height = surfaceHeight.toFloat()
        if (width <= 1f || height <= 1f) return

        if (frame.backgroundFrame != null) {
            bgRenderer.draw(frame.backgroundFrame, width, height)
        } else {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }
        GLES20.glUseProgram(program)

        val rows = if (frame.channelCount > 1) 2 else 1
        val contentHeight = (rows * frame.rowHeightPx) + ((rows - 1).coerceAtLeast(0) * frame.rowGapPx)
        val topY = when (frame.anchor) {
            VisualizationVuAnchor.Top -> frame.verticalPadPx
            VisualizationVuAnchor.Center -> ((height - contentHeight) * 0.5f).coerceAtLeast(frame.verticalPadPx)
            VisualizationVuAnchor.Bottom -> (height - frame.verticalPadPx - contentHeight).coerceAtLeast(frame.verticalPadPx)
        }
        val trackX = frame.horizontalPadPx + frame.labelWidthPx + frame.labelGapPx
        val trackWidth = (width - trackX - frame.horizontalPadPx).coerceAtLeast(1f)
        val trackRadius = (frame.rowHeightPx * 0.5f).coerceAtLeast(0f)

        for (idx in 0 until rows) {
            val y = topY + (idx * (frame.rowHeightPx + frame.rowGapPx))
            val raw = frame.vuLevels.getOrElse(idx) { 0f }.coerceIn(0f, 1f)
            val db = 20f * log10(raw.coerceAtLeast(0.0001f))
            val dbFloor = -58f
            val norm = ((db - dbFloor) / -dbFloor).coerceIn(0f, 1f)
            val value = norm.toDouble().pow(0.62).toFloat().coerceIn(0f, 1f)

            val trackVertices = GlSimplePrimitives.roundRectToTrianglesNdc(
                x = trackX,
                y = y,
                w = trackWidth,
                h = frame.rowHeightPx,
                radius = trackRadius,
                surfaceWidth = width,
                surfaceHeight = height
            )
            GlSimplePrimitives.drawTriangles(trackVertices, frame.trackColorArgb, positionHandle, colorHandle)

            val fillWidth = (trackWidth * value).coerceAtLeast(0f)
            val fillVertices = GlSimplePrimitives.roundRectToTrianglesNdc(
                x = trackX,
                y = y,
                w = fillWidth,
                h = frame.rowHeightPx,
                radius = trackRadius,
                surfaceWidth = width,
                surfaceHeight = height
            )
            GlSimplePrimitives.drawTriangles(fillVertices, frame.vuColorArgb, positionHandle, colorHandle)
        }

        // Draw OpenGL text labels ("Left" / "Right" / "Mono")
        textBatch.clear()
        val textScale = (11f * frame.density) / 32f
        val textHeight = fontAtlas.lineHeightPx * textScale
        val labelA = ((frame.labelColorArgb ushr 24) and 0xFF) / 255f
        val labelR = ((frame.labelColorArgb ushr 16) and 0xFF) / 255f
        val labelG = ((frame.labelColorArgb ushr 8) and 0xFF) / 255f
        val labelB = (frame.labelColorArgb and 0xFF) / 255f
        for (idx in 0 until rows) {
            val y = topY + (idx * (frame.rowHeightPx + frame.rowGapPx))
            val textY = y + (frame.rowHeightPx - textHeight) * 0.5f
            val label = if (rows > 1) {
                if (idx == 0) "Left" else "Right"
            } else {
                "Mono"
            }
            textBatch.addText(
                atlas = fontAtlas,
                text = label,
                startX = frame.horizontalPadPx,
                startY = textY,
                scale = textScale,
                r = labelR,
                g = labelG,
                b = labelB,
                a = labelA
            )
        }
        val vCount = textBatch.count
        if (vCount > 0) {
            textVertexBuffer = textBatch.uploadToBuffer(textVertexBuffer)
            textProgram.draw(
                buffer = textVertexBuffer!!,
                vertexCount = vCount,
                atlas = fontAtlas,
                surfaceWidth = width,
                surfaceHeight = height
            )
        }

        reportFrameStats()
    }

    private fun reportFrameStats() {
        val callback = onFrameStats ?: return
        val now = System.nanoTime()
        if (frameWindowStartNs == 0L) {
            frameWindowStartNs = now
            lastFrameNs = now
        }
        frameCount += 1
        val frameMs = if (lastFrameNs == 0L) 0 else ((now - lastFrameNs) / 1_000_000L).toInt().coerceAtLeast(0)
        lastFrameNs = now
        val elapsed = now - frameWindowStartNs
        if (elapsed >= 1_000_000_000L) {
            val fps = (frameCount * 1_000_000_000L / elapsed).toInt().coerceAtLeast(0)
            callback(fps, frameMs)
            frameWindowStartNs = now
            frameCount = 0
        }
    }
}
