package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.flopster101.siliconplayer.ui.icons.PlaceholderGamepadIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderMusicNoteIcon
import com.flopster101.siliconplayer.ui.icons.PlaceholderTrackerChipIcon
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
        DecoderArtworkHint.TrackedFile -> PlaceholderTrackerChipIcon
        DecoderArtworkHint.GameFile -> PlaceholderGamepadIcon
        null -> PlaceholderMusicNoteIcon
    }
}
