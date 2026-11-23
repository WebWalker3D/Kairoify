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
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RoomFavoritesStoreTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), KairoifyDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private var clock = 1L
    private val store = RoomFavoritesStore(db) { clock }

    private val song = Track(
        ref = MediaRef("youtube", "Jb6gcoR266U"), title = "Around the World", subtitle = "Daft Punk",
        artists = listOf("Daft Punk"), album = "Homework", durationMillis = 430_000,
        pageUrl = "https://music.youtube.com/watch?v=Jb6gcoR266U", isHls = false,
    )
    private val station = Station(MediaRef("radiobrowser", "u1"), "Jazz FM", streamUrl = "http://jazz/stream", isHls = true)
    private val other = Track(MediaRef("soundcloud", "a/b"), "Other")

    @After
    fun close() = db.close()

    private suspend fun items(): List<Item> = store.favorites.first().map { it.item }

    @Test
    fun `toggle adds then removes`() = runTest {
        assertTrue(store.toggle(song))
        assertEquals(listOf<Item>(song), items())
        assertFalse(store.toggle(song))
        assertEquals(emptyList(), items())
    }

    @Test
    fun `newest first`() = runTest {
        listOf(song, station, other).forEach {
            store.toggle(it)
            clock++
        }
        assertEquals(listOf(other, station, song), items())
    }

    @Test
    fun `isFavorite follows toggles`() = runTest {
        assertFalse(store.isFavorite(song.ref).first())
        store.toggle(song)
        assertTrue(store.isFavorite(song.ref).first())
        store.toggle(song)
        assertFalse(store.isFavorite(song.ref).first())
    }

    @Test
    fun `stores the whole item`() = runTest {
        store.toggle(song)
        store.toggle(station)
        assertEquals(setOf<Item>(song, station), items().toSet())
    }

    @Test
    fun `unreadable rows are skipped`() = runTest {
        db.favorites().upsert(FavoriteEntity(ref = "x:1", kind = "SONG", addedAtMillis = 5, itemJson = "{}"))
        store.toggle(song)
        assertEquals(listOf<Item>(song), items())
    }

    @Test
    fun `remove deletes by ref`() = runTest {
        store.toggle(song)
        store.toggle(other)
        store.remove(song.ref)
        assertEquals(listOf<Item>(other), items())
    }
}
