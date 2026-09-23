package com.novelnexus.app.data.repo

import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.core.model.NovelDetails
import com.novelnexus.app.core.source.SourceRegistry
import com.novelnexus.app.data.db.NovelDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

class NovelRepository(
    val sources: SourceRegistry,
    private val db: NovelDatabase
) {
    data class SourceResult<T>(
        val sourceId: String,
        val data: T?,
        val error: String?
    )

    private data class Timed<T>(
        val value: T,
        val at: Long = System.currentTimeMillis()
    )

    private val detailsCache = ConcurrentHashMap<String, Timed<NovelDetails>>()
    private val chaptersCache = ConcurrentHashMap<String, Timed<List<ChapterRef>>>()
    private val searchCache =
        ConcurrentHashMap<String, Timed<List<SourceResult<List<NovelCard>>>>>()
    private var latestCache: Timed<List<SourceResult<List<NovelCard>>>>? = null

    private fun cacheKey(sourceId: String, url: String) = "$sourceId::$url"

    private fun <T> Timed<T>?.fresh(ttl: Long): T? {
        if (this == null) return null
        return if (System.currentTimeMillis() - at <= ttl) value else null
    }

    private fun trim() {
        if (detailsCache.size > 80) detailsCache.clear()
        if (chaptersCache.size > 80) chaptersCache.clear()
        if (searchCache.size > 30) searchCache.clear()
    }

    suspend fun searchAll(
        query: String
    ): List<SourceResult<List<NovelCard>>> = coroutineScope {
        val enabled = sources.enabled()
        val key = enabled.joinToString(",") { it.id } + "::" + query.trim().lowercase()

        searchCache[key].fresh(120_000L)?.let {
            return@coroutineScope it
        }

        val result = enabled.map { source ->
            async {
                runCatching { source.search(query) }
                    .fold(
                        onSuccess = {
                            SourceResult<List<NovelCard>>(source.id, it, null)
                        },
                        onFailure = {
                            SourceResult<List<NovelCard>>(
                                source.id,
                                null,
                                it.message ?: "Source failed"
                            )
                        }
                    )
            }
        }.awaitAll()

        if (result.any { !it.data.isNullOrEmpty() }) {
            searchCache[key] = Timed(result)
            trim()
        }

        result
    }

    suspend fun latest(): List<SourceResult<List<NovelCard>>> = coroutineScope {
        latestCache.fresh(120_000L)?.let {
            return@coroutineScope it
        }

        val result = sources.enabled()
            .filter { it.supportsLatest }
            .map { source ->
                async {
                    runCatching { source.latest() }
                        .fold(
                            onSuccess = {
                                SourceResult<List<NovelCard>>(source.id, it, null)
                            },
                            onFailure = {
                                SourceResult<List<NovelCard>>(
                                    source.id,
                                    null,
                                    it.message ?: "Source failed"
                                )
                            }
                        )
                }
            }
            .awaitAll()

        if (result.any { !it.data.isNullOrEmpty() }) {
            latestCache = Timed(result)
        }

        result
    }

    suspend fun novel(sourceId: String, url: String): NovelDetails {
        val key = cacheKey(sourceId, url)
        detailsCache[key].fresh(300_000L)?.let { return it }

        return runCatching { sources.require(sourceId).novel(url) }
            .fold(
                onSuccess = { details ->
                    detailsCache[key] = Timed(details)
                    if (details.chapters.isNotEmpty()) {
                        chaptersCache[key] = Timed(details.chapters)
                    }
                    trim()
                    details
                },
                onFailure = { failure ->
                    db.offlineNovelDetails(sourceId, url) ?: throw failure
                }
            )
    }

    suspend fun chapters(sourceId: String, url: String): List<ChapterRef> {
        val key = cacheKey(sourceId, url)
        chaptersCache[key].fresh(600_000L)?.let { return it }

        return runCatching { sources.require(sourceId).chapters(url) }
            .fold(
                onSuccess = { chapters ->
                    chaptersCache[key] = Timed(chapters)
                    trim()
                    chapters
                },
                onFailure = { failure ->
                    db.downloadedChapters(sourceId, url)
                        .takeIf { it.isNotEmpty() }
                        ?: throw failure
                }
            )
    }

    suspend fun chapter(ref: ChapterRef): ChapterContent {
        db.getChapter(ref.sourceId, ref.url)?.let { return it }
        return sources.require(ref.sourceId).chapter(ref)
    }

    suspend fun downloadChapter(ref: ChapterRef): ChapterContent {
        db.getChapter(ref.sourceId, ref.url)?.let { return it }
        val chapter = sources.require(ref.sourceId).chapter(ref)
        db.saveChapter(chapter)
        return chapter
    }

    fun db(): NovelDatabase = db
}
