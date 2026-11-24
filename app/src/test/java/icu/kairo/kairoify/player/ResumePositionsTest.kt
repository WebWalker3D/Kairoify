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
package icu.kairo.kairoify.player

import icu.kairo.kairoify.core.model.MediaRef
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class ResumePositionsTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("resume-test", 0)
    private val song = MediaRef("archive", "show/01.mp3")

    @Test
    fun `a track left partway through resumes there`() {
        val positions = ResumePositions(prefs())
        positions.remember(song, positionMs = 95_000, durationMs = 600_000)
        assertEquals(95_000L, positions.positionFor(song))
        assertEquals(95_000L, ResumePositions(prefs()).positionFor(song))
    }

    @Test
    fun `the first five seconds start over`() {
        val positions = ResumePositions(prefs())
        positions.remember(song, positionMs = 4_000, durationMs = 600_000)
        assertNull(positions.positionFor(song))
    }

    @Test
    fun `the last ten seconds count as finished`() {
        val positions = ResumePositions(prefs())
        positions.remember(song, 95_000, 600_000)
        positions.remember(song, positionMs = 591_000, durationMs = 600_000)
        assertNull(positions.positionFor(song))
    }

    @Test
    fun `an unknown length still resumes`() {
        val positions = ResumePositions(prefs())
        positions.remember(song, positionMs = 30_000, durationMs = null)
        assertEquals(30_000L, positions.positionFor(song))
    }

    @Test
    fun `starting over forgets the spot`() {
        val positions = ResumePositions(prefs())
        positions.remember(song, 95_000, 600_000)
        positions.forget(song)
        assertNull(positions.positionFor(song))
    }

    @Test
    fun `only the most recent items are kept`() {
        val positions = ResumePositions(prefs(), maxEntries = 2)
        val a = MediaRef("s", "a"); val b = MediaRef("s", "b"); val c = MediaRef("s", "c")
        positions.remember(a, 60_000, null)
        positions.remember(b, 60_000, null)
        positions.remember(c, 60_000, null)
        assertNull(positions.positionFor(a))
        assertEquals(60_000L, positions.positionFor(c))
    }
}
