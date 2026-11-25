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
package icu.kairo.kairoify.source.protocols

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Links: any web address the person pastes into search or saves in Settings, opened on the phone.
 * - Direct HTTP audio (MP3, FLAC, AAC, Ogg, Opus…) plays as a song, or as a live station when it never ends.
 * - M3U/M3U8, PLS and XSPF playlists open as a list of songs; a playlist of live streams plays as a station.
 * - RSS/Atom feeds (podcasts, artist releases, DJ shows) open with their episodes, Podcasting 2.0 tags included.
 * - A web page that advertises a feed (`<link rel="alternate" type="application/rss+xml">`) opens that feed.
 * - HLS (.m3u8 media playlists) plays as one stream.
 *
 * Item ids carry what the link turned out to be: "t:" song, "s:" live stream, "h:" HLS stream, "a:" list or feed.
 */
class LinkSource(
    private val client: OkHttpClient,
    private val saved: () -> List<String> = { emptyList() },
) : MusicSource {
    override val info = SourceInfo(ID, "Links")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.DETAILS)
    override val searchKind = ItemKind.ALBUM

    private val known = ConcurrentHashMap<String, Item>()

    override suspend fun home(): List<Shelf> {
        val items = openSaved()
        return if (items.isEmpty()) emptyList() else listOf(Shelf("links:saved", "Your links", items))
    }

    /** A pasted address is opened; words are matched against saved links' names. */
    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        if (page != null) return Page.empty()
        val text = query.trim()
        if (isWebAddress(text)) return Page(listOf(open(text)), null)
        val words = text.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty()) return Page.empty()
        val matches = openSaved().filter { item -> val hay = "${item.title} ${item.subtitle.orEmpty()}".lowercase(); words.all { it in hay } }
        return Page(matches, null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val url = ref.itemId.removePrefix("a:")
        val probe = client.probe(url, info.displayName)
        val body = probe.body ?: throw SourceException.NotFound("That link is a single recording, not a list.")
        val feedUrl = if (looksLikeFeed(probe.contentType, body)) null else feedLinkIn(body, probe.finalUrl)
        if (feedUrl != null) return details(MediaRef(ID, "a:$feedUrl"))
        if (looksLikeFeed(probe.contentType, body)) {
            val feed = parseFeed(body, probe.finalUrl)
            val album = feedAlbum(ref, feed)
            val tracks = feed.episodes.map { e ->
                Track(MediaRef(ID, "t:${e.audioUrl}"), e.title, e.artist, e.artworkUrl, listOfNotNull(e.artist), feed.title, e.durationMillis, e.link ?: feed.link)
            }
            return AlbumDetails(album.copy(trackCount = tracks.size), tracks)
        }
        val format = playlistFormat(probe.contentType, probe.finalUrl, body)
            ?: throw SourceException.NotFound("That link isn't a playlist, a feed or audio Kairoify can play.")
        val list = parsePlaylist(format, body, probe.finalUrl)
        val name = list.title ?: titleFromPath(probe.finalUrl)
        val tracks = list.entries.map { e ->
            Track(
                MediaRef(ID, (if (e.url.substringBefore('?').endsWith(".m3u8")) "h:" else "t:") + e.url),
                e.title ?: titleFromPath(e.url), e.artist, e.artworkUrl, listOfNotNull(e.artist), e.album ?: name, e.durationMillis,
                isHls = e.url.substringBefore('?').endsWith(".m3u8"),
            )
        }
        return AlbumDetails(Album(ref, name, hostOf(probe.finalUrl), tracks.firstNotNullOfOrNull { it.artworkUrl }, trackCount = tracks.size, pageUrl = url), tracks)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val kind = ref.itemId.substringBefore(':')
        val url = ref.itemId.substringAfter(':')
        return when (kind) {
            "h" -> ResolvedStream(url, mimeType = StreamMime.HLS, isLive = true)
            "s" -> ResolvedStream(url, isLive = true)
            "t" -> ResolvedStream(url, isLive = false)
            else -> throw SourceException.Unplayable("Open that link to choose what to play.")
        }
    }

    /** What [url] is, as an item: probed once, then remembered for this run of the app. */
    suspend fun open(url: String): Item = known[url] ?: identify(url).also { known[url] = it }

    private suspend fun openSaved(): List<Item> {
        val links = saved().distinct()
        val permits = Semaphore(4)
        return coroutineScope {
            links.map { url -> async { permits.withPermit { runCatching { open(url) }.getOrNull() } } }.awaitAll().filterNotNull()
        }
    }

    private suspend fun identify(url: String, depth: Int = 0): Item {
        val probe = client.probe(url, info.displayName)
        val host = hostOf(probe.finalUrl)
        val body = probe.body
        if (body == null) {
            // Audio. A stream that names itself (Icecast/Shoutcast) or has no length is live radio; a file is a song.
            return if (probe.isLiveHint || probe.contentLength == null && audioExtension(probe.finalUrl) == null) {
                Station(MediaRef(ID, "s:$url"), probe.icyName ?: titleFromPath(url), host, streamUrl = url)
            } else {
                Track(MediaRef(ID, "t:$url"), titleFromPath(probe.finalUrl), host, durationMillis = null, pageUrl = url)
            }
        }
        if (isHls(body)) return Station(MediaRef(ID, "h:$url"), titleFromPath(url), host, streamUrl = url, isHls = true)
        if (looksLikeFeed(probe.contentType, body)) return feedAlbum(MediaRef(ID, "a:$url"), parseFeed(body, probe.finalUrl))
        playlistFormat(probe.contentType, probe.finalUrl, body)?.let { format ->
            val list = parsePlaylist(format, body, probe.finalUrl)
            if (list.entries.isEmpty()) throw SourceException.NotFound("That playlist is empty.")
            // Radio stations hand out .pls/.m3u files with one or a few mirrors of the same live stream.
            val allLive = list.entries.all { it.durationMillis == null } && list.entries.size <= 4 && list.entries.none { audioExtension(it.url) != null }
            if (allLive) {
                val first = list.entries.first()
                val hls = first.url.substringBefore('?').endsWith(".m3u8")
                return Station(
                    MediaRef(ID, (if (hls) "h:" else "s:") + first.url),
                    list.title ?: first.title ?: titleFromPath(url), host, streamUrl = first.url, isHls = hls,
                )
            }
            return Album(MediaRef(ID, "a:$url"), list.title ?: titleFromPath(probe.finalUrl), host, trackCount = list.entries.size, pageUrl = url)
        }
        if (depth == 0) feedLinkIn(body, probe.finalUrl)?.let { return identify(it, depth + 1) }
        throw SourceException.NotFound("That page has no audio, playlist or feed Kairoify can play.")
    }

    private fun feedAlbum(ref: MediaRef, feed: Feed): Album {
        val kind = when (feed.medium) {
            "music", "album" -> "Music feed"
            "musicl" -> "Music playlist feed"
            else -> null
        }
        val subtitle = listOfNotNull(feed.author, kind, "Takes tips".takeIf { feed.acceptsTips }).joinToString(" · ").ifBlank { null }
        return Album(ref, feed.title, subtitle, feed.artworkUrl, listOfNotNull(feed.author), trackCount = feed.episodes.size.takeIf { it > 0 }, pageUrl = feed.link)
    }

    companion object {
        const val ID = "links"

        fun isWebAddress(text: String): Boolean =
            (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) &&
                text.none(Char::isWhitespace) && text.toHttpUrlOrNull() != null
    }
}

/** The RSS or Atom feed a web page advertises in its <head>, if any. */
internal fun feedLinkIn(html: String, baseUrl: String): String? {
    if (!html.contains("<link", ignoreCase = true)) return null
    val tag = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }.firstOrNull { link ->
        Regex("rel\\s*=\\s*[\"']?alternate", RegexOption.IGNORE_CASE).containsMatchIn(link) &&
            Regex("type\\s*=\\s*[\"']?application/(rss|atom)\\+xml", RegexOption.IGNORE_CASE).containsMatchIn(link)
    } ?: return null
    val href = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1) ?: return null
    return resolveUrl(baseUrl, href.replace("&amp;", "&"))
}

internal fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host
