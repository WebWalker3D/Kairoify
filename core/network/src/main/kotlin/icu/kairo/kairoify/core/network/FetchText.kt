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
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** GETs [url] and returns the body; failures become [SourceException]s that name [sourceName]. Cancels with the coroutine. */
suspend fun OkHttpClient.fetchText(url: HttpUrl, sourceName: String): String = fetchText(Request.Builder().url(url).build(), sourceName)

/** Sends [request] (any method, any headers) and returns the body, with the same failures as the GET form. */
suspend fun OkHttpClient.fetchText(request: Request, sourceName: String): String = suspendCancellableCoroutine { cont ->
    val call = newCall(request)
    cont.invokeOnCancellation { call.cancel() }
    val unreachable = "Couldn't reach $sourceName."
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(SourceException.Unavailable(unreachable, e))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                when {
                    it.code == 401 || it.code == 403 && request.header("Authorization") != null ->
                        cont.resumeWithException(SourceException.Unavailable("$sourceName didn't accept the user name or password."))
                    it.code == 429 -> cont.resumeWithException(SourceException.RateLimited("$sourceName is busy. Try again in a minute."))
                    !it.isSuccessful -> cont.resumeWithException(SourceException.Unavailable(unreachable, IOException("HTTP ${it.code}")))
                    else -> try {
                        cont.resume(it.body.string())
                    } catch (e: IOException) {
                        cont.resumeWithException(SourceException.Unavailable(unreachable, e))
                    }
                }
            }
        }
    })
}
