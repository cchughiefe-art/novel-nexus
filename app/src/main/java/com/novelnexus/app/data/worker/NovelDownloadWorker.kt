package com.novelnexus.app.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.novelnexus.app.NovelNexusApp

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

        // v0.5.0 anchored downloads.
        val startUrl = inputData.getString("startUrl")
        val startIndex = inputData.getInt("startIndex", Int.MIN_VALUE)
        val limit = inputData.getInt("limit", -1)

        // Backward compatibility with older queued jobs.
        val legacyFrom = inputData.getInt("from", Int.MIN_VALUE)
        val legacyTo = inputData.getInt("to", Int.MAX_VALUE)

        val graph = (applicationContext as NovelNexusApp).graph
        val repo = graph.repository

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

        val loaded = runCatching { repo.chapters(sourceId, novelUrl) }
            .getOrElse { error ->
                return if (runAttemptCount < 2) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf("error" to (error.message ?: "Could not load chapters"))
                    )
                }
            }

        val chapters = loaded
            .distinctBy { it.url }
            .sortedWith(compareBy({ it.index }, { it.url }))

        if (chapters.isEmpty()) {
            return Result.failure(workDataOf("error" to "This novel has no chapters"))
        }

        val selected = if (startUrl != null || startIndex != Int.MIN_VALUE) {
            val byUrl = if (!startUrl.isNullOrBlank()) {
                chapters.indexOfFirst { it.url == startUrl }
            } else {
                -1
            }

            val startPosition = if (byUrl >= 0) {
                byUrl
            } else {
                chapters.indexOfFirst { it.index >= startIndex }
            }

            if (startPosition < 0) {
                return Result.failure(
                    workDataOf("error" to "The selected starting chapter is no longer available")
                )
            }

            val remaining = chapters.drop(startPosition)
            if (limit > 0) remaining.take(limit) else remaining
        } else {
            chapters.filter { it.index in legacyFrom..legacyTo }
        }

        if (selected.isEmpty()) {
            return Result.failure(
                workDataOf("error" to "No chapters matched this download")
            )
        }

        val firstPosition = chapters.indexOfFirst { it.url == selected.first().url }
        val lastPosition = chapters.indexOfFirst { it.url == selected.last().url }
        val fromNumber = firstPosition + 1
        val toNumber = lastPosition + 1

        val existing = repo.db().downloadedUrls(sourceId, novelUrl)
        var done = selected.count { it.url in existing }
        var failed = 0
        var lastError: String? = null

        fun progress(
            stage: String,
            currentNumber: Int = fromNumber,
            currentTitle: String = ""
        ) = workDataOf(
            "title" to title,
            "stage" to stage,
            "done" to done,
            "total" to selected.size,
            "failed" to failed,
            "rangeFrom" to fromNumber,
            "rangeTo" to toNumber,
            "currentChapterNumber" to currentNumber,
            "currentChapterTitle" to currentTitle
        )

        setProgress(progress("downloading"))

        // Strictly sequential: N, N+1, N+2...
        // This prevents "Chapter 100 then Chapter 27" behavior.
        selected.forEachIndexed { offset, ref ->
            if (isStopped) {
                return Result.failure(workDataOf("error" to "Download cancelled"))
            }

            val currentNumber = fromNumber + offset

            if (repo.db().isDownloaded(sourceId, ref.url)) {
                setProgress(progress("downloading", currentNumber, ref.title))
                return@forEachIndexed
            }

            setProgress(progress("downloading", currentNumber, ref.title))

            runCatching { repo.downloadChapter(ref) }
                .onSuccess { done += 1 }
                .onFailure {
                    failed += 1
                    lastError = it.message
                }

            setProgress(progress("downloading", currentNumber, ref.title))
        }

        return when {
            failed == 0 -> Result.success(
                workDataOf(
                    "title" to title,
                    "done" to done,
                    "total" to selected.size,
                    "failed" to 0,
                    "rangeFrom" to fromNumber,
                    "rangeTo" to toNumber
                )
            )
            runAttemptCount < 2 -> Result.retry()
            else -> Result.failure(
                workDataOf(
                    "title" to title,
                    "error" to (
                        lastError?.let { "$failed chapter(s) failed: $it" }
                            ?: "$failed chapter(s) failed"
                    ),
                    "done" to done,
                    "total" to selected.size,
                    "failed" to failed,
                    "rangeFrom" to fromNumber,
                    "rangeTo" to toNumber
                )
            )
        }
    }
}
