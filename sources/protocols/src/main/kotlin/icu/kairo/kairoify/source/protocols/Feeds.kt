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

/** An RSS feed's show or release: podcasts, artist feeds, DJ shows. */
data class Feed(
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val link: String?,
    /** Podcasting 2.0 `podcast:medium`: "music" and "album" feeds are releases, not talk shows. */
    val medium: String?,
    /** Podcasting 2.0 `podcast:value`: the artist takes tips (Lightning). Shown, never paid by Kairoify. */
    val acceptsTips: Boolean,
    val episodes: List<FeedEpisode>,
)

data class FeedEpisode(
    val audioUrl: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val durationMillis: Long?,
    val link: String?,
)

/** Whether [text] looks like an RSS or Atom feed. */
fun looksLikeFeed(contentType: String?, text: String): Boolean {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val head = text.trimStart('﻿', ' ', '\r', '\n', '\t').take(1000).lowercase()
    return type.contains("rss") || type.contains("atom") || head.contains("<rss") || head.contains("<feed")
}

/**
 * Reads an RSS 2.0 feed, with the iTunes tags and the Podcasting 2.0 namespace (medium, person, value, images).
 * Episodes without an audio enclosure are left out.
 */
fun parseFeed(text: String, baseUrl: String): Feed {
    val root = parseXml(text, "That feed")
    val channel = root.kid("channel") ?: if (root.name == "channel") root else null
    if (channel == null) return parseAtom(root, baseUrl)
    val channelArt = channel.kidNs(ITUNES, "image")?.getAttribute("href")?.ifBlank { null }
        ?: channel.kid("image")?.textOf("url")
        ?: channel.kidPodcast("image")?.getAttribute("href")?.ifBlank { null }
    val author = channel.kidNs(ITUNES, "author")?.textContent?.trim()?.ifBlank { null }
        ?: channel.kidPodcast("person")?.textContent?.trim()?.ifBlank { null }
    val episodes = channel.kids("item").mapNotNull { item ->
        val enclosure = item.kid("enclosure") ?: return@mapNotNull null
        val type = enclosure.getAttribute("type").lowercase()
        if (type.isNotBlank() && !type.startsWith("audio/") && type != "application/ogg") return@mapNotNull null
        val audio = resolveUrl(baseUrl, enclosure.getAttribute("url")) ?: return@mapNotNull null
        FeedEpisode(
            audioUrl = audio,
            title = item.textOf("title") ?: audio.substringAfterLast('/'),
            artist = item.kidPodcast("person")?.textContent?.trim()?.ifBlank { null }
                ?: item.kidNs(ITUNES, "author")?.textContent?.trim()?.ifBlank { null } ?: author,
            artworkUrl = item.kidNs(ITUNES, "image")?.getAttribute("href")?.ifBlank { null } ?: channelArt,
            durationMillis = parseFeedDuration(item.kidNs(ITUNES, "duration")?.textContent),
            link = item.textOf("link"),
        )
    }
    return Feed(
        title = channel.textOf("title") ?: "Feed",
        author = author,
        artworkUrl = channelArt,
        link = channel.textOf("link"),
        medium = channel.kidPodcast("medium")?.textContent?.trim()?.lowercase()?.ifBlank { null },
        acceptsTips = channel.kidPodcast("value") != null,
        episodes = episodes,
    )
}

/** Atom feeds: entries whose rel="enclosure" link is audio. */
private fun parseAtom(root: org.w3c.dom.Element, baseUrl: String): Feed {
    val author = root.kid("author")?.textOf("name")
    val episodes = root.kids("entry").mapNotNull { entry ->
        val link = entry.kids("link").firstOrNull { it.getAttribute("rel") == "enclosure" && it.getAttribute("type").let { t -> t.isBlank() || t.startsWith("audio/") } }
            ?: return@mapNotNull null
        val audio = resolveUrl(baseUrl, link.getAttribute("href")) ?: return@mapNotNull null
        FeedEpisode(audio, entry.textOf("title") ?: audio.substringAfterLast('/'), entry.kid("author")?.textOf("name") ?: author, null, null, null)
    }
    return Feed(root.textOf("title") ?: "Feed", author, root.textOf("logo") ?: root.textOf("icon"), null, null, false, episodes)
}

/** itunes:duration: "1:02:03", "12:34" or plain seconds. */
fun parseFeedDuration(text: String?): Long? {
    val parts = text?.trim()?.takeIf(String::isNotEmpty)?.split(':')?.map { it.trim().toDoubleOrNull() ?: return null } ?: return null
    if (parts.size > 3) return null
    return (parts.fold(0.0) { acc, p -> acc * 60 + p } * 1000).toLong().takeIf { it > 0 }
}

private const val ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"

/** The Podcasting 2.0 namespace, under either address feeds declare it with. */
private fun org.w3c.dom.Element.kidPodcast(name: String): org.w3c.dom.Element? =
    kids(name).firstOrNull { it.namespaceURI.orEmpty().let { ns -> "podcastindex.org/namespace" in ns || "podcast-namespace" in ns } }
