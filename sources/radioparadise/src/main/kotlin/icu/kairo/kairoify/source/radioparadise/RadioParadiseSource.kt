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
package icu.kairo.kairoify.source.radioparadise

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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class RpChannel(
    val chan: String? = null,
    val title: String? = null,
    val stream_name: String? = null,
    val current_listeners: String? = null,
    val image: String? = null,
)

/** Radio Paradise: listener-supported, ad-free, human-curated radio, from its public channel list and streams. */
class RadioParadiseSource(
    private val client: OkHttpClient,
    private val apiUrl: HttpUrl = "https://api.radioparadise.com/api/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Radio Paradise")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.LIVE)
    override val searchKind = ItemKind.STATION

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun home(): List<Shelf> {
        val stations = channels().sortedByDescending { it.current_listeners?.toIntOrNull() ?: 0 }.mapNotNull(::station)
        return if (stations.isEmpty()) emptyList() else listOf(Shelf("radioparadise:all", "Radio Paradise", stations))
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val q = query.trim().lowercase()
        return Page(channels().filter { q.isNotEmpty() && it.title.orEmpty().lowercase().contains(q) }.mapNotNull(::station), next = null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream = ResolvedStream(url = STREAMS + ref.itemId, isLive = true)

    private suspend fun channels(): List<RpChannel> = try {
        val url = apiUrl.newBuilder().addPathSegment("list_chan").addQueryParameter("C_user_id", "0").build()
        json.decodeFromString(ListSerializer(RpChannel.serializer()), client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach Radio Paradise.", e)
    }

    private fun station(c: RpChannel): Station? {
        val name = c.stream_name?.takeIf { it.matches(Regex("[a-z0-9-]+")) } ?: return null
        val title = c.title?.takeIf(String::isNotBlank) ?: return null
        val path = streamPath(name)
        return Station(
            ref = MediaRef(ID, path),
            title = title,
            subtitle = "Radio Paradise",
            artworkUrl = c.image,
            countryCode = "US",
            homepage = "https://radioparadise.com/",
            streamUrl = STREAMS + path,
        )
    }

    companion object {
        const val ID = "radioparadise"
        private const val STREAMS = "https://stream.radioparadise.com/"

        /** The AAC 320 stream for a channel; the Main Mix and Serenity are named differently. */
        fun streamPath(streamName: String) = when (streamName) {
            "main-mix" -> "aac-320"
            "serenity" -> "serenity"
            else -> "$streamName-320"
        }
    }
}
