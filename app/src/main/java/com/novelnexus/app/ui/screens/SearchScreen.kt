package com.novelnexus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.data.repo.NovelRepository
import com.novelnexus.app.ui.components.NovelPosterCard
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(graph: AppGraph, onOpen: (NovelCard) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var results by remember {
        mutableStateOf<List<NovelRepository.SourceResult<List<NovelCard>>>>(emptyList())
    }

    fun runSearch() {
        val term = query.trim()
        if (term.length < 2 || loading) return
        scope.launch {
            loading = true
            searched = true
            results = graph.repository.searchAll(term)
            loading = false
        }
    }

    val books = results
        .flatMap { it.data.orEmpty() }
        .distinctBy { "${it.sourceId}:${it.url}" }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Search",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
        )
        Text(
            "Search all enabled sources at once.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 14.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            placeholder = { Text("Novel title, author, keyword…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = {
                        query = ""
                        searched = false
                        results = emptyList()
                    }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() })
        )

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            !searched -> Column(
                Modifier.fillMaxSize().padding(top = 34.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Search tips", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Try a full title first. You can also search by author or a strong keyword.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${graph.sources.enabled().size} sources enabled",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            books.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No matches found. Try a shorter title or check your enabled sources.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> {
                Text(
                    "${books.size} results",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(books, key = { "${it.sourceId}:${it.url}" }) { novel ->
                        NovelPosterCard(
                            item = novel,
                            sourceName = graph.sources.get(novel.sourceId)?.name ?: novel.sourceId,
                            onClick = { onOpen(novel) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
