package com.flopster101.siliconplayer.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flopster101.siliconplayer.MainActivity
import com.flopster101.siliconplayer.R

/**
 * Progress notification for library scans. Silent (low-importance channel),
 * time-throttled updates, and a no-op whenever notifications are not allowed
 * so scanning works with or without POST_NOTIFICATIONS granted.
 */
internal object LibraryScanNotifier {

    private const val CHANNEL_ID = "library_scan"
    private const val NOTIFICATION_ID = 4711
    private const val MIN_UPDATE_INTERVAL_MS = 500L

    private var lastPostMs = 0L

    fun start(context: Context) {
        post(
            context,
            base(context)
                .setContentTitle("Scanning library")
                .setContentText("Indexing music sources")
                .setProgress(0, 0, true)
        )
    }

    fun progress(context: Context, state: LibrarySyncState) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPostMs < MIN_UPDATE_INTERVAL_MS) return
        lastPostMs = nowMs
        val currentFile = state.currentPath?.substringAfterLast('/')
        post(
            context,
            base(context)
                .setContentTitle("Scanning library")
                .setContentText(
                    buildString {
                        append("${state.indexedTracks} tracks indexed")
                        if (currentFile != null && currentFile.isNotBlank()) {
                            append(" — ")
                            append(currentFile)
                        }
                    }
                )
                .setProgress(0, 0, true)
        )
    }

    fun finish(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun base(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(launchIntent(context))

    private fun post(context: Context, builder: NotificationCompat.Builder) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Library scanning",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private fun launchIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
