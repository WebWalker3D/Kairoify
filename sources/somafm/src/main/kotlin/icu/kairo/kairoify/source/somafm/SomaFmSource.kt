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
package icu.kairo.kairoify.source.somafm

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class SomaChannel(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val largeimage: String? = null,
    val listeners: String? = null,
)

@Serializable
internal data class SomaReply(val channels: List<SomaChannel> = emptyList())

/** SomaFM: listener-supported, ad-free internet radio from San Francisco, from its public channel list. */
class SomaFmSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://somafm.com/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "SomaFM")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.LIVE)
    override val searchKind = ItemKind.STATION

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun home(): List<Shelf> {
        val stations = channels().sortedByDescending { it.listeners?.toIntOrNull() ?: 0 }.mapNotNull(::station)
        return if (stations.isEmpty()) emptyList() else listOf(Shelf("somafm:all", "SomaFM: ad-free radio", stations))
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val words = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val hits = channels().filter { c ->
            val text = listOfNotNull(c.title, c.description, c.genre).joinToString(" ").lowercase()
            words.isNotEmpty() && words.all { it in text }
        }
        return Page(hits.mapNotNull(::station), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream = ResolvedStream(url = streamUrl(ref.itemId), isLive = true)

    private suspend fun channels(): List<SomaChannel> = try {
        json.decodeFromString(SomaReply.serializer(), client.fetchText(baseUrl.resolve("channels.json")!!, info.displayName)).channels
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach SomaFM.", e)
    }

    private fun station(c: SomaChannel): Station? {
        val id = c.id?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: return null
        val title = c.title?.takeIf(String::isNotBlank) ?: return null
        return Station(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = c.description,
            artworkUrl = c.largeimage,
            countryCode = "US",
            tags = c.genre.orEmpty().split('|').map(String::trim).filter(String::isNotBlank),
            homepage = "https://somafm.com/$id/",
            streamUrl = streamUrl(id),
        )
    }

    companion object {
        const val ID = "somafm"

        /** SomaFM's direct MP3 stream for a channel (its .pls playlists point at the same servers). */
        fun streamUrl(id: String) = "https://ice2.somafm.com/$id-128-mp3"
    }
}
