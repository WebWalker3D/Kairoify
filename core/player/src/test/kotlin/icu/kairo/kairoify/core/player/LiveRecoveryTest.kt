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

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import icu.kairo.kairoify.core.source.SourceException
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveRecoveryTest {
    private class Recorder(override val isLive: Boolean) : RestartTarget {
        val calls = mutableListOf<String>()
        override fun seekToDefaultPosition() { calls += "seek" }
        override fun prepare() { calls += "prepare" }
    }

    private val network = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

    @Test
    fun `live item seeks to live edge before prepare`() {
        val target = Recorder(isLive = true)
        restart(target)
        assertEquals(listOf("seek", "prepare"), target.calls)
    }

    @Test
    fun `non-live item only prepares`() {
        val target = Recorder(isLive = false)
        restart(target)
        assertEquals(listOf("prepare"), target.calls)
    }

    @Test
    fun `resumes live item stopped by network error when network returns`() {
        assertTrue(shouldResumeOnNetworkBack(isLive = true, playWhenReady = true, playbackState = Player.STATE_IDLE, errorCode = network))
        assertTrue(
            shouldResumeOnNetworkBack(true, true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW),
        )
    }

    @Test
    fun `does not resume when the listener paused`() {
        assertFalse(shouldResumeOnNetworkBack(isLive = true, playWhenReady = false, playbackState = Player.STATE_IDLE, errorCode = network))
    }

    @Test
    fun `does not resume a dead station`() {
        assertFalse(shouldResumeOnNetworkBack(true, true, Player.STATE_IDLE, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
    }

    @Test
    fun `does not resume non-live items or players that are not stopped`() {
        assertFalse(shouldResumeOnNetworkBack(isLive = false, playWhenReady = true, playbackState = Player.STATE_IDLE, errorCode = network))
        assertFalse(shouldResumeOnNetworkBack(isLive = true, playWhenReady = true, playbackState = Player.STATE_READY, errorCode = null))
        assertFalse(shouldResumeOnNetworkBack(isLive = true, playWhenReady = true, playbackState = Player.STATE_IDLE, errorCode = null))
    }

    @Test
    fun `not found uses the source's own words`() {
        assertEquals(
            "That station is no longer listed",
            resolveFailureMessage(SourceException.NotFound("That station is no longer listed"), "Radio Browser"),
        )
    }

    @Test
    fun `rate limited uses the source's own words`() {
        assertEquals(
            "Radio Browser is busy. Try again in a minute.",
            resolveFailureMessage(SourceException.RateLimited("Radio Browser is busy. Try again in a minute."), "Radio Browser"),
        )
    }

    @Test
    fun `unreachable source says couldn't reach it`() {
        assertEquals("Couldn't reach Radio Browser. Try again.", resolveFailureMessage(SourceException.Unavailable("x"), "Radio Browser"))
        assertEquals("Couldn't reach Radio Browser. Try again.", resolveFailureMessage(IOException("socket"), "Radio Browser"))
    }
}
