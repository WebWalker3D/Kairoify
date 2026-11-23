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

import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bandcamp's own search box, as its website uses it. yt-dlp can play Bandcamp but can't search it. This endpoint is
 * unofficial, so failures say "Couldn't reach Bandcamp" and leave the other sources alone.
 */
class BandcampSearchApi(private val client: OkHttpClient, private val baseUrl: HttpUrl = "https://bandcamp.com/".toHttpUrl()) {
    suspend fun searchTracks(query: String): List<BcResult> {
        val body = buildJsonObject {
            put("search_text", JsonPrimitive(query))
            put("search_filter", JsonPrimitive("t"))
            put("full_page", JsonPrimitive(false))
            put("fan_id", JsonNull)
        }.toString()
        val request = Request.Builder()
            .url(baseUrl.resolve("api/bcsearch_public_api/1/autocomplete_elastic")!!)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = client.call(request)
        val reply = try {
            lenientJson.decodeFromString(BcReply.serializer(), text)
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable(UNREACHABLE, e)
        }
        return reply.auto.results.filter { it.type == "t" }
    }

    private suspend fun OkHttpClient.call(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(SourceException.Unavailable(UNREACHABLE, e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    when {
                        it.code == 429 -> cont.resumeWithException(SourceException.RateLimited("Bandcamp is busy. Try again in a minute."))
                        !it.isSuccessful -> cont.resumeWithException(SourceException.Unavailable(UNREACHABLE, IOException("HTTP ${it.code}")))
                        else -> try {
                            cont.resume(it.body.string())
                        } catch (e: IOException) {
                            cont.resumeWithException(SourceException.Unavailable(UNREACHABLE, e))
                        }
                    }
                }
            }
        })
    }

    private companion object {
        const val UNREACHABLE = "Couldn't reach Bandcamp."
    }
}
