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
package icu.kairo.kairoify.source.audius

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudiusSourceTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private lateinit var source: AudiusSource

    private fun fixture(name: String) = AudiusSourceTest::class.java.getResource("/fixtures/$name")!!.readText()

    @BeforeTest fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val body = when (request.url.encodedPath) {
                    "/v1/tracks/search" -> fixture("search_daft_punk.json")
                    "/v1/tracks/trending" -> fixture("trending.json")
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
        source = AudiusSource(OkHttpClient(), server.url("/v1/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps streamable tracks`() = runTest {
        val tracks = source.search("daft punk").items.map { assertIs<Track>(it) }
        assertEquals(9, tracks.size)
        tracks.forEach {
            assertEquals("audius", it.ref.sourceId)
            assertTrue(it.artworkUrl.orEmpty().endsWith("480x480.jpg"), it.artworkUrl)
            assertTrue(it.pageUrl.orEmpty().startsWith("https://audius.co/"), it.pageUrl)
            assertTrue((it.durationMillis ?: 0) > 0)
            assertTrue(!it.subtitle.isNullOrBlank())
        }
        assertEquals("kairoify", requests.last().url.queryParameter("app_name"))
    }

    @Test
    fun `passes the query intact`() = runTest {
        source.search("AC/DC \"Back in Black\" & Sigur Rós")
        assertEquals("AC/DC \"Back in Black\" & Sigur Rós", requests.last().url.queryParameter("query"))
    }

    @Test
    fun `home shows trending`() = runTest {
        val shelf = source.home().single()
        assertEquals("Trending on Audius", shelf.title)
        assertEquals(20, shelf.items.size)
        assertEquals("week", requests.last().url.queryParameter("time"))
    }

    @Test
    fun `resolve streams from the api`() = runTest {
        val stream = source.resolve(MediaRef("audius", "vZJJz"))
        assertEquals("http://${server.hostName}:${server.port}/v1/tracks/vZJJz/stream?app_name=kairoify", stream.url)
        assertEquals(false, stream.isLive)
    }
}
