package com.flopster101.siliconplayer.ui.visualization

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap

// Blurred-fill backdrop for non-square artwork. The thumbnail math here is
// a byte-exact port of native GlArtworkRenderer::buildBlurThumb
// (box-downscale plus one 3x3 pass), so the Compose canvas blurs
// identically to the GL one; only the final bitmap upload is platform
// code. Bilinear upscaling at draw time turns the thumbnail creamy with
// no RenderEffect API gating and no per-frame cost.
const val ARTWORK_BLUR_FILL_LONG_EDGE_PX = 48

fun artworkNeedsBlurFill(
    artworkWidth: Int,
    artworkHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float
): Boolean {
    if (artworkWidth <= 0 || artworkHeight <= 0) return false
    if (canvasWidth <= 0f || canvasHeight <= 0f) return false
    // IEEE division is correctly rounded, so mathematically equal ratios
    // compare equal; any difference leaves real bands, however thin.
    return (artworkWidth.toFloat() / artworkHeight) != (canvasWidth / canvasHeight)
}

data class BlurThumbPixels(
    val width: Int,
    val height: Int,
    val argb: IntArray
)

fun ImageBitmap.blurThumbPixels(): BlurThumbPixels? {
    if (width <= 0 || height <= 0) return null
    val pixelMap = runCatching { this@blurThumbPixels.toPixelMap() }.getOrNull() ?: return null
    val srcWidth = pixelMap.width
    val srcHeight = pixelMap.height
    if (srcWidth <= 0 || srcHeight <= 0) return null
    val src = runCatching {
        val buffer = pixelMap.buffer
        require(buffer.size >= srcWidth * srcHeight)
        buffer.copyOf(srcWidth * srcHeight)
    }.getOrNull() ?: return null
    return computeBlurThumbArgb(src, srcWidth, srcHeight)
}

fun computeBlurThumbArgb(srcArgb: IntArray, srcWidth: Int, srcHeight: Int): BlurThumbPixels? {
    if (srcArgb.isEmpty() || srcWidth <= 0 || srcHeight <= 0) return null
    if (srcArgb.size < srcWidth * srcHeight) return null
    val longEdge = maxOf(srcWidth, srcHeight)
    val thumbLong = minOf(ARTWORK_BLUR_FILL_LONG_EDGE_PX, longEdge)
    val dstWidth = maxOf(1, ((srcWidth.toLong() * thumbLong) / longEdge).toInt())
    val dstHeight = maxOf(1, ((srcHeight.toLong() * thumbLong) / longEdge).toInt())
    val dst = IntArray(dstWidth * dstHeight)
    for (y in 0 until dstHeight) {
        val y0 = ((y.toLong() * srcHeight) / dstHeight).toInt()
        val y1 = maxOf(y0 + 1, (((y + 1).toLong() * srcHeight) / dstHeight).toInt())
        for (x in 0 until dstWidth) {
            val x0 = ((x.toLong() * srcWidth) / dstWidth).toInt()
            val x1 = maxOf(x0 + 1, (((x + 1).toLong() * srcWidth) / dstWidth).toInt())
            var r = 0L
            var g = 0L
            var b = 0L
            var a = 0L
            for (sy in y0 until y1) {
                for (sx in x0 until x1) {
                    val pixel = srcArgb[sy * srcWidth + sx]
                    r += ((pixel ushr 16) and 0xFF).toLong()
                    g += ((pixel ushr 8) and 0xFF).toLong()
                    b += (pixel and 0xFF).toLong()
                    a += ((pixel ushr 24) and 0xFF).toLong()
                }
            }
            // Truncating division on non-negative sums, matching the native
            // int64 accumulator exactly.
            val n = (x1 - x0).toLong() * (y1 - y0)
            dst[y * dstWidth + x] = ((a / n).toInt() shl 24) or
                ((r / n).toInt() shl 16) or
                ((g / n).toInt() shl 8) or
                (b / n).toInt()
        }
    }
    // Melt the block edges the bilinear upscale would otherwise keep.
    val pre = dst.copyOf()
    for (y in 0 until dstHeight) {
        for (x in 0 until dstWidth) {
            var r = 0L
            var g = 0L
            var b = 0L
            var a = 0L
            var n = 0
            for (ky in -1..1) {
                for (kx in -1..1) {
                    val sx = x + kx
                    val sy = y + ky
                    if (sx < 0 || sy < 0 || sx >= dstWidth || sy >= dstHeight) continue
                    val pixel = pre[sy * dstWidth + sx]
                    r += ((pixel ushr 16) and 0xFF).toLong()
                    g += ((pixel ushr 8) and 0xFF).toLong()
                    b += (pixel and 0xFF).toLong()
                    a += ((pixel ushr 24) and 0xFF).toLong()
                    ++n
                }
            }
            dst[y * dstWidth + x] = ((a / n).toInt() shl 24) or
                ((r / n).toInt() shl 16) or
                ((g / n).toInt() shl 8) or
                (b / n).toInt()
        }
    }
    return BlurThumbPixels(dstWidth, dstHeight, dst)
}
