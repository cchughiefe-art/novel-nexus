package com.novelnexus.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.novelnexus.app.AppGraph

@Composable
fun SettingsScreen(graph: AppGraph) {
    val sources = graph.sources.all()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Choose where Novel Nexus searches and downloads from.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
            )
            Text("Sources", style = MaterialTheme.typography.titleLarge)
        }

        items(sources, key = { it.id }) { source ->
            var checked by remember(source.id) {
                mutableStateOf(graph.sources.isEnabled(source.id))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Rounded.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f)) {
                    Text(source.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        source.baseUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        graph.sources.setEnabled(source.id, it)
                    }
                )
            }
            HorizontalDivider()
        }

        item {
            Row(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Novel Nexus 0.2.0", fontWeight = FontWeight.Bold)
                    Text(
                        "Reader-first Android build. Source availability depends on each website.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
