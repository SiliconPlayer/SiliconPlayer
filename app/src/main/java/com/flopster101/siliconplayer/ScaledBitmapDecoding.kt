package com.flopster101.siliconplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal fun decodeScaledBitmapFromBytes(data: ByteArray, maxSize: Int = 1024): Bitmap? {
    if (data.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}

internal fun decodeScaledBitmapFromFile(file: File, maxSize: Int = 1024): Bitmap? {
    if (!file.exists() || !file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun calculateInSampleSize(width: Int, height: Int, maxSize: Int): Int {
    var sample = 1
    var currentWidth = width
    var currentHeight = height
    while (currentWidth > maxSize || currentHeight > maxSize) {
        currentWidth /= 2
        currentHeight /= 2
        sample *= 2
    }
    return sample.coerceAtLeast(1)
}
