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
package icu.kairo.kairoify.source.radiobrowser

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.network.createHttpClient
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RadioBrowserSourceTest {
    private val server = MockWebServer()
    private val client = createHttpClient("test", null)
    private var country: String? = "DE"

    private val fiftyStations: String by lazy {
        val one = fixture("station_byuuid.json").trim().removePrefix("[").removeSuffix("]")
        (1..50).joinToString(",", "[", "]") { one }
    }

    private fun source() = RadioBrowserSource(client, { server.url("/") }, { country })

    private fun routes(vararg extra: Pair<String, () -> MockResponse>) {
        server.dispatcher = FixtureDispatcher(
            mapOf(
                "/json/stations/search" to { json(fixture("stations_search.json")) },
                "/json/stations/bycountrycodeexact" to { json(fixture("stations_country_DE.json")) },
                "/json/stations/bytagexact" to { json(fixture("stations_search.json")) },
                "/json/stations/topclick" to { json(fixture("stations_search.json")) },
                "/json/countries" to { json(fixture("countries.json")) },
                "/json/tags" to { json(fixture("tags.json")) },
                "/json/stations/byuuid" to { json(fixture("station_byuuid.json")) },
                "/json/url" to { json("""{"ok":true}""") },
            ) + extra,
        )
    }

    @BeforeTest fun start() {
        Locale.setDefault(Locale.US)
        server.start()
        routes()
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps fixture stations`() = runTest {
        val page = source().search("jazz")
        assertEquals(3, page.items.size)
        val first = assertIs<Station>(page.items[0])
        assertEquals("101 SMOOTH JAZZ", first.title)
        assertEquals("http://jking.cdnstream1.com/b22139_128mp3", first.streamUrl)
        assertEquals(MediaRef("radiobrowser", "d28420a4-eccf-47a2-ace1-088c7e7cb7e0"), first.ref)
    }

    @Test
    fun `search sends paging and hidebroken params`() = runTest {
        source().search("jazz")
        val url = server.takeRequest().url
        assertEquals("clickcount", url.queryParameter("order"))
        assertEquals("true", url.queryParameter("reverse"))
        assertEquals("50", url.queryParameter("limit"))
        assertEquals("0", url.queryParameter("offset"))
        assertEquals("true", url.queryParameter("hidebroken"))
    }

    @Test
    fun `search encodes special characters`() = runTest {
        source().search("AC/DC & más")
        assertEquals("AC/DC & más", server.takeRequest().url.queryParameter("name"))
    }

    @Test
    fun `genre with slash is one path segment`() = runTest {
        source().browse(BrowseNode.Genre("80s/90s"))
        assertEquals("/json/stations/bytagexact/80s%2F90s", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `next page token only when page is full`() = runTest {
        assertNull(source().search("jazz").next)
        routes("/json/stations/search" to { json(fiftyStations) })
        assertEquals("50", assertNotNull(source().search("jazz").next).value)
    }

    @Test
    fun `home with country has local then top shelf`() = runTest {
        val shelves = source().home()
        assertEquals(listOf("radiobrowser:local", "radiobrowser:top"), shelves.map { it.id })
        assertEquals("Live now in Germany", shelves[0].title)
        assertEquals("Most popular worldwide", shelves[1].title)
    }

    @Test
    fun `home without country has only top shelf`() = runTest {
        country = null
        assertEquals(listOf("radiobrowser:top"), source().home().map { it.id })
    }

    @Test
    fun `countries facets drop empty and sort by label`() = runTest {
        val facets = source().facets(FacetKind.COUNTRY)
        assertEquals(listOf("Brazil", "Germany", "Ghana", "Japan"), facets.map { it.label })
        assertEquals(BrowseNode.Country("DE"), facets[1].node)
        assertEquals(5626, facets[1].count)
    }

    @Test
    fun `genre facets keep station count order`() = runTest {
        val facets = source().facets(FacetKind.GENRE)
        assertEquals(listOf("pop", "music", "news", "rock", "talk"), facets.map { it.label })
        assertEquals(BrowseNode.Genre("pop"), facets[0].node)
    }

    @Test
    fun `resolve plain station`() = runTest {
        val stream = source().resolve(MediaRef("radiobrowser", "d28420a4-eccf-47a2-ace1-088c7e7cb7e0"))
        assertEquals("http://jking.cdnstream1.com/b22139_128mp3", stream.url)
        assertNull(stream.mimeType)
        assertEquals(true, stream.isLive)
    }

    @Test
    fun `resolve hls station sets HLS mime`() = runTest {
        routes("/json/stations/byuuid" to { json(fixture("station_hls.json")) })
        val stream = source().resolve(MediaRef("radiobrowser", "11111111-2222-3333-4444-555555555555"))
        assertEquals(StreamMime.HLS, stream.mimeType)
    }

    @Test
    fun `resolve unknown uuid throws NotFound`() = runTest {
        routes("/json/stations/byuuid" to { json("[]") })
        assertFailsWith<SourceException.NotFound> { source().resolve(MediaRef("radiobrowser", "nope")) }
    }

    @Test
    fun `429 becomes RateLimited`() = runTest {
        routes("/json/stations/search" to { json("slow down", code = 429) })
        assertFailsWith<SourceException.RateLimited> { source().search("jazz") }
    }

    @Test
    fun `500 becomes Unavailable`() = runTest {
        routes("/json/stations/search" to { json("broken", code = 500) })
        assertFailsWith<SourceException.Unavailable> { source().search("jazz") }
    }

    @Test
    fun `reportPlay hits click endpoint`() = runTest {
        source().reportPlay(MediaRef("radiobrowser", "abc-123"))
        assertEquals("/json/url/abc-123", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `the globe gets only stations that say where they are`() = runTest {
        routes("/json/stations/search" to {
            json("""[
              {"stationuuid":"a","name":"Here","url":"http://a","geo_lat":52.5,"geo_long":13.4},
              {"stationuuid":"b","name":"Nowhere","url":"http://b"},
              {"stationuuid":"c","name":"Null island","url":"http://c","geo_lat":0.0,"geo_long":0.0}]""")
        })
        val placed = source().placedStations()
        assertEquals(listOf("Here"), placed.map { it.station.title })
        assertEquals(52.5, placed.single().lat)
        assertEquals("true", server.takeRequest().url.queryParameter("has_geo_info"))
    }
}
