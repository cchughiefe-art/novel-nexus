package com.novelnexus.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.novelnexus.app.NovelNexusApp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class NovelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val sourceId = inputData.getString("sourceId") ?: return Result.failure()
        val novelUrl = inputData.getString("novelUrl") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Novel"
        val cover = inputData.getString("coverUrl")
        val author = inputData.getString("author")
        val from = inputData.getInt("from", 0)
        val to = inputData.getInt("to", Int.MAX_VALUE)

        val graph = (applicationContext as NovelNexusApp).graph
        val repo = graph.repository
        val chapters = runCatching { repo.chapters(sourceId, novelUrl) }.getOrElse { return Result.retry() }
        val selected = chapters.filter { it.index in from..to }
        repo.db().upsertNovel(sourceId, novelUrl, title, cover, author)

        val gate = Semaphore(4)
        val failures = AtomicInteger(0)
        coroutineScope {
            selected.map { ref ->
                async {
                    gate.withPermit {
                        if (!repo.db().isDownloaded(sourceId, ref.url)) {
                            runCatching { repo.downloadChapter(ref) }.onFailure { failures.incrementAndGet() }
                        }
                    }
                }
            }.awaitAll()
        }
        return if (failures.get() == 0) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}
