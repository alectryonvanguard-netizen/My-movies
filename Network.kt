package com.mymovie.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.mymovie.app.data.Prefs
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

object Network {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun isOnline(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun client(context: Context, prefs: Prefs): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            var req = chain.request()
            // Key and language are injected per request so Settings changes apply instantly.
            if (req.url.host == "api.themoviedb.org") {
                val url = req.url.newBuilder()
                    .addQueryParameter("api_key", prefs.tmdbKey)
                    .addQueryParameter("language", prefs.language)
                    .build()
                req = req.newBuilder().url(url).build()
            }
            // Offline: serve cached metadata up to 7 days old.
            if (!isOnline(context)) {
                req = req.newBuilder()
                    .cacheControl(CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build())
                    .build()
            }
            chain.proceed(req)
        }
        .build()
}
