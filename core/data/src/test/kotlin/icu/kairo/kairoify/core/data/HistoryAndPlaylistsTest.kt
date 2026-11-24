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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class HistoryAndPlaylistsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KairoifyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var clock = 1L
    private val history = RoomHistoryStore(db, { clock }, keep = 3)
    private val playlists = RoomPlaylistStore(db) { clock }

    private fun song(id: String) = Track(MediaRef("audius", id), "Song $id")

    @After
    fun close() = db.close()

    @Test
    fun `replaying moves a song to the top and counts it`() = runTest {
        history.record(song("a"))
        clock = 2
        history.record(song("b"))
        clock = 3
        history.record(song("a"))
        val recent = history.recent().first()
        assertEquals(listOf("a", "b"), recent.map { it.item.ref.itemId })
        assertEquals(2, recent.first().playCount)
        assertEquals("a", history.mostPlayed(1).single().item.ref.itemId)
    }

    @Test
    fun `history keeps only the newest entries`() = runTest {
        listOf("a", "b", "c", "d").forEach { clock++; history.record(song(it)) }
        assertEquals(listOf("d", "c", "b"), history.recent().first().map { it.item.ref.itemId })
        history.clear()
        assertTrue(history.recent().first().isEmpty())
    }

    @Test
    fun `playlists keep order, duplicates, moves and removals`() = runTest {
        val id = playlists.create("  Road trip ")
        playlists.add(id, listOf(song("a"), song("b"), song("a")))
        playlists.move(id, 2, 0)
        var p = playlists.playlist(id).first()!!
        assertEquals("Road trip", p.name)
        assertEquals(listOf("a", "a", "b"), p.entries.map { it.item.ref.itemId })
        playlists.remove(id, p.entries[1].entryId)
        p = playlists.playlist(id).first()!!
        assertEquals(listOf("a", "b"), p.entries.map { it.item.ref.itemId })
        val summary = playlists.playlists.first().single()
        assertEquals(2, summary.size)
        assertEquals("a", summary.cover?.ref?.itemId)
    }

    @Test
    fun `deleting a playlist removes its songs`() = runTest {
        val id = playlists.create("")
        assertEquals("New playlist", playlists.playlist(id).first()!!.name)
        playlists.add(id, listOf(song("a")))
        playlists.delete(id)
        assertNull(playlists.playlist(id).first())
        assertTrue(db.playlists().itemsNow(id).isEmpty())
    }
}
