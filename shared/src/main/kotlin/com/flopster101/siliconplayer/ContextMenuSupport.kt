package com.flopster101.siliconplayer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

// Secondary (right/middle) click opens the same menu a long-press or
// kebab opens. Touch screens never report a secondary press, so this
// stays inert there; mice on desktop, DeX or Chromebooks trigger it.
fun Modifier.contextMenuSecondaryClick(
    onOpenMenu: (() -> Unit)?
): Modifier = composed {
    if (onOpenMenu == null) {
        return@composed this
    }
    val currentHandler by rememberUpdatedState(onOpenMenu)
    pointerInput(Unit) {
        awaitEachGesture {
            val press = awaitPointerEvent(PointerEventPass.Initial)
            if (!press.buttons.isSecondaryPressed) return@awaitEachGesture
            press.changes.forEach { it.consume() }
            currentHandler()
            var current = press
            while (current.buttons.isSecondaryPressed) {
                current = awaitPointerEvent(PointerEventPass.Initial)
                current.changes.forEach { it.consume() }
            }
        }
    }
}

// Shift+Left click selects a range, mirroring tap-hold on touch. The
// press is consumed so the regular click underneath never fires for it.
fun Modifier.shiftClickToSelect(
    onSelectRange: (() -> Unit)?
): Modifier = composed {
    if (onSelectRange == null) {
        return@composed this
    }
    val currentHandler by rememberUpdatedState(onSelectRange)
    pointerInput(Unit) {
        awaitEachGesture {
            val press = awaitPointerEvent(PointerEventPass.Initial)
            if (press.buttons.isPrimaryPressed && press.keyboardModifiers.isShiftPressed &&
                !press.keyboardModifiers.isCtrlPressed
            ) {
                press.changes.forEach { it.consume() }
                currentHandler()
            }
        }
    }
}

// Ctrl+Left click selects, mirroring touch long-press. The press is
// consumed so the regular click underneath never fires for it.
fun Modifier.ctrlClickToSelect(
    onSelect: (() -> Unit)?
): Modifier = composed {
    if (onSelect == null) {
        return@composed this
    }
    val currentHandler by rememberUpdatedState(onSelect)
    pointerInput(Unit) {
        awaitEachGesture {
            val press = awaitPointerEvent(PointerEventPass.Initial)
            if (press.buttons.isPrimaryPressed && press.keyboardModifiers.isCtrlPressed &&
                !press.keyboardModifiers.isShiftPressed
            ) {
                press.changes.forEach { it.consume() }
                currentHandler()
            }
        }
    }
}
