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
package icu.kairo.kairoify.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.settings.SourceCategory
import icu.kairo.kairoify.ui.components.ShelfRow
import icu.kairo.kairoify.ui.search.SearchUiState
import icu.kairo.kairoify.ui.search.SearchViewModel
import icu.kairo.kairoify.ui.search.sourceRows

/**
 * One section of Kairoify (Music, Radio, Audiobooks & poetry…): its own search over its own sources, results as rows
 * of cover art, and, before anything is typed, its shelves and the collections or genres to browse.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryScreen(
    category: SourceCategory,
    search: SearchViewModel,
    home: CategoryHomeViewModel,
    onPlay: (Item) -> Unit,
    onLongPress: (Item) -> Unit,
    onBrowse: (BrowseEntry) -> Unit,
    onOpenFacets: (FacetKind) -> Unit,
) {
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.state.collectAsStateWithLifecycle()
    val browse by home.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "title") {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text(category.label, style = MaterialTheme.typography.headlineMedium)
                Text(category.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = search::onQueryChange,
                placeholder = { Text(category.searchHint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { search.onQueryChange("") }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") } }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        when (val r = results) {
            is SearchUiState.Results -> sourceRows(r, onPlay, onLongPress, onRetry = search::retry)
            SearchUiState.Idle -> {
                val chips = buildList {
                    if (category == SourceCategory.RADIO) {
                        add("Countries" to { onOpenFacets(FacetKind.COUNTRY) })
                        add("Genres" to { onOpenFacets(FacetKind.GENRE) })
                    }
                    browse.browse.forEach { entry -> add(entry.facet.label to { onBrowse(entry) }) }
                }
                if (chips.isNotEmpty()) {
                    item(key = "browse") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text("Browse", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                chips.forEach { (label, action) ->
                                    if (category == SourceCategory.RADIO && (label == "Countries" || label == "Genres")) {
                                        AssistChip(onClick = action, label = { Text(label) })
                                    } else {
                                        SuggestionChip(onClick = action, label = { Text(label) })
                                    }
                                }
                            }
                        }
                    }
                }
                browse.shelves.forEach { shelf ->
                    item(key = "shelf-${shelf.id}") { ShelfRow(shelf.title, shelf.items, onItemClick = onPlay, onItemLongClick = onLongPress) }
                }
                if (browse.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp)) }
            }
        }
    }
}
