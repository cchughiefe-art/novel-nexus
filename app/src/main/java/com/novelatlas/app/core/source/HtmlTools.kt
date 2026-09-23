package com.novelatlas.app.core.source

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

fun parseHtml(html: String, baseUrl: String): Document = Jsoup.parse(html, baseUrl)

fun Element.firstText(vararg selectors: String): String? = selectors.asSequence()
    .mapNotNull { selectFirst(it)?.text()?.trim() }
    .firstOrNull { it.isNotBlank() }

fun Element.firstAttr(attr: String, vararg selectors: String): String? = selectors.asSequence()
    .mapNotNull { selectFirst(it)?.attr(attr)?.trim() }
    .firstOrNull { it.isNotBlank() }

fun cleanChapterHtml(container: Element): String {
    val copy = container.clone()
    copy.select("script,style,iframe,ins,.ads,[class*=ads],[id*=ads],.unlock-buttons,.social,.share").remove()
    copy.select("a").forEach { a ->
        if (a.text().contains("read", ignoreCase = true) && a.text().contains("site", ignoreCase = true)) a.remove()
    }
    return copy.html().trim()
}

fun cleanChapterText(container: Element): String {
    val copy = container.clone()
    copy.select("script,style,iframe,ins,.ads,[class*=ads],[id*=ads],.unlock-buttons,.social,.share").remove()
    val paragraphs = copy.select("p").map { it.text().trim() }.filter { it.isNotBlank() }
    return if (paragraphs.isNotEmpty()) paragraphs.joinToString("\n\n") else copy.text().trim()
}

fun absolute(base: String, href: String?): String? {
    if (href.isNullOrBlank()) return null
    return try {
        java.net.URI(base).resolve(href).toString()
    } catch (_: Throwable) {
        href
    }
}
