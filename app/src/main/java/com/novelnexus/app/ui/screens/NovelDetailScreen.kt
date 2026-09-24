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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

private data class DownloadRange(val from: Int, val to: Int, val label: String)

private fun enqueueRange(context: Context, novel: NovelDetails, from: Int, to: Int) {
    val input = Data.Builder()
        .putString("sourceId", novel.sourceId)
        .putString("novelUrl", novel.url)
        .putString("title", novel.title)
        .putString("coverUrl", novel.coverUrl)
        .putString("author", novel.author)
        .putInt("from", from)
        .putInt("to", to)
        .build()

    val request = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(input)
        .addTag("novel-download")
        .addTag("novel:${novel.sourceId}:${novel.url.hashCode()}")
        .addTag("range:$from-$to")
        .build()

    WorkManager.getInstance(context).enqueue(request)
}

@Composable
private fun DownloadPicker(
    novel: NovelDetails,
    onDismiss: () -> Unit,
    onDownloadSelected: (List<DownloadRange>) -> Unit,
    onDownloadAll: () -> Unit
) {
    val canonical = remember(novel.chapters) { novel.chapters.sortedBy { it.index } }
    val groups = remember(canonical) {
        canonical.chunked(10).map { chunk ->
            val first = chunk.first()
            val last = chunk.last()
            DownloadRange(
                first.index,
                last.index,
                if (first.index == last.index) "Chapter ${first.index + 1}"
                else "Chapters ${first.index + 1}–${last.index + 1}"
            )
        }
    }
    var selected by remember { mutableStateOf<Set<Int>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose chapters") },
        text = {
            LazyColumn(modifier = Modifier.height(360.dp)) {
                itemsIndexed(groups) { index, group ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (index in selected) selected - index else selected + index
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = index in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) selected + index else selected - index
                            }
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(group.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${group.to - group.from + 1} chapter(s)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    onDownloadSelected(selected.sorted().mapNotNull { groups.getOrNull(it) })
                }
            ) { Text("Download selected") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDownloadAll) { Text("Download all") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
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
    var progress by remember { mutableStateOf<NovelDatabase.ReadingProgress?>(null) }
    var chapterFilter by remember { mutableStateOf("") }
    var showDownloads by remember { mutableStateOf(false) }
    var newChapterCount by remember { mutableStateOf(0) }

    suspend fun refresh() {
        refreshing = true
        runCatching { graph.repository.refreshNovel(sourceId, url) }
            .onSuccess {
                novel = it.details
                newChapterCount = it.newChapterCount
                error = null
            }
            .onFailure { error = it.message ?: "Could not refresh this novel." }
        refreshing = false
    }

    LaunchedEffect(sourceId, url) {
        loading = true
        refresh()
        progress = withContext(Dispatchers.IO) {
            graph.repository.db().getReadingProgress(sourceId, url)
        }
        loading = false
    }

    if (loading && novel == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val item = novel
    if (item == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(18.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text("Could not open novel", style = MaterialTheme.typography.titleLarge)
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    if (showDownloads) {
        DownloadPicker(
            novel = item,
            onDismiss = { showDownloads = false },
            onDownloadSelected = { ranges ->
                ranges.forEach { enqueueRange(context, item, it.from, it.to) }
                showDownloads = false
            },
            onDownloadAll = {
                val c = item.chapters.sortedBy { it.index }
                if (c.isNotEmpty()) enqueueRange(context, item, c.first().index, c.last().index)
                showDownloads = false
            }
        )
    }

    val filtered = remember(item.chapters, chapterFilter) {
        val matching = if (chapterFilter.isBlank()) item.chapters else item.chapters.filter {
            it.title.contains(chapterFilter, true) || (it.index + 1).toString() == chapterFilter.trim()
        }
        matching.sortedBy { it.index }
    }

    val resumeChapter = progress?.let { saved ->
        item.chapters.firstOrNull { it.url == saved.chapterUrl }
            ?: item.chapters.firstOrNull { it.index == saved.chapterIndex }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (!item.coverUrl.isNullOrBlank()) {
                    AsyncImage(item.coverUrl, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
                    androidx.compose.ui.graphics.Color(0x44000000),
                    androidx.compose.ui.graphics.Color(0x77000000),
                    androidx.compose.ui.graphics.Color(0xF0000000)
                ))))
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(8.dp)
                        .background(androidx.compose.ui.graphics.Color(0x66000000), RoundedCornerShape(50))
                ) { Icon(Icons.Rounded.ArrowBack, "Back", tint = androidx.compose.ui.graphics.Color.White) }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Text(graph.sources.get(sourceId)?.name ?: sourceId,
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color(0xFFFFC7B9))
                    Text(item.title, style = MaterialTheme.typography.headlineMedium,
                        color = androidx.compose.ui.graphics.Color.White, maxLines = 3,
                        overflow = TextOverflow.Ellipsis)
                    item.author?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = androidx.compose.ui.graphics.Color(0xFFE7E1DE),
                            modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }

            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { (resumeChapter ?: item.chapters.minByOrNull { it.index })?.let(onRead) },
                        enabled = item.chapters.isNotEmpty(), modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.MenuBook, null)
                        Text(if (resumeChapter != null) " Continue" else " Read")
                    }
                    FilledTonalButton(
                        onClick = { showDownloads = true },
                        enabled = item.chapters.isNotEmpty(), modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Download, null)
                        Text(" Download")
                    }
                }

                if (newChapterCount > 0) {
                    Text(
                        "+$newChapterCount new chapter${if (newChapterCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (resumeChapter != null) {
                    Text("Resume: ${resumeChapter.title}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                }
                if (item.genres.isNotEmpty()) {
                    Text(item.genres.take(6).joinToString("  •  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    Text("About", style = MaterialTheme.typography.titleLarge)
                    Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp), maxLines = 10, overflow = TextOverflow.Ellipsis)
                }

                Spacer(Modifier.height(22.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Chapters", style = MaterialTheme.typography.titleLarge)
                    Text("${item.chapters.size}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                    Spacer(Modifier.weight(1f))
                    IconButton(enabled = !refreshing, onClick = { scope.launch { refresh() } }) {
                        if (refreshing) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        else Icon(Icons.Rounded.Refresh, "Check for new chapters")
                    }
                }

                OutlinedTextField(
                    value = chapterFilter, onValueChange = { chapterFilter = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Find chapter number or title") }
                )
            }
        }

        items(filtered, key = { it.url }) { chapter ->
            Row(
                Modifier.fillMaxWidth().clickable { onRead(chapter) }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${chapter.index + 1}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp))
                Text(chapter.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
