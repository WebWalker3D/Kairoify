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
package icu.kairo.kairoify.source.protocols

import icu.kairo.kairoify.core.model.Album
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
import kotlin.test.assertTrue

class FunkwhaleSourceTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()

    private val track = """{"id":7,"title":"Song","is_playable":true,"artist":{"id":3,"name":"Band"},"tags":["jazz"],
        "album":{"id":5,"title":"LP","cover":{"urls":{"large_square_crop":"/media/c.jpg"}}},
        "license":"cc-by-sa-4.0","uploads":[{"listen_url":"/api/v1/listen/abc/?upload=u1","duration":200,"mimetype":"audio/mpeg"}]}"""
    private val locked = """{"id":8,"title":"Locked","is_playable":false,"artist":{"id":4,"name":"X"},"uploads":[]}"""
    private val other = """{"id":9,"title":"Other","is_playable":true,"artist":{"id":3,"name":"Band"},"uploads":[]}"""
    private val album = """{"id":5,"title":"LP","is_playable":true,"release_date":"2014-01-01","tracks_count":2,"artist":{"id":3,"name":"Band"}}"""

    private fun ok(body: String) = MockResponse.Builder().body(body).addHeader("Content-Type", "application/json").build()
    private fun page(vararg r: String) = ok("""{"count":${r.size},"results":[${r.joinToString(",")}]}""")

    @BeforeTest fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val q = request.url
                return when (q.encodedPath) {
                    "/api/v1/tracks/" -> when {
                        q.queryParameter("artist") == "3" -> page(track, other)
                        q.queryParameter("tag") == "jazz" -> page(other)
                        else -> page(track, locked)
                    }
                    "/api/v1/albums/" -> page(album)
                    "/api/v1/albums/5/" -> ok(album)
                    "/api/v1/tracks/7/" -> ok(track)
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @AfterTest fun stop() = server.close()

    private val pod get() = Server(ServerKind.FUNKWHALE, "Pod", server.url("/").toString().trimEnd('/'), password = "tok")
    private fun source() = FunkwhaleSource(OkHttpClient(), { listOf(pod) }, publicPods = emptyList())

    @Test fun `search returns playable tracks and albums, skipping locked ones`() = runTest {
        val items = source().search("song").items
        val t = items.filterIsInstance<Track>().single()
        assertEquals("Song", t.title)
        assertEquals(listOf("Band"), t.artists)
        assertEquals(200_000L, t.durationMillis)
        assertEquals(server.url("/media/c.jpg").toString(), t.artworkUrl)
        assertEquals("LP", items.filterIsInstance<Album>().single().title)
        assertTrue(requests.all { it.headers["Authorization"] == "Bearer tok" && it.url.queryParameter("playable") == "true" })
    }

    @Test fun `album details, resolve with the token, and related by artist then tag`() = runTest {
        val s = source()
        val a = s.search("lp").items.filterIsInstance<Album>().single()
        val d = s.details(a.ref)
        assertEquals("2014", d.album.year)
        assertEquals(listOf("Song"), d.tracks.map { it.title })
        val stream = s.resolve(d.tracks.single().ref)
        assertEquals(server.url("/api/v1/listen/abc/?upload=u1").toString(), stream.url)
        assertEquals("Bearer tok", stream.headers["Authorization"])
        assertEquals(listOf("Other"), s.related(d.tracks.single().ref).map { it.title })
    }

    @Test fun `home shows the newest albums per pod`() = runTest {
        val shelf = source().home().single()
        assertEquals("New on Pod", shelf.title)
        assertEquals("-creation_date", requests.single().url.queryParameter("ordering"))
    }

    @Test fun `public pods show only licensed tracks, with the license`() = runTest {
        val public = FunkwhaleSource(OkHttpClient(), { emptyList() }, publicPods = listOf(pod.copy(password = "")))
        val items = public.search("song").items
        assertEquals(listOf("Song"), items.map { it.title }) // "Other" has no license; no albums from public pods
        assertEquals("CC BY-SA 4.0", (items.single() as Track).license?.name)
        assertEquals(listOf<String>(), public.related(items.single().ref).map { it.title })
    }

    @Test fun `license codes`() {
        assertEquals("CC BY 3.0", FunkwhaleSource.licenseOf("cc-by-3.0-de")?.name)
        assertEquals("CC0", FunkwhaleSource.licenseOf("cc0-1.0")?.name)
        assertEquals("Free Art License 1.3", FunkwhaleSource.licenseOf("LAL-1.3")?.name)
        assertEquals(null, FunkwhaleSource.licenseOf(null))
    }
}
