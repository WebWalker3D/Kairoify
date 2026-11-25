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

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** One entry of a playlist file. [durationMillis] is null for live streams (M3U's -1, PLS's -1, or none given). */
data class PlaylistEntry(
    val url: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMillis: Long? = null,
    val artworkUrl: String? = null,
)

/** A playlist file's name (when it has one) and entries. */
data class Playlist(val title: String?, val entries: List<PlaylistEntry>)

enum class PlaylistFormat { M3U, PLS, XSPF }

/** Which playlist format [text] is in, from its MIME type, its address, then its first bytes. Null when it's none. */
fun playlistFormat(contentType: String?, url: String, text: String?): PlaylistFormat? {
    val type = contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    val path = url.substringBefore('?').substringBefore('#').lowercase()
    val head = text?.trimStart('﻿', ' ', '\r', '\n', '\t')?.take(200)?.lowercase().orEmpty()
    return when {
        type == "application/xspf+xml" || path.endsWith(".xspf") || head.contains("<playlist") && head.contains("xspf") -> PlaylistFormat.XSPF
        type in PLS_TYPES || path.endsWith(".pls") || head.startsWith("[playlist]") -> PlaylistFormat.PLS
        // HLS media playlists are M3U8 too, but those are one stream for the player, not a list of songs.
        head.startsWith("#extm3u") && (head.contains("#ext-x-targetduration") || head.contains("#ext-x-stream-inf") || head.contains("#ext-x-version")) -> null
        type in M3U_TYPES || path.endsWith(".m3u") || path.endsWith(".m3u8") || head.startsWith("#extm3u") -> PlaylistFormat.M3U
        else -> null
    }
}

/** Whether [text] is an HLS playlist: the player streams it whole, it isn't a list of separate songs. */
fun isHls(text: String): Boolean {
    val head = text.take(4000)
    return head.contains("#EXT-X-TARGETDURATION") || head.contains("#EXT-X-STREAM-INF") || head.contains("#EXT-X-MEDIA-SEQUENCE")
}

fun parsePlaylist(format: PlaylistFormat, text: String, baseUrl: String): Playlist = when (format) {
    PlaylistFormat.M3U -> parseM3u(text, baseUrl)
    PlaylistFormat.PLS -> parsePls(text, baseUrl)
    PlaylistFormat.XSPF -> parseXspf(text, baseUrl)
}

/** M3U and M3U8: one address per line, with optional "#EXTINF:seconds,Artist - Title" before it. */
fun parseM3u(text: String, baseUrl: String): Playlist {
    var title: String? = null
    var pending: Pair<Long?, String?>? = null
    val entries = mutableListOf<PlaylistEntry>()
    for (raw in text.lineSequence()) {
        val line = raw.trim().trimStart('﻿')
        when {
            line.isEmpty() -> Unit
            line.startsWith("#PLAYLIST:", ignoreCase = true) -> title = line.substringAfter(':').trim().ifBlank { null }
            line.startsWith("#EXTINF:", ignoreCase = true) -> {
                val body = line.substringAfter(':')
                val seconds = body.substringBefore(',').substringBefore(' ').trim().toDoubleOrNull()
                pending = seconds?.takeIf { it > 0 }?.let { (it * 1000).toLong() } to body.substringAfter(',', "").trim().ifBlank { null }
            }
            line.startsWith("#") -> Unit
            else -> resolveUrl(baseUrl, line)?.let { url ->
                val (artist, songTitle) = splitArtistTitle(pending?.second)
                entries += PlaylistEntry(url, songTitle, artist, durationMillis = pending?.first)
                pending = null
            }
        }
    }
    return Playlist(title, entries)
}

/** PLS: "File1=…", "Title1=…", "Length1=…" (seconds, -1 for a live stream). */
fun parsePls(text: String, baseUrl: String): Playlist {
    val values = text.lineSequence().map(String::trim).filter { '=' in it }
        .associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=').trim() }
    val numbers = values.keys.mapNotNull { key -> key.removePrefix("file").takeIf { key.startsWith("file") }?.toIntOrNull() }.sorted()
    val entries = numbers.mapNotNull { n ->
        val url = resolveUrl(baseUrl, values["file$n"].orEmpty()) ?: return@mapNotNull null
        val seconds = values["length$n"]?.toLongOrNull()?.takeIf { it > 0 }
        val (artist, title) = splitArtistTitle(values["title$n"]?.ifBlank { null })
        PlaylistEntry(url, title, artist, durationMillis = seconds?.times(1000))
    }
    return Playlist(null, entries)
}

/** XSPF ("spiff"): an XML playlist with a title, creator, album, duration and image per track. */
fun parseXspf(text: String, baseUrl: String): Playlist {
    val root = parseXml(text, "That playlist")
    val tracks = root.kid("trackList")?.kids("track").orEmpty()
    val entries = tracks.mapNotNull { t ->
        val location = t.kids("location").firstNotNullOfOrNull { resolveUrl(baseUrl, it.textContent.trim()) } ?: return@mapNotNull null
        PlaylistEntry(
            url = location,
            title = t.textOf("title"),
            artist = t.textOf("creator"),
            album = t.textOf("album"),
            durationMillis = t.textOf("duration")?.toLongOrNull()?.takeIf { it > 0 },
            artworkUrl = t.textOf("image")?.let { resolveUrl(baseUrl, it) },
        )
    }
    return Playlist(root.textOf("title"), entries)
}

/** "Artist - Title" → (Artist, Title); anything else is all title. */
internal fun splitArtistTitle(text: String?): Pair<String?, String?> {
    if (text == null) return null to null
    val parts = text.split(" - ", limit = 2)
    return if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0].trim() to parts[1].trim() else null to text
}

/** An entry's address made absolute against the playlist's. Only http(s) is accepted: no file:, no content:. */
internal fun resolveUrl(base: String, value: String): String? {
    if (value.isBlank()) return null
    val absolute = value.toHttpUrlOrNull() ?: base.toHttpUrlOrNull()?.resolve(value) ?: return null
    return absolute.toString()
}

private val PLS_TYPES = setOf("audio/x-scpls", "audio/scpls", "application/pls+xml")
private val M3U_TYPES = setOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/m3u")
