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
package icu.kairo.kairoify.source.hearthis

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.licenseFromUrl
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class HtUser(val username: String? = null)

@Serializable
internal data class HtTrack(
    val title: String? = null,
    val duration: String? = null,
    @SerialName("permalink_url") val permalinkUrl: String? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    val license: String? = null,
    val user: HtUser? = null,
)

/** hearthis.at: DJ sets, live recordings and tracks, with direct streams. */
class HearthisSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api-v2.hearthis.at/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "hearthis.at")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val url = baseUrl.newBuilder().addPathSegment("search")
            .addQueryParameter("t", query).addQueryParameter("type", "tracks")
            .addQueryParameter("page", "1").addQueryParameter("count", "30").build()
        return Page(decode(url, ListSerializer(HtTrack.serializer())).mapNotNull(::track), next = null)
    }

    /** The item id is "<user>/<track>", the track's path on hearthis.at; its API answers at the same path. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = baseUrl.newBuilder().addPathSegments(ref.itemId).addPathSegment("").build()
        val stream = decode(url, HtTrack.serializer()).streamUrl?.takeIf(String::isNotBlank)
            ?: throw SourceException.NotFound("That track is no longer on hearthis.at.")
        return ResolvedStream(url = stream, isLive = false)
    }

    private suspend fun <T> decode(url: HttpUrl, serializer: kotlinx.serialization.KSerializer<T>): T = try {
        json.decodeFromString(serializer, client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach hearthis.at.", e)
    }

    private fun track(t: HtTrack): Track? {
        val page = t.permalinkUrl?.takeIf { it.startsWith(SITE) } ?: return null
        val path = page.removePrefix(SITE).trim('/').takeIf { it.count { c -> c == '/' } == 1 } ?: return null
        val title = t.title?.takeIf(String::isNotBlank) ?: return null
        val artist = t.user?.username?.takeIf(String::isNotBlank)
        return Track(
            ref = MediaRef(ID, path),
            title = title,
            subtitle = artist,
            artworkUrl = t.artworkUrl?.takeIf(String::isNotBlank),
            artists = listOfNotNull(artist),
            durationMillis = t.duration?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.times(1000),
            pageUrl = page,
            license = licenseFromUrl(t.license),
        )
    }

    companion object {
        const val ID = "hearthis"
        private const val SITE = "https://hearthis.at/"
    }
}
