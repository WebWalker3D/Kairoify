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

import androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveRetryTest {
    private val scope = TestScope()
    private val retry = LiveRetry(scope)
    private var restarts = 0
    private val net = ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

    @Test
    fun `restarts after the policy delay`() {
        assertTrue(retry.onError(net) { restarts++ })
        scope.advanceTimeBy(1_999)
        scope.runCurrent()
        assertEquals(0, restarts)
        scope.advanceTimeBy(2)
        scope.runCurrent()
        assertEquals(1, restarts)
    }

    @Test
    fun `reset cancels a pending restart`() {
        retry.onError(net) { restarts++ }
        scope.advanceTimeBy(1_000)
        retry.reset()
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertEquals(0, restarts)
    }

    @Test
    fun `gives up when the policy does`() {
        repeat(3) { assertTrue(retry.onError(net) { restarts++ }) }
        assertFalse(retry.onError(net) { restarts++ })
    }

    @Test
    fun `ready resets the attempts`() {
        retry.onError(net) { restarts++ }
        scope.advanceTimeBy(2_001)
        retry.onReady()
        retry.onError(net) { restarts++ }
        scope.advanceTimeBy(2_001)
        scope.runCurrent()
        assertEquals(2, restarts)
    }
}
