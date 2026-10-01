package com.flopster101.siliconplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

// A wheel can scroll the list but never perform the pull gesture, so the
// pull affordance only gets in the way there. Any press re-arms it, which
// keeps touchscreens (including touch-capable desktop hardware) working.
fun Modifier.trackPullRefreshInput(
    onAllowedChanged: (Boolean) -> Unit
): Modifier = composed {
    val currentHandler by rememberUpdatedState(onAllowedChanged)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Scroll -> currentHandler(false)
                    PointerEventType.Press -> currentHandler(true)
                    else -> Unit
                }
            }
        }
    }
}
