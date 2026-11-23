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
    private val _state = MutableStateFlow<FacetsUiState>(FacetsUiState.Loading)
    val state: StateFlow<FacetsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = FacetsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                FacetsUiState.Loaded(registry.get(sourceId).facets(kind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FacetsUiState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
}
