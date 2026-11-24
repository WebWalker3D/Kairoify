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
package icu.kairo.kairoify.source.openverse

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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class OvAudio(
    val id: String? = null,
    val title: String? = null,
    val creator: String? = null,
    val url: String? = null,
    val duration: Long? = null,
    @SerialName("license_url") val licenseUrl: String? = null,
    @SerialName("foreign_landing_url") val landingUrl: String? = null,
    val thumbnail: String? = null,
)

@Serializable
internal data class OvReply(val results: List<OvAudio> = emptyList())

/** Openverse: Creative Commons audio gathered from many sites, with each work's license. */
class OpenverseSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://api.openverse.org/v1/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Openverse")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val url = baseUrl.newBuilder().addPathSegment("audio").addPathSegment("")
            .addQueryParameter("q", query).addQueryParameter("page_size", "20").build()
        return Page(decode(url, OvReply.serializer()).results.mapNotNull(::track), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = baseUrl.newBuilder().addPathSegment("audio").addPathSegment(ref.itemId).addPathSegment("").build()
        val audio = decode(url, OvAudio.serializer())
        val stream = audio.url?.takeIf(String::isNotBlank) ?: throw SourceException.NotFound("That recording is no longer on Openverse.")
        return ResolvedStream(url = stream, isLive = false, license = licenseFromUrl(audio.licenseUrl))
    }

    private suspend fun <T> decode(url: HttpUrl, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach Openverse.", e)
    }

    private fun track(a: OvAudio): Track? {
        val id = a.id?.takeIf(String::isNotBlank) ?: return null
        val title = a.title?.takeIf(String::isNotBlank) ?: return null
        if (a.url.isNullOrBlank()) return null
        val creator = a.creator?.takeIf(String::isNotBlank)
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = creator,
            artworkUrl = a.thumbnail,
            artists = listOfNotNull(creator),
            durationMillis = a.duration?.takeIf { it > 0 },
            pageUrl = a.landingUrl,
            license = licenseFromUrl(a.licenseUrl),
        )
    }

    companion object {
        const val ID = "openverse"
    }
}
