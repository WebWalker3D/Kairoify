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
package icu.kairo.kairoify.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.source.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import icu.kairo.kairoify.core.model.BrowseNode
import java.text.Normalizer

sealed interface FacetsUiState {
    data object Loading : FacetsUiState

    data class Loaded(val facets: List<Facet>) : FacetsUiState

    data class Failed(val message: String) : FacetsUiState
}

class FacetsViewModel(
    private val registry: SourceRegistry,
    private val sourceId: String,
    private val kind: FacetKind,
) : ViewModel() {
    private val loaded = MutableStateFlow<FacetsUiState>(FacetsUiState.Loading)
    private val _filter = MutableStateFlow("")

    /** What the person typed in the filter box above the list. */
    val filter: StateFlow<String> = _filter.asStateFlow()

    /** The list, narrowed to the facets that match [filter]. */
    val state: StateFlow<FacetsUiState> = combine(loaded, _filter) { s, f ->
        if (s is FacetsUiState.Loaded) FacetsUiState.Loaded(s.facets.filter { matches(it, f) }) else s
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FacetsUiState.Loading)

    fun setFilter(value: String) {
        _filter.value = value
    }

    init {
        load()
    }

    fun load() {
        loaded.value = FacetsUiState.Loading
        viewModelScope.launch {
            loaded.value = try {
                FacetsUiState.Loaded(registry.get(sourceId).facets(kind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FacetsUiState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
}

/** Lower case with accents removed: "Côte" → "cote". */
private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Every word typed must appear in the label; a country also matches its two-letter code ("de" → Germany). */
internal fun matches(facet: Facet, filter: String): Boolean {
    val words = fold(filter).split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.isEmpty()) return true
    val label = fold(facet.label)
    val code = (facet.node as? BrowseNode.Country)?.code?.lowercase()
    return words.all { it in label } || (words.size == 1 && words[0] == code)
}
