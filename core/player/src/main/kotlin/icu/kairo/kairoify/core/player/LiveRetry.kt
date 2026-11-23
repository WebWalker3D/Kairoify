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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Brings a live stream back after network trouble, waiting longer each time, and forgets everything when the item changes. */
class LiveRetry(private val scope: CoroutineScope, private val policy: LiveRetryPolicy = LiveRetryPolicy()) {
    private var attempt = 0
    private var pending: Job? = null

    /** Schedules [restart]; false when the policy gives up (or the error isn't one a retry can fix). */
    fun onError(errorCode: Int, restart: () -> Unit): Boolean {
        val wait = policy.delayFor(attempt, errorCode) ?: return false
        attempt++
        pending?.cancel()
        pending = scope.launch {
            delay(wait)
            restart()
        }
        return true
    }

    /** Playback works again: the next failure starts from the shortest wait. */
    fun onReady() {
        attempt = 0
    }

    /** The person paused, stopped or moved on: drop any scheduled restart. */
    fun reset() {
        pending?.cancel()
        pending = null
        attempt = 0
    }
}
