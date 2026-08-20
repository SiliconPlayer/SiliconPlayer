package com.flopster101.siliconplayer.ui.visualization.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.min

/**
 * Contrast backdrop darkening gradient mode for visualizations in OpenGL.
 */
enum class GlContrastBackdropMode {
    None,
    Bars,
    OscilloscopeMono,
    OscilloscopeStereo,
    VuMetersTop,
    VuMetersBottom,
    ChannelScope
}

/**
 * Configuration frame for universal OpenGL background, artwork, and contrast backdrop rendering.
 */
data class GlArtworkBackgroundFrame(
    val artworkBitmap: Bitmap? = null,
    val placeholderIconResId: Int = 0,
    val primaryColorArgb: Int = 0xFF4A90E2.toInt(),
    val surfaceVariantColorArgb: Int = 0xFF2A2D34.toInt(),
    val backgroundColorArgb: Int = 0xFF121418.toInt(),
    val showArtworkBackground: Boolean = true,
    val contrastBackdropMode: GlContrastBackdropMode = GlContrastBackdropMode.None,
    val density: Float = 1f
)

/**
 * Universal background renderer for all OpenGL visualizations.
 * Handles:
 * 1. Solid background mode when showArtworkBackground is false.
 * 2. Real song album artwork with aspect-fit centering.
 * 3. Default fallback artwork (radial gradient, subtle circular disc, and format vector icon).
 * 4. Contrast backdrop gradients (darkening overlays to enhance readability of waveforms/bars/meters).
 */
internal class GlArtworkBackgroundRenderer(private val context: Context) {
    private var bgProgramId: Int = 0
    private var bgPositionLoc: Int = -1
    private var bgResolutionLoc: Int = -1
    private var bgCenterColorLoc: Int = -1
    private var bgEdgeColorLoc: Int = -1
    private var bgCircleColorLoc: Int = -1
    private var bgCircleRadiusLoc: Int = -1

    private var texProgramId: Int = 0
    private var texPositionLoc: Int = -1
    private var texCoordLoc: Int = -1
    private var texResolutionLoc: Int = -1
    private var texColorLoc: Int = -1
    private var texSamplerLoc: Int = -1

    private var contrastProgramId: Int = 0
    private var contrastPositionLoc: Int = -1
    private var contrastCoordLoc: Int = -1
    private var contrastResolutionLoc: Int = -1
    private var contrastModeLoc: Int = -1

    private var quadVertexBuffer: FloatBuffer? = null

    private var artworkTextureId: Int = 0
    private var lastArtworkBitmap: Bitmap? = null

    private var iconTextureId: Int = 0
    private var lastIconResId: Int = 0
    private var lastIconTintArgb: Int = 0

    val isReady: Boolean
        get() = bgProgramId != 0 && texProgramId != 0

    fun onSurfaceCreated() {
        try {
            initBgProgram()
            initTexProgram()
            initContrastProgram()
        } catch (t: Throwable) {
            Log.e("GlArtworkBg", "Failed initializing GL artwork programs", t)
        }
    }

    private fun initBgProgram() {
        if (bgProgramId != 0) return
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, BG_VERTEX_SHADER)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, BG_FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vShader)
        GLES20.glAttachShader(prog, fShader)
        GLES20.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            Log.e("GlArtworkBg", "Bg program link failed:\n$log")
            GLES20.glDeleteProgram(prog)
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
            throw IllegalStateException("GlArtworkBackgroundRenderer bg program link error: $log")
        }
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)

        bgProgramId = prog
        bgPositionLoc = GLES20.glGetAttribLocation(prog, "aPosition")
        bgResolutionLoc = GLES20.glGetUniformLocation(prog, "uResolution")
        bgCenterColorLoc = GLES20.glGetUniformLocation(prog, "uCenterColor")
        bgEdgeColorLoc = GLES20.glGetUniformLocation(prog, "uEdgeColor")
        bgCircleColorLoc = GLES20.glGetUniformLocation(prog, "uCircleColor")
        bgCircleRadiusLoc = GLES20.glGetUniformLocation(prog, "uCircleRadiusPx")
    }

    private fun initTexProgram() {
        if (texProgramId != 0) return
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, TEX_VERTEX_SHADER)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, TEX_FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vShader)
        GLES20.glAttachShader(prog, fShader)
        GLES20.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            Log.e("GlArtworkBg", "Tex program link failed:\n$log")
            GLES20.glDeleteProgram(prog)
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
            throw IllegalStateException("GlArtworkBackgroundRenderer tex program link error: $log")
        }
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)

        texProgramId = prog
        texPositionLoc = GLES20.glGetAttribLocation(prog, "aPosition")
        texCoordLoc = GLES20.glGetAttribLocation(prog, "aTexCoord")
        texResolutionLoc = GLES20.glGetUniformLocation(prog, "uResolution")
        texColorLoc = GLES20.glGetUniformLocation(prog, "uColor")
        texSamplerLoc = GLES20.glGetUniformLocation(prog, "uTexture")
    }

    private fun initContrastProgram() {
        if (contrastProgramId != 0) return
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, CONTRAST_VERTEX_SHADER)
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, CONTRAST_FRAGMENT_SHADER)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vShader)
        GLES20.glAttachShader(prog, fShader)
        GLES20.glLinkProgram(prog)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(prog)
            Log.e("GlArtworkBg", "Contrast program link failed:\n$log")
            GLES20.glDeleteProgram(prog)
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
            return
        }
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)

        contrastProgramId = prog
        contrastPositionLoc = GLES20.glGetAttribLocation(prog, "aPosition")
        contrastCoordLoc = GLES20.glGetAttribLocation(prog, "aTexCoord")
        contrastResolutionLoc = GLES20.glGetUniformLocation(prog, "uResolution")
        contrastModeLoc = GLES20.glGetUniformLocation(prog, "uMode")
    }

    fun draw(frame: GlArtworkBackgroundFrame, surfaceWidth: Float, surfaceHeight: Float) {
        if (surfaceWidth <= 0f || surfaceHeight <= 0f) return

        if (!frame.showArtworkBackground) {
            drawSolidBackground(frame.backgroundColorArgb)
            return
        }

        val artwork = frame.artworkBitmap
        if (artwork != null && !artwork.isRecycled) {
            drawArtwork(artwork, frame, surfaceWidth, surfaceHeight)
        } else {
            drawFallback(frame, surfaceWidth, surfaceHeight)
        }

        if (frame.contrastBackdropMode != GlContrastBackdropMode.None) {
            drawContrastBackdrop(frame.contrastBackdropMode, surfaceWidth, surfaceHeight)
        }
    }

    private fun drawSolidBackground(colorArgb: Int) {
        val a = ((colorArgb ushr 24) and 0xFF) / 255f
        val r = ((colorArgb ushr 16) and 0xFF) / 255f
        val g = ((colorArgb ushr 8) and 0xFF) / 255f
        val b = (colorArgb and 0xFF) / 255f
        GLES20.glClearColor(r, g, b, a)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    }

    private fun drawArtwork(
        artwork: Bitmap,
        frame: GlArtworkBackgroundFrame,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        drawSolidBackground(frame.surfaceVariantColorArgb)

        if (!isReady) return

        ensureArtworkTexture(artwork)
        if (artworkTextureId == 0) return

        val imgW = artwork.width.toFloat().coerceAtLeast(1f)
        val imgH = artwork.height.toFloat().coerceAtLeast(1f)
        val scale = min(surfaceWidth / imgW, surfaceHeight / imgH)
        val dstW = imgW * scale
        val dstH = imgH * scale
        val dstX = (surfaceWidth - dstW) * 0.5f
        val dstY = (surfaceHeight - dstH) * 0.5f

        drawTexturedQuad(
            textureId = artworkTextureId,
            x = dstX,
            y = dstY,
            w = dstW,
            h = dstH,
            r = 1f,
            g = 1f,
            b = 1f,
            a = 1f,
            surfaceWidth = surfaceWidth,
            surfaceHeight = surfaceHeight
        )
    }

    private fun drawFallback(
        frame: GlArtworkBackgroundFrame,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        if (!isReady) {
            drawSolidBackground(frame.surfaceVariantColorArgb)
            return
        }

        val primArgb = frame.primaryColorArgb
        val primR = ((primArgb ushr 16) and 0xFF) / 255f
        val primG = ((primArgb ushr 8) and 0xFF) / 255f
        val primB = (primArgb and 0xFF) / 255f

        val surfArgb = frame.surfaceVariantColorArgb
        val surfR = ((surfArgb ushr 16) and 0xFF) / 255f
        val surfG = ((surfArgb ushr 8) and 0xFF) / 255f
        val surfB = (surfArgb and 0xFF) / 255f

        // Center color: blend 28% primary over surfaceVariant
        val centerR = (surfR * 0.72f) + (primR * 0.28f)
        val centerG = (surfG * 0.72f) + (primG * 0.28f)
        val centerB = (surfB * 0.72f) + (primB * 0.28f)

        // Circle disc: 120.dp diameter -> 60.dp radius
        val density = frame.density.coerceAtLeast(1f)
        val circleRadiusPx = min(60f * density, min(surfaceWidth, surfaceHeight) * 0.35f)

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // 1. Draw radial gradient background + circle disc
        GLES20.glUseProgram(bgProgramId)
        GLES20.glUniform2f(bgResolutionLoc, surfaceWidth, surfaceHeight)
        GLES20.glUniform4f(bgCenterColorLoc, centerR, centerG, centerB, 1f)
        GLES20.glUniform4f(bgEdgeColorLoc, surfR, surfG, surfB, 1f)
        GLES20.glUniform4f(bgCircleColorLoc, primR, primG, primB, 0.14f)
        GLES20.glUniform1f(bgCircleRadiusLoc, circleRadiusPx)

        val fullQuad = getQuadBuffer(0f, 0f, surfaceWidth, surfaceHeight)
        fullQuad.position(0)
        GLES20.glEnableVertexAttribArray(bgPositionLoc)
        GLES20.glVertexAttribPointer(bgPositionLoc, 2, GLES20.GL_FLOAT, false, 4 * 4, fullQuad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(bgPositionLoc)

        // 2. Draw centered placeholder icon
        if (frame.placeholderIconResId != 0) {
            ensureIconTexture(frame.placeholderIconResId, frame.primaryColorArgb)
            if (iconTextureId != 0) {
                val iconSizePx = min(72f * density, circleRadiusPx * 1.25f)
                val iconX = (surfaceWidth - iconSizePx) * 0.5f
                val iconY = (surfaceHeight - iconSizePx) * 0.5f

                drawTexturedQuad(
                    textureId = iconTextureId,
                    x = iconX,
                    y = iconY,
                    w = iconSizePx,
                    h = iconSizePx,
                    r = 1f,
                    g = 1f,
                    b = 1f,
                    a = 1f,
                    surfaceWidth = surfaceWidth,
                    surfaceHeight = surfaceHeight
                )
            }
        }
    }

    private fun drawTexturedQuad(
        textureId: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(texProgramId)
        GLES20.glUniform2f(texResolutionLoc, surfaceWidth, surfaceHeight)
        GLES20.glUniform4f(texColorLoc, r, g, b, a)
        GLES20.glUniform1i(texSamplerLoc, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

        val buffer = getQuadBuffer(x, y, w, h)
        val stride = 4 * 4 // 4 floats (2 pos + 2 uv)

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(texPositionLoc)
        GLES20.glVertexAttribPointer(texPositionLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(2)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(texPositionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun drawContrastBackdrop(
        mode: GlContrastBackdropMode,
        surfaceWidth: Float,
        surfaceHeight: Float
    ) {
        if (mode == GlContrastBackdropMode.None || contrastProgramId == 0) return

        val modeInt = when (mode) {
            GlContrastBackdropMode.None -> 0
            GlContrastBackdropMode.Bars -> 1
            GlContrastBackdropMode.OscilloscopeMono -> 2
            GlContrastBackdropMode.OscilloscopeStereo -> 3
            GlContrastBackdropMode.VuMetersTop -> 4
            GlContrastBackdropMode.VuMetersBottom -> 5
            GlContrastBackdropMode.ChannelScope -> 6
        }
        if (modeInt == 0) return

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glUseProgram(contrastProgramId)
        GLES20.glUniform2f(contrastResolutionLoc, surfaceWidth, surfaceHeight)
        GLES20.glUniform1i(contrastModeLoc, modeInt)

        val buffer = getQuadBuffer(0f, 0f, surfaceWidth, surfaceHeight)
        val stride = 4 * 4

        buffer.position(0)
        GLES20.glEnableVertexAttribArray(contrastPositionLoc)
        GLES20.glVertexAttribPointer(contrastPositionLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(2)
        GLES20.glEnableVertexAttribArray(contrastCoordLoc)
        GLES20.glVertexAttribPointer(contrastCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(contrastPositionLoc)
        GLES20.glDisableVertexAttribArray(contrastCoordLoc)
    }

    private fun getQuadBuffer(x: Float, y: Float, w: Float, h: Float): FloatBuffer {
        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h

        val data = floatArrayOf(
            x0, y0, 0f, 0f,
            x1, y0, 1f, 0f,
            x0, y1, 0f, 1f,
            x1, y0, 1f, 0f,
            x1, y1, 1f, 1f,
            x0, y1, 0f, 1f
        )

        var buf = quadVertexBuffer
        if (buf == null || buf.capacity() < data.size) {
            buf = ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
            quadVertexBuffer = buf
        }
        buf.clear()
        buf.put(data)
        buf.position(0)
        return buf
    }

    private fun ensureArtworkTexture(bitmap: Bitmap) {
        if (lastArtworkBitmap === bitmap && artworkTextureId != 0) return
        if (artworkTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(artworkTextureId), 0)
            artworkTextureId = 0
        }
        val safeBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        ) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        } ?: bitmap

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        artworkTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, artworkTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, safeBitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        lastArtworkBitmap = bitmap
    }

    private fun ensureIconTexture(drawableResId: Int, tintArgb: Int) {
        if (lastIconResId == drawableResId && lastIconTintArgb == tintArgb && iconTextureId != 0) return
        if (iconTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(iconTextureId), 0)
            iconTextureId = 0
        }
        val drawable = ContextCompat.getDrawable(context, drawableResId)?.mutate()
        if (drawable == null) {
            Log.e("GlArtworkBg", "Failed to get drawable from resId: $drawableResId")
            return
        }
        drawable.setTint(tintArgb)
        val sizePx = 256
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(c)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        iconTextureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, iconTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bmp.recycle()

        lastIconResId = drawableResId
        lastIconTintArgb = tintArgb
    }

    fun release() {
        if (artworkTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(artworkTextureId), 0)
            artworkTextureId = 0
        }
        lastArtworkBitmap = null

        if (iconTextureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(iconTextureId), 0)
            iconTextureId = 0
        }
        lastIconResId = 0
        lastIconTintArgb = 0

        if (bgProgramId != 0) {
            GLES20.glDeleteProgram(bgProgramId)
            bgProgramId = 0
        }
        if (texProgramId != 0) {
            GLES20.glDeleteProgram(texProgramId)
            texProgramId = 0
        }
        if (contrastProgramId != 0) {
            GLES20.glDeleteProgram(contrastProgramId)
            contrastProgramId = 0
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e("GlArtworkBg", "Shader compile failed ($type):\n$log\nSource:\n$source")
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile error: $log")
        }
        return shader
    }

    companion object {
        private const val BG_VERTEX_SHADER = """
            precision mediump float;
            attribute vec2 aPosition;
            uniform vec2 uResolution;
            varying vec2 vPixelCoord;
            void main() {
                vec2 zeroToOne = aPosition / uResolution;
                vec2 zeroToTwo = zeroToOne * 2.0;
                vec2 clipSpace = zeroToTwo - 1.0;
                gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
                vPixelCoord = aPosition;
            }
        """

        private const val BG_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vPixelCoord;
            uniform vec2 uResolution;
            uniform vec4 uCenterColor;
            uniform vec4 uEdgeColor;
            uniform vec4 uCircleColor;
            uniform float uCircleRadiusPx;

            void main() {
                vec2 center = uResolution * 0.5;
                float maxDist = length(center);
                float distFromCenter = length(vPixelCoord - center);

                // Radial gradient
                float gradT = clamp(distFromCenter / max(maxDist, 1.0), 0.0, 1.0);
                vec4 bg = mix(uCenterColor, uEdgeColor, gradT);

                // Centered circle with smooth anti-aliased edge
                float edge = 1.5;
                float circleAlpha = 1.0 - smoothstep(uCircleRadiusPx - edge, uCircleRadiusPx + edge, distFromCenter);
                vec4 color = mix(bg, vec4(uCircleColor.rgb, 1.0), uCircleColor.a * circleAlpha);

                gl_FragColor = color;
            }
        """

        private const val TEX_VERTEX_SHADER = """
            precision mediump float;
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform vec2 uResolution;
            varying vec2 vTexCoord;
            void main() {
                vec2 zeroToOne = aPosition / uResolution;
                vec2 zeroToTwo = zeroToOne * 2.0;
                vec2 clipSpace = zeroToTwo - 1.0;
                gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val TEX_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform vec4 uColor;
            uniform sampler2D uTexture;
            void main() {
                vec4 tex = texture2D(uTexture, vTexCoord);
                gl_FragColor = vec4(tex.rgb * uColor.rgb, tex.a * uColor.a);
            }
        """

        private const val CONTRAST_VERTEX_SHADER = """
            precision mediump float;
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            uniform vec2 uResolution;
            varying vec2 vTexCoord;
            void main() {
                vec2 zeroToOne = aPosition / uResolution;
                vec2 zeroToTwo = zeroToOne * 2.0;
                vec2 clipSpace = zeroToTwo - 1.0;
                gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        private const val CONTRAST_FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform int uMode; // 1=Bars, 2=OscMono, 3=OscStereo, 4=VuTop, 5=VuBottom, 6=ChannelScope

            void main() {
                float alpha = 0.0;
                float y = vTexCoord.y;
                float x = vTexCoord.x;

                if (uMode == 1) {
                    // Bars: bottom-up darkening
                    if (y < 0.45) {
                        alpha = mix(0.10, 0.28, y / 0.45);
                    } else {
                        alpha = mix(0.28, 0.55, (y - 0.45) / 0.55);
                    }
                } else if (uMode == 2) {
                    // Osc Mono: center line darkening around 0.50
                    float dist = abs(y - 0.50);
                    alpha = mix(0.42, 0.08, clamp(dist / 0.50, 0.0, 1.0));
                } else if (uMode == 3) {
                    // Osc Stereo: dual lane darkening around 0.25 and 0.75
                    float distL = abs(y - 0.25);
                    float distR = abs(y - 0.75);
                    float minDist = min(distL, distR);
                    alpha = mix(0.42, 0.08, clamp(minDist / 0.25, 0.0, 1.0));
                } else if (uMode == 4) {
                    // VU Top: top darkening
                    if (y < 0.42) {
                        alpha = mix(0.48, 0.24, y / 0.42);
                    } else {
                        alpha = mix(0.24, 0.08, (y - 0.42) / 0.58);
                    }
                } else if (uMode == 5) {
                    // VU Bottom: bottom darkening
                    if (y < 0.58) {
                        alpha = mix(0.08, 0.24, y / 0.58);
                    } else {
                        alpha = mix(0.24, 0.48, (y - 0.58) / 0.42);
                    }
                } else if (uMode == 6) {
                    // Channel Scope: center horizontal darkening
                    float dist = abs(x - 0.50);
                    alpha = mix(0.30, 0.22, clamp(dist / 0.50, 0.0, 1.0));
                }

                gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
            }
        """
    }
}
