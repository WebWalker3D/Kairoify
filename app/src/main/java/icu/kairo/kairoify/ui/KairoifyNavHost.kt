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
package icu.kairo.kairoify.ui

import androidx.compose.runtime.Composable
import icu.kairo.kairoify.ui.album.opensDetails
import icu.kairo.kairoify.ui.album.AlbumViewModel
import icu.kairo.kairoify.ui.album.AlbumScreen
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.FacetKind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import icu.kairo.kairoify.core.data.FavoritesStore
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.ui.library.LibraryScreen
import icu.kairo.kairoify.ui.library.LibraryViewModel
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.ui.explore.BrowseScreen
import icu.kairo.kairoify.ui.explore.BrowseViewModel
import icu.kairo.kairoify.ui.explore.ExploreScreen
import icu.kairo.kairoify.ui.explore.FacetsScreen
import icu.kairo.kairoify.ui.explore.FacetsViewModel
import icu.kairo.kairoify.ui.home.HomeScreen
import icu.kairo.kairoify.ui.home.HomeViewModel
import icu.kairo.kairoify.ui.search.SearchScreen
import icu.kairo.kairoify.ui.search.SearchViewModel

@Composable
fun KairoifyNavHost(
    navController: NavHostController,
    registry: SourceRegistry,
    favorites: FavoritesStore,
    about: () -> AboutViewModel,
    onPlay: (Item) -> Unit,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onToggleFavorite: (Item) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Albums and concerts open their own page; stations and songs play.
    val open: (Item) -> Unit = { item ->
        if (opensDetails(item)) navController.navigate(AlbumRoute(item.ref.sourceId, item.ref.itemId, item.title)) else onPlay(item)
    }
    NavHost(navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(viewModel { HomeViewModel(registry) }, open, onLongPress, onAbout = { navController.navigate(AboutRoute) })
        }
        composable<SearchRoute> { SearchScreen(viewModel { SearchViewModel(registry) }, open, onLongPress) }
        composable<ExploreRoute> {
            ExploreScreen(
                onOpenFacets = { navController.navigate(FacetsRoute(it)) },
                onOpenArchives = { navController.navigate(FacetsRoute(FacetKind.COLLECTION, sourceId = "archive")) },
            )
        }
        composable<FacetsRoute> { entry ->
            val route = entry.toRoute<FacetsRoute>()
            FacetsScreen(viewModel { FacetsViewModel(registry, route.sourceId, route.kind) }, route.kind) { facet ->
                navController.navigate(BrowseRoute.of(route.sourceId, facet.node, facet.label))
            }
        }
        composable<BrowseRoute> { entry ->
            val route = entry.toRoute<BrowseRoute>()
            BrowseScreen(viewModel { BrowseViewModel(registry, route.sourceId, route.node()) }, route.title, open, onLongPress)
        }
        composable<LibraryRoute> {
            LibraryScreen(
                viewModel { LibraryViewModel(favorites) },
                sourceName = { id -> registry.sources.firstOrNull { it.info.id == id }?.info?.displayName ?: id },
                onPlayQueue = onPlayQueue,
                onLongPress = onLongPress,
                onOpen = open,
            )
        }
        composable<AlbumRoute> { entry ->
            val route = entry.toRoute<AlbumRoute>()
            val ref = MediaRef(route.sourceId, route.itemId)
            val isFavorite by remember(ref) { favorites.isFavorite(ref) }.collectAsStateWithLifecycle(false)
            AlbumScreen(
                viewModel { AlbumViewModel(registry, ref) },
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPlayQueue = onPlayQueue,
                onLongPress = onLongPress,
                onOpenLink = onOpenLink,
            )
        }
        composable<AboutRoute> { AboutScreen(viewModel { about() }) }
    }
}
