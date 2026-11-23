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
package icu.kairo.kairoify.source.hearthis

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HearthisSourceTest {
    private val server = MockWebServer()
    private lateinit var source: HearthisSource
    private val recorded = HearthisSourceTest::class.java.getResource("/fixtures/search_techno.json")!!.readText()

    @BeforeTest fun start() {
        server.start()
        source = HearthisSource(OkHttpClient(), server.url("/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps tracks`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val tracks = source.search("techno").items.map { assertIs<Track>(it) }
        assertEquals(20, tracks.size)
        tracks.forEach {
            assertEquals("hearthis", it.ref.sourceId)
            assertTrue(it.ref.itemId.matches(Regex("[^/]+/[^/]+")), it.ref.itemId)
            assertTrue(it.pageUrl.orEmpty().startsWith("https://hearthis.at/"))
            assertTrue((it.durationMillis ?: 0) > 0)
        }
        assertEquals(15_059_000L, tracks.first().durationMillis)
    }

    @Test
    fun `empty durations and licenses are tolerated`() = runTest {
        server.enqueue(MockResponse.Builder().body("""[{"id":"1","title":"T","duration":"","permalink_url":"https://hearthis.at/a/t/","license":"","user":{"username":"a"}}]""").build())
        val track = source.search("x").items.single() as Track
        assertNull(track.durationMillis)
        assertNull(track.license)
        assertEquals("a/t", track.ref.itemId)
    }

    @Test
    fun `passes the query intact`() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").build())
        source.search("AC/DC \"Live\" & Sigur Rós")
        val url = server.takeRequest().url
        assertEquals("AC/DC \"Live\" & Sigur Rós", url.queryParameter("t"))
        assertEquals("tracks", url.queryParameter("type"))
    }

    @Test
    fun `resolve fetches the track's stream url`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"stream_url":"https://hearthis.app/a/t/listen/?s=X"}""").build())
        val stream = source.resolve(MediaRef("hearthis", "a/t"))
        assertEquals("/a/t/", server.takeRequest().url.encodedPath)
        assertEquals("https://hearthis.app/a/t/listen/?s=X", stream.url)
        assertEquals(false, stream.isLive)
    }

    @Test
    fun `no results is an empty page, not an outage`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"success":false,"message":"No results found","timestamp":1}""").build())
        assertEquals(emptyList(), source.search("zzqxqx").items)
    }
}
