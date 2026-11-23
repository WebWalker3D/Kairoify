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

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface SourceResult<out T> {
    val source: SourceInfo

    data class Ok<T>(override val source: SourceInfo, val value: T) : SourceResult<T>

    data class Failed(override val source: SourceInfo, val error: SourceException) : SourceResult<Nothing>
}

/** All registered sources. Fans requests out in parallel and keeps one source's failure from touching the rest. */
class SourceRegistry(
    sources: List<MusicSource>,
    private val searchTimeoutMillis: Long = 8_000,
    private val homeTimeoutMillis: Long = 10_000,
) {
    val sources: List<MusicSource> = sources.toList()

    fun get(sourceId: String): MusicSource =
        sources.firstOrNull { it.info.id == sourceId } ?: throw SourceException.NotFound("Unknown source '$sourceId'")

    fun search(query: String): Flow<SourceResult<Page<Item>>> = channelFlow {
        sources.filter { Capability.SEARCH in it.capabilities }.forEach { source ->
            launch { send(searchOne(source, query)) }
        }
    }

    /** Searches one source again, e.g. when the person taps Retry on its section. */
    suspend fun searchSource(sourceId: String, query: String): SourceResult<Page<Item>> = searchOne(get(sourceId), query)

    /** What to play after [ref] ("Start radio"). Throws [SourceException] when the source fails. */
    suspend fun related(ref: MediaRef): List<Item> = get(ref.sourceId).related(ref)

    /** An album's tracks. Throws [SourceException] when the source fails. */
    suspend fun details(ref: MediaRef): AlbumDetails = get(ref.sourceId).details(ref)

    fun home(): Flow<SourceResult<List<Shelf>>> =
        fanOut(Capability.HOME, homeTimeoutMillis) { it.home() }

    suspend fun resolve(ref: MediaRef): ResolvedStream = get(ref.sourceId).resolve(ref)

    suspend fun reportPlay(ref: MediaRef) {
        try {
            get(ref.sourceId).reportPlay(ref)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Play counting is best-effort; never let it affect playback.
        }
    }

    private suspend fun searchOne(source: MusicSource, query: String): SourceResult<Page<Item>> =
        attempt(source, source.searchTimeoutMillis ?: searchTimeoutMillis) { it.search(query) }

    private fun <T> fanOut(
        capability: Capability,
        timeoutMillis: Long,
        call: suspend (MusicSource) -> T,
    ): Flow<SourceResult<T>> = channelFlow {
        sources.filter { capability in it.capabilities }.forEach { source ->
            launch { send(attempt(source, timeoutMillis, call)) }
        }
    }

    private suspend fun <T> attempt(
        source: MusicSource,
        timeoutMillis: Long,
        call: suspend (MusicSource) -> T,
    ): SourceResult<T> = try {
        SourceResult.Ok(source.info, withTimeout(timeoutMillis) { call(source) })
    } catch (e: TimeoutCancellationException) {
        SourceResult.Failed(source.info, SourceException.Unavailable("${source.info.displayName} took too long", e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceException) {
        SourceResult.Failed(source.info, e)
    } catch (e: Exception) {
        SourceResult.Failed(source.info, SourceException.Unavailable(e.message ?: "${source.info.displayName} failed", e))
    }
}
