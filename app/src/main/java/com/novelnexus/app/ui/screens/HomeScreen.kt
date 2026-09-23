package com.novelnexus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.data.repo.NovelRepository
import com.novelnexus.app.ui.components.HeroNovelCard
import com.novelnexus.app.ui.components.NovelPosterCard
import com.novelnexus.app.ui.components.NovelRowCard
import com.novelnexus.app.ui.components.SectionTitle

@Composable
fun HomeScreen(graph: AppGraph, onOpen: (NovelCard) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var sections by remember {
        mutableStateOf<List<NovelRepository.SourceResult<List<NovelCard>>>>(emptyList())
    }

    LaunchedEffect(Unit) {
        sections = graph.repository.latest()
        loading = false
    }

    val allBooks = sections
        .flatMap { it.data.orEmpty() }
        .distinctBy { "${it.sourceId}:${it.url}" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "NOVEL NEXUS",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Find your next world.", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Fresh chapters and full novels from your enabled sources.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (loading) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Building your shelf…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (allBooks.isEmpty()) {
            item {
                Text(
                    "No source returned books right now. Try Search or check Sources in Settings.",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                val featured = allBooks.first()
                HeroNovelCard(
                    item = featured,
                    sourceName = graph.sources.get(featured.sourceId)?.name ?: featured.sourceId,
                    onClick = { onOpen(featured) },
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }

            item {
                SectionTitle(
                    title = "Popular right now",
                    subtitle = "A quick mix from every enabled source",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(allBooks.take(12), key = { "${it.sourceId}:${it.url}" }) { novel ->
                        NovelPosterCard(
                            item = novel,
                            sourceName = graph.sources.get(novel.sourceId)?.name ?: novel.sourceId,
                            onClick = { onOpen(novel) }
                        )
                    }
                }
            }

            item {
                SectionTitle(
                    title = "Latest updates",
                    subtitle = "Recently surfaced chapters",
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }

            items(allBooks.drop(1).take(20), key = { "${it.sourceId}:${it.url}" }) { novel ->
                NovelRowCard(
                    item = novel,
                    sourceName = graph.sources.get(novel.sourceId)?.name ?: novel.sourceId,
                    onClick = { onOpen(novel) },
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            }
        }
    }
}
