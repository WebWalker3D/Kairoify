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
package icu.kairo.kairoify.source.radiobrowser

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

/** GETs a URL and returns the body, mapping HTTP trouble onto [SourceException]. Cancels the call with the coroutine. */
internal suspend fun OkHttpClient.getBody(url: HttpUrl): String = suspendCancellableCoroutine { cont ->
    val call = newCall(Request.Builder().url(url).build())
    cont.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            cont.resumeWithException(SourceException.Unavailable("Radio Browser is unreachable", e))
        }

        override fun onResponse(call: Call, response: Response) {
            response.use {
                when {
                    it.code == 429 -> cont.resumeWithException(SourceException.RateLimited("Radio Browser is busy. Try again in a minute."))
                    !it.isSuccessful -> cont.resumeWithException(
                        SourceException.Unavailable("Radio Browser is unreachable", IOException("HTTP ${it.code}")),
                    )
                    else -> try {
                        cont.resume(it.body.string())
                    } catch (e: IOException) {
                        cont.resumeWithException(SourceException.Unavailable("Radio Browser is unreachable", e))
                    }
                }
            }
        }
    })
}
