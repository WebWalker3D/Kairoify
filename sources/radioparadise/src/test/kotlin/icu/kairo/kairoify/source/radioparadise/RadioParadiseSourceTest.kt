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
package icu.kairo.kairoify.source.radioparadise

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
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

class RadioParadiseSourceTest {
    private val server = MockWebServer()
    private lateinit var source: RadioParadiseSource
    private val channels = """[
      {"chan":"0","title":"The Main Mix","stream_name":"main-mix","current_listeners":"11659","image":"https://img/0.jpg"},
      {"chan":"42","title":"Serenity","stream_name":"serenity","current_listeners":"86","image":"https://img/42.jpg"},
      {"chan":"1","title":"Mellow Mix","stream_name":"mellow","current_listeners":"6576","image":"https://img/1.jpg"}]"""

    @BeforeTest fun start() {
        server.start()
        source = RadioParadiseSource(OkHttpClient(), server.url("/api/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `home lists every channel, busiest first, with the right stream`() = runTest {
        server.enqueue(MockResponse.Builder().body(channels).build())
        val stations = source.home().single().items.map { assertIs<Station>(it) }
        assertEquals(listOf("The Main Mix", "Mellow Mix", "Serenity"), stations.map { it.title })
        assertEquals(
            listOf("https://stream.radioparadise.com/aac-320", "https://stream.radioparadise.com/mellow-320", "https://stream.radioparadise.com/serenity"),
            stations.map { it.streamUrl },
        )
        assertEquals(MediaRef("radioparadise", "mellow-320"), stations[1].ref)
    }

    @Test
    fun `search matches channel names`() = runTest {
        server.enqueue(MockResponse.Builder().body(channels).build())
        assertEquals(listOf("Mellow Mix"), source.search("mellow").items.map { it.title })
    }

    @Test
    fun `resolve plays the stream live`() = runTest {
        val stream = source.resolve(MediaRef("radioparadise", "mellow-320"))
        assertEquals("https://stream.radioparadise.com/mellow-320", stream.url)
        assertTrue(stream.isLive)
    }
}
