package com.flopster101.siliconplayer

internal data class CachedSourceFile(
    val absolutePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val sourceId: String?
)
