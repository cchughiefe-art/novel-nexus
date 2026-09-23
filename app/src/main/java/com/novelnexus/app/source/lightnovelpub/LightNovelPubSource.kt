package com.novelnexus.app.source.lightnovelpub

import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.core.model.NovelDetails
import com.novelnexus.app.core.network.HttpClient
import com.novelnexus.app.core.source.NovelSource
import com.novelnexus.app.core.source.absolute
import com.novelnexus.app.core.source.cleanChapterHtml
import com.novelnexus.app.core.source.cleanChapterText
import com.novelnexus.app.core.source.firstAttr
import com.novelnexus.app.core.source.firstText
import com.novelnexus.app.core.source.parseHtml
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LightNovelPubSource(private val http: HttpClient) : NovelSource {
    override val id = "lightnovelpub"
    override val name = "Light Novel Pub"
    override val baseUrl = "https://lightnovelpub.me"
    override val supportsLatest = false

    override suspend fun search(query: String, page: Int): List<NovelCard> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val candidates = listOf(
            "$baseUrl/search?inputContent=$q",
            "$baseUrl/search?keyword=$q"
        )
        var last: Throwable? = null
        for (url in candidates) {
            try {
                val items = parseCards(http.get(url, referer = baseUrl))
                if (items.isNotEmpty()) return items
            } catch (t: Throwable) { last = t }
        }
        if (last != null) throw last
        return emptyList()
    }

    private fun parseCards(html: String): List<NovelCard> {
        val doc = parseHtml(html, baseUrl)
        return doc.select(".novel-item, .li-row, .row, .search-result-item").mapNotNull { row ->
            val link = row.selectFirst(".novel-title a[href], h3 a[href], h4 a[href], a[href*=/book/], a[href*=/novel/]")
                ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            NovelCard(
                sourceId = id,
                title = title,
                url = href,
                coverUrl = row.selectFirst("img")?.let { img ->
                    img.absUrl("data-src").ifBlank { img.absUrl("src") }
                }.takeUnless { it.isNullOrBlank() },
                author = row.selectFirst(".author, .novel-author")?.text()?.trim()
            )
        }.distinctBy { it.url }
    }

    override suspend fun novel(url: String): NovelDetails {
        val html = http.get(url, referer = baseUrl)
        val doc = parseHtml(html, url)
        val title = doc.firstText(".m-desc .tit", "h1.novel-title", "h1") ?: "Untitled"
        val cover = doc.firstAttr("src", ".m-book1 .pic img", ".m-book1 img", "figure.cover img")
            ?: doc.firstAttr("data-src", ".m-book1 .pic img", ".m-book1 img", "figure.cover img")
        val author = doc.selectFirst(".m-book1 .item [title=Author] + .right a, .m-book1 .item .right a, .author a")?.text()?.trim()
        val description = doc.selectFirst(".m-desc .inner, .abstract + .txt, .summary .content")?.text()?.trim().orEmpty()
        val genres = doc.select(".categories a, a[href*=genre], a[href*=category]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val status = doc.select(".m-book1 .item, .header-stats span").firstOrNull { it.text().contains("Status", true) }
            ?.text()?.substringAfter(":")?.trim()
        return NovelDetails(id, title, url, cover, author, description, genres, status, chapters(url))
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val seen = linkedMapOf<String, ChapterRef>()
        var current: String? = url
        var page = 1
        val visited = mutableSetOf<String>()
        while (!current.isNullOrBlank() && page <= 80 && current !in visited) {
            visited += current
            val html = http.get(current, referer = if (page == 1) baseUrl else url)
            val doc = parseHtml(html, current)
            val links = doc.select(".m-newest2 .ul-list5 a.con, .chapter-list a, a[href*=chapter-]")
            links.forEach { link ->
                val href = link.absUrl("href").ifBlank { absolute(current, link.attr("href")).orEmpty() }
                val title = link.attr("title").ifBlank { link.text() }.trim()
                if (href.isNotBlank() && title.isNotBlank() && !seen.containsKey(href)) {
                    seen[href] = ChapterRef(id, url, title, href, seen.size)
                }
            }
            val next = doc.select(".index-container-btn, a[rel=next], .pagination a").firstOrNull {
                it.text().trim().equals("next", ignoreCase = true)
            }?.let { link ->
                link.absUrl("href").ifBlank { absolute(current, link.attr("href")).orEmpty() }
            }
            if (next.isNullOrBlank()) break
            current = next
            page++
        }
        return seen.values.toList().mapIndexed { index, ref -> ref.copy(index = index) }
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val html = http.get(ref.url, referer = ref.novelUrl)
        val doc = parseHtml(html, ref.url)
        val container = doc.selectFirst(".txt, .read-content, .chapter-content, #chapter-container, article")
            ?: error("Chapter content not found")
        val title = doc.firstText("h1", "h2", ".chapter-title") ?: ref.title
        return ChapterContent(id, ref.novelUrl, ref.url, title, ref.index, cleanChapterHtml(container), cleanChapterText(container))
    }
}
