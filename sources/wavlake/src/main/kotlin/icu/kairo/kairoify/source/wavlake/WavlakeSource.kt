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
package icu.kairo.kairoify.source.wavlake

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class WlContent(
    val id: String? = null,
    val title: String? = null,
    val type: String? = null,
    val albumArtUrl: String? = null,
    val albumTitle: String? = null,
    val mediaUrl: String? = null,
    val artist: String? = null,
    val duration: Long? = null,
)

/** Wavlake: independent artists who publish their music free to stream, with optional listener tips. */
class WavlakeSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://wavlake.com/api/v1/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Wavlake")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val url = baseUrl.newBuilder().addPathSegments("content/search").addQueryParameter("term", query).build()
        return Page(contents(url).filter { it.type == "track" }.mapNotNull(::track), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = baseUrl.newBuilder().addPathSegments("content/track").addPathSegment(ref.itemId).build()
        val media = contents(url).firstNotNullOfOrNull { it.mediaUrl?.takeIf(String::isNotBlank) }
            ?: throw SourceException.NotFound("That track is no longer on Wavlake.")
        return ResolvedStream(url = media, isLive = false)
    }

    private suspend fun contents(url: HttpUrl): List<WlContent> = try {
        json.decodeFromString(ListSerializer(WlContent.serializer()), client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach Wavlake.", e)
    }

    private fun track(c: WlContent): Track? {
        val id = c.id?.takeIf(String::isNotBlank) ?: return null
        val title = c.title?.takeIf(String::isNotBlank) ?: return null
        if (c.mediaUrl.isNullOrBlank()) return null
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = c.artist,
            artworkUrl = c.albumArtUrl,
            artists = listOfNotNull(c.artist),
            album = c.albumTitle,
            durationMillis = c.duration?.takeIf { it > 0 }?.times(1000),
            pageUrl = "https://wavlake.com/track/$id",
        )
    }

    companion object {
        const val ID = "wavlake"
    }
}
