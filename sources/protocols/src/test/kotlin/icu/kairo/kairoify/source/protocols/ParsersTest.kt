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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParsersTest {
    @Test
    fun `m3u reads EXTINF titles, durations and relative addresses`() {
        val list = parseM3u(
            "#EXTM3U\n#PLAYLIST:Road trip\n#EXTINF:185,Nina Simone - Feeling Good\nsongs/feeling%20good.mp3\n\n#EXTINF:-1,Radio\nhttp://radio.example/live\n# comment\nfile:///etc/passwd\n",
            "https://host.example/lists/trip.m3u",
        )
        assertEquals("Road trip", list.title)
        assertEquals(2, list.entries.size)
        assertEquals(PlaylistEntry("https://host.example/lists/songs/feeling%20good.mp3", "Feeling Good", "Nina Simone", durationMillis = 185_000), list.entries[0])
        assertNull(list.entries[1].durationMillis)
        assertEquals("http://radio.example/live", list.entries[1].url)
    }

    @Test
    fun `pls reads numbered files and lengths`() {
        val list = parsePls("[playlist]\nFile1=http://a.example/stream\nTitle1=Jazz FM\nLength1=-1\nFile2=http://b.example/song.ogg\nTitle2=Artist - Song\nLength2=200\nNumberOfEntries=2\n", "http://x/")
        assertEquals(listOf("http://a.example/stream", "http://b.example/song.ogg"), list.entries.map { it.url })
        assertNull(list.entries[0].durationMillis)
        assertEquals("Artist", list.entries[1].artist)
        assertEquals(200_000L, list.entries[1].durationMillis)
    }

    @Test
    fun `xspf reads tracks with creator, album and duration`() {
        val xml = """<?xml version="1.0"?><playlist version="1" xmlns="http://xspf.org/ns/0/"><title>Mix</title><trackList>
            <track><location>a.flac</location><title>One</title><creator>Band</creator><album>LP</album><duration>61000</duration><image>cover.jpg</image></track>
            <track><title>No location</title></track></trackList></playlist>"""
        val list = parseXspf(xml, "https://h.example/m/mix.xspf")
        assertEquals("Mix", list.title)
        assertEquals(listOf(PlaylistEntry("https://h.example/m/a.flac", "One", "Band", "LP", 61_000, "https://h.example/m/cover.jpg")), list.entries)
    }

    @Test
    fun `formats are told apart, and HLS isn't a song list`() {
        assertEquals(PlaylistFormat.PLS, playlistFormat("audio/x-scpls", "http://h/x", "[playlist]"))
        assertEquals(PlaylistFormat.M3U, playlistFormat(null, "http://h/list.m3u", "a.mp3"))
        assertEquals(PlaylistFormat.XSPF, playlistFormat("application/xspf+xml", "http://h/x", "<playlist"))
        assertNull(playlistFormat("application/vnd.apple.mpegurl", "http://h/live.m3u8", "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:6\n"))
        assertTrue(isHls("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8"))
        assertFalse(isHls("#EXTM3U\n#EXTINF:1,a\na.mp3"))
    }

    @Test
    fun `xml with a DOCTYPE is refused`() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><playlist><title>&e;</title></playlist>"""
        assertTrue(runCatching { parseXspf(evil, "http://h/") }.isFailure)
    }

    @Test
    fun `feeds read episodes and Podcasting 2 tags`() {
        val xml = """<?xml version="1.0"?><rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd" xmlns:podcast="https://podcastindex.org/namespace/1.0">
            <channel><title>Band Releases</title><link>https://band.example</link><itunes:author>The Band</itunes:author>
            <itunes:image href="https://band.example/art.jpg"/><podcast:medium>music</podcast:medium><podcast:value type="lightning" method="keysend"/>
            <item><title>Song One</title><enclosure url="https://band.example/1.mp3" type="audio/mpeg" length="1"/><itunes:duration>3:05</itunes:duration></item>
            <item><title>Music video</title><enclosure url="https://band.example/v.mp4" type="video/mp4" length="1"/></item>
            <item><title>No audio</title></item></channel></rss>"""
        val feed = parseFeed(xml, "https://band.example/feed.xml")
        assertEquals("Band Releases", feed.title)
        assertEquals("music", feed.medium)
        assertTrue(feed.acceptsTips)
        assertEquals("https://band.example/art.jpg", feed.artworkUrl)
        assertEquals(listOf(FeedEpisode("https://band.example/1.mp3", "Song One", "The Band", "https://band.example/art.jpg", 185_000, null)), feed.episodes)
    }

    @Test
    fun `a page's advertised feed is found`() {
        val html = """<html><head><link rel="stylesheet" href="a.css"><link rel="alternate" type="application/rss+xml" title="Feed" href="/feed.xml?a=1&amp;b=2"></head></html>"""
        assertEquals("https://site.example/feed.xml?a=1&b=2", feedLinkIn(html, "https://site.example/music/"))
        assertNull(feedLinkIn("<html><head></head></html>", "https://site.example/"))
    }

    @Test
    fun `icecast yellow pages keep playable audio entries`() {
        val xml = "<directory><entry><server_name>Jazz &amp;amp; Blues</server_name><server_type>audio/mpeg</server_type><bitrate>128</bitrate><listen_url>http://j.example:8000/live</listen_url><genre>jazz blues</genre></entry>" +
            "<entry><server_name>Empty</server_name><server_type>audio/mpeg</server_type><listen_url></listen_url></entry>" +
            "<entry><server_name>TV</server_name><server_type>video/webm</server_type><listen_url>http://tv.example/x</listen_url></entry></directory>"
        val list = parseYellowPages(okio.Buffer().writeUtf8(xml))
        assertEquals(listOf(IcecastStream("Jazz & Blues", "http://j.example:8000/live", "audio/mpeg", 128, "jazz blues")), list)
    }

    @Test
    fun `webdav multistatus lists folders and files`() {
        val xml = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:">
            <d:response><d:href>/dav/Music/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            <d:response><d:href>/dav/Music/Nina%20Simone/</d:href><d:propstat><d:prop><d:displayname>Nina Simone</d:displayname><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
            <d:response><d:href>https://h.example/dav/Music/a.mp3</d:href><d:propstat><d:prop><d:resourcetype/><d:getcontenttype>audio/mpeg</d:getcontenttype></d:prop></d:propstat></d:response>
            </d:multistatus>"""
        val entries = parseMultistatus(xml)
        assertEquals(DavEntry("/dav/Music/Nina%20Simone/", "Nina Simone", true, null), entries[1])
        assertEquals(DavEntry("/dav/Music/a.mp3", "a.mp3", false, "audio/mpeg"), entries[2])
    }

    @Test
    fun `didl lists containers and audio items, not video`() {
        val xml = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
            <container id="1" parentID="0"><dc:title>Music</dc:title><upnp:class>object.container</upnp:class></container>
            <item id="9" parentID="1"><dc:title>Song</dc:title><upnp:artist>Band</upnp:artist><upnp:album>LP</upnp:album><upnp:class>object.item.audioItem.musicTrack</upnp:class>
              <res protocolInfo="http-get:*:audio/mpeg:*" duration="0:03:05.500">http://10.0.0.5:8200/MediaItems/9.mp3</res></item>
            <item id="10"><dc:title>Clip</dc:title><upnp:class>object.item.videoItem</upnp:class><res protocolInfo="http-get:*:video/mp4:*">http://10.0.0.5/v.mp4</res></item>
            </DIDL-Lite>"""
        val entries = parseDidl(xml)
        assertEquals(listOf(DidlEntry("1", "Music", true), DidlEntry("9", "Song", false, "Band", "LP", null, "http://10.0.0.5:8200/MediaItems/9.mp3", 185_500)), entries)
    }

    @Test
    fun `ssdp replies give their location`() {
        assertEquals("http://10.0.0.5:8200/rootDesc.xml", ssdpLocation("HTTP/1.1 200 OK\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\nLOCATION: http://10.0.0.5:8200/rootDesc.xml\r\n\r\n"))
        assertNull(ssdpLocation("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test
    fun `server ids are stable and passwords don't change them`() {
        val a = Server(ServerKind.SUBSONIC, "Home", "https://music.example/", "me", "one")
        assertEquals(a.id, a.copy(password = "two", url = "https://music.example").id)
        assertEquals(listOf(a), Server.decodeAll(Server.encodeAll(listOf(a))))
        assertEquals(emptyList(), Server.decodeAll("not json"))
    }
}
