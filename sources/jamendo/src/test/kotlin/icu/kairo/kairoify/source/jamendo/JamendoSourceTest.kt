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
package icu.kairo.kairoify.source.jamendo

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class JamendoSourceTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private var reply = "tracks_search.json"
    private lateinit var source: JamendoSource

    private fun fixture(name: String) = JamendoSourceTest::class.java.getResource("/fixtures/$name")!!.readText()

    @BeforeTest fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                return if (request.url.encodedPath == "/v3.0/tracks/") {
                    MockResponse.Builder().body(fixture(reply)).build()
                } else {
                    MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        source = JamendoSource(OkHttpClient(), { "test-client" }, server.url("/v3.0/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps tracks with their license`() = runTest {
        val (first, second) = source.search("ketsa").items.map { assertIs<Track>(it) }
        assertEquals(MediaRef("jamendo", "1532771"), first.ref)
        assertEquals("Wanderer", first.title)
        assertEquals("Ketsa", first.subtitle)
        assertEquals("Saturated Colours", first.album)
        assertEquals(214_000L, first.durationMillis)
        assertEquals("CC BY-NC-ND 3.0", first.license?.name)
        assertEquals("https://www.jamendo.com/track/1532771", first.pageUrl)
        assertNull(second.license)
        assertNull(second.subtitle)
        assertNull(second.durationMillis)
        assertNull(second.artworkUrl)
        assertEquals("test-client", requests.last().url.queryParameter("client_id"))
    }

    @Test
    fun `passes the query intact`() = runTest {
        source.search("AC/DC \"Back in Black\" & Sigur Rós")
        assertEquals("AC/DC \"Back in Black\" & Sigur Rós", requests.last().url.queryParameter("search"))
    }

    @Test
    fun `a rejected client id is a clear failure`() = runTest {
        reply = "failed.json"
        val e = assertFailsWith<SourceException.Unavailable> { source.search("x") }
        assertEquals("Jamendo rejected Kairoify's client ID.", e.message)
    }

    @Test
    fun `home shows this week's picks`() = runTest {
        val shelf = source.home().single()
        assertEquals("Jamendo picks", shelf.title)
        assertEquals("popularity_week", requests.last().url.queryParameter("order"))
    }

    @Test
    fun `resolve uses the track's audio url`() = runTest {
        val stream = source.resolve(MediaRef("jamendo", "1532771"))
        assertEquals("1532771", requests.last().url.queryParameter("id"))
        assertEquals("https://prod-1.storage.jamendo.com/?trackid=1532771&format=mp31&from=app-xxxx", stream.url)
        assertEquals("CC BY-NC-ND 3.0", stream.license?.name)
    }
}
