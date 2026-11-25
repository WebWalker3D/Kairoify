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
package icu.kairo.kairoify.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import icu.kairo.kairoify.local.LocalMusicSource
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import icu.kairo.kairoify.core.model.isPlayable
import icu.kairo.kairoify.ui.components.Artwork
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.ui.components.ItemRow
import icu.kairo.kairoify.ui.components.label
import icu.kairo.kairoify.ui.icons.KairoifyIcons

/** The Library tab: Favorites, Playlists and History. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playlists: PlaylistsViewModel,
    history: HistoryViewModel,
    localMusic: LocalMusicSource,
    sourceName: (String) -> String,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpen: (Item) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Text("Library", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 16.dp) {
            listOf("Favorites", "Playlists", "History", "On this phone").forEachIndexed { i, label ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            0 -> FavoritesTab(viewModel, sourceName, onPlayQueue, onLongPress, onOpen)
            1 -> PlaylistsTab(playlists, onOpenPlaylist)
            2 -> HistoryTab(history, sourceName, onPlayQueue, onLongPress, onOpen)
            else -> OnThisPhoneTab(localMusic, onPlayQueue, onLongPress)
        }
    }
}

@Composable
private fun FavoritesTab(
    viewModel: LibraryViewModel,
    sourceName: (String) -> String,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.kinds.isEmpty() -> Text(
                "Nothing here yet. Tap ♡ on anything that's playing, or long-press a station or song, to keep it here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            else -> {
                // Scrolls sideways: four chips don't fit on narrow phones.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.filter == null, onClick = { viewModel.setFilter(null) }, label = { Text("All") })
                    state.kinds.forEach { kind ->
                        FilterChip(selected = state.filter == kind, onClick = { viewModel.setFilter(kind) }, label = { Text(kind.label) })
                    }
                }
                PlayButtons(state.playable, onPlayQueue)
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.items, key = { _, item -> item.ref.encode() }) { _, item ->
                        ItemRow(
                            item,
                            onClick = {
                                val start = state.playable.indexOf(item)
                                if (start < 0) onOpen(item) else onPlayQueue(state.playable, start, false)
                            },
                            onLongClick = onLongPress,
                            badge = sourceName(item.ref.sourceId),
                        )
                    }
                }
            }
        }
    }
}

/** Play all and Shuffle for a list. */
@Composable
internal fun PlayButtons(
    playable: List<Item>,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { onPlayQueue(playable, 0, false) }, enabled = playable.isNotEmpty()) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play all")
        }
        OutlinedButton(onClick = { onPlayQueue(playable, 0, true) }, enabled = playable.isNotEmpty()) {
            Icon(KairoifyIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
        trailing()
    }
}

@Composable
private fun PlaylistsTab(viewModel: PlaylistsViewModel, onOpenPlaylist: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var naming by rememberSaveable { mutableStateOf(false) }
    var importing by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "new") {
            ListItem(
                leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                modifier = Modifier.clickable { naming = true },
                headlineContent = { Text("New playlist") },
            )
        }
        if (viewModel.canImport) {
            item(key = "import") {
                ListItem(
                    leadingContent = { Icon(KairoifyIcons.PlaylistAdd, contentDescription = null) },
                    modifier = Modifier.clickable { importing = true },
                    headlineContent = { Text("Import a playlist") },
                    supportingContent = { Text("From a YouTube, YouTube Music, SoundCloud or Bandcamp link, or a pasted song list") },
                )
            }
        }
        if (!state.loading && state.playlists.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Make a playlist here, or long-press any song and choose Add to playlist.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.playlists, key = { it.id }) { p ->
            ListItem(
                leadingContent = { Artwork(p.cover?.artworkUrl, p.name, 48.dp) },
                headlineContent = { Text(p.name) },
                supportingContent = { Text(if (p.size == 1) "1 song" else "${p.size} songs") },
                modifier = Modifier.clickable { onOpenPlaylist(p.id) },
            )
        }
    }
    if (naming) {
        NameDialog(title = "New playlist", initial = "", onDismiss = { naming = false }) { name ->
            naming = false
            viewModel.create(name, onOpenPlaylist)
        }
    }
    if (importing) {
        ImportDialog(viewModel, onClose = { importing = false; viewModel.clearImport() }, onOpenPlaylist = { id ->
            importing = false
            viewModel.clearImport()
            onOpenPlaylist(id)
        })
    }
}

@Composable
private fun HistoryTab(
    viewModel: HistoryViewModel,
    sourceName: (String) -> String,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = state.played.map { it.item }
    val playable = items.filter { it.isPlayable }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Text(
                "Nothing played yet. Everything you play is listed here, newest first. It stays on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            else -> {
                PlayButtons(playable, onPlayQueue) { TextButton(onClick = { confirm = true }) { Text("Clear") } }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.played, key = { it.item.ref.encode() }) { played ->
                        val item = played.item
                        ItemRow(
                            item,
                            onClick = {
                                val start = playable.indexOf(item)
                                if (start < 0) onOpen(item) else onPlayQueue(playable, start, false)
                            },
                            onLongClick = onLongPress,
                            badge = sourceName(item.ref.sourceId) + if (played.playCount > 1) " · ${played.playCount} plays" else "",
                        )
                    }
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Clear history?") },
            text = { Text("This forgets everything you've played. Favorites and playlists stay.") },
            confirmButton = { TextButton(onClick = { confirm = false; viewModel.clear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

/** Asks for a playlist name. */
@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
