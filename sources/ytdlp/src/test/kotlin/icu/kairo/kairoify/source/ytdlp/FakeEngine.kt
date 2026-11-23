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

import icu.kairo.kairoify.core.ytdlp.EngineStream
import icu.kairo.kairoify.core.ytdlp.MusicEngine
import icu.kairo.kairoify.core.ytdlp.ResolveMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** A [MusicEngine] with scripted answers that records what it was asked. */
class FakeEngine : MusicEngine {
    val calls = mutableListOf<Pair<String, List<Any>>>()
    var flat: List<JsonObject> = emptyList()
    var searchResults: List<JsonObject> = emptyList()
    var radio: List<JsonObject> = emptyList()
    val resolveAnswers = mutableMapOf<ResolveMode, () -> EngineStream>()

    override suspend fun resolve(url: String, mode: ResolveMode): EngineStream {
        calls += "resolve" to listOf(url, mode)
        return (resolveAnswers[mode] ?: error("no answer for $mode"))()
    }

    override suspend fun flatSearch(queryUrl: String): List<JsonObject> {
        calls += "flatSearch" to listOf(queryUrl)
        return flat
    }

    override suspend fun ytMusicSearch(query: String, limit: Int): List<JsonObject> {
        calls += "ytMusicSearch" to listOf(query, limit)
        return searchResults
    }

    override suspend fun ytMusicRadio(videoId: String, limit: Int): List<JsonObject> {
        calls += "ytMusicRadio" to listOf(videoId, limit)
        return radio
    }
}

/** A recorded engine reply from src/test/resources/fixtures, as its list of objects under [key]. */
fun fixture(name: String, key: String): List<JsonObject> {
    val text = FakeEngine::class.java.getResource("/fixtures/$name")!!.readText()
    return Json.parseToJsonElement(text).jsonObject.getValue(key).jsonArray.map { it.jsonObject }
}

fun json(text: String): List<JsonObject> = Json.parseToJsonElement(text).jsonArray.map { it.jsonObject }
