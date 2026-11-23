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

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The player every controller (notification, Bluetooth, Android Auto, the app) talks to. Stations behave like car
 * radio presets: ⏮ always goes to the previous station, because "restart from the beginning" means nothing on live radio.
 */
@OptIn(UnstableApi::class)
class PresetSkipPlayer(player: Player) : ForwardingPlayer(player) {
    override fun seekToPrevious() {
        val isStation = currentMediaItem?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION
        if (isStation && hasPreviousMediaItem()) seekToPreviousMediaItem() else super.seekToPrevious()
        resumeAfterFailure()
    }

    override fun seekToNext() {
        super.seekToNext()
        resumeAfterFailure()
    }

    override fun seekToNextMediaItem() {
        super.seekToNextMediaItem()
        resumeAfterFailure()
    }

    override fun seekToPreviousMediaItem() {
        super.seekToPreviousMediaItem()
        resumeAfterFailure()
    }

    /** After a failure the player sits idle; skipping to another station or song should play it, not show it silently. */
    private fun resumeAfterFailure() {
        if (playbackState == Player.STATE_IDLE && playerError != null) prepare()
    }
}
