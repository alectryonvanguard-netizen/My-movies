package com.mymovie.app.data

import android.content.Context
import java.util.Locale

const val DEFAULT_TSDB_KEY = "123" // TheSportsDB public free key

/** Private preferences. API credentials are encrypted with the Android Keystore and never logged. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mymovie_prefs", Context.MODE_PRIVATE)

    init { // migrate a plaintext key stored by v1
        sp.getString("tmdb_key", null)?.takeIf { it.isNotBlank() }?.let {
            setSecret("tmdb_cred", it); sp.edit().remove("tmdb_key").apply()
        }
        sp.edit().remove("tmdb_key").apply()
    }

    private fun secret(name: String): String = sp.getString(name, null)?.let { SecureStore.decrypt(it) }.orEmpty()
    private fun setSecret(name: String, v: String): Boolean = runCatching {
        if (v.isBlank()) sp.edit().remove(name).apply() else sp.edit().putString(name, SecureStore.encrypt(v.trim())).apply()
    }.isSuccess

    var tmdbCredential: String
        get() = secret("tmdb_cred")
        set(v) { setSecret("tmdb_cred", v) }
    fun saveTmdb(v: String): Boolean = setSecret("tmdb_cred", v)
    val hasKey: Boolean get() = tmdbCredential.isNotBlank()
    val tmdbIsToken: Boolean get() = TmdbAuth.looksLikeToken(tmdbCredential)

    var sportsDbKey: String
        get() = secret("tsdb_key").ifBlank { DEFAULT_TSDB_KEY }
        set(v) { setSecret("tsdb_key", v) }
    var footballDataKey: String
        get() = secret("fd_key")
        set(v) { setSecret("fd_key", v) }

    var language: String
        get() = sp.getString("language", "en-US") ?: "en-US"
        set(v) { sp.edit().putString("language", v.trim().ifBlank { "en-US" }).apply() }
    var region: String
        get() = sp.getString("region", null) ?: Locale.getDefault().country.ifBlank { "US" }
        set(v) { sp.edit().putString("region", v.trim().uppercase().ifBlank { "US" }).apply() }

    /** AUTO, DATA_SAVER, BALANCED, HD, FULL */
    var qualityPreset: String
        get() = sp.getString("quality", "AUTO") ?: "AUTO"
        set(v) { sp.edit().putString("quality", v).apply() }
    var audioLang: String
        get() = sp.getString("audio_lang", "") ?: ""
        set(v) { sp.edit().putString("audio_lang", v).apply() }
    var subtitleLang: String
        get() = sp.getString("sub_lang", "") ?: ""
        set(v) { sp.edit().putString("sub_lang", v).apply() }
}
