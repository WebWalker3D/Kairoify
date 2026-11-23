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

private val NOT_FOUND = listOf("video unavailable", "private video", "has been removed", "http error 404", "does not exist")
private val GEOBLOCKED = listOf("available in your country", "geo restrict", "geo-restrict")
private val RATE_LIMITED = listOf("http error 429", "too many requests")
private val BOT_CHECK = listOf("sign in to confirm", "not a bot")
private val NETWORK_TYPES = setOf("URLError", "ConnectionError", "TransportError", "SSLError")
private val NETWORK = listOf("timed out", "failed to resolve", "connection")
private val BROKEN_EXTRACTOR = listOf("unable to extract", "nsig", "signature", "unsupported url", "requested format is not available")

/** Turns a Python error from yt-dlp or ytmusicapi into a typed failure with words a person can read. */
fun engineError(type: String, message: String): SourceException {
    val text = message.lowercase()
    fun has(needles: List<String>) = needles.any { it in text }
    return when {
        has(NOT_FOUND) -> SourceException.NotFound("That song isn't available anymore.")
        has(GEOBLOCKED) -> SourceException.Geoblocked("That song isn't available in your country.")
        has(RATE_LIMITED) -> SourceException.RateLimited("The service is busy. Try again in a minute.")
        has(BOT_CHECK) -> SourceException.Unavailable("YouTube wants to check you're not a bot. Try again later.")
        type in NETWORK_TYPES || has(NETWORK) -> SourceException.Unavailable("Couldn't reach the service. Check your internet.")
        has(BROKEN_EXTRACTOR) || type == "ExtractorError" -> SourceException.NeedsUpdate("The music engine needs an update.")
        else -> SourceException.Unavailable(message.ifBlank { "The music engine failed." })
    }
}
