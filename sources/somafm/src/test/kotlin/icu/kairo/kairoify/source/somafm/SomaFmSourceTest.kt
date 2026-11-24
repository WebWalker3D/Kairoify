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
package icu.kairo.kairoify.source.somafm

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

class SomaFmSourceTest {
    private val server = MockWebServer()
    private lateinit var source: SomaFmSource
    private val channels = """{"channels":[
      {"id":"groovesalad","title":"Groove Salad","description":"A nicely chilled plate of ambient beats.","genre":"ambient|electronica","largeimage":"https://api.somafm.com/logos/256/groovesalad256.png","listeners":"1500"},
      {"id":"dronezone","title":"Drone Zone","description":"Served best chilled.","genre":"ambient","largeimage":"https://api.somafm.com/logos/256/dronezone256.png","listeners":"900"}]}"""

    @BeforeTest fun start() {
        server.start()
        source = SomaFmSource(OkHttpClient(), server.url("/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `home lists every channel as a station, busiest first`() = runTest {
        server.enqueue(MockResponse.Builder().body(channels).build())
        val shelf = source.home().single()
        assertEquals("SomaFM: ad-free radio", shelf.title)
        val stations = shelf.items.map { assertIs<Station>(it) }
        assertEquals(listOf("Groove Salad", "Drone Zone"), stations.map { it.title })
        assertEquals("https://ice2.somafm.com/groovesalad-128-mp3", stations.first().streamUrl)
        assertEquals(listOf("ambient", "electronica"), stations.first().tags)
    }

    @Test
    fun `search matches title, description and genre`() = runTest {
        server.enqueue(MockResponse.Builder().body(channels).build())
        assertEquals(listOf("Groove Salad"), source.search("electronica").items.map { it.title })
        server.enqueue(MockResponse.Builder().body(channels).build())
        assertEquals(listOf("Drone Zone"), source.search("CHILLED drone").items.map { it.title })
    }

    @Test
    fun `resolve plays the channel's mp3 stream`() = runTest {
        val stream = source.resolve(MediaRef("somafm", "dronezone"))
        assertEquals("https://ice2.somafm.com/dronezone-128-mp3", stream.url)
        assertTrue(stream.isLive)
    }
}
