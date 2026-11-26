package com.mymovie.app

import android.content.Context
import androidx.room.Room
import com.mymovie.app.data.MovieRepository
import com.mymovie.app.data.Prefs
import com.mymovie.app.data.local.AppDatabase
import com.mymovie.app.data.NewsRepository
import com.mymovie.app.data.SportsRepository
import com.mymovie.app.downloads.Downloader
import com.mymovie.app.data.remote.FootballDataApi
import com.mymovie.app.data.remote.SportsDbApi
import com.mymovie.app.data.remote.ArchiveApi
import kotlinx.coroutines.launch
import com.mymovie.app.data.remote.Network
import com.mymovie.app.data.remote.TmdbApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Simple manual dependency container. */
class AppContainer(val context: Context) {
    val prefs = Prefs(context)
    private val client = Network.client(context, prefs)

    private fun retrofit(base: String): Retrofit = Retrofit.Builder()
        .baseUrl(base)
        .client(client)
        .addConverterFactory(Network.json.asConverterFactory("application/json".toMediaType()))
        .build()

    val httpClient get() = client
    val tmdb: TmdbApi = retrofit("https://api.themoviedb.org/3/").create(TmdbApi::class.java)
    val archive: ArchiveApi = retrofit("https://archive.org/").create(ArchiveApi::class.java)
    private val sportsDb: SportsDbApi = retrofit("https://www.thesportsdb.com/").create(SportsDbApi::class.java)
    private val footballData: FootballDataApi = retrofit("https://api.football-data.org/").create(FootballDataApi::class.java)
    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "mymovie.db")
        .addMigrations(AppDatabase.MIGRATION_1_2).build()

    /** For work that must outlive a screen (saving progress, downloads). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repo = MovieRepository(tmdb, archive, db.dao(), prefs)
    val sports = SportsRepository(sportsDb, footballData, prefs)
    val news = NewsRepository(client, prefs)
    val downloader = Downloader(context, db.dao(), client, appScope)

    init { appScope.launch { db.dao().pauseAllActive() } } // downloads interrupted by a process death become resumable
}
