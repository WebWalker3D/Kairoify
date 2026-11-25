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

import icu.kairo.kairoify.core.model.Album
import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Shelf
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.network.fetchText
import icu.kairo.kairoify.core.source.AlbumDetails
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.security.SecureRandom

/**
 * The person's own music servers that speak the Subsonic API: OpenSubsonic servers (Navidrome, Gonic, LMS…),
 * classic Subsonic/Airsonic, and Ampache (which answers Subsonic clients at /rest). Every saved server is searched
 * together. Signs in with the API's salted token (md5(password + salt)), never the plain password.
 */
class SubsonicSource(
    private val client: OkHttpClient,
    private val servers: () -> List<Server>,
    private val salt: () -> String = ::randomSalt,
) : MusicSource {
    override val info = SourceInfo(ID, "Your music servers")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.DETAILS, Capability.RELATED)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true }

    private fun mine() = servers().filter { it.kind == ServerKind.SUBSONIC }

    override suspend fun home(): List<Shelf> = coroutineScope {
        mine().map { server ->
            async {
                runCatching {
                    val reply = call(server, "getAlbumList2", "type" to "newest", "size" to "24")
                    val albums = reply.obj("albumList2")?.list("album").orEmpty().mapNotNull { album(server, it) }
                    Shelf("subsonic:${server.id}:newest", "New on ${server.name}", albums).takeIf { albums.isNotEmpty() }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val list = mine()
        if (list.isEmpty() || page != null) return Page.empty()
        val results = coroutineScope {
            list.map { server ->
                async {
                    val r = call(server, "search3", "query" to query, "songCount" to "40", "albumCount" to "15", "artistCount" to "0")
                        .obj("searchResult3")
                    r?.list("song").orEmpty().mapNotNull { song(server, it) } + r?.list("album").orEmpty().mapNotNull { album(server, it) }
                }
            }.map { runCatching { it.await() } }
        }
        val items = results.flatMap { it.getOrNull().orEmpty() }
        // Every server failed: say why (the first reason), so a wrong password doesn't look like "no results".
        if (items.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        return Page(items, null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val (server, id) = locate(ref, "album:")
        val album = call(server, "getAlbum", "id" to id).obj("album") ?: throw SourceException.NotFound("That album is no longer on ${server.name}.")
        val tracks = album.list("song").mapNotNull { song(server, it) }
        val head = album(server, album) ?: throw SourceException.NotFound("That album is no longer on ${server.name}.")
        return AlbumDetails(head.copy(trackCount = tracks.size), tracks)
    }

    override suspend fun related(ref: MediaRef): List<Item> {
        val (server, id) = locate(ref, "song:")
        return call(server, "getSimilarSongs", "id" to id, "count" to "40").obj("similarSongs")?.list("song").orEmpty().mapNotNull { song(server, it) }
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val (server, id) = locate(ref, "song:")
        return ResolvedStream(url(server, "stream", "id" to id).toString(), isLive = false)
    }

    /** Signs in to [server] once (Subsonic "ping"); throws the reason in plain words if it can't. */
    suspend fun check(server: Server) {
        call(server, "ping")
    }

    private fun locate(ref: MediaRef, prefix: String): Pair<Server, String> {
        val (serverId, rest) = splitServerRef(ref.itemId)
        val server = mine().firstOrNull { it.id == serverId }
            ?: throw SourceException.NotFound("That server was removed from Settings.")
        if (!rest.startsWith(prefix)) throw SourceException.NotFound("That item can't be opened.")
        return server to rest.removePrefix(prefix)
    }

    private fun song(server: Server, o: JsonObject): Track? {
        val id = o.str("id") ?: return null
        if (o.str("isVideo") == "true") return null
        val artist = o.str("artist")
        return Track(
            ref = MediaRef(ID, serverRef(server, "song:$id")),
            title = o.str("title") ?: return null,
            subtitle = artist,
            artworkUrl = o.str("coverArt")?.let { coverUrl(server, it) },
            artists = listOfNotNull(artist),
            album = o.str("album"),
            durationMillis = o.num("duration")?.times(1000),
        )
    }

    private fun album(server: Server, o: JsonObject): Album? {
        val id = o.str("id") ?: return null
        val artist = o.str("artist")
        return Album(
            ref = MediaRef(ID, serverRef(server, "album:$id")),
            title = o.str("name") ?: o.str("title") ?: return null,
            subtitle = listOfNotNull(artist, server.name).joinToString(" · "),
            artworkUrl = o.str("coverArt")?.let { coverUrl(server, it) },
            artists = listOfNotNull(artist),
            year = o.str("year"),
            trackCount = o.num("songCount")?.toInt(),
        )
    }

    private fun coverUrl(server: Server, id: String) = url(server, "getCoverArt", "id" to id, "size" to "400").toString()

    /** Calls [method] and returns the "subsonic-response" object, or throws the server's own error in plain words. */
    private suspend fun call(server: Server, method: String, vararg params: Pair<String, String>): JsonObject {
        val text = client.fetchText(url(server, method, *params), server.name)
        val root = try {
            json.parseToJsonElement(text).jsonObject["subsonic-response"]?.jsonObject
        } catch (e: IllegalArgumentException) {
            throw SourceException.Unavailable("${server.name} didn't answer like a Subsonic server. Check its address.", e)
        } ?: throw SourceException.Unavailable("${server.name} didn't answer like a Subsonic server. Check its address.")
        if (root.str("status") != "ok") {
            val error = root.obj("error")
            throw when (error?.num("code")) {
                40L, 41L -> SourceException.Unavailable("${server.name} didn't accept the user name or password.")
                70L -> SourceException.NotFound("That's no longer on ${server.name}.")
                else -> SourceException.Unavailable("${server.name}: ${error?.str("message") ?: "the server refused."}")
            }
        }
        return root
    }

    /** The endpoint URL with the Subsonic auth parameters (u, t = md5(password + s), s) and the client name. */
    internal fun url(server: Server, method: String, vararg params: Pair<String, String>): HttpUrl {
        val base = server.url.trim().trimEnd('/').removeSuffix("/rest").toHttpUrlOrNull()
            ?: throw SourceException.NotFound("${server.name}'s address isn't valid.")
        val s = salt()
        return base.newBuilder().addPathSegment("rest").addPathSegment("$method.view")
            .addQueryParameter("u", server.user)
            .addQueryParameter("t", md5(server.password + s))
            .addQueryParameter("s", s)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "Kairoify")
            .addQueryParameter("f", "json")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
    }

    companion object {
        const val ID = "subsonic"
    }
}

private fun randomSalt(): String = ByteArray(8).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.list(key: String): List<JsonObject> = when (val v: JsonElement? = this[key]) {
    is JsonArray -> v.mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(v) // some servers send a single item as an object
    else -> emptyList()
}
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
private fun JsonObject.num(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
