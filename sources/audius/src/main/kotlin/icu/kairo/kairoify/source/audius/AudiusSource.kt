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
package icu.kairo.kairoify.source.audius

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class AudiusUser(val name: String? = null, val handle: String? = null)

@Serializable
internal data class AudiusTrack(
    val id: String? = null,
    val title: String? = null,
    val duration: Long? = null,
    @SerialName("is_streamable") val isStreamable: Boolean? = null,
    val permalink: String? = null,
    val artwork: Map<String, kotlinx.serialization.json.JsonElement>? = null,
    val user: AudiusUser? = null,
)

@Serializable
internal data class AudiusReply(val data: List<AudiusTrack> = emptyList())

/** Independent artists on Audius, an open music network: search, trending, full-length streams. */
class AudiusSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.audius.co/v1/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Audius")
    override val capabilities = setOf(Capability.SEARCH, Capability.HOME)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        Page(tracks(url("tracks/search").addQueryParameter("query", query).build()), next = null)

    override suspend fun home(): List<Shelf> {
        val trending = tracks(url("tracks/trending").addQueryParameter("time", "week").build()).take(HOME_SIZE)
        return if (trending.isEmpty()) emptyList() else listOf(Shelf("audius:trending", "Trending on Audius", trending))
    }

    /** The API's stream endpoint redirects to a freshly signed audio URL, so the endpoint itself is what's played. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream =
        ResolvedStream(url = url("tracks/${ref.itemId}/stream").build().toString(), isLive = false)

    private fun url(path: String): HttpUrl.Builder =
        baseUrl.newBuilder().addPathSegments(path).addQueryParameter("app_name", APP_NAME)

    private suspend fun tracks(url: HttpUrl): List<Track> {
        val reply = try {
            json.decodeFromString(AudiusReply.serializer(), client.fetchText(url, info.displayName))
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach Audius.", e)
        }
        return reply.data.filter { it.isStreamable == true }.mapNotNull(::track)
    }

    private fun track(t: AudiusTrack): Track? {
        val id = t.id?.takeIf(String::isNotBlank) ?: return null
        val title = t.title?.takeIf(String::isNotBlank) ?: return null
        val artist = t.user?.name?.takeIf(String::isNotBlank) ?: t.user?.handle
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = artist,
            artworkUrl = (t.artwork?.get("480x480") as? kotlinx.serialization.json.JsonPrimitive)?.content,
            artists = listOfNotNull(artist),
            durationMillis = t.duration?.times(1000),
            pageUrl = t.permalink?.let { "https://audius.co$it" },
        )
    }

    companion object {
        const val ID = "audius"
        private const val APP_NAME = "kairoify"
        private const val HOME_SIZE = 20
    }
}
