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
import icu.kairo.kairoify.core.model.License
import icu.kairo.kairoify.core.model.licenseFromUrl
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Funkwhale, the federated music library. Searches the public pods that let anyone listen without an account, plus
 * any pod the person adds under Your servers. Public pods also carry uploads nobody licensed, so from those only
 * tracks with a free license set (Creative Commons, Free Art License) are shown, each with its license; a pod the
 * person adds is their own library and is shown whole, like their Subsonic server. for a private pod the password field
 * holds an application token made in that pod's settings (sent as a Bearer token). Item ids are
 * "podKey|t:trackId" or "podKey|a:albumId".
 */
class FunkwhaleSource(
    private val client: OkHttpClient,
    private val servers: () -> List<Server>,
    private val publicPods: List<Server> = PUBLIC_PODS,
) : MusicSource {
    override val info = SourceInfo(ID, "Funkwhale")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.DETAILS, Capability.RELATED)
    override val searchKind = ItemKind.SONG

    private val json = Json { ignoreUnknownKeys = true }

    private fun pods(): List<Server> = publicPods + servers().filter { it.kind == ServerKind.FUNKWHALE }

    private fun licensedOnly(pod: Server) = publicPods.any { it.id == pod.id }

    override suspend fun home(): List<Shelf> = coroutineScope {
        pods().map { pod ->
            async {
                runCatching {
                    if (licensedOnly(pod)) {
                        val tracks = coroutineScope {
                            HOME_LICENSES.map { code ->
                                async { runCatching { results(get(pod, "tracks", "license" to code, "playable" to "true", "ordering" to "-creation_date", "page_size" to "12")) }.getOrNull().orEmpty() }
                            }.awaitAll().flatten()
                        }.mapNotNull { track(pod, it) }
                        return@runCatching Shelf("funkwhale:${pod.id}:cc", "Creative Commons on ${pod.name}", tracks).takeIf { tracks.isNotEmpty() }
                    }
                    val albums = results(get(pod, "albums", "playable" to "true", "ordering" to "-creation_date", "page_size" to "24"))
                        .mapNotNull { album(pod, it) }
                    Shelf("funkwhale:${pod.id}:new", "New on ${pod.name}", albums).takeIf { albums.isNotEmpty() }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        if (page != null) return Page.empty()
        val results = coroutineScope {
            pods().map { pod ->
                async {
                    val licensed = licensedOnly(pod)
                    // Few public uploads carry a license, so look further and keep only those.
                    val tracks = async { results(get(pod, "tracks", "q" to query, "playable" to "true", "page_size" to if (licensed) "100" else "40")).mapNotNull { track(pod, it) } }
                    if (licensed) return@async tracks.await()
                    val albums = async { results(get(pod, "albums", "q" to query, "playable" to "true", "page_size" to "15")).mapNotNull { album(pod, it) } }
                    tracks.await() + albums.await()
                }
            }.map { runCatching { it.await() } }
        }
        val items = results.flatMap { it.getOrNull().orEmpty() }
        if (items.isEmpty()) results.firstNotNullOfOrNull { it.exceptionOrNull() }?.let { throw it }
        return Page(items.distinctBy { it.ref }, null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val (pod, id) = locate(ref, "a:")
        val head = album(pod, (get(pod, "albums/$id/"))) ?: throw SourceException.NotFound("That album is no longer on ${pod.name}.")
        val tracks = results(get(pod, "tracks", "album" to id, "playable" to "true", "ordering" to "disc_number,position", "page_size" to "100"))
            .mapNotNull { track(pod, it) }
        return AlbumDetails(head.copy(trackCount = tracks.size), tracks)
    }

    /** More by the same artist, then more with the song's first tag; the song itself left out. */
    override suspend fun related(ref: MediaRef): List<Item> {
        val (pod, id) = locate(ref, "t:")
        val seed = (get(pod, "tracks/$id/"))
        val artistId = seed.obj("artist")?.str("id")
        val tag = seed.strings("tags").firstOrNull()
        return coroutineScope {
            val byArtist = async {
                artistId?.let { runCatching { results(get(pod, "tracks", "artist" to it, "playable" to "true", "page_size" to "20")) }.getOrNull() }.orEmpty()
            }
            val byTag = async {
                tag?.let { runCatching { results(get(pod, "tracks", "tag" to it, "playable" to "true", "ordering" to "random", "page_size" to "30")) }.getOrNull() }.orEmpty()
            }
            (byArtist.await() + byTag.await()).mapNotNull { track(pod, it) }.filter { it.ref != ref }.distinctBy { it.ref }
        }
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val (pod, id) = locate(ref, "t:")
        val o = (get(pod, "tracks/$id/"))
        if (licensedOnly(pod) && track(pod, o) == null) throw SourceException.NotFound("That song has no free license on ${pod.name}.")
        val upload = o.list("uploads").firstOrNull()
        val path = upload?.str("listen_url") ?: o.str("listen_url") ?: throw SourceException.NotFound("That song can't be played from ${pod.name}.")
        return ResolvedStream(absolute(pod, path), headers = auth(pod), mimeType = upload?.str("mimetype"), isLive = false, license = licenseOf(o.str("license")))
    }

    /** Opens the pod once with the token, so a wrong address or token is caught before it's saved. */
    suspend fun check(server: Server) {
        val o = (get(server, "tracks", "playable" to "true", "page_size" to "1"))
        if (o["count"] == null) throw SourceException.Unavailable("${server.name} didn't answer like a Funkwhale pod. Check its address.")
    }

    private fun locate(ref: MediaRef, prefix: String): Pair<Server, String> {
        val (podId, rest) = splitServerRef(ref.itemId)
        val pod = pods().firstOrNull { it.id == podId } ?: throw SourceException.NotFound("That Funkwhale pod was removed.")
        if (!rest.startsWith(prefix)) throw SourceException.NotFound("That item can't be opened.")
        return pod to rest.removePrefix(prefix)
    }

    private fun track(pod: Server, o: JsonObject): Track? {
        if (o.bool("is_playable") == false) return null
        val id = o.str("id") ?: return null
        val license = licenseOf(o.str("license"))
        if (license == null && licensedOnly(pod)) return null
        val artist = o.obj("artist")?.str("name")
        val album = o.obj("album")
        return Track(
            ref = MediaRef(ID, serverRef(pod, "t:$id")),
            title = o.str("title") ?: return null,
            subtitle = listOfNotNull(artist, pod.name).joinToString(" · "),
            artworkUrl = cover(pod, o.obj("cover")) ?: cover(pod, album?.obj("cover")),
            artists = listOfNotNull(artist),
            album = album?.str("title"),
            durationMillis = o.list("uploads").firstOrNull()?.num("duration")?.times(1000),
            pageUrl = absolute(pod, "/library/tracks/$id"),
            license = license,
        )
    }

    private fun album(pod: Server, o: JsonObject): Album? {
        if (o.bool("is_playable") == false) return null
        val id = o.str("id") ?: return null
        val artist = o.obj("artist")?.str("name")
        return Album(
            ref = MediaRef(ID, serverRef(pod, "a:$id")),
            title = o.str("title") ?: return null,
            subtitle = listOfNotNull(artist, pod.name).joinToString(" · "),
            artworkUrl = cover(pod, o.obj("cover")),
            artists = listOfNotNull(artist),
            year = o.str("release_date")?.take(4),
            trackCount = o.num("tracks_count")?.toInt(),
            pageUrl = absolute(pod, "/library/albums/$id"),
        )
    }

    private fun cover(pod: Server, c: JsonObject?): String? {
        val urls = c?.obj("urls") ?: return null
        return (urls.str("large_square_crop") ?: urls.str("medium_square_crop") ?: urls.str("original"))?.let { absolute(pod, it) }
    }

    private fun base(pod: Server): HttpUrl =
        pod.url.trim().trimEnd('/').toHttpUrlOrNull() ?: throw SourceException.NotFound("${pod.name}'s address isn't valid.")

    private fun absolute(pod: Server, path: String): String = base(pod).resolve(path)?.toString() ?: path

    private fun auth(pod: Server): Map<String, String> =
        if (pod.password.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${pod.password.trim()}")

    private suspend fun get(pod: Server, endpoint: String, vararg params: Pair<String, String>): JsonObject {
        val url = base(pod).newBuilder().addPathSegments("api/v1/").addPathSegments(endpoint.trimEnd('/') + "/")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(url).apply { auth(pod).forEach { (k, v) -> header(k, v) } }.build()
        val text = client.fetchText(request, pod.name)
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw SourceException.Unavailable("${pod.name} didn't answer like a Funkwhale pod.")
    }

    private fun results(o: JsonObject) = o.list("results")

    companion object {
        const val ID = "funkwhale"

        /** Licenses the public pod's Home shelf draws from. */
        private val HOME_LICENSES = listOf("cc-by-4.0", "cc-by-sa-4.0", "cc0-1.0")

        /** Funkwhale's license codes ("cc-by-sa-4.0", "cc-by-3.0-de", "cc0-1.0", "LAL-1.3") as a named license. */
        internal fun licenseOf(code: String?): License? {
            val c = code?.lowercase()?.trim() ?: return null
            if (c.startsWith("cc0")) return licenseFromUrl("https://creativecommons.org/publicdomain/zero/1.0/")
            if (c.startsWith("lal")) return License("Free Art License ${c.substringAfter('-', "1.3")}", "https://artlibre.org/licence/lal/en/")
            val m = Regex("^cc-((?:by|sa|nc|nd)(?:-(?:by|sa|nc|nd))*)-(\\d\\.\\d)").find(c) ?: return null
            return licenseFromUrl("https://creativecommons.org/licenses/${m.groupValues[1]}/${m.groupValues[2]}/")
        }

        /** Pods checked 2026-11-25 to let anyone listen without an account; only their licensed tracks are used. */
        val PUBLIC_PODS = listOf(Server(ServerKind.FUNKWHALE, "Tanuki Tunes", "https://tanukitunes.com"))
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.num(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() }
