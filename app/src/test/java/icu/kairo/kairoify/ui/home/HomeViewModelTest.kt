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

import icu.kairo.kairoify.core.model.Shelf
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
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `sections follow source order`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val a = FakeSource("a", homeShelves = listOf(Shelf("a1", "A one", listOf(station("1")))), homeDelay = 500)
            val b = FakeSource("b", homeShelves = listOf(Shelf("b1", "B one", listOf(station("2")))), homeDelay = 100)
            val vm = HomeViewModel(SourceRegistry(listOf(a, b)))
            advanceUntilIdle()
            val state = vm.state.value
            assertFalse(state.loading)
            assertEquals(listOf("a1", "b1"), state.sections.map { (it as HomeSection.Loaded).shelf.id })
            assertEquals("A", (state.sections[0] as HomeSection.Loaded).sourceName)
        }
    }

    @Test
    fun `failed source becomes Failed section and others still load`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val a = FakeSource("a", homeError = IOException("down"))
            val b = FakeSource("b", homeShelves = listOf(Shelf("b1", "B one", listOf(station("2")))))
            val vm = HomeViewModel(SourceRegistry(listOf(a, b)))
            advanceUntilIdle()
            assertEquals(
                listOf(HomeSection.Failed("A", "down"), HomeSection.Loaded("B", Shelf("b1", "B one", listOf(station("2"))))),
                vm.state.value.sections,
            )
        }
    }

    @Test
    fun `refresh reloads`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val a = FakeSource("a")
            val vm = HomeViewModel(SourceRegistry(listOf(a)))
            advanceUntilIdle()
            vm.refresh()
            advanceUntilIdle()
            assertEquals(2, a.homeCalls)
        }
    }
}
