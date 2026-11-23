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
package icu.kairo.kairoify.ui.explore

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val pages = mapOf(
        null to Page(listOf(station("1"), station("2")), PageToken("2")),
        "2" to Page(listOf(station("3")), null),
    )

    @Test
    fun `loads first page then appends next page`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = BrowseViewModel(SourceRegistry(listOf(FakeSource("fake", browsePages = pages))), "fake", BrowseNode.Country("JP"))
            advanceUntilIdle()
            assertEquals(2, vm.state.value.items.size)
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("1", "2", "3"), vm.state.value.items.map { it.ref.itemId })
            assertTrue(vm.state.value.endReached)
        }
    }

    @Test
    fun `stops at end`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val source = FakeSource("fake", browsePages = pages)
            val vm = BrowseViewModel(SourceRegistry(listOf(source)), "fake", BrowseNode.Country("JP"))
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(2, source.browseCalls)
        }
    }

    @Test
    fun `error keeps items and sets error`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = BrowseViewModel(
                SourceRegistry(listOf(FakeSource("fake", browsePages = pages, browseErrorOn = "2"))),
                "fake",
                BrowseNode.Country("JP"),
            )
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(2, vm.state.value.items.size)
            assertEquals("nope", vm.state.value.error)
        }
    }

    @Test
    fun `duplicate station across pages is shown once`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val overlapping = mapOf(
                null to Page(listOf(station("1"), station("2")), PageToken("2")),
                "2" to Page(listOf(station("2"), station("3")), null),
            )
            val vm = BrowseViewModel(SourceRegistry(listOf(FakeSource("fake", browsePages = overlapping))), "fake", BrowseNode.Country("JP"))
            advanceUntilIdle()
            vm.loadMore()
            advanceUntilIdle()
            assertEquals(listOf("1", "2", "3"), vm.state.value.items.map { it.ref.itemId })
        }
    }
}
