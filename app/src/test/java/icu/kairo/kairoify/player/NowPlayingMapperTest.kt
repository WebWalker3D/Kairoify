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
package icu.kairo.kairoify.player

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.player.MediaItems
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class NowPlayingMapperTest {
    private val station = Station(
        ref = MediaRef("radiobrowser", "u1"), title = "Jazz FM", subtitle = "Germany · jazz",
        artworkUrl = "https://x/f.png", countryCode = "DE", tags = listOf("jazz"), codec = null,
        bitrateKbps = null, homepage = "https://jazz.fm", streamUrl = "http://a/stream", isHls = false,
    )
    private val item = MediaItems.fromItem(station)

    @Test
    fun `null item gives null`() {
        assertNull(nowPlayingOf(null, item.mediaMetadata, isPlaying = false, playbackState = Player.STATE_IDLE, error = null))
    }

    @Test
    fun `live station maps title subtitle and live flag`() {
        val np = assertNotNull(nowPlayingOf(item, item.mediaMetadata, isPlaying = true, playbackState = Player.STATE_READY, error = null))
        assertEquals(station.ref, np.ref)
        assertEquals("Jazz FM", np.title)
        assertEquals("Germany · jazz", np.subtitle)
        assertEquals("https://x/f.png", np.artworkUrl)
        assertEquals("https://jazz.fm", np.homepage)
        assertTrue(np.isLive)
        assertTrue(np.isPlaying)
        assertNull(np.errorMessage)
    }

    @Test
    fun `icy artist shows as subtitle`() {
        val icy = item.mediaMetadata.buildUpon().setArtist("Miles Davis - So What").build()
        assertEquals("Miles Davis - So What", nowPlayingOf(item, icy, true, Player.STATE_READY, null)?.subtitle)
    }

    @Test
    fun `buffering state sets isBuffering`() {
        assertEquals(true, nowPlayingOf(item, item.mediaMetadata, false, Player.STATE_BUFFERING, null)?.isBuffering)
    }

    @Test
    fun `bad http status error maps to station not broadcasting copy`() {
        val error = PlaybackException("boom", null, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)
        assertEquals(
            "This station isn't broadcasting right now.",
            nowPlayingOf(item, item.mediaMetadata, false, Player.STATE_IDLE, error)?.errorMessage,
        )
    }
}
