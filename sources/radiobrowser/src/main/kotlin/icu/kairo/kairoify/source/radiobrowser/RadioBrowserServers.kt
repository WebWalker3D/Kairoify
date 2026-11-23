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

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/** Radio Browser asks clients to pick a random mirror from its server list rather than hard-coding one. */
class RadioBrowserServers(
    private val client: OkHttpClient,
    private val discoveryUrl: HttpUrl = "https://all.api.radio-browser.info/json/servers".toHttpUrl(),
) {
    @Volatile private var chosen: HttpUrl? = null

    suspend fun baseUrl(): HttpUrl = chosen ?: discover().also { chosen = it }

    private suspend fun discover(): HttpUrl = try {
        val names = json.decodeFromString<List<ServerDto>>(client.getBody(discoveryUrl))
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        names.randomOrNull()?.let { "https://$it/".toHttpUrl() } ?: FALLBACK
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        FALLBACK
    }

    private companion object {
        val FALLBACK = "https://all.api.radio-browser.info/".toHttpUrl()
        val json = Json { ignoreUnknownKeys = true }
    }
}
