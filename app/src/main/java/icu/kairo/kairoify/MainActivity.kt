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
import icu.kairo.kairoify.core.data.HistoryStore
import icu.kairo.kairoify.core.data.PlaylistStore
import icu.kairo.kairoify.local.LocalMusicSource
import icu.kairo.kairoify.core.model.isPlayable
import icu.kairo.kairoify.ui.components.AddToPlaylistSheet
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.player.PlayerConnection
import icu.kairo.kairoify.player.canStartRadio
import icu.kairo.kairoify.player.radioQueue
import icu.kairo.kairoify.ui.AboutViewModel
import icu.kairo.kairoify.ui.components.StartOverPill
import icu.kairo.kairoify.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow
import icu.kairo.kairoify.player.FreeRadio
import icu.kairo.kairoify.ui.home.PersonalShelves
import icu.kairo.kairoify.importing.PlaylistImporter
import icu.kairo.kairoify.source.lyrics.LrcLib
import icu.kairo.kairoify.source.lyrics.CaptionFinder
import icu.kairo.kairoify.player.findWords
import icu.kairo.kairoify.player.transcription
import icu.kairo.kairoify.core.transcribe.Transcriber
import icu.kairo.kairoify.ui.player.TranscribeOffer
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.mainArtist
import icu.kairo.kairoify.player.seedArtist
import icu.kairo.kairoify.player.RadioSong
import icu.kairo.kairoify.player.findRadioSong
import icu.kairo.kairoify.player.radioSong
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.flow.first
import androidx.compose.material3.SnackbarDuration
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.settings.SourceCategory
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
                    KairoifyScaffold(
                        container.sourceRegistry,
                        container.playerConnection,
                        container.favorites,
                        container.history,
                        container.playlists,
                        container.localMusic,
                        about = { AboutViewModel(container.engine, container.updater) },
                        settings = { SettingsViewModel(container.sourceCatalog, container.sourceSettings) },
                        sourceChanges = container.sourceSettings.changes,
                        inCategory = container.sourceCatalog::inCategory,
                        freeRadio = container.freeRadio,
                        lyrics = container.lyrics,
                        captions = container.captions,
                        transcriber = container.transcriber,
                        importer = container.importer,
                    )
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
    history: HistoryStore,
    playlists: PlaylistStore,
    localMusic: LocalMusicSource,
    about: () -> AboutViewModel,
    settings: () -> SettingsViewModel,
    sourceChanges: Flow<Int>,
    inCategory: (MusicSource, SourceCategory) -> Boolean,
    freeRadio: FreeRadio,
    lyrics: LrcLib,
    captions: CaptionFinder,
    transcriber: Transcriber,
    importer: PlaylistImporter,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val nowPlaying by player.nowPlaying.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSheet by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Item?>(null) }
    var addingToPlaylist by remember { mutableStateOf<List<Item>?>(null) }
    val playlistList by playlists.playlists.collectAsStateWithLifecycle(emptyList())
    val openLink = rememberLinkOpener { scope.launch { snackbar.showSnackbar("No app on this phone can open that link.") } }
    val toggleFavorite: (Item) -> Unit = { item -> scope.launch { favorites.toggle(item) } }
    // Free radio plays the chosen song at once and adds similar music from every source as it's found.
    val startFreeRadio: (Item) -> Unit = { item ->
        player.play(item)
        val generation = player.queueGeneration
        val title = "Free radio · " + (seedArtist(item) ?: item.title)
        player.markStation(generation, title, building = true)
        showSheet = true
        scope.launch {
            var added = 0
            freeRadio.stream(item) { batch ->
                player.append(generation, batch)
                added += batch.size
            }
            player.markStation(generation, title, building = false)
            if (added == 0 && generation == player.queueGeneration) {
                snackbar.showSnackbar("Couldn't find similar free music for this one.")
            }
        }
    }
    // A station's current song, found on demand on the song sources and kept in Favorites.
    val saveRadioSong: (RadioSong) -> Unit = { song ->
        scope.launch {
            snackbar.showSnackbar("Looking for “${song.title}” by ${song.artist}…", duration = SnackbarDuration.Short)
        }
        scope.launch {
            val found = try {
                registry.findRadioSong(song)
            } catch (e: SourceException) {
                null
            }
            snackbar.currentSnackbarData?.dismiss()
            if (found == null) {
                snackbar.showSnackbar("Couldn't find “${song.title}” by ${song.artist} on your sources.")
            } else {
                if (!favorites.isFavorite(found.ref).first()) favorites.toggle(found)
                val result = snackbar.showSnackbar(
                    "Saved “${found.title}” to Favorites (from ${sourceName(registry, found.ref.sourceId)}).",
                    actionLabel = "Play",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) player.play(found)
            }
        }
    }
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
    val station by player.station.collectAsStateWithLifecycle()
    val upNext by player.upNext.collectAsStateWithLifecycle()
    val npFavorite by rememberIsFavorite(favorites, npItem?.ref)
    LaunchedEffect(Unit) { player.connect() }
    // Every item that starts playing goes into History (on this phone only).
    LaunchedEffect(npItem?.ref) { npItem?.let { history.record(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                val resumeOffer by player.resumeOffer.collectAsStateWithLifecycle()
                StartOverPill(resumeOffer, onStartOver = player::startOver, onDismiss = player::dismissResumeOffer)
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
            history,
            playlists,
            localMusic,
            personal = remember { PersonalShelves(history, freeRadio::build) },
            importer = importer,
            about = about,
            settings = settings,
            sourceChanges = sourceChanges,
            inCategory = inCategory,
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
            station = station,
            upNext = upNext,
            findLyrics = findWords(npItem, np.isLive, np.durationMs, captions, lyrics),
            wordsLabel = if (runCatching { registry.get(np.ref.sourceId) }.getOrNull()?.let { inCategory(it, SourceCategory.MUSIC) } != false) "Lyrics" else "Captions",
            transcribe = transcription(npItem, np.isLive, registry, transcriber, player)?.let { start ->
                TranscribeOffer(transcriber.modelReady, transcriber.modelMegabytes, start)
            },
            extraActions = {
                if (npItem != null && canStartRadio(registry, npItem)) {
                    TextButton(onClick = { startRadio(npItem) }) { Text("Start radio") }
                }
                if (npItem != null && seedArtist(npItem) != null) {
                    TextButton(onClick = { startFreeRadio(npItem) }) { Text("Free radio") }
                }
                if (npItem != null && npItem.isPlayable) {
                    TextButton(onClick = { addingToPlaylist = listOf(npItem) }) { Text("Add to playlist") }
                }
                radioSong(np)?.let { song ->
                    TextButton(onClick = { saveRadioSong(song) }) { Text("Save this song") }
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
                if (item.isPlayable) {
                    ListItem(
                        leadingContent = { Icon(KairoifyIcons.PlaylistAdd, contentDescription = null) },
                        modifier = Modifier.clickable {
                            addingToPlaylist = listOf(item)
                            actionsFor = null
                        },
                    ) { Text("Add to playlist") }
                }
                if (seedArtist(item) != null) {
                    ListItem(
                        supportingContent = { Text("A station from every free music source") },
                        leadingContent = { Icon(KairoifyIcons.Radio, contentDescription = null) },
                        modifier = Modifier.clickable {
                            startFreeRadio(item)
                            actionsFor = null
                        },
                    ) { Text("Free radio") }
                }
            },
        )
    }
    addingToPlaylist?.let { items ->
        val done: (String) -> Unit = { name ->
            addingToPlaylist = null
            scope.launch { snackbar.showSnackbar("Added to $name") }
        }
        AddToPlaylistSheet(
            items = items,
            playlists = playlistList,
            onAdd = { id ->
                scope.launch { playlists.add(id, items) }
                done(playlistList.firstOrNull { it.id == id }?.name ?: "playlist")
            },
            onCreateAndAdd = { name ->
                scope.launch { playlists.add(playlists.create(name), items) }
                done(name.trim().ifEmpty { "New playlist" })
            },
            onDismiss = { addingToPlaylist = null },
        )
    }
}

@Composable
private fun rememberIsFavorite(favorites: FavoritesStore, ref: MediaRef?) =
    remember(ref) { ref?.let(favorites::isFavorite) ?: flowOf(false) as Flow<Boolean> }.collectAsStateWithLifecycle(false)

private fun sourceName(registry: SourceRegistry, sourceId: String): String =
    registry.sources.firstOrNull { it.info.id == sourceId }?.info?.displayName ?: sourceId
