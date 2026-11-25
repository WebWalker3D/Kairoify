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
package icu.kairo.kairoify.importing

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlaylistImporterTest {
    private val importer = PlaylistImporter(
        listLink = { listOf(
            buildJsonObject { put("title", JsonPrimitive("Feeling Good")); put("channel", JsonPrimitive("Nina Simone - Topic")) },
            buildJsonObject { put("title", JsonPrimitive("Etta James - At Last (Official Audio)")); put("channel", JsonPrimitive("Some Fan")) },
        ) },
        findSong = { t -> if (t.title.startsWith("Feeling")) Track(MediaRef("audius", "f"), t.title) else null },
    )

    @Test
    fun `reads pasted lists in several shapes`() = runTest {
        val songs = importer.songsIn("1. Nina Simone - Feeling Good\nAt Last by Etta James\n\njunk line\nA – B")
        assertEquals(listOf(WantedSong("Nina Simone", "Feeling Good"), WantedSong("Etta James", "At Last"), WantedSong("A", "B")), songs)
    }

    @Test
    fun `reads links through the engine`() = runTest {
        val songs = importer.songsIn("https://music.youtube.com/playlist?list=PL1")
        assertEquals(listOf(WantedSong("Nina Simone", "Feeling Good"), WantedSong("Etta James", "At Last (Official Audio)")), songs)
    }

    @Test
    fun `refuses Spotify links with a way forward`() = runTest {
        assertFailsWith<IllegalArgumentException> { importer.songsIn("https://open.spotify.com/playlist/x") }
    }

    @Test
    fun `reports what was found and what wasn't`() = runTest {
        val progress = mutableListOf<Int>()
        val result = importer.import("Nina Simone - Feeling Good\nEtta James - At Last") { d, _ -> progress += d }
        assertEquals(listOf("f"), result.found.map { it.ref.itemId })
        assertEquals(listOf(WantedSong("Etta James", "At Last")), result.missed)
        assertEquals(listOf(0, 1, 2), progress)
    }
}
