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

import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.station
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
}
