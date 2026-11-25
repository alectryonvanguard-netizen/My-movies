package com.mymovie.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymovie.app.appContainer
import com.mymovie.app.data.MediaType
import com.mymovie.app.data.remote.Network
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Warms the catalogue cache every few hours so Home and Movies open instantly and work offline. */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        if (!c.prefs.hasKey) return Result.success()
        return try {
            Network.forceRefresh()
            coroutineScope {
                listOf(
                    async { c.repo.trending(null) },
                    async { c.repo.popular(MediaType.MOVIE) },
                    async { c.repo.popular(MediaType.TV) }
                ).awaitAll()
            }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }
}
