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

import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import icu.kairo.kairoify.core.source.SourceRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StreamResolverTest {
    private class CountingSource(var stream: ResolvedStream) : MusicSource {
        override val info = SourceInfo("s", "S")
        override val capabilities = emptySet<Capability>()
        override val searchKind = ItemKind.SONG
        var calls = 0
        var failWith: Exception? = null

        override suspend fun resolve(ref: MediaRef): ResolvedStream {
            calls++
            failWith?.let { throw it }
            return stream
        }
    }

    private val ref = MediaRef("s", "1")
    private var clock = 1_000_000L
    private val minute = 60_000L

    private fun resolverFor(source: CountingSource) = StreamResolver(SourceRegistry(listOf(source))) { clock }

    @Test
    fun `caches a stream until shortly before it expires`() = runTest {
        val source = CountingSource(ResolvedStream("https://a", isLive = false, expiresAtMillis = clock + 10 * minute))
        val resolver = resolverFor(source)
        resolver.resolve(ref)
        clock += 8 * minute
        resolver.resolve(ref)
        assertEquals(1, source.calls)
        clock += 90_000
        resolver.resolve(ref)
        assertEquals(2, source.calls)
    }

    @Test
    fun `live streams are cached for ten minutes`() = runTest {
        val source = CountingSource(ResolvedStream("http://radio", isLive = true))
        val resolver = resolverFor(source)
        resolver.resolve(ref)
        clock += 9 * minute
        resolver.resolve(ref)
        assertEquals(1, source.calls)
        clock += 2 * minute
        resolver.resolve(ref)
        assertEquals(2, source.calls)
    }

    @Test
    fun `invalidate forces a fresh resolve`() = runTest {
        val source = CountingSource(ResolvedStream("https://a", isLive = false))
        val resolver = resolverFor(source)
        resolver.resolve(ref)
        resolver.invalidate(ref)
        resolver.resolve(ref)
        assertEquals(2, source.calls)
    }

    @Test
    fun `failures are not cached`() = runTest {
        val source = CountingSource(ResolvedStream("https://a", isLive = false))
        source.failWith = SourceException.Unavailable("down")
        val resolver = resolverFor(source)
        assertFailsWith<SourceException.Unavailable> { resolver.resolve(ref) }
        source.failWith = null
        assertEquals("https://a", resolver.resolve(ref).url)
        assertEquals(2, source.calls)
    }
}
