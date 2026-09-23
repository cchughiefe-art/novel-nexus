package com.novelnexus.app.source.freewebnovel

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

class FreeWebNovelSource(private val http: HttpClient) : NovelSource {
    override val id = "freewebnovel"
    override val name = "FreeWebNovel"
    override val baseUrl = "https://freewebnovel.com"

    override suspend fun search(query: String, page: Int): List<NovelCard> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val candidates = listOf(
            "$baseUrl/search?keyword=$q&page=$page",
            "$baseUrl/search?q=$q&page=$page",
            "$baseUrl/search/$q"
        )

        var last: Throwable? = null
        for (url in candidates) {
            try {
                val cards = parseCards(http.get(url, referer = "$baseUrl/home"))
                if (cards.isNotEmpty()) return cards
            } catch (t: Throwable) {
                last = t
            }
        }
        if (last != null) throw last
        return emptyList()
    }

    override suspend fun latest(page: Int): List<NovelCard> {
        val url = if (page <= 1) {
            "$baseUrl/sort/latest-release"
        } else {
            "$baseUrl/sort/latest-release/$page"
        }
        return parseCards(http.get(url, referer = "$baseUrl/home"))
    }

    private fun isNovelUrl(href: String): Boolean {
        val path = href.substringBefore('?').substringBefore('#')
        val marker = "/novel/"
        val i = path.indexOf(marker)
        if (i < 0) return false
        val rest = path.substring(i + marker.length).trim('/')
        return rest.isNotBlank() && !rest.contains('/') &&
            !rest.startsWith("chapter-", ignoreCase = true)
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

        doc.select("a[href*=/novel/]").forEach { link ->
            val href = link.absUrl("href")
                .ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            if (!isNovelUrl(href)) return@forEach

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
                "a[href*=chapter-], .chapter a, .latest-chapter a"
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
        val html = http.get(url, referer = "$baseUrl/home")
        val doc = parseHtml(html, url)

        val title = doc.firstText("h3.title", "h1.title", "h1", "h3") ?: "Untitled"
        val cover = doc.firstAttr(
            "src",
            ".book img", ".info-holder img", ".m-book1 img", ".cover img"
        ) ?: doc.firstAttr(
            "data-src",
            ".book img", ".info-holder img", ".m-book1 img", ".cover img"
        )

        val author = doc.selectFirst(
            "a[href*=/author/], a[href*=/authors/], .author a"
        )?.text()?.trim()

        val genres = doc.select("a[href*=/genre/], a[href*=/genres/]")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val status = doc.select("li, .item, .info-meta").firstOrNull {
            it.text().contains("Status", ignoreCase = true)
        }?.text()?.substringAfter(":")?.trim()

        val description = doc.selectFirst(
            ".desc-text, .description, .summary, .m-desc .inner, [class*=summary]"
        )?.text()?.trim().orEmpty()

        return NovelDetails(
            id,
            title,
            url,
            cover,
            author,
            description,
            genres,
            status,
            chapters(url)
        )
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val html = http.get(url, referer = "$baseUrl/home")
        val doc = parseHtml(html, url)
        val seen = linkedMapOf<String, ChapterRef>()

        doc.select(
            "a[href*=/novel/][href*=chapter-], " +
                "#idData a[href*=chapter-], ul.list-chapter a[href*=chapter-], " +
                ".chapter-list a[href*=chapter-]"
        ).forEach { link ->
            val href = link.absUrl("href")
                .ifBlank { absolute(url, link.attr("href")).orEmpty() }
            val title = link.attr("title").ifBlank { link.text() }.trim()

            if (href.isNotBlank() &&
                "/chapter-" in href &&
                title.isNotBlank() &&
                !seen.containsKey(href)
            ) {
                seen[href] = ChapterRef(id, url, title, href, seen.size)
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
            "#chapter-content, .chapter-content, .chapter-body, .reading-content, " +
                ".read-content, .txt, #chr-content, article, main"
        ) ?: error("Chapter content not found")

        val title = doc.firstText(
            "h4", "h1", "h2", ".chapter-title"
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
