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
package icu.kairo.kairoify.ui.category

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryHomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun `shows only its own sources' shelves and browse entries`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val books = FakeSource(
                "books",
                homeShelves = listOf(Shelf("b1", "New books", listOf(station("1")))),
                facetList = listOf(Facet(BrowseNode.Collection("poetry"), "Poetry", null)),
            )
            val radio = FakeSource("radio", homeShelves = listOf(Shelf("r1", "Live", listOf(station("2")))))
            val vm = CategoryHomeViewModel(SourceRegistry(listOf(books, radio)), only = { it.info.id == "books" })
            advanceUntilIdle()
            val state = vm.state.value
            assertEquals(listOf("New books"), state.shelves.map { it.title })
            assertEquals(listOf("Poetry"), state.browse.map { it.facet.label })
            assertEquals("books", state.browse.single().sourceId)
            assertEquals(0, radio.homeCalls)
        }
    }
}
