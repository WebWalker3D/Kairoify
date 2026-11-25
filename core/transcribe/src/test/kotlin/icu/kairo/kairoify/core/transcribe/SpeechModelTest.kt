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
package icu.kairo.kairoify.core.transcribe

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechModelTest {
    private val server = MockWebServer().apply { start() }
    private val dir = Files.createTempDirectory("model").toFile()
    private val bytes = ByteArray(5000) { it.toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @After fun stop() {
        server.close()
        dir.deleteRecursively()
    }

    @Test
    fun `downloads once, checks the hash and reports progress`() = runTest {
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(bytes)).build())
        val model = SpeechModel(OkHttpClient(), dir, server.url("/m.bin").toString(), sha, sizeBytes = 5000)
        assertFalse(model.isReady)
        val seen = mutableListOf<Float>()
        val file = model.file { seen += it }
        assertTrue(model.isReady)
        assertEquals(5000, file.length())
        assertEquals(1f, seen.last())
        model.file {}
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a download with the wrong hash is thrown away`() = runTest {
        server.enqueue(MockResponse.Builder().body("not the model").build())
        val model = SpeechModel(OkHttpClient(), dir, server.url("/m.bin").toString(), sha, sizeBytes = 5000)
        assertFailsWith<java.io.IOException> { model.file {} }
        assertFalse(model.isReady)
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }
}
