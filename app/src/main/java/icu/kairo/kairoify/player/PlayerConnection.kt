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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import icu.kairo.kairoify.core.model.MediaRef
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

/** Offered after a track picks up where it was left: "Start track over?" for a few seconds. */
data class ResumeOffer(val ref: MediaRef, val positionMs: Long, val id: Long)

/** One upcoming song in the queue, for the player's "Up next" list. */
data class UpNextEntry(val index: Int, val title: String, val subtitle: String?, val artworkUrl: String?)

/** A queue that is a station, e.g. "Free radio · Etta James"; [building] while songs are still being added. */
data class StationLabel(val title: String, val building: Boolean)

/** The app's handle on the playback service. Everything the UI shows about playback comes from [nowPlaying]. */
@OptIn(UnstableApi::class)
class PlayerConnection(private val context: Context, private val positions: ResumePositions? = null) {
    private val _resumeOffer = MutableStateFlow<ResumeOffer?>(null)

    /** Set when the track just started from where the person left it; the UI offers to start it over. */
    val resumeOffer: StateFlow<ResumeOffer?> = _resumeOffer.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var requested: Item? = null
    private var sessionError: String? = null

    /** Attach to the service so a session already playing (e.g. started from Android Auto) shows up. */
    fun connect() = withController { }

    private val _upNext = MutableStateFlow<List<UpNextEntry>>(emptyList())

    /** The next songs in the queue, in play order (shuffle included), up to [UP_NEXT_LIMIT]. */
    val upNext: StateFlow<List<UpNextEntry>> = _upNext.asStateFlow()

    private val _station = MutableStateFlow<StationLabel?>(null)

    /** Set while the queue is a station; cleared when anything else starts. */
    val station: StateFlow<StationLabel?> = _station.asStateFlow()

    /** Labels the queue started at [generation] as a station, unless something else has started since. */
    fun markStation(generation: Int, title: String, building: Boolean) {
        if (generation == queueGeneration) _station.value = StationLabel(title, building)
    }

    /** Jumps to a song in the queue (from "Up next"). */
    fun skipTo(index: Int) = withController {
        saveCurrent(it)
        it.seekToDefaultPosition(index)
    }

    /** Goes up every time a new queue starts (or playback stops), so late additions to an old queue are dropped. */
    var queueGeneration = 0
        private set

    fun play(item: Item) = playAll(listOf(item))

    /** Adds [items] to the end of the queue, unless another queue has started since [generation]. */
    fun append(generation: Int, items: List<Item>) = withController {
        if (generation == queueGeneration && items.isNotEmpty()) {
            it.addMediaItems(items.map(MediaItems::fromItem))
            publish(it)
        }
    }

    /** Plays [items] as the queue, from [startIndex] (or a random item when shuffling). */
    fun playAll(items: List<Item>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        queueGeneration++
        _station.value = null
        val request = queueRequest(items, startIndex, shuffle)
        withController {
            saveCurrent(it)
            val start = request.items[request.startIndex]
            requested = start
            sessionError = null
            val resumeAt = if (start is Station) null else positions?.positionFor(start.ref)
            _resumeOffer.value = resumeAt?.let { ms -> ResumeOffer(start.ref, ms, System.nanoTime()) }
            // Shuffle goes off, the queue is set, then shuffle goes on: the service orders the shuffle from the
            // start item when shuffle turns on, so every item plays.
            it.shuffleModeEnabled = false
            it.repeatMode = request.repeatMode
            it.setMediaItems(request.items.map(MediaItems::fromItem), request.startIndex, resumeAt ?: 0)
            it.shuffleModeEnabled = request.shuffle
            it.prepare()
            it.play()
            publish(it)
        }
    }

    fun skipNext() = withController {
        saveCurrent(it)
        it.seekToNext()
    }

    fun skipPrevious() = withController {
        saveCurrent(it)
        it.seekToPrevious()
    }

    /** "Start track over?": back to the beginning, and forget the saved spot. */
    fun startOver() = withController {
        _resumeOffer.value?.let { offer -> positions?.forget(offer.ref) }
        _resumeOffer.value = null
        it.seekTo(0)
    }

    fun dismissResumeOffer() {
        _resumeOffer.value = null
    }

    /** Remembers where the current (non-live) item is, so playing it again picks up there. */
    private fun saveCurrent(controller: MediaController) {
        val current = _nowPlaying.value ?: return
        if (current.isLive || controller.currentMediaItem == null) return
        val duration = controller.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        positions?.remember(current.ref, controller.currentPosition, duration)
    }

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

    fun stop() {
        queueGeneration++
        _station.value = null
        stopNow()
    }

    private fun stopNow() = withController {
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
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    // The previous item played to its end: next time it starts from the top.
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) _nowPlaying.value?.let { positions?.forget(it.ref) }
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && !player.isPlaying) {
                        if (player.playbackState == Player.STATE_ENDED) {
                            _nowPlaying.value?.let { positions?.forget(it.ref) }
                        } else {
                            saveCurrent(controller)
                        }
                    }
                    if (player.playbackState == Player.STATE_READY) sessionError = null
                    publish(controller)
                }
            })
            publish(controller)
        }, ContextCompat.getMainExecutor(context))
        return future
    }

    private fun upNextOf(controller: MediaController): List<UpNextEntry> {
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = mutableListOf<UpNextEntry>()
        var index = controller.currentMediaItemIndex
        while (out.size < UP_NEXT_LIMIT) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, controller.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) break
            val meta = controller.getMediaItemAt(index).mediaMetadata
            out += UpNextEntry(index, meta.title?.toString().orEmpty(), meta.artist?.toString(), meta.artworkUri?.toString())
        }
        return out
    }

    private fun publish(controller: MediaController) {
        _upNext.value = upNextOf(controller)
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

private const val UP_NEXT_LIMIT = 50
