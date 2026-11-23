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
package icu.kairo.kairoify.source.radiobrowser

import icu.kairo.kairoify.core.model.BrowseNode
import icu.kairo.kairoify.core.model.Facet
import icu.kairo.kairoify.core.model.FacetKind
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.IllformedLocaleException
import java.util.Locale

/** Live radio from radio-browser.info: ~50,000 community-listed stations in every country. */
class RadioBrowserSource(
    private val client: OkHttpClient,
    private val baseUrl: suspend () -> HttpUrl,
    private val countryCode: () -> String?,
) : MusicSource {
    override val info = SourceInfo(SOURCE_ID, "Radio Browser")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.BROWSE, Capability.LIVE)
    override val searchKind = ItemKind.STATION

    override suspend fun home(): List<Shelf> {
        val local = countryCode()?.let { cc ->
            val stations = stationList(listOf("json", "stations", "bycountrycodeexact", cc), offset = 0, limit = LOCAL_SHELF_SIZE)
            Shelf("$SOURCE_ID:local", "Live now in ${countryName(cc, fallback = cc)}", stations)
        }
        val top = Shelf(
            "$SOURCE_ID:top",
            "Most popular worldwide",
            get<List<StationDto>>(listOf("json", "stations", "topclick", "$TOP_SHELF_SIZE"), mapOf("hidebroken" to "true"))
                .map { it.toStation() },
        )
        return listOfNotNull(local, top)
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> =
        stationPage(listOf("json", "stations", "search"), page, extra = mapOf("name" to query))

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> = when (node) {
        is BrowseNode.Country -> stationPage(listOf("json", "stations", "bycountrycodeexact", node.code), page)
        is BrowseNode.Genre -> stationPage(listOf("json", "stations", "bytagexact", node.name), page)
        is BrowseNode.Collection -> Page.empty()
    }

    override suspend fun facets(kind: FacetKind): List<Facet> = when (kind) {
        FacetKind.COUNTRY -> get<List<CountryDto>>(listOf("json", "countries"))
            .filter { it.stationcount > 0 && it.code.isNotBlank() }
            .map { Facet(BrowseNode.Country(it.code), countryName(it.code, fallback = it.name), it.stationcount) }
            .sortedBy { it.label }
        FacetKind.GENRE -> get<List<TagDto>>(
            listOf("json", "tags"),
            mapOf("order" to "stationcount", "reverse" to "true", "limit" to "200", "hidebroken" to "true"),
        ).filter { it.name.isNotBlank() }.map { Facet(BrowseNode.Genre(it.name), it.name, it.stationcount) }
        FacetKind.COLLECTION -> emptyList()
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val station = get<List<StationDto>>(listOf("json", "stations", "byuuid", ref.itemId)).firstOrNull()?.toStation()
            ?: throw SourceException.NotFound("That station is no longer listed")
        return ResolvedStream(
            url = station.streamUrl,
            mimeType = if (station.isHls) StreamMime.HLS else null,
            isLive = true,
        )
    }

    override suspend fun reportPlay(ref: MediaRef) {
        client.getBody(url(listOf("json", "url", ref.itemId)))
    }

    private suspend fun stationPage(path: List<String>, page: PageToken?, extra: Map<String, String> = emptyMap()): Page<Item> {
        val offset = page?.value?.toIntOrNull() ?: 0
        val items = stationList(path, offset, PAGE_SIZE, extra)
        return Page(items, if (items.size == PAGE_SIZE) PageToken("${offset + PAGE_SIZE}") else null)
    }

    private suspend fun stationList(path: List<String>, offset: Int, limit: Int, extra: Map<String, String> = emptyMap()): List<Item> =
        get<List<StationDto>>(
            path,
            extra + mapOf(
                "order" to "clickcount",
                "reverse" to "true",
                "limit" to "$limit",
                "offset" to "$offset",
                "hidebroken" to "true",
            ),
        ).map { it.toStation() }

    private suspend inline fun <reified T> get(path: List<String>, query: Map<String, String> = emptyMap()): T =
        try {
            json.decodeFromString<T>(client.getBody(url(path, query)))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw SourceException.Unavailable("Radio Browser sent something unexpected", e)
        }

    private suspend fun url(path: List<String>, query: Map<String, String> = emptyMap()): HttpUrl =
        baseUrl().newBuilder().apply {
            path.forEach { addPathSegment(it) }
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private fun countryName(code: String, fallback: String): String {
        val name = try {
            Locale.Builder().setRegion(code).build().displayCountry
        } catch (_: IllformedLocaleException) {
            null
        }
        return name?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: fallback
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val LOCAL_SHELF_SIZE = 20
        const val TOP_SHELF_SIZE = 20
        val json = Json { ignoreUnknownKeys = true }
    }
}
