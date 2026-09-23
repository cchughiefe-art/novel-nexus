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
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
                .sortedByDescending { it.runAttemptCount }
                .take(12)

            delay(1200)
        }
    }

    val chapters = books.sumOf { it.chapters }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 14.dp,
            bottom = 28.dp
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
                "Live progress and everything saved on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp, bottom = 14.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Rounded.Storage,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "${books.size}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "novels",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Card(
                    Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "$chapters",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            "chapters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val activeOrRecent = jobs.filter {
            it.state == WorkInfo.State.RUNNING ||
                it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.FAILED
        }

        if (activeOrRecent.isNotEmpty()) {
            item {
                Text(
                    "Download activity",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(activeOrRecent, key = { it.id }) { info ->
                val title = info.progress.getString("title") ?: "Novel download"
                val done = info.progress.getInt("done", 0)
                val total = info.progress.getInt("total", 0)
                val failed = info.progress.getInt("failed", 0)
                val error = info.outputData.getString("error")

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (info.state == WorkInfo.State.FAILED) {
                                    Icons.Rounded.ErrorOutline
                                } else {
                                    Icons.Rounded.Download
                                },
                                contentDescription = null,
                                tint = if (info.state == WorkInfo.State.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )

                            Column(
                                Modifier.weight(1f).padding(horizontal = 12.dp)
                            ) {
                                Text(title, fontWeight = FontWeight.Bold)
                                Text(
                                    when (info.state) {
                                        WorkInfo.State.ENQUEUED -> "Queued"
                                        WorkInfo.State.RUNNING ->
                                            if (total > 0) "$done of $total chapters"
                                            else "Loading chapter list…"
                                        WorkInfo.State.FAILED ->
                                            error ?: "Download failed"
                                        else -> info.state.name
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!info.state.isFinished) {
                                IconButton(
                                    onClick = { manager.cancelWorkById(info.id) }
                                ) {
                                    Icon(
                                        Icons.Rounded.Cancel,
                                        contentDescription = "Cancel"
                                    )
                                }
                            }
                        }

                        if (info.state == WorkInfo.State.RUNNING && total > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (done.toFloat() / total.toFloat())
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            )
                            if (failed > 0) {
                                Text(
                                    "$failed chapters will retry",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Saved novels",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (books.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 42.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No chapters have been saved yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(books, key = { "${it.sourceId}:${it.novelUrl}" }) { book ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                book.title,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2
                            )
                            Text(
                                "${book.chapters} chapters saved",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
