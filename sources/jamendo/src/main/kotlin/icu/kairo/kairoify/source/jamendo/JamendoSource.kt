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
package icu.kairo.kairoify.source.jamendo

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.licenseFromUrl
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
internal data class JamendoHeaders(val status: String? = null)

@Serializable
internal data class JamendoTrack(
    val id: String? = null,
    val name: String? = null,
    val duration: Long? = null,
    @SerialName("artist_name") val artistName: String? = null,
    @SerialName("album_name") val albumName: String? = null,
    @SerialName("license_ccurl") val licenseUrl: String? = null,
    val audio: String? = null,
    val shareurl: String? = null,
    val image: String? = null,
)

@Serializable
internal data class JamendoReply(val headers: JamendoHeaders = JamendoHeaders(), val results: List<JamendoTrack> = emptyList())

/**
 * Creative Commons music from Jamendo. Its API needs a registered client ID, so the app only registers this source
 * when one was supplied at build time.
 */
class JamendoSource(
    private val client: OkHttpClient,
    private val clientId: String,
    private val baseUrl: HttpUrl = "https://api.jamendo.com/v3.0/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Jamendo")
    override val capabilities = setOf(Capability.SEARCH, Capability.HOME)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        Page(fetch(mapOf("search" to query, "limit" to "30")).mapNotNull(::track), next = null)

    override suspend fun home(): List<Shelf> {
        val picks = fetch(mapOf("order" to "popularity_week", "limit" to "20")).mapNotNull(::track)
        return if (picks.isEmpty()) emptyList() else listOf(Shelf("jamendo:picks", "Jamendo picks", picks))
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val track = fetch(mapOf("id" to ref.itemId)).firstOrNull { it.id == ref.itemId }
        val audio = track?.audio?.takeIf(String::isNotBlank) ?: throw SourceException.NotFound("That song is no longer on Jamendo.")
        return ResolvedStream(url = audio, isLive = false, license = licenseFromUrl(track.licenseUrl))
    }

    private suspend fun fetch(params: Map<String, String>): List<JamendoTrack> {
        val url = baseUrl.newBuilder().addPathSegment("tracks").addPathSegment("")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("format", "json")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val reply = try {
            json.decodeFromString(JamendoReply.serializer(), client.fetchText(url, info.displayName))
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach Jamendo.", e)
        }
        if (reply.headers.status == "failed") throw SourceException.Unavailable("Jamendo rejected Kairoify's client ID.")
        return reply.results
    }

    private fun track(t: JamendoTrack): Track? {
        val id = t.id?.takeIf(String::isNotBlank) ?: return null
        val title = t.name?.takeIf(String::isNotBlank) ?: return null
        val artist = t.artistName?.takeIf(String::isNotBlank)
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = artist,
            artworkUrl = t.image?.takeIf(String::isNotBlank),
            artists = listOfNotNull(artist),
            album = t.albumName?.takeIf(String::isNotBlank),
            durationMillis = t.duration?.takeIf { it > 0 }?.times(1000),
            pageUrl = t.shareurl,
            license = licenseFromUrl(t.licenseUrl),
        )
    }

    companion object {
        const val ID = "jamendo"
    }
}
