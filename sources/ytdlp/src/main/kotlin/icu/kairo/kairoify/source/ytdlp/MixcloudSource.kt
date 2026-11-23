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
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.ytdlp.MusicEngine
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
data class McUser(val name: String? = null, val username: String? = null)

@Serializable
data class McCloudcast(
    val key: String? = null,
    val name: String? = null,
    val url: String? = null,
    @SerialName("audio_length") val audioLength: Long? = null,
    val pictures: Map<String, String> = emptyMap(),
    val user: McUser? = null,
)

@Serializable
internal data class McReply(val data: List<McCloudcast> = emptyList())

/** Mixcloud's public search (no key). Playback goes through yt-dlp. */
class MixcloudSearchApi(private val client: OkHttpClient, private val baseUrl: HttpUrl = "https://api.mixcloud.com/".toHttpUrl()) {
    suspend fun search(query: String): List<McCloudcast> {
        val url = baseUrl.newBuilder().addPathSegment("search").addPathSegment("")
            .addQueryParameter("q", query)
            .addQueryParameter("type", "cloudcast")
            .addQueryParameter("limit", "30")
            .build()
        return try {
            lenientJson.decodeFromString(McReply.serializer(), client.fetchText(url, "Mixcloud")).data
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach Mixcloud.", e)
        }
    }
}

/** DJ mixes and radio shows from Mixcloud. Mixcloud also offers DASH, so playback asks yt-dlp for HLS. */
class MixcloudSource(private val api: MixcloudSearchApi, private val engine: MusicEngine) : MusicSource {
    override val info = SourceInfo(ID, "Mixcloud")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 10_000L

    override suspend fun search(query: String, page: PageToken?): Page<Item> = Page(api.search(query).mapNotNull(::track), next = null)

    override suspend fun resolve(ref: MediaRef): ResolvedStream =
        engine.resolve(SITE + ref.itemId.removePrefix("/"), ResolveMode.HLS).toResolvedStream()

    private fun track(c: McCloudcast): Track? {
        val key = c.key?.takeIf { it.startsWith("/") } ?: return null
        val title = c.name?.takeIf(String::isNotBlank) ?: return null
        val artist = c.user?.name?.takeIf(String::isNotBlank) ?: c.user?.username
        return Track(
            ref = MediaRef(ID, key),
            title = title,
            subtitle = artist,
            artworkUrl = c.pictures["640wx640h"] ?: c.pictures["large"] ?: c.pictures["medium"],
            artists = listOfNotNull(artist),
            durationMillis = c.audioLength?.takeIf { it > 0 }?.times(1000),
            pageUrl = c.url ?: (SITE + key.removePrefix("/")),
            isHls = true,
        )
    }

    companion object {
        const val ID = "mixcloud"
        private const val SITE = "https://www.mixcloud.com/"
    }
}
