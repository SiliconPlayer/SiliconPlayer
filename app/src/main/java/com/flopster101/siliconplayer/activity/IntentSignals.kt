package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import java.io.File

internal fun shouldOpenPlayerFromNotification(intent: Intent?): Boolean {
    return intent?.getBooleanExtra(PlaybackService.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, false) == true
}

internal fun resolveInitialFileToOpen(context: Context, intent: Intent?): File? {
    return resolveFileFromViewIntent(context, intent)
}
