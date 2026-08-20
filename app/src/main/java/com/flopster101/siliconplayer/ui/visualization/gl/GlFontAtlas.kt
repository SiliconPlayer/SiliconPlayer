package com.flopster101.siliconplayer.ui.visualization.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.max

/**
 * High-performance glyph atlas and batched text renderer for OpenGL ES 2.0.
 */
internal class GlFontAtlas(
    private val typeface: Typeface = Typeface.MONOSPACE,
    val baseFontSizePx: Float = 32f
) {
    data class Glyph(
        val char: Char,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val widthPx: Float,
        val heightPx: Float,
        val advanceX: Float,
        val ascentPx: Float
    )

    private var textureId: Int = 0
    private val asciiGlyphs = arrayOfNulls<Glyph>(128)
    private val extendedGlyphs = HashMap<Char, Glyph>()
    private var fallbackGlyph: Glyph? = null
    var lineHeightPx: Float = baseFontSizePx * 1.2f
        private set

    fun initGl() {
        if (textureId != 0) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = this@GlFontAtlas.typeface
            this.textSize = baseFontSizePx
            this.color = 0xFFFFFFFF.toInt()
        }

        val fontMetrics = paint.fontMetrics
        val fontAscent = -fontMetrics.ascent
        val fontDescent = fontMetrics.descent
        lineHeightPx = fontAscent + fontDescent

        // Character set: ASCII 32..126 plus common tracker notation symbols
        val chars = ArrayList<Char>(160)
        for (c in 32..126) {
            chars.add(c.toChar())
        }
        val extraChars = charArrayOf(
            '▲', '▼', '◄', '►', '■', '□', '▪', '▫',
            '│', '─', '┌', '┐', '└', '┘', '├', '┤', '┬', '┴', '┼',
            '°', '±', '·', '…', '♯', '♭'
        )
        for (c in extraChars) {
            chars.add(c)
        }

        val padding = 2
        val cellW = ceil(paint.measureText("W") + (padding * 2)).toInt()
        val cellH = ceil(lineHeightPx + (padding * 2)).toInt()
        val cols = 16
        val rows = ceil(chars.size.toDouble() / cols.toDouble()).toInt()
        val atlasW = 512
        val atlasH = max(256, (rows * cellH + 31) / 32 * 32)

        val bitmap = Bitmap.createBitmap(atlasW, atlasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = Rect()

        var col = 0
        var row = 0
        for (ch in chars) {
            val str = ch.toString()
            paint.getTextBounds(str, 0, str.length, rect)
            val advance = paint.measureText(str)

            val x = col * cellW + padding
            val y = row * cellH + padding
            val drawY = y + fontAscent

            canvas.drawText(str, x.toFloat(), drawY, paint)

            val u0 = x.toFloat() / atlasW.toFloat()
            val v0 = y.toFloat() / atlasH.toFloat()
            val u1 = (x + cellW - padding).toFloat() / atlasW.toFloat()
            val v1 = (y + cellH - padding).toFloat() / atlasH.toFloat()

            val glyph = Glyph(
                char = ch,
                u0 = u0,
                v0 = v0,
                u1 = u1,
                v1 = v1,
                widthPx = (cellW - padding).toFloat(),
                heightPx = (cellH - padding).toFloat(),
                advanceX = advance,
                ascentPx = fontAscent
            )

            if (ch.code in 0..127) {
                asciiGlyphs[ch.code] = glyph
            } else {
                extendedGlyphs[ch] = glyph
            }

            if (ch == '?') {
                fallbackGlyph = glyph
            }

            col++
            if (col >= cols) {
                col = 0
                row++
            }
        }

        if (fallbackGlyph == null) {
            fallbackGlyph = asciiGlyphs[' '.code]
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
    }

    fun getGlyph(ch: Char): Glyph? {
        val code = ch.code
        if (code in 0..127) {
            return asciiGlyphs[code] ?: fallbackGlyph
        }
        return extendedGlyphs[ch] ?: fallbackGlyph
    }

    fun measureTextWidth(text: String, scale: Float): Float {
        var width = 0f
        for (i in 0 until text.length) {
            val glyph = getGlyph(text[i])
            if (glyph != null) {
                width += glyph.advanceX * scale
            }
        }
        return width
    }

    fun bindTexture(textureUnit: Int = GLES20.GL_TEXTURE0) {
        GLES20.glActiveTexture(textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    }

    fun release() {
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
    }
}

/**
 * Batched dynamic vertex buffer for rendering glyph quads with colors.
 * Format per vertex (8 floats): [x, y, u, v, r, g, b, a]
 */
internal class GlTextBatchBuilder(initialQuadCapacity: Int = 512) {
    // 6 vertices per quad * 8 floats per vertex = 48 floats per quad
    private var data = FloatArray(initialQuadCapacity.coerceAtLeast(64) * 48)
    private var vertexCount = 0

    val count: Int
        get() = vertexCount

    fun clear() {
        vertexCount = 0
    }

    fun addText(
        atlas: GlFontAtlas,
        text: String,
        startX: Float,
        startY: Float,
        scale: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        shadow: Boolean = false,
        shadowR: Float = 0f,
        shadowG: Float = 0f,
        shadowB: Float = 0f,
        shadowA: Float = 0.75f,
        shadowOffsetPx: Float = 1.5f
    ): Float {
        var cursorX = startX
        if (shadow && a > 0f) {
            var sX = startX + shadowOffsetPx
            val sY = startY + shadowOffsetPx
            for (i in 0 until text.length) {
                val ch = text[i]
                val glyph = atlas.getGlyph(ch) ?: continue
                addGlyphQuad(glyph, sX, sY, scale, shadowR, shadowG, shadowB, a * shadowA)
                sX += glyph.advanceX * scale
            }
        }

        for (i in 0 until text.length) {
            val ch = text[i]
            val glyph = atlas.getGlyph(ch) ?: continue
            addGlyphQuad(glyph, cursorX, startY, scale, r, g, b, a)
            cursorX += glyph.advanceX * scale
        }
        return cursorX - startX
    }

    private fun addGlyphQuad(
        glyph: GlFontAtlas.Glyph,
        x: Float,
        y: Float,
        scale: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        val w = glyph.widthPx * scale
        val h = glyph.heightPx * scale
        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h
        val u0 = glyph.u0
        val v0 = glyph.v0
        val u1 = glyph.u1
        val v1 = glyph.v1

        ensureCapacity(48)
        val offset = vertexCount * 8

        // Triangle 1: (x0, y0), (x1, y0), (x0, y1)
        data[offset + 0] = x0; data[offset + 1] = y0; data[offset + 2] = u0; data[offset + 3] = v0
        data[offset + 4] = r;  data[offset + 5] = g;  data[offset + 6] = b;  data[offset + 7] = a

        data[offset + 8] = x1; data[offset + 9] = y0; data[offset + 10] = u1; data[offset + 11] = v0
        data[offset + 12] = r; data[offset + 13] = g; data[offset + 14] = b;  data[offset + 15] = a

        data[offset + 16] = x0; data[offset + 17] = y1; data[offset + 18] = u0; data[offset + 19] = v1
        data[offset + 20] = r;  data[offset + 21] = g;  data[offset + 22] = b;  data[offset + 23] = a

        // Triangle 2: (x1, y0), (x1, y1), (x0, y1)
        data[offset + 24] = x1; data[offset + 25] = y0; data[offset + 26] = u1; data[offset + 27] = v0
        data[offset + 28] = r;  data[offset + 29] = g;  data[offset + 30] = b;  data[offset + 31] = a

        data[offset + 32] = x1; data[offset + 33] = y1; data[offset + 34] = u1; data[offset + 35] = v1
        data[offset + 36] = r;  data[offset + 37] = g;  data[offset + 38] = b;  data[offset + 39] = a

        data[offset + 40] = x0; data[offset + 41] = y1; data[offset + 42] = u0; data[offset + 43] = v1
        data[offset + 44] = r;  data[offset + 45] = g;  data[offset + 46] = b;  data[offset + 47] = a

        vertexCount += 6
    }

    private fun ensureCapacity(neededFloats: Int) {
        val currentFloats = vertexCount * 8
        val required = currentFloats + neededFloats
        if (required <= data.size) return
        var newSize = data.size * 2
        while (newSize < required) {
            newSize *= 2
        }
        data = data.copyOf(newSize)
    }

    fun uploadToBuffer(targetBuffer: FloatBuffer?): FloatBuffer {
        val totalFloats = vertexCount * 8
        val buffer = if (targetBuffer != null && targetBuffer.capacity() >= totalFloats) {
            targetBuffer
        } else {
            ByteBuffer.allocateDirect(totalFloats * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
        }
        buffer.clear()
        buffer.put(data, 0, totalFloats)
        buffer.position(0)
        return buffer
    }
}

/**
 * Shader program for rendering batched textured glyphs with per-vertex RGBA color.
 */
internal class GlTextProgram {
    private var programId: Int = 0
    private var positionLoc: Int = -1
    private var texCoordLoc: Int = -1
    private var colorLoc: Int = -1
    private var resolutionLoc: Int = -1
    private var textureLoc: Int = -1

    val isReady: Boolean
        get() = programId != 0

    fun init() {
        if (programId != 0) return
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE)
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            programId = 0
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            throw IllegalStateException("GlTextProgram link error: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        positionLoc = GLES20.glGetAttribLocation(programId, "aPosition")
        texCoordLoc = GLES20.glGetAttribLocation(programId, "aTexCoord")
        colorLoc = GLES20.glGetAttribLocation(programId, "aColor")
        resolutionLoc = GLES20.glGetUniformLocation(programId, "uResolution")
        textureLoc = GLES20.glGetUniformLocation(programId, "uTexture")
    }

    fun draw(buffer: FloatBuffer, vertexCount: Int, atlas: GlFontAtlas, surfaceWidth: Float, surfaceHeight: Float) {
        if (programId == 0 || vertexCount <= 0) return

        GLES20.glUseProgram(programId)
        GLES20.glUniform2f(resolutionLoc, surfaceWidth, surfaceHeight)
        GLES20.glUniform1i(textureLoc, 0)
        atlas.bindTexture(GLES20.GL_TEXTURE0)

        val stride = 8 * 4 // 8 floats * 4 bytes
        buffer.position(0)
        GLES20.glEnableVertexAttribArray(positionLoc)
        GLES20.glVertexAttribPointer(positionLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(2)
        GLES20.glEnableVertexAttribArray(texCoordLoc)
        GLES20.glVertexAttribPointer(texCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        buffer.position(4)
        GLES20.glEnableVertexAttribArray(colorLoc)
        GLES20.glVertexAttribPointer(colorLoc, 4, GLES20.GL_FLOAT, false, stride, buffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionLoc)
        GLES20.glDisableVertexAttribArray(texCoordLoc)
        GLES20.glDisableVertexAttribArray(colorLoc)
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
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
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader compile error: $log")
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER_SOURCE = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            attribute vec4 aColor;
            uniform vec2 uResolution;
            varying vec2 vTexCoord;
            varying vec4 vColor;
            void main() {
                vec2 zeroToOne = aPosition / uResolution;
                vec2 zeroToTwo = zeroToOne * 2.0;
                vec2 clipSpace = zeroToTwo - 1.0;
                gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
                vTexCoord = aTexCoord;
                vColor = aColor;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE = """
            precision mediump float;
            varying vec2 vTexCoord;
            varying vec4 vColor;
            uniform sampler2D uTexture;
            void main() {
                float alpha = texture2D(uTexture, vTexCoord).a;
                gl_FragColor = vec4(vColor.rgb, vColor.a * alpha);
            }
        """
    }
}
