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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** A media server found on the network: its name and where its ContentDirectory service answers. */
data class UpnpDevice(val id: String, val name: String, val controlUrl: String)

/** One entry of a DIDL-Lite listing: a container (folder) or an item with its first audio resource. */
data class DidlEntry(
    val id: String,
    val title: String,
    val isContainer: Boolean,
    val artist: String? = null,
    val album: String? = null,
    val artworkUrl: String? = null,
    val audioUrl: String? = null,
    val durationMillis: Long? = null,
)

/**
 * UPnP/DLNA media servers on the person's own network (Plex, Jellyfin, MiniDLNA, Synology, Windows Media Player,
 * many NAS boxes). Found with SSDP multicast, browsed with the ContentDirectory service; nothing leaves the network.
 * [multicast] holds Android's multicast lock while searching (without it Wi-Fi drivers drop the replies).
 */
class UpnpSource(
    private val client: OkHttpClient,
    private val multicast: suspend (suspend () -> List<String>) -> List<String> = { it() },
    private val discoverMillis: Int = 2500,
) : MusicSource {
    override val info = SourceInfo(ID, "Media servers on your network")
    override val capabilities = setOf(Capability.HOME, Capability.SEARCH, Capability.DETAILS)
    override val searchKind = ItemKind.SONG

    private val lock = Mutex()
    private var devices: List<UpnpDevice>? = null
    private var foundAt = 0L

    override suspend fun home(): List<Shelf> = coroutineScope {
        devices(refresh = true).map { device ->
            async {
                runCatching {
                    val items = browse(device, "0").map { item(device, it) }
                    Shelf("upnp:${device.id}", device.name, items).takeIf { items.isNotEmpty() }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    /** Uses the server's own Search when it has one; servers without it just don't add results. */
    override suspend fun search(query: String, page: PageToken?): Page<Item> {
        if (query.isBlank() || page != null) return Page.empty()
        val escaped = query.replace("\\", "\\\\").replace("\"", "\\\"")
        val criteria = "upnp:class derivedfrom \"object.item.audioItem\" and (dc:title contains \"$escaped\" or upnp:artist contains \"$escaped\" or upnp:album contains \"$escaped\")"
        val items = coroutineScope {
            devices().map { device ->
                async { runCatching { soap(device, "Search", "ContainerID" to "0", "SearchCriteria" to criteria) }.getOrDefault(emptyList()).map { item(device, it) } }
            }.awaitAll().flatten()
        }
        return Page(items.take(80), null)
    }

    override suspend fun details(ref: MediaRef): AlbumDetails {
        val (deviceId, rest) = splitServerRef(ref.itemId)
        val device = device(deviceId)
        val objectId = rest.removePrefix("c|")
        val entries = browse(device, objectId)
        val tracks = entries.filter { !it.isContainer }.mapNotNull { item(device, it) as? Track }
        val folders = entries.filter { it.isContainer }.map { item(device, it) as Album }
        val album = Album(ref, entries.firstNotNullOfOrNull { it.album } ?: device.name, device.name,
            tracks.firstNotNullOfOrNull { it.artworkUrl }, trackCount = tracks.size)
        return AlbumDetails(album, tracks, folders)
    }

    /** Track ids carry the address the server gave, so a song plays without asking the server again. */
    override suspend fun resolve(ref: MediaRef): ResolvedStream {
        val url = splitServerRef(ref.itemId).second.substringAfter("i|").substringAfter('|')
        if (url.toHttpUrlOrNull() == null) throw SourceException.Unplayable("That server didn't give an address for this song.")
        return ResolvedStream(url, isLive = false)
    }

    private fun item(device: UpnpDevice, e: DidlEntry): Item =
        if (e.isContainer) {
            Album(MediaRef(ID, "${device.id}|c|${e.id}"), e.title, e.artist ?: device.name, e.artworkUrl, listOfNotNull(e.artist))
        } else {
            Track(
                MediaRef(ID, "${device.id}|i|${e.id.replace("|", "%7C")}|${e.audioUrl.orEmpty()}"), e.title, e.artist,
                e.artworkUrl, listOfNotNull(e.artist), e.album, e.durationMillis,
            )
        }

    private suspend fun device(id: String): UpnpDevice =
        devices().firstOrNull { it.id == id } ?: devices(refresh = true).firstOrNull { it.id == id }
            ?: throw SourceException.Unavailable("That media server isn't on this network right now.")

    private suspend fun browse(device: UpnpDevice, objectId: String): List<DidlEntry> =
        soap(device, "Browse", "ObjectID" to objectId, "BrowseFlag" to "BrowseDirectChildren")
            .filter { it.isContainer || it.audioUrl != null }

    /** Calls a ContentDirectory action and returns the DIDL-Lite entries in its Result. */
    private suspend fun soap(device: UpnpDevice, action: String, vararg args: Pair<String, String>): List<DidlEntry> {
        val params = args.toList() + listOf("Filter" to "*", "StartingIndex" to "0", "RequestedCount" to "300", "SortCriteria" to "")
        val body = buildString {
            append("""<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body>""")
            append("""<u:$action xmlns:u="$CONTENT_DIRECTORY">""")
            params.forEach { (k, v) -> append("<$k>").append(escapeXml(v)).append("</$k>") }
            append("</u:$action></s:Body></s:Envelope>")
        }
        val request = Request.Builder().url(device.controlUrl)
            .post(body.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .header("SOAPACTION", "\"$CONTENT_DIRECTORY#$action\"")
            .build()
        val reply = parseXml(client.fetchText(request, device.name), device.name)
        val result = reply.descendants("Result").firstOrNull()?.textContent ?: return emptyList()
        return parseDidl(result)
    }

    /** Media servers on the network, searched for again at most every five minutes (or when asked). */
    suspend fun devices(refresh: Boolean = false): List<UpnpDevice> = lock.withLock {
        val cached = devices
        if (cached != null && !refresh && System.currentTimeMillis() - foundAt < 5 * 60_000) return cached
        val locations = multicast { ssdpSearch() }.distinct()
        val found = coroutineScope {
            locations.map { location -> async { runCatching { describe(location) }.getOrNull() } }.awaitAll().filterNotNull()
        }.distinctBy { it.id }
        devices = found
        foundAt = System.currentTimeMillis()
        found
    }

    private suspend fun describe(location: String): UpnpDevice? {
        val url = location.toHttpUrlOrNull() ?: return null
        val root = parseXml(client.fetchText(url, "A media server"), "A media server's description")
        val base = root.textOf("URLBase")?.toHttpUrlOrNull() ?: url
        val device = root.kid("device") ?: return null
        val service = root.descendants("service").firstOrNull { it.textOf("serviceType")?.contains("ContentDirectory") == true } ?: return null
        val control = service.textOf("controlURL")?.let { base.resolve(it) } ?: return null
        val udn = device.textOf("UDN") ?: location
        return UpnpDevice(sha1(udn).take(10), device.textOf("friendlyName") ?: url.host, control.toString())
    }

    /** SSDP M-SEARCH for media servers; returns the LOCATION of every reply within [discoverMillis]. */
    private suspend fun ssdpSearch(): List<String> = runInterruptible(Dispatchers.IO) {
        val message = (
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
            ).toByteArray()
        val group = InetAddress.getByName("239.255.255.250")
        val out = mutableListOf<String>()
        DatagramSocket().use { socket ->
            socket.soTimeout = 400
            repeat(2) { socket.send(DatagramPacket(message, message.size, group, 1900)) }
            val until = System.currentTimeMillis() + discoverMillis
            val buffer = ByteArray(4096)
            while (System.currentTimeMillis() < until) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                ssdpLocation(String(packet.data, 0, packet.length))?.let(out::add)
            }
        }
        out
    }

    companion object {
        const val ID = "upnp"
        private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"
    }
}

/** The LOCATION header of an SSDP reply, if it's an http address. */
fun ssdpLocation(reply: String): String? = reply.lineSequence()
    .firstOrNull { it.startsWith("location:", ignoreCase = true) }
    ?.substringAfter(':')?.trim()?.takeIf { it.toHttpUrlOrNull() != null }

/** A DIDL-Lite document: containers, and items with an audio <res> (video and images are left out). */
fun parseDidl(xml: String): List<DidlEntry> {
    val root = parseXml(xml, "A media server's listing")
    return root.kids().mapNotNull { e ->
        val id = e.getAttribute("id").ifBlank { return@mapNotNull null }
        val title = e.textOf("title") ?: return@mapNotNull null
        val artist = e.textOf("artist") ?: e.textOf("creator")
        val art = e.textOf("albumArtURI")
        when (e.name) {
            "container" -> DidlEntry(id, title, true, artist = artist, artworkUrl = art)
            "item" -> {
                val upnpClass = e.textOf("class").orEmpty()
                if (upnpClass.isNotEmpty() && !upnpClass.startsWith("object.item.audioItem")) return@mapNotNull null
                val res = e.kids("res").firstOrNull { it.getAttribute("protocolInfo").split(':').getOrNull(2)?.startsWith("audio/") == true }
                    ?: e.kids("res").firstOrNull()
                DidlEntry(
                    id, title, false, artist = artist, album = e.textOf("album"), artworkUrl = art,
                    audioUrl = res?.textContent?.trim()?.takeIf { it.toHttpUrlOrNull() != null },
                    durationMillis = res?.getAttribute("duration")?.let(::parseDidlDuration),
                )
            }
            else -> null
        }
    }
}

/** DIDL durations: "H:MM:SS" with optional ".fff" fraction. */
internal fun parseDidlDuration(text: String): Long? {
    val parts = text.trim().split(':')
    if (parts.size != 3) return null
    val h = parts[0].toLongOrNull() ?: return null
    val m = parts[1].toLongOrNull() ?: return null
    val s = parts[2].toDoubleOrNull() ?: return null
    return ((h * 3600 + m * 60) * 1000 + (s * 1000).toLong()).takeIf { it > 0 }
}

private fun escapeXml(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
