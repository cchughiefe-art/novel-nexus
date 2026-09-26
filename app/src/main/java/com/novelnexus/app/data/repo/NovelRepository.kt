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
    data class SourceResult<T>(val sourceId: String, val data: T?, val error: String?)
    data class NovelRefresh(val details: NovelDetails, val newChapterCount: Int)
    data class ChapterLoad(val content: ChapterContent, val origin: String)
    data class ChapterEditCheck(val changed: Boolean, val fresh: ChapterContent?)

    private data class Timed<T>(val value: T, val at: Long = System.currentTimeMillis())
    private val detailsCache = ConcurrentHashMap<String, Timed<NovelDetails>>()
    private val chaptersCache = ConcurrentHashMap<String, Timed<List<ChapterRef>>>()
    private val searchCache = ConcurrentHashMap<String, Timed<List<SourceResult<List<NovelCard>>>>>()
    private var latestCache: Timed<List<SourceResult<List<NovelCard>>>>? = null

    private fun cacheKey(sourceId: String, url: String) = "$sourceId::$url"
    private fun <T> Timed<T>?.fresh(ttl: Long): T? = if (this != null && System.currentTimeMillis()-at <= ttl) value else null
    private fun trim() {
        if (detailsCache.size > 80) detailsCache.clear()
        if (chaptersCache.size > 80) chaptersCache.clear()
        if (searchCache.size > 30) searchCache.clear()
    }

    suspend fun searchAll(query: String): List<SourceResult<List<NovelCard>>> = coroutineScope {
        val enabled = sources.enabled()
        val key = enabled.joinToString(",") { it.id } + "::" + query.trim().lowercase()
        searchCache[key].fresh(120_000L)?.let { return@coroutineScope it }
        val result = enabled.map { source -> async {
            runCatching { source.search(query) }.fold(
                onSuccess = { SourceResult<List<NovelCard>>(source.id, it, null) },
                onFailure = { SourceResult<List<NovelCard>>(source.id, null, it.message ?: "Source failed") }
            )
        }}.awaitAll()
        if (result.any { !it.data.isNullOrEmpty() }) { searchCache[key]=Timed(result); trim() }
        result
    }

    suspend fun latest(): List<SourceResult<List<NovelCard>>> = coroutineScope {
        latestCache.fresh(120_000L)?.let { return@coroutineScope it }
        val result = sources.enabled().filter { it.supportsLatest }.map { source -> async {
            runCatching { source.latest() }.fold(
                onSuccess = { SourceResult<List<NovelCard>>(source.id, it, null) },
                onFailure = { SourceResult<List<NovelCard>>(source.id, null, it.message ?: "Source failed") }
            )
        }}.awaitAll()
        if (result.any { !it.data.isNullOrEmpty() }) latestCache=Timed(result)
        result
    }

    suspend fun novel(sourceId: String, url: String): NovelDetails {
        val key=cacheKey(sourceId,url)
        detailsCache[key].fresh(300_000L)?.let { return it }

        db.offlineNovelDetails(sourceId,url)?.let { offline ->
            detailsCache[key]=Timed(offline)
            if(offline.chapters.isNotEmpty()) {
                chaptersCache[key]=Timed(offline.chapters)
            }
            return offline
        }

        return runCatching {
            sources.require(sourceId).novel(url)
        }.fold(
            onSuccess = { details ->
                detailsCache[key]=Timed(details)
                if (details.chapters.isNotEmpty()) {
                    chaptersCache[key]=Timed(details.chapters)
                    db.saveChapterCatalog(sourceId,url,details.chapters)
                }
                trim()
                details
            },
            onFailure = { failure ->
                db.offlineNovelDetails(sourceId,url) ?: throw failure
            }
        )
    }

    suspend fun refreshNovel(sourceId: String, url: String): NovelRefresh {
        val key=cacheKey(sourceId,url)
        val previous = db.cachedChapterCatalog(sourceId,url)
        val previousUrls = previous.mapTo(mutableSetOf()) { it.url }
        val details = sources.require(sourceId).novel(url)
        val newCount = if (previousUrls.isEmpty()) 0 else details.chapters.count { it.url !in previousUrls }
        detailsCache[key]=Timed(details)
        chaptersCache[key]=Timed(details.chapters)
        if (details.chapters.isNotEmpty()) db.saveChapterCatalog(sourceId,url,details.chapters)
        trim()
        return NovelRefresh(details,newCount)
    }

    suspend fun chapters(sourceId: String, url: String): List<ChapterRef> {
        val key=cacheKey(sourceId,url)
        chaptersCache[key].fresh(600_000L)?.let { return it }

        val local = db.cachedChapterCatalog(sourceId,url)
            .takeIf { it.isNotEmpty() }
            ?: db.downloadedChapters(sourceId,url).takeIf { it.isNotEmpty() }

        if(local != null) {
            chaptersCache[key]=Timed(local)
            return local
        }

        return runCatching {
            sources.require(sourceId).chapters(url)
        }.fold(
            onSuccess = { list ->
                chaptersCache[key]=Timed(list)
                if (list.isNotEmpty()) {
                    db.saveChapterCatalog(sourceId,url,list)
                }
                trim()
                list
            },
            onFailure = { failure ->
                db.cachedChapterCatalog(sourceId,url).takeIf { it.isNotEmpty() }
                    ?: db.downloadedChapters(sourceId,url)
                        .takeIf { it.isNotEmpty() }
                    ?: throw failure
            }
        )
    }

    suspend fun refreshChapters(sourceId: String, url: String): Pair<List<ChapterRef>,Int> {
        val previous=db.cachedChapterCatalog(sourceId,url)
        val previousUrls=previous.mapTo(mutableSetOf()) { it.url }
        val fresh=sources.require(sourceId).chapters(url)
        val count=if(previousUrls.isEmpty()) 0 else fresh.count { it.url !in previousUrls }
        if(fresh.isNotEmpty()) {
            db.saveChapterCatalog(sourceId,url,fresh)
            chaptersCache[cacheKey(sourceId,url)]=Timed(fresh)
        }
        return fresh to count
    }

    suspend fun chapterLoad(ref: ChapterRef): ChapterLoad {
        db.getChapter(ref.sourceId,ref.url)?.let { local ->
            val verified = runCatching {
                db.verifyDownloadedChapter(ref.sourceId,ref.url)
            }.getOrDefault(false)

            return ChapterLoad(
                local,
                if(verified) "Downloaded · Offline ready"
                else "Downloaded · Offline copy"
            )
        }

        return ChapterLoad(
            sources.require(ref.sourceId).chapter(ref),
            "Network / cache"
        )
    }

    suspend fun checkDownloadedChapterEdit(ref: ChapterRef): ChapterEditCheck {
        if (!db.shouldCheckForEdit(ref.sourceId, ref.url)) return ChapterEditCheck(false,null)
        val remote=runCatching { sources.require(ref.sourceId).chapter(ref) }.getOrNull() ?: return ChapterEditCheck(false,null)
        db.markChapterChecked(ref.sourceId,ref.url)
        val local=db.chapterChecksum(ref.sourceId,ref.url)
        val remoteHash=db.contentChecksum(remote.plainText)
        return ChapterEditCheck(local!=null && local!=remoteHash, if(local!=remoteHash) remote else null)
    }

    fun acceptEditedChapter(content: ChapterContent) { db.saveChapter(content) }

    suspend fun chapter(ref: ChapterRef): ChapterContent = chapterLoad(ref).content

    suspend fun prefetch(ref: ChapterRef) {
        if (db.isDownloaded(ref.sourceId,ref.url)) return
        runCatching { sources.require(ref.sourceId).chapter(ref) }
    }

    suspend fun downloadChapter(ref: ChapterRef): ChapterContent {
        db.getChapter(ref.sourceId,ref.url)?.let { return it }
        val chapter=sources.require(ref.sourceId).chapter(ref)
        db.saveChapter(chapter)
        return chapter
    }

    fun db(): NovelDatabase = db
}
