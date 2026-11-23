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
package icu.kairo.kairoify.core.player

import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.StreamMime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class MediaItemsTest {
    private val station = Station(
        ref = MediaRef("radiobrowser", "u1"), title = "Jazz FM", subtitle = "Germany · jazz",
        artworkUrl = "https://x/f.png", countryCode = "DE", tags = listOf("jazz"), codec = "MP3",
        bitrateKbps = 128, homepage = "https://jazz.fm", streamUrl = "http://a/stream", isHls = false,
    )

    @Test
    fun `fromItem keeps ref in mediaId`() {
        assertEquals(station.ref, MediaItems.refOf(MediaItems.fromItem(station)))
    }

    @Test
    fun `fromItem maps station metadata`() {
        val md = MediaItems.fromItem(station).mediaMetadata
        assertEquals("Jazz FM", md.title.toString())
        assertEquals("Germany · jazz", md.artist.toString())
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, md.mediaType)
        assertEquals("https://x/f.png", md.artworkUri.toString())
        assertEquals("https://jazz.fm", md.extras?.getString(MediaItems.EXTRA_HOMEPAGE))
        assertEquals(true, md.isPlayable)
        assertEquals(false, md.isBrowsable)
    }

    @Test
    fun `withStream sets HLS mime`() {
        val item = MediaItems.withStream(
            MediaItems.fromItem(station),
            ResolvedStream(url = "https://x/live.m3u8", mimeType = StreamMime.HLS, isLive = true),
        )
        val config = assertNotNull(item.localConfiguration)
        assertEquals(MimeTypes.APPLICATION_M3U8, config.mimeType)
        assertEquals("https://x/live.m3u8", config.uri.toString())
        assertTrue(item.mediaId == station.ref.encode())
    }

    @Test
    fun `withStream leaves mime null for plain streams`() {
        val item = MediaItems.withStream(MediaItems.fromItem(station), ResolvedStream(url = "http://a/stream", isLive = true))
        assertNull(assertNotNull(item.localConfiguration).mimeType)
    }
}
