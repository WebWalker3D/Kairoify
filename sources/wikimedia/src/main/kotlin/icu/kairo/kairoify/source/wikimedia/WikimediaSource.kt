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
package icu.kairo.kairoify.source.wikimedia

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.License
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Most metadata values are text, but some are numbers, booleans or objects (e.g. CommonsMetadataExtension). */
@Serializable
internal data class WmMetaValue(val value: JsonElement? = null) {
    val text: String? get() = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
}

@Serializable
internal data class WmImageInfo(val url: String? = null, val mime: String? = null, val extmetadata: Map<String, WmMetaValue> = emptyMap())

@Serializable
internal data class WmPage(val title: String? = null, val index: Int? = null, val imageinfo: List<WmImageInfo> = emptyList())

@Serializable
internal data class WmQuery(val pages: Map<String, WmPage> = emptyMap())

@Serializable
internal data class WmReply(val query: WmQuery? = null)

/** Wikimedia Commons: classical, folk, historical and international recordings, each with its own license. */
class WikimediaSource(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://commons.wikimedia.org/w/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Wikimedia Commons")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val reply = call(
            mapOf(
                "generator" to "search", "gsrsearch" to "filetype:audio $query", "gsrnamespace" to "6", "gsrlimit" to "20",
            ),
        )
        val pages = reply.query?.pages?.values.orEmpty().sortedBy { it.index ?: Int.MAX_VALUE }
        return Page(pages.mapNotNull(::track), next = null)
    }

    /** The item id is the file's page title; the file itself is what plays. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val info = call(mapOf("titles" to ref.itemId)).query?.pages?.values?.firstOrNull()?.imageinfo?.firstOrNull()
        val url = info?.url?.takeIf(String::isNotBlank) ?: throw SourceException.NotFound("That recording is no longer on Wikimedia Commons.")
        return ResolvedStream(url = url, isLive = false, license = license(info))
    }

    private suspend fun call(params: Map<String, String>): WmReply {
        val url = baseUrl.newBuilder().addPathSegment("api.php")
            .addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("prop", "imageinfo").addQueryParameter("iiprop", "url|extmetadata|mime")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return try {
            json.decodeFromString(WmReply.serializer(), client.fetchText(url, info.displayName))
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach Wikimedia Commons.", e)
        }
    }

    private fun license(info: WmImageInfo): License? =
        wikimediaLicense(info.extmetadata["LicenseUrl"]?.text, info.extmetadata["LicenseShortName"]?.text)

    private fun track(page: WmPage): Track? {
        val title = page.title?.takeIf { it.startsWith("File:") } ?: return null
        val info = page.imageinfo.firstOrNull() ?: return null
        if (info.url.isNullOrBlank()) return null
        // Commons' "audio" includes MIDI, which the player can't decode.
        if (info.mime.orEmpty().lowercase() in UNPLAYABLE) return null
        val artist = info.extmetadata["Artist"]?.text?.let(::plainText)?.takeIf(String::isNotBlank)
        val name = title.removePrefix("File:").substringBeforeLast('.')
        return Track(
            ref = MediaRef(ID, title),
            title = name,
            subtitle = artist,
            artists = listOfNotNull(artist),
            pageUrl = "https://commons.wikimedia.org/wiki/" + title.replace(' ', '_'),
            license = license(info),
        )
    }

    companion object {
        const val ID = "wikimedia"
        private val UNPLAYABLE = setOf("audio/midi", "audio/x-midi", "audio/mid")
    }
}

/** Commons metadata is HTML; keep just the words. */
fun plainText(html: String): String =
    html.replace(Regex("<[^>]*>"), "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

/** A license from Commons' URL, or its short name when that says public domain. */
fun wikimediaLicense(url: String?, shortName: String?): License? =
    licenseFromUrl(url?.removeSuffix("/deed.en")?.let { if (it.endsWith("/")) it else "$it/" })
        ?: shortName?.takeIf { "public domain" in it.lowercase() }?.let { License("Public domain", url) }
