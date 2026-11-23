/*
 * Copyright (c) 2026 WebWalker3D. All Rights Reserved.
 *
 * The contents of this file are subject to the Common Public Attribution License
 * Version 1.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * https://github.com/WebWalker3D/Kairoify/blob/main/LICENSE. The License is based on
 * the Mozilla Public License Version 1.1 but Sections 14 and 15 have been added to
 * cover use of software over a computer network and provide for limited attribution
 * for the Original Developer. In addition, Exhibit A has been modified to be
 * consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT
 * WARRANTY OF ANY KIND, either express or implied. See the License for the specific
 * language governing rights and limitations under the License.
 *
 * The Original Code is Kairoify. The Initial Developer of the Original Code is
 * WebWalker3D. Attribution information: see NOTICE.md.
 */
package icu.kairo.kairoify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import icu.kairo.kairoify.core.data.FavoritesStore
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.player.PlayerConnection
import icu.kairo.kairoify.player.canStartRadio
import icu.kairo.kairoify.player.radioQueue
import icu.kairo.kairoify.ui.AboutViewModel
import icu.kairo.kairoify.ui.AttributionSplash
import icu.kairo.kairoify.ui.ExploreRoute
import icu.kairo.kairoify.ui.HomeRoute
import icu.kairo.kairoify.ui.KairoifyNavHost
import icu.kairo.kairoify.ui.LibraryRoute
import icu.kairo.kairoify.ui.SearchRoute
import icu.kairo.kairoify.ui.components.ItemActionsSheet
import icu.kairo.kairoify.ui.components.rememberLinkOpener
import icu.kairo.kairoify.ui.icons.KairoifyIcons
import icu.kairo.kairoify.ui.player.MiniPlayer
import icu.kairo.kairoify.ui.player.NowPlayingSheet
import icu.kairo.kairoify.ui.theme.KairoifyTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as KairoifyApp
        setContent {
            KairoifyTheme {
                var splashDone by remember { mutableStateOf(app.attributionShown) }
                if (!splashDone) {
                    AttributionSplash(onFinished = {
                        app.attributionShown = true
                        splashDone = true
                    })
                } else {
                    val container = app.container
                    KairoifyScaffold(container.sourceRegistry, container.playerConnection, container.favorites) {
                        AboutViewModel(container.engine, container.updater)
                    }
                }
            }
        }
    }
}

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(HomeRoute, "Home", Icons.Filled.Home),
    Tab(SearchRoute, "Search", Icons.Filled.Search),
    Tab(ExploreRoute, "Explore", KairoifyIcons.Globe),
    Tab(LibraryRoute, "Library", KairoifyIcons.Library),
)

@Composable
private fun KairoifyScaffold(
    registry: SourceRegistry,
    player: PlayerConnection,
    favorites: FavoritesStore,
    about: () -> AboutViewModel,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Item?>(null) }
    val openLink = rememberLinkOpener { scope.launch { snackbar.showSnackbar("No app on this phone can open that link.") } }
    val toggleFavorite: (Item) -> Unit = { item -> scope.launch { favorites.toggle(item) } }
    val startRadio: (Item) -> Unit = { item ->
        scope.launch {
            try {
                val related = registry.related(item.ref)
                if (related.isEmpty()) snackbar.showSnackbar("No radio for this song yet.") else player.playAll(radioQueue(item, related))
            } catch (e: SourceException) {
                snackbar.showSnackbar(e.message ?: "Couldn't start radio.")
            }
        }
    }
    val np = nowPlaying
    val npItem = np?.item
    val npFavorite by rememberIsFavorite(favorites, npItem?.ref)
    LaunchedEffect(Unit) { player.connect() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                np?.let {
                    MiniPlayer(
                        it,
                        onToggle = player::togglePlayPause,
                        onOpen = { showSheet = true },
                        isFavorite = npFavorite,
                        onToggleFavorite = npItem?.let { item -> { toggleFavorite(item) } },
                    )
                }
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = backStack?.destination?.hasRoute(tab.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(selectedTextColor = MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        },
    ) { padding ->
        KairoifyNavHost(
            navController,
            registry,
            favorites,
            about = about,
            onPlay = player::play,
            onPlayQueue = { items, start, shuffle -> player.playAll(items, start, shuffle) },
            onLongPress = { actionsFor = it },
            onToggleFavorite = toggleFavorite,
            onOpenLink = openLink,
            modifier = Modifier.padding(padding),
        )
    }
    if (showSheet && np != null) {
        NowPlayingSheet(
            nowPlaying = np,
            sourceName = sourceName(registry, np.ref.sourceId),
            player = player,
            isFavorite = npFavorite,
            onToggleFavorite = npItem?.let { item -> { toggleFavorite(item) } },
            onOpenLink = openLink,
            onStopped = { showSheet = false },
            onDismiss = { showSheet = false },
            extraActions = {
                if (npItem != null && canStartRadio(registry, npItem)) {
                    TextButton(onClick = { startRadio(npItem) }) { Text("Start radio") }
                }
            },
        )
    }
    actionsFor?.let { item ->
        val isFavorite by rememberIsFavorite(favorites, item.ref)
        ItemActionsSheet(
            item = item,
            isFavorite = isFavorite,
            onToggleFavorite = { toggleFavorite(item) },
            onOpenLink = openLink,
            onDismiss = { actionsFor = null },
            extraActions = {
                if (canStartRadio(registry, item)) {
                    ListItem(
                        leadingContent = { Icon(KairoifyIcons.Radio, contentDescription = null) },
                        modifier = Modifier.clickable {
                            startRadio(item)
                            actionsFor = null
                        },
                    ) { Text("Start radio") }
                }
            },
        )
    }
}

@Composable
private fun rememberIsFavorite(favorites: FavoritesStore, ref: MediaRef?) =
    remember(ref) { ref?.let(favorites::isFavorite) ?: flowOf(false) as Flow<Boolean> }.collectAsStateWithLifecycle(false)

private fun sourceName(registry: SourceRegistry, sourceId: String): String =
    registry.sources.firstOrNull { it.info.id == sourceId }?.info?.displayName ?: sourceId
