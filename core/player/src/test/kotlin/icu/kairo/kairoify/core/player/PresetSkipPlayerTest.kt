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

import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import org.robolectric.RuntimeEnvironment
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class PresetSkipPlayerTest {
    private val exo = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setLooper(Looper.getMainLooper()).build()
    private val player = PresetSkipPlayer(exo)

    @After
    fun release() = exo.release()

    private fun queue(vararg items: Item) =
        exo.setMediaItems(items.map { MediaItems.forPlayer(MediaItems.fromItem(it)) })

    private fun station(id: String) = Station(MediaRef("radiobrowser", id), "Station $id", streamUrl = "http://x/$id")

    private fun track(id: String) = Track(MediaRef("youtube", id), "Song $id")

    @Test
    fun `previous on a station goes to the previous station`() {
        queue(station("a"), station("b"))
        exo.seekTo(1, 10_000)
        player.seekToPrevious()
        assertEquals(0, exo.currentMediaItemIndex)
    }

    @Test
    fun `previous on a song past three seconds restarts it`() {
        queue(track("a"), track("b"))
        exo.seekTo(1, 10_000)
        player.seekToPrevious()
        assertEquals(1, exo.currentMediaItemIndex)
        assertEquals(0, exo.currentPosition)
    }
}
