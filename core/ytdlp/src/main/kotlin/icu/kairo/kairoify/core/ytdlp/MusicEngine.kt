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

import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.StreamMime
import kotlinx.serialization.json.JsonObject

/** yt-dlp's two ways in: the fast JS-less YouTube client, or the full one that runs YouTube's JavaScript in QuickJS. */
/** HLS: for services that also offer DASH, which the player can't use (Mixcloud). */
enum class ResolveMode(val arg: String) { FAST("fast"), FULL("full"), HLS("hls") }

/** A stream yt-dlp found. Never persisted: YouTube's URLs are tied to this phone's IP and expire. */
data class EngineStream(
    val url: String,
    val protocol: String?,
    val httpHeaders: Map<String, String>,
    val durationSeconds: Double?,
) {
    fun toResolvedStream(): ResolvedStream = ResolvedStream(
        url = url,
        headers = httpHeaders,
        mimeType = if (protocol?.startsWith("m3u8") == true) StreamMime.HLS else null,
        isLive = false,
        expiresAtMillis = EXPIRE.find(url)?.groupValues?.get(1)?.toLongOrNull()?.let { it * 1000 },
    )

    private companion object {
        val EXPIRE = Regex("[?&]expire=(\\d+)")
    }
}

/** Which yt-dlp is running, and whether a downloaded update failed to load. */
data class EngineStatus(val version: String, val updated: Boolean, val updateError: String?)

/** The on-device music engine, as the yt-dlp sources see it. Results are raw JSON objects each source maps itself. */
interface MusicEngine {
    suspend fun resolve(url: String, mode: ResolveMode): EngineStream

    /** yt-dlp "flat" search, e.g. "scsearch20:query". */
    suspend fun flatSearch(queryUrl: String): List<JsonObject>

    suspend fun ytMusicSearch(query: String, limit: Int): List<JsonObject>

    /** YouTube Music's radio for a song: its "watch playlist". */
    suspend fun ytMusicRadio(videoId: String, limit: Int): List<JsonObject>
}
