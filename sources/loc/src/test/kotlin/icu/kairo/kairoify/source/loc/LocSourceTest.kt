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
package icu.kairo.kairoify.source.loc

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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LocSourceTest {
    private val server = MockWebServer()
    private lateinit var source: LocSource
    private val search = """{"results":[
      {"title":"It's the blues","id":"http://www.loc.gov/item/jukebox-69787/","date":"1924-03-27","contributor_primary":["jean goldkette orchestra"],
       "image_url":["https://tile.loc.gov/a/pct:50/default.jpg","https://tile.loc.gov/a/pct:100/default.jpg"],"access_restricted":false,"online_format":["audio","image"]},
      {"title":"Locked away","id":"http://www.loc.gov/item/locked-1/","access_restricted":true,"online_format":["audio"]},
      {"title":"Just a picture","id":"http://www.loc.gov/item/pic-1/","access_restricted":false,"online_format":["image"]}]}"""

    @BeforeTest fun start() {
        server.start()
        source = LocSource(OkHttpClient(), server.url("/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search keeps open audio only`() = runTest {
        server.enqueue(MockResponse.Builder().body(search).build())
        val tracks = source.search("blues").items.map { assertIs<Track>(it) }
        assertEquals(1, tracks.size)
        val t = tracks.single()
        assertEquals(MediaRef("loc", "jukebox-69787"), t.ref)
        assertEquals("Jean Goldkette Orchestra · 1924", t.subtitle)
        assertEquals("https://tile.loc.gov/a/pct:50/default.jpg", t.artworkUrl)
        assertEquals("https://www.loc.gov/item/jukebox-69787/", t.pageUrl)
        val url = server.takeRequest().url
        assertEquals("/audio/", url.encodedPath)
        assertEquals("blues", url.queryParameter("q"))
        assertEquals("json", url.queryParameter("fo"))
    }

    @Test
    fun `resolve plays the item's mp3`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"resources":[{"audio":"https://tile.loc.gov/streaming-services/x/default.mp3","files":[]}]}""").build())
        val stream = source.resolve(MediaRef("loc", "jukebox-69787"))
        assertEquals("/item/jukebox-69787/", server.takeRequest().url.encodedPath)
        assertEquals("https://tile.loc.gov/streaming-services/x/default.mp3", stream.url)
    }

    @Test
    fun `items without audio say so`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"resources":[{"files":[]}]}""").build())
        assertFailsWith<icu.kairo.kairoify.core.source.SourceException.NotFound> { source.resolve(MediaRef("loc", "x")) }
    }

    @Test
    fun `names are title-cased`() = assertTrue(titleCase("joseph c. smith's orchestra") == "Joseph C. Smith's Orchestra")

    @Test
    fun `search waits long enough for loc_gov`() = assertTrue(source.searchTimeoutMillis >= 20_000L)

    @Test
    fun `browsing a collection filters by what it is part of`() = runTest {
        server.enqueue(MockResponse.Builder().body(search).build())
        val labels = source.facets(icu.kairo.kairoify.core.model.FacetKind.COLLECTION).map { it.label }
        assertEquals("National Jukebox", labels.first())
        source.browse(icu.kairo.kairoify.core.model.BrowseNode.Collection("national jukebox"))
        val url = server.takeRequest().url
        assertEquals("online-format:audio|partof:national jukebox", url.queryParameter("fa"))
        assertEquals(null, url.queryParameter("q"))
    }

    @Test
    fun `unknown collections are empty`() = runTest {
        assertTrue(source.browse(icu.kairo.kairoify.core.model.BrowseNode.Collection("nope")).items.isEmpty())
    }
}
