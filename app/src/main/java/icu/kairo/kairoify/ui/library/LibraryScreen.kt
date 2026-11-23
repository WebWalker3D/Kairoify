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

/** The Library tab: every favorite in one list, with Play all and Shuffle. */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    sourceName: (String) -> String,
    onPlayQueue: (items: List<Item>, startIndex: Int, shuffle: Boolean) -> Unit,
    onLongPress: (Item) -> Unit,
    onOpen: (Item) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text("Favorites", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
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
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onPlayQueue(state.playable, 0, false) }, enabled = state.playable.isNotEmpty()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                    OutlinedButton(onClick = { onPlayQueue(state.playable, 0, true) }, enabled = state.playable.isNotEmpty()) {
                        Icon(KairoifyIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
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
