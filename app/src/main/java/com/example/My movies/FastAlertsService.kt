package com.mymovie.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional "fast alerts" mode: keeps the app alive with a small ongoing notification and runs the alert
 * check every 2 minutes (normal mode is limited by Android to about every 15–30 minutes).
 * It uses more battery, and free data sources may still publish results later than that.
 */
class FastAlertsService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(CH, "Fast alerts running", NotificationManager.IMPORTANCE_LOW))
        }
        val n = NotificationCompat.Builder(this, CH).setSmallIcon(com.mymovie.app.R.drawable.ic_notification)
            .setContentTitle("My Movie fast alerts").setContentText("Checking for updates every 2 minutes")
            .setOngoing(true).setOnlyAlertOnce(true).build()
        ServiceCompat.startForeground(this, 43, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (job?.isActive != true) {
            job = scope.launch {
                while (true) {
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                        "alerts_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<AlertsWorker>().build()
                    )
                    delay(120_000)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CH = "fast"
        fun start(ctx: Context) {
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, FastAlertsService::class.java)) }
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, FastAlertsService::class.java)) }
    }
}
