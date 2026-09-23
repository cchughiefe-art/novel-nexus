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
import org.jsoup.nodes.Element
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
        val candidates = listOf(
            "$baseUrl/search?keyword=$q&page=$page",
            "$baseUrl/search?q=$q&page=$page"
        )
        var last: Throwable? = null
        for (url in candidates) {
            try {
                val cards = parseCards(http.get(url, referer = baseUrl))
                if (cards.isNotEmpty()) return cards
            } catch (t: Throwable) {
                last = t
            }
        }
        if (last != null) throw last
        return emptyList()
    }

    override suspend fun latest(page: Int): List<NovelCard> {
        val url = "$baseUrl/latest-release-novel" + if (page > 1) "?page=$page" else ""
        return parseCards(http.get(url, referer = baseUrl))
    }

    private fun novelHref(href: String): Boolean {
        val path = href.substringBefore('#').substringBefore('?').lowercase()
        if (!path.endsWith(".html")) return false
        if ("/chapter-" in path || "/chapter_" in path) return false
        return listOf(
            "privacy", "terms", "contact", "sitemap", "login", "register"
        ).none { "/$it" in path }
    }

    private fun contextFor(link: Element): Element {
        var node: Element? = link
        repeat(5) {
            val current = node
            if (current != null && current.selectFirst("img") != null) return current
            node = current?.parent()
        }
        return link.parent() ?: link
    }

    private fun parseCards(html: String): List<NovelCard> {
        val doc = parseHtml(html, baseUrl)
        val seen = linkedMapOf<String, NovelCard>()

        val links = doc.select(
            "h3.truyen-title a[href], h3.novel-title a[href], " +
                ".truyen-title a[href], .novel-title a[href], " +
                "h3 a[href], a[href$=.html]"
        )

        links.forEach { link ->
            val href = link.absUrl("href")
                .ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            if (href.isBlank() || !novelHref(href)) return@forEach

            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (title.length < 2) return@forEach

            val box = contextFor(link)
            val img = box.selectFirst("img")
            val cover = img?.absUrl("data-src")
                ?.ifBlank { img.absUrl("src") }
                ?.takeIf { it.isNotBlank() }

            val author = box.selectFirst(".author, .novel-author, a[href*=/author/]")
                ?.text()?.trim()?.takeIf { it.isNotBlank() }

            val latest = box.select(
                "a[href*=chapter-], .text-info a, .chapter a, .latest-chapter a"
            ).lastOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }

            seen.putIfAbsent(
                href,
                NovelCard(
                    sourceId = id,
                    title = title,
                    url = href,
                    coverUrl = cover,
                    author = author,
                    latestChapter = latest
                )
            )
        }
        return seen.values.toList()
    }

    override suspend fun novel(url: String): NovelDetails {
        val html = http.get(url, referer = baseUrl)
        val doc = parseHtml(html, url)

        val title = doc.firstText("h3.title", "h1.title", "h1", "h3") ?: "Untitled"
        val cover = doc.firstAttr(
            "src",
            ".book img", ".info-holder img", ".book img[data-src]", ".cover img"
        ) ?: doc.firstAttr(
            "data-src",
            ".book img", ".info-holder img", ".cover img"
        )

        val author = doc.select("ul.info-meta li, .info li").firstOrNull {
            it.text().contains("Author", ignoreCase = true)
        }?.selectFirst("a")?.text()?.trim()
            ?: doc.selectFirst("a[href*=/author/]")?.text()?.trim()

        val genres = doc.select("a[href*=/genre/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val status = doc.select("ul.info-meta li, .info li, .info-meta li").firstOrNull {
            it.text().contains("Status", ignoreCase = true)
        }?.text()?.substringAfter(":")?.trim()

        val description = doc.selectFirst(
            ".desc-text, .description, .desc, .summary, [class*=summary]"
        )?.text()?.trim().orEmpty()

        return NovelDetails(
            sourceId = id,
            title = title,
            url = url,
            coverUrl = cover,
            author = author,
            description = description,
            genres = genres,
            status = status,
            chapters = chapters(url)
        )
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val firstHtml = http.get(url, referer = baseUrl)
        val firstDoc = parseHtml(firstHtml, url)
        val seen = linkedMapOf<String, ChapterRef>()

        fun add(docHtml: String, pageUrl: String) {
            val doc = parseHtml(docHtml, pageUrl)
            doc.select(
                "ul.list-chapter a[href], #list-chapter a[href], " +
                    ".chapter-list a[href], a[href*=chapter-]"
            ).forEach { link ->
                val href = link.absUrl("href")
                    .ifBlank { absolute(pageUrl, link.attr("href")).orEmpty() }
                if (href.isBlank() || seen.containsKey(href)) return@forEach

                val title = link.attr("title").ifBlank { link.text() }.trim()
                if (title.isBlank()) return@forEach

                seen[href] = ChapterRef(id, url, title, href, seen.size)
            }
        }

        add(firstHtml, url)

        val lastHref = firstDoc.selectFirst(
            "li.last a[href], .pagination a:last-child[href], a[rel=last]"
        )?.absUrl("href").orEmpty()

        val totalPages = sequenceOf(
            firstDoc.selectFirst("li.last a")?.attr("data-page")
                ?.toIntOrNull()?.plus(1),
            Regex("[?&](?:page|page_num)=(\\d+)")
                .find(lastHref)?.groupValues?.getOrNull(1)?.toIntOrNull(),
            Regex("/(\\d+)$")
                .find(lastHref.substringBefore("?"))?.groupValues?.getOrNull(1)?.toIntOrNull(),
            firstDoc.selectFirst("#total-page")?.attr("value")?.toIntOrNull()
        ).filterNotNull().firstOrNull() ?: 1

        if (totalPages > 1) {
            for (p in 2..minOf(totalPages, 160)) {
                val sep = if (url.contains("?")) "&" else "?"
                val pageUrl = "$url${sep}page=$p&per-page=50"
                runCatching { http.get(pageUrl, referer = url) }
                    .getOrNull()?.let { add(it, pageUrl) }
            }
        }

        return seen.values.toList().mapIndexed { index, ref ->
            ref.copy(index = index)
        }
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val html = http.get(ref.url, referer = ref.novelUrl)
        val doc = parseHtml(html, ref.url)
        val container = doc.selectFirst(
            "#chr-content, #chapter-content, .chapter-c, .chapter-content, " +
                ".chapter-body, .reading-content, article"
        ) ?: error("Chapter content not found")

        val title = doc.firstText(
            "h1", "h2", ".chapter-title", ".chr-title"
        ) ?: ref.title

        val text = cleanChapterText(container)
        if (text.length < 40) error("Chapter content was empty")

        return ChapterContent(
            id,
            ref.novelUrl,
            ref.url,
            title,
            ref.index,
            cleanChapterHtml(container),
            text
        )
    }
}
