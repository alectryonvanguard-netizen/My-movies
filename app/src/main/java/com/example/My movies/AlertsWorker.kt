package com.mymovie.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mymovie.app.AppContainer
import com.mymovie.app.appContainer
import com.mymovie.app.data.EventState
import com.mymovie.app.data.MediaType
import com.mymovie.app.data.SportLeague
import com.mymovie.app.notify.Notifier
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs about every 30 minutes (Android may delay it to save battery) and sends:
 *  - important: watchlist movie releasing today/soon, followed match starting within 45 min, final score of followed matches
 *  - normal: popular movies releasing today, daily headline digest (opt-in)
 * This polls free data providers; it is not a real-time push service.
 */
class AlertsWorker(private val ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = ctx.appContainer
        val p = c.prefs
        if (!Notifier.enabled(ctx)) return Result.success()
        if (p.alertSports) runCatching { sports(c) }
        if (p.alertMovies && p.due("movies_check", 6 * 3600_000L)) runCatching { movies(c) }
        if (p.alertNews && p.due("news_digest", 24 * 3600_000L)) runCatching { digest(c) }
        return Result.success()
    }

    private fun today(offset: Int = 0): String {
        val c = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, offset) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    private suspend fun movies(c: AppContainer) {
        if (!c.prefs.hasKey) return
        val t = today()
        // Watchlist movies: release day and "this week"
        c.db.dao().watchlist().first().filter { it.type == "movie" }.take(20).forEach { w ->
            val d = runCatching { c.repo.details(MediaType.MOVIE, w.tmdbId) }.getOrNull() ?: return@forEach
            val date = d.releaseDate
            when {
                date == t && c.prefs.markNotified("rel:${w.uid}:$date:today") ->
                    Notifier.send(ctx, w.uid + "today", Notifier.HIGH, "${w.title} is out today", "A title on your watchlist releases today. Check where to watch.")
                date > t && date <= today(3) && c.prefs.markNotified("rel:${w.uid}:$date:soon") ->
                    Notifier.send(ctx, w.uid + "soon", Notifier.HIGH, "${w.title} is coming soon", "Releases on $date.")
            }
        }
        // New popular releases today
        val fresh = c.repo.discover(MediaType.MOVIE, 1, mapOf("sort_by" to "popularity.desc", "primary_release_date.gte" to t, "primary_release_date.lte" to t)).items.take(3)
        if (fresh.isNotEmpty() && c.prefs.markNotified("new:$t"))
            Notifier.send(ctx, "new$t", Notifier.NORMAL, "New releases today", fresh.joinToString(" • ") { it.title })
    }

    private suspend fun sports(c: AppContainer) {
        val now = System.currentTimeMillis()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault())
        c.prefs.followedLeagues.take(6).forEach { raw ->
            val parts = raw.split("|")
            if (parts.size < 4) return@forEach
            val l = SportLeague(parts[1], parts[3], parts[2], parts[0])
            runCatching {
                c.sports.fixtures(l).filter { (it.startMillis ?: 0L) in now..(now + 45 * 60_000L) }.forEach { e ->
                    if (c.prefs.markNotified("ko:${e.key}"))
                        Notifier.send(ctx, "ko${e.key}", Notifier.HIGH, "Starting soon · ${l.name}", "${e.title} at ${time.format(Date(e.startMillis!!))}")
                }
            }
            runCatching {
                c.sports.results(l).filter { it.state == EventState.FINISHED && it.homeScore != null && it.awayScore != null && (it.startMillis ?: 0L) > now - 6 * 3600_000L }.forEach { e ->
                    if (c.prefs.markNotified("ft:${e.key}"))
                        Notifier.send(ctx, "ft${e.key}", Notifier.HIGH, "Full time · ${l.name}", "${e.home} ${e.homeScore} - ${e.awayScore} ${e.away}")
                }
            }
        }
    }

    private suspend fun digest(c: AppContainer) {
        val m = c.news.load("Movies").firstOrNull()
        val s = c.news.load("Sports").firstOrNull()
        val lines = listOfNotNull(m?.let { "Movies: ${it.title}" }, s?.let { "Sports: ${it.title}" })
        if (lines.isNotEmpty()) Notifier.send(ctx, "digest" + today(), Notifier.NORMAL, "Today's headlines", lines.joinToString("\n"))
    }
}
