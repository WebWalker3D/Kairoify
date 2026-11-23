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
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SoundCloudSourceTest {
    private val engine = FakeEngine()
    private val source = SoundCloudSource(engine)

    @Test
    fun `search maps tracks from the recorded fixture`() = runTest {
        engine.flat = fixture("sc_search_daft_punk.json", "entries")
        val items = source.search("daft punk").items
        assertTrue(items.isNotEmpty())
        items.forEach { item ->
            val track = assertIs<Track>(item)
            assertEquals("soundcloud", track.ref.sourceId)
            assertTrue(Regex("[^/]+/[^/]+").matches(track.ref.itemId), track.ref.itemId)
            assertTrue(track.isHls)
            assertEquals("https://soundcloud.com/${track.ref.itemId}", track.pageUrl)
            assertTrue((track.durationMillis ?: 0) !in 29_000..31_000)
            assertTrue(track.artworkUrl == null || track.artworkUrl!!.contains("t500x500"), track.artworkUrl)
        }
        assertTrue(items.size < engine.flat.size)
    }

    @Test
    fun `30-second previews are dropped`() = runTest {
        engine.flat = json(
            """[{"title":"Preview","duration":30.0,"webpage_url":"https://soundcloud.com/a/preview","uploader":"a"},
                {"title":"Full","duration":246.4,"webpage_url":"https://soundcloud.com/a/full","uploader":"a"}]""",
        )
        val item = source.search("x").items.single() as Track
        assertEquals("Full", item.title)
        assertEquals("a/full", item.ref.itemId)
        assertEquals(246_400L, item.durationMillis)
        assertEquals("a", item.subtitle)
        assertEquals(listOf("a"), item.artists)
    }

    @Test
    fun `artwork prefers the 500 pixel image`() = runTest {
        engine.flat = json(
            """[{"title":"T","duration":100,"webpage_url":"https://soundcloud.com/a/t","uploader":"a","thumbnails":[
                {"id":"mini","url":"https://i1.sndcdn.com/x-mini.jpg","width":16},
                {"id":"t500x500","url":"https://i1.sndcdn.com/x-t500x500.jpg","width":500},
                {"id":"original","url":"https://i1.sndcdn.com/x-original.jpg"}]},
                {"title":"U","duration":100,"webpage_url":"https://soundcloud.com/a/u","uploader":"a","thumbnails":[
                {"id":"small","url":"https://i1.sndcdn.com/y-small.jpg","width":32},
                {"id":"large","url":"https://i1.sndcdn.com/y-large.jpg","width":100}]}]""",
        )
        val (t, u) = source.search("x").items
        assertEquals("https://i1.sndcdn.com/x-t500x500.jpg", t.artworkUrl)
        assertEquals("https://i1.sndcdn.com/y-large.jpg", u.artworkUrl)
    }

    @Test
    fun `passes the query intact`() = runTest {
        source.search("AC/DC \"Back in Black\" & Sigur Rós")
        assertEquals(listOf<Any>("scsearch20:AC/DC \"Back in Black\" & Sigur Rós"), engine.calls.single().second)
    }

    @Test
    fun `entries off soundcloud are skipped`() = runTest {
        engine.flat = json("""[{"title":"Odd","duration":100,"url":"https://api.soundcloud.com/tracks/1"}]""")
        assertTrue(source.search("x").items.isEmpty())
    }

    @Test
    fun `resolve asks for the track page and returns hls`() = runTest {
        engine.resolveAnswers[ResolveMode.FAST] = { EngineStream("https://cf-hls-media.sndcdn.com/playlist.m3u8", "m3u8_native", emptyMap(), 246.4) }
        val stream = source.resolve(MediaRef("soundcloud", "artist/song"))
        assertEquals(listOf<Any>("https://soundcloud.com/artist/song", ResolveMode.FAST), engine.calls.single().second)
        assertEquals(StreamMime.HLS, stream.mimeType)
    }
}
