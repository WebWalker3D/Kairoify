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
package icu.kairo.kairoify.source.ccmixter

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
import kotlin.test.assertTrue

class CcMixterSourceTest {
    private val server = MockWebServer()
    private lateinit var source: CcMixterSource
    private val recorded = CcMixterSourceTest::class.java.getResource("/fixtures/search_piano.json")!!.readText()

    @BeforeTest fun start() {
        server.start()
        source = CcMixterSource(OkHttpClient(), server.url("/api/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps uploads with their license`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val tracks = source.search("piano").items.map { assertIs<Track>(it) }
        assertEquals(10, tracks.size)
        val first = tracks.first()
        assertEquals(MediaRef("ccmixter", "71499"), first.ref)
        assertEquals("Close to You", first.title)
        assertEquals("Rewob", first.subtitle)
        assertEquals(197_000L, first.durationMillis)
        assertEquals("CC BY-NC 3.0", first.license?.name)
        assertEquals("https://ccmixter.org/files/rewob/71499", first.pageUrl)
        assertTrue(tracks.all { it.license != null })
        val request = server.takeRequest().url
        assertEquals("10", request.queryParameter("limit"))
        assertEquals("default", request.queryParameter("dataview"))
    }

    @Test
    fun `passes the query intact`() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").build())
        source.search("AC/DC \"Live\" & Sigur Rós")
        assertEquals("AC/DC \"Live\" & Sigur Rós", server.takeRequest().url.queryParameter("search"))
    }

    @Test
    fun `plays the mp3, never the flac`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val stream = source.resolve(MediaRef("ccmixter", "71499"))
        assertEquals("71499", server.takeRequest().url.queryParameter("ids"))
        assertTrue(stream.url.endsWith(".mp3"), stream.url)
        assertEquals("CC BY-NC 3.0", stream.license?.name)
    }
}
