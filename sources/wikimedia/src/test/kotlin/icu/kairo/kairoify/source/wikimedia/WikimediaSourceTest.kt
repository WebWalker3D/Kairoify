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
package icu.kairo.kairoify.source.wikimedia

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

class WikimediaSourceTest {
    private val server = MockWebServer()
    private lateinit var source: WikimediaSource
    private val recorded = WikimediaSourceTest::class.java.getResource("/fixtures/search_beethoven.json")!!.readText()

    @BeforeTest fun start() {
        server.start()
        source = WikimediaSource(OkHttpClient(), server.url("/w/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search maps audio files`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val tracks = source.search("beethoven").items.map { assertIs<Track>(it) }
        assertEquals(20, tracks.size)
        tracks.forEach {
            assertEquals("wikimedia", it.ref.sourceId)
            assertTrue(it.ref.itemId.startsWith("File:"), it.ref.itemId)
            assertTrue(!it.title.startsWith("File:") && !it.title.endsWith(".ogg"), it.title)
            assertTrue(it.pageUrl.orEmpty().startsWith("https://commons.wikimedia.org/wiki/File:"))
            assertTrue(it.subtitle.orEmpty().none { c -> c == '<' || c == '>' }, it.subtitle)
        }
        val first = tracks.first()
        assertEquals("Ludwig van Beethoven", first.subtitle)
        assertEquals("CC0", first.license?.name)
        val q = server.takeRequest().url
        assertEquals("filetype:audio beethoven", q.queryParameter("gsrsearch"))
    }

    @Test
    fun `html artists become plain text`() {
        assertEquals("Ludwig van Beethoven & Friends", plainText("<bdi><a href=\"x\">Ludwig van Beethoven</a></bdi> &amp; Friends"))
        assertEquals("Unknown", plainText("<span>Unknown</span>\n"))
    }

    @Test
    fun `public domain without a link still shows`() {
        assertEquals("Public domain", wikimediaLicense(null, "Public domain")?.name)
        assertEquals("CC BY-SA 4.0", wikimediaLicense("https://creativecommons.org/licenses/by-sa/4.0", "CC BY-SA 4.0")?.name)
    }

    @Test
    fun `resolve plays the file itself`() = runTest {
        server.enqueue(MockResponse.Builder().body(recorded).build())
        val title = "File:Beethoven, Sonata No. 8 in C Minor Pathetique, Op. 13 - I. Grave - Allegro di molto e con brio.ogg"
        val stream = source.resolve(MediaRef("wikimedia", title))
        assertEquals(title, server.takeRequest().url.queryParameter("titles"))
        assertTrue(stream.url.startsWith("https://upload.wikimedia.org/"), stream.url)
        assertEquals(false, stream.isLive)
    }

    @Test
    fun `midi files are left out`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"query":{"pages":{
                  "1":{"title":"File:Fugue.mid","index":1,"imageinfo":[{"url":"https://upload.wikimedia.org/a/Fugue.mid","mime":"audio/midi","extmetadata":{}}]},
                  "2":{"title":"File:Fugue.ogg","index":2,"imageinfo":[{"url":"https://upload.wikimedia.org/a/Fugue.ogg","mime":"application/ogg","extmetadata":{}}]}}}}""",
            ).build(),
        )
        assertEquals(listOf("Fugue"), source.search("fugue").items.map { it.title })
    }
}
