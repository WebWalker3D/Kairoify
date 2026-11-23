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
package icu.kairo.kairoify.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.ui.components.ItemRow
import icu.kairo.kairoify.ui.components.SectionError
import icu.kairo.kairoify.ui.components.label

private val CHIPS = listOf(null, ItemKind.SONG, ItemKind.ALBUM, ItemKind.STATION)

@Composable
fun SearchScreen(viewModel: SearchViewModel, onPlay: (Item) -> Unit, onLongPress: (Item) -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Songs, artists, stations…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when (val s = state) {
            SearchUiState.Idle -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
                Text("Search songs and stations from every free source.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is SearchUiState.Results -> {
                // Scrolls sideways: four chips don't fit on narrow phones.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CHIPS.forEach { kind ->
                        FilterChip(
                            selected = s.filter == kind,
                            onClick = { viewModel.setFilter(kind) },
                            label = { Text(kind?.label ?: "All") },
                        )
                    }
                }
                Results(s, onPlay, onLongPress, onRetry = viewModel::retry, onShowAll = viewModel::setFilter)
            }
        }
    }
}

@Composable
private fun Results(
    results: SearchUiState.Results,
    onPlay: (Item) -> Unit,
    onLongPress: (Item) -> Unit,
    onRetry: (String) -> Unit,
    onShowAll: (ItemKind) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        results.sections.groupBy { it.kind }.forEach { (kind, sections) ->
            item(key = "kind-$kind") {
                Text(kind.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
            }
            sections.forEach { section ->
                val id = section.source.id
                item(key = "header-$id") {
                    Text(
                        section.source.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                when (val st = section.state) {
                    SearchSectionState.Loading -> item(key = "loading-$id") {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    }
                    is SearchSectionState.Failed -> item(key = "failed-$id") { SectionError(st.message, onRetry = { onRetry(id) }) }
                    is SearchSectionState.Loaded -> if (st.items.isEmpty()) {
                        item(key = "empty-$id") {
                            Text(
                                "No results for “${results.query}”.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    } else {
                        items(visibleItems(st.items, results.filter), key = { "$id-${it.ref.encode()}" }) {
                            ItemRow(it, onPlay, onLongClick = onLongPress)
                        }
                        if (results.filter == null && st.items.size > ALL_VIEW_LIMIT) {
                            item(key = "more-$id") {
                                TextButton(onClick = { onShowAll(kind) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text("Show all ${kind.label.lowercase()}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
