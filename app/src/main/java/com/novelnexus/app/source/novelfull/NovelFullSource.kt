package com.novelnexus.app.source.novelfull

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

class NovelFullSource(
    override val id: String,
    override val name: String,
    override val baseUrl: String,
    private val http: HttpClient
) : NovelSource {

    override suspend fun search(query: String, page: Int): List<NovelCard> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val html = http.get("$baseUrl/search?keyword=$q&page=$page")
        return parseCards(html)
    }

    override suspend fun latest(page: Int): List<NovelCard> {
        val html = http.get("$baseUrl/latest-release-novel?page=$page")
        return parseCards(html)
    }

    private fun parseCards(html: String): List<NovelCard> {
        val doc = parseHtml(html, baseUrl)
        val rows = doc.select(".list-truyen .row, .list-novel .row, .archive .row, .col-truyen-main .row")
        return rows.mapNotNull { row ->
            val link = row.selectFirst("h3.truyen-title a, h3.novel-title a, .truyen-title a, .novel-title a")
                ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (href.isBlank() || title.isBlank()) return@mapNotNull null
            NovelCard(
                sourceId = id,
                title = title,
                url = href,
                coverUrl = row.selectFirst("img")?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }.takeUnless { it.isNullOrBlank() },
                author = row.selectFirst(".author")?.text()?.trim(),
                latestChapter = row.select(".text-info a, .chapter a, .latest-chapter a").lastOrNull()?.text()?.trim()
            )
        }.distinctBy { it.url }
    }

    override suspend fun novel(url: String): NovelDetails {
        val html = http.get(url)
        val doc = parseHtml(html, url)
        val title = doc.firstText("h3.title", "h1.title", "h1") ?: "Untitled"
        val cover = doc.firstAttr("src", ".book img", ".info-holder img", ".book img[data-src]")
            ?: doc.firstAttr("data-src", ".book img", ".info-holder img")
        val author = doc.select("ul.info-meta li, .info li").firstOrNull {
            it.text().contains("Author", ignoreCase = true)
        }?.selectFirst("a")?.text()?.trim()
            ?: doc.selectFirst("a[href*=/author/]")?.text()?.trim()
        val genres = doc.select("a[href*=/genre/]").map { it.text().trim() }.filter { it.isNotBlank() }.distinct()
        val status = doc.select("ul.info-meta li, .info li").firstOrNull {
            it.text().contains("Status", ignoreCase = true)
        }?.text()?.substringAfter(":")?.trim()
        val description = doc.selectFirst(".desc-text, .description, .desc")?.text()?.trim().orEmpty()
        val chapterList = chapters(url)
        return NovelDetails(id, title, url, cover, author, description, genres, status, chapterList)
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val firstHtml = http.get(url)
        val firstDoc = parseHtml(firstHtml, url)
        val totalPages = sequenceOf(
            firstDoc.selectFirst("li.last a")?.attr("data-page")?.toIntOrNull()?.plus(1),
            firstDoc.selectFirst("li.last a")?.absUrl("href")?.let { href ->
                Regex("[?&](?:page|page_num)=(\\d+)").find(href)?.groupValues?.getOrNull(1)?.toIntOrNull()
            },
            firstDoc.selectFirst("#total-page")?.attr("value")?.toIntOrNull()
        ).filterNotNull().firstOrNull() ?: 1

        val seen = linkedMapOf<String, ChapterRef>()
        fun addFromDoc(docHtml: String, pageUrl: String) {
            val doc = parseHtml(docHtml, pageUrl)
            val links = doc.select("ul.list-chapter a, #list-chapter a, .chapter-list a, a[href*=chapter-]")
            links.forEach { link ->
                val href = link.absUrl("href").ifBlank { absolute(pageUrl, link.attr("href")).orEmpty() }
                if (href.isBlank() || seen.containsKey(href)) return@forEach
                val title = link.attr("title").ifBlank { link.text() }.trim()
                if (title.isBlank()) return@forEach
                seen[href] = ChapterRef(id, url, title, href, seen.size)
            }
        }
        addFromDoc(firstHtml, url)
        if (totalPages > 1) {
            for (p in 2..minOf(totalPages, 120)) {
                val sep = if (url.contains("?")) "&" else "?"
                val pageUrl = "$url${sep}page=$p&per-page=50"
                runCatching { http.get(pageUrl, referer = url) }.getOrNull()?.let { addFromDoc(it, pageUrl) }
            }
        }
        return seen.values.toList().mapIndexed { index, ref -> ref.copy(index = index) }
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val html = http.get(ref.url, referer = ref.novelUrl)
        val doc = parseHtml(html, ref.url)
        val container = doc.selectFirst("#chr-content, #chapter-content, .chapter-c, .chapter-content, article")
            ?: error("Chapter content not found")
        val title = doc.firstText("h2", ".chapter-title", "h1") ?: ref.title
        return ChapterContent(id, ref.novelUrl, ref.url, title, ref.index, cleanChapterHtml(container), cleanChapterText(container))
    }
}
