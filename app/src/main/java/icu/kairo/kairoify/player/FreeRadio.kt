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

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceRegistry
import icu.kairo.kairoify.core.source.SourceResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * "Free radio": a station built on the phone from every switched-on music source, not just one service. The seed's
 * artist and the artists its listeners also play (from [similar]) are searched everywhere at once, and the songs
 * really by them are mixed into one queue. No server involved.
 */
class FreeRadio(
    private val registry: SourceRegistry,
    private val similar: suspend (String) -> List<String>,
    private val isMusic: (MusicSource) -> Boolean,
) {
    /** The station's queue, seed first; just the seed when nothing else was found, empty without an artist. */
    suspend fun build(seed: Item): List<Item> {
        val artist = seedArtist(seed) ?: return emptyList()
        val artists = (listOf(artist) + similarOrSuggested(seed, artist)).distinctBy { norm(it) }
        val permits = Semaphore(PARALLEL_ARTISTS)
        val found = coroutineScope {
            artists.map { name ->
                async {
                    permits.withPermit {
                        name to registry.search(name, isMusic).toList()
                            .filterIsInstance<SourceResult.Ok<icu.kairo.kairoify.core.model.Page<Item>>>()
                            .flatMap { it.value.items }
                    }
                }
            }.awaitAll()
        }
        return mixStation(seed, found)
    }

    /**
     * Builds the station a little at a time, so the seed can play at once: as each artist's search finishes, its best
     * song goes to [onBatch]; when every search is done, the rest follow, mixed so artists take turns.
     */
    suspend fun stream(seed: Item, onBatch: suspend (List<Item>) -> Unit) {
        val artist = seedArtist(seed) ?: return
        val artists = (listOf(artist) + similarOrSuggested(seed, artist)).distinctBy { norm(it) }
        val mixer = StationMixer(seed)
        val permits = Semaphore(PARALLEL_ARTISTS)
        val gate = kotlinx.coroutines.sync.Mutex()
        coroutineScope {
            artists.map { name ->
                async {
                    val items = permits.withPermit { searchEverywhere(name) }
                    gate.withLock { mixer.firstOf(name, items)?.let { onBatch(listOf(it)) } }
                }
            }.awaitAll()
        }
        mixer.rest().takeIf { it.isNotEmpty() }?.let { onBatch(it) }
    }

    private suspend fun searchEverywhere(name: String): List<Item> = registry.search(name, isMusic).toList()
        .filterIsInstance<SourceResult.Ok<icu.kairo.kairoify.core.model.Page<Item>>>()
        .flatMap { it.value.items }

    /** Similar artists; if that lookup fails, the artists of the songs the seed's own service would play next. */
    private suspend fun similarOrSuggested(seed: Item, artist: String): List<String> =
        similar(artist).ifEmpty {
            try {
                registry.related(seed.ref).mapNotNull(::seedArtist).filterNot { norm(it) == norm(artist) }.distinct().take(8)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }

    private companion object {
        const val PARALLEL_ARTISTS = 3
    }
}

/** The artist a station grows from: a song's or album's first artist. */
fun seedArtist(item: Item): String? = when (item) {
    is Track -> item.artists.firstOrNull() ?: item.subtitle
    is Album -> item.artists.firstOrNull() ?: item.subtitle
    else -> null
}?.substringBefore(" · ")?.trim()?.takeIf(String::isNotBlank)

/**
 * Mixes search results into a station: the seed first, then each artist in turn (at most [maxPerArtist] songs each),
 * only songs really by that artist, each song once even when several sources have it.
 */
fun mixStation(seed: Item, byArtist: List<Pair<String, List<Item>>>, maxPerArtist: Int = 4): List<Item> {
    val mixer = StationMixer(seed, maxPerArtist)
    val firsts = byArtist.mapNotNull { (artist, items) -> mixer.firstOf(artist, items) }
    return listOf(seed) + firsts + mixer.rest()
}

/** Keeps a station's songs unique and fair: at most [maxPerArtist] per artist, artists taking turns. */
private class StationMixer(seed: Item, private val maxPerArtist: Int = 4) {
    private val seen = mutableSetOf(songKey(seed))
    private val seenRefs = mutableSetOf(seed.ref)
    private val lanes = mutableListOf<MutableList<Track>>()

    /** Takes [artist]'s songs from [items] and hands back the first one to play; the others wait for [rest]. */
    fun firstOf(artist: String, items: List<Item>): Item? {
        val lane = items.filterIsInstance<Track>()
            .filter { track -> isBy(track, artist) }
            .filter { seenRefs.add(it.ref) && seen.add(songKey(it)) }
            .take(maxPerArtist)
            .toMutableList()
        val first = lane.removeFirstOrNull() ?: return null
        lanes += lane
        return first
    }

    /** Everything not yet handed out, one song per artist in turn. */
    fun rest(): List<Item> {
        val out = mutableListOf<Item>()
        while (lanes.any { it.isNotEmpty() }) lanes.forEach { lane -> lane.removeFirstOrNull()?.let(out::add) }
        return out
    }
}

/** Whether [artist] is one of the track's credited artists: whole names only, so "Ray Charles Manson" isn't Ray Charles. */
private fun isBy(track: Track, artist: String): Boolean {
    val wanted = norm(artist)
    return (track.artists + listOfNotNull(track.subtitle))
        .flatMap { credit -> credit.split(CREDIT_SEPARATORS) }
        .any { norm(it) == wanted }
}

/** How shared credits are written: "A / B", "A & B", "A, B", "A feat. B", "A ft. B", "A featuring B", "A x B", "A with B", "A · 1962". */
private val CREDIT_SEPARATORS = Regex("\\s*(?:/|&|,|;|·|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\bx\\b|\\bwith\\b)\\s*", RegexOption.IGNORE_CASE)

/** "Feeling Good (Remastered)" by "Nina Simone" and "feeling good" by "nina simone" are the same song. */
private fun songKey(item: Item): String {
    val artist = (item as? Track)?.let { seedArtist(it) } ?: item.subtitle.orEmpty()
    val title = item.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "")
    return norm(artist) + "|" + norm(title)
}

private fun norm(text: String) = text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
