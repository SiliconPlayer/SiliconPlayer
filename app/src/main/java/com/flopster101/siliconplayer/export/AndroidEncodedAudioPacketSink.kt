package com.flopster101.siliconplayer.export

import android.media.MediaCodec
import android.media.MediaFormat
import java.io.Closeable
import java.nio.ByteBuffer

internal interface AndroidEncodedAudioPacketSink : Closeable {
    fun onFormatChanged(format: MediaFormat)
    fun writePacket(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
    fun finish(totalFrames: Long)
}
