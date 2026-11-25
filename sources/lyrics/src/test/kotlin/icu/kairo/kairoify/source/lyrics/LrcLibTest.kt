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
package icu.kairo.kairoify.source.lyrics

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcLibTest {
    private val server = MockWebServer()
    private lateinit var lib: LrcLib

    @BeforeTest fun start() {
        server.start()
        lib = LrcLib(OkHttpClient(), server.url("/api/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `parses timed lines in order`() {
        val lines = parseLrc("[ar:Nina]\n[00:12.50] Birds flyin' high\n[01:02.5][00:20.00]Sun in the sky\n\nno time")
        assertEquals(listOf(12_500L, 20_000L, 62_500L), lines.map { it.atMillis })
        assertEquals("Sun in the sky", lines[1].text)
        val lyrics = Lyrics(lines, null)
        assertEquals(-1, lyrics.lineAt(1_000))
        assertEquals(1, lyrics.lineAt(30_000))
    }

    @Test
    fun `picks synced lyrics closest in length`() = runTest {
        server.enqueue(MockResponse.Builder().body("""[
          {"trackName":"Feeling Good","artistName":"Nina Simone","duration":300,"syncedLyrics":"[00:01.00] wrong"},
          {"trackName":"Feeling Good","artistName":"Nina Simone","duration":178,"plainLyrics":"plain only"},
          {"trackName":"Feeling Good","artistName":"Nina Simone","duration":176,"syncedLyrics":"[00:01.00] right","plainLyrics":"right"}]""").build())
        val lyrics = lib.find("Nina Simone", "Feeling Good", 177_000)!!
        assertEquals("right", lyrics.lines.single().text)
        val url = server.takeRequest().url
        assertEquals("Feeling Good", url.queryParameter("track_name"))
        assertEquals("Nina Simone", url.queryParameter("artist_name"))
    }

    @Test
    fun `nothing found or a failure gives null`() = runTest {
        server.enqueue(MockResponse.Builder().body("[]").build())
        assertNull(lib.find("x", "y", null))
        server.enqueue(MockResponse.Builder().code(500).build())
        assertNull(lib.find("x", "y", null))
        assertTrue(parseLrc("").isEmpty())
    }
}
