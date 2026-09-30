package com.novelnexus.app.source.novelfullparse

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.core.model.NovelDetails
import com.novelnexus.app.core.source.NovelSource
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/** NovelFull's app API, registered separately from the NovelFull website sources. */
class NovelNexusProvider(context: Context) : NovelSource {
    override val id = "novelfull-app"
    override val name = "NovelFull app"
    override val baseUrl = "http://mapp4u.com:1337/"
    override val supportsPopular = true
    override val supportsGenres = true

    private val gson = Gson()
    private val installationId: String = context.getSharedPreferences("novelfull_parse", Context.MODE_PRIVATE)
        .let { preferences ->
            preferences.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
                preferences.edit().putString("installation_id", it).apply()
            }
        }

    private val headers: Map<String, String> = mapOf(
        "X-Parse-Application-Id" to "N@v)(KKddjjeQQQMMM848jffj1!@@dmd$$\$kdkoeiwl",
        "X-Parse-Client-Version" to "a1.17.3",
        "X-Parse-App-Build-Version" to "13801",
        "X-Parse-App-Display-Version" to "1.3.8",
        "X-Parse-OS-Version" to "15",
        "User-Agent" to "Parse Android SDK 1.17.3 (tlt.online.free.book.novelfull/13801) API Level 35",
        "X-Parse-Installation-Id" to installationId,
        "Content-Type" to "application/json"
    )

    private val api: NovelFullApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(NovelFullApi::class.java)

    private fun novelUrl(objectId: String) = "${baseUrl}vbparse/novel/novels/$objectId"
    private fun chapterUrl(objectId: String) = "${baseUrl}vbparse/novel/chapters/$objectId"

    private fun idFromUrl(url: String, type: String): String {
        val prefix = "${baseUrl}vbparse/novel/$type/"
        require(url.startsWith(prefix)) { "Invalid NovelFull app URL" }
        return url.removePrefix(prefix).takeIf { it.isNotBlank() && '/' !in it && '?' !in it }
            ?: error("Missing NovelFull app ID")
    }

    private fun imageUrl(image: JsonElement?): String? = when {
        image == null || image.isJsonNull -> null
        image.isJsonPrimitive -> image.asString.takeIf { it.startsWith("http") }
        image.isJsonObject -> image.asJsonObject.get("url")?.takeIf { it.isJsonPrimitive }?.asString
        else -> null
    }

    private fun NovelItem.toCard(): NovelCard? {
        val objectId = objectId?.takeIf(String::isNotBlank) ?: return null
        val title = title?.takeIf(String::isNotBlank) ?: return null
        val latest = latestChapter?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("title")?.takeIf { it.isJsonPrimitive }?.asString
        return NovelCard(id, title, novelUrl(objectId), imageUrl(image),
            authors.orEmpty().joinToString(", ").takeIf { it.isNotBlank() }, latest,
            if (isCompleted == true) "Completed" else null)
    }

    /** The verified home/discover order is chapter count, descending. */
    suspend fun fetchTrending(page: Int = 1): List<NovelCard> =
        api.novels(headers, ParseQuery(limit = 20, skip = (page.coerceAtLeast(1) - 1) * 20,
            order = "-chapterCount")).results.mapNotNull { it.toCard() }

    suspend fun fetchByGenre(genre: String, page: Int = 1): List<NovelCard> {
        if (genre.isBlank()) return emptyList()
        val where = gson.toJson(mapOf("genres" to genre.trim()))
        return api.novels(headers, ParseQuery(where = where, limit = 20,
            skip = (page.coerceAtLeast(1) - 1) * 20)).results.mapNotNull { it.toCard() }
    }

    suspend fun searchNovels(query: String, page: Int = 1): List<NovelCard> {
        if (query.isBlank()) return emptyList()
        // Keep user input literal inside the server-side regex.
        val safe = java.util.regex.Pattern.quote(query.trim())
        val expression = JsonObject().apply {
            addProperty("\$regex", safe)
            addProperty("\$options", "i")
        }
        val where = JsonObject().apply { add("title", expression) }
        return api.novels(headers, ParseQuery(where = gson.toJson(where), limit = 20,
            skip = (page.coerceAtLeast(1) - 1) * 20)).results.mapNotNull { it.toCard() }
    }

    suspend fun fetchNovelDetails(objectId: String): NovelDetails {
        require(objectId.isNotBlank()) { "Novel ID is required" }
        val where = gson.toJson(mapOf("objectId" to objectId))
        val item = api.novels(headers, ParseQuery(where = where)).results.firstOrNull()
            ?: throw IOException("NovelFull app novel not found: $objectId")
        val card = item.toCard() ?: throw IOException("NovelFull app novel has no title or ID")
        return NovelDetails(id, card.title, card.url, card.coverUrl, card.author,
            Jsoup.parse(item.info.orEmpty()).text(), item.genres.orEmpty(), card.status,
            chapters(card.url))
    }

    override suspend fun search(query: String, page: Int) = searchNovels(query, page)
    override suspend fun latest(page: Int) = fetchTrending(page)
    override suspend fun popular(page: Int) = fetchTrending(page)
    override suspend fun browseGenre(genre: String, page: Int) = fetchByGenre(genre, page)
    override suspend fun novel(url: String) = fetchNovelDetails(idFromUrl(url, "novels"))

    override suspend fun chapters(url: String): List<ChapterRef> {
        val objectId = idFromUrl(url, "novels")
        val pointer = JsonObject().apply {
            addProperty("__type", "Pointer")
            addProperty("className", "Novel")
            addProperty("objectId", objectId)
        }
        val where = gson.toJson(JsonObject().apply { add("novel", pointer) })
        val result = mutableListOf<ChapterRef>()
        var skip = 0
        do {
            val batch = api.chapters(headers, ParseQuery(where = where, limit = 1000,
                skip = skip, order = "index", keys = "objectId,title,index")).results
            batch.forEach { item ->
                val chapterId = item.objectId ?: return@forEach
                result += ChapterRef(id, url, item.title ?: "Chapter ${result.size + 1}",
                    chapterUrl(chapterId), item.index ?: result.size)
            }
            skip += batch.size
        } while (batch.size == 1000 && skip < 100_000)
        return result
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val objectId = idFromUrl(ref.url, "chapters")
        val where = gson.toJson(mapOf("objectId" to objectId))
        val item = api.chapters(headers, ParseQuery(where = where, limit = 1)).results.firstOrNull()
            ?: throw IOException("NovelFull app chapter not found: $objectId")
        val safeHtml = Jsoup.clean(item.content.orEmpty(), Safelist.basic())
        val text = Jsoup.parse(safeHtml).text()
        if (text.isBlank()) throw IOException("NovelFull app chapter is empty: $objectId")
        return ChapterContent(id, ref.novelUrl, ref.url, item.title ?: ref.title,
            ref.index, safeHtml, text)
    }
}
