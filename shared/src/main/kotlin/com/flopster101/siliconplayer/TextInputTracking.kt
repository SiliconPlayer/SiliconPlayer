package com.flopster101.siliconplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

// Refcounted so nested dialogs and multi-field layouts cannot unbalance it.
class TextInputTracker {
    var activeCount by mutableIntStateOf(0)
        private set

    val hasActiveInput: Boolean get() = activeCount > 0

    fun activate() {
        activeCount++
    }

    fun deactivate() {
        activeCount = (activeCount - 1).coerceAtLeast(0)
    }
}

val LocalTextInputTracker = compositionLocalOf { TextInputTracker() }

// Call from dialog bodies that own a text field: while the dialog is
// composed, global shortcuts yield so typing never drives playback.
@Composable
fun TrackTextInputActive() {
    val tracker = LocalTextInputTracker.current
    DisposableEffect(tracker) {
        tracker.activate()
        onDispose { tracker.deactivate() }
    }
}

// For text fields that stay composed (search bars): only focused counts.
fun Modifier.trackTextInputFocus(): Modifier = composed {
    val tracker = LocalTextInputTracker.current
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(tracker, focused) {
        if (focused) tracker.activate()
        onDispose { if (focused) tracker.deactivate() }
    }
    onFocusChanged { focused = it.hasFocus }
}
