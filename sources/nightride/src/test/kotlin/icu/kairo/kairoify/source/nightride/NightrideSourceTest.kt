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
package icu.kairo.kairoify.source.nightride

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NightrideSourceTest {
    private val source = NightrideSource()

    @Test
    fun `home lists all nine stations with their streams`() = runTest {
        val stations = source.home().single().items.map { assertIs<Station>(it) }
        assertEquals(9, stations.size)
        assertEquals("https://stream.nightride.fm/darksynth.mp3", stations.first { it.title == "Darksynth" }.streamUrl)
    }

    @Test
    fun `search matches names and styles`() = runTest {
        assertEquals(listOf("Rekt"), source.search("drum bass").items.map { it.title })
        assertEquals(9, source.search("synthwave").items.size)
        assertTrue(source.search("polka").items.isEmpty())
        assertTrue(source.search("  ").items.isEmpty())
    }

    @Test
    fun `resolve plays known stations live and refuses others`() = runTest {
        val stream = source.resolve(MediaRef("nightride", "chillsynth"))
        assertEquals("https://stream.nightride.fm/chillsynth.mp3", stream.url)
        assertTrue(stream.isLive)
        assertFailsWith<SourceException.NotFound> { source.resolve(MediaRef("nightride", "../x")) }
    }
}
