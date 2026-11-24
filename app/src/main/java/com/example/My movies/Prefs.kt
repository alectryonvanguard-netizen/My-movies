package com.mymovie.app.data

import android.content.Context
import com.mymovie.app.BuildConfig
import java.util.Locale

/** Private on-device preferences. The TMDB key is never hardcoded in source. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mymovie_prefs", Context.MODE_PRIVATE)

    var tmdbKey: String
        get() = (sp.getString("tmdb_key", "") ?: "").trim().ifBlank { BuildConfig.TMDB_API_KEY }
        set(value) { sp.edit().putString("tmdb_key", value.trim()).apply() }

    val hasKey: Boolean get() = tmdbKey.isNotBlank()

    var language: String
        get() = sp.getString("language", "en-US") ?: "en-US"
        set(value) { sp.edit().putString("language", value.trim().ifBlank { "en-US" }).apply() }

    var region: String
        get() = sp.getString("region", null) ?: Locale.getDefault().country.ifBlank { "US" }
        set(value) { sp.edit().putString("region", value.trim().uppercase().ifBlank { "US" }).apply() }
}
