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
package icu.kairo.kairoify.core.ytdlp

import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtDlpEngineTest {
    /** Answers Python calls from a map and records them. */
    private class FakePython(val answers: MutableMap<String, String> = mutableMapOf()) : PythonCall {
        val calls = mutableListOf<Pair<String, List<Any?>>>()

        override fun call(function: String, vararg args: Any?): String {
            synchronized(calls) { calls += function to args.toList() }
            if (function == "start") Thread.sleep(20)
            return answers[function] ?: error("no answer for $function")
        }
    }

    private val started = """{"version":"2026.10.26","updated":false,"update_error":null}"""

    private fun engine(
        python: FakePython,
        updateZip: File? = null,
        onNeedsUpdate: () -> Unit = {},
        onUpdateFailed: (File) -> Unit = {},
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = YtDlpEngine(python, File("/cache"), "/lib/libqjs.so", { updateZip }, onNeedsUpdate, onUpdateFailed, dispatcher)

    @Test
    fun `concurrent start runs python once`() = runTest {
        val python = FakePython(mutableMapOf("start" to started))
        val engine = engine(python, dispatcher = kotlinx.coroutines.Dispatchers.IO)
        listOf(async { engine.start() }, async { engine.start() }, async { engine.start() }).awaitAll()
        assertEquals(1, python.calls.count { it.first == "start" })
    }

    @Test
    fun `first search starts the engine`() = runTest {
        val python = FakePython(mutableMapOf("start" to started, "ytm_search" to """{"results":[]}"""))
        engine(python, dispatcher = StandardTestDispatcher(testScheduler)).ytMusicSearch("jazz", 20)
        assertEquals(listOf("start", "ytm_search"), python.calls.map { it.first })
    }

    @Test
    fun `passes the update zip to start`() = runTest {
        val python = FakePython(mutableMapOf("start" to started))
        engine(python, updateZip = File("/x/yt-dlp-2026.11.02.zip"), dispatcher = StandardTestDispatcher(testScheduler)).start()
        assertEquals(listOf<Any?>("/cache", "/lib/libqjs.so", "/x/yt-dlp-2026.11.02.zip"), python.calls.single().second)
    }

    @Test
    fun `reports a broken update`() = runTest {
        val python = FakePython(mutableMapOf("start" to """{"version":"2026.10.26","updated":false,"update_error":"bad zip"}"""))
        val failed = mutableListOf<File>()
        val zip = File("/x/yt-dlp-2026.11.02.zip")
        val status = engine(python, updateZip = zip, onUpdateFailed = { failed += it }, dispatcher = StandardTestDispatcher(testScheduler)).start()
        assertEquals("bad zip", status.updateError)
        assertEquals(listOf(zip), failed)
    }

    @Test
    fun `parses a progressive stream`() = runTest {
        val url = "https://rr1.googlevideo.com/videoplayback?expire=1790000000&itag=140&ip=203.0.113.7"
        val python = FakePython(
            mutableMapOf(
                "start" to started,
                "resolve" to """{"url":"$url","protocol":"https","ext":"m4a","acodec":"mp4a.40.2","http_headers":{"User-Agent":"UA"},"duration":430.0}""",
            ),
        )
        val stream = engine(python, dispatcher = StandardTestDispatcher(testScheduler)).resolve("https://music.youtube.com/watch?v=x", ResolveMode.FAST)
        assertEquals(listOf<Any?>("https://music.youtube.com/watch?v=x", "fast"), python.calls.last().second)
        val resolved = stream.toResolvedStream()
        assertEquals(url, resolved.url)
        assertEquals(1_790_000_000_000L, resolved.expiresAtMillis)
        assertNull(resolved.mimeType)
        assertEquals(mapOf("User-Agent" to "UA"), resolved.headers)
        assertEquals(false, resolved.isLive)
    }

    @Test
    fun `parses an hls stream`() = runTest {
        val python = FakePython(
            mutableMapOf("start" to started, "resolve" to """{"url":"https://cf-hls/x.m3u8","protocol":"m3u8_native","http_headers":{}}"""),
        )
        val stream = engine(python, dispatcher = StandardTestDispatcher(testScheduler)).resolve("https://soundcloud.com/a/b", ResolveMode.FAST)
        assertEquals(StreamMime.HLS, stream.toResolvedStream().mimeType)
        assertNull(stream.toResolvedStream().expiresAtMillis)
    }

    @Test
    fun `python errors become typed exceptions`() = runTest {
        val python = FakePython(
            mutableMapOf("start" to started, "resolve" to """{"error":{"type":"DownloadError","message":"ERROR: [youtube] x: Video unavailable"}}"""),
        )
        assertFailsWith<SourceException.NotFound> {
            engine(python, dispatcher = StandardTestDispatcher(testScheduler)).resolve("u", ResolveMode.FAST)
        }
    }

    @Test
    fun `needs-update errors call the hook`() = runTest {
        var hooked = 0
        val python = FakePython(
            mutableMapOf("start" to started, "resolve" to """{"error":{"type":"ExtractorError","message":"Unable to extract nsig function"}}"""),
        )
        assertFailsWith<SourceException.NeedsUpdate> {
            engine(python, onNeedsUpdate = { hooked++ }, dispatcher = StandardTestDispatcher(testScheduler)).resolve("u", ResolveMode.FULL)
        }
        assertEquals(1, hooked)
    }

    @Test
    fun `queries reach python unchanged`() = runTest {
        val query = "AC/DC \"Back in Black\" & Sigur Rós"
        val python = FakePython(mutableMapOf("start" to started, "ytm_search" to """{"results":[{"videoId":"a"}]}"""))
        val results = engine(python, dispatcher = StandardTestDispatcher(testScheduler)).ytMusicSearch(query, 20)
        assertEquals(listOf<Any?>(query, 20), python.calls.last().second)
        assertTrue(results.single().containsKey("videoId"))
    }
}
