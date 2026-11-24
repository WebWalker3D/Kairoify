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
package icu.kairo.kairoify.source.freesound

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

class FreesoundSourceTest {
    private val server = MockWebServer()
    private lateinit var source: FreesoundSource

    @BeforeTest fun start() {
        server.start()
        source = FreesoundSource(OkHttpClient(), { "KEY" }, server.url("/apiv2/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search sends the key and maps sounds with their license`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"results":[{"id":123,"name":"Piano loop.wav","username":"ann","duration":12.5,"license":"http://creativecommons.org/licenses/by/4.0/",
                   "url":"https://freesound.org/people/ann/sounds/123/","previews":{"preview-hq-mp3":"https://cdn.freesound.org/previews/0/123-hq.mp3"}}]}""",
            ).build(),
        )
        val t = assertIs<Track>(source.search("piano").items.single())
        assertEquals(MediaRef("freesound", "123"), t.ref)
        assertEquals("Piano loop", t.title)
        assertEquals("ann", t.subtitle)
        assertEquals(12_500L, t.durationMillis)
        assertEquals("CC BY 4.0", t.license?.name)
        val url = server.takeRequest().url
        assertEquals("KEY", url.queryParameter("token"))
        assertEquals("piano", url.queryParameter("query"))
    }

    @Test
    fun `resolve plays the high-quality preview`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"id":123,"license":"http://creativecommons.org/publicdomain/zero/1.0/","previews":{"preview-hq-mp3":"https://cdn/123-hq.mp3"}}""").build())
        val stream = source.resolve(MediaRef("freesound", "123"))
        assertEquals("/apiv2/sounds/123/", server.takeRequest().url.encodedPath)
        assertEquals("https://cdn/123-hq.mp3", stream.url)
        assertEquals("CC0", stream.license?.name)
    }
}
