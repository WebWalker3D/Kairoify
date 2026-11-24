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
package icu.kairo.kairoify.source.openverse

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

class OpenverseSourceTest {
    private val server = MockWebServer()
    private lateinit var source: OpenverseSource
    private val reply = """{"result_count":2,"results":[
      {"id":"2b66","title":"All About Jazz","creator":"Brotha D","url":"https://prod-1.storage.jamendo.com/?trackid=1348466&format=mp32","duration":235000,
       "license":"by-nc-nd","license_version":"3.0","license_url":"https://creativecommons.org/licenses/by-nc-nd/3.0/","foreign_landing_url":"https://www.jamendo.com/track/1348466",
       "thumbnail":"https://api.openverse.org/v1/audio/2b66/thumb/","source":"jamendo"},
      {"id":"x9","title":"","creator":null,"url":"https://a/b.mp3","duration":null}]}"""

    @BeforeTest fun start() {
        server.start()
        source = OpenverseSource(OkHttpClient(), server.url("/v1/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps audio with its license and keeps the url for playback`() = runTest {
        server.enqueue(MockResponse.Builder().body(reply).build())
        val tracks = source.search("jazz").items.map { assertIs<Track>(it) }
        assertEquals(1, tracks.size)
        val t = tracks.single()
        assertEquals(MediaRef("openverse", "2b66"), t.ref)
        assertEquals("Brotha D", t.subtitle)
        assertEquals(235_000L, t.durationMillis)
        assertEquals("CC BY-NC-ND 3.0", t.license?.name)
        assertEquals("https://www.jamendo.com/track/1348466", t.pageUrl)
        val url = server.takeRequest().url
        assertEquals("/v1/audio/", url.encodedPath)
        assertEquals("jazz", url.queryParameter("q"))
    }

    @Test
    fun `resolve looks the audio up by id`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"id":"2b66","url":"https://a/b.mp3","license_url":"https://creativecommons.org/licenses/by/4.0/"}""").build())
        val stream = source.resolve(MediaRef("openverse", "2b66"))
        assertEquals("/v1/audio/2b66/", server.takeRequest().url.encodedPath)
        assertEquals("https://a/b.mp3", stream.url)
        assertEquals("CC BY 4.0", stream.license?.name)
        assertNull(stream.expiresAtMillis)
    }
}
