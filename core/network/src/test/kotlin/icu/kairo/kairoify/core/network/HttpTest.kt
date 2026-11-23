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

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpTest {
    @Test
    fun `request carries Kairoify user agent`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse.Builder().body("ok").build())
            server.start()
            val client = createHttpClient(kairoifyUserAgent("0.1.0"), cacheDir = null)
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            assertEquals(
                "Kairoify/0.1.0 (+https://github.com/WebWalker3D/Kairoify)",
                server.takeRequest().headers["User-Agent"],
            )
        }
    }
}
