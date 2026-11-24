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
package icu.kairo.kairoify.settings

import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.withMain
import icu.kairo.kairoify.ui.settings.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = SourceSettings(RuntimeEnvironment.getApplication().getSharedPreferences("vm", 0))
    private val catalog = SourceCatalog(
        listOf(
            SourceEntry(FakeSource("radio"), defaultOn = true, note = "Official"),
            SourceEntry(FakeSource("jamendo"), defaultOn = true, keyName = "jamendo", note = "Needs a key"),
        ),
        settings,
    )

    @Test
    fun `rows show each source's switch and whether it's active`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = SettingsViewModel(catalog, settings)
            advanceUntilIdle()
            val rows = vm.rows.value
            assertEquals(listOf("RADIO", "JAMENDO"), rows.map { it.name })
            assertEquals(listOf(true, true), rows.map { it.switchedOn })
            assertEquals(listOf(true, false), rows.map { it.active })
            assertEquals("Add its key to switch it on.", rows[1].status)
        }
    }

    @Test
    fun `switching and saving a key update the rows`() = withMain(dispatcher) {
        runTest(dispatcher) {
            val vm = SettingsViewModel(catalog, settings)
            vm.setOn("radio", false)
            vm.saveKey("jamendo", "abc")
            advanceUntilIdle()
            val rows = vm.rows.value
            assertEquals(false, rows[0].active)
            assertEquals("Off", rows[0].status)
            assertEquals(true, rows[1].active)
            assertEquals("abc", rows[1].key)
            assertEquals("On", rows[1].status)
        }
    }
}
