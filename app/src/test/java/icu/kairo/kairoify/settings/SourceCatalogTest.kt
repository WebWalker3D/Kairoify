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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SourceCatalogTest {
    private val settings = SourceSettings(RuntimeEnvironment.getApplication().getSharedPreferences("t", 0))
    private val radio = FakeSource("radio")
    private val keyed = FakeSource("jamendo")
    private val risky = FakeSource("ccmixter")
    private val catalog = SourceCatalog(
        listOf(
            SourceEntry(radio, defaultOn = true, note = "Official"),
            SourceEntry(keyed, defaultOn = true, keyName = "jamendo", note = "Official, key"),
            SourceEntry(risky, defaultOn = false, note = "Blocked"),
        ),
        settings,
    )

    @Test
    fun `sources follow their defaults`() {
        assertTrue(catalog.isEnabled(radio))
        assertFalse(catalog.isEnabled(risky))
    }

    @Test
    fun `keyed sources stay off until they have a key`() {
        assertFalse(catalog.isEnabled(keyed))
        settings.setKey("jamendo", "  abc123 ")
        assertTrue(catalog.isEnabled(keyed))
        assertEquals("abc123", catalog.key("jamendo"))
        settings.setOn("jamendo", false)
        assertFalse(catalog.isEnabled(keyed))
    }

    @Test
    fun `switches are remembered`() {
        settings.setOn("ccmixter", true)
        assertTrue(SourceCatalog(catalog.entries, SourceSettings(RuntimeEnvironment.getApplication().getSharedPreferences("t", 0))).isEnabled(risky))
    }

    @Test
    fun `a built-in key is used when none is saved`() {
        val withFallback = SourceCatalog(listOf(SourceEntry(keyed, true, keyName = "jamendo", keyFallback = "built-in", note = "")), settings)
        assertTrue(withFallback.isEnabled(keyed))
        assertEquals("built-in", withFallback.key("jamendo"))
    }

    @Test
    fun `every change is announced`() {
        val before = settings.changes.value
        settings.setOn("radio", false)
        settings.setKey("jamendo", "x")
        assertEquals(before + 2, settings.changes.value)
    }
}
