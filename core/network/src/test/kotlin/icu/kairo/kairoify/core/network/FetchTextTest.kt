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
package icu.kairo.kairoify.core.network

import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FetchTextTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()

    @BeforeTest fun start() = server.start()

    @AfterTest fun stop() = server.close()

    @Test
    fun `returns the body`() = runTest {
        server.enqueue(MockResponse.Builder().body("hello").build())
        assertEquals("hello", client.fetchText(server.url("/x"), "Audius"))
    }

    @Test
    fun `rate limits say the service is busy`() = runTest {
        server.enqueue(MockResponse.Builder().code(429).build())
        val e = assertFailsWith<SourceException.RateLimited> { client.fetchText(server.url("/x"), "Audius") }
        assertEquals("Audius is busy. Try again in a minute.", e.message)
    }

    @Test
    fun `server errors say it can't be reached`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).build())
        val e = assertFailsWith<SourceException.Unavailable> { client.fetchText(server.url("/x"), "Internet Archive") }
        assertEquals("Couldn't reach Internet Archive.", e.message)
    }

    @Test
    fun `dropped connections say it can't be reached`() = runTest {
        server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        assertFailsWith<SourceException.Unavailable> { client.fetchText(server.url("/x"), "Audius") }
    }
}
