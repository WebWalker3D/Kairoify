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

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.player.MediaItems
import icu.kairo.kairoify.core.player.PlaybackService
import icu.kairo.kairoify.core.player.restartCurrent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The app's handle on the playback service. Everything the UI shows about playback comes from [nowPlaying]. */
@OptIn(UnstableApi::class)
class PlayerConnection(private val context: Context) {
    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var requested: Item? = null
    private var sessionError: String? = null

    /** Attach to the service so a session already playing (e.g. started from Android Auto) shows up. */
    fun connect() = withController { }

    fun play(item: Item) = playAll(listOf(item))

    /** Plays [items] as the queue, from [startIndex] (or a random item when shuffling). */
    fun playAll(items: List<Item>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        val request = queueRequest(items, startIndex, shuffle)
        withController {
            requested = request.items[request.startIndex]
            sessionError = null
            it.shuffleModeEnabled = request.shuffle
            it.repeatMode = request.repeatMode
            it.setMediaItems(request.items.map(MediaItems::fromItem), request.startIndex, 0)
            it.prepare()
            it.play()
            publish(it)
        }
    }

    fun skipNext() = withController { it.seekToNext() }

    fun skipPrevious() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    /** Off → all → one → off. */
    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Where playback is in the current item, for the seek bar; 0 before the controller connects. */
    fun position(): Long = controllerFuture?.takeIf { it.isDone }?.get()?.currentPosition ?: 0

    fun togglePlayPause() = withController {
        if (it.isPlaying) {
            it.pause()
        } else {
            if (it.playbackState == Player.STATE_IDLE) it.restartCurrent()
            it.play()
        }
    }

    fun stop() = withController {
        requested = null
        sessionError = null
        it.stop()
        it.clearMediaItems()
        publish(it)
    }

    private fun withController(block: (MediaController) -> Unit) {
        val future = controllerFuture ?: buildController().also { controllerFuture = it }
        future.addListener({ block(future.get()) }, ContextCompat.getMainExecutor(context))
    }

    private fun buildController(): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onError(controller: MediaController, sessionError: SessionError) {
                    this@PlayerConnection.sessionError = sessionError.message
                    publish(controller)
                }
            })
            .buildAsync()
        future.addListener({
            val controller = future.get()
            controller.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (player.playbackState == Player.STATE_READY) sessionError = null
                    publish(controller)
                }
            })
            publish(controller)
        }, ContextCompat.getMainExecutor(context))
        return future
    }

    private fun publish(controller: MediaController) {
        val current = nowPlayingOf(
            controller.currentMediaItem,
            controller.mediaMetadata,
            controller.isPlaying,
            controller.playbackState,
            controller.playerError,
            QueueState(
                hasPrevious = controller.hasPreviousMediaItem(),
                hasNext = controller.hasNextMediaItem(),
                shuffle = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                durationMs = controller.duration,
            ),
        )
        val error = sessionError
        val pending = requested
        _nowPlaying.value = when {
            current != null && error != null -> current.copy(errorMessage = error, isBuffering = false)
            current != null -> current
            error != null && pending != null -> NowPlaying(
                ref = pending.ref, title = pending.title, subtitle = pending.subtitle, artworkUrl = pending.artworkUrl,
                homepage = (pending as? Station)?.homepage, isPlaying = false, isBuffering = false,
                isLive = pending is Station, errorMessage = error,
            )
            else -> null
        }
    }
}
