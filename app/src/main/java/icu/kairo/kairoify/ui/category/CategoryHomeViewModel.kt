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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One thing to browse on a category page, e.g. "Poetry" in Audiobooks & poetry. */
data class BrowseEntry(val sourceId: String, val facet: Facet)

data class CategoryHomeState(val loading: Boolean = true, val shelves: List<Shelf> = emptyList(), val browse: List<BrowseEntry> = emptyList())

/** What a category page shows before anything is typed: its sources' shelves and the collections or genres to browse. */
class CategoryHomeViewModel(private val registry: SourceRegistry, private val only: (MusicSource) -> Boolean) : ViewModel() {
    private val _state = MutableStateFlow(CategoryHomeState())
    val state: StateFlow<CategoryHomeState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val browse = registry.enabledSources().filter { Capability.BROWSE in it.capabilities && only(it) }
                .map { source -> async { facetsOf(source) } }
            val shelves = mutableMapOf<String, List<Shelf>>()
            registry.home(only).collect { result ->
                if (result is SourceResult.Ok) shelves[result.source.id] = result.value
                _state.update { it.copy(shelves = inSourceOrder(shelves)) }
            }
            _state.update { it.copy(loading = false, browse = browse.awaitAll().flatten()) }
        }
    }

    private suspend fun facetsOf(source: MusicSource): List<BrowseEntry> = try {
        source.facets(FacetKind.COLLECTION).map { BrowseEntry(source.info.id, it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private fun inSourceOrder(bySource: Map<String, List<Shelf>>): List<Shelf> =
        registry.sources.flatMap { bySource[it.info.id].orEmpty() }
}
