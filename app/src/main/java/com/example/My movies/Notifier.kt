package com.mymovie.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mymovie.app.MainActivity

object Notifier {
    const val HIGH = "important"
    const val NORMAL = "updates"

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(HIGH, "Important alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Release day for your watchlist, matches about to start, final scores of followed competitions"
        })
        nm.createNotificationChannel(NotificationChannel(NORMAL, "Updates & news", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "New releases today and the daily headline digest"
        })
    }

    fun send(ctx: Context, key: String, channel: String, title: String, text: String) {
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(com.mymovie.app.R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (channel == HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (channel == HIGH) NotificationCompat.CATEGORY_EVENT else NotificationCompat.CATEGORY_RECOMMENDATION)
            .setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(ctx).notify(key.hashCode(), n) } catch (e: SecurityException) { /* permission not granted */ }
    }

    fun enabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
}
