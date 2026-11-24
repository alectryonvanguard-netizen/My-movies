package com.mymovie.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1
)

@Serializable
data class MediaDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null
)

@Serializable data class GenreDto(val id: Int, val name: String)
@Serializable data class CastDto(val name: String, val character: String? = null, @SerialName("profile_path") val profilePath: String? = null)
@Serializable data class CreditsDto(val cast: List<CastDto> = emptyList())
@Serializable data class VideoDto(val key: String, val site: String? = null, val type: String? = null)
@Serializable data class VideosDto(val results: List<VideoDto> = emptyList())
@Serializable data class ProviderDto(@SerialName("provider_name") val name: String)

@Serializable
data class ProviderRegionDto(
    val link: String? = null,
    val flatrate: List<ProviderDto> = emptyList(),
    val free: List<ProviderDto> = emptyList(),
    val ads: List<ProviderDto> = emptyList(),
    val rent: List<ProviderDto> = emptyList(),
    val buy: List<ProviderDto> = emptyList()
)

@Serializable data class WatchProvidersDto(val results: Map<String, ProviderRegionDto> = emptyMap())

@Serializable
data class SeasonSummaryDto(
    @SerialName("season_number") val seasonNumber: Int,
    val name: String? = null,
    @SerialName("episode_count") val episodeCount: Int = 0
)

@Serializable
data class DetailDto(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val seasons: List<SeasonSummaryDto> = emptyList(),
    val credits: CreditsDto? = null,
    val videos: VideosDto? = null,
    @SerialName("watch/providers") val providers: WatchProvidersDto? = null
)

@Serializable
data class EpisodeDto(
    @SerialName("episode_number") val episodeNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("still_path") val stillPath: String? = null,
    val runtime: Int? = null,
    @SerialName("air_date") val airDate: String? = null
)

@Serializable data class SeasonDto(val episodes: List<EpisodeDto> = emptyList())
