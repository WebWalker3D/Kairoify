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
package icu.kairo.kairoify.source.ytdlp

import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeMusicSourceTest {
    private val engine = FakeEngine()
    private val source = YouTubeMusicSource(engine)

    @Test
    fun `search maps songs from the recorded fixture`() = runTest {
        val results = fixture("ytm_search_daft_punk.json", "results")
        engine.searchResults = results
        val items = source.search("daft punk around the world").items
        assertTrue(items.isNotEmpty())
        items.forEach { item ->
            val track = assertIs<Track>(item)
            assertEquals("youtube", track.ref.sourceId)
            assertTrue(Regex("[A-Za-z0-9_-]{11}").matches(track.ref.itemId), track.ref.itemId)
            assertTrue(track.title.isNotBlank())
            assertTrue(track.artworkUrl.orEmpty().contains("w544-h544"), track.artworkUrl)
            assertTrue((track.durationMillis ?: 0) > 0)
            assertEquals("https://music.youtube.com/watch?v=${track.ref.itemId}", track.pageUrl)
        }
        assertEquals(results[0]["title"]!!.jsonPrimitive.content, items[0].title)
        assertTrue(items.any { (it as Track).album != null })
        assertTrue(items.all { !it.subtitle.isNullOrBlank() })
    }

    @Test
    fun `search passes the query intact`() = runTest {
        val query = "AC/DC \"Back in Black\" & Sigur Rós"
        source.search(query)
        assertEquals(listOf<Any>(query, 20), engine.calls.single().second)
    }

    @Test
    fun `entries without a videoId are dropped`() = runTest {
        engine.searchResults = json(
            """[{"resultType":"song","title":"No id","artists":[]},
                {"resultType":"song","videoId":"abcdefghijk","title":"Has id","artists":[{"name":"A"},{"name":"B"}],"duration_seconds":61}]""",
        )
        val item = source.search("x").items.single() as Track
        assertEquals("Has id", item.title)
        assertEquals("A, B", item.subtitle)
        assertEquals(listOf("A", "B"), item.artists)
        assertEquals(61_000L, item.durationMillis)
        assertNull(item.album)
    }

    @Test
    fun `radio drops the seed and maps watch tracks`() = runTest {
        val tracks = fixture("ytm_radio_Jb6gcoR266U.json", "tracks")
        engine.radio = tracks
        val related = source.related(MediaRef("youtube", "Jb6gcoR266U"))
        assertEquals(listOf<Any>("Jb6gcoR266U", 50), engine.calls.single().second)
        assertTrue(related.none { it.ref.itemId == "Jb6gcoR266U" })
        assertTrue(related.isNotEmpty())
        val second = tracks[1]
        val mapped = related.first { it.ref.itemId == second["videoId"]!!.jsonPrimitive.content } as Track
        assertEquals(parseLength(second["length"]!!.jsonPrimitive.content), mapped.durationMillis)
        assertTrue(mapped.artworkUrl.orEmpty().contains("w544-h544"))
    }

    private val stream = EngineStream("https://rr1.googlevideo.com/videoplayback?expire=1", "https", emptyMap(), 1.0)

    @Test
    fun `resolve uses the fast client first`() = runTest {
        engine.resolveAnswers[ResolveMode.FAST] = { stream }
        assertEquals(stream.url, source.resolve(MediaRef("youtube", "Jb6gcoR266U")).url)
        assertEquals(listOf<Any>("https://music.youtube.com/watch?v=Jb6gcoR266U", ResolveMode.FAST), engine.calls.single().second)
    }

    @Test
    fun `resolve falls back to QuickJS when the fast client fails`() = runTest {
        engine.resolveAnswers[ResolveMode.FAST] = { throw SourceException.NeedsUpdate("broken") }
        engine.resolveAnswers[ResolveMode.FULL] = { stream }
        assertEquals(stream.url, source.resolve(MediaRef("youtube", "x")).url)
        assertEquals(listOf(ResolveMode.FAST, ResolveMode.FULL), engine.calls.map { it.second[1] })
    }

    @Test
    fun `resolve does not retry a removed song`() = runTest {
        engine.resolveAnswers[ResolveMode.FAST] = { throw SourceException.NotFound("gone") }
        engine.resolveAnswers[ResolveMode.FULL] = { stream }
        assertFailsWith<SourceException.NotFound> { source.resolve(MediaRef("youtube", "x")) }
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `parseLength handles minutes and hours`() {
        assertEquals(321_000L, parseLength("5:21"))
        assertEquals(3_723_000L, parseLength("1:02:03"))
        assertNull(parseLength(null))
        assertNull(parseLength("live"))
    }
}
