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
package icu.kairo.kairoify.source.ytdlp

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.ytdlp.MusicEngine
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
data class NtsAudioSource(val url: String? = null, val source: String? = null)

@Serializable
data class NtsArticle(val path: String? = null)

@Serializable
data class NtsEpisode(
    val title: String? = null,
    val name: String? = null,
    val article: NtsArticle? = null,
    @SerialName("audio_sources") val audioSources: List<NtsAudioSource> = emptyList(),
    val image: Map<String, String>? = null,
)

@Serializable
data class NtsNow(@SerialName("broadcast_title") val broadcastTitle: String? = null)

@Serializable
data class NtsChannel(@SerialName("channel_name") val channelName: String? = null, val now: NtsNow? = null)

@Serializable
internal data class NtsResults<T>(val results: List<T> = emptyList())

/** NTS Radio's public API: what's on air, and its archive of shows. */
class NtsApi(private val client: OkHttpClient, private val baseUrl: HttpUrl = "https://www.nts.live/api/v2/".toHttpUrl()) {
    suspend fun live(): List<NtsChannel> = get(baseUrl.resolve("live")!!, NtsResults.serializer(NtsChannel.serializer())).results

    suspend fun searchEpisodes(query: String): List<NtsEpisode> {
        // "search/episodes" ignores the query and lists the latest shows; "search" with version 2 filters properly.
        val url = baseUrl.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query).addQueryParameter("version", "2")
            .addQueryParameter("offset", "0").addQueryParameter("limit", "30")
            .addQueryParameter("types[]", "episode").build()
        return get(url, NtsResults.serializer(NtsEpisode.serializer())).results
    }

    /** One episode by its site path, e.g. "/shows/x/episodes/y". */
    suspend fun episode(path: String): NtsEpisode =
        get(baseUrl.newBuilder().addPathSegments(path.removePrefix("/")).build(), NtsEpisode.serializer())

    private suspend fun <T> get(url: HttpUrl, serializer: kotlinx.serialization.KSerializer<T>): T = try {
        lenientJson.decodeFromString(serializer, client.fetchText(url, "NTS Radio"))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach NTS Radio.", e)
    }
}

/**
 * NTS Radio: two live channels, and an archive of shows played from their SoundCloud or Mixcloud recordings (through
 * yt-dlp). Episodes with no recording there are left out.
 */
class NtsSource(private val api: NtsApi, private val engine: MusicEngine) : MusicSource {
    override val info = SourceInfo(ID, "NTS Radio")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 10_000L

    override suspend fun home(): List<Shelf> {
        val nowPlaying = runCatching { api.live() }.getOrDefault(emptyList()).associate { it.channelName to it.now?.broadcastTitle }
        val stations = LIVE.map { (channel, stream) ->
            Station(
                ref = MediaRef(ID, "live$channel"),
                title = "NTS $channel",
                subtitle = nowPlaying[channel]?.takeIf(String::isNotBlank) ?: "Live from London",
                homepage = "https://www.nts.live",
                streamUrl = stream,
            )
        }
        return listOf(Shelf("nts:live", "Live on NTS", stations))
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        Page(api.searchEpisodes(query).filter { playable(it) != null }.mapNotNull(::track), next = null)

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        LIVE[ref.itemId.removePrefix("live")]?.let { return ResolvedStream(url = it, isLive = true) }
        val source = playable(api.episode(ref.itemId)) ?: throw SourceException.NotFound("This NTS show has no recording to play.")
        // Mixcloud also offers DASH, which the player can't use, so ask for HLS there.
        val mode = if (source.source == "mixcloud") ResolveMode.HLS else ResolveMode.FAST
        return engine.resolve(source.url!!, mode).toResolvedStream()
    }

    private fun playable(episode: NtsEpisode): NtsAudioSource? =
        episode.audioSources.firstOrNull { it.source in PLAYABLE && !it.url.isNullOrBlank() }

    private fun track(e: NtsEpisode): Track? {
        val path = e.article?.path?.takeIf { it.startsWith("/") } ?: return null
        val title = (e.title ?: e.name)?.takeIf(String::isNotBlank) ?: return null
        return Track(
            ref = MediaRef(ID, path),
            title = title,
            subtitle = "NTS Radio",
            artworkUrl = e.image?.get("medium_large") ?: e.image?.get("large") ?: e.image?.get("medium"),
            artists = listOf("NTS Radio"),
            pageUrl = "https://www.nts.live$path",
            isHls = playable(e) != null,
        )
    }

    companion object {
        const val ID = "nts"
        // Both are extraction the providers don't authorize; included by the owner's choice (see AUDIO.md).
        private val PLAYABLE = setOf("soundcloud", "mixcloud")
        private val LIVE = linkedMapOf(
            "1" to "https://stream-relay-geo.ntslive.net/stream",
            "2" to "https://stream-relay-geo.ntslive.net/stream2",
        )
    }
}
