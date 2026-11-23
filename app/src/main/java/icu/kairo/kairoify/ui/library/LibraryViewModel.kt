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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import icu.kairo.kairoify.core.data.FavoritesStore
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.isPlayable
import icu.kairo.kairoify.core.model.kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LibraryUiState(
    val loading: Boolean = true,
    /** The chosen chip; null is "All". */
    val filter: ItemKind? = null,
    /** Chips to offer: only kinds that are in the favorites. */
    val kinds: List<ItemKind> = emptyList(),
    val items: List<Item> = emptyList(),
) {
    /** What Play all and Shuffle queue: songs and stations (albums open instead). */
    val playable: List<Item> get() = items.filter { it.isPlayable }
}

/** The Favorites list: newest first, narrowed by a chip. */
class LibraryViewModel(store: FavoritesStore) : ViewModel() {
    private val filter = MutableStateFlow<ItemKind?>(null)

    val state: StateFlow<LibraryUiState> = combine(store.favorites, filter) { favorites, chosen ->
        val all = favorites.map { it.item }
        val kinds = ItemKind.entries.filter { kind -> all.any { it.kind == kind } }
        val active = chosen?.takeIf { it in kinds }
        if (active == null && chosen != null) filter.value = null
        LibraryUiState(
            loading = false,
            filter = active,
            kinds = kinds,
            items = if (active == null) all else all.filter { it.kind == active },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    fun setFilter(kind: ItemKind?) {
        filter.value = kind
    }
}
