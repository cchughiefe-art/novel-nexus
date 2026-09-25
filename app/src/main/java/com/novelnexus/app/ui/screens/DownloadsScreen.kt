package com.novelnexus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.novelnexus.app.AppGraph
import com.novelnexus.app.data.db.NovelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun DownloadsScreen(graph: AppGraph) {
    val context = LocalContext.current
    val manager = remember { WorkManager.getInstance(context) }

    var books by remember {
        mutableStateOf<List<NovelDatabase.OfflineNovel>>(emptyList())
    }
    var jobs by remember {
        mutableStateOf<List<WorkInfo>>(emptyList())
    }

    LaunchedEffect(Unit) {
        while (true) {
            books = withContext(Dispatchers.IO) {
                graph.repository.db().offlineNovels()
            }
            jobs = withContext(Dispatchers.IO) {
                runCatching {
                    manager.getWorkInfosByTag("novel-download").get()
                }.getOrDefault(emptyList())
            }
            delay(900)
        }
    }

    val savedChapters = books.sumOf { it.chapters }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "Downloads",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                "Exact chapter ranges, live progress, and your offline shelf.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    value = books.size.toString(),
                    label = "novels offline",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    value = savedChapters.toString(),
                    label = "chapters saved",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val active = jobs.filter {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.FAILED
        }

        if (active.isNotEmpty()) {
            item {
                Text(
                    "Download activity",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(active, key = { it.id }) { info ->
                val data = if (info.state == WorkInfo.State.FAILED) {
                    info.outputData
                } else {
                    info.progress
                }

                val title = info.progress.getString("title")
                    ?: info.outputData.getString("title")
                    ?: "Novel download"

                val done = data.getInt("done", 0)
                val total = data.getInt("total", 0)
                val failed = data.getInt("failed", 0)
                val from = data.getInt("rangeFrom", 0)
                val to = data.getInt("rangeTo", 0)
                val current =
                    info.progress.getInt("currentChapterNumber", 0)
                val currentTitle =
                    info.progress.getString("currentChapterTitle").orEmpty()
                val error = info.outputData.getString("error")

                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (
                                    info.state == WorkInfo.State.FAILED
                                ) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Box(
                                    Modifier.padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (
                                            info.state == WorkInfo.State.FAILED
                                        ) {
                                            Icons.Rounded.ErrorOutline
                                        } else {
                                            Icons.Rounded.CloudDownload
                                        },
                                        contentDescription = null,
                                        tint = if (
                                            info.state == WorkInfo.State.FAILED
                                        ) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                            }

                            Column(
                                Modifier.weight(1f).padding(horizontal = 12.dp)
                            ) {
                                Text(
                                    title,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (from > 0 && to >= from) {
                                    Text(
                                        "Chapter $from → Chapter $to",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }

                            if (!info.state.isFinished) {
                                IconButton(
                                    onClick = {
                                        manager.cancelWorkById(info.id)
                                    }
                                ) {
                                    Icon(
                                        Icons.Rounded.Cancel,
                                        contentDescription = "Cancel download"
                                    )
                                }
                            }
                        }

                        Text(
                            when (info.state) {
                                WorkInfo.State.ENQUEUED ->
                                    "Waiting to start…"

                                WorkInfo.State.RUNNING ->
                                    if (current > 0) {
                                        "Downloading Chapter $current" +
                                            if (currentTitle.isNotBlank()) {
                                                " · $currentTitle"
                                            } else {
                                                ""
                                            }
                                    } else {
                                        "Preparing chapter list…"
                                    }

                                WorkInfo.State.FAILED ->
                                    error ?: "Download failed"

                                else -> info.state.name
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (
                            info.state == WorkInfo.State.RUNNING &&
                            total > 0
                        ) {
                            LinearProgressIndicator(
                                progress = {
                                    (done.toFloat() / total.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(Modifier.fillMaxWidth()) {
                                Text(
                                    "$done / $total",
                                    style = MaterialTheme.typography.labelLarge,
                                    color =
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (failed > 0) {
                                    Text(
                                        " · $failed failed",
                                        style =
                                            MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Offline library",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (books.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Nothing downloaded yet",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                        Text(
                            "Open a novel and tap the download icon beside any chapter.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                books,
                key = { "${it.sourceId}:${it.novelUrl}" }
            ) { book ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color =
                                MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Rounded.DownloadDone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(11.dp)
                            )
                        }

                        Column(
                            Modifier.weight(1f).padding(start = 13.dp)
                        ) {
                            Text(
                                book.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${book.chapters} chapters offline",
                                color =
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(
                Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                label,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
