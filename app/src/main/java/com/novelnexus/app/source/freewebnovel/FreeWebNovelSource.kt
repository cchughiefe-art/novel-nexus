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
        for (candidate in candidates) {
            try {
                val cards = parseCards(http.get(candidate, referer = "$baseUrl/home"))
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
        return rest.isNotBlank() &&
            !rest.contains('/') &&
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

    private data class Candidate(
        val number: Int,
        val title: String,
        val url: String,
        val quality: Int
    )

    private fun chapterNumber(href: String, title: String): Int? =
        Regex("(?i)/chapter-(\\d+)(?:[/?#]|$)")
            .find(href)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: Regex("(?i)\\bchapter\\s*[:#-]?\\s*(\\d+)\\b")
                .find(title)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

    private fun titleQuality(title: String, number: Int): Int = when {
        Regex("(?i)^chapter\\s*[:#-]?\\s*$number\\b")
            .containsMatchIn(title.trim()) -> 3
        title.contains("chapter", ignoreCase = true) -> 2
        title.startsWith("read ", ignoreCase = true) -> 0
        else -> 1
    }

    private fun isCataloguePage(candidate: String, novelUrl: String): Boolean {
        return runCatching {
            val base = java.net.URI(novelUrl)
            val other = java.net.URI(candidate)
            if (!other.host.equals(base.host, ignoreCase = true)) {
                return@runCatching false
            }

            val novelPath = base.path.trimEnd('/')
            val otherPath = other.path.trimEnd('/')

            !otherPath.contains("/chapter-", ignoreCase = true) &&
                (
                    otherPath == novelPath ||
                        Regex("^${Regex.escape(novelPath)}/\\d+$")
                            .matches(otherPath)
                    )
        }.getOrDefault(false)
    }

    override suspend fun chapters(url: String): List<ChapterRef> {
        val found = linkedMapOf<Int, Candidate>()
        val pending = java.util.ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val signatures = mutableSetOf<String>()

        pending.add(url)

        fun accept(href: String, rawTitle: String) {
            val number = chapterNumber(href, rawTitle) ?: return
            if (number <= 0) return

            val title = rawTitle
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "Chapter $number" }

            val candidate = Candidate(
                number = number,
                title = title,
                url = href,
                quality = titleQuality(title, number)
            )

            val old = found[number]
            if (old == null || candidate.quality > old.quality) {
                found[number] = candidate
            }
        }

        var pages = 0

        while (pending.isNotEmpty() && pages < 60) {
            val pageUrl = pending.removeFirst()
            if (!visited.add(pageUrl)) continue

            val html = runCatching {
                http.get(
                    pageUrl,
                    referer = if (pageUrl == url) "$baseUrl/home" else url
                )
            }.getOrNull() ?: continue

            val doc = parseHtml(html, pageUrl)
            val pageNumbers = mutableListOf<Int>()

            doc.select(
                "a[href*=/novel/][href*=chapter-], " +
                    "#idData a[href*=chapter-], " +
                    "ul.list-chapter a[href*=chapter-], " +
                    ".chapter-list a[href*=chapter-]"
            ).forEach { link ->
                val href = link.absUrl("href")
                    .ifBlank { absolute(pageUrl, link.attr("href")).orEmpty() }
                val title = link.attr("title").ifBlank { link.text() }.trim()

                if (!href.contains("/chapter-", ignoreCase = true)) return@forEach

                chapterNumber(href, title)?.let(pageNumbers::add)
                accept(href, title)
            }

            val signature = pageNumbers.distinct().sorted().joinToString(",")
            val isNewPage = signature.isNotBlank() && signatures.add(signature)

            if (pageUrl == url || isNewPage) {
                val nav = linkedSetOf<String>()

                doc.select(
                    ".pagination a[href], .paging a[href], .page-nav a[href], " +
                        "a[rel=next][href], a[rel=last][href]"
                ).forEach { link ->
                    val candidate = link.absUrl("href")
                        .ifBlank { absolute(pageUrl, link.attr("href")).orEmpty() }

                    if (candidate.isNotBlank() &&
                        candidate != pageUrl &&
                        isCataloguePage(candidate, url)
                    ) {
                        nav += candidate
                    }
                }

                doc.select("a[href]").filter {
                    val text = it.text().trim()
                    text.equals("next", true) ||
                        text.equals("last", true)
                }.forEach { link ->
                    val candidate = link.absUrl("href")
                        .ifBlank { absolute(pageUrl, link.attr("href")).orEmpty() }

                    if (candidate.isNotBlank() &&
                        candidate != pageUrl &&
                        isCataloguePage(candidate, url)
                    ) {
                        nav += candidate
                    }
                }

                doc.select("select option[value]").forEach { option ->
                    val raw = option.attr("value").trim()
                    val page = raw.toIntOrNull()

                    if (page != null && page > 1) {
                        nav += "$url?page=$page"
                        nav += "$url/$page"
                    } else if (raw.isNotBlank()) {
                        val candidate = absolute(pageUrl, raw).orEmpty()
                        if (candidate.isNotBlank() &&
                            isCataloguePage(candidate, url)
                        ) {
                            nav += candidate
                        }
                    }
                }

                nav.filterNot { it in visited }.forEach(pending::addLast)

                if (pageUrl == url &&
                    pageNumbers.distinct().size in 35..50 &&
                    nav.isEmpty()
                ) {
                    pending.addLast("$url?page=2")
                    pending.addLast("$url/2")
                }

                if (pageUrl != url && isNewPage && nav.isEmpty()) {
                    Regex("[?&]page=(\\d+)")
                        .find(pageUrl)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?.let { pending.addLast("$url?page=${it + 1}") }

                    Regex("/(\\d+)$")
                        .find(pageUrl.substringBefore("?"))
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                        ?.let { pending.addLast("$url/${it + 1}") }
                }
            }

            pages++
        }

        // The latest-chapter block gives us the true current max chapter.
        // If the site's paginator hides some catalogue pages behind JS,
        // sequential FreeWebNovel chapter URLs let us keep a complete list.
        val maxChapter = found.keys.maxOrNull() ?: 0
        if (maxChapter > 0) {
            val novelBase = url.trimEnd('/')
            for (number in 1..maxChapter) {
                if (number !in found) {
                    found[number] = Candidate(
                        number = number,
                        title = "Chapter $number",
                        url = "$novelBase/chapter-$number",
                        quality = 0
                    )
                }
            }
        }

        return found.values
            .sortedBy { it.number }
            .map { candidate ->
                ChapterRef(
                    sourceId = id,
                    novelUrl = url,
                    title = candidate.title,
                    url = candidate.url,
                    index = candidate.number - 1
                )
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
