package com.flopster101.siliconplayer.usb

import java.nio.ByteBuffer

internal object UacDriverNative {
    init {
        System.loadLibrary("siliconplayer")
    }

    external fun nativeInit(): Boolean
    external fun nativeOpen(fd: Int): Boolean
    external fun nativeClose()
    external fun nativeIsOpen(): Boolean
    external fun nativeStart(sampleRate: Int, bitsPerSample: Int, channels: Int): Boolean
    external fun nativeStop()
    external fun nativeFlushRing()
    external fun nativeIsStreamingFormat(sampleRate: Int, bitsPerSample: Int, channels: Int): Boolean
    external fun nativeIsStreaming(): Boolean
    external fun nativeWrite(directBuffer: ByteBuffer, frames: Int): Int
    external fun nativeWritableFrames(): Int
    external fun nativePlayedFrames(): Long
    external fun nativePendingFrames(): Long
    external fun nativeLastErrorCode(): Int
    external fun nativeLastErrorDetail(): String?
    external fun nativeSupportedRates(): IntArray?
    external fun nativeFormatDiagnostics(): LongArray?
}
