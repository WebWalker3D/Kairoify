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
import androidx.media3.common.Player
import icu.kairo.kairoify.core.source.SourceException

/** The part of a player a restart needs. Lets the restart rule be tested without a real player. */
interface RestartTarget {
    val isLive: Boolean
    fun seekToDefaultPosition()
    fun prepare()
}

/**
 * Restarts a stopped item. Live streams jump back to the live edge first; re-preparing at a stale position
 * fails again with "behind live window" on HLS radio.
 */
fun restart(target: RestartTarget) {
    if (target.isLive) target.seekToDefaultPosition()
    target.prepare()
}

fun Player.restartCurrent() = restart(
    object : RestartTarget {
        override val isLive = currentMediaItem?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION
        override fun seekToDefaultPosition() = this@restartCurrent.seekToDefaultPosition()
        override fun prepare() = this@restartCurrent.prepare()
    },
)

/** When the phone gets a working network back, should a live stream that died on the way come back by itself? */
fun shouldResumeOnNetworkBack(isLive: Boolean, playWhenReady: Boolean, playbackState: Int, errorCode: Int?): Boolean =
    isLive && playWhenReady && playbackState == Player.STATE_IDLE && errorCode != null && LiveRetryPolicy.isNetworkError(errorCode)

/** Plain words for a failed resolve. The source's own message when it says something specific. */
fun resolveFailureMessage(error: Throwable, sourceName: String): String = when (error) {
    is SourceException.NotFound,
    is SourceException.RateLimited,
    is SourceException.Geoblocked,
    is SourceException.NeedsUpdate,
    is SourceException.Unplayable,
    -> error.message ?: "Couldn't reach $sourceName. Try again."
    else -> "Couldn't reach $sourceName. Try again."
}
