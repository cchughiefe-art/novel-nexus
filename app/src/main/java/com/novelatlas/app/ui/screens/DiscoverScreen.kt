package com.novelatlas.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material3.CircularProgressIndicator
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
import com.novelatlas.app.AppGraph
import com.novelatlas.app.core.model.NovelCard
import com.novelatlas.app.data.repo.NovelRepository
import com.novelatlas.app.ui.components.NovelCardView

@Composable
fun DiscoverScreen(graph: AppGraph, onOpen: (NovelCard) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var sections by remember { mutableStateOf<List<NovelRepository.SourceResult<List<NovelCard>>>>(emptyList()) }
    LaunchedEffect(Unit) {
        sections = graph.repository.latest()
        loading = false
    }
    if (loading) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Loading fresh novels…")
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("NovelAtlas", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("All your approved novel sources. One fast reader.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
        }
        sections.forEach { section ->
            val source = graph.sources.get(section.sourceId)
            item {
                Text(source?.name ?: section.sourceId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                if (section.error != null) Text(section.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            items(section.data.orEmpty().take(10), key = { "${it.sourceId}:${it.url}" }) { novel ->
                NovelCardView(novel, source?.name ?: novel.sourceId) { onOpen(novel) }
            }
        }
        if (sections.all { it.data.isNullOrEmpty() }) {
            item { Text("No source returned content right now. Search can still be used source-by-source.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
