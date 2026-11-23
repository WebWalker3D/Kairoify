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
package icu.kairo.kairoify.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemJsonTest {
    private val station = Station(
        ref = MediaRef("radiobrowser", "uuid-1"),
        title = "Jazz FM",
        subtitle = "London",
        artworkUrl = "https://x/a.png",
        countryCode = "GB",
        tags = listOf("jazz"),
        codec = "MP3",
        bitrateKbps = 128,
        homepage = "https://jazz.fm",
        streamUrl = "http://jazz/stream",
        isHls = false,
    )

    private val track = Track(
        ref = MediaRef("soundcloud", "artist/song"),
        title = "Song",
        subtitle = "Artist",
        artworkUrl = "https://x/t500x500.jpg",
        artists = listOf("Artist", "Guest"),
        album = "Album",
        durationMillis = 245_000,
        pageUrl = "https://soundcloud.com/artist/song",
        isHls = true,
    )

    @Test
    fun `station round-trips`() = assertEquals<Item?>(station, ItemJson.decode(ItemJson.encode(station)))

    @Test
    fun `track round-trips`() = assertEquals<Item?>(track, ItemJson.decode(ItemJson.encode(track)))

    @Test
    fun `encoded item names its type`() = assertTrue(ItemJson.encode(track).contains("\"type\":\"track\""))

    @Test
    fun `unknown fields are ignored`() {
        val item = ItemJson.decode("""{"type":"track","ref":{"sourceId":"youtube","itemId":"abc"},"title":"T","future":1}""")
        assertEquals<Item?>(Track(MediaRef("youtube", "abc"), "T"), item)
    }

    @Test
    fun `unknown type decodes to null`() =
        assertNull(ItemJson.decode("""{"type":"podcast","ref":{"sourceId":"x","itemId":"1"},"title":"P"}"""))

    @Test
    fun `garbage decodes to null`() = assertNull(ItemJson.decode("not json"))

    @Test
    fun `kinds`() {
        assertEquals(ItemKind.STATION, station.kind)
        assertEquals(ItemKind.SONG, track.kind)
    }

    private val album = Album(
        ref = MediaRef("archive", "gd77-05-08"), title = "Live at Barton Hall", subtitle = "Grateful Dead",
        artworkUrl = "https://archive.org/services/img/gd77-05-08", artists = listOf("Grateful Dead"), year = "1977",
        trackCount = 20, pageUrl = "https://archive.org/details/gd77-05-08", license = License("Public domain", null),
    )

    @Test
    fun `album round-trips`() = assertEquals<Item?>(album, ItemJson.decode(ItemJson.encode(album)))

    @Test
    fun `old tracks without a license still decode`() {
        val item = ItemJson.decode("""{"type":"track","ref":{"sourceId":"youtube","itemId":"abc"},"title":"T","artists":["A"],"isHls":false}""")
        assertEquals<Item?>(Track(MediaRef("youtube", "abc"), "T", artists = listOf("A")), item)
    }

    @Test
    fun `albums are their own kind`() = assertEquals(ItemKind.ALBUM, album.kind)
}
