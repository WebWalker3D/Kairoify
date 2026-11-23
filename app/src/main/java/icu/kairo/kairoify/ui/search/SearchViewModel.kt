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
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface SearchSectionState {
    data object Loading : SearchSectionState

    data class Loaded(val items: List<Item>) : SearchSectionState

    data class Failed(val message: String) : SearchSectionState
}

data class SearchSection(val source: SourceInfo, val state: SearchSectionState)

sealed interface SearchUiState {
    data object Idle : SearchUiState

    data class Results(val query: String, val sections: List<SearchSection>) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val registry: SourceRegistry) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchUiState> = _query
        .debounce(DEBOUNCE_MILLIS)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> if (q.length < MIN_QUERY) flowOf(SearchUiState.Idle) else results(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchUiState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private fun results(query: String) = flow<SearchUiState> {
        val sections = registry.sources
            .filter { Capability.SEARCH in it.capabilities }
            .map { SearchSection(it.info, SearchSectionState.Loading) }
            .toMutableList()
        emit(SearchUiState.Results(query, sections.toList()))
        registry.search(query).collect { result ->
            val index = sections.indexOfFirst { it.source.id == result.source.id }
            if (index < 0) return@collect
            sections[index] = SearchSection(
                result.source,
                when (result) {
                    is SourceResult.Ok -> SearchSectionState.Loaded(result.value.items)
                    is SourceResult.Failed -> SearchSectionState.Failed(result.error.message ?: "Something went wrong")
                },
            )
            emit(SearchUiState.Results(query, sections.toList()))
        }
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 300L
        const val MIN_QUERY = 2
    }
}
