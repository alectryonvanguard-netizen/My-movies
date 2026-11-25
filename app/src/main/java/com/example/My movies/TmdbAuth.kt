package com.mymovie.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface TestResult {
    data object Ok : TestResult
    data object Invalid : TestResult
    data class Failed(val message: String) : TestResult
}

object TmdbAuth {
    /** v4 Read Access Tokens are long JWTs; v3 API keys are 32 hex characters. */
    fun looksLikeToken(c: String) = c.startsWith("eyJ") || c.length > 64

    suspend fun test(cred: String): TestResult = withContext(Dispatchers.IO) {
        val token = looksLikeToken(cred)
        val url = "https://api.themoviedb.org/3/configuration".toHttpUrl().newBuilder()
            .apply { if (!token) addQueryParameter("api_key", cred) }.build()
        val req = Request.Builder().url(url).apply { if (token) header("Authorization", "Bearer $cred") }.build()
        try {
            OkHttpClient().newCall(req).execute().use {
                when {
                    it.isSuccessful -> TestResult.Ok
                    it.code == 401 -> TestResult.Invalid
                    else -> TestResult.Failed("TMDB answered HTTP ${it.code}")
                }
            }
        } catch (e: IOException) {
            TestResult.Failed("No connection")
        }
    }
}
