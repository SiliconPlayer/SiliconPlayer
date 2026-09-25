package com.flopster101.siliconplayer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import java.io.File

@Composable
internal fun placeholderArtworkIconForFile(
    file: File?,
    decoderName: String?,
    allowCurrentDecoderFallback: Boolean = true
): ImageVector {
    val decoderExtensionArtworkHints = buildDecoderExtensionArtworkHintMap()
    val effectiveDecoderName = decoderName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val resolvedHint =
        decoderArtworkHintForName(effectiveDecoderName)
            ?: file?.name?.let { resolveDecoderArtworkHintForFileName(it, decoderExtensionArtworkHints) }
    return when (resolvedHint) {
        DecoderArtworkHint.TrackedFile -> Icons.Default.Memory
        DecoderArtworkHint.GameFile -> Icons.Default.Gamepad
        null -> Icons.Default.MusicNote
    }
}
