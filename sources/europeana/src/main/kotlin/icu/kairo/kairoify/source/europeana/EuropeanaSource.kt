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
package icu.kairo.kairoify.source.europeana

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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class EuItem(
    val id: String? = null,
    val title: List<String> = emptyList(),
    val dcCreator: List<String> = emptyList(),
    val edmIsShownBy: List<String> = emptyList(),
    val edmPreview: List<String> = emptyList(),
    val rights: List<String> = emptyList(),
    val guid: String? = null,
)

@Serializable
internal data class EuReply(val items: List<EuItem> = emptyList())

/**
 * Europeana: European historical and cultural recordings, limited to openly reusable ones whose media is an audio file.
 * Its API needs a free key, entered in Settings.
 */
class EuropeanaSource(
    private val client: OkHttpClient,
    private val apiKey: () -> String,
    private val baseUrl: HttpUrl = "https://api.europeana.eu/record/v2/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Europeana")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG
    override val searchTimeoutMillis = 12_000L

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> = Page(items(query).mapNotNull(::track), next = null)

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val item = items("europeana_id:\"${ref.itemId}\"").firstOrNull { it.id == ref.itemId }
        val media = item?.let(::audioUrl) ?: throw SourceException.NotFound("That recording is no longer on Europeana.")
        return ResolvedStream(url = media, isLive = false, license = licenseFromUrl(item.rights.firstOrNull()))
    }

    private suspend fun items(query: String): List<EuItem> {
        val url = baseUrl.newBuilder().addPathSegment("search.json")
            .addQueryParameter("query", query).addQueryParameter("media", "true").addQueryParameter("type", "SOUND")
            .addQueryParameter("reusability", "open").addQueryParameter("rows", "40").addQueryParameter("wskey", apiKey()).build()
        return try {
            json.decodeFromString(EuReply.serializer(), client.fetchText(url, info.displayName)).items
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach Europeana.", e)
        }
    }

    private fun audioUrl(item: EuItem): String? =
        item.edmIsShownBy.firstOrNull { url -> AUDIO.any { url.substringBefore('?').lowercase().endsWith(it) } }

    private fun track(item: EuItem): Track? {
        val id = item.id?.takeIf(String::isNotBlank) ?: return null
        audioUrl(item) ?: return null
        val title = item.title.firstOrNull()?.takeIf(String::isNotBlank) ?: return null
        val creator = item.dcCreator.firstOrNull()
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = creator,
            artworkUrl = item.edmPreview.firstOrNull(),
            artists = listOfNotNull(creator),
            pageUrl = item.guid?.substringBefore('?'),
            license = licenseFromUrl(item.rights.firstOrNull()),
        )
    }

    companion object {
        const val ID = "europeana"
        private val AUDIO = listOf(".mp3", ".ogg", ".oga", ".opus", ".wav", ".flac", ".m4a", ".aac")
    }
}
