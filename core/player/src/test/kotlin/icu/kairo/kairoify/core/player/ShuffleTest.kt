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
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.random.Random
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ShuffleTest {
    private val exo = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).setLooper(Looper.getMainLooper()).build()

    @After
    fun release() = exo.release()

    @Test
    fun `shuffledFrom puts the start first and keeps every index`() {
        val order = shuffledFrom(start = 7, size = 10, random = Random(3))
        assertEquals(7, order.first())
        assertEquals((0 until 10).toSet(), order.toSet())
        assertEquals(10, order.size)
    }

    @Test
    fun `shuffle plays every item starting from the current one`() {
        exo.setMediaItems((0 until 10).map { MediaItems.forPlayer(MediaItems.fromItem(Track(MediaRef("youtube", "v$it"), "Song $it"))) })
        exo.seekTo(7, 0)
        exo.shuffleModeEnabled = true
        exo.shuffleFromCurrent(Random(5))
        val timeline = exo.currentTimeline
        val played = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            played += index
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }
        assertEquals(7, played.first())
        assertEquals((0 until 10).toSet(), played.toSet())
        assertEquals(10, played.size)
    }
}
