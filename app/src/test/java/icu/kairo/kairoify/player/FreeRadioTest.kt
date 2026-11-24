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
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.testing.FakeSource
import icu.kairo.kairoify.testing.station
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FreeRadioTest {
    private fun song(source: String, id: String, title: String, artist: String) =
        Track(ref = MediaRef(source, id), title = title, subtitle = artist, artists = listOf(artist))

    private val seed = song("youtube", "s", "Feeling Good", "Nina Simone")

    @Test
    fun `the seed plays first, then other artists in turn`() {
        val mixed = mixStation(
            seed,
            listOf(
                "Nina Simone" to listOf(song("archive", "1", "Sinnerman", "Nina Simone"), song("audius", "2", "Lilac Wine", "Nina Simone")),
                "Billie Holiday" to listOf(song("archive", "3", "Strange Fruit", "Billie Holiday")),
            ),
        )
        assertEquals(listOf("Feeling Good", "Sinnerman", "Strange Fruit", "Lilac Wine"), mixed.map { it.title })
    }

    @Test
    fun `tracks not really by the artist are left out`() {
        val mixed = mixStation(seed, listOf("Billie Holiday" to listOf(song("jamendo", "9", "Random tune", "Some Band"), song("archive", "3", "Strange Fruit", "Billie Holiday"))))
        assertEquals(listOf("Feeling Good", "Strange Fruit"), mixed.map { it.title })
    }

    @Test
    fun `the same song from two sources plays once, and so does the seed`() {
        val mixed = mixStation(
            seed,
            listOf("Nina Simone" to listOf(song("archive", "1", "Feeling Good (Remastered)", "Nina Simone"), song("audius", "2", "Sinnerman", "Nina Simone"), song("archive", "3", "Sinnerman", "Nina Simone"))),
        )
        assertEquals(listOf("Feeling Good", "Sinnerman"), mixed.map { it.title })
    }

    @Test
    fun `stations and albums never get in`() {
        val mixed = mixStation(seed, listOf("Nina Simone" to listOf(station("x"))))
        assertEquals(listOf(seed), mixed)
    }

    @Test
    fun `no artist gets more than its share`() {
        val many = (1..10).map { song("archive", "n$it", "Song $it", "Nina Simone") }
        val mixed = mixStation(seed, listOf("Nina Simone" to many), maxPerArtist = 3)
        assertEquals(4, mixed.size)
    }

    @Test
    fun `builds from every music source, for the seed artist and similar ones`() = runTest {
        val archive = FakeSource("archive", searchItems = listOf(song("archive", "1", "Sinnerman", "Nina Simone"), song("archive", "3", "Strange Fruit", "Billie Holiday")))
        val radio = FakeSource("radio", searchItems = listOf(station("r")))
        val freeRadio = FreeRadio(SourceRegistry(listOf(archive, radio)), similar = { listOf("Billie Holiday") }, isMusic = { it.info.id == "archive" })
        val queue = freeRadio.build(seed)
        assertEquals(listOf("Feeling Good", "Sinnerman", "Strange Fruit"), queue.map { it.title })
        assertEquals(listOf("Nina Simone", "Billie Holiday"), archive.searchQueries)
        assertTrue(radio.searchQueries.isEmpty())
    }

    @Test
    fun `a seed without an artist has no free radio`() {
        assertEquals(null, seedArtist(Track(ref = MediaRef("x", "1"), title = "Untitled")))
        assertEquals("Nina Simone", seedArtist(seed))
    }

    @Test
    fun `streams the station, one song per artist as each arrives, then the rest`() = runTest {
        val archive = FakeSource(
            "archive",
            searchItems = listOf(
                song("archive", "1", "Sinnerman", "Nina Simone"), song("archive", "2", "Lilac Wine", "Nina Simone"),
                song("archive", "3", "Strange Fruit", "Billie Holiday"), song("archive", "4", "Lover Man", "Billie Holiday"),
            ),
        )
        val freeRadio = FreeRadio(SourceRegistry(listOf(archive)), similar = { listOf("Billie Holiday") }, isMusic = { true })
        val batches = mutableListOf<List<String>>()
        freeRadio.stream(seed) { batch -> batches += batch.map { it.title } }
        assertEquals(listOf(listOf("Sinnerman"), listOf("Strange Fruit"), listOf("Lilac Wine", "Lover Man")), batches)
    }

    @Test
    fun `without similar artists it borrows them from the seed service's own suggestions`() = runTest {
        val youtube = FakeSource("youtube", relatedItems = listOf(song("youtube", "r1", "Respect", "Aretha Franklin")), capabilities = setOf(icu.kairo.kairoify.core.source.Capability.RELATED))
        val archive = FakeSource("archive", searchItems = listOf(song("archive", "a1", "Think", "Aretha Franklin")))
        val freeRadio = FreeRadio(SourceRegistry(listOf(youtube, archive)), similar = { emptyList() }, isMusic = { it.info.id == "archive" })
        val batches = mutableListOf<String>()
        freeRadio.stream(seed) { batch -> batches += batch.map { it.title } }
        assertEquals(listOf("Nina Simone", "Aretha Franklin"), archive.searchQueries)
        assertEquals(listOf("Think"), batches)
    }

    @Test
    fun `only whole artist names count, including in shared credits`() {
        val mixed = mixStation(
            seed,
            listOf(
                "Ray Charles" to listOf(song("audius", "1", "DMT", "Ray Charles Manson"), song("archive", "2", "Hit the Road Jack", "Ray Charles")),
                "Aretha Franklin" to listOf(song("audius", "3", "A Change", "Edwin Starr / Aretha Franklin"), song("audius", "4", "Duet", "Someone feat. Aretha Franklin")),
            ),
        )
        assertEquals(listOf("Feeling Good", "Hit the Road Jack", "A Change", "Duet"), mixed.map { it.title })
    }
}
