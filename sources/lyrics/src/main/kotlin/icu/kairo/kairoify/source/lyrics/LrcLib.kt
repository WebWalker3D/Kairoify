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
package icu.kairo.kairoify.source.lyrics

import icu.kairo.kairoify.core.network.fetchText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.math.abs

/** One timed line of a song's lyrics. */
data class LyricLine(val atMillis: Long, val text: String)

/** A song's lyrics: timed lines when known, else plain text. */
data class Lyrics(val lines: List<LyricLine>, val plain: String?, val instrumental: Boolean = false) {
    val isSynced: Boolean get() = lines.isNotEmpty()

    /** The index of the line being sung at [positionMillis], or -1 before the first. */
    fun lineAt(positionMillis: Long): Int = lines.indexOfLast { it.atMillis <= positionMillis }
}

@Serializable
internal data class LrcRecord(
    val trackName: String? = null,
    val artistName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
)

/** LRCLIB: a free, open lyrics database (no key). Lyrics are looked up only for the song playing, only when asked. */
class LrcLib(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://lrclib.net/api/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The lyrics for [title] by [artist], closest in length to [durationMillis]; null when LRCLIB has none. */
    suspend fun find(artist: String, title: String, durationMillis: Long?): Lyrics? = try {
        val url = baseUrl.newBuilder().addPathSegment("search")
            .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build()
        val records = json.decodeFromString(ListSerializer(LrcRecord.serializer()), client.fetchText(url, "LRCLIB"))
        val seconds = durationMillis?.div(1000.0)
        records
            .filter { it.instrumental || !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
            .filter { seconds == null || it.duration == null || abs(it.duration - seconds) <= 10 }
            .sortedWith(compareBy({ it.syncedLyrics.isNullOrBlank() }, { seconds?.let { s -> abs((it.duration ?: s) - s) } ?: 0.0 }))
            .firstOrNull()
            ?.let { Lyrics(parseLrc(it.syncedLyrics.orEmpty()), it.plainLyrics?.takeIf(String::isNotBlank), it.instrumental) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

private val TIME = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

/** Reads LRC text: "[01:02.50] words". Lines with several times appear once per time; tags and blanks are dropped. */
fun parseLrc(text: String): List<LyricLine> = text.lineSequence().flatMap { raw ->
    val times = TIME.findAll(raw).toList()
    if (times.isEmpty()) return@flatMap emptySequence()
    val words = raw.substring(times.last().range.last + 1).trim()
    times.asSequence().map { m ->
        val (min, sec, frac) = m.destructured
        val millis = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        LyricLine(min.toLong() * 60_000 + sec.toLong() * 1_000 + millis, words)
    }
}.sortedBy { it.atMillis }.toList()
