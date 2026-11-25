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
package icu.kairo.kairoify.core.source

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.flow.toList
import kotlin.math.abs

/** How shared credits are written: "A / B", "A & B", "A, B", "A feat. B", "A ft. B", "A featuring B", "A x B", "A with B", "A · 1962". */
private val CREDIT_SEPARATORS = Regex("\\s*(?:/|&|,|;|·|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bx\\b|\\bwith\\b)\\s*", RegexOption.IGNORE_CASE)

/** Words that mark a different recording of a song. */
private val VERSION_WORDS = Regex("\\b(remix|edit|live|cover|karaoke|instrumental|acoustic|sped up|slowed|nightcore|mashup|flip|bootleg|rework|vip)\\b", RegexOption.IGNORE_CASE)

/** Lower case, letters and digits only: "Feeling Good!" → "feeling good". */
fun normalizeName(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** The artist a song or album is by: its first credited artist, else its subtitle before " · ". */
fun mainArtist(item: Item): String? = when (item) {
    is Track -> item.artists.firstOrNull() ?: item.subtitle
    is Album -> item.artists.firstOrNull() ?: item.subtitle
    else -> null
}?.substringBefore(" · ")?.trim()?.takeIf(String::isNotBlank)

/** Whether [artist] is one of the track's credited artists: whole names only, so "Ray Charles Manson" isn't Ray Charles. */
fun isBy(track: Track, artist: String): Boolean {
    val wanted = normalizeName(artist)
    return (track.artists + listOfNotNull(track.subtitle))
        .flatMap { credit -> credit.split(CREDIT_SEPARATORS) }
        .any { normalizeName(it) == wanted }
}

/** A title without its bracketed parts: "Feeling Good (Remastered 2013)" → "feeling good". */
fun bareTitle(title: String) = normalizeName(title.replace(Regex("\\s*[(\\[].*?[)\\]]"), ""))

/** "Feeling Good (Remastered)" by "Nina Simone" and "feeling good" by "nina simone" are the same song. */
fun songKey(item: Item): String = normalizeName(mainArtist(item) ?: item.subtitle.orEmpty()) + "|" + bareTitle(item.title)

/**
 * Picks the same song as [wanted] out of [candidates]: same artist and same title, not a remix or live version unless
 * [wanted] is one, closest in length. Null when nothing is close enough to be honest about.
 */
fun bestMatch(wanted: Track, candidates: List<Item>): Track? {
    val artist = mainArtist(wanted) ?: return null
    val title = bareTitle(wanted.title)
    if (title.isEmpty()) return null
    val wantedVersion = VERSION_WORDS.containsMatchIn(wanted.title)
    return candidates.filterIsInstance<Track>()
        .filter { it.ref != wanted.ref && isBy(it, artist) }
        .filter { bareTitle(it.title) == title || bareTitle(it.title).removePrefix(normalizeName(artist)).trim() == title }
        .filter { wantedVersion || !VERSION_WORDS.containsMatchIn(it.title) }
        .filter { c -> wanted.durationMillis == null || c.durationMillis == null || abs(c.durationMillis!! - wanted.durationMillis!!) <= MAX_LENGTH_GAP_MILLIS }
        .minByOrNull { c -> if (wanted.durationMillis == null || c.durationMillis == null) MAX_LENGTH_GAP_MILLIS else abs(c.durationMillis!! - wanted.durationMillis!!) }
}

private const val MAX_LENGTH_GAP_MILLIS = 20_000L

/**
 * The same song as [wanted] from another switched-on source that has songs, when [wanted]'s own source failed. Asks
 * them all at once and keeps the closest match; sources earlier in the registry win ties.
 */
suspend fun SourceRegistry.findSameSong(wanted: Track, skipSource: String = wanted.ref.sourceId): Track? {
    val artist = mainArtist(wanted) ?: return null
    val results = search("$artist ${bareTitle(wanted.title)}", only = { it.info.id != skipSource && it.searchKind == ItemKind.SONG })
        .toList()
        .filterIsInstance<SourceResult.Ok<icu.kairo.kairoify.core.model.Page<Item>>>()
    val order = sources.map { it.info.id }
    val bySource = results.sortedBy { order.indexOf(it.source.id) }.flatMap { it.value.items }
    return bestMatch(wanted, bySource)
}
