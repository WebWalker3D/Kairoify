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
package icu.kairo.kairoify.source.protocols

import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** What an address answered: its type and, for anything that isn't audio, the start of its body. */
internal data class Probe(
    val finalUrl: String,
    val contentType: String?,
    val contentLength: Long?,
    /** The stream's own name, from Icecast/Shoutcast "icy-name" (only live streams send it). */
    val icyName: String?,
    val isLiveHint: Boolean,
    val body: String?,
)

/**
 * GETs [url] without downloading audio: headers are read, then audio is closed at once and only text bodies (up to
 * [MAX_TEXT] bytes) are read. Runs on the IO pool and stops when the coroutine is cancelled.
 */
internal suspend fun OkHttpClient.probe(url: String, sourceName: String, headers: Map<String, String> = emptyMap()): Probe {
    val httpUrl = url.toHttpUrlOrNull() ?: throw SourceException.NotFound("That isn't a web address Kairoify can open.")
    val request = Request.Builder().url(httpUrl).header("Icy-MetaData", "1").apply { headers.forEach { (k, v) -> header(k, v) } }.build()
    val call = newCall(request)
    return try {
        runInterruptible(Dispatchers.IO) {
            call.execute().use { response ->
                if (response.code == 401 || response.code == 403) throw SourceException.Unavailable("$sourceName: that address needs a sign-in.")
                if (!response.isSuccessful) throw SourceException.Unavailable("$sourceName: the address answered ${response.code}.")
                val type = response.header("Content-Type")
                val length = response.header("Content-Length")?.toLongOrNull()
                val icy = response.header("icy-name")?.trim()?.ifBlank { null }
                val live = icy != null || response.header("icy-metaint") != null || response.header("icy-br") != null
                val audio = isAudioType(type) && !isPlaylistType(type)
                val body = if (audio) null else response.body.source().let { src ->
                    src.request(MAX_TEXT)
                    src.buffer.readUtf8(minOf(src.buffer.size, MAX_TEXT))
                }
                Probe(response.request.url.toString(), type, length, icy, live, body)
            }
        }
    } catch (e: IOException) {
        throw SourceException.Unavailable("Couldn't reach that address.", e)
    }
}

internal fun isAudioType(type: String?): Boolean {
    val t = type?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return t.startsWith("audio/") || t == "application/ogg"
}

internal fun isPlaylistType(type: String?): Boolean {
    val t = type?.substringBefore(';')?.trim()?.lowercase() ?: return false
    return "mpegurl" in t || "scpls" in t || t == "application/pls+xml" || t == "application/xspf+xml" || t == "audio/m3u"
}

/** Audio file endings Kairoify plays directly. */
internal val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wave", "aif", "aiff", "mka", "weba", "webm", "mp4", "alac", "wma", "ape", "wv")

internal fun audioExtension(path: String): String? =
    path.substringBefore('?').substringBefore('#').substringAfterLast('/').substringAfterLast('.', "").lowercase().takeIf { it in AUDIO_EXTENSIONS }

/** A file name as a title: "03%20Rich_Like Me.mp3" → "03 Rich_Like Me". */
internal fun titleFromPath(path: String): String =
    java.net.URLDecoder.decode(path.substringBefore('?').trimEnd('/').substringAfterLast('/').replace("+", "%2B"), "UTF-8")
        .substringBeforeLast('.').ifBlank { path }

internal const val MAX_TEXT = 4L * 1024 * 1024
