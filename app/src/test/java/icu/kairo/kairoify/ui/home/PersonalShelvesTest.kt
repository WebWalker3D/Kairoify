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
package icu.kairo.kairoify.ui.home

import icu.kairo.kairoify.core.data.HistoryStore
import icu.kairo.kairoify.core.data.Played
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalShelvesTest {
    private fun t(id: String, artist: String) = Track(MediaRef("a", id), "Song $id", subtitle = artist, artists = listOf(artist))

    private class FakeHistory(val played: List<Played>) : HistoryStore {
        override fun recent(limit: Int): Flow<List<Played>> = flowOf(played.take(limit))
        override suspend fun record(item: Item) = Unit
        override suspend fun mostPlayed(limit: Int) = played.sortedByDescending { it.playCount }.take(limit)
        override suspend fun remove(ref: MediaRef) = Unit
        override suspend fun clear() = Unit
    }

    @Test
    fun `recently played, then music like the most played artist`() = runTest {
        val nina = t("1", "Nina Simone")
        val history = FakeHistory(listOf(Played(t("2", "Etta James"), 2, 1), Played(nina, 1, 5)))
        val shelves = PersonalShelves(history) { listOf(nina, t("3", "Nina Simone"), t("4", "Billie Holiday"), t("2", "Etta James")) }.shelves()
        assertEquals(listOf("Recently played", "Because you play Nina Simone"), shelves.map { it.title })
        assertEquals(listOf("4"), shelves[1].items.map { it.ref.itemId })
    }

    @Test
    fun `nothing played means no rows`() = runTest {
        assertTrue(PersonalShelves(FakeHistory(emptyList())) { error("not asked") }.shelves().isEmpty())
    }
}
