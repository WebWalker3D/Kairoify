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
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.withMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class FacetsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val countries = listOf(
        Facet(BrowseNode.Country("DE"), "Germany", 9000),
        Facet(BrowseNode.Country("CI"), "Côte d'Ivoire", 40),
        Facet(BrowseNode.Country("US"), "The United States Of America", 7000),
    )

    private fun vm() = FacetsViewModel(SourceRegistry(listOf(FakeSource("radio", facetList = countries))), "radio", FacetKind.COUNTRY)

    private fun labels(vm: FacetsViewModel) = assertIs<FacetsUiState.Loaded>(vm.state.value).facets.map { it.label }

    @Test
    fun `an empty filter shows everything`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = vm()
            advanceUntilIdle()
            assertEquals(3, labels(vm).size)
        }
    }

    @Test
    fun `the filter ignores case and accents`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = vm()
            vm.setFilter("  COTE ")
            advanceUntilIdle()
            assertEquals(listOf("Côte d'Ivoire"), labels(vm))
        }
    }

    @Test
    fun `every word has to match`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = vm()
            vm.setFilter("united america")
            advanceUntilIdle()
            assertEquals(listOf("The United States Of America"), labels(vm))
        }
    }

    @Test
    fun `countries also match their code`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = vm()
            vm.setFilter("de")
            advanceUntilIdle()
            assertEquals(listOf("Germany"), labels(vm))
        }
    }
}
