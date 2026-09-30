package com.novelnexus.app.source.novelfullparse

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

/** Parse uses POST plus `_method: GET`, including for reads. `where` is a JSON string. */
interface NovelFullApi {
    @POST("vbparse/novel/classes/Novel")
    suspend fun novels(
        @HeaderMap headers: Map<String, String>,
        @Body query: ParseQuery
    ): NovelResponse

    @POST("vbparse/novel/classes/Chapter")
    suspend fun chapters(
        @HeaderMap headers: Map<String, String>,
        @Body query: ParseQuery
    ): ChapterResponse
}

data class ParseQuery(
    val where: String? = null,
    val limit: Int? = null,
    val skip: Int? = null,
    val order: String? = null,
    val keys: String? = null,
    @SerializedName("_method") val method: String = "GET"
)

data class NovelResponse(val results: List<NovelItem> = emptyList())

data class NovelItem(
    val objectId: String? = null,
    val title: String? = null,
    val info: String? = null,
    val image: JsonElement? = null,
    val chapterCount: Int? = null,
    val genres: List<String>? = null,
    val authors: List<String>? = null,
    val latestChapter: JsonElement? = null,
    val isCompleted: Boolean? = null
)

data class ChapterResponse(val results: List<ChapterItem> = emptyList())

data class ChapterItem(
    val objectId: String? = null,
    val title: String? = null,
    val content: String? = null,
    val index: Int? = null
)
