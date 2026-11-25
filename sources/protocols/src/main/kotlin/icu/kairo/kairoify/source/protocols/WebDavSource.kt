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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/** One file or folder a WebDAV server listed. [path] is the URL path, percent-encoded as the server sent it. */
data class DavEntry(val path: String, val name: String, val isFolder: Boolean, val contentType: String?)

/**
 * Music folders on WebDAV servers the person added (Nextcloud, ownCloud, a NAS, any WebDAV share). Folders open as
 * albums of the audio files in them; "Artist/Album/03 Song.mp3" layouts give the song its artist. Search walks each
 * server's folders once per run of the app (at most [MAX_FOLDERS]) and matches folder and file names.
 */
class WebDavSource(
    private val client: OkHttpClient,
    private val servers: () -> List<Server>,
) : MusicSource {
    override val info = SourceInfo(ID, "Your WebDAV folders")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.DETAILS)
    override val searchKind = ItemKind.SONG

    private val indexes = ConcurrentHashMap<String, List<DavEntry>>()
    private val indexLock = Mutex()

    private fun mine() = servers().filter { it.kind == ServerKind.WEBDAV }

    override suspend fun home(): List<Shelf> = coroutineScope {
        mine().map { server ->
            async {
                runCatching {
                    val root = rootPath(server)
                    val children = list(server, root)
                    val items = children.filter { it.isFolder }.map { folderAlbum(server, it) } +
                        children.filter { isAudio(it) }.map { track(server, it) }
                    Shelf("webdav:${server.id}", server.name, items).takeIf { items.isNotEmpty() }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val words = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty() || page != null) return Page.empty()
        val items = coroutineScope {
            mine().map { server ->
                async {
                    index(server).filter { e -> val hay = decodedPath(e.path).lowercase(); words.all { it in hay } }
                        .take(60)
                        .map { if (it.isFolder) folderAlbum(server, it) else track(server, it) }
                }
            }.awaitAll().flatten()
        }
        return Page(items, null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val (server, path) = locate(ref)
        val entries = list(server, path)
        val tracks = entries.filter(::isAudio).sortedBy { it.name.lowercase() }.map { track(server, it) }
        val name = decodedPath(path).trimEnd('/').substringAfterLast('/').ifBlank { server.name }
        val art = entries.firstOrNull { !it.isFolder && it.name.lowercase().let { n -> n == "cover.jpg" || n == "folder.jpg" || n == "cover.png" || n == "front.jpg" } }
        val album = Album(
            ref, name, artistOf(server, path + "x")?.let { "$it · ${server.name}" } ?: server.name,
            art?.let { absolute(server, it.path).toString() }, listOfNotNull(artistOf(server, path + "x")), trackCount = tracks.size,
        )
        val folders = entries.filter { it.isFolder }.sortedBy { it.name.lowercase() }.map { folderAlbum(server, it) }
        return AlbumDetails(album, tracks, folders)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val (server, path) = locate(ref)
        return ResolvedStream(absolute(server, path).toString(), headers = auth(server), isLive = false)
    }

    /** Lists [server]'s folder once; throws the reason in plain words if it can't. */
    suspend fun check(server: Server) {
        list(server, rootPath(server))
        indexes.remove(server.id)
    }

    private fun locate(ref: MediaRef): Pair<Server, String> {
        val (serverId, path) = splitServerRef(ref.itemId)
        val server = mine().firstOrNull { it.id == serverId } ?: throw SourceException.NotFound("That server was removed from Settings.")
        return server to path
    }

    private fun folderAlbum(server: Server, e: DavEntry) = Album(
        MediaRef(ID, serverRef(server, e.path)), e.name, artistOf(server, e.path.trimEnd('/') + "/x")?.takeIf { it != e.name } ?: server.name,
    )

    private fun track(server: Server, e: DavEntry): Track {
        val artist = artistOf(server, e.path)
        return Track(
            ref = MediaRef(ID, serverRef(server, e.path)),
            title = e.name.substringBeforeLast('.').replace(Regex("^\\d{1,3}[ ._-]+"), "").ifBlank { e.name },
            subtitle = artist,
            artists = listOfNotNull(artist),
            album = decodedPath(e.path).split('/').filter(String::isNotBlank).dropLast(1).lastOrNull(),
        )
    }

    /** The artist from the part of the path below the server's own folder, so ".../files/<user>/Music" isn't read as one. */
    private fun artistOf(server: Server, path: String): String? = artistFromPath(path.removePrefix(rootPath(server).trimEnd('/')))

    private fun isAudio(e: DavEntry) = !e.isFolder && (e.contentType?.startsWith("audio/") == true || audioExtension(e.name) != null)

    /** Every folder and audio file under the server's address, walked breadth first, once per run of the app. */
    private suspend fun index(server: Server): List<DavEntry> = indexLock.withLock {
        indexes[server.id]?.let { return it }
        val out = mutableListOf<DavEntry>()
        val queue = ArrayDeque(listOf(rootPath(server)))
        var folders = 0
        while (queue.isNotEmpty() && folders < MAX_FOLDERS) {
            val path = queue.removeFirst()
            folders++
            val children = runCatching { list(server, path) }.getOrElse { if (folders == 1) throw it else emptyList() }
            children.forEach { e ->
                if (e.isFolder) { out += e; queue += e.path } else if (isAudio(e)) out += e
            }
        }
        indexes[server.id] = out
        out
    }

    /** PROPFIND with Depth: 1: the folder's direct children (the folder itself left out). */
    internal suspend fun list(server: Server, path: String): List<DavEntry> {
        val url = absolute(server, path)
        val request = Request.Builder().url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()))
            .header("Depth", "1")
            .apply { auth(server).forEach { (k, v) -> header(k, v) } }
            .build()
        val xml = client.fetchText(request, server.name)
        return parseMultistatus(xml).filter { it.path.trimEnd('/') != url.encodedPath.trimEnd('/') }
    }

    private fun rootPath(server: Server): String = baseUrl(server).encodedPath.let { if (it.endsWith('/')) it else "$it/" }

    private fun baseUrl(server: Server): HttpUrl = server.url.trim().toHttpUrlOrNull()
        ?: throw SourceException.NotFound("${server.name}'s address isn't valid.")

    private fun absolute(server: Server, path: String): HttpUrl =
        baseUrl(server).newBuilder().encodedPath(if (path.startsWith('/')) path else "/$path").build()

    private fun auth(server: Server): Map<String, String> =
        if (server.user.isBlank()) emptyMap() else mapOf("Authorization" to Credentials.basic(server.user, server.password, Charsets.UTF_8))

    companion object {
        const val ID = "webdav"
        private const val MAX_FOLDERS = 400
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:displayname/><d:getcontenttype/></d:prop></d:propfind>"""
    }
}

/** A WebDAV multistatus reply as entries. Hrefs may be full URLs or paths; both become paths. */
fun parseMultistatus(xml: String): List<DavEntry> = parseXml(xml, "The WebDAV folder listing").descendants("response").mapNotNull { r ->
    val href = r.textOf("href") ?: return@mapNotNull null
    val path = href.toHttpUrlOrNull()?.encodedPath ?: href
    val prop = r.descendants("prop").firstOrNull()
    val folder = prop?.descendants("collection")?.isNotEmpty() == true || path.endsWith('/')
    val name = prop?.textOf("displayname") ?: decodedPath(path).trimEnd('/').substringAfterLast('/')
    DavEntry(path, name, folder, prop?.textOf("getcontenttype"))
}

internal fun decodedPath(path: String): String = runCatching { java.net.URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)

/** "…/Artist/Album/03 Song.mp3" → "Artist": the folder two above the file, when the path is deep enough. */
internal fun artistFromPath(path: String): String? {
    val parts = decodedPath(path).split('/').filter(String::isNotBlank)
    return if (parts.size >= 3) parts[parts.size - 3] else null
}
