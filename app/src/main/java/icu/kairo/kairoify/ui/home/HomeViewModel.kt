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
package icu.kairo.kairoify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeSection {
    data class Loaded(val sourceName: String, val shelf: Shelf) : HomeSection

    data class Failed(val sourceName: String, val message: String) : HomeSection
}

data class HomeUiState(val loading: Boolean, val sections: List<HomeSection>)

/** @param sourceChanges ticks whenever a source is switched on or off, or gets a key; home reloads then. */
class HomeViewModel(private val registry: SourceRegistry, sourceChanges: Flow<Int> = emptyFlow()) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(loading = true, sections = emptyList()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
        viewModelScope.launch { sourceChanges.drop(1).collect { refresh() } }
    }

    fun refresh() {
        job?.cancel()
        _state.value = _state.value.copy(loading = true)
        job = viewModelScope.launch {
            val bySource = mutableMapOf<String, List<HomeSection>>()
            registry.home().collect { result ->
                bySource[result.source.id] = when (result) {
                    is SourceResult.Ok -> result.value.map { HomeSection.Loaded(result.source.displayName, it) }
                    is SourceResult.Failed -> listOf(
                        HomeSection.Failed(result.source.displayName, result.error.message ?: "Something went wrong"),
                    )
                }
                _state.value = HomeUiState(loading = true, sections = inSourceOrder(bySource))
            }
            _state.value = HomeUiState(loading = false, sections = inSourceOrder(bySource))
        }
    }

    private fun inSourceOrder(bySource: Map<String, List<HomeSection>>): List<HomeSection> =
        registry.sources.flatMap { bySource[it.info.id].orEmpty() }
}
