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

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import icu.kairo.kairoify.core.model.MediaRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/** Background playback: notification, lock screen, Bluetooth and Android Auto all talk to this session. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val retryPolicy = LiveRetryPolicy()
    private lateinit var deps: PlayerDependencies
    private lateinit var player: ExoPlayer
    private lateinit var tree: LibraryTree
    private var session: MediaLibrarySession? = null
    private var retryAttempt = 0
    private var retryJob: Job? = null
    private var lastReportedId: String? = null
    private val networkWatcher = NetworkWatcher()

    override fun onCreate() {
        super.onCreate()
        deps = (application as PlayerDependencies.Provider).playerDependencies
        tree = LibraryTree(deps.sourceRegistry)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(deps.userAgent)
            .setAllowCrossProtocolRedirects(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(PlayerEvents())
        session = MediaLibrarySession.Builder(this, player, SessionCallback()).build()
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkWatcher)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkWatcher) }
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            resolveAll(mediaSession, controller, mediaItems, replacing = false)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            MediaSession.MediaItemsWithStartPosition(
                resolveAll(mediaSession, controller, mediaItems, replacing = true),
                startIndex,
                startPositionMs,
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(LibraryTree.root(), params))

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            LibraryResult.ofItemList(tree.children(parentId), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(
            tree.item(mediaId)?.let { LibraryResult.ofItem(it, null) } ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE),
        )
    }

    private inner class PlayerEvents : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            val icy = (0 until metadata.length()).map { metadata[it] }.filterIsInstance<IcyInfo>().firstOrNull() ?: return
            val current = player.currentMediaItem ?: return
            val updated = withIcyTitle(current.mediaMetadata, icy.title) ?: return
            player.replaceMediaItem(player.currentMediaItemIndex, current.buildUpon().setMediaMetadata(updated).build())
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val id = mediaItem?.mediaId ?: return
            if (id == lastReportedId) return
            lastReportedId = id
            retryAttempt = 0
            scope.launch { deps.sourceRegistry.reportPlay(MediaRef.decode(id)) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retryAttempt = 0
            if (player.mediaItemCount == 0) lastReportedId = null
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            if (item.mediaMetadata.mediaType != MediaMetadata.MEDIA_TYPE_RADIO_STATION) return
            val wait = retryPolicy.delayFor(retryAttempt, error.errorCode) ?: return
            retryAttempt++
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(wait)
                player.restartCurrent()
                player.play()
            }
        }
    }

    /**
     * Resolves every item to a playable stream. If one fails while replacing the queue, the old item stops too,
     * so the screen and the speaker never disagree about what is playing.
     */
    private suspend fun resolveAll(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        replacing: Boolean,
    ): MutableList<MediaItem> = mediaItems.map { requested ->
        val ref = MediaItems.refOf(requested)
        val item = if (requested.mediaMetadata.title == null) tree.item(requested.mediaId) ?: requested else requested
        try {
            MediaItems.withStream(item, deps.sourceRegistry.resolve(ref))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (replacing) {
                player.stop()
                player.clearMediaItems()
            }
            mediaSession.sendError(controller, SessionError(SessionError.ERROR_IO, resolveFailureMessage(e, sourceName(ref))))
            throw e
        }
    }.toMutableList()

    /** Brings a live stream back by itself when the phone finds a working network again (tunnels, lifts, dead zones). */
    private inner class NetworkWatcher : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
            scope.launch {
                val live = player.currentMediaItem?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION
                if (shouldResumeOnNetworkBack(live, player.playWhenReady, player.playbackState, player.playerError?.errorCode)) {
                    retryJob?.cancel()
                    retryAttempt = 0
                    player.restartCurrent()
                }
            }
        }
    }

    private fun sourceName(ref: MediaRef): String =
        deps.sourceRegistry.sources.firstOrNull { it.info.id == ref.sourceId }?.info?.displayName ?: "that service"
}
