package com.mymovie.app.data

import android.text.Html
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class NewsItem(val title: String, val link: String, val source: String, val category: String, val published: Long?, val image: String?)
data class Feed(val source: String, val url: String, val category: String)

/** Headlines come from publishers' public RSS feeds. Only the headline, picture and link are shown; articles open on the publisher's page. */
class NewsRepository(private val client: OkHttpClient) {
    val feeds = listOf(
        Feed("Variety", "https://variety.com/feed/", "Movies"),
        Feed("Deadline", "https://deadline.com/feed/", "Movies"),
        Feed("The Hollywood Reporter", "https://www.hollywoodreporter.com/feed/", "Movies"),
        Feed("BBC Sport", "https://feeds.bbci.co.uk/sport/rss.xml", "Sports"),
        Feed("BBC Football", "https://feeds.bbci.co.uk/sport/football/rss.xml", "Football"),
        Feed("BBC Cricket", "https://feeds.bbci.co.uk/sport/cricket/rss.xml", "Cricket"),
        Feed("BBC Tennis", "https://feeds.bbci.co.uk/sport/tennis/rss.xml", "Tennis"),
        Feed("BBC Golf", "https://feeds.bbci.co.uk/sport/golf/rss.xml", "Golf"),
        Feed("BBC Rugby Union", "https://feeds.bbci.co.uk/sport/rugby-union/rss.xml", "Rugby"),
        Feed("BBC Formula 1", "https://feeds.bbci.co.uk/sport/formula1/rss.xml", "Formula 1")
    )
    private val sportsCats = setOf("Sports", "Football", "Cricket", "Tennis", "Golf", "Rugby", "Formula 1")
    val categories = listOf("All", "Movies", "Sports", "Football", "Cricket", "Tennis", "Golf", "Rugby", "Formula 1")

    suspend fun load(category: String): List<NewsItem> = withContext(Dispatchers.IO) {
        val chosen = feeds.filter {
            when (category) { "All" -> true; "Sports" -> it.category in sportsCats; else -> it.category == category }
        }
        coroutineScope {
            chosen.map { f -> async { runCatching { fetch(f) }.getOrDefault(emptyList()) } }.awaitAll().flatten()
        }.distinctBy { it.link }.sortedByDescending { it.published ?: 0L }.take(80)
    }

    private fun fetch(feed: Feed): List<NewsItem> {
        val req = Request.Builder().url(feed.url).header("User-Agent", "MyMovie/1.0 (personal news reader)").build()
        client.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            return parse(r.body?.byteStream() ?: return emptyList(), feed)
        }
    }

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    private fun parseDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        for (f in dateFormats) runCatching { SimpleDateFormat(f, Locale.US).parse(s.trim())?.time }.getOrNull()?.let { return it }
        return null
    }

    private fun https(u: String?) = u?.replaceFirst("http://", "https://")

    private fun parse(input: InputStream, feed: Feed): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, null)
        val out = ArrayList<NewsItem>()
        var inItem = false
        var title: String? = null; var link: String? = null; var date: String? = null; var img: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "item", "entry" -> { inItem = true; title = null; link = null; date = null; img = null }
                    "title" -> if (inItem) title = p.nextText()
                    "link" -> if (inItem) { val href = p.getAttributeValue(null, "href"); link = p.nextText().trim().ifBlank { href } }
                    "pubDate", "published", "updated", "dc:date" -> if (inItem && date == null) date = p.nextText()
                    "media:thumbnail", "media:content", "enclosure" -> if (inItem && img == null) {
                        val u = p.getAttributeValue(null, "url")
                        val type = p.getAttributeValue(null, "type").orEmpty()
                        val looksImage = p.name == "media:thumbnail" || type.startsWith("image") || Regex("\\.(jpe?g|png|webp)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(u.orEmpty())
                        if (u != null && looksImage) img = u
                    }
                }
                XmlPullParser.END_TAG -> if ((p.name == "item" || p.name == "entry") && inItem) {
                    inItem = false
                    val t = title; val l = https(link)
                    if (!t.isNullOrBlank() && !l.isNullOrBlank())
                        out += NewsItem(Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString().trim(), l, feed.source, feed.category, parseDate(date), https(img))
                }
            }
            ev = p.next()
        }
        return out
    }
}
