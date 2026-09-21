package com.flopster101.siliconplayer

import androidx.compose.runtime.compositionLocalOf

/**
 * Player overlay visibility, 1 shown and 0 hidden.
 *
 * A provider so out-of-window renderers can sample it from the draw phase,
 * where the overlay's own alpha cannot reach across layers.
 */
internal val LocalPlayerOverlayVisibility = compositionLocalOf { { 1f } }

/**
 * Exit slide progress, 0 parked and 1 fully down. Follows the veil clock
 * so the punch stays aligned until the surface behind it is covered.
 */
internal val LocalPlayerExitSlideFraction = compositionLocalOf { 0f }
