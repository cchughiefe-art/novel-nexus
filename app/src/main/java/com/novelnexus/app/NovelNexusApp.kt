package com.novelnexus.app

import android.app.Application
import com.novelnexus.app.core.network.HttpClient
import com.novelnexus.app.core.source.SourceRegistry
import com.novelnexus.app.data.db.NovelDatabase
import com.novelnexus.app.data.repo.NovelRepository
import com.novelnexus.app.source.freewebnovel.FreeWebNovelSource
import com.novelnexus.app.source.lightnovelpub.LightNovelPubSource
import com.novelnexus.app.source.novelfull.NovelFullSource

class NovelNexusApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val http = HttpClient()
        val db = NovelDatabase(this)
        val registry = SourceRegistry(
            listOf(
                NovelFullSource("novelfull-com", "NovelFull.com", "https://novelfull.com", http),
                NovelFullSource("novelfull-net", "NovelFull.net", "https://novelfull.net", http),
                FreeWebNovelSource(http),
                LightNovelPubSource(http)
            )
        )
        graph = AppGraph(registry, NovelRepository(registry, db))
    }
}

data class AppGraph(
    val sources: SourceRegistry,
    val repository: NovelRepository
)
