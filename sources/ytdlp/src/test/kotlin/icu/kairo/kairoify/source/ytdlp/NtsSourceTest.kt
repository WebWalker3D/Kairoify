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
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NtsSourceTest {
    private val server = MockWebServer()
    private val engine = FakeEngine()
    private val paths = mutableListOf<String>()
    private lateinit var source: NtsSource

    private fun fixture(name: String) = NtsSourceTest::class.java.getResource("/fixtures/$name")!!.readText()

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(paths) { paths += request.url.encodedPath }
                val body = when {
                    request.url.encodedPath == "/api/v2/live" -> fixture("nts_live.json")
                    request.url.encodedPath == "/api/v2/search" -> fixture("nts_search_jazz.json")
                    request.url.encodedPath.startsWith("/api/v2/shows/") -> fixture("nts_episode.json")
                    else -> return MockResponse.Builder().code(404).build()
                }
                return MockResponse.Builder().body(body).build()
            }
        }
        server.start()
        source = NtsSource(NtsApi(OkHttpClient(), server.url("/api/v2/")), engine)
    }

    @After fun stop() = server.close()

    @Test
    fun `home shows both live channels`() = runTest {
        val shelf = source.home().single()
        assertEquals("Live on NTS", shelf.title)
        val stations = shelf.items.map { assertIs<Station>(it) }
        assertEquals(listOf("NTS 1", "NTS 2"), stations.map { it.title })
        assertEquals(listOf("live1", "live2"), stations.map { it.ref.itemId })
        assertTrue(stations.all { !it.subtitle.isNullOrBlank() })
    }

    @Test
    fun `live channels stream from the relay`() = runTest {
        val stream = source.resolve(MediaRef("nts", "live2"))
        assertEquals("https://stream-relay-geo.ntslive.net/stream2", stream.url)
        assertEquals(true, stream.isLive)
    }

    @Test
    fun `episodes without playable audio are left out`() = runTest {
        val tracks = source.search("jazz").items.map { assertIs<Track>(it) }
        assertEquals(20, tracks.size)
        assertTrue(tracks.none { it.title == "No audio episode" || it.title == "Other host episode" })
        val first = tracks.first()
        assertTrue(first.ref.itemId.startsWith("/shows/"), first.ref.itemId)
        assertEquals("https://www.nts.live${first.ref.itemId}", first.pageUrl)
        assertTrue(first.artworkUrl.orEmpty().startsWith("https://"))
    }

    @Test
    fun `episodes play their SoundCloud recording`() = runTest {
        engine.resolveAnswers[ResolveMode.FAST] = { EngineStream("https://cf-hls/x.m3u8", "m3u8_native", emptyMap(), 7200.0) }
        source.resolve(MediaRef("nts", "/shows/the-breakfast-show-flo/episodes/the-breakfast-show-flo-23rd-november-2026"))
        assertTrue(paths.last().startsWith("/api/v2/shows/the-breakfast-show-flo/episodes/"))
        val (url, mode) = engine.calls.single().second
        assertTrue((url as String).startsWith("https://soundcloud.com/"), url)
        assertEquals(ResolveMode.FAST, mode)
    }

    @Test
    fun `passes the query intact`() = runTest {
        source.search("AC/DC \"Live\" & Sigur Rós")
        assertEquals("AC/DC \"Live\" & Sigur Rós", server.takeRequest().url.queryParameter("q"))
    }

    @Test
    fun `shows hosted only on Mixcloud are left out`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().body(
                """{"results":[
                  {"title":"On SoundCloud","article":{"path":"/shows/a/episodes/sc"},"audio_sources":[{"url":"https://soundcloud.com/x/y","source":"soundcloud"}]},
                  {"title":"On Mixcloud","article":{"path":"/shows/a/episodes/mc"},"audio_sources":[{"url":"https://www.mixcloud.com/x/y/","source":"mixcloud"}]}]}""",
            ).build()
        }
        assertEquals(listOf("On SoundCloud"), source.search("x").items.map { it.title })
    }

    @Test
    fun `search uses the endpoint that filters by the query`() = runTest {
        source.search("floating points")
        val url = server.takeRequest().url
        assertEquals("/api/v2/search", url.encodedPath)
        assertEquals("floating points", url.queryParameter("q"))
        assertEquals("episode", url.queryParameter("types[]"))
        assertEquals("2", url.queryParameter("version"))
    }
}
