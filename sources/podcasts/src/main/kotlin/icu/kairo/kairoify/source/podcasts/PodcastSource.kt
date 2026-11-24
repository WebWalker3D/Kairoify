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
package icu.kairo.kairoify.source.podcasts

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

@Serializable
internal data class ItShow(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    val artworkUrl600: String? = null,
    val trackCount: Int? = null,
    val collectionViewUrl: String? = null,
)

@Serializable
internal data class ItReply(val results: List<ItShow> = emptyList())

/**
 * Podcasts: shows found through Apple's public podcast directory, episodes read straight from each show's own RSS
 * feed. A show is an album; its episodes are the tracks, played from the feed's audio enclosure.
 */
class PodcastSource(
    private val client: OkHttpClient,
    private val directoryUrl: HttpUrl = "https://itunes.apple.com/".toHttpUrl(),
) : MusicSource {
    override val info = SourceInfo(ID, "Podcasts")
    override val capabilities = setOf(Capability.SEARCH, Capability.DETAILS)
    override val searchKind = ItemKind.ALBUM

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val url = directoryUrl.newBuilder().addPathSegment("search").addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast").addQueryParameter("term", query).addQueryParameter("limit", "25").build()
        val shows = try {
            json.decodeFromString(ItReply.serializer(), client.fetchText(url, info.displayName)).results
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("Couldn't reach the podcast directory.", e)
        }
        return Page(shows.mapNotNull(::show), next = null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val feed = ref.itemId.toHttpUrlOrNull() ?: throw SourceException.NotFound("That podcast feed address isn't valid.")
        val channel = parseFeed(client.fetchText(feed, info.displayName))
        val title = channel.text("title") ?: throw SourceException.NotFound("That podcast's feed is empty.")
        val author = channel.text("itunes:author")
        val art = channel.child("itunes:image")?.getAttribute("href")?.takeIf(String::isNotBlank)
        val episodes = channel.children("item").mapNotNull { item ->
            val enclosure = item.child("enclosure") ?: return@mapNotNull null
            val audio = enclosure.getAttribute("url").takeIf(String::isNotBlank) ?: return@mapNotNull null
            if (!enclosure.getAttribute("type").let { it.isBlank() || it.startsWith("audio/") }) return@mapNotNull null
            Track(
                ref = MediaRef(ID, audio),
                title = item.text("title") ?: return@mapNotNull null,
                subtitle = author,
                artworkUrl = item.child("itunes:image")?.getAttribute("href")?.takeIf(String::isNotBlank) ?: art,
                artists = listOfNotNull(author),
                album = title,
                durationMillis = parseDuration(item.text("itunes:duration")),
                pageUrl = item.text("link"),
            )
        }
        val album = Album(ref, title, author, art, listOfNotNull(author), trackCount = episodes.size, pageUrl = channel.text("link"))
        return AlbumDetails(album, episodes)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream = ResolvedStream(url = ref.itemId, isLive = false)

    private fun show(s: ItShow): Album? {
        val feed = s.feedUrl?.takeIf { it.toHttpUrlOrNull() != null } ?: return null
        val title = s.collectionName?.takeIf(String::isNotBlank) ?: return null
        return Album(
            ref = MediaRef(ID, feed),
            title = title,
            subtitle = s.artistName,
            artworkUrl = s.artworkUrl600,
            artists = listOfNotNull(s.artistName),
            trackCount = s.trackCount,
            pageUrl = s.collectionViewUrl,
        )
    }

    /** Parses a feed's <channel>. Feeds with a DOCTYPE are refused so no entity is ever expanded. */
    private fun parseFeed(xml: String): Element = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        if (Regex("<!DOCTYPE", RegexOption.IGNORE_CASE).containsMatchIn(xml)) throw IllegalArgumentException("DOCTYPE")
        factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement.child("channel")
            ?: throw IllegalArgumentException("no channel")
    } catch (e: Exception) {
        if (e is SourceException) throw e
        throw SourceException.Unavailable("That podcast's feed couldn't be read.", e)
    }

    companion object {
        const val ID = "podcasts"
    }
}

private fun Element.children(name: String): List<Element> =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.filter { it.tagName == name }

private fun Element.child(name: String): Element? = children(name).firstOrNull()

private fun Element.text(name: String): String? = child(name)?.textContent?.trim()?.takeIf(String::isNotBlank)

/** itunes:duration is "1:02:03", "12:34" or plain seconds. */
fun parseDuration(text: String?): Long? {
    val parts = text?.trim()?.split(':')?.map { it.toLongOrNull() ?: return null } ?: return null
    if (parts.isEmpty() || parts.size > 3) return null
    return parts.fold(0L) { acc, p -> acc * 60 + p }.takeIf { it > 0 }?.times(1000)
}
