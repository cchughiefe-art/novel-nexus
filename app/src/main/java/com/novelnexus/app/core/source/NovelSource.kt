package com.novelnexus.app.core.source

import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.core.model.NovelDetails

interface NovelSource {
    val id: String
    val name: String
    val baseUrl: String
    val supportsLatest: Boolean get() = true
    val supportsPopular: Boolean get() = false

    suspend fun search(query: String, page: Int = 1): List<NovelCard>
    suspend fun latest(page: Int = 1): List<NovelCard> = emptyList()
    suspend fun popular(page: Int = 1): List<NovelCard> = emptyList()
    suspend fun novel(url: String): NovelDetails
    suspend fun chapters(url: String): List<ChapterRef>
    suspend fun chapter(ref: ChapterRef): ChapterContent
}
