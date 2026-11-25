package com.mymovie.app.data

enum class MediaType(val path: String) {
    MOVIE("movie"), TV("tv");
    companion object { fun from(s: String?) = if (s == "tv") TV else MOVIE }
}

object Img {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(p: String?) = p?.let { "${BASE}w342$it" }
    fun backdrop(p: String?) = p?.let { "${BASE}w1280$it" }
    fun profile(p: String?) = p?.let { "${BASE}w185$it" }
    fun still(p: String?) = p?.let { "${BASE}w300$it" }
}

val ANIME_PARAMS = mapOf("with_genres" to "16", "with_original_language" to "ja")

data class MediaItem(
    val id: Int, val type: MediaType, val title: String, val overview: String,
    val posterUrl: String?, val backdropUrl: String?, val rating: Double, val year: String,
    val genreIds: List<Int>, val language: String?
) { val key: String get() = "${type.path}:$id" }

data class FreeItem(val identifier: String, val title: String, val year: String?) {
    val thumb: String get() = "https://archive.org/services/img/$identifier"
}

data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int)
data class CastMember(val name: String, val character: String, val photoUrl: String?, val id: Int = 0)
data class CrewMember(val name: String, val job: String, val id: Int)

enum class VideoKind(val label: String) { TRAILER("Trailer"), TEASER("Teaser"), CLIP("Clip"), PREVIEW("Preview"), OTHER("Video") }
data class VideoInfo(val key: String, val name: String, val kind: VideoKind) {
    val url get() = "https://www.youtube.com/watch?v=$key"
}

data class Episode(val number: Int, val name: String, val overview: String, val stillUrl: String?, val runtime: Int?, val airDate: String?)
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int)
data class Providers(val link: String?, val free: List<String>, val subscription: List<String>, val rentBuy: List<String>)

data class MediaDetail(
    val item: MediaItem, val tagline: String, val runtime: Int?, val genres: List<String>,
    val cast: List<CastMember>, val crew: List<CrewMember>, val directors: List<String>,
    val videos: List<VideoInfo>, val providers: Providers?, val seasons: List<SeasonInfo>,
    val releaseDate: String, val countries: List<String>, val languages: List<String>,
    val originalLanguage: String, val originalTitle: String, val altTitles: List<String>,
    val popularity: Double, val voteCount: Int, val status: String,
    val similar: List<MediaItem>, val recommended: List<MediaItem>
)

/** An Internet Archive film whose collection, access flags and licence metadata were checked. */
data class VerifiedFree(
    val item: FreeItem, val url: String, val subtitles: List<String>,
    val sizeBytes: Long, val rights: String, val downloadable: Boolean
)

data class PersonBrief(val id: Int, val name: String, val photoUrl: String?, val department: String)
data class PersonInfo(
    val id: Int, val name: String, val bio: String, val birthday: String?, val birthplace: String?,
    val photoUrl: String?, val department: String, val known: List<MediaItem>, val directed: List<MediaItem>
)
