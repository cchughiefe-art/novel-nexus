package com.novelatlas.app.source.freewebnovel

import com.novelatlas.app.core.model.ChapterContent
import com.novelatlas.app.core.model.ChapterRef
import com.novelatlas.app.core.model.NovelCard
import com.novelatlas.app.core.model.NovelDetails
import com.novelatlas.app.core.network.HttpClient
import com.novelatlas.app.core.source.NovelSource
import com.novelatlas.app.core.source.absolute
import com.novelatlas.app.core.source.cleanChapterHtml
import com.novelatlas.app.core.source.cleanChapterText
import com.novelatlas.app.core.source.firstAttr
import com.novelatlas.app.core.source.firstText
import com.novelatlas.app.core.source.parseHtml
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class FreeWebNovelSource(private val http: HttpClient) : NovelSource {
    override val id = "freewebnovel"
    override val name = "FreeWebNovel"
    override val baseUrl = "https://freewebnovel.com"

    override suspend fun search(query: String, page: Int): List<NovelCard> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val html = http.get("$baseUrl/search?keyword=$q&page=$page")
        return parseCards(html)
    }

    override suspend fun latest(page: Int): List<NovelCard> {
        val url = if (page <= 1) "$baseUrl/sort/latest-release" else "$baseUrl/sort/latest-release/$page"
        return parseCards(http.get(url))
    }

    private fun parseCards(html: String): List<NovelCard> {
        val doc = parseHtml(html, baseUrl)
        return doc.select(".list-truyen .row, .list-novel .row, .archive .row, .novel-item, .row").mapNotNull { row ->
            val link = row.selectFirst("h3 a[href], .truyen-title a[href], .novel-title a[href], a[href$=.html]")
                ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (href.isBlank() || title.isBlank() || href.contains("/chapter", ignoreCase = true)) return@mapNotNull null
            NovelCard(
                sourceId = id,
                title = title,
                url = href,
                coverUrl = row.selectFirst("img")?.let { img -> img.absUrl("data-src").ifBlank { img.absUrl("src") } }.takeUnless { it.isNullOrBlank() },
                author = row.selectFirst(".author, .novel-author")?.text()?.trim(),
                latestChapter = row.select("a[href*=chapter]").lastOrNull()?.text()?.trim()
            )
        }.distinctBy { it.url }
    }

    override suspend fun novel(url: String): NovelDetails {
        val html = http.get(url)
        val doc = parseHtml(html, url)
        val title = doc.firstText("h3.title", "h1.title", "h1") ?: "Untitled"
        val cover = doc.firstAttr("src", ".book img", ".info-holder img", ".m-book1 img")
            ?: doc.firstAttr("data-src", ".book img", ".info-holder img", ".m-book1 img")
        val author = doc.selectFirst("a[href*=/author/], a[href*=/authors/], .author a")?.text()?.trim()
        val genres = doc.select("a[href*=/genre/], a[href*=/genres/]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val status = doc.select("li, .item").firstOrNull { it.text().contains("Status", ignoreCase = true) }
            ?.text()?.substringAfter(":")?.trim()
        val description = doc.selectFirst(".desc-text, .description, .summary, .m-desc .inner")?.text()?.trim().orEmpty()
        return NovelDetails(id, title, url, cover, author, description, genres, status, chapters(url))
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val html = http.get(url)
        val doc = parseHtml(html, url)
        val seen = linkedMapOf<String, ChapterRef>()
        doc.select("#idData a[href], ul.list-chapter a[href], .chapter-list a[href], a[href*=chapter]").forEach { link ->
            val href = link.absUrl("href").ifBlank { absolute(url, link.attr("href")).orEmpty() }
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (href.isNotBlank() && title.isNotBlank() && !seen.containsKey(href)) {
                seen[href] = ChapterRef(id, url, title, href, seen.size)
            }
        }
        if (seen.isNotEmpty()) return seen.values.toList()

        // Some FWN layouts expose an id and serve the full TOC from this endpoint.
        val aid = Regex("(?:data-novel-id|data-articleid)=[\"']([^\"']+)").find(html)?.groupValues?.getOrNull(1)
            ?: Regex("sourceid=(\\d+)").find(html)?.groupValues?.getOrNull(1)
        if (!aid.isNullOrBlank()) {
            val ajax = runCatching { http.get("$baseUrl/api/chapterlist.php?aid=$aid", referer = url) }.getOrNull().orEmpty()
            val ajaxDoc = parseHtml(ajax, url)
            ajaxDoc.select("a[href], option[value]").forEach { node ->
                val raw = node.attr("href").ifBlank { node.attr("value") }
                val href = absolute(url, raw).orEmpty()
                val title = node.attr("title").ifBlank { node.text() }.trim()
                if (href.isNotBlank() && title.isNotBlank() && !seen.containsKey(href)) {
                    seen[href] = ChapterRef(id, url, title, href, seen.size)
                }
            }
        }
        return seen.values.toList()
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val html = http.get(ref.url, referer = ref.novelUrl)
        val doc = parseHtml(html, ref.url)
        val container = doc.selectFirst(".txt, #chr-content, #chapter-content, .chapter-content, article")
            ?: error("Chapter content not found")
        val title = doc.firstText("h2", ".chapter-title", "h1") ?: ref.title
        return ChapterContent(id, ref.novelUrl, ref.url, title, ref.index, cleanChapterHtml(container), cleanChapterText(container))
    }
}
