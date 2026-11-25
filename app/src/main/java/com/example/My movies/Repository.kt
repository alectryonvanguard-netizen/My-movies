package com.mymovie.app.data

import android.net.Uri
import com.mymovie.app.data.local.*
import com.mymovie.app.data.remote.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class MissingApiKeyException : Exception("TMDB API key is missing")

class MovieRepository(
    private val tmdb: TmdbApi,
    private val archive: ArchiveApi,
    private val dao: AppDao,
    private val prefs: Prefs
) {
    private suspend fun <T> tmdbCall(block: suspend () -> T): T {
        if (!prefs.hasKey) throw MissingApiKeyException()
        return block()
    }

    private fun MediaDto.toItem(default: MediaType): MediaItem {
        val t = when (mediaType) { "tv" -> MediaType.TV; "movie" -> MediaType.MOVIE; else -> default }
        return MediaItem(
            id, t, title ?: name.orEmpty(), overview.orEmpty(), Img.poster(posterPath), Img.backdrop(backdropPath),
            voteAverage, (releaseDate ?: firstAirDate).orEmpty().take(4), genreIds, originalLanguage
        )
    }

    private fun PagedResponse<MediaDto>.toPage(default: MediaType?): Page<MediaItem> = Page(
        results.filter { it.mediaType != "person" && it.posterPath != null }
            .map { it.toItem(default ?: MediaType.MOVIE) }
            .filter { it.title.isNotBlank() }
            .distinctBy { it.key },
        page, totalPages
    )

    // ---------- TMDB catalogue ----------
    suspend fun trending(type: MediaType?, page: Int = 1) =
        tmdbCall { tmdb.trending(type?.path ?: "all", page).toPage(type) }

    suspend fun popular(type: MediaType, page: Int = 1) =
        tmdbCall { tmdb.list(type.path, "popular", page).toPage(type) }

    suspend fun discover(type: MediaType, page: Int, params: Map<String, String>) =
        tmdbCall { tmdb.discover(type.path, page, params).toPage(type) }

    suspend fun search(type: MediaType, query: String, page: Int, year: String?): Page<MediaItem> = tmdbCall {
        val yearKey = if (type == MediaType.MOVIE) "year" else "first_air_date_year"
        val extra: Map<String, String> = if (year == null) emptyMap() else mapOf(yearKey to year)
        tmdb.search(type.path, query, page, extra).toPage(type)
    }

    suspend fun recommendations(type: MediaType, id: Int): List<MediaItem> =
        tmdbCall { tmdb.recommendations(type.path, id).toPage(type).items }

    suspend fun details(type: MediaType, id: Int): MediaDetail = tmdbCall {
        val d = tmdb.details(type.path, id, "credits,videos,watch/providers,alternative_titles,similar,recommendations")
        val item = MediaItem(
            d.id, type, d.title ?: d.name.orEmpty(), d.overview.orEmpty(), Img.poster(d.posterPath),
            Img.backdrop(d.backdropPath), d.voteAverage, (d.releaseDate ?: d.firstAirDate).orEmpty().take(4),
            d.genres.map { it.id }, d.originalLanguage
        )
        val videos = d.videos?.results.orEmpty().filter { it.site == "YouTube" }.map {
            VideoInfo(it.key, it.name.orEmpty().ifBlank { it.type.orEmpty() }, when (it.type) {
                "Trailer" -> VideoKind.TRAILER; "Teaser" -> VideoKind.TEASER; "Clip" -> VideoKind.CLIP
                "Featurette", "Behind the Scenes", "Bloopers" -> VideoKind.PREVIEW; else -> VideoKind.OTHER
            })
        }.sortedBy { it.kind.ordinal }
        val region = d.providers?.results?.get(prefs.region)
        val crew = d.credits?.crew.orEmpty()
        val keyJobs = listOf("Director", "Screenplay", "Writer", "Producer", "Original Music Composer", "Director of Photography")
        val alt = d.alternativeTitles?.let { (it.titles + it.results).mapNotNull { t -> t.title } }.orEmpty()
        MediaDetail(
            item = item, tagline = d.tagline.orEmpty(), runtime = d.runtime ?: d.episodeRunTime.firstOrNull(),
            genres = d.genres.map { it.name },
            cast = d.credits?.cast.orEmpty().take(20).map { CastMember(it.name, it.character.orEmpty(), Img.profile(it.profilePath), it.id) },
            crew = crew.filter { it.job in keyJobs }.distinctBy { it.name + it.job }.take(12).map { CrewMember(it.name, it.job.orEmpty(), it.id) },
            directors = crew.filter { it.job == "Director" }.map { it.name }.distinct(),
            videos = videos,
            providers = region?.let { r ->
                Providers(r.link, (r.free + r.ads).map { it.name }.distinct(), r.flatrate.map { it.name }.distinct(), (r.rent + r.buy).map { it.name }.distinct())
            },
            seasons = d.seasons.filter { it.seasonNumber > 0 }.map { SeasonInfo(it.seasonNumber, it.name.orEmpty(), it.episodeCount) },
            releaseDate = (d.releaseDate ?: d.firstAirDate).orEmpty(),
            countries = d.productionCountries.mapNotNull { it.name },
            languages = d.spokenLanguages.mapNotNull { it.englishName },
            originalLanguage = d.originalLanguage?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH) }.orEmpty(),
            originalTitle = (d.originalTitle ?: d.originalName).orEmpty(),
            altTitles = alt.filter { it != item.title }.distinct().take(10),
            popularity = d.popularity, voteCount = d.voteCount, status = d.status.orEmpty(),
            similar = d.similar?.toPage(type)?.items.orEmpty(),
            recommended = d.recommendations?.toPage(type)?.items.orEmpty()
        )
    }

    suspend fun searchPeople(q: String): List<PersonBrief> = tmdbCall {
        tmdb.searchPeople(q, 1).results.map { PersonBrief(it.id, it.name, Img.profile(it.profilePath), it.knownForDepartment.orEmpty()) }
    }

    suspend fun person(id: Int): PersonInfo = tmdbCall {
        val p = tmdb.person(id, "movie_credits")
        fun List<MediaDto>.movies() = filter { it.posterPath != null }.map { it.toItem(MediaType.MOVIE) }.distinctBy { it.key }.take(30)
        PersonInfo(
            p.id, p.name, p.biography.orEmpty(), p.birthday, p.placeOfBirth, Img.profile(p.profilePath), p.knownForDepartment.orEmpty(),
            p.movieCredits?.cast.orEmpty().sortedByDescending { it.popularity }.movies(),
            p.movieCredits?.crew.orEmpty().filter { it.job == "Director" }.sortedByDescending { it.popularity }.movies()
        )
    }

    suspend fun season(tvId: Int, number: Int): List<Episode> = tmdbCall {
        tmdb.season(tvId, number).episodes.map {
            Episode(it.episodeNumber, it.name.orEmpty(), it.overview.orEmpty(), Img.still(it.stillPath), it.runtime, it.airDate)
        }
    }

    /** Recommendations seeded from the user's library and recent history. */
    suspend fun personalRecommendations(): List<MediaItem> {
        if (!prefs.hasKey) return emptyList()
        val seeds = LinkedHashSet<Pair<MediaType, Int>>()
        dao.recentLibrary().forEach { seeds += MediaType.from(it.type) to it.tmdbId }
        dao.recentHistory().forEach { h ->
            val parts = h.mediaKey.split(":")
            if (parts.size == 2 && (parts[0] == "movie" || parts[0] == "tv")) {
                parts[1].toIntOrNull()?.let { seeds += MediaType.from(parts[0]) to it }
            }
        }
        if (seeds.isEmpty()) return emptyList()
        val owned = seeds.map { "${it.first.path}:${it.second}" }.toSet()
        return coroutineScope {
            seeds.take(5).map { (t, id) -> async { runCatching { recommendations(t, id) }.getOrDefault(emptyList()) } }
                .flatMap { it.await() }
        }.distinctBy { it.key }.filter { it.key !in owned }.take(20)
    }

    // ---------- Internet Archive (public-domain films) ----------
    private fun ArchiveDoc.toFree() = FreeItem(identifier, title.asText() ?: identifier, year.asText())

    suspend fun freeToWatch(page: Int = 1): List<FreeItem> =
        archive.search("collection:feature_films AND mediatype:movies", listOf("identifier", "title", "year"), 24, page, "downloads desc")
            .response.docs.map { it.toFree() }

    suspend fun findFree(title: String, year: String?): List<FreeItem> {
        fun norm(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val clean = title.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (clean.isBlank()) return emptyList()
        val docs = archive.search(
            "title:($clean) AND collection:feature_films AND mediatype:movies",
            listOf("identifier", "title", "year"), 10, 1, "downloads desc"
        ).response.docs.map { it.toFree() }
        return docs.filter { norm(it.title).contains(norm(title)) && (year.isNullOrBlank() || it.year.isNullOrBlank() || it.year == year) }
    }

    /**
     * Verifies an Internet Archive item before it is offered: curated Feature Films collection, type movies,
     * not restricted/removed, a real video file, and explicit licence metadata for downloading.
     */
    suspend fun verifyFree(item: FreeItem): VerifiedFree? {
        val meta = archive.metadata(item.identifier)
        if (meta.isDark == true) return null
        val md = meta.metadata
        if (md["access-restricted-item"].asText().equals("true", true)) return null
        if (md["mediatype"].asText() != "movies") return null
        val collections = (md["collection"] as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: listOfNotNull(md["collection"].asText())
        if (collections.none { it == "feature_films" }) return null
        val files = meta.files
        val mp4 = files.filter { it.name.endsWith(".mp4", true) }
        val video = mp4.firstOrNull { it.format.equals("h.264", true) } ?: mp4.maxByOrNull { it.size?.toLongOrNull() ?: 0L } ?: return null
        val lic = md["licenseurl"].asText().orEmpty()
        val pdStatus = md["possible-copyright-status"].asText().orEmpty()
        val explicit = lic.contains("publicdomain", true) || lic.contains("creativecommons", true) || pdStatus.equals("NOT_IN_COPYRIGHT", true)
        val rights = when {
            lic.contains("publicdomain", true) -> "Public domain (licence: $lic)"
            lic.isNotBlank() -> "Licence: $lic"
            pdStatus.equals("NOT_IN_COPYRIGHT", true) -> "Marked not in copyright by the Internet Archive"
            else -> "Internet Archive Feature Films collection; no licence metadata (stream only, rights vary by country)"
        }
        fun url(n: String) = "https://archive.org/download/${item.identifier}/" + Uri.encode(n, "/")
        val subs = files.filter { it.name.endsWith(".srt", true) || it.name.endsWith(".vtt", true) }.map { url(it.name) }
        return VerifiedFree(item, url(video.name), subs, video.size?.toLongOrNull() ?: 0L, rights, explicit)
    }

    suspend fun findVerifiedFree(title: String, year: String?): List<VerifiedFree> = coroutineScope {
        findFree(title, year).take(3).map { async { runCatching { verifyFree(it) }.getOrNull() } }.mapNotNull { it.await() }
    }

    // ---------- Local data ----------
    fun libraryItem(key: String): Flow<LibraryEntity?> = dao.libraryItem(key)
    fun sources(key: String): Flow<List<SourceEntity>> = dao.sources(key)

    suspend fun setFlags(item: MediaItem, watchlist: Boolean? = null, favorite: Boolean? = null) {
        val cur = dao.getLibraryItem(item.key) ?: LibraryEntity(
            item.key, item.id, item.type.path, item.title, item.overview, item.posterUrl, item.backdropUrl,
            item.year, item.rating, false, false, System.currentTimeMillis()
        )
        val upd = cur.copy(inWatchlist = watchlist ?: cur.inWatchlist, isFavorite = favorite ?: cur.isFavorite)
        if (!upd.inWatchlist && !upd.isFavorite) dao.deleteLibrary(upd.uid) else dao.upsertLibrary(upd)
    }

    suspend fun addSource(mediaKey: String, label: String, url: String) =
        dao.insertSource(SourceEntity(mediaKey = mediaKey, label = label, url = url))

    suspend fun deleteSource(id: Long) = dao.deleteSource(id)

    suspend fun getProgress(uid: String): Long =
        dao.getHistory(uid)?.takeIf { !it.finished }?.positionMs ?: 0L

    suspend fun saveProgress(
        uid: String, mediaKey: String, title: String, subtitle: String?, poster: String?, backdrop: String?,
        url: String, positionMs: Long, durationMs: Long
    ) {
        val finished = durationMs > 0 && positionMs >= durationMs * 0.95
        dao.upsertHistory(
            HistoryEntity(uid, mediaKey, title, subtitle, poster, backdrop, url,
                if (finished) 0L else positionMs, durationMs, finished, System.currentTimeMillis())
        )
    }
}

fun LibraryEntity.toItem() = MediaItem(
    tmdbId, MediaType.from(type), title, overview, posterUrl, backdropUrl, rating, year, emptyList(), null
)
