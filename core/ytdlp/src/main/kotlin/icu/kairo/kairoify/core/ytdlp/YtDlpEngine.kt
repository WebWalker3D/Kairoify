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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * yt-dlp and ytmusicapi running in the app's own Python. Starts once (on first use or warm-up), and every call runs
 * on [dispatcher], never the main thread.
 */
class YtDlpEngine(
    private val python: PythonCall,
    private val cacheDir: File,
    private val qjsPath: String,
    private val updateZip: () -> File? = { null },
    private val onNeedsUpdate: () -> Unit = {},
    private val onUpdateFailed: (File) -> Unit = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MusicEngine {
    private val startLock = Mutex()

    @Volatile private var status: EngineStatus? = null

    /** What's running, once started; null before that. */
    val currentStatus: EngineStatus? get() = status

    /** Starts the engine if it isn't running yet. Safe to call from many places at once. */
    suspend fun start(): EngineStatus = status ?: startLock.withLock {
        status ?: run {
            val zip = updateZip()
            val reply = call("start", cacheDir.path, qjsPath, zip?.path)
            val started = EngineStatus(
                version = reply.string("version") ?: "unknown",
                updated = (reply["updated"] as? JsonPrimitive)?.booleanOrNull ?: false,
                updateError = reply.string("update_error"),
            )
            if (started.updateError != null && zip != null) onUpdateFailed(zip)
            status = started
            started
        }
    }

    override suspend fun resolve(url: String, mode: ResolveMode): EngineStream {
        start()
        val reply = call("resolve", url, mode.arg)
        return EngineStream(
            url = reply.string("url") ?: throw SourceException.NeedsUpdate("The music engine needs an update."),
            protocol = reply.string("protocol"),
            httpHeaders = (reply["http_headers"] as? JsonObject).orEmpty().mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() },
            durationSeconds = (reply["duration"] as? JsonPrimitive)?.doubleOrNull,
        )
    }

    override suspend fun flatSearch(queryUrl: String): List<JsonObject> {
        start()
        return call("flat_search", queryUrl).objects("entries")
    }

    override suspend fun ytMusicSearch(query: String, limit: Int): List<JsonObject> {
        start()
        return call("ytm_search", query, limit).objects("results")
    }

    override suspend fun ytMusicRadio(videoId: String, limit: Int): List<JsonObject> {
        start()
        return call("ytm_radio", videoId, limit).objects("tracks")
    }

    private suspend fun call(function: String, vararg args: Any?): JsonObject = withContext(dispatcher) {
        val text = try {
            python.call(function, *args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SourceException.Unavailable("The music engine failed: ${e.message}", e)
        }
        val reply = Json.parseToJsonElement(text).jsonObject
        (reply["error"] as? JsonObject)?.let { error ->
            val failure = engineError(error.string("type").orEmpty(), error.string("message").orEmpty())
            if (failure is SourceException.NeedsUpdate) onNeedsUpdate()
            throw failure
        }
        reply
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] ?: return emptyList()).let { element -> runCatching { element.jsonArray.map { it.jsonObject } }.getOrDefault(emptyList()) }
}
