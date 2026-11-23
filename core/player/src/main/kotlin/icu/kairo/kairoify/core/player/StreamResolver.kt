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
package icu.kairo.kairoify.core.player

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.source.SourceRegistry
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns a [MediaRef] into a playable stream, remembering the answer in memory for a while. HLS radio re-reads its
 * playlist every few seconds; without the cache each refresh would ask the source again. Nothing here is persisted.
 */
class StreamResolver(
    private val registry: SourceRegistry,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val stream: ResolvedStream, val validUntilMillis: Long)

    private val cache = ConcurrentHashMap<MediaRef, Entry>()

    suspend fun resolve(ref: MediaRef): ResolvedStream {
        cache[ref]?.takeIf { now() < it.validUntilMillis }?.let { return it.stream }
        val stream = registry.resolve(ref)
        cache[ref] = Entry(stream, validUntil(stream))
        return stream
    }

    /** Forget a stream, e.g. after its URL stopped working. */
    fun invalidate(ref: MediaRef) {
        cache.remove(ref)
    }

    private fun validUntil(stream: ResolvedStream): Long =
        stream.expiresAtMillis?.let { it - EXPIRY_MARGIN_MILLIS }
            ?: (now() + if (stream.isLive) LIVE_TTL_MILLIS else ON_DEMAND_TTL_MILLIS)

    private companion object {
        const val EXPIRY_MARGIN_MILLIS = 60_000L
        const val LIVE_TTL_MILLIS = 10 * 60_000L
        const val ON_DEMAND_TTL_MILLIS = 30 * 60_000L
    }
}
