package com.mymovie.app

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import androidx.work.*
import com.mymovie.app.work.RefreshWorker
import com.mymovie.app.work.AlertsWorker
import com.mymovie.app.notify.Notifier
import java.util.concurrent.TimeUnit

class MyMovieApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "alerts", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AlertsWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "catalog_refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        )
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(300)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .build()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as MyMovieApp).container
