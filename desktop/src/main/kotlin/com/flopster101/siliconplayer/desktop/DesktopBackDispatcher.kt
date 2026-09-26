package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.concurrent.CopyOnWriteArrayList

class DesktopBackCallback(
    var isEnabled: Boolean,
    var onBack: () -> Unit
)

fun interface DesktopBackRegistration {
    fun unregister()
}

class DesktopBackDispatcher {
    private val callbacks = CopyOnWriteArrayList<DesktopBackCallback>()

    fun register(callback: DesktopBackCallback): DesktopBackRegistration {
        callbacks.add(callback)
        return DesktopBackRegistration {
            callbacks.remove(callback)
        }
    }

    /**
     * Traverses registered back handlers in reverse order (LIFO) and
     * invokes the first active (enabled) handler.
     * Returns true if a handler was found and invoked, false otherwise.
     */
    fun onBackPressed(): Boolean {
        val snapshot = callbacks.toList()
        for (i in snapshot.indices.reversed()) {
            val cb = snapshot[i]
            if (cb.isEnabled) {
                cb.onBack()
                return true
            }
        }
        return false
    }

    val hasActiveHandlers: Boolean
        get() = callbacks.any { it.isEnabled }
}

val LocalDesktopBackDispatcher = staticCompositionLocalOf<DesktopBackDispatcher?> { null }

@Composable
fun DesktopBackHandler(
    dispatcher: DesktopBackDispatcher,
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val callback = remember {
        DesktopBackCallback(isEnabled = enabled, onBack = { currentOnBack() })
    }
    SideEffect {
        callback.isEnabled = enabled
        callback.onBack = { currentOnBack() }
    }
    DisposableEffect(dispatcher, callback) {
        val registration = dispatcher.register(callback)
        onDispose {
            registration.unregister()
        }
    }
}

@Composable
fun DesktopBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit
) {
    val dispatcher = LocalDesktopBackDispatcher.current ?: return
    DesktopBackHandler(dispatcher = dispatcher, enabled = enabled, onBack = onBack)
}
