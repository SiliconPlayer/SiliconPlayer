package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import java.io.File

@Composable
internal fun placeholderArtworkIconForFile(
    file: File?,
    decoderName: String?,
    allowCurrentDecoderFallback: Boolean = true
): ImageVector {
    val resId = placeholderArtworkDrawableResIdForFile(file, decoderName)
    return ImageVector.vectorResource(resId)
}

internal fun placeholderArtworkDrawableResIdForFile(
    file: File?,
    decoderName: String?
): Int {
    val extension = file?.name?.let(::inferredPrimaryExtensionForName) ?: return R.drawable.ic_placeholder_music_note
    val decoderExtensionArtworkHints = buildDecoderExtensionArtworkHintMap()
    val effectiveDecoderName = decoderName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val resolvedHint =
        decoderArtworkHintForName(effectiveDecoderName)
            ?: file?.name?.let { resolveDecoderArtworkHintForFileName(it, decoderExtensionArtworkHints) }
    return when (resolvedHint) {
        DecoderArtworkHint.TrackedFile -> R.drawable.ic_placeholder_tracker_chip
        DecoderArtworkHint.GameFile -> R.drawable.ic_placeholder_gamepad
        null -> R.drawable.ic_placeholder_music_note
    }
}
