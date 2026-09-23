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

class NovelRepository(
    val sources: SourceRegistry,
    private val db: NovelDatabase
) {
    data class SourceResult<T>(val sourceId: String, val data: T?, val error: String?)

    suspend fun searchAll(query: String): List<SourceResult<List<NovelCard>>> = coroutineScope {
        sources.enabled().map { source ->
            async {
                runCatching { source.search(query) }
                    .fold(
                        onSuccess = { SourceResult(source.id, it, null) },
                        onFailure = { SourceResult(source.id, null, it.message ?: "Source failed") }
                    )
            }
        }.awaitAll()
    }

    suspend fun latest(): List<SourceResult<List<NovelCard>>> = coroutineScope {
        sources.enabled().filter { it.supportsLatest }.map { source ->
            async {
                runCatching { source.latest() }
                    .fold(
                        onSuccess = { SourceResult(source.id, it, null) },
                        onFailure = { SourceResult(source.id, null, it.message ?: "Source failed") }
                    )
            }
        }.awaitAll()
    }

    suspend fun novel(sourceId: String, url: String): NovelDetails {
        return runCatching { sources.require(sourceId).novel(url) }
            .getOrElse { failure ->
                db.offlineNovelDetails(sourceId, url) ?: throw failure
            }
    }

    suspend fun chapters(sourceId: String, url: String): List<ChapterRef> {
        return runCatching { sources.require(sourceId).chapters(url) }
            .getOrElse { failure ->
                db.downloadedChapters(sourceId, url).takeIf { it.isNotEmpty() } ?: throw failure
            }
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
