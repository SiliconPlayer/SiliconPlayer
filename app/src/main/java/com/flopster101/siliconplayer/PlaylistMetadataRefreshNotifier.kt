package com.flopster101.siliconplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Progress notification for playlist metadata refresh. Silent (low-importance channel),
 * time-throttled updates, and a no-op whenever notifications are not allowed
 * so refreshing works cleanly with or without POST_NOTIFICATIONS granted.
 */
internal object PlaylistMetadataRefreshNotifier {

    private const val CHANNEL_ID = "playlist_metadata_refresh"
    private const val NOTIFICATION_ID = 4712
    private const val MIN_UPDATE_INTERVAL_MS = 300L

    private var lastPostMs = 0L

    fun start(context: Context, total: Int) {
        post(
            context,
            base(context)
                .setContentTitle("Refreshing playlist metadata")
                .setContentText("Starting refresh for $total tracks…")
                .setProgress(total, 0, false)
        )
    }

    fun progress(context: Context, current: Int, total: Int, trackTitle: String?) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPostMs < MIN_UPDATE_INTERVAL_MS && current < total) return
        lastPostMs = nowMs
        val content = buildString {
            append("$current of $total tracks")
            if (!trackTitle.isNullOrBlank()) {
                append(" — ")
                append(trackTitle)
            }
        }
        post(
            context,
            base(context)
                .setContentTitle("Refreshing playlist metadata")
                .setContentText(content)
                .setProgress(total, current, false)
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
                    "Playlist metadata refresh",
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
