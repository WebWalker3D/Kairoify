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
package icu.kairo.kairoify.core.data

import androidx.room.withTransaction
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemJson
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.kind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Favorite(val item: Item, val addedAtMillis: Long)

/** The person's favorites, newest first: stations, songs, anything playable. */
interface FavoritesStore {
    val favorites: Flow<List<Favorite>>

    fun isFavorite(ref: MediaRef): Flow<Boolean>

    /** Adds the item if it isn't a favorite yet, removes it if it is. Returns whether it is a favorite now. */
    suspend fun toggle(item: Item): Boolean

    suspend fun remove(ref: MediaRef)
}

class RoomFavoritesStore(
    private val db: KairoifyDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : FavoritesStore {
    private val dao = db.favorites()

    /** Rows this version can't read (written by a newer app, say) are left alone and skipped. */
    override val favorites: Flow<List<Favorite>> = dao.all().map { rows ->
        rows.mapNotNull { row -> ItemJson.decode(row.itemJson)?.let { Favorite(it, row.addedAtMillis) } }
    }

    override fun isFavorite(ref: MediaRef): Flow<Boolean> = dao.exists(ref.encode())

    override suspend fun toggle(item: Item): Boolean = db.withTransaction {
        val ref = item.ref.encode()
        if (dao.delete(ref) > 0) {
            false
        } else {
            dao.upsert(FavoriteEntity(ref, item.kind.name, now(), ItemJson.encode(item)))
            true
        }
    }

    override suspend fun remove(ref: MediaRef) {
        dao.delete(ref.encode())
    }
}
