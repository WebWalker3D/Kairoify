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
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.ytdlp.MusicEngine
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/** The mainstream catalog: songs from YouTube Music, searched with ytmusicapi and played through yt-dlp. */
class YouTubeMusicSource(private val engine: MusicEngine) : MusicSource {
    override val info = SourceInfo(ID, "YouTube Music")
    override val capabilities = setOf(Capability.SEARCH, Capability.RELATED)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 15_000L

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val songs = engine.ytMusicSearch(query, PAGE_SIZE).decode<YtmSong>()
            .filter { it.resultType == "song" }
            .mapNotNull { song ->
                track(song.videoId, song.title, song.artists, song.album, song.durationSeconds?.times(1000), song.thumbnails)
            }
        return Page(songs, next = null)
    }

    override suspend fun related(ref: MediaRef): List<Item> =
        engine.ytMusicRadio(ref.itemId, RADIO_SIZE).decode<YtmWatchTrack>()
            .filter { it.videoId != ref.itemId }
            .mapNotNull { track(it.videoId, it.title, it.artists, it.album, parseLength(it.length), it.thumbnail) }

    /**
     * The JS-less client first: about a second and a half. If YouTube changed something it can't handle, the full
     * client runs YouTube's own JavaScript in QuickJS (about nine seconds). A song that is gone stays gone.
     */
    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = watchUrl(ref.itemId)
        return try {
            engine.resolve(url, ResolveMode.FAST)
        } catch (e: SourceException) {
            if (e is SourceException.NotFound || e is SourceException.Geoblocked || e is SourceException.RateLimited) throw e
            engine.resolve(url, ResolveMode.FULL)
        }.toResolvedStream()
    }

    private fun track(
        videoId: String?,
        title: String?,
        artists: List<YtmArtist>,
        album: YtmAlbum?,
        durationMillis: Long?,
        thumbnails: List<YtmThumb>,
    ): Track? {
        if (videoId.isNullOrBlank() || title.isNullOrBlank()) return null
        val names = artists.mapNotNull { it.name?.takeIf(String::isNotBlank) }
        return Track(
            ref = MediaRef(ID, videoId),
            title = title,
            subtitle = names.joinToString(", ").ifBlank { null },
            artworkUrl = thumbnails.lastOrNull()?.url?.replace(SIZE, "=w544-h544"),
            artists = names,
            album = album?.name,
            durationMillis = durationMillis,
            pageUrl = watchUrl(videoId),
        )
    }

    private inline fun <reified T> List<JsonObject>.decode(): List<T> =
        mapNotNull { runCatching { lenientJson.decodeFromJsonElement<T>(it) }.getOrNull() }

    companion object {
        const val ID = "youtube"
        private const val PAGE_SIZE = 20
        private const val RADIO_SIZE = 50
        private val SIZE = Regex("=w\\d+-h\\d+")

        fun watchUrl(videoId: String) = "https://music.youtube.com/watch?v=$videoId"
    }
}
