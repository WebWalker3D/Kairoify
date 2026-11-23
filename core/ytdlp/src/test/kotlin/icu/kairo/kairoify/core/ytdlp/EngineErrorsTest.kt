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

import icu.kairo.kairoify.core.source.SourceException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EngineErrorsTest {
    @Test
    fun `missing songs are NotFound`() {
        listOf("ERROR: Video unavailable", "Private video. Sign in", "This track has been removed", "HTTP Error 404: Not Found", "Track does not exist")
            .forEach {
                val e = engineError("DownloadError", it)
                assertIs<SourceException.NotFound>(e, it)
                assertEquals("That song isn't available anymore.", e.message)
            }
    }

    @Test
    fun `region locks are Geoblocked`() {
        listOf("The uploader has not made this video available in your country", "geo restricted", "This content is geo-restricted")
            .forEach {
                val e = engineError("DownloadError", it)
                assertIs<SourceException.Geoblocked>(e, it)
                assertEquals("That song isn't available in your country.", e.message)
            }
    }

    @Test
    fun `rate limits are RateLimited`() {
        listOf("HTTP Error 429: Too Many Requests", "too many requests").forEach {
            val e = engineError("DownloadError", it)
            assertIs<SourceException.RateLimited>(e, it)
            assertEquals("The service is busy. Try again in a minute.", e.message)
        }
    }

    @Test
    fun `bot checks are Unavailable with their own words`() {
        val e = engineError("DownloadError", "Sign in to confirm you're not a bot")
        assertIs<SourceException.Unavailable>(e)
        assertEquals("YouTube wants to check you're not a bot. Try again later.", e.message)
    }

    @Test
    fun `network trouble is Unavailable`() {
        listOf("URLError" to "x", "ConnectionError" to "x", "TransportError" to "x", "SSLError" to "x", "DownloadError" to "Read timed out", "DownloadError" to "Failed to resolve host", "DownloadError" to "Connection reset")
            .forEach { (type, message) ->
                val e = engineError(type, message)
                assertIs<SourceException.Unavailable>(e, "$type $message")
                assertEquals("Couldn't reach the service. Check your internet.", e.message)
            }
    }

    @Test
    fun `extractor breakage needs an update`() {
        listOf("Unable to extract player response", "nsig extraction failed", "Signature extraction failed", "Unsupported URL: x", "Requested format is not available")
            .forEach {
                val e = engineError("DownloadError", it)
                assertIs<SourceException.NeedsUpdate>(e, it)
                assertEquals("The music engine needs an update.", e.message)
            }
        assertIs<SourceException.NeedsUpdate>(engineError("ExtractorError", "something odd"))
    }

    @Test
    fun `anything else is unplayable`() {
        listOf("odd thing", "ERROR: [bandcamp] x: No video formats found!", "No module named 'yt_dlp.extractor.x'").forEach {
            val e = engineError("DownloadError", it)
            assertIs<SourceException.Unplayable>(e, it)
            assertEquals("The music engine couldn't handle this.", e.message)
        }
    }

    @Test
    fun `age-restricted songs are unplayable`() {
        listOf("Sign in to confirm your age. This video may be inappropriate for some users.", "This video is age-restricted").forEach {
            val e = engineError("DownloadError", it)
            assertIs<SourceException.Unplayable>(e, it)
            assertEquals("That song is age-restricted.", e.message)
        }
    }

    @Test
    fun `subscription-only songs are unplayable`() {
        listOf("Join this channel to get access to members-only content", "This video requires payment to watch", "Music Premium only").forEach {
            val e = engineError("DownloadError", it)
            assertIs<SourceException.Unplayable>(e, it)
            assertEquals("That song needs a subscription.", e.message)
        }
    }
}
