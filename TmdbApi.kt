package com.mymovie.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface TmdbApi {
    @GET("trending/{type}/week")
    suspend fun trending(@Path("type") type: String, @Query("page") page: Int): PagedResponse<MediaDto>

    @GET("{type}/{list}")
    suspend fun list(@Path("type") type: String, @Path("list") list: String, @Query("page") page: Int): PagedResponse<MediaDto>

    @GET("discover/{type}")
    suspend fun discover(@Path("type") type: String, @Query("page") page: Int, @QueryMap params: Map<String, String>): PagedResponse<MediaDto>

    @GET("search/{type}")
    suspend fun search(@Path("type") type: String, @Query("query") query: String, @Query("page") page: Int, @QueryMap params: Map<String, String>): PagedResponse<MediaDto>

    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("append_to_response") append: String
    ): DetailDto

    @GET("tv/{id}/season/{season}")
    suspend fun season(@Path("id") id: Int, @Path("season") season: Int): SeasonDto

    @GET("{type}/{id}/recommendations")
    suspend fun recommendations(@Path("type") type: String, @Path("id") id: Int): PagedResponse<MediaDto>
}
