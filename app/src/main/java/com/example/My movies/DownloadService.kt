package com.mymovie.app.downloads

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
import com.mymovie.app.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps downloads alive in the background and shows one progress notification. */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String, pct: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CH).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("My Movie").setContentText(text).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(100, pct, indeterminate).build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Downloads", NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification("Preparing download…", 0, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (job?.isActive != true) {
            job = scope.launch {
                val dao = appContainer.db.dao()
                while (true) {
                    val active = withContext(Dispatchers.IO) { dao.activeDownloads() }
                    if (active.isEmpty()) { stopSelf(); break }
                    val total = active.sumOf { it.totalBytes }
                    val pct = if (total > 0) (active.sumOf { it.downloadedBytes } * 100 / total).toInt() else 0
                    nm.notify(ID, notification("${active.size} download(s) · $pct%", pct, total <= 0))
                    delay(1000)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    companion object {
        private const val CH = "downloads"
        private const val ID = 42
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
