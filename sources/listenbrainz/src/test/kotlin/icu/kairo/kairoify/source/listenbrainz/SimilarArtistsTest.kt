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
package icu.kairo.kairoify.source.listenbrainz

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SimilarArtistsTest {
    private val server = MockWebServer()
    private lateinit var similar: SimilarArtists

    @BeforeTest fun start() {
        server.start()
        similar = SimilarArtists(OkHttpClient(), musicBrainz = server.url("/mb/"), listenBrainz = server.url("/lb/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `finds the artist, then the artists listeners pair with it`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"artists":[{"id":"2944","name":"Nina Simone","score":100}]}""").build())
        server.enqueue(
            MockResponse.Builder().body("""[{"name":"Billie Holiday","score":900},{"name":"Ella Fitzgerald","score":800},{"name":"Nina Simone","score":700}]""").build(),
        )
        assertEquals(listOf("Billie Holiday", "Ella Fitzgerald"), similar.similarTo("nina simone"))
        val search = server.takeRequest().url
        assertEquals("/mb/ws/2/artist/", search.encodedPath)
        assertEquals("artist:\"nina simone\"", search.queryParameter("query"))
        assertEquals("2944", server.takeRequest().url.queryParameter("artist_mbids"))
    }

    @Test
    fun `an artist MusicBrainz doesn't really know has no similar artists`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"artists":[{"id":"x","name":"Someone Else","score":41}]}""").build())
        assertEquals(emptyList(), similar.similarTo("dj nobody"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a busy MusicBrainz is asked again`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().body("""{"artists":[{"id":"2944","name":"Nina Simone","score":100}]}""").build())
        server.enqueue(MockResponse.Builder().body("""[{"name":"Billie Holiday"}]""").build())
        assertEquals(listOf("Billie Holiday"), similar.similarTo("nina simone"))
    }

    @Test
    fun `a lookup that keeps failing gives no similar artists rather than an error`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(503).build()) }
        assertEquals(emptyList(), similar.similarTo("nina simone"))
        assertEquals(3, server.requestCount)
    }
}
