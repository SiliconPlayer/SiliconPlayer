package com.flopster101.siliconplayer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

private val nativeAudioSessionMutex = Mutex()

// A native session call that never returns must not wedge every future
// load; past the timeout, proceed unserialized and let the engine's
// lifecycle mutex arbitrate.
private val nativeAudioSessionLockTimeout = 5.seconds

internal suspend fun <T> runWithNativeAudioSession(block: () -> T): T {
    return withContext(Dispatchers.IO) {
        var locked = false
        val deadline = TimeSource.Monotonic.markNow() + nativeAudioSessionLockTimeout
        while (!locked) {
            locked = nativeAudioSessionMutex.tryLock()
            if (locked || deadline.hasPassedNow()) break
            coroutineContext.ensureActive()
            delay(10)
        }
        try {
            block()
        } finally {
            if (locked) {
                nativeAudioSessionMutex.unlock()
            }
        }
    }
}
