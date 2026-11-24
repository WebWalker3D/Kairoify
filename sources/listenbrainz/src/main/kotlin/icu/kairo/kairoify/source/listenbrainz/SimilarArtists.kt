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
package icu.kairo.kairoify.source.listenbrainz

import icu.kairo.kairoify.core.network.fetchText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
internal data class MbArtist(val id: String? = null, val name: String? = null, val score: Int = 0)

@Serializable
internal data class MbSearch(val artists: List<MbArtist> = emptyList())

@Serializable
internal data class LbSimilar(val name: String? = null)

/**
 * Artists people listen to alongside a given artist, from two open, keyless music databases: MusicBrainz finds the
 * artist, ListenBrainz says who its listeners also play. Used to build free-radio stations on the phone.
 */
class SimilarArtists(
    private val client: OkHttpClient,
    private val musicBrainz: HttpUrl = "https://musicbrainz.org/".toHttpUrl(),
    private val listenBrainz: HttpUrl = "https://labs.api.listenbrainz.org/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** Up to [limit] similar artists, most similar first; empty when the artist isn't known or a lookup fails. */
    suspend fun similarTo(artist: String, limit: Int = 8): List<String> {
        // MusicBrainz allows one request a second per address and answers 503 when busy: ask again, a second apart.
        repeat(ATTEMPTS) { attempt ->
            try {
                return lookUp(artist, limit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        return emptyList()
    }

    private suspend fun lookUp(artist: String, limit: Int): List<String> {
        val search = musicBrainz.newBuilder().addPathSegments("ws/2/artist/")
            .addQueryParameter("query", "artist:\"${artist.trim()}\"").addQueryParameter("limit", "1")
            .addQueryParameter("fmt", "json").build()
        val found = json.decodeFromString(MbSearch.serializer(), client.fetchText(search, NAME)).artists.firstOrNull()
        val id = found?.takeIf { it.score >= MIN_SCORE }?.id
        val similar = if (id == null) {
            emptyList()
        } else {
            val url = listenBrainz.newBuilder().addPathSegments("similar-artists/json")
                .addQueryParameter("artist_mbids", id).addQueryParameter("algorithm", ALGORITHM).build()
            json.decodeFromString(ListSerializer(LbSimilar.serializer()), client.fetchText(url, NAME))
                .mapNotNull { it.name?.takeIf(String::isNotBlank) }
                .filterNot { it.equals(found.name, ignoreCase = true) }
                .distinct()
                .take(limit)
        }
        return similar
    }

    private companion object {
        const val NAME = "ListenBrainz"
        const val MIN_SCORE = 90
        const val ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1_100L
        const val ALGORITHM = "session_based_days_7500_session_300_contribution_5_threshold_10_limit_100_filter_True_skip_30"
    }
}
