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
    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** While in the future, API requests skip the cache (manual refresh / background refresh). */
    @Volatile var bypassCacheUntil = 0L
    fun forceRefresh() { bypassCacheUntil = System.currentTimeMillis() + 15_000 }

    private fun isOnline(c: Context): Boolean {
        val cm = c.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun ttl(host: String, path: String): Int = when {
        host == "api.themoviedb.org" -> if (path.contains("/trending")) 1800 else if (Regex("/(movie|tv)/\\d+$").containsMatchIn(path)) 21600 else 3600
        host.endsWith("thesportsdb.com") -> when {
            path.contains("livescore") -> 20
            path.contains("eventsday") -> 120
            path.contains("eventsnext") || path.contains("eventspast") -> 90
            path.contains("lookuptable") -> 900
            path.contains("all_leagues") -> 86400
            path.contains("search") -> 600
            else -> 60
        }
        host == "api.football-data.org" -> when {
            path.contains("/matches") -> 60
            path.contains("/standings") -> 900
            path.contains("/scorers") -> 1800
            else -> 300
        }
        else -> 0
    }

    fun client(context: Context, prefs: Prefs): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 30L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            var req = chain.request()
            val host = req.url.host
            when {
                host == "api.themoviedb.org" -> {
                    val cred = prefs.tmdbCredential
                    val url = req.url.newBuilder().addQueryParameter("language", prefs.language)
                    val rb = req.newBuilder()
                    if (cred.isNotBlank()) {
                        if (prefs.tmdbIsToken) rb.header("Authorization", "Bearer $cred") else url.addQueryParameter("api_key", cred)
                    }
                    req = rb.url(url.build()).build()
                }
                host == "api.football-data.org" ->
                    prefs.footballDataKey.takeIf { it.isNotBlank() }?.let { req = req.newBuilder().header("X-Auth-Token", it).build() }
                host.endsWith("thesportsdb.com") && req.url.encodedPath.contains("/api/v2/") ->
                    req = req.newBuilder().header("X-API-KEY", prefs.sportsDbKey).build()
            }
            val cacheable = ttl(host, req.url.encodedPath) > 0
            if (!isOnline(context)) {
                req = req.newBuilder().cacheControl(CacheControl.Builder().maxStale(7, TimeUnit.DAYS).build()).build()
            } else if (cacheable && System.currentTimeMillis() < bypassCacheUntil) {
                req = req.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
            }
            chain.proceed(req)
        }
        .addNetworkInterceptor { chain ->
            val resp = chain.proceed(chain.request())
            val t = ttl(chain.request().url.host, chain.request().url.encodedPath)
            if (t > 0 && resp.isSuccessful) resp.newBuilder().removeHeader("Pragma").header("Cache-Control", "public, max-age=$t").build() else resp
        }
        .build()
}
