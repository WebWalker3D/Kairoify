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
package icu.kairo.kairoify.ui.search

import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.album
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.track
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `short query stays idle`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = SearchViewModel(SourceRegistry(listOf(FakeSource("a"))))
            vm.onQueryChange("a")
            advanceTimeBy(1_000)
            assertEquals(SearchUiState.Idle, vm.state.value)
            vm.onQueryChange(" a ")
            advanceTimeBy(1_000)
            assertEquals(SearchUiState.Idle, vm.state.value)
        }
    }

    @Test
    fun `debounces typing`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val source = FakeSource("a")
            val vm = SearchViewModel(SourceRegistry(listOf(source)))
            vm.onQueryChange("ja")
            advanceTimeBy(100)
            vm.onQueryChange("jaz")
            advanceTimeBy(100)
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            assertEquals(listOf("jazz"), source.searchQueries)
            assertEquals("jazz", vm.query.value)
        }
    }

    @Test
    fun `sections start loading then fill`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val source = FakeSource("a", searchItems = listOf(station("1"), station("2")), searchDelay = 500)
            val vm = SearchViewModel(SourceRegistry(listOf(source)))
            vm.onQueryChange("jazz")
            advanceTimeBy(350)
            val loading = assertIs<SearchUiState.Results>(vm.state.value)
            assertEquals(SearchSectionState.Loading, loading.sections.single().state)
            advanceTimeBy(600)
            val loaded = assertIs<SearchUiState.Results>(vm.state.value)
            assertEquals(2, assertIs<SearchSectionState.Loaded>(loaded.sections.single().state).items.size)
        }
    }

    @Test
    fun `failed source shows Failed section`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = SearchViewModel(SourceRegistry(listOf(FakeSource("a", searchError = IOException("offline")))))
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            val results = assertIs<SearchUiState.Results>(vm.state.value)
            assertEquals(SearchSectionState.Failed("offline"), results.sections.single().state)
        }
    }

    @Test
    fun `song sections come before station sections`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val radio = FakeSource("radio", searchItems = listOf(station("1")))
            val songs = FakeSource("songs", searchItems = listOf(track("1")), searchKind = ItemKind.SONG)
            val vm = SearchViewModel(SourceRegistry(listOf(radio, songs)))
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            val results = assertIs<SearchUiState.Results>(vm.state.value)
            assertEquals(listOf("songs", "radio"), results.sections.map { it.source.id })
            assertEquals(listOf(ItemKind.SONG, ItemKind.STATION), results.sections.map { it.kind })
        }
    }

    @Test
    fun `filter shows only that kind`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val radio = FakeSource("radio", searchItems = listOf(station("1")))
            val songs = FakeSource("songs", searchItems = listOf(track("1")), searchKind = ItemKind.SONG)
            val vm = SearchViewModel(SourceRegistry(listOf(radio, songs)))
            vm.onQueryChange("jazz")
            vm.setFilter(ItemKind.STATION)
            advanceTimeBy(1_000)
            val results = assertIs<SearchUiState.Results>(vm.state.value)
            assertEquals(ItemKind.STATION, results.filter)
            assertEquals(listOf("radio"), results.sections.map { it.source.id })
            vm.setFilter(null)
            advanceTimeBy(10)
            assertEquals(2, assertIs<SearchUiState.Results>(vm.state.value).sections.size)
        }
    }

    @Test
    fun `retry re-runs only the failed source`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val a = FakeSource("a", searchError = IOException("offline"))
            val b = FakeSource("b", searchItems = listOf(station("2")))
            val vm = SearchViewModel(SourceRegistry(listOf(a, b)))
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            a.searchError = null
            a.searchItems = listOf(station("1"))
            vm.retry("a")
            advanceTimeBy(1_000)
            val results = assertIs<SearchUiState.Results>(vm.state.value)
            val section = results.sections.first { it.source.id == "a" }
            assertEquals(1, assertIs<SearchSectionState.Loaded>(section.state).items.size)
            assertEquals(listOf("jazz", "jazz"), a.searchQueries)
            assertEquals(listOf("jazz"), b.searchQueries)
        }
    }

    @Test
    fun `all view shows four per source`() {
        val items = (1..10).map { track("$it") }
        assertEquals(ALL_VIEW_LIMIT, visibleItems(items, null).size)
        assertEquals(10, visibleItems(items, ItemKind.SONG).size)
    }

    @Test
    fun `duplicate results show once`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val songs = FakeSource("songs", searchItems = listOf(track("1"), track("1"), track("2")), searchKind = ItemKind.SONG)
            val vm = SearchViewModel(SourceRegistry(listOf(songs)))
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            val section = assertIs<SearchUiState.Results>(vm.state.value).sections.single()
            assertEquals(listOf("1", "2"), assertIs<SearchSectionState.Loaded>(section.state).items.map { it.ref.itemId })
        }
    }

    @Test
    fun `albums come between songs and stations`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val radio = FakeSource("radio", searchItems = listOf(station("1")))
            val archive = FakeSource("archive", searchItems = listOf(album("x")), searchKind = ItemKind.ALBUM)
            val songs = FakeSource("songs", searchItems = listOf(track("1")), searchKind = ItemKind.SONG)
            val vm = SearchViewModel(SourceRegistry(listOf(radio, archive, songs)))
            vm.onQueryChange("jazz")
            advanceTimeBy(1_000)
            assertEquals(listOf("songs", "archive", "radio"), assertIs<SearchUiState.Results>(vm.state.value).sections.map { it.source.id })
        }
    }
}
