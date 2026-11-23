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
package icu.kairo.kairoify.source.ytdlp

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BandcampSourceTest {
    private val server = MockWebServer()
    private val engine = FakeEngine()
    private lateinit var source: BandcampSource

    @Before fun start() {
        server.start()
        source = BandcampSource(BandcampSearchApi(OkHttpClient(), server.url("/")), engine)
    }

    @After fun stop() = server.close()

    private fun reply(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())

    private val recorded = BandcampSourceTest::class.java.getResource("/fixtures/bandcamp_search_daft_punk.json")!!.readText()

    @Test
    fun `sends the query as json`() = runTest {
        reply(recorded)
        val query = "AC/DC \"Live\" & Sigur Rós"
        source.search(query)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/bcsearch_public_api/1/autocomplete_elastic", request.url.encodedPath)
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertEquals(query, body["search_text"]!!.jsonPrimitive.content)
        assertEquals("t", body["search_filter"]!!.jsonPrimitive.content)
        assertEquals(false, body["full_page"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `maps tracks from the recorded fixture`() = runTest {
        reply(recorded)
        val items = source.search("daft punk").items
        assertTrue(items.isNotEmpty())
        items.forEach { item ->
            val track = assertIs<Track>(item)
            assertEquals("bandcamp", track.ref.sourceId)
            assertTrue(track.ref.itemId.startsWith("https://") && "/track/" in track.ref.itemId, track.ref.itemId)
            assertEquals(track.ref.itemId, track.pageUrl)
            assertTrue(track.artworkUrl == null || track.artworkUrl!!.endsWith("_2.jpg"), track.artworkUrl)
            assertTrue(!track.subtitle.isNullOrBlank())
        }
    }

    @Test
    fun `non-track results are skipped`() = runTest {
        reply("""{"auto":{"results":[{"type":"a","name":"Album","item_url_path":"https://x.bandcamp.com/album/a"},{"type":"b","name":"Band"}]}}""")
        assertTrue(source.search("x").items.isEmpty())
    }

    @Test
    fun `rate limit becomes RateLimited`() = runTest {
        reply("{}", code = 429)
        val e = assertFailsWith<SourceException.RateLimited> { source.search("x") }
        assertEquals("Bandcamp is busy. Try again in a minute.", e.message)
    }

    @Test
    fun `server errors become Unavailable`() = runTest {
        reply("oops", code = 503)
        val e = assertFailsWith<SourceException.Unavailable> { source.search("x") }
        assertEquals("Couldn't reach Bandcamp.", e.message)
    }

    @Test
    fun `resolve passes the track page to the engine`() = runTest {
        val page = "https://artist.bandcamp.com/track/song"
        engine.resolveAnswers[ResolveMode.FAST] = { EngineStream("https://t4.bcbits.com/stream/x.mp3", "https", emptyMap(), 200.0) }
        val stream = source.resolve(MediaRef("bandcamp", page))
        assertEquals(listOf<Any>(page, ResolveMode.FAST), engine.calls.single().second)
        assertEquals("https://t4.bcbits.com/stream/x.mp3", stream.url)
    }
}
