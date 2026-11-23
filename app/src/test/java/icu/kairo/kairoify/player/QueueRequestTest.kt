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

import androidx.media3.common.Player
import icu.kairo.kairoify.testing.station
import icu.kairo.kairoify.testing.track
import org.junit.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueRequestTest {
    private val stations = listOf(station("a"), station("b"), station("c"))
    private val songs = (1..10).map { track("$it") }

    @Test
    fun `plays from the tapped item`() {
        val request = queueRequest(songs, startIndex = 2, shuffle = false)
        assertEquals(2, request.startIndex)
        assertFalse(request.shuffle)
        assertEquals(songs, request.items)
    }

    @Test
    fun `shuffle starts anywhere`() {
        val request = queueRequest(songs, startIndex = 0, shuffle = true, random = Random(42))
        assertEquals(Random(42).nextInt(songs.size), request.startIndex)
        assertTrue(request.shuffle)
    }

    @Test
    fun `station-only queues wrap like presets`() {
        assertEquals(Player.REPEAT_MODE_ALL, queueRequest(stations, 0, shuffle = false).repeatMode)
    }

    @Test
    fun `mixed or single queues do not repeat`() {
        assertEquals(Player.REPEAT_MODE_OFF, queueRequest(listOf(station("a"), track("1")), 0, shuffle = false).repeatMode)
        assertEquals(Player.REPEAT_MODE_OFF, queueRequest(listOf(station("a")), 0, shuffle = false).repeatMode)
    }

    @Test
    fun `empty queues are refused`() {
        assertFailsWith<IllegalArgumentException> { queueRequest(emptyList(), 0, shuffle = false) }
    }
}
