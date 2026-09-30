package com.novelnexus.app

import android.app.Application
import com.novelnexus.app.core.network.HttpClient
import com.novelnexus.app.core.source.SourceRegistry
import com.novelnexus.app.data.cache.CacheSettings
import com.novelnexus.app.data.db.NovelDatabase
import com.novelnexus.app.data.reader.ReaderPreferences
import com.novelnexus.app.data.repo.NovelRepository
import com.novelnexus.app.source.freewebnovel.FreeWebNovelSource
import com.novelnexus.app.source.lightnovelpub.LightNovelPubSource
import com.novelnexus.app.source.novelfull.NovelFullSource
import com.novelnexus.app.source.novelfullparse.NovelNexusProvider

class NovelNexusApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val cacheSettings = CacheSettings(this)
        val http = HttpClient(this, cacheSettings.limitMb())
        val db = NovelDatabase(this)
        val registry = SourceRegistry(
            listOf(
                NovelFullSource("novelfull-com", "NovelFull.com", "https://novelfull.com", http),
                NovelFullSource("novelfull-net", "NovelFull.net", "https://novelfull.net", http),
                NovelNexusProvider(this),
                FreeWebNovelSource(http),
                LightNovelPubSource(http)
            )
        )
        graph = AppGraph(
            sources = registry,
            repository = NovelRepository(registry, db),
            readerPreferences = ReaderPreferences(this),
            http = http,
            cacheSettings = cacheSettings
        )
    }
}

data class AppGraph(
    val sources: SourceRegistry,
    val repository: NovelRepository,
    val readerPreferences: ReaderPreferences,
    val http: HttpClient,
    val cacheSettings: CacheSettings
)
