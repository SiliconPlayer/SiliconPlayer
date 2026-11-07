package com.flopster101.siliconplayer.export

import java.io.Closeable

internal interface AudioExportWriter : Closeable {
    fun write(samples: FloatArray, frameCount: Int)
    fun finish()
}
