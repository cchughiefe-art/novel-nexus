package com.novelnexus.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class ReaderPalette(
    val background: Color,
    val foreground: Color,
    val muted: Color
) {
    NIGHT(Color(0xFF090A0C), Color(0xFFF0EEE9), Color(0xFFAAA49B)),
    SEPIA(Color(0xFFF0E4CC), Color(0xFF34291D), Color(0xFF776855)),
    LIGHT(Color(0xFFFFFCF8), Color(0xFF1D1915), Color(0xFF6F675F));

    fun next(): ReaderPalette = entries[(ordinal + 1) % entries.size]
}

@Composable
fun ReaderScreen(
    graph: AppGraph,
    sourceId: String,
    novelUrl: String,
    chapterUrl: String,
    title: String,
    index: Int,
    onBack: () -> Unit,
    onNavigateChapter: (ChapterRef) -> Unit
) {
    var content by remember { mutableStateOf<ChapterContent?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterRef>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(19f) }
    var palette by remember { mutableStateOf(ReaderPalette.NIGHT) }
    var showChrome by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    LaunchedEffect(sourceId, novelUrl, chapterUrl) {
        error = null
        val current = ChapterRef(sourceId, novelUrl, title, chapterUrl, index)

        runCatching { graph.repository.chapter(current) }
            .onSuccess { content = it }
            .onFailure { error = it.message ?: "Could not load chapter." }

        chapters = runCatching { graph.repository.chapters(sourceId, novelUrl) }
            .getOrDefault(emptyList())
    }

    val paragraphs = remember(content?.plainText) {
        content?.plainText
            ?.split(Regex("\\n\\s*\\n"))
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    val progress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f
            else (listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat())
                .coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(content?.chapterUrl, paragraphs.size) {
        val loaded = content ?: return@LaunchedEffect
        if (paragraphs.isEmpty()) return@LaunchedEffect

        val saved = withContext(Dispatchers.IO) {
            graph.repository.db().getReadingProgress(sourceId, novelUrl)
        }

        if (saved?.chapterUrl == loaded.chapterUrl && saved.progress > 0f) {
            val target = (saved.progress * (paragraphs.size + 1))
                .roundToInt()
                .coerceIn(0, paragraphs.size)
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(content?.chapterUrl) {
        if (content == null) return@LaunchedEffect

        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collectLatest {
            delay(500)
            withContext(Dispatchers.IO) {
                graph.repository.db().saveProgress(
                    sourceId,
                    novelUrl,
                    chapterUrl,
                    index,
                    progress
                )
            }
        }
    }

    val currentPosition = chapters.indexOfFirst { it.url == chapterUrl }
        .takeIf { it >= 0 } ?: index
    val previous = chapters.getOrNull(currentPosition - 1)
    val next = chapters.getOrNull(currentPosition + 1)

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        if (showChrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = palette.foreground)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = palette.foreground,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        "Chapter ${index + 1}",
                        color = palette.muted,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                IconButton(onClick = { palette = palette.next() }) {
                    Icon(Icons.Rounded.Palette, "Theme", tint = palette.foreground)
                }
                IconButton(onClick = {
                    fontSize = (fontSize - 1f).coerceAtLeast(14f)
                }) {
                    Icon(Icons.Rounded.Remove, "Smaller", tint = palette.foreground)
                }
                IconButton(onClick = {
                    fontSize = (fontSize + 1f).coerceAtMost(30f)
                }) {
                    Icon(Icons.Rounded.Add, "Larger", tint = palette.foreground)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        when {
            content == null && error == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            error != null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable { showChrome = !showChrome }
                ) {
                    item {
                        Text(
                            content!!.title,
                            modifier = Modifier.padding(
                                start = 22.dp,
                                end = 22.dp,
                                top = 28.dp,
                                bottom = 20.dp
                            ),
                            style = TextStyle(
                                color = palette.foreground,
                                fontSize = (fontSize + 5f).sp,
                                lineHeight = (fontSize + 12f).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                        )
                    }

                    items(paragraphs) { paragraph ->
                        Text(
                            paragraph,
                            modifier = Modifier.padding(
                                horizontal = 22.dp,
                                vertical = 9.dp
                            ),
                            style = TextStyle(
                                color = palette.foreground,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.72f).sp,
                                fontFamily = FontFamily.Serif
                            )
                        )
                    }

                    item {
                        Text(
                            "End of chapter",
                            color = palette.muted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 34.dp)
                        )
                    }
                }

                if (showChrome) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.background)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { previous?.let(onNavigateChapter) },
                            enabled = previous != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, null)
                            Text(" Previous")
                        }
                        FilledTonalButton(
                            onClick = { next?.let(onNavigateChapter) },
                            enabled = next != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Next ")
                            Icon(Icons.Rounded.ArrowForward, null)
                        }
                    }
                }
            }
        }
    }
}
