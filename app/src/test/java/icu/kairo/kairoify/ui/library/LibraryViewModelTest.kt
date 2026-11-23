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

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.testing.InMemoryFavorites
import icu.kairo.kairoify.testing.album
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.track
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `shows favorites newest first`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = LibraryViewModel(InMemoryFavorites(listOf(station("a"), track("1"), station("b"))))
            advanceUntilIdle()
            assertEquals(listOf<Item>(station("b"), track("1"), station("a")), vm.state.value.items)
        }
    }

    @Test
    fun `chips list only the kinds present`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val store = InMemoryFavorites(listOf(track("1")))
            val vm = LibraryViewModel(store)
            advanceUntilIdle()
            assertEquals(listOf(ItemKind.SONG), vm.state.value.kinds)
            store.toggle(station("a"))
            advanceUntilIdle()
            assertEquals(listOf(ItemKind.STATION, ItemKind.SONG), vm.state.value.kinds)
        }
    }

    @Test
    fun `filter narrows the list`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = LibraryViewModel(InMemoryFavorites(listOf(station("a"), track("1"), station("b"))))
            vm.setFilter(ItemKind.STATION)
            advanceUntilIdle()
            assertEquals(ItemKind.STATION, vm.state.value.filter)
            assertEquals(listOf<Item>(station("b"), station("a")), vm.state.value.items)
        }
    }

    @Test
    fun `filter resets when its kind disappears`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val store = InMemoryFavorites(listOf(station("a"), track("1")))
            val vm = LibraryViewModel(store)
            vm.setFilter(ItemKind.SONG)
            advanceUntilIdle()
            store.remove(track("1").ref)
            advanceUntilIdle()
            assertNull(vm.state.value.filter)
            assertEquals(listOf<Item>(station("a")), vm.state.value.items)
        }
    }

    @Test
    fun `stops loading after the first emission`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = LibraryViewModel(InMemoryFavorites())
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertEquals(emptyList(), vm.state.value.items)
        }
    }

    @Test
    fun `albums are listed but only songs and stations play`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = LibraryViewModel(InMemoryFavorites(listOf(station("a"), album("x"), track("1"))))
            advanceUntilIdle()
            assertEquals(listOf(ItemKind.STATION, ItemKind.SONG, ItemKind.ALBUM), vm.state.value.kinds)
            assertEquals(listOf<Item>(track("1"), station("a")), vm.state.value.playable)
            vm.setFilter(ItemKind.ALBUM)
            advanceUntilIdle()
            assertEquals(emptyList(), vm.state.value.playable)
        }
    }
}
