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
package icu.kairo.kairoify.testing

import icu.kairo.kairoify.core.data.Favorite
import icu.kairo.kairoify.core.data.FavoritesStore
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Favorites held in memory, newest first. */
class InMemoryFavorites(initial: List<Item> = emptyList()) : FavoritesStore {
    private var clock = 0L
    private val state = MutableStateFlow(initial.map { Favorite(it, ++clock) }.reversed())

    override val favorites: Flow<List<Favorite>> = state

    override fun isFavorite(ref: MediaRef): Flow<Boolean> = state.map { list -> list.any { it.item.ref == ref } }

    override suspend fun toggle(item: Item): Boolean {
        val present = state.value.any { it.item.ref == item.ref }
        state.value = if (present) state.value.filterNot { it.item.ref == item.ref } else listOf(Favorite(item, ++clock)) + state.value
        return !present
    }

    override suspend fun remove(ref: MediaRef) {
        state.value = state.value.filterNot { it.item.ref == ref }
    }
}
