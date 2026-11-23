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
package icu.kairo.kairoify.source.archive

import icu.kairo.kairoify.core.model.Album
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
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.model.licenseFromUrl
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The Internet Archive's music: Live Music Archive concerts (recorded with the bands' permission), the Great 78
 * Project, netlabels and public-domain classical. Items are albums; their MP3 files are the tracks.
 */
class ArchiveSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://archive.org/".toHttpUrl(),
    private val today: () -> LocalDate = LocalDate::now,
) : MusicSource {
    override val info = SourceInfo(ID, "Internet Archive")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.BROWSE, Capability.DETAILS)
    override val searchKind = ItemKind.ALBUM

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val phrase = query.trim().replace("\\", "\\\\").replace("\"", "\\\"")
        return albums("(${COLLECTIONS.values.joinToString(" OR ") { "($it)" }}) AND (\"$phrase\")", page, SEARCH_ROWS)
    }

    override suspend fun home(): List<Shelf> {
        val day = today()
        val dates = (FIRST_YEAR until day.year).joinToString(" OR ") { "date:%04d-%02d-%02d".format(it, day.monthValue, day.dayOfMonth) }
        val concerts = albums("mediatype:etree AND ($dates)", null, HOME_ROWS).items
        if (concerts.isEmpty()) return emptyList()
        val month = day.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return listOf(Shelf("archive:onthisday", "On this day: concerts recorded $month ${day.dayOfMonth}", concerts))
    }

    override suspend fun facets(kind: FacetKind): List<Facet> =
        if (kind == FacetKind.COLLECTION) COLLECTION_NAMES.map { (id, label) -> Facet(BrowseNode.Collection(id), label, null) } else emptyList()

    override suspend fun browse(node: BrowseNode, page: PageToken?): Page<Item> {
        val scope = (node as? BrowseNode.Collection)?.let { COLLECTIONS[it.id] } ?: return Page.empty()
        return albums(scope, page, SEARCH_ROWS)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val url = baseUrl.newBuilder().addPathSegment("metadata").addPathSegment(ref.itemId).build()
        val root = parse(client.fetchText(url, info.displayName))
        val meta = root["metadata"] as? JsonObject ?: throw SourceException.NotFound("That recording is no longer on the Internet Archive.")
        val album = album(meta, ref.itemId)
        val tracks = (root["files"] as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .filter { it.string("name").orEmpty().lowercase().endsWith(".mp3") }
            .sortedWith(compareBy({ trackNumber(it.string("track")) }, { it.string("name") }))
            .map { file ->
                val name = file.string("name")!!
                Track(
                    ref = MediaRef(ID, "${ref.itemId}/$name"),
                    title = file.string("title")?.takeIf(String::isNotBlank) ?: name.substringAfterLast('/').removeSuffix(".mp3"),
                    subtitle = file.creators().ifEmpty { album.artists }.joinToString(", ").ifBlank { null },
                    artworkUrl = album.artworkUrl,
                    artists = file.creators().ifEmpty { album.artists },
                    album = album.title,
                    durationMillis = parseArchiveLength(file.string("length")),
                    pageUrl = album.pageUrl,
                    license = album.license,
                )
            }
        return AlbumDetails(album.copy(trackCount = tracks.size), tracks)
    }

    /** "<identifier>/<file path>" streams from the item's download folder; the Archive redirects to a mirror. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val id = ref.itemId.substringBefore('/')
        val file = ref.itemId.substringAfter('/', missingDelimiterValue = "")
        if (file.isBlank()) throw SourceException.NotFound("Open the recording to pick a track.")
        val url = baseUrl.newBuilder().addPathSegment("download").addPathSegment(id).addPathSegments(file).build()
        return ResolvedStream(url = url.toString(), isLive = false)
    }

    private suspend fun albums(query: String, page: PageToken?, rows: Int): Page<Item> {
        val number = page?.value?.toIntOrNull() ?: 1
        val url = baseUrl.newBuilder().addPathSegment("advancedsearch.php")
            .addQueryParameter("q", query)
            .apply { FIELDS.forEach { addQueryParameter("fl[]", it) } }
            .addQueryParameter("sort[]", "downloads desc")
            .addQueryParameter("rows", rows.toString())
            .addQueryParameter("page", number.toString())
            .addQueryParameter("output", "json")
            .build()
        val response = parse(client.fetchText(url, info.displayName))["response"] as? JsonObject ?: return Page.empty()
        val found = (response["numFound"] as? JsonPrimitive)?.intOrNull ?: 0
        val docs = (response["docs"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let { doc -> album(doc, doc.string("identifier") ?: return@let null) } }
        return Page(docs, if (number * rows < found && docs.isNotEmpty()) PageToken((number + 1).toString()) else null)
    }

    private fun album(doc: JsonObject, id: String): Album {
        val artists = doc.creators()
        val year = doc.string("year") ?: doc.string("date")?.take(4)?.takeIf { it.all(Char::isDigit) }
        val venue = doc.string("venue")?.takeIf(String::isNotBlank)
        return Album(
            ref = MediaRef(ID, id),
            title = doc.string("title")?.takeIf(String::isNotBlank) ?: id,
            subtitle = listOfNotNull(artists.joinToString(", ").ifBlank { null }, venue, year).joinToString(" · ").ifBlank { null },
            artworkUrl = baseUrl.newBuilder().addPathSegments("services/img").addPathSegment(id).build().toString(),
            artists = artists,
            year = year,
            pageUrl = "https://archive.org/details/$id",
            license = licenseFromUrl(doc.string("licenseurl")),
        )
    }

    private fun parse(text: String): JsonObject = try {
        Json.parseToJsonElement(text).jsonObject
    } catch (e: IllegalArgumentException) {
        throw SourceException.Unavailable("Couldn't reach Internet Archive.", e)
    }

    private fun JsonObject.string(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull
        is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    /** "creator" is a string, a list of strings, or missing. */
    private fun JsonObject.creators(): List<String> = when (val v: JsonElement? = this["creator"]) {
        is JsonPrimitive -> listOfNotNull(v.contentOrNull?.takeIf(String::isNotBlank))
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        else -> emptyList()
    }

    private fun trackNumber(text: String?): Int = text?.substringBefore('/')?.trim()?.toIntOrNull() ?: Int.MAX_VALUE

    companion object {
        const val ID = "archive"
        private const val FIRST_YEAR = 1960
        private const val SEARCH_ROWS = 30
        private const val HOME_ROWS = 20
        private val FIELDS = listOf("identifier", "title", "creator", "date", "year", "venue", "coverage", "licenseurl")

        /** Collection id → the search that finds its playable items. */
        private val COLLECTIONS = linkedMapOf(
            "etree" to "collection:etree AND mediatype:etree",
            "georgeblood" to "collection:georgeblood AND mediatype:audio",
            "netlabels" to "collection:netlabels AND mediatype:audio",
            "classical" to "collection:opensource_audio AND subject:classical",
        )
        private val COLLECTION_NAMES = listOf(
            "etree" to "Live Music Archive",
            "georgeblood" to "Great 78 Project",
            "netlabels" to "Netlabels",
            "classical" to "Classical",
        )
    }
}

/** Archive track lengths come as "06:21", "1:02:03" or seconds ("182.26"). */
fun parseArchiveLength(text: String?): Long? {
    val value = text?.trim() ?: return null
    if (":" in value) {
        val parts = value.split(":").map { it.toDoubleOrNull() ?: return null }
        return (parts.fold(0.0) { total, part -> total * 60 + part } * 1000).toLong()
    }
    return value.toDoubleOrNull()?.let { (it * 1000).toLong() }
}
