package com.mymovie.app.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Internet Archive: public-domain / openly hosted feature films. */
interface ArchiveApi {
    @GET("advancedsearch.php")
    suspend fun search(
        @Query("q") q: String,
        @Query("fl[]") fields: List<String>,
        @Query("rows") rows: Int,
        @Query("page") page: Int,
        @Query("sort[]") sort: String,
        @Query("output") output: String = "json"
    ): ArchiveSearchResponse

    @GET("metadata/{id}")
    suspend fun metadata(@Path("id") id: String): ArchiveMetadata
}

@Serializable data class ArchiveSearchResponse(val response: ArchiveResponse = ArchiveResponse())
@Serializable data class ArchiveResponse(val docs: List<ArchiveDoc> = emptyList())
@Serializable data class ArchiveDoc(val identifier: String, val title: JsonElement? = null, val year: JsonElement? = null)
@Serializable data class ArchiveMetadata(val files: List<ArchiveFile> = emptyList())
@Serializable data class ArchiveFile(val name: String, val format: String? = null, val size: String? = null)

fun JsonElement?.asText(): String? = when (this) {
    is JsonPrimitive -> contentOrNull
    is JsonArray -> (firstOrNull() as? JsonPrimitive)?.contentOrNull
    else -> null
}
