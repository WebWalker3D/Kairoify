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
package icu.kairo.kairoify.player

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.findSameSong

/** A song a station says it is playing. */
data class RadioSong(val artist: String, val title: String)

/**
 * The song in a station's "now playing" line, e.g. "Nina Simone - Feeling Good". Null when the line is the station's
 * own description, or has no artist and title (ads, jingles, show names).
 */
fun radioSong(nowPlaying: NowPlaying): RadioSong? {
    if (!nowPlaying.isLive) return null
    val line = nowPlaying.subtitle?.trim() ?: return null
    if (line == nowPlaying.item?.subtitle?.trim()) return null
    val parts = line.split(Regex("\\s+[-–—]\\s+"), limit = 2)
    if (parts.size != 2) return null
    var (artist, title) = parts.map { it.trim().trim('"', '\'') }
    // "Le Mans by Bob Thompson - Classic Vinyl on walmradio.com": title by artist, then the station's own tag.
    val byForm = Regex("^(.+?)\\s+by\\s+(.+)$", RegexOption.IGNORE_CASE).find(artist)
    if (byForm != null && isStationTag(title, nowPlaying.title)) {
        title = byForm.groupValues[1].trim()
        artist = byForm.groupValues[2].trim()
    }
    if (artist.isEmpty() || title.isEmpty() || artist.length > 120 || title.length > 160) return null
    return RadioSong(artist, title)
}

/** Whether [tail] is the station naming itself ("Classic Vinyl on walmradio.com") rather than a song title. */
private fun isStationTag(tail: String, stationName: String): Boolean {
    val words = stationName.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
    return Regex("\\b[\\w-]+\\.(com|net|org|fm|radio|uk|de)\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail) ||
        words.any { it in tail.lowercase() }
}

/** The same song, playable on demand, from the switched-on song sources. */
suspend fun SourceRegistry.findRadioSong(song: RadioSong): Track? =
    findSameSong(Track(MediaRef("radio", "${song.artist}|${song.title}"), song.title, subtitle = song.artist, artists = listOf(song.artist)), skipSource = "")
