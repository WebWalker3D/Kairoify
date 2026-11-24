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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.isPlayable
import icu.kairo.kairoify.ui.components.ItemRow

/** One playlist: Play all, Shuffle, rename, delete, and per-song move and remove. */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    sourceName: (String) -> String,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpen: (Item) -> Unit,
    onGone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading, playlist) { if (!state.loading && playlist == null) onGone() }
    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val items = playlist.entries.map { it.item }
    val playable = items.filter { it.isPlayable }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(playlist.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, onClick = { menu = false; deleting = true })
                }
            }
        }
        Text(
            if (items.size == 1) "1 song" else "${items.size} songs",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        PlayButtons(playable, onPlayQueue)
        if (items.isEmpty()) {
            Text(
                "Empty for now. Long-press any song, anywhere in Kairoify, and choose Add to playlist.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(playlist.entries, key = { _, e -> e.entryId }) { index, entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemRow(
                        entry.item,
                        onClick = {
                            val start = playable.indexOf(entry.item)
                            if (start < 0) onOpen(entry.item) else onPlayQueue(playable, start, false)
                        },
                        onLongClick = onLongPress,
                        badge = sourceName(entry.item.ref.sourceId),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < playlist.entries.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { viewModel.remove(entry.entryId) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove from playlist")
                    }
                }
            }
        }
    }
    if (renaming) {
        NameDialog("Rename playlist", playlist.name, onDismiss = { renaming = false }) { name ->
            renaming = false
            viewModel.rename(name)
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${playlist.name}”?") },
            text = { Text("The songs stay wherever else you've kept them.") },
            confirmButton = { TextButton(onClick = { deleting = false; viewModel.delete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}
