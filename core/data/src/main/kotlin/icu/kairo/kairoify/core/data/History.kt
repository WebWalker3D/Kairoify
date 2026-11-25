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

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemJson
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.kind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Played(val item: Item, val playedAtMillis: Long, val playCount: Int)

/** What the person has played, newest first. Kept on the phone only. */
interface HistoryStore {
    fun recent(limit: Int = 200): Flow<List<Played>>

    /** Counts a play of [item] and moves it to the top. */
    suspend fun record(item: Item)

    suspend fun mostPlayed(limit: Int): List<Played>

    suspend fun remove(ref: MediaRef)

    suspend fun clear()
}

class RoomHistoryStore(
    db: KairoifyDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val keep: Int = 1_000,
) : HistoryStore {
    private val dao = db.history()

    override fun recent(limit: Int): Flow<List<Played>> = dao.recent(limit).map { rows -> rows.mapNotNull(::played) }

    override suspend fun record(item: Item) {
        val ref = item.ref.encode()
        val count = (dao.get(ref)?.playCount ?: 0) + 1
        dao.upsert(HistoryEntity(ref, item.kind.name, now(), count, ItemJson.encode(item)))
        dao.trim(keep)
    }

    override suspend fun mostPlayed(limit: Int): List<Played> = dao.mostPlayed(limit).mapNotNull(::played)

    override suspend fun remove(ref: MediaRef) = dao.delete(ref.encode())

    override suspend fun clear() = dao.clear()

    private fun played(row: HistoryEntity): Played? = ItemJson.decode(row.itemJson)?.let { Played(it, row.playedAtMillis, row.playCount) }
}
