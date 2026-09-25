package com.novelnexus.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil.compose.AsyncImage
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelDetails
import com.novelnexus.app.data.db.NovelDatabase
import com.novelnexus.app.data.worker.NovelDownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class DownloadAmount(val label: String, val count: Int?)

private val downloadAmounts = listOf(
    DownloadAmount("Next 10", 10),
    DownloadAmount("Next 25", 25),
    DownloadAmount("Next 50", 50),
    DownloadAmount("Next 100", 100),
    DownloadAmount("All remaining", null)
)

private fun canonicalChapters(novel: NovelDetails): List<ChapterRef> =
    novel.chapters
        .distinctBy { it.url }
        .sortedWith(compareBy({ it.index }, { it.url }))

private fun cleanedChapterTitle(title: String): String {
    var value = title.trim()
    value = value.replace(
        Regex("""^chapter\s+\d+\s*[:\-–—]?\s*""", RegexOption.IGNORE_CASE),
        ""
    )
    value = value.replace(Regex("""^\d+\s*[:\-–—]\s*"""), "")
    return value.ifBlank { "Untitled chapter" }
}

private fun enqueueFrom(
    context: Context,
    novel: NovelDetails,
    start: ChapterRef,
    limit: Int?
) {
    val input = Data.Builder()
        .putString("sourceId", novel.sourceId)
        .putString("novelUrl", novel.url)
        .putString("title", novel.title)
        .putString("coverUrl", novel.coverUrl)
        .putString("author", novel.author)
        .putString("startUrl", start.url)
        .putInt("startIndex", start.index)
        .putInt("limit", limit ?: -1)
        .build()

    val request = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setInputData(input)
        .addTag("novel-download")
        .addTag("novel:${novel.sourceId}:${novel.url.hashCode()}")
        .addTag("start:${start.url.hashCode()}")
        .build()

    WorkManager.getInstance(context).enqueue(request)
}

@Composable
private fun DownloadFromDialog(
    novel: NovelDetails,
    start: ChapterRef,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    val canonical = remember(novel.chapters) { canonicalChapters(novel) }
    val startPosition = canonical.indexOfFirst { it.url == start.url }.coerceAtLeast(0)
    val startNumber = startPosition + 1
    val remaining = (canonical.size - startPosition).coerceAtLeast(1)
    var selected by remember(start.url) {
        mutableStateOf(downloadAmounts.first())
    }

    val count = selected.count?.coerceAtMost(remaining) ?: remaining
    val endNumber = startNumber + count - 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download from Chapter $startNumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Downloads move forward only: Chapter $startNumber, " +
                        "${startNumber + 1}, ${startNumber + 2}…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    cleanedChapterTitle(start.title),
                    fontWeight = FontWeight.SemiBold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    downloadAmounts.forEach { amount ->
                        FilterChip(
                            selected = selected == amount,
                            onClick = { selected = amount },
                            label = {
                                val shown = amount.count?.coerceAtMost(remaining)
                                Text(
                                    if (shown == null) {
                                        "${amount.label} ($remaining)"
                                    } else {
                                        "${amount.label} ($shown)"
                                    }
                                )
                            }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            "Chapter $startNumber → Chapter $endNumber",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "$count chapter${if (count == 1) "" else "s"} in exact reading order",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selected.count) }) {
                Icon(Icons.Rounded.CloudDownload, null)
                Text(" Start download", modifier = Modifier.padding(start = 6.dp))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StartChapterPicker(
    novel: NovelDetails,
    onDismiss: () -> Unit,
    onPick: (ChapterRef) -> Unit
) {
    val canonical = remember(novel.chapters) { canonicalChapters(novel) }
    var query by remember { mutableStateOf("") }

    val shown = remember(canonical, query) {
        if (query.isBlank()) canonical
        else canonical.filterIndexed { index, chapter ->
            val number = index + 1
            number.toString() == query.trim() ||
                chapter.title.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose starting chapter") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Chapter number or title") },
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.height(380.dp).padding(top = 8.dp)
                ) {
                    itemsIndexed(shown, key = { _, it -> it.url }) { _, chapter ->
                        val actualNumber =
                            canonical.indexOfFirst { it.url == chapter.url } + 1

                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(chapter) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                actualNumber.toString(),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(52.dp)
                            )
                            Text(
                                cleanedChapterTitle(chapter.title),
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun NovelDetailScreen(
    graph: AppGraph,
    sourceId: String,
    url: String,
    onBack: () -> Unit,
    onRead: (ChapterRef) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var novel by remember { mutableStateOf<NovelDetails?>(null) }
    var progress by remember {
        mutableStateOf<NovelDatabase.ReadingProgress?>(null)
    }
    var downloaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chapterFilter by remember { mutableStateOf("") }
    var newChapterCount by remember { mutableStateOf(0) }

    var pickStart by remember { mutableStateOf(false) }
    var downloadStart by remember { mutableStateOf<ChapterRef?>(null) }

    suspend fun loadDownloaded() {
        downloaded = withContext(Dispatchers.IO) {
            graph.repository.db().downloadedUrls(sourceId, url)
        }
    }

    suspend fun refresh() {
        refreshing = true
        runCatching { graph.repository.refreshNovel(sourceId, url) }
            .onSuccess {
                novel = it.details
                newChapterCount = it.newChapterCount
                error = null
            }
            .onFailure {
                error = it.message ?: "Could not refresh this novel."
                if (novel == null) {
                    novel = runCatching {
                        graph.repository.novel(sourceId, url)
                    }.getOrNull()
                }
            }
        loadDownloaded()
        refreshing = false
    }

    LaunchedEffect(sourceId, url) {
        loading = true
        novel = runCatching {
            graph.repository.novel(sourceId, url)
        }.getOrNull()

        progress = withContext(Dispatchers.IO) {
            graph.repository.db().getReadingProgress(sourceId, url)
        }

        loadDownloaded()

        if (novel == null) refresh()
        loading = false
    }

    if (loading && novel == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val item = novel
    if (item == null) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(20.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, "Back")
            }
            Text(
                "Could not open novel",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    val canonical = remember(item.chapters) {
        canonicalChapters(item)
    }

    val chapterNumberByUrl = remember(canonical) {
        canonical.mapIndexed { index, chapter ->
            chapter.url to (index + 1)
        }.toMap()
    }

    val filtered = remember(canonical, chapterFilter) {
        if (chapterFilter.isBlank()) canonical
        else canonical.filter { chapter ->
            val number = chapterNumberByUrl[chapter.url]
            number?.toString() == chapterFilter.trim() ||
                chapter.title.contains(chapterFilter, ignoreCase = true)
        }
    }

    val resumeChapter = progress?.let { saved ->
        canonical.firstOrNull { it.url == saved.chapterUrl }
            ?: canonical.firstOrNull { it.index == saved.chapterIndex }
    }

    if (pickStart) {
        StartChapterPicker(
            novel = item,
            onDismiss = { pickStart = false },
            onPick = {
                pickStart = false
                downloadStart = it
            }
        )
    }

    downloadStart?.let { start ->
        DownloadFromDialog(
            novel = item,
            start = start,
            onDismiss = { downloadStart = null },
            onConfirm = { count ->
                enqueueFrom(context, item, start, count)
                downloadStart = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(330.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!item.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0x22000000),
                                Color(0x55000000),
                                Color(0xF0000000)
                            )
                        )
                    )
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(10.dp)
                        .background(
                            Color(0x77000000),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        "Back",
                        tint = Color.White
                    )
                }

                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Text(
                        graph.sources.get(sourceId)?.name ?: sourceId,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFFBDD0FF)
                    )
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.author?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = Color(0xFFE7E9F0),
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            (resumeChapter ?: canonical.firstOrNull())
                                ?.let(onRead)
                        },
                        enabled = canonical.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.MenuBook, null)
                        Text(
                            if (resumeChapter != null) {
                                " Continue"
                            } else {
                                " Read now"
                            },
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { pickStart = true },
                        enabled = canonical.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Text(
                            " Download",
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                canonical.size.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                "chapters",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                downloaded.size.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "offline",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (resumeChapter != null) {
                    val resumeNumber =
                        chapterNumberByUrl[resumeChapter.url] ?: 1

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRead(resumeChapter) }
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text(
                                "CONTINUE READING",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Chapter $resumeNumber · " +
                                    cleanedChapterTitle(resumeChapter.title),
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }

                if (newChapterCount > 0) {
                    Text(
                        "+$newChapterCount new chapter" +
                            if (newChapterCount == 1) "" else "s" +
                                " found",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Text(
                        item.genres.take(6).joinToString("  •  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (item.description.isNotBlank()) {
                    Text(
                        "About",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "1 → ${canonical.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        enabled = !refreshing,
                        onClick = { scope.launch { refresh() } }
                    ) {
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Rounded.Refresh,
                                "Check for new chapters"
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = chapterFilter,
                    onValueChange = { chapterFilter = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, null)
                    },
                    placeholder = {
                        Text("Find chapter number or title")
                    }
                )

                Text(
                    "Tap a chapter to read. Tap its download icon to " +
                        "download forward from that exact chapter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(filtered, key = { it.url }) { chapter ->
            val number = chapterNumberByUrl[chapter.url] ?: 1
            val isDownloaded = chapter.url in downloaded
            val isCurrent = resumeChapter?.url == chapter.url

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.45f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onRead(chapter) }
                    .padding(
                        start = 18.dp,
                        end = 8.dp,
                        top = 12.dp,
                        bottom = 12.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Box(
                        Modifier.size(width = 54.dp, height = 42.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            number.toString(),
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Column(
                    Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        cleanedChapterTitle(chapter.title),
                        fontWeight = if (isCurrent) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Medium
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isDownloaded) {
                        Text(
                            "Downloaded",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (isCurrent) {
                        Text(
                            "Current chapter",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = { downloadStart = chapter }
                ) {
                    Icon(
                        if (isDownloaded) {
                            Icons.Rounded.CheckCircle
                        } else {
                            Icons.Rounded.CloudDownload
                        },
                        contentDescription = if (isDownloaded) {
                            "Downloaded"
                        } else {
                            "Download from Chapter $number"
                        },
                        tint = if (isDownloaded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 84.dp)
            )
        }
    }
}
