package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Vulkan swapchain composited inside the window (no overlay punch hole).
 * Reuses the SurfaceView render thread: only the window differs.
 */
@Composable
fun SiliconNativeVkTextureVisualization(
    frame: SiliconNativeGlFrame,
    onFrameStats: ((fps: Int, frameMs: Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    var vkView: SiliconNativeVkTextureView? = null

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SiliconNativeVkTextureView(context, density).also { view ->
                vkView = view
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

private class SiliconNativeVkTextureView(
    context: Context,
    private val density: Float
) : TextureView(context), TextureView.SurfaceTextureListener, SiliconNativeGlDataConsumer {
    private var renderThread: SiliconNativeVkRenderThread? = null
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
        if (!stopRenderThread()) return
        SiliconNativeGlDataSink.register(this)
        val thread = SiliconNativeVkRenderThread(
            context = context,
            outputSurface = Surface(surfaceTexture),
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
        thread.requestStop()
        runCatching { thread.join(2000L) }
        if (thread.isAlive) {
            android.util.Log.w("SiliconVis", "Vk render thread still alive after join")
            return false
        }
        return true
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
