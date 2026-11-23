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
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MixcloudSourceTest {
    private val server = MockWebServer()
    private val engine = FakeEngine()
    private lateinit var source: MixcloudSource

    @Before fun start() {
        server.start()
        source = MixcloudSource(MixcloudSearchApi(OkHttpClient(), server.url("/")), engine)
    }

    @After fun stop() = server.close()

    private val recorded = MixcloudSourceTest::class.java.getResource("/fixtures/mixcloud_search_techno.json")!!.readText()

    @Test
    fun `search maps mixes`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val tracks = source.search("techno").items.map { assertIs<Track>(it) }
        assertEquals(20, tracks.size)
        tracks.forEach {
            assertEquals("mixcloud", it.ref.sourceId)
            assertTrue(it.ref.itemId.startsWith("/") && it.ref.itemId.endsWith("/"), it.ref.itemId)
            assertTrue(it.isHls)
            assertTrue(it.pageUrl.orEmpty().startsWith("https://www.mixcloud.com/"))
            assertTrue((it.durationMillis ?: 0) > 0)
            assertTrue(!it.subtitle.isNullOrBlank())
        }
        assertEquals("/search/", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `passes the query intact`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"data":[]}""").build())
        source.search("AC/DC \"Live\" & Sigur Rós")
        val url = server.takeRequest().url
        assertEquals("AC/DC \"Live\" & Sigur Rós", url.queryParameter("q"))
        assertEquals("cloudcast", url.queryParameter("type"))
    }

    @Test
    fun `resolve asks yt-dlp for the HLS stream`() = runTest {
        engine.resolveAnswers[ResolveMode.HLS] = { EngineStream("https://audio.mixcloud.com/x.m3u8", "m3u8_native", emptyMap(), 100.0) }
        val stream = source.resolve(MediaRef("mixcloud", "/dj/mix-1/"))
        assertEquals(listOf<Any>("https://www.mixcloud.com/dj/mix-1/", ResolveMode.HLS), engine.calls.single().second)
        assertEquals(StreamMime.HLS, stream.mimeType)
    }
}
