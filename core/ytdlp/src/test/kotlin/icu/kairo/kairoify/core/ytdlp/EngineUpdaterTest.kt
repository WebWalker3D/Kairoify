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

import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineUpdaterTest {
    @get:Rule val temp = TemporaryFolder()

    private val server = MockWebServer()
    private var clock = 10_000_000_000L
    private val hour = 3_600_000L
    private var tag = "2026.11.02"
    private var zip = "PK fake yt-dlp zipapp".toByteArray()
    private var sums: () -> String = { "${sha256(zip)}  yt-dlp\n0000  yt-dlp.exe\n" }
    private val paths = mutableListOf<String>()
    private var latestBody: (() -> String)? = null

    private lateinit var updater: EngineUpdater

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Before fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(paths) { paths += path }
                return when (path) {
                    "/repos/yt-dlp/yt-dlp/releases/latest" -> MockResponse.Builder()
                        .body(latestBody?.invoke() ?: """{"tag_name":"$tag","assets":[{"browser_download_url":"https://evil.example/x"}]}""")
                        .build()
                    "/yt-dlp/yt-dlp/releases/download/$tag/SHA2-256SUMS" -> MockResponse.Builder().body(sums()).build()
                    "/yt-dlp/yt-dlp/releases/download/$tag/yt-dlp" -> MockResponse.Builder().body(okio.Buffer().write(zip)).build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
        updater = newUpdater()
    }

    private fun newUpdater(bundled: String = BUNDLED_YTDLP_VERSION) =
        EngineUpdater(OkHttpClient(), temp.root, { clock }, server.url("/"), server.url("/"), bundledVersion = bundled)

    @After fun stop() = server.close()

    @Test
    fun `not due within a day`() = runTest {
        updater.check()
        paths.clear()
        clock += 23 * hour
        assertEquals(UpdateResult.NotDue, updater.check())
        assertTrue(paths.isEmpty())
    }

    @Test
    fun `forced check ignores the daily limit but not the hourly one`() = runTest {
        tag = "2026.10.26"
        updater.check()
        clock += hour / 2
        assertIs<UpdateResult.UpToDate>(updater.check(force = true))
        clock += hour / 2
        assertEquals(UpdateResult.NotDue, updater.check(force = true))
    }

    @Test
    fun `up to date when latest is not newer`() = runTest {
        tag = BUNDLED_YTDLP_VERSION
        assertEquals(UpdateResult.UpToDate(BUNDLED_YTDLP_VERSION), updater.check())
        assertNull(updater.activeUpdate())
        assertTrue(paths.none { it.endsWith("/yt-dlp") })
    }

    @Test
    fun `downloads verifies and activates a newer version`() = runTest {
        assertEquals(UpdateResult.Downloaded("2026.11.02"), updater.check())
        val file = updater.activeUpdate()!!
        assertTrue(file.readBytes().contentEquals(zip))
        assertEquals("2026.11.02", updater.activeVersion())
        assertEquals("2026.11.02", newUpdater().activeVersion())
        assertTrue(paths.none { it.contains("evil") })
    }

    @Test
    fun `checksum mismatch leaves nothing active`() = runTest {
        sums = { "${"0".repeat(64)}  yt-dlp\n" }
        assertEquals(UpdateResult.Failed("Engine download didn't match its checksum"), updater.check())
        assertNull(updater.activeUpdate())
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.startsWith("yt-dlp") })
    }

    @Test
    fun `rejects a malformed tag`() = runTest {
        tag = "../../evil"
        assertEquals(UpdateResult.Failed("Unexpected engine version"), updater.check())
        assertEquals(listOf("/repos/yt-dlp/yt-dlp/releases/latest"), paths)
    }

    @Test
    fun `rollback keeps the running zip until the next start`() = runTest {
        updater.check()
        val running = updater.updateToLoad()!!
        updater.rollback()
        assertTrue(running.isFile)
        assertNull(updater.activeVersion())
        assertNull(updater.updateToLoad())
        assertFalse(running.exists())
    }

    @Test
    fun `a newer download keeps the zip the engine is running from`() = runTest {
        updater.check()
        val running = updater.updateToLoad()!!
        tag = "2026.11.09"
        zip = "PK newer zipapp".toByteArray()
        clock += 25 * hour
        assertEquals(UpdateResult.Downloaded("2026.11.09"), updater.check())
        assertTrue(running.isFile)
        assertEquals("2026.11.09", updater.activeVersion())
        val next = updater.updateToLoad()!!
        assertTrue(next.readBytes().contentEquals(zip))
        assertFalse(running.exists())
    }

    @Test
    fun `an app update with a newer built-in engine wins over an older download`() = runTest {
        updater.check()
        val upgraded = newUpdater(bundled = "2026.12.01")
        assertNull(upgraded.activeVersion())
        assertNull(upgraded.updateToLoad())
        assertTrue(temp.root.listFiles().orEmpty().none { it.name.endsWith(".zip") })
    }

    @Test
    fun `odd replies fail quietly`() = runTest {
        latestBody = { "<html>Sign in to the hotel Wi-Fi</html>" }
        assertEquals(UpdateResult.Failed("Couldn't check for an update."), updater.check())
    }

    @Test
    fun `a bad version is not activated again`() = runTest {
        updater.check()
        updater.markBad("2026.11.02")
        assertNull(updater.activeUpdate())
        clock += 25 * hour
        assertEquals(UpdateResult.UpToDate(BUNDLED_YTDLP_VERSION), updater.check())
        assertNull(updater.activeUpdate())
    }
}
