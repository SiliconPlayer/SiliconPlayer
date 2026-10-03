package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal enum class DecoderPlatform {
    Android,
    Desktop
}

// Decoders missing from this table run on every platform. An entry
// restricts the decoder to exactly the listed platforms.
internal val decoderRestrictedPlatforms: Map<String, Set<DecoderPlatform>> = mapOf(
    DecoderNames.PLATFORM_DOLBY to setOf(DecoderPlatform.Android)
)

internal val LocalDecoderPlatform = staticCompositionLocalOf { DecoderPlatform.Android }

internal fun isDecoderSupportedOnPlatform(decoderName: String, platform: DecoderPlatform): Boolean {
    val restricted = decoderRestrictedPlatforms.entries.firstOrNull { (name, _) ->
        name.equals(decoderName, ignoreCase = true)
    } ?: return true
    return restricted.value.contains(platform)
}

@Composable
internal fun isDecoderSupportedOnCurrentPlatform(decoderName: String): Boolean {
    return isDecoderSupportedOnPlatform(decoderName, LocalDecoderPlatform.current)
}
