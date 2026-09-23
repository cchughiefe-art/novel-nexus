package com.novelnexus.app.core.model

data class NovelCard(
    val sourceId: String,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val latestChapter: String? = null,
    val status: String? = null
)

data class NovelDetails(
    val sourceId: String,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val chapters: List<ChapterRef> = emptyList()
)

data class ChapterRef(
    val sourceId: String,
    val novelUrl: String,
    val title: String,
    val url: String,
    val index: Int
)

data class ChapterContent(
    val sourceId: String,
    val novelUrl: String,
    val chapterUrl: String,
    val title: String,
    val index: Int,
    val html: String,
    val plainText: String
)

data class SourceState(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val healthy: Boolean = true,
    val message: String? = null
)
