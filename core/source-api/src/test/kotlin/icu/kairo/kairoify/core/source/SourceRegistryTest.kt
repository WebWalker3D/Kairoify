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
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private class FakeSource(
    id: String,
    override val capabilities: Set<Capability> = setOf(Capability.SEARCH),
    private val delayMillis: Long = 0,
    private val failWith: Throwable? = null,
    override val searchTimeoutMillis: Long? = null,
    private val relatedItems: List<Item> = emptyList(),
) : MusicSource {
    override val info = SourceInfo(id, id.replaceFirstChar { it.uppercase() })
    override val searchKind = ItemKind.SONG
    var reported = 0
    val queries = mutableListOf<String>()

    override suspend fun related(ref: MediaRef): List<Item> = relatedItems

    override suspend fun details(ref: MediaRef): AlbumDetails =
        AlbumDetails(Album(ref, "Album ${ref.itemId}"), listOf(Track(MediaRef(ref.sourceId, "${ref.itemId}/1"), "One")))

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        queries += query
        delay(delayMillis)
        failWith?.let { throw it }
        return Page(emptyList(), null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        failWith?.let { throw it }
        return ResolvedStream(url = "https://example.com/${ref.itemId}", isLive = true)
    }

    override suspend fun reportPlay(ref: MediaRef) {
        reported++
        failWith?.let { throw it }
    }
}

class SourceRegistryTest {
    @Test
    fun `search emits results in completion order`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("slow", delayMillis = 500), FakeSource("fast", delayMillis = 100)))
        val results = registry.search("jazz").toList()
        assertEquals(listOf("fast", "slow"), results.map { it.source.id })
    }

    @Test
    fun `failing source does not stop others`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("bad", failWith = IOException("boom")), FakeSource("good")))
        val results = registry.search("jazz").toList()
        val failed = results.filterIsInstance<SourceResult.Failed>().single()
        assertIs<SourceException.Unavailable>(failed.error)
        assertEquals("boom", failed.error.message)
        assertEquals("good", results.filterIsInstance<SourceResult.Ok<*>>().single().source.id)
    }

    @Test
    fun `search timeout becomes Unavailable`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("slow", delayMillis = 9_000)))
        val failed = registry.search("jazz").toList().single()
        assertIs<SourceResult.Failed>(failed)
        assertIs<SourceException.Unavailable>(failed.error)
        assertEquals("Slow took too long", failed.error.message)
    }

    @Test
    fun `search skips sources without SEARCH`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("radio", capabilities = setOf(Capability.LIVE)), FakeSource("music")))
        assertEquals(listOf("music"), registry.search("jazz").toList().map { it.source.id })
    }

    @Test
    fun `resolve routes by sourceId`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("a"), FakeSource("b")))
        assertEquals("https://example.com/x1", registry.resolve(MediaRef("b", "x1")).url)
    }

    @Test
    fun `resolve unknown source throws NotFound`() = runTest {
        val registry = SourceRegistry(listOf(FakeSource("a")))
        assertFailsWith<SourceException.NotFound> { registry.resolve(MediaRef("zzz", "x1")) }
    }

    @Test
    fun `reportPlay swallows exceptions`() = runTest {
        val source = FakeSource("a", failWith = IOException("down"))
        SourceRegistry(listOf(source)).reportPlay(MediaRef("a", "x1"))
        assertTrue(source.reported == 1)
    }
    @Test
    fun `search uses a source's own timeout`() = runTest {
        val registry = SourceRegistry(
            listOf(
                FakeSource("quick", delayMillis = 100, searchTimeoutMillis = 50),
                FakeSource("normal", delayMillis = 100),
            ),
            searchTimeoutMillis = 1_000,
        )
        val results = registry.search("jazz").toList().associateBy { it.source.id }
        val failed = results.getValue("quick")
        assertIs<SourceResult.Failed>(failed)
        assertEquals("Quick took too long", failed.error.message)
        assertIs<SourceResult.Ok<*>>(results.getValue("normal"))
    }

    @Test
    fun `searchSource searches only that source`() = runTest {
        val a = FakeSource("a")
        val b = FakeSource("b")
        val result = SourceRegistry(listOf(a, b)).searchSource("b", "jazz")
        assertIs<SourceResult.Ok<*>>(result)
        assertEquals(emptyList(), a.queries)
        assertEquals(listOf("jazz"), b.queries)
    }

    @Test
    fun `searchSource maps failures`() = runTest {
        val result = SourceRegistry(listOf(FakeSource("a", failWith = IOException("boom")))).searchSource("a", "jazz")
        assertIs<SourceResult.Failed>(result)
        assertIs<SourceException.Unavailable>(result.error)
        assertEquals("boom", result.error.message)
    }

    @Test
    fun `related delegates to the item's source`() = runTest {
        val song = Track(MediaRef("b", "2"), "Two")
        val registry = SourceRegistry(listOf(FakeSource("a"), FakeSource("b", relatedItems = listOf(song))))
        assertEquals(listOf<Item>(song), registry.related(MediaRef("b", "1")))
    }

    @Test
    fun `details delegates to the album's source`() = runTest {
        val details = SourceRegistry(listOf(FakeSource("a"), FakeSource("b"))).details(MediaRef("b", "x"))
        assertEquals("Album x", details.album.title)
        assertEquals("x/1", details.tracks.single().ref.itemId)
    }

    @Test
    fun `switched-off sources are left out of search and home`() = runTest {
        val on = FakeSource("on", capabilities = setOf(Capability.SEARCH, Capability.HOME))
        val off = FakeSource("off", capabilities = setOf(Capability.SEARCH, Capability.HOME))
        val registry = SourceRegistry(listOf(on, off), isEnabled = { it.info.id != "off" })
        assertEquals(listOf("on"), registry.search("jazz").toList().map { it.source.id })
        assertEquals(listOf("on"), registry.home().toList().map { it.source.id })
        assertEquals(listOf("on"), registry.enabledSources().map { it.info.id })
        assertEquals("off", registry.get("off").info.id)
    }
}
