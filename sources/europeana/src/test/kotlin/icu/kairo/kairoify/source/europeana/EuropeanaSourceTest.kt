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
package icu.kairo.kairoify.source.europeana

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

class EuropeanaSourceTest {
    private val server = MockWebServer()
    private lateinit var source: EuropeanaSource
    private val reply = """{"items":[
      {"id":"/2059208/data_sounds_123","title":["Polka"],"dcCreator":["Village band"],"edmIsShownBy":["https://sounds.example.org/polka.mp3"],
       "edmPreview":["https://api.europeana.eu/thumbnail/v2/url.json?uri=x"],"rights":["http://creativecommons.org/licenses/by-sa/4.0/"],"guid":"https://www.europeana.eu/item/2059208/data_sounds_123?utm_source=api"},
      {"id":"/285/gam28292","title":["Jazz"],"edmIsShownBy":["http://93.62.170.226/foto/gam/Grafica/fot1512.jpg"]}]}"""

    @BeforeTest fun start() {
        server.start()
        source = EuropeanaSource(OkHttpClient(), { "KEY" }, server.url("/record/v2/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search keeps items whose media is audio`() = runTest {
        server.enqueue(MockResponse.Builder().body(reply).build())
        val t = assertIs<Track>(source.search("polka").items.single())
        assertEquals(MediaRef("europeana", "/2059208/data_sounds_123"), t.ref)
        assertEquals("Village band", t.subtitle)
        assertEquals("CC BY-SA 4.0", t.license?.name)
        assertEquals("https://www.europeana.eu/item/2059208/data_sounds_123", t.pageUrl)
        val url = server.takeRequest().url
        assertEquals("KEY", url.queryParameter("wskey"))
        assertEquals("SOUND", url.queryParameter("type"))
        assertEquals("open", url.queryParameter("reusability"))
    }

    @Test
    fun `resolve finds the record's audio again`() = runTest {
        server.enqueue(MockResponse.Builder().body(reply).build())
        val stream = source.resolve(MediaRef("europeana", "/2059208/data_sounds_123"))
        assertEquals("europeana_id:\"/2059208/data_sounds_123\"", server.takeRequest().url.queryParameter("query"))
        assertEquals("https://sounds.example.org/polka.mp3", stream.url)
    }
}
