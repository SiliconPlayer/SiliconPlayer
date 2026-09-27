package com.flopster101.siliconplayer

import android.content.Context
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.util.Locale

internal fun guessMimeTypeFromFilename(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension.isBlank()) return "application/octet-stream"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

internal fun defaultChannelScopeTextSizeSp(context: Context): Int {
    val tabletLike = context.resources.configuration.smallestScreenWidthDp >= 600
    return if (tabletLike) 10 else AppDefaults.Visualization.ChannelScope.textSizeSp
}

internal fun applyRepeatModeToNative(mode: RepeatMode) {
    NativeBridge.setRepeatMode(mode.nativeValue)
}

internal fun showRepeatModeToast(context: Context, mode: RepeatMode) {
    Toast.makeText(context, mode.label, Toast.LENGTH_SHORT).show()
}
