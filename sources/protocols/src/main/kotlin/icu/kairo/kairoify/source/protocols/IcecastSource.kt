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

import icu.kairo.kairoify.core.model.Item
import icu.kairo.kairoify.core.model.ItemKind
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Page
import icu.kairo.kairoify.core.model.PageToken
import icu.kairo.kairoify.core.model.ResolvedStream
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.source.Capability
import icu.kairo.kairoify.core.source.MusicSource
import icu.kairo.kairoify.core.source.SourceException
import icu.kairo.kairoify.core.source.SourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString.Companion.encodeUtf8
import java.io.File
import java.io.IOException

/** One stream in the Icecast directory. */
data class IcecastStream(val name: String, val url: String, val type: String, val bitrate: Int?, val genre: String)

/**
 * The Icecast directory (dir.xiph.org): every Icecast server that announces itself. Its only machine-readable form
 * is one large file (~9 MB, ~33,000 entries), so the phone downloads it at most once a week and keeps just the
 * playable entries (those that list an address) in [cacheFile]. Searches run against that copy, on the phone.
 */
class IcecastSource(
    private val client: OkHttpClient,
    private val cacheFile: File,
    private val directoryUrl: HttpUrl = "https://dir.xiph.org/yp.xml".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
) : MusicSource {
    override val info = SourceInfo(ID, "Icecast directory")
    override val capabilities = setOf(Capability.SEARCH)
    override val searchKind = ItemKind.STATION
    override val searchTimeoutMillis: Long = 60_000

    private val lock = Mutex()
    private var streams: List<IcecastStream>? = null

    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        val words = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.isEmpty() || page != null) return Page.empty()
        val found = directory().filter { s -> val hay = "${s.name} ${s.genre}".lowercase(); words.all { it in hay } }
            .distinctBy { it.url }
            .take(MAX_RESULTS)
        return Page(found.map(::station), null)
    }

    override suspend fun resolve(ref: MediaRef): ResolvedStream = ResolvedStream(ref.itemId, isLive = true)

    private fun station(s: IcecastStream) = Station(
        ref = MediaRef(ID, s.url),
        title = s.name,
        subtitle = s.genre.ifBlank { null },
        tags = s.genre.split(' ', ',').filter(String::isNotBlank).take(6),
        codec = codecOf(s.type),
        bitrateKbps = s.bitrate,
        streamUrl = s.url,
    )

    /** The directory: from memory, else the saved copy if it's under a week old, else downloaded again. */
    suspend fun directory(): List<IcecastStream> = lock.withLock {
        streams?.let { return it }
        val fresh = cacheFile.exists() && now() - cacheFile.lastModified() < MAX_AGE_MILLIS
        val list = if (fresh) readCache() else runCatching { download() }.getOrElse { e ->
            // Offline or the directory is down: an old copy is better than nothing.
            if (cacheFile.exists()) readCache() else throw e
        }
        streams = list
        list
    }

    private suspend fun download(): List<IcecastStream> {
        val call = client.newCall(Request.Builder().url(directoryUrl).build())
        val list = try {
            runInterruptible(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw SourceException.Unavailable("Couldn't reach the Icecast directory.")
                    parseYellowPages(response.body.source())
                }
            }
        } catch (e: IOException) {
            throw SourceException.Unavailable("Couldn't reach the Icecast directory.", e)
        }
        runInterruptible(Dispatchers.IO) { writeCache(list) }
        return list
    }

    private fun writeCache(list: List<IcecastStream>) {
        cacheFile.parentFile?.mkdirs()
        val tmp = File(cacheFile.path + ".tmp")
        tmp.bufferedWriter().use { w ->
            list.forEach { s -> w.write(listOf(s.name, s.url, s.type, s.bitrate?.toString().orEmpty(), s.genre).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }); w.newLine() }
        }
        tmp.renameTo(cacheFile)
    }

    private suspend fun readCache(): List<IcecastStream> = runInterruptible(Dispatchers.IO) {
        cacheFile.readLines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 5) null else IcecastStream(f[0], f[1], f[2], f[3].toIntOrNull(), f[4])
        }
    }

    companion object {
        const val ID = "icecast"
        private const val MAX_RESULTS = 60
        private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * Reads yp.xml one <entry> at a time, so the 9 MB file is never held whole. Keeps entries with an http(s) address
 * and an audio type; drops video and empty addresses (most of the directory).
 */
fun parseYellowPages(source: okio.BufferedSource): List<IcecastStream> {
    val end = "</entry>".encodeUtf8()
    val out = ArrayList<IcecastStream>()
    while (true) {
        val at = source.indexOf(end)
        if (at < 0) break
        val entry = source.readUtf8(at + end.size)
        val url = field(entry, "listen_url")?.takeIf { it.toHttpUrlOrNull() != null } ?: continue
        val type = field(entry, "server_type").orEmpty().lowercase()
        if (type.startsWith("video/")) continue
        out += IcecastStream(
            name = field(entry, "server_name")?.ifBlank { null } ?: url.substringAfterLast('/'),
            url = url,
            type = type,
            bitrate = field(entry, "bitrate")?.toIntOrNull()?.takeIf { it in 1..10_000 },
            genre = field(entry, "genre").orEmpty(),
        )
    }
    return out
}

private fun field(entry: String, name: String): String? {
    val start = entry.indexOf("<$name>").takeIf { it >= 0 } ?: return null
    val stop = entry.indexOf("</$name>", start).takeIf { it >= 0 } ?: return null
    // Some servers announce names already escaped, so the file holds "&amp;amp;": unescape until nothing changes.
    var text = entry.substring(start + name.length + 2, stop)
    repeat(3) { val next = unescapeXml(text); if (next == text) return text.trim(); text = next }
    return text.trim()
}

private fun unescapeXml(text: String): String =
    text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&amp;", "&")

private fun codecOf(type: String): String? = when {
    "mpeg" in type -> "MP3"
    "aac" in type || "aacp" in type -> "AAC"
    "ogg" in type || "vorbis" in type -> "OGG"
    "opus" in type -> "OPUS"
    "flac" in type -> "FLAC"
    else -> null
}
