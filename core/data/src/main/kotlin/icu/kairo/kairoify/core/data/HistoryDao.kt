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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY playedAtMillis DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY playCount DESC, playedAtMillis DESC LIMIT :limit")
    suspend fun mostPlayed(limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE ref = :ref")
    suspend fun get(ref: String): HistoryEntity?

    @Upsert
    suspend fun upsert(entity: HistoryEntity)

    @Query("DELETE FROM history WHERE ref = :ref")
    suspend fun delete(ref: String)

    @Query("DELETE FROM history")
    suspend fun clear()

    /** Keeps the newest [keep] rows. */
    @Query("DELETE FROM history WHERE ref NOT IN (SELECT ref FROM history ORDER BY playedAtMillis DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface PlaylistDao {
    @Query(
        "SELECT p.id, p.name, p.updatedAtMillis, COUNT(i.id) AS size, " +
            "(SELECT itemJson FROM playlist_items WHERE playlistId = p.id ORDER BY position LIMIT 1) AS firstItemJson " +
            "FROM playlists p LEFT JOIN playlist_items i ON i.playlistId = p.id GROUP BY p.id ORDER BY p.updatedAtMillis DESC",
    )
    fun summaries(): Flow<List<PlaylistRow>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    fun items(id: Long): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY position")
    suspend fun itemsNow(id: Long): List<PlaylistItemEntity>

    @Query("INSERT INTO playlists (name, createdAtMillis, updatedAtMillis) VALUES (:name, :now, :now)")
    suspend fun create(name: String, now: Long): Long

    @Query("UPDATE playlists SET name = :name, updatedAtMillis = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAtMillis = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_items WHERE playlistId = :id")
    suspend fun nextPosition(id: Long): Int

    @Upsert
    suspend fun upsertItem(item: PlaylistItemEntity)

    @Upsert
    suspend fun upsertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE id = :entryId")
    suspend fun deleteItem(entryId: Long)
}

data class PlaylistRow(val id: Long, val name: String, val updatedAtMillis: Long, val size: Int, val firstItemJson: String?)
