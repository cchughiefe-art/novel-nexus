package com.novelatlas.app.core.source

import java.util.concurrent.ConcurrentHashMap

class SourceRegistry(sources: List<NovelSource>) {
    private val byId = sources.associateBy { it.id }
    private val enabled = ConcurrentHashMap<String, Boolean>().apply {
        byId.keys.forEach { put(it, true) }
    }

    fun all(): List<NovelSource> = byId.values.sortedBy { it.name }
    fun enabled(): List<NovelSource> = all().filter { isEnabled(it.id) }
    fun get(id: String): NovelSource? = byId[id]
    fun isEnabled(id: String): Boolean = enabled[id] ?: false
    fun setEnabled(id: String, value: Boolean) {
        if (id in byId) enabled[id] = value
    }

    fun require(id: String): NovelSource =
        byId[id] ?: error("Unknown source: $id")
}
