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
import androidx.media3.datasource.ResolvingDataSource
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
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Background playback: notification, lock screen, Bluetooth and Android Auto all talk to this session. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val liveRetry = LiveRetry(scope)
    private lateinit var deps: PlayerDependencies
    private lateinit var player: ExoPlayer
    private lateinit var tree: LibraryTree
    private lateinit var resolver: StreamResolver
    private var session: MediaLibrarySession? = null
    private var lastReportedId: String? = null
    private var refreshedMediaId: String? = null
    private val networkWatcher = NetworkWatcher()

    override fun onCreate() {
        super.onCreate()
        deps = (application as PlayerDependencies.Provider).playerDependencies
        tree = LibraryTree(deps.sourceRegistry) { deps.favoriteItems() }
        resolver = StreamResolver(deps.sourceRegistry)
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(deps.userAgent)
            .setAllowCrossProtocolRedirects(true)
        // Streams are resolved when the player first reads from them, on its loading thread, so a queue of any
        // length starts at once and the next item is resolved while the current one plays.
        val streams = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec ->
            resolveDataSpec(spec) { ref -> runBlocking { resolver.resolve(ref) } }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(streams).setLoadErrorHandlingPolicy(ResolveAwareLoadErrorPolicy()))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(PlayerEvents())
        session = MediaLibrarySession.Builder(this, PresetSkipPlayer(player), SessionCallback()).build()
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
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems.map(::playable).toMutableList())

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // A favorite picked in a car browser plays the whole Favorites folder from there, like radio presets.
            val favoritesQueue = mediaItems.singleOrNull()?.let { tree.queueFor(it.mediaId) }
            val result = if (favoritesQueue != null) {
                MediaSession.MediaItemsWithStartPosition(favoritesQueue.first.map(::playable), favoritesQueue.second, startPositionMs)
            } else {
                MediaSession.MediaItemsWithStartPosition(mediaItems.map(::playable), startIndex, startPositionMs)
            }
            return Futures.immediateFuture(result)
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
            liveRetry.reset()
            refreshedMediaId = null
            val id = mediaItem?.mediaId ?: return
            if (id == lastReportedId) return
            lastReportedId = id
            scope.launch { deps.sourceRegistry.reportPlay(MediaRef.decode(id)) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) liveRetry.reset()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) liveRetry.onReady()
            if (player.mediaItemCount == 0) lastReportedId = null
        }

        override fun onPlayerError(error: PlaybackException) {
            val item = player.currentMediaItem ?: return
            val ref = MediaItems.refOf(item)
            val failure = error.resolveFailure()?.reason
            failure?.let { tellControllers(resolveFailureMessage(it, sourceName(ref))) }
            if (item.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION) {
                // A station that is gone stays gone; only network-type trouble is worth retrying.
                if (failure == null || failure is SourceException.Unavailable) {
                    liveRetry.onError(error.errorCode) {
                        player.restartCurrent()
                        player.play()
                    }
                }
                return
            }
            val refreshed = refreshedMediaId == item.mediaId
            when (trackRecovery(error.errorCode, failure, refreshed, player.hasNextMediaItem())) {
                Recovery.REFRESH_URL -> {
                    refreshedMediaId = item.mediaId
                    resolver.invalidate(ref)
                    player.prepare()
                }
                Recovery.SKIP -> {
                    val why = failure?.let { resolveFailureMessage(it, sourceName(ref)) } ?: playbackErrorMessage(error.errorCode, isLive = false)
                    tellControllers("Skipped “${item.mediaMetadata.title}”: $why")
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
                Recovery.STOP -> Unit
            }
        }
    }

    /** Fills in metadata browsers left out, then swaps the stream for a placeholder resolved at play time. */
    private fun playable(requested: MediaItem): MediaItem {
        val item = if (requested.mediaMetadata.title == null) tree.item(requested.mediaId) ?: requested else requested
        return MediaItems.forPlayer(item)
    }

    private fun tellControllers(message: String) {
        val current = session ?: return
        current.connectedControllers.forEach { current.sendError(it, SessionError(SessionError.ERROR_IO, message)) }
    }

    /** Brings a live stream back by itself when the phone finds a working network again (tunnels, lifts, dead zones). */
    private inner class NetworkWatcher : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return
            scope.launch {
                val live = player.currentMediaItem?.mediaMetadata?.mediaType == MediaMetadata.MEDIA_TYPE_RADIO_STATION
                if (shouldResumeOnNetworkBack(live, player.playWhenReady, player.playbackState, player.playerError?.errorCode)) {
                    liveRetry.reset()
                    player.restartCurrent()
                }
            }
        }
    }

    private fun sourceName(ref: MediaRef): String =
        deps.sourceRegistry.sources.firstOrNull { it.info.id == ref.sourceId }?.info?.displayName ?: "that service"
}
