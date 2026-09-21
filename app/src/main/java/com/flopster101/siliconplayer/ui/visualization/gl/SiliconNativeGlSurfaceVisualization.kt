package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.flopster101.siliconplayer.LocalPlayerOverlayVisibility
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * SurfaceView twin of [SiliconNativeGlTextureVisualization], sharing its
 * [SiliconNativeTextureRenderThread]. Unlike a TextureView it is composited in
 * its own layer, so vis frames do not re-record the app's view tree.
 *
 * That layer sits behind the window, which keeps Compose content drawn over
 * the vis (player controls, sheets) on top of it. Nothing that the window
 * paints can cover the vis either, so its area is punched out of the window
 * buffer outright, and the overlay fade is painted back as a plain veil in
 * [veilColor]: the two cross-fade, since the overlay's own alpha cannot
 * reach across layers. No ancestor clip reaches across either, so
 * [cornerRadiusDp] is masked into the GL frame instead.
 *
 * Panel travel is a graphicsLayer transform, which moves the punch hole but
 * not the layout the surface layer derives its position from. The interop
 * layer offsets the embedded view to follow the transform, which does move
 * the surface, so the two stay aligned without a mirror. Adding one on top
 * double-counts the travel and the canvas overshoots its hole.
 */
@Composable
fun SiliconNativeGlSurfaceVisualization(
    frame: SiliconNativeGlFrame,
    cornerRadiusDp: Int = 0,
    veilColor: Color = Color.Black,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    val cornerRadiusPx = with(LocalDensity.current) {
        cornerRadiusDp.coerceAtLeast(0).dp.toPx()
    }
    var glView by remember { mutableStateOf<SiliconNativeGlSurfaceView?>(null) }
    val overlayVisibility = LocalPlayerOverlayVisibility.current

    LaunchedEffect(overlayVisibility) {
        snapshotFlow { overlayVisibility() }.collect { v ->
            // The surface fades itself out in lockstep with the veil: the
            // window fade multiplies how much of the layer behind it leaks
            // through, and a surface converging to the veil color leaves
            // nothing to leak.
            glView?.setMasterDim(1f - v.coerceIn(0f, 1f))
            glView?.setDimColor(veilColor.red, veilColor.green, veilColor.blue)
        }
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            val visibility = overlayVisibility().coerceIn(0f, 1f)
            // The window composites above the surface, so its pixels here
            // have to go or the card behind the vis would cover it. The
            // veil paints the fade back over the hole, and the surface
            // dims itself to the same color in lockstep, so every path
            // the compositor can show lands on the veil color.
            drawRect(Color.Transparent, blendMode = BlendMode.Clear)
            val veilAlpha = 1f - visibility
            if (veilAlpha > 0f) {
                drawRect(veilColor.copy(alpha = veilAlpha))
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SiliconNativeGlSurfaceView(context, density, cornerRadiusPx).also { view ->
                    glView = view
                }
            },
            update = { view ->
                view.cornerRadiusPx = cornerRadiusPx
                view.onFrameStats = onFrameStats
                view.updateFrame(frame)
            }
        )
    }

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

private class SiliconNativeGlSurfaceView(
    context: Context,
    private val density: Float,
    cornerRadiusPx: Float
) : SurfaceView(context), SurfaceHolder.Callback, SiliconNativeGlDataConsumer {
    private var renderThread: SiliconNativeTextureRenderThread? = null
    private var latestFrame: SiliconNativeGlFrame? = null
    private var lifecyclePaused: Boolean = false
    var onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null

    @Volatile
    var cornerRadiusPx: Float = cornerRadiusPx
        set(value) {
            field = value
            renderThread?.setCornerRadius(value)
        }

    init {
        // Behind the window: content drawn over the vis stays visible, at the
        // cost of the window having to punch the vis area out (see the caller).
        setZOrderOnTop(false)
        // Translucent keeps the pause fade compositing instead of flashing black.
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(this)
        SiliconNativeGlDataSink.register(this)
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

    fun setLifecyclePaused(paused: Boolean) {
        lifecyclePaused = paused
        if (paused) {
            stopRenderThread()
        } else if (holder.surface.isValid) {
            startRenderThread(holder, width, height)
        }
    }

    fun shutdown() {
        SiliconNativeGlDataSink.unregister(this)
        // Blank the surface before it dies: a released layer can linger a
        // frame in the compositor, and a transparent last buffer leaves
        // nothing to flash.
        stopRenderThread(blankFrameOnStop = true)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!lifecyclePaused) {
            startRenderThread(holder, width, height)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        renderThread?.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    private fun startRenderThread(holder: SurfaceHolder, width: Int, height: Int) {
        if (!holder.surface.isValid) return
        applySurfaceFrameRate(holder)
        // One render thread per surface; skip if the previous outlived the join.
        if (!stopRenderThread()) return
        SiliconNativeGlDataSink.register(this)
        val thread = SiliconNativeTextureRenderThread(
            context = context,
            outputSurface = holder.surface,
            initialWidth = width.coerceAtLeast(1),
            initialHeight = height.coerceAtLeast(1),
            density = density,
            cornerRadiusPx = cornerRadiusPx,
            onFrameStats = { fps, frameMs ->
                post { onFrameStats?.invoke(fps, frameMs) }
            }
        )
        renderThread = thread
        thread.start()
        latestFrame?.let { thread.setFrameData(it) }
    }

    /** Stops the render thread; false if it outlived the join. */
    private fun stopRenderThread(blankFrameOnStop: Boolean = false): Boolean {
        val thread = renderThread ?: return true
        renderThread = null
        clearSurfaceFrameRate(holder)
        if (blankFrameOnStop) {
            thread.requestStopWithBlankFrame()
        } else {
            thread.requestStop()
        }
        // Must exit before a replacement starts: both share the Surface.
        runCatching { thread.join(2000L) }
        if (thread.isAlive) {
            android.util.Log.w("SiliconVis", "Render thread still alive after join")
            return false
        }
        return true
    }

    /**
     * Votes for the display's fastest mode while the renderer runs: nothing
     * else holds the panel above 60 Hz once the window stops invalidating.
     */
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

    /** Drops the frame rate vote so an idle surface does not hold the panel up. */
    private fun clearSurfaceFrameRate(holder: SurfaceHolder) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            holder.surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
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

/**
 * Fades the whole frame toward the veil color. The surface sits behind the
 * window, so a fading window leaks whatever the surface holds through its
 * translucent pixels; the surface converging to the same color as the veil
 * keeps that leak continuous instead of snapping to black.
 */
internal class GlDimQuad {
    private var program = 0
    private var positionLoc = -1
    private var dimLoc = -1
    private var colorLoc = -1

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun draw(dim: Float, red: Float, green: Float, blue: Float) {
        if (!ensureProgram()) return
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glUniform1f(dimLoc, dim.coerceIn(0f, 1f))
        GLES20.glUniform3f(colorLoc, red, green, blue)
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glUseProgram(0)
    }

    private fun ensureProgram(): Boolean {
        if (program != 0) return true
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
            return false
        }
        val created = GLES20.glCreateProgram()
        GLES20.glAttachShader(created, vertexShader)
        GLES20.glAttachShader(created, fragmentShader)
        GLES20.glLinkProgram(created)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        val status = IntArray(1)
        GLES20.glGetProgramiv(created, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteProgram(created)
            return false
        }
        positionLoc = GLES20.glGetAttribLocation(created, "aPosition")
        dimLoc = GLES20.glGetUniformLocation(created, "uDim")
        colorLoc = GLES20.glGetUniformLocation(created, "uColor")
        program = created
        return positionLoc >= 0
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform float uDim;
            uniform vec3 uColor;
            void main() {
                gl_FragColor = vec4(uColor, uDim);
            }
        """
    }
}

/**
 * Clears the pixels outside a rounded rectangle. Vis pixels are multiplied by
 * zero, leaving them fully transparent so the window shows through.
 */
internal class GlRoundedClipMask {
    private var program = 0
    private var positionLoc = -1
    private var resolutionLoc = -1
    private var radiusLoc = -1

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(8 * Float.SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun draw(widthPx: Float, heightPx: Float, radiusPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        if (!ensureProgram()) return
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)
        GLES20.glUniform2f(resolutionLoc, widthPx, heightPx)
        GLES20.glUniform1f(radiusLoc, radiusPx.coerceAtMost(minOf(widthPx, heightPx) * 0.5f))
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ZERO)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glUseProgram(0)
    }

    private fun ensureProgram(): Boolean {
        if (program != 0) return true
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) GLES20.glDeleteShader(vertexShader)
            if (fragmentShader != 0) GLES20.glDeleteShader(fragmentShader)
            return false
        }
        val created = GLES20.glCreateProgram()
        GLES20.glAttachShader(created, vertexShader)
        GLES20.glAttachShader(created, fragmentShader)
        GLES20.glLinkProgram(created)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        val status = IntArray(1)
        GLES20.glGetProgramiv(created, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteProgram(created)
            return false
        }
        positionLoc = GLES20.glGetAttribLocation(created, "aPosition")
        resolutionLoc = GLES20.glGetUniformLocation(created, "uResolution")
        radiusLoc = GLES20.glGetUniformLocation(created, "uRadius")
        program = created
        return positionLoc >= 0
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private companion object {
        const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        // Signed distance to a rounded rect centred on the surface; inside
        // pixels are kept, everything else is cleared by the zero blend.
        const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec2 uResolution;
            uniform float uRadius;
            void main() {
                vec2 halfSize = uResolution * 0.5;
                vec2 p = abs(gl_FragCoord.xy - halfSize) - (halfSize - vec2(uRadius));
                float dist = length(max(p, 0.0)) + min(max(p.x, p.y), 0.0) - uRadius;
                if (dist < 0.0) discard;
                gl_FragColor = vec4(0.0);
            }
        """
    }
}
