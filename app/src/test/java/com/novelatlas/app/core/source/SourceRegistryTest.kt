package com.novelatlas.app.core.source

import com.novelatlas.app.core.model.ChapterContent
import com.novelatlas.app.core.model.ChapterRef
import com.novelatlas.app.core.model.NovelCard
import com.novelatlas.app.core.model.NovelDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SourceRegistryTest {
    private class FakeSource(override val id: String, override val name: String) : NovelSource {
        override val baseUrl = "https://example.invalid"
        override suspend fun search(query: String, page: Int) = emptyList<NovelCard>()
        override suspend fun novel(url: String) = NovelDetails(id, "x", url)
        override suspend fun chapters(url: String) = emptyList<ChapterRef>()
        override suspend fun chapter(ref: ChapterRef) = ChapterContent(id, ref.novelUrl, ref.url, ref.title, ref.index, "", "")
    }

    @Test
    fun registryResolvesPluginsById() {
        val registry = SourceRegistry(listOf(FakeSource("a", "A"), FakeSource("b", "B")))
        assertEquals(2, registry.all().size)
        assertNotNull(registry.get("a"))
        assertEquals("B", registry.require("b").name)
    }
}
