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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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
import kotlinx.coroutines.withContext

private fun enqueueNovelDownload(context: Context, novel: NovelDetails) {
    if (novel.chapters.isEmpty()) return

    val input = Data.Builder()
        .putString("sourceId", novel.sourceId)
        .putString("novelUrl", novel.url)
        .putString("title", novel.title)
        .putString("coverUrl", novel.coverUrl)
        .putString("author", novel.author)
        .putInt("from", 0)
        .putInt("to", novel.chapters.lastIndex)
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
        .build()

    WorkManager.getInstance(context).enqueue(request)
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
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var novel by remember { mutableStateOf<NovelDetails?>(null) }
    var progress by remember { mutableStateOf<NovelDatabase.ReadingProgress?>(null) }
    var chapterFilter by remember { mutableStateOf("") }

    LaunchedEffect(sourceId, url) {
        loading = true
        error = null

        runCatching { graph.repository.novel(sourceId, url) }
            .onSuccess { novel = it }
            .onFailure { error = it.message ?: "Could not load this novel." }

        progress = withContext(Dispatchers.IO) {
            graph.repository.db().getReadingProgress(sourceId, url)
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val item = novel
    if (item == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(18.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }
            Text("Could not open novel", style = MaterialTheme.typography.titleLarge)
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val filtered = remember(item.chapters, chapterFilter) {
        val matching = if (chapterFilter.isBlank()) {
            item.chapters
        } else {
            item.chapters.filter {
                it.title.contains(chapterFilter, ignoreCase = true) ||
                    (it.index + 1).toString() == chapterFilter.trim()
            }
        }
        matching.sortedByDescending { it.index }
    }

    val resumeChapter = progress?.let { saved ->
        item.chapters.firstOrNull { it.url == saved.chapterUrl }
            ?: item.chapters.getOrNull(saved.chapterIndex)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 30.dp)
    ) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp)
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
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color(0x44000000),
                                    androidx.compose.ui.graphics.Color(0x77000000),
                                    androidx.compose.ui.graphics.Color(0xF0000000)
                                )
                            )
                        )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(8.dp)
                        .background(
                            androidx.compose.ui.graphics.Color(0x66000000),
                            RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(20.dp)
                ) {
                    Text(
                        graph.sources.get(sourceId)?.name ?: sourceId,
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color(0xFFFFC7B9)
                    )
                    Text(
                        item.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.author?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = androidx.compose.ui.graphics.Color(0xFFE7E1DE),
                            modifier = Modifier.padding(top = 5.dp)
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            (resumeChapter ?: item.chapters.firstOrNull())?.let(onRead)
                        },
                        enabled = item.chapters.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.MenuBook, contentDescription = null)
                        Text(if (resumeChapter != null) " Continue" else " Read")
                    }
                    FilledTonalButton(
                        onClick = { enqueueNovelDownload(context, item) },
                        enabled = item.chapters.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = null)
                        Text(" Download")
                    }
                }

                if (resumeChapter != null) {
                    Text(
                        "Resume: ${resumeChapter.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                if (item.genres.isNotEmpty()) {
                    Text(
                        item.genres.take(6).joinToString("  •  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text("About", style = MaterialTheme.typography.titleLarge)
                    Text(
                        item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chapters", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${item.chapters.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                OutlinedTextField(
                    value = chapterFilter,
                    onValueChange = { chapterFilter = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                    },
                    placeholder = { Text("Find chapter number or title") }
                )
            }
        }

        items(filtered, key = { it.url }) { chapter ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onRead(chapter) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${chapter.index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 14.dp)
                )
                Text(
                    chapter.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
