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
package icu.kairo.kairoify.source.loc

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Shelf
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class LocResult(
    val title: String? = null,
    val id: String? = null,
    val date: String? = null,
    @SerialName("contributor_primary") val contributors: List<String> = emptyList(),
    @SerialName("image_url") val images: List<String> = emptyList(),
    @SerialName("access_restricted") val restricted: Boolean? = null,
    @SerialName("online_format") val formats: List<String> = emptyList(),
)

@Serializable
internal data class LocSearch(val results: List<LocResult> = emptyList())

@Serializable
internal data class LocResource(val audio: String? = null)

@Serializable
internal data class LocItem(val resources: List<LocResource> = emptyList())

/**
 * The Library of Congress's online audio, including the National Jukebox (early recordings, 1900–1925). Only items it
 * marks as unrestricted are listed.
 */
class LocSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://www.loc.gov/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Library of Congress")
    override val capabilities = setOf(Capability.SEARCH, Capability.BROWSE, Capability.HOME)
    override val searchKind = ItemKind.SONG
    // loc.gov often takes 3–8 s to answer a search, longer on a phone.
    override val searchTimeoutMillis = 20_000L

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> = Page(find(query = query), next = null)

    override suspend fun facets(kind: FacetKind): List<Facet> =
        if (kind == FacetKind.COLLECTION) COLLECTIONS.map { (id, label) -> Facet(BrowseNode.Collection(id), label, null) } else emptyList()

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> {
        val id = (node as? BrowseNode.Collection)?.id?.takeIf { c -> COLLECTIONS.any { it.first == c } } ?: return Page.empty()
        return Page(find(partOf = id), next = null)
    }

    /** The National Jukebox: 78s from 1900–1925, the Library's best-known audio. */
    override suspend fun home(): List<Shelf> =
        find(partOf = JUKEBOX).takeIf { it.isNotEmpty() }?.let { listOf(Shelf("loc:jukebox", "National Jukebox", it)) }.orEmpty()

    private suspend fun find(query: String? = null, partOf: String? = null): List<Track> {
        val url = baseUrl.newBuilder().addPathSegment("audio").addPathSegment("")
            .apply { query?.let { addQueryParameter("q", it) } }.addQueryParameter("fo", "json").addQueryParameter("c", "25")
            .addQueryParameter("fa", listOfNotNull("online-format:audio", partOf?.let { "partof:$it" }).joinToString("|")).build()
        return decode(url, LocSearch.serializer()).results
            .filter { it.restricted == false && "audio" in it.formats }
            .mapNotNull(::track)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = baseUrl.newBuilder().addPathSegment("item").addPathSegment(ref.itemId).addPathSegment("")
            .addQueryParameter("fo", "json").build()
        val audio = decode(url, LocItem.serializer()).resources.firstNotNullOfOrNull { it.audio?.takeIf(String::isNotBlank) }
            ?: throw SourceException.NotFound("That recording isn't available to play online.")
        return ResolvedStream(url = audio, isLive = false)
    }

    private suspend fun <T> decode(url: HttpUrl, serializer: KSerializer<T>): T = try {
        json.decodeFromString(serializer, client.fetchText(url, info.displayName))
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach the Library of Congress.", e)
    }

    private fun track(r: LocResult): Track? {
        val id = r.id?.trimEnd('/')?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: return null
        val title = r.title?.takeIf(String::isNotBlank) ?: return null
        val artists = r.contributors.map(::titleCase)
        return Track(
            ref = MediaRef(ID, id),
            title = title,
            subtitle = (artists.take(2) + listOfNotNull(r.date?.take(4))).joinToString(" · ").ifBlank { null },
            artworkUrl = r.images.firstOrNull(),
            artists = artists,
            pageUrl = "https://www.loc.gov/item/$id/",
        )
    }

    companion object {
        const val ID = "loc"
        private const val JUKEBOX = "national jukebox"

        /** Collections from loc.gov's own "part of" list, by how much audio they hold. */
        internal val COLLECTIONS = listOf(
            JUKEBOX to "National Jukebox",
            "the library of congress celebrates the songs of america" to "Songs of America",
            "american folklife center" to "American Folklife Center",
            "veterans history project collection" to "Veterans' stories",
            "recorded sound research center" to "Recorded Sound",
        )
    }
}

/** The Library lists names in lower case: "joseph c. smith's orchestra" → "Joseph C. Smith's Orchestra". */
fun titleCase(text: String): String = text.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
