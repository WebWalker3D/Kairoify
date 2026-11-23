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
import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.source.SourceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseUiState(val items: List<Item>, val loading: Boolean, val endReached: Boolean, val error: String?)

class BrowseViewModel(
    private val registry: SourceRegistry,
    private val sourceId: String,
    private val node: BrowseNode,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState(emptyList(), loading = false, endReached = false, error = null))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()
    private var next: PageToken? = null

    init {
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.endReached) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                val page = registry.get(sourceId).browse(node, next)
                next = page.next
                // Offset paging over a live ranking can repeat a station at a page boundary; a repeated key would crash the list.
                _state.value.copy(
                    items = (_state.value.items + page.items).distinctBy { it.ref },
                    loading = false,
                    endReached = page.next == null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = e.message ?: "Something went wrong")
            }
        }
    }
}
