package com.flopster101.siliconplayer

import java.util.UUID

actual fun generatePlaylistId(): String = UUID.randomUUID().toString()

actual fun currentPlaylistTimeMillis(): Long = System.currentTimeMillis()
