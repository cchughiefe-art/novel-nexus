package com.novelnexus.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private enum class ReaderPalette(val bg: Color, val fg: Color, val secondary: Color) {
    NIGHT(Color(0xFF080B10), Color(0xFFECEEF4), Color(0xFFAEB7C7)),
    SEPIA(Color(0xFFF2E7D3), Color(0xFF322B22), Color(0xFF726453)),
    LIGHT(Color(0xFFFAFAF8), Color(0xFF1B1A18), Color(0xFF67635C));

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
    onBack: () -> Unit
) {
    var content by remember { mutableStateOf<ChapterContent?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(18f) }
    var palette by remember { mutableStateOf(ReaderPalette.NIGHT) }
    val listState = rememberLazyListState()
    val progress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 1) 0f else (listState.firstVisibleItemIndex.toFloat() / (total - 1).toFloat()).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(sourceId, chapterUrl) {
        runCatching {
            graph.repository.chapter(ChapterRef(sourceId, novelUrl, title, chapterUrl, index))
        }.onSuccess { content = it }.onFailure { error = it.message ?: "Could not load chapter" }
    }

    LaunchedEffect(chapterUrl) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collectLatest {
                delay(600)
                graph.repository.db().saveProgress(sourceId, novelUrl, chapterUrl, index, progress)
            }
    }

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = palette.fg) }
            Text(title, modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.SemiBold, color = palette.fg)
            IconButton(onClick = { palette = palette.next() }) { Icon(Icons.Rounded.Palette, "Reader theme", tint = palette.fg) }
            IconButton(onClick = { fontSize = (fontSize - 1f).coerceAtLeast(14f) }) { Icon(Icons.Rounded.Remove, "Smaller text", tint = palette.fg) }
            IconButton(onClick = { fontSize = (fontSize + 1f).coerceAtMost(30f) }) { Icon(Icons.Rounded.Add, "Larger text", tint = palette.fg) }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        when {
            content == null && error == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = MaterialTheme.colorScheme.error) }
            else -> {
                val chapter = content!!
                val paragraphs = chapter.plainText
                    .split(Regex("\\n\\s*\\n"))
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            chapter.title,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 18.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.fg
                        )
                    }
                    items(paragraphs.size) { position ->
                        Text(
                            paragraphs[position],
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                            style = TextStyle(
                                color = palette.fg,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.75f).sp,
                                fontFamily = FontFamily.Serif
                            )
                        )
                    }
                    item {
                        Text(
                            "End of chapter",
                            color = palette.secondary,
                            modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 72.dp)
                        )
                    }
                }
            }
        }
    }
}
