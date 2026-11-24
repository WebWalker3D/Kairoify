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
package icu.kairo.kairoify.source.podcasts

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.MediaRef
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PodcastSourceTest {
    private val server = MockWebServer()
    private lateinit var source: PodcastSource

    @BeforeTest fun start() {
        server.start()
        source = PodcastSource(OkHttpClient(), server.url("/"))
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `search finds shows that have a feed`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"results":[{"collectionId":1,"collectionName":"Jazz After Dark","artistName":"Don Shor","feedUrl":"https://kdrt.org/feed",
                   "artworkUrl600":"https://img/600.jpg","trackCount":2,"collectionViewUrl":"https://podcasts.apple.com/x"},
                   {"collectionId":2,"collectionName":"No feed"}]}""",
            ).build(),
        )
        val show = assertIs<Album>(source.search("jazz").items.single())
        assertEquals(MediaRef("podcasts", "https://kdrt.org/feed"), show.ref)
        assertEquals("Don Shor", show.subtitle)
        assertEquals(2, show.trackCount)
        val url = server.takeRequest().url
        assertEquals("podcast", url.queryParameter("media"))
        assertEquals("jazz", url.queryParameter("term"))
    }

    @Test
    fun `a show's episodes come from its feed`() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """<?xml version="1.0"?><rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd"><channel>
                  <title>Jazz After Dark</title><itunes:author>Don Shor</itunes:author><itunes:image href="https://img/show.jpg"/><link>https://kdrt.org/</link>
                  <item><title>Episode 2</title><enclosure url="https://cdn/ep2.mp3" type="audio/mpeg"/><itunes:duration>1:02:03</itunes:duration><link>https://kdrt.org/2</link></item>
                  <item><title>Video only</title><enclosure url="https://cdn/v.mp4" type="video/mp4"/></item>
                  <item><title>Episode 1</title><enclosure url="https://cdn/ep1.mp3" type="audio/mpeg"/><itunes:duration>754</itunes:duration></item>
                </channel></rss>""",
            ).build(),
        )
        val feed = server.url("/feed").toString()
        val details = source.details(MediaRef("podcasts", feed))
        assertEquals("Jazz After Dark", details.album.title)
        assertEquals(listOf("Episode 2", "Episode 1"), details.tracks.map { it.title })
        assertEquals(3_723_000L, details.tracks[0].durationMillis)
        assertEquals(754_000L, details.tracks[1].durationMillis)
        assertEquals("https://img/show.jpg", details.tracks[0].artworkUrl)
        assertEquals(MediaRef("podcasts", "https://cdn/ep2.mp3"), details.tracks[0].ref)
    }

    @Test
    fun `an episode plays its enclosure`() = runTest {
        assertEquals("https://cdn/ep2.mp3", source.resolve(MediaRef("podcasts", "https://cdn/ep2.mp3")).url)
    }

    @Test
    fun `feeds with a doctype are refused rather than expanded`() = runTest {
        server.enqueue(MockResponse.Builder().body("""<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>""").build())
        kotlin.test.assertFailsWith<icu.kairo.kairoify.core.source.SourceException> { source.details(MediaRef("podcasts", server.url("/f").toString())) }
    }
}
