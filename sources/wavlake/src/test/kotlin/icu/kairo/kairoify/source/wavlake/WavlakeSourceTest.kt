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
package icu.kairo.kairoify.source.wavlake

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WavlakeSourceTest {
    private val server = MockWebServer()
    private lateinit var source: WavlakeSource
    private val track = """{"id":"b50b","title":"Rock de Yate","type":"track","albumArtUrl":"https://img/a.jpg","albumTitle":"Rock de Yate",
        "mediaUrl":"https://cdn/b50b.mp3","artist":"Mr. Information","duration":206}"""

    @BeforeTest fun start() {
        server.start()
        source = WavlakeSource(OkHttpClient(), server.url("/api/v1/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search keeps tracks only`() = runTest {
        server.enqueue(MockResponse.Builder().body("""[{"id":"a1","name":"Some artist","type":"artist"},{"id":"al","title":"An album","type":"album"},$track]""").build())
        val t = assertIs<Track>(source.search("rock").items.single())
        assertEquals(MediaRef("wavlake", "b50b"), t.ref)
        assertEquals("Mr. Information", t.subtitle)
        assertEquals("Rock de Yate", t.album)
        assertEquals(206_000L, t.durationMillis)
        val url = server.takeRequest().url
        assertEquals("/api/v1/content/search", url.encodedPath)
        assertEquals("rock", url.queryParameter("term"))
    }

    @Test
    fun `resolve looks the track up and plays its mp3`() = runTest {
        server.enqueue(MockResponse.Builder().body("[$track]").build())
        assertEquals("https://cdn/b50b.mp3", source.resolve(MediaRef("wavlake", "b50b")).url)
        assertEquals("/api/v1/content/track/b50b", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `a missing track says so`() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").build())
        assertFailsWith<SourceException.NotFound> { source.resolve(MediaRef("wavlake", "gone")) }
    }
}
