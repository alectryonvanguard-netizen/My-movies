package com.mymovie.app.data

enum class MediaType(val path: String) {
    MOVIE("movie"), TV("tv");

    companion object {
        fun from(s: String?) = if (s == "tv") TV else MOVIE
    }
}

object Img {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(p: String?) = p?.let { "${BASE}w342$it" }
    fun backdrop(p: String?) = p?.let { "${BASE}w1280$it" }
    fun profile(p: String?) = p?.let { "${BASE}w185$it" }
    fun still(p: String?) = p?.let { "${BASE}w300$it" }
}

/** Discover params that define "anime": Japanese-language animation. */
val ANIME_PARAMS = mapOf("with_genres" to "16", "with_original_language" to "ja")

data class MediaItem(
    val id: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double,
    val year: String,
    val genreIds: List<Int>,
    val language: String?
) {
    val key: String get() = "${type.path}:$id"
}

data class FreeItem(val identifier: String, val title: String, val year: String?) {
    val thumb: String get() = "https://archive.org/services/img/$identifier"
}

data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int)

data class CastMember(val name: String, val character: String, val photoUrl: String?)
data class Episode(val number: Int, val name: String, val overview: String, val stillUrl: String?, val runtime: Int?, val airDate: String?)
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int)
data class Providers(val link: String?, val free: List<String>, val subscription: List<String>, val rentBuy: List<String>)

data class MediaDetail(
    val item: MediaItem,
    val tagline: String,
    val runtime: Int?,
    val genres: List<String>,
    val cast: List<CastMember>,
    val trailerKey: String?,
    val providers: Providers?,
    val seasons: List<SeasonInfo>
)

data class PlayableSource(val label: String, val url: String, val subtitles: List<String> = emptyList())
