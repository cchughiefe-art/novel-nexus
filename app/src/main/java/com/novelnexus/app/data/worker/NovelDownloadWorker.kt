package com.novelnexus.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novelnexus.app.NovelNexusApp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

        // Make the queued novel visible immediately.
        repo.db().upsertNovel(sourceId, novelUrl, title, cover, author)

        setProgress(
            workDataOf(
                "title" to title,
                "stage" to "loading_chapters",
                "done" to 0,
                "total" to 0,
                "failed" to 0
            )
        )

        val chapters = runCatching {
            repo.chapters(sourceId, novelUrl)
        }.getOrElse { error ->
            return if (runAttemptCount < 2) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf("error" to (error.message ?: "Could not load chapters"))
                )
            }
        }

        val selected = chapters.filter { it.index in from..to }
        if (selected.isEmpty()) {
            return Result.failure(
                workDataOf("error" to "No chapters were found for this novel")
            )
        }

        val total = selected.size
        val done = AtomicInteger(
            selected.count { repo.db().isDownloaded(sourceId, it.url) }
        )
        val failures = AtomicInteger(0)

        setProgress(
            workDataOf(
                "title" to title,
                "stage" to "downloading",
                "done" to done.get(),
                "total" to total,
                "failed" to 0
            )
        )

        // Important: do not launch thousands of coroutines at once.
        // Process in bounded groups of four.
        for (batch in selected.chunked(4)) {
            coroutineScope {
                batch.map { ref ->
                    async {
                        if (repo.db().isDownloaded(sourceId, ref.url)) return@async

                        runCatching {
                            repo.downloadChapter(ref)
                        }.onSuccess {
                            done.incrementAndGet()
                        }.onFailure {
                            failures.incrementAndGet()
                        }
                    }
                }.awaitAll()
            }

            setProgress(
                workDataOf(
                    "title" to title,
                    "stage" to "downloading",
                    "done" to done.get(),
                    "total" to total,
                    "failed" to failures.get()
                )
            )
        }

        val failed = failures.get()
        return if (failed == 0) {
            Result.success(
                workDataOf(
                    "done" to done.get(),
                    "total" to total,
                    "failed" to 0
                )
            )
        } else if (runAttemptCount < 2) {
            Result.retry()
        } else {
            Result.failure(
                workDataOf(
                    "error" to "$failed chapters failed",
                    "done" to done.get(),
                    "total" to total,
                    "failed" to failed
                )
            )
        }
    }
}
