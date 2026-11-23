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
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
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

    private val track = Track(
        ref = MediaRef("youtube", "Jb6gcoR266U"), title = "Around the World", subtitle = "Daft Punk",
        artworkUrl = "https://x/w544.jpg", artists = listOf("Daft Punk"), album = "Homework", durationMillis = 430_000,
        pageUrl = "https://music.youtube.com/watch?v=Jb6gcoR266U",
    )

    @Test
    fun `track metadata and item json travel with the media item`() {
        val item = MediaItems.fromItem(track)
        assertEquals<Item?>(track, MediaItems.itemOf(item))
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, item.mediaMetadata.mediaType)
        assertEquals("Homework", item.mediaMetadata.albumTitle.toString())
        assertEquals("Daft Punk", item.mediaMetadata.artist.toString())
        assertEquals(430_000L, item.mediaMetadata.durationMs)
        assertEquals<Item?>(station, MediaItems.itemOf(MediaItems.fromItem(station)))
    }

    @Test
    fun `queued items get placeholders and need no source`() {
        val items = (0 until 200).map { i ->
            if (i % 2 == 0) track.copy(ref = MediaRef("youtube", "v$i")) else station.copy(ref = MediaRef("radiobrowser", "s$i"))
        }
        items.forEach { item ->
            val config = assertNotNull(MediaItems.forPlayer(MediaItems.fromItem(item)).localConfiguration)
            assertEquals("kairoify", config.uri.scheme)
            assertEquals(item.ref, MediaItems.refOfPlaceholder(config.uri))
        }
    }

    @Test
    fun `hls items are marked for the hls player`() {
        fun mimeOf(item: Item) = assertNotNull(MediaItems.forPlayer(MediaItems.fromItem(item)).localConfiguration).mimeType
        assertEquals(MimeTypes.APPLICATION_M3U8, mimeOf(station.copy(isHls = true)))
        assertEquals(MimeTypes.APPLICATION_M3U8, mimeOf(track.copy(isHls = true)))
        assertNull(mimeOf(track))
        assertNull(mimeOf(station))
    }

    @Test
    fun `placeholder survives refs with colons and slashes`() {
        val ref = MediaRef("bandcamp", "https://a.bandcamp.com/track/x?y=1&z=2")
        assertEquals(ref, MediaItems.refOfPlaceholder(MediaItems.placeholderUri(ref)))
        assertTrue(MediaItems.refOfPlaceholder(android.net.Uri.parse("https://a.bandcamp.com/track/x")) == null)
    }

    @Test
    fun `items without a Kairoify id are refused`() {
        assertNull(MediaItems.forPlayerOrNull(androidx.media3.common.MediaItem.Builder().setMediaId("").build()))
        assertNull(MediaItems.forPlayerOrNull(androidx.media3.common.MediaItem.Builder().setMediaId("nonsense").build()))
        assertNotNull(MediaItems.forPlayerOrNull(MediaItems.fromItem(track)))
    }
}
