package com.mymovie.app.player

import com.mymovie.app.web.WebActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mymovie.app.data.FreeItem
import com.mymovie.app.data.MovieRepository
import com.mymovie.app.data.local.HistoryEntity
import com.mymovie.app.data.local.SourceEntity

object PlayerLauncher {
    fun play(
        context: Context, url: String, title: String, uid: String, mediaKey: String,
        subtitle: String? = null, poster: String? = null, backdrop: String? = null,
        subtitleUrls: List<String> = emptyList(), live: Boolean = false
    ) {
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            putExtra("url", url); putExtra("title", title); putExtra("uid", uid); putExtra("mediaKey", mediaKey)
            putExtra("subtitle", subtitle); putExtra("poster", poster); putExtra("backdrop", backdrop)
            putStringArrayListExtra("subs", ArrayList(subtitleUrls)); putExtra("live", live)
        })
    }

    fun playHistory(context: Context, h: HistoryEntity) =
        play(context, h.sourceUrl, h.title, h.uid, h.mediaKey, h.subtitle, h.posterUrl, h.backdropUrl)

    /** Resolves an Internet Archive item to a real file URL and plays it. */
    suspend fun playFree(
        context: Context, repo: MovieRepository, item: FreeItem,
        uid: String? = null, mediaKey: String? = null, title: String? = null,
        poster: String? = null, backdrop: String? = null
    ): Boolean {
        val s = runCatching { repo.verifyFree(item) }.getOrNull() ?: return false
        play(
            context, s.url, title ?: item.title, uid ?: "ia:${item.identifier}", mediaKey ?: "ia:${item.identifier}",
            "Internet Archive", poster ?: item.thumb, backdrop ?: item.thumb, s.subtitles
        )
        return true
    }

    fun isDirect(url: String): Boolean {
        val lower = url.substringBefore('?').lowercase()
        return url.startsWith("content://") || url.startsWith("file://") || directExt.any { lower.endsWith(it) }
    }

    private val directExt = listOf(".mp4", ".m3u8", ".mkv", ".webm", ".mpd", ".mov", ".m4v", ".ts")

    /** Plays direct media in-app; opens any other link in the browser / provider app. */
    fun openSource(
        context: Context, s: SourceEntity, title: String, uid: String, mediaKey: String,
        subtitle: String?, poster: String?, backdrop: String?, live: Boolean = false
    ) {
        val lower = s.url.substringBefore('?').lowercase()
        val direct = s.url.startsWith("content://") || s.url.startsWith("file://") || directExt.any { lower.endsWith(it) }
        if (direct) {
            play(context, s.url, title, uid, mediaKey, subtitle, poster, backdrop, live = live)
        } else {
            WebActivity.open(context, s.url, title)
        }
    }
}
