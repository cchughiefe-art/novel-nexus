package com.novelatlas.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.novelatlas.app.AppGraph
import com.novelatlas.app.core.model.ChapterRef
import com.novelatlas.app.core.model.NovelDetails
import com.novelatlas.app.data.worker.NovelDownloadWorker

private fun enqueueDownload(context: Context, novel: NovelDetails) {
    val input = Data.Builder()
        .putString("sourceId", novel.sourceId)
        .putString("novelUrl", novel.url)
        .putString("title", novel.title)
        .putString("coverUrl", novel.coverUrl)
        .putString("author", novel.author)
        .putInt("from", 0)
        .putInt("to", maxOf(0, novel.chapters.size - 1))
        .build()
    val req = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setInputData(input)
        .addTag("novel:${novel.sourceId}:${novel.url.hashCode()}")
        .build()
    WorkManager.getInstance(context).enqueue(req)
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

    LaunchedEffect(sourceId, url) {
        loading = true
        error = null
        runCatching { graph.repository.novel(sourceId, url) }
            .onSuccess { novel = it }
            .onFailure { error = it.message ?: "Could not load novel" }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val item = novel
    if (item == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(error ?: "Novel unavailable", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
                Text(graph.sources.get(sourceId)?.name ?: sourceId, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(item.coverUrl, item.title, Modifier.size(width = 112.dp, height = 164.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    item.author?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp)) }
                    if (item.genres.isNotEmpty()) Text(item.genres.take(4).joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    Text("${item.chapters.size} chapters", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (item.description.isNotBlank()) {
                Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp))
                Text(item.description, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 7, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { item.chapters.firstOrNull()?.let(onRead) }, enabled = item.chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.MenuBook, null)
                    Text(" Read")
                }
                Button(onClick = { enqueueDownload(context, item) }, enabled = item.chapters.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Download, null)
                    Text(" Download novel")
                }
            }
            Text("Chapters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        items(item.chapters, key = { it.url }) { chapter ->
            Row(
                Modifier.fillMaxWidth().clickable { onRead(chapter) }.padding(horizontal = 16.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("${chapter.index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(chapter.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
