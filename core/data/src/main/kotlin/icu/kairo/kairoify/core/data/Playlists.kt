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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PlaylistSummary(val id: Long, val name: String, val size: Int, val cover: Item?)

/** One entry of a playlist: [entryId] tells two copies of the same song apart. */
data class PlaylistEntry(val entryId: Long, val item: Item)

data class Playlist(val id: Long, val name: String, val entries: List<PlaylistEntry>)

/** The person's own playlists. */
interface PlaylistStore {
    val playlists: Flow<List<PlaylistSummary>>

    fun playlist(id: Long): Flow<Playlist?>

    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    suspend fun delete(id: Long)

    suspend fun add(id: Long, items: List<Item>)

    suspend fun remove(id: Long, entryId: Long)

    /** Moves the entry at [from] to [to] (both positions in the list as shown). */
    suspend fun move(id: Long, from: Int, to: Int)
}

class RoomPlaylistStore(
    private val db: KairoifyDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : PlaylistStore {
    private val dao = db.playlists()

    override val playlists: Flow<List<PlaylistSummary>> = dao.summaries().map { rows ->
        rows.map { PlaylistSummary(it.id, it.name, it.size, it.firstItemJson?.let(ItemJson::decode)) }
    }

    override fun playlist(id: Long): Flow<Playlist?> = dao.playlist(id).combineItems(id)

    private fun Flow<PlaylistEntity?>.combineItems(id: Long): Flow<Playlist?> =
        kotlinx.coroutines.flow.combine(this, dao.items(id)) { p, rows ->
            p?.let { Playlist(it.id, it.name, rows.mapNotNull { r -> ItemJson.decode(r.itemJson)?.let { item -> PlaylistEntry(r.id, item) } }) }
        }

    override suspend fun create(name: String): Long = dao.create(name.trim().ifEmpty { "New playlist" }, now())

    override suspend fun rename(id: Long, name: String) {
        name.trim().takeIf(String::isNotEmpty)?.let { dao.rename(id, it, now()) }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun add(id: Long, items: List<Item>) = db.withTransaction {
        var position = dao.nextPosition(id)
        dao.upsertItems(items.map { PlaylistItemEntity(playlistId = id, position = position++, ref = it.ref.encode(), itemJson = ItemJson.encode(it)) })
        dao.touch(id, now())
    }

    override suspend fun remove(id: Long, entryId: Long) = db.withTransaction {
        dao.deleteItem(entryId)
        dao.touch(id, now())
    }

    override suspend fun move(id: Long, from: Int, to: Int) = db.withTransaction {
        val rows = dao.itemsNow(id).toMutableList()
        if (from !in rows.indices || to !in rows.indices || from == to) return@withTransaction
        rows.add(to, rows.removeAt(from))
        dao.upsertItems(rows.mapIndexed { i, r -> r.copy(position = i) })
        dao.touch(id, now())
    }
}
