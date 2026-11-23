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

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.License
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.player.MediaItems
import icu.kairo.kairoify.core.player.playbackErrorMessage

data class NowPlaying(
    val ref: MediaRef,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val homepage: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val isLive: Boolean,
    val errorMessage: String?,
    /** The full item playing, when the media item carries it. */
    val item: Item? = null,
    /** Length of a song; null for live radio or when unknown. */
    val durationMs: Long? = null,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    /** Creative Commons or public-domain license of the song, shown with the artist. */
    val license: License? = null,
)

/** The queue-level facts the controller knows about. */
data class QueueState(
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = C.TIME_UNSET,
)

/** What the mini-player and Now Playing show, from the controller's current state. */
fun nowPlayingOf(
    item: MediaItem?,
    metadata: MediaMetadata,
    isPlaying: Boolean,
    playbackState: Int,
    error: PlaybackException?,
    queue: QueueState = QueueState(),
): NowPlaying? {
    if (item == null) return null
    val fallback = item.mediaMetadata
    val isLive = (metadata.mediaType ?: fallback.mediaType) == MediaMetadata.MEDIA_TYPE_RADIO_STATION
    return NowPlaying(
        ref = MediaItems.refOf(item),
        title = (metadata.title ?: fallback.title)?.toString().orEmpty(),
        subtitle = (metadata.artist ?: fallback.artist)?.toString(),
        artworkUrl = (metadata.artworkUri ?: fallback.artworkUri)?.toString(),
        homepage = (metadata.extras ?: fallback.extras)?.getString(MediaItems.EXTRA_HOMEPAGE),
        isPlaying = isPlaying,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        isLive = isLive,
        errorMessage = error?.let { playbackErrorMessage(it.errorCode, isLive) },
        item = MediaItems.itemOf(item),
        license = (MediaItems.itemOf(item) as? Track)?.license,
        durationMs = queue.durationMs.takeIf { !isLive && it != C.TIME_UNSET && it > 0 },
        hasPrevious = queue.hasPrevious,
        hasNext = queue.hasNext,
        shuffle = queue.shuffle,
        repeatMode = queue.repeatMode,
    )
}
