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
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class LightNovelPubSource(private val http: HttpClient) : NovelSource {
    override val id = "lightnovelpub"
    override val name = "Light Novel Pub"
    override val baseUrl = "https://lightnovelpub.me"
    override val supportsLatest = true

    override suspend fun latest(page: Int): List<NovelCard> {
        val url = if (page <= 1) {
            "$baseUrl/list/latest-release-novels/"
        } else {
            "$baseUrl/list/latest-release-novels/$page"
        }
        return parseCards(http.get(url, referer = baseUrl))
    }

    override suspend fun search(query: String, page: Int): List<NovelCard> {
        val q = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val candidates = listOf(
            "$baseUrl/search?keyword=$q",
            "$baseUrl/search?inputContent=$q",
            "$baseUrl/search?q=$q"
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

    private fun isBookUrl(href: String): Boolean {
        val path = href.substringBefore('?').substringBefore('#')
        val marker = "/book/"
        val i = path.indexOf(marker)
        if (i < 0) return false
        val rest = path.substring(i + marker.length).trim('/')
        return rest.isNotBlank() && !rest.contains('/')
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

        doc.select("a[href*=/book/]").forEach { link ->
            val href = link.absUrl("href")
                .ifBlank { absolute(baseUrl, link.attr("href")).orEmpty() }
            if (!isBookUrl(href)) return@forEach

            val title = link.attr("title").ifBlank { link.text() }.trim()
            if (title.length < 2) return@forEach

            val box = contextFor(link)
            val img = box.selectFirst("img")
            val cover = img?.absUrl("data-src")
                ?.ifBlank { img.absUrl("src") }
                ?.takeIf { it.isNotBlank() }

            val author = box.selectFirst(".author, .novel-author, a[href*=/author/]")
                ?.text()?.trim()?.takeIf { it.isNotBlank() }

            seen.putIfAbsent(
                href,
                NovelCard(
                    sourceId = id,
                    title = title,
                    url = href,
                    coverUrl = cover,
                    author = author
                )
            )
        }

        return seen.values.toList()
    }

    override suspend fun novel(url: String): NovelDetails {
        val html = http.get(url, referer = baseUrl)
        val doc = parseHtml(html, url)

        val title = doc.firstText(
            ".m-desc .tit", "h1.novel-title", "h1", "h3"
        ) ?: "Untitled"

        val cover = doc.firstAttr(
            "src",
            ".m-book1 .pic img", ".m-book1 img", "figure.cover img", ".cover img"
        ) ?: doc.firstAttr(
            "data-src",
            ".m-book1 .pic img", ".m-book1 img", "figure.cover img", ".cover img"
        )

        val author = doc.selectFirst(
            ".m-book1 .item [title=Author] + .right a, " +
                ".m-book1 .item .right a, .author a, a[href*=/author/]"
        )?.text()?.trim()

        val description = doc.selectFirst(
            ".m-desc .inner, .abstract + .txt, .summary .content, " +
                ".description, [class*=summary]"
        )?.text()?.trim().orEmpty()

        val genres = doc.select(
            ".categories a, a[href*=genre], a[href*=category]"
        ).map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val status = doc.select(
            ".m-book1 .item, .header-stats span, .info-meta"
        ).firstOrNull {
            it.text().contains("Status", true) ||
                it.text().contains("OnGoing", true) ||
                it.text().contains("Completed", true)
        }?.text()?.trim()

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
        val seen = linkedMapOf<String, ChapterRef>()
        var current: String? = url
        var page = 1
        val visited = mutableSetOf<String>()

        while (!current.isNullOrBlank() && page <= 200 && current !in visited) {
            visited += current
            val html = http.get(current, referer = if (page == 1) baseUrl else url)
            val doc = parseHtml(html, current)

            doc.select(
                "a[href*=/book/][href*=chapter-], " +
                    ".chapter-list a[href], .m-newest2 .ul-list5 a.con, " +
                    "a[href*=chapter-]"
            ).forEach { link ->
                val href = link.absUrl("href")
                    .ifBlank { absolute(current, link.attr("href")).orEmpty() }
                val title = link.attr("title").ifBlank { link.text() }.trim()

                if (href.isNotBlank() &&
                    "/chapter-" in href &&
                    title.isNotBlank() &&
                    !seen.containsKey(href)
                ) {
                    seen[href] = ChapterRef(id, url, title, href, seen.size)
                }
            }

            val next = doc.select(
                "a[rel=next], .pagination a, a:matchesOwn((?i)^next$)"
            ).firstOrNull {
                it.text().trim().equals("next", ignoreCase = true) ||
                    it.attr("rel").equals("next", ignoreCase = true)
            }?.let { link ->
                link.absUrl("href")
                    .ifBlank { absolute(current, link.attr("href")).orEmpty() }
            }

            if (next.isNullOrBlank()) break
            current = next
            page++
        }

        return seen.values.toList().mapIndexed { index, ref ->
            ref.copy(index = index)
        }
    }

    override suspend fun chapter(ref: ChapterRef): ChapterContent {
        val html = http.get(ref.url, referer = ref.novelUrl)
        val doc = parseHtml(html, ref.url)

        val container = doc.selectFirst(
            ".read-content, .chapter-content, #chapter-container, " +
                ".chapter-body, .reading-content, .txt, article"
        ) ?: error(
            "Light Novel Pub blocked or changed direct chapter access for this title"
        )

        val title = doc.firstText(
            "h1", "h2", ".chapter-title"
        ) ?: ref.title

        val text = cleanChapterText(container)
        if (text.length < 40) error(
            "Light Novel Pub returned an empty or blocked chapter"
        )

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
