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
package icu.kairo.kairoify.source.archive

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.MediaRef
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import icu.kairo.kairoify.core.source.SourceException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveSourceTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private var searchFixture = "search_grateful_dead.json"
    private lateinit var source: ArchiveSource

    private fun fixture(name: String) = ArchiveSourceTest::class.java.getResource("/fixtures/$name")!!.readText()

    @BeforeTest fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val body = when (request.url.encodedPath) {
                    "/advancedsearch.php" -> fixture(searchFixture)
                    "/metadata/gd77-05-08.sbd.hicks.4982.sbeok.shnf" -> fixture("metadata_gd77.json")
                    "/metadata/flac-only-show" -> fixture("metadata_flac_only.json")
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
        source = ArchiveSource(OkHttpClient(), server.url("/"), today = { LocalDate.of(2026, 11, 23) })
    }

    @AfterTest fun stop() = server.close()

    private fun query() = requests.last().url.queryParameter("q")!!

    @Test
    fun `search maps concerts to albums`() = runTest {
        val page = source.search("grateful dead")
        val albums = page.items.map { assertIs<Album>(it) }
        assertEquals(30, albums.size)
        val first = albums.first()
        assertEquals("archive", first.ref.sourceId)
        assertTrue(first.subtitle!!.startsWith("Grateful Dead"), first.subtitle)
        assertTrue(albums.any { it.subtitle!!.contains(" · ") })
        assertEquals("http://${server.hostName}:${server.port}/services/img/${first.ref.itemId}", first.artworkUrl)
        assertEquals("https://archive.org/details/${first.ref.itemId}", first.pageUrl)
        assertTrue(page.next != null)
    }

    @Test
    fun `search matches every word anywhere`() = runTest {
        source.search("  grateful dead   cornell ")
        assertTrue(query().endsWith("AND (\"grateful\" AND \"dead\" AND \"cornell\")"), query())
        assertTrue(query().contains("collection:etree AND mediatype:etree"))
    }

    @Test
    fun `search words stay quoted and escaped`() = runTest {
        source.search("AC/DC \"Live\"")
        assertTrue(query().endsWith("AND (\"AC/DC\" AND \"\\\"Live\\\"\")"), query())
    }

    @Test
    fun `backend errors are failures, not empty results`() = runTest {
        searchFixture = "backend_error.json"
        assertFailsWith<SourceException.Unavailable> { source.search("grateful dead") }
    }

    @Test
    fun `leap day only asks for dates that exist`() = runTest {
        source = ArchiveSource(OkHttpClient(), server.url("/"), today = { LocalDate.of(2028, 2, 29) })
        source.home()
        assertTrue(query().contains("date:2024-02-29"))
        assertTrue(!query().contains("date:2025-02-29"), query())
    }

    @Test
    fun `creators can be lists or missing`() = runTest {
        searchFixture = "browse_78s.json"
        val albums = source.browse(BrowseNode.Collection("georgeblood")).items.map { it as Album }
        assertTrue(albums.any { it.subtitle.orEmpty().contains(", ") }, albums.map { it.subtitle }.toString())
        assertTrue(albums.isNotEmpty())
        assertTrue(query().contains("collection:georgeblood AND mediatype:audio"))
    }

    @Test
    fun `details lists the mp3 tracks in order`() = runTest {
        val details = source.details(MediaRef("archive", "gd77-05-08.sbd.hicks.4982.sbeok.shnf"))
        assertEquals(20, details.tracks.size)
        assertEquals(20, details.album.trackCount)
        val first = details.tracks.first()
        assertEquals("Minglewood Blues", first.title)
        assertEquals(381_000L, first.durationMillis)
        assertEquals("gd77-05-08.sbd.hicks.4982.sbeok.shnf/gd77-05-08eaton-d1t01.mp3", first.ref.itemId)
        assertEquals(details.album.artworkUrl, first.artworkUrl)
        assertEquals("Grateful Dead", first.subtitle)
        assertTrue(details.tracks.zipWithNext().all { (a, b) -> a.ref.itemId < b.ref.itemId || a.title != b.title })
    }

    @Test
    fun `items without mp3s have no tracks`() = runTest {
        assertEquals(emptyList(), source.details(MediaRef("archive", "flac-only-show")).tracks)
    }

    @Test
    fun `lengths in seconds or minutes`() {
        assertEquals(182_260L, parseArchiveLength("182.26"))
        assertEquals(381_000L, parseArchiveLength("06:21"))
        assertEquals(3_723_000L, parseArchiveLength("1:02:03"))
        assertNull(parseArchiveLength("n/a"))
    }

    @Test
    fun `resolve encodes file names`() = runTest {
        val stream = source.resolve(MediaRef("archive", "some-show/disc 1/01 Intro #1.mp3"))
        assertEquals("http://${server.hostName}:${server.port}/download/some-show/disc%201/01%20Intro%20%231.mp3", stream.url)
        assertEquals(false, stream.isLive)
    }

    @Test
    fun `on this day asks for today's date in every year`() = runTest {
        val shelf = source.home().single()
        assertEquals("On this day: concerts recorded November 23", shelf.title)
        assertTrue(query().contains("date:1977-11-23"))
        assertTrue(query().contains("date:2025-11-23"))
        assertTrue(query().startsWith("mediatype:etree AND ("))
    }

    @Test
    fun `collections to browse`() = runTest {
        val facets = source.facets(FacetKind.COLLECTION)
        assertEquals(listOf("Live Music Archive", "Great 78 Project", "Netlabels", "Classical"), facets.map { it.label }.take(4))
        assertEquals(emptyList(), source.facets(FacetKind.COUNTRY))
    }

    @Test
    fun `netlabels are browsable by name`() = runTest {
        val labels = source.facets(FacetKind.COLLECTION).map { it.label }
        assertTrue("Monotonik" in labels && "Thinner" in labels && "Kahvi Collective" in labels, labels.toString())
        searchFixture = "browse_78s.json"
        source.browse(BrowseNode.Collection("monotonik"))
        assertEquals("collection:monotonik", query())
    }

    @Test
    fun `librivox is its own source over its own collection`() = runTest {
        val librivox = ArchiveSource.librivox(OkHttpClient(), server.url("/"))
        assertEquals("librivox", librivox.info.id)
        val albums = librivox.search("sherlock holmes").items
        assertTrue(albums.all { it.ref.sourceId == "librivox" })
        assertTrue(query().startsWith("(collection:librivoxaudio) AND ("), query())
        assertEquals(emptyList(), librivox.home())
    }
}
