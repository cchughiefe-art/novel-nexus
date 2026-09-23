package com.novelnexus.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryBooks
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.novelnexus.app.AppGraph
import com.novelnexus.app.core.model.NovelCard
import com.novelnexus.app.data.db.NovelDatabase
import com.novelnexus.app.ui.screens.DiscoverScreen
import com.novelnexus.app.ui.screens.LibraryScreen
import com.novelnexus.app.ui.screens.NovelDetailScreen
import com.novelnexus.app.ui.screens.ReaderScreen
import com.novelnexus.app.ui.screens.SearchScreen
import com.novelnexus.app.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val tabs = listOf(
    Tab("discover", "Discover", Icons.Rounded.Explore),
    Tab("search", "Search", Icons.Rounded.Search),
    Tab("library", "Library", Icons.Rounded.LibraryBooks),
    Tab("settings", "Settings", Icons.Rounded.Settings)
)

private fun detailRoute(sourceId: String, url: String) =
    "detail?sourceId=${Uri.encode(sourceId)}&url=${Uri.encode(url)}"

private fun readerRoute(sourceId: String, novelUrl: String, chapterUrl: String, title: String, index: Int) =
    "reader?sourceId=${Uri.encode(sourceId)}&novelUrl=${Uri.encode(novelUrl)}&chapterUrl=${Uri.encode(chapterUrl)}&title=${Uri.encode(title)}&index=$index"

@Composable
fun NovelNexusRoot(graph: AppGraph) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()
    val showBottom = current in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController = nav, startDestination = "discover", modifier = Modifier.padding(padding)) {
            composable("discover") {
                DiscoverScreen(graph) { novel -> nav.navigate(detailRoute(novel.sourceId, novel.url)) }
            }
            composable("search") {
                SearchScreen(graph) { novel -> nav.navigate(detailRoute(novel.sourceId, novel.url)) }
            }
            composable("library") {
                LibraryScreen(graph) { item -> nav.navigate(detailRoute(item.sourceId, item.novelUrl)) }
            }
            composable("settings") { SettingsScreen(graph) }
            composable(
                route = "detail?sourceId={sourceId}&url={url}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) { backStack ->
                val sourceId = Uri.decode(backStack.arguments?.getString("sourceId").orEmpty())
                val url = Uri.decode(backStack.arguments?.getString("url").orEmpty())
                NovelDetailScreen(
                    graph = graph,
                    sourceId = sourceId,
                    url = url,
                    onBack = { nav.popBackStack() },
                    onRead = { ref -> nav.navigate(readerRoute(ref.sourceId, ref.novelUrl, ref.url, ref.title, ref.index)) }
                )
            }
            composable(
                route = "reader?sourceId={sourceId}&novelUrl={novelUrl}&chapterUrl={chapterUrl}&title={title}&index={index}",
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("novelUrl") { type = NavType.StringType },
                    navArgument("chapterUrl") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                    navArgument("index") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { backStack ->
                ReaderScreen(
                    graph = graph,
                    sourceId = Uri.decode(backStack.arguments?.getString("sourceId").orEmpty()),
                    novelUrl = Uri.decode(backStack.arguments?.getString("novelUrl").orEmpty()),
                    chapterUrl = Uri.decode(backStack.arguments?.getString("chapterUrl").orEmpty()),
                    title = Uri.decode(backStack.arguments?.getString("title").orEmpty()),
                    index = backStack.arguments?.getInt("index") ?: 0,
                    onBack = { nav.popBackStack() }
                )
            }
        }
    }
}
