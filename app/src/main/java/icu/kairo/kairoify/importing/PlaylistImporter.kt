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
package icu.kairo.kairoify.importing

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A song named in someone else's playlist. */
data class WantedSong(val artist: String, val title: String)

data class ImportResult(val found: List<Track>, val missed: List<WantedSong>)

/**
 * Brings a playlist in from elsewhere: a link yt-dlp can list (YouTube, YouTube Music, SoundCloud, Bandcamp…) or a
 * pasted list of "Artist - Title" lines. Each song is then found on the free sources by [findSong].
 */
class PlaylistImporter(
    private val listLink: suspend (String) -> List<JsonObject>,
    private val findSong: suspend (Track) -> Track?,
) {
    suspend fun import(input: String, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ImportResult {
        val wanted = songsIn(input)
        val found = arrayOfNulls<Track>(wanted.size)
        val lock = Mutex()
        var done = 0
        onProgress(0, wanted.size)
        coroutineScope {
            val permits = Semaphore(PARALLEL)
            wanted.mapIndexed { i, song ->
                async {
                    found[i] = permits.withPermit {
                        try {
                            findSong(Track(MediaRef("import", "$i"), song.title, subtitle = song.artist, artists = listOf(song.artist)))
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                    lock.withLock { onProgress(++done, wanted.size) }
                }
            }.awaitAll()
        }
        return ImportResult(found.filterNotNull().distinctBy { it.ref }, wanted.filterIndexed { i, _ -> found[i] == null })
    }

    /** The songs a link or a pasted list names, in order. */
    suspend fun songsIn(input: String): List<WantedSong> {
        val text = input.trim()
        val link = text.lineSequence().firstOrNull()?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (link != null) {
            require(!link.contains("spotify.com") && !link.contains("music.apple.com")) {
                "Spotify and Apple Music don't share their playlists with other apps. Paste the song list instead (one \"Artist - Title\" per line)."
            }
            return listLink(link).mapNotNull(::fromEntry).take(MAX_SONGS)
        }
        return text.lineSequence().mapNotNull(::fromLine).take(MAX_SONGS).toList()
    }

    private fun fromEntry(entry: JsonObject): WantedSong? {
        fun str(key: String) = (entry[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        val title = str("track") ?: str("title") ?: return null
        val artist = str("artist") ?: str("creator")
            ?: (str("channel") ?: str("uploader"))?.removeSuffix(" - Topic")?.takeIf { it.endsWith("VEVO").not() }
        if (str("track") == null) fromLine(title)?.let { return it }
        return artist?.let { WantedSong(it, title) }
    }

    private companion object {
        const val PARALLEL = 4
        const val MAX_SONGS = 300
    }
}

private val DASH = Regex("\\s+[-–—]\\s+")
private val BY = Regex("^(.+?)\\s+by\\s+(.+)$", RegexOption.IGNORE_CASE)

/** "Nina Simone - Feeling Good", "1. Nina Simone – Feeling Good", or "Feeling Good by Nina Simone". */
internal fun fromLine(raw: String): WantedSong? {
    val line = raw.trim().replace(Regex("^\\d+[.)]\\s*"), "")
    if (line.isEmpty()) return null
    DASH.split(line, 2).takeIf { it.size == 2 }?.let { (a, t) -> if (a.isNotBlank() && t.isNotBlank()) return WantedSong(a.trim(), t.trim()) }
    BY.find(line)?.let { m -> return WantedSong(m.groupValues[2].trim(), m.groupValues[1].trim()) }
    return null
}
