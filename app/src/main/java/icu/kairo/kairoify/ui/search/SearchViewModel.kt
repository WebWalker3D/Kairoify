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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SearchSectionState {
    data object Loading : SearchSectionState

    data class Loaded(val items: List<Item>) : SearchSectionState

    data class Failed(val message: String) : SearchSectionState
}

/** One source's answer, filed under the kind of thing it returns (songs or stations). */
data class SearchSection(val source: SourceInfo, val kind: ItemKind, val state: SearchSectionState)

sealed interface SearchUiState {
    data object Idle : SearchUiState

    /** [filter] null is "All"; [sections] are already narrowed to it, songs first. */
    data class Results(val query: String, val filter: ItemKind?, val sections: List<SearchSection>) : SearchUiState
}

/** How many results each source shows under "All" before "Show all". */
const val ALL_VIEW_LIMIT = 4

fun visibleItems(items: List<Item>, filter: ItemKind?): List<Item> = if (filter == null) items.take(ALL_VIEW_LIMIT) else items

/** Searches every source at once; each source's section fills in as it answers and can be retried on its own. */
@OptIn(FlowPreview::class)
class SearchViewModel(private val registry: SourceRegistry) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val filter = MutableStateFlow<ItemKind?>(null)

    /** The query being shown and its sections; null while idle. */
    private val results = MutableStateFlow<Pair<String, List<SearchSection>>?>(null)
    private val retries = mutableMapOf<String, Job>()

    val state: StateFlow<SearchUiState> = combine(results, filter) { current, kind ->
        if (current == null) {
            SearchUiState.Idle
        } else {
            SearchUiState.Results(current.first, kind, current.second.filter { kind == null || it.kind == kind })
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState.Idle)

    init {
        viewModelScope.launch {
            _query.debounce(DEBOUNCE_MILLIS).map { it.trim() }.distinctUntilChanged().collectLatest { q ->
                retries.values.forEach { it.cancel() }
                retries.clear()
                if (q.length < MIN_QUERY) {
                    results.value = null
                    return@collectLatest
                }
                results.value = q to initialSections()
                registry.search(q).collect { record(q, it) }
            }
        }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun setFilter(kind: ItemKind?) {
        filter.value = kind
    }

    /** Asks one source again for the current query, leaving every other section as it is. */
    fun retry(sourceId: String) {
        val q = results.value?.first ?: return
        setState(q, sourceId, SearchSectionState.Loading)
        retries.remove(sourceId)?.cancel()
        retries[sourceId] = viewModelScope.launch { record(q, registry.searchSource(sourceId, q)) }
    }

    private fun initialSections(): List<SearchSection> =
        registry.sources
            .filter { Capability.SEARCH in it.capabilities }
            .map { SearchSection(it.info, it.searchKind, SearchSectionState.Loading) }
            .sortedBy { KIND_ORDER.indexOf(it.kind) }

    private fun record(q: String, result: SourceResult<Page<Item>>) {
        val state = when (result) {
            // A source can list the same song twice; each appears once (lists are keyed by it).
            is SourceResult.Ok -> SearchSectionState.Loaded(result.value.items.distinctBy { it.ref })
            is SourceResult.Failed -> SearchSectionState.Failed(result.error.message ?: "Something went wrong")
        }
        setState(q, result.source.id, state)
    }

    private fun setState(q: String, sourceId: String, state: SearchSectionState) {
        results.update { current ->
            if (current == null || current.first != q) {
                current
            } else {
                q to current.second.map { if (it.source.id == sourceId) it.copy(state = state) else it }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY = 2
        val KIND_ORDER = listOf(ItemKind.SONG, ItemKind.STATION)
    }
}
