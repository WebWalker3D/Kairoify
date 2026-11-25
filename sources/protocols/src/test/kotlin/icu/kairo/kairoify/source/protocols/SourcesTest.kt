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
import icu.kairo.kairoify.core.model.MediaRef
import icu.kairo.kairoify.core.model.Station
import icu.kairo.kairoify.core.model.StreamMime
import icu.kairo.kairoify.core.model.Track
import icu.kairo.kairoify.core.source.SourceException
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourcesTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<RecordedRequest>()
    private var yp = 0

    private fun ok(body: String, type: String, vararg headers: Pair<String, String>) =
        MockResponse.Builder().body(body).addHeader("Content-Type", type).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build()

    @BeforeTest fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(requests) { requests += request }
                val path = request.url.encodedPath
                return when (path) {
                    "/song.mp3" -> ok("ID3....", "audio/mpeg")
                    "/live" -> MockResponse.Builder().addHeader("Content-Type", "audio/mpeg").addHeader("icy-name", "Jazz Live").body("x").build()
                    "/radio.pls" -> ok("[playlist]\nFile1=${server.url("/live")}\nTitle1=Jazz Live\nLength1=-1\n", "audio/x-scpls")
                    "/mix.m3u" -> ok("#EXTM3U\n#EXTINF:100,Band - One\n/song.mp3\n#EXTINF:120,Band - Two\nsong2.mp3\n", "audio/x-mpegurl")
                    "/hls.m3u8" -> ok("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n#EXTINF:6,\nseg1.ts\n", "application/vnd.apple.mpegurl")
                    "/feed.xml" -> ok("""<rss><channel><title>Show</title><item><title>Ep 1</title><enclosure url="/ep1.mp3" type="audio/mpeg"/></item></channel></rss>""", "application/rss+xml")
                    "/artist" -> ok("""<html><head><link rel="alternate" type="application/rss+xml" href="/feed.xml"></head></html>""", "text/html")
                    "/nothing" -> ok("<html><body>hi</body></html>", "text/html")
                    "/yp.xml" -> { yp++; ok("<directory><entry><server_name>Jazz One</server_name><server_type>audio/mpeg</server_type><listen_url>http://j/1</listen_url><genre>jazz</genre></entry><entry><server_name>Rock</server_name><server_type>audio/mpeg</server_type><listen_url>http://r/1</listen_url><genre>rock</genre></entry></directory>", "text/xml") }
                    "/rest/search3.view", "/rest/getAlbum.view", "/rest/stream.view" -> subsonic(request)
                    "/desc.xml" -> ok("""<root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>NAS</friendlyName><UDN>uuid:1</UDN><serviceList><service><serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType><controlURL>/ctl</controlURL></service></serviceList></device></root>""", "text/xml")
                    "/ctl" -> upnp(request)
                    else -> if (request.method == "PROPFIND") dav(request) else MockResponse.Builder().code(404).build()
                }
            }
        }
        server.start()
    }

    @AfterTest fun stop() = server.close()

    private fun subsonic(r: RecordedRequest): MockResponse {
        val u = r.url
        if (u.queryParameter("u") != "me") return ok("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""", "application/json")
        return when (u.encodedPath) {
            "/rest/search3.view" -> ok("""{"subsonic-response":{"status":"ok","searchResult3":{"song":[{"id":"s1","title":"Feeling Good","artist":"Nina Simone","album":"I Put a Spell","duration":177,"coverArt":"c1"}],"album":{"id":"a1","name":"I Put a Spell","artist":"Nina Simone","songCount":12}}}}""", "application/json")
            "/rest/getAlbum.view" -> ok("""{"subsonic-response":{"status":"ok","album":{"id":"a1","name":"I Put a Spell","artist":"Nina Simone","song":[{"id":"s1","title":"Feeling Good","artist":"Nina Simone"},{"id":"v","title":"Clip","isVideo":true}]}}}""", "application/json")
            else -> ok("{}", "application/json")
        }
    }

    private fun dav(r: RecordedRequest): MockResponse {
        if (r.headers["Authorization"] == null) return MockResponse.Builder().code(401).build()
        val body = when (r.url.encodedPath) {
            "/remote.php/dav/files/me/Music/" -> """<d:multistatus xmlns:d="DAV:">
                <d:response><d:href>/remote.php/dav/files/me/Music/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response></d:multistatus>"""
            "/remote.php/dav/files/me/Music/Band/" -> """<d:multistatus xmlns:d="DAV:">
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/LP/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response></d:multistatus>"""
            "/remote.php/dav/files/me/Music/Band/LP/" -> """<d:multistatus xmlns:d="DAV:">
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/LP/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/LP/02%20Second.flac</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/LP/01%20First%20Song.mp3</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
                <d:response><d:href>/remote.php/dav/files/me/Music/Band/LP/notes.txt</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response></d:multistatus>"""
            else -> return MockResponse.Builder().code(404).build()
        }
        return MockResponse.Builder().code(207).addHeader("Content-Type", "application/xml").body(body).build()
    }

    private fun upnp(r: RecordedRequest): MockResponse {
        val body = r.body!!.utf8()
        val didl = if ("<ObjectID>0</ObjectID>" in body) {
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><container id="1"><dc:title>Music</dc:title></container></DIDL-Lite>"""
        } else {
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="7"><dc:title>Song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class><res protocolInfo="http-get:*:audio/mpeg:*">http://10.0.0.5/7.mp3</res></item><container id="2"><dc:title>More</dc:title></container></DIDL-Lite>"""
        }
        val escaped = didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return ok("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1"><Result>$escaped</Result></u:BrowseResponse></s:Body></s:Envelope>""", "text/xml")
    }

    // --- Links ---

    private val links get() = LinkSource(OkHttpClient())

    @Test
    fun `a pasted audio file is a song, an Icecast stream is a station`() = runTest {
        val song = links.search(server.url("/song.mp3").toString()).items.single()
        assertIs<Track>(song)
        assertEquals("song", song.title)
        val live = links.search(server.url("/live").toString()).items.single()
        assertIs<Station>(live)
        assertEquals("Jazz Live", live.title)
        assertEquals(true, links.resolve(live.ref).isLive)
    }

    @Test
    fun `a radio pls plays as its station`() = runTest {
        val item = links.open(server.url("/radio.pls").toString())
        assertIs<Station>(item)
        assertEquals("Jazz Live", item.title)
        assertEquals(server.url("/live").toString(), links.resolve(item.ref).url)
    }

    @Test
    fun `a song playlist opens into its songs`() = runTest {
        val album = links.open(server.url("/mix.m3u").toString())
        assertIs<Album>(album)
        val details = links.details(album.ref)
        assertEquals(listOf("One", "Two"), details.tracks.map { it.title })
        assertEquals(server.url("/song2.mp3").toString(), links.resolve(details.tracks[1].ref).url)
    }

    @Test
    fun `hls is one live stream`() = runTest {
        val item = links.open(server.url("/hls.m3u8").toString())
        assertIs<Station>(item)
        assertEquals(StreamMime.HLS, links.resolve(item.ref).mimeType)
    }

    @Test
    fun `feeds and pages that advertise feeds open their episodes`() = runTest {
        val feed = links.open(server.url("/artist").toString())
        assertIs<Album>(feed)
        assertEquals("Show", feed.title)
        assertEquals(listOf(server.url("/ep1.mp3").toString()), links.details(feed.ref).tracks.map { links.resolve(it.ref).url })
    }

    @Test
    fun `a page with nothing to play says so, and words search saved links`() = runTest {
        assertFailsWith<SourceException.NotFound> { links.open(server.url("/nothing").toString()) }
        val saved = LinkSource(OkHttpClient()) { listOf(server.url("/radio.pls").toString(), server.url("/nothing").toString()) }
        assertEquals(listOf("Jazz Live"), saved.search("jazz").items.map { it.title })
        assertEquals("Your links", saved.home().single().title)
        assertTrue(saved.search("rock").items.isEmpty())
    }

    // --- Icecast ---

    @Test
    fun `icecast downloads once, searches the saved copy`() = runTest {
        val file = Files.createTempDirectory("ice").resolve("yp.tsv").toFile()
        val ice = IcecastSource(OkHttpClient(), file, server.url("/yp.xml"))
        assertEquals(listOf("Jazz One"), ice.search("JAZZ").items.map { it.title })
        assertEquals(listOf("Rock"), IcecastSource(OkHttpClient(), file, server.url("/yp.xml")).search("rock").items.map { it.title })
        assertEquals(1, yp)
        assertEquals("http://j/1", ice.resolve(MediaRef(IcecastSource.ID, "http://j/1")).url)
    }

    // --- Subsonic ---

    private fun sub(user: String = "me") = Server(ServerKind.SUBSONIC, "Home", server.url("/").toString() + "rest/", user, "pw")

    @Test
    fun `subsonic searches with a salted token, never the password`() = runTest {
        val source = SubsonicSource(OkHttpClient(), { listOf(sub()) }, salt = { "abc" })
        val items = source.search("feeling").items
        assertEquals(listOf("Feeling Good", "I Put a Spell"), items.map { it.title })
        val url = requests.last().url
        assertEquals(md5("pwabc"), url.queryParameter("t"))
        assertNull(url.queryParameter("p"))
        assertEquals("/rest/search3.view", url.encodedPath)
        val details = source.details(items[1].ref)
        assertEquals(listOf("Feeling Good"), details.tracks.map { it.title })
        assertTrue(source.resolve(items[0].ref).url.contains("/rest/stream.view"))
    }

    @Test
    fun `a wrong subsonic password is said plainly`() = runTest {
        val e = assertFailsWith<SourceException.Unavailable> { SubsonicSource(OkHttpClient(), { listOf(sub("nobody")) }).search("x") }
        assertEquals("Home didn't accept the user name or password.", e.message)
    }

    // --- WebDAV ---

    private fun dav(password: String = "pw") = Server(ServerKind.WEBDAV, "Cloud", server.url("/remote.php/dav/files/me/Music/").toString(), "me", password)

    @Test
    fun `webdav folders open with subfolders and sorted songs, artist from the folders`() = runTest {
        val source = WebDavSource(OkHttpClient(), { listOf(dav()) })
        val shelf = source.home().single()
        val band = shelf.items.single()
        assertEquals("Band", band.title)
        val bandDetails = source.details(band.ref)
        assertEquals(listOf("LP"), bandDetails.folders.map { it.title })
        val lp = source.details(bandDetails.folders.single().ref)
        assertEquals(listOf("First Song", "Second"), lp.tracks.map { it.title })
        assertEquals(listOf("Band"), lp.tracks[0].artists)
        val stream = source.resolve(lp.tracks[0].ref)
        assertTrue(stream.url.endsWith("/Music/Band/LP/01%20First%20Song.mp3"))
        assertTrue(stream.headers["Authorization"]!!.startsWith("Basic "))
        assertEquals(listOf("First Song"), source.search("first").items.map { it.title })
    }

    @Test
    fun `webdav without credentials says so`() = runTest {
        val source = WebDavSource(OkHttpClient(), { listOf(dav().copy(user = "")) })
        assertFailsWith<SourceException.Unavailable> { source.search("x") }
    }

    // --- UPnP ---

    @Test
    fun `upnp finds a server and browses its folders`() = runTest {
        val source = UpnpSource(OkHttpClient(), multicast = { listOf(server.url("/desc.xml").toString()) })
        val shelf = source.home().single()
        assertEquals("NAS", shelf.title)
        val music = shelf.items.single()
        assertIs<Album>(music)
        val details = source.details(music.ref)
        assertEquals(listOf("Song"), details.tracks.map { it.title })
        assertEquals(listOf("More"), details.folders.map { it.title })
        assertEquals("http://10.0.0.5/7.mp3", source.resolve(details.tracks.single().ref).url)
    }
}
