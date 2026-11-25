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
package icu.kairo.kairoify.source.lyrics

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaptionsTest {
    private val server = MockWebServer()
    private lateinit var finder: CaptionFinder

    @BeforeTest fun start() {
        server.start()
        finder = CaptionFinder(OkHttpClient(), server.url("/w/"), server.url("/"), language = { "fr" })
    }

    @AfterTest fun stop() = server.close()

    @Test
    fun `reads srt and vtt cues at their start times`() {
        val srt = "1\r\n00:00:01,500 --> 00:00:04,000\r\n<i>Four score</i> and\r\nseven years ago\r\n\r\n2\r\n01:02:03,040 --> 01:02:05,000\r\n{\\an8}our fathers\r\n"
        assertEquals(listOf(LyricLine(1_500, "Four score and seven years ago"), LyricLine(3_723_040, "our fathers")), parseSubtitles(srt))
        val vtt = "WEBVTT\n\nNOTE made by hand\n\n00:05.250 --> 00:07.000 align:start\nAsk not\n"
        assertEquals(listOf(LyricLine(5_250, "Ask not")), parseSubtitles(vtt))
    }

    @Test
    fun `commons captions prefer the phone's language`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"query":{"allpages":[
            {"title":"TimedText:Speech.ogg.en.srt"},{"title":"TimedText:Speech.ogg.fr.srt"}]}}""").build())
        server.enqueue(MockResponse.Builder().body("1\n00:00:02,000 --> 00:00:03,000\nBonjour\n").build())
        val captions = finder.wikimedia("File:Speech.ogg")!!
        assertEquals(listOf(LyricLine(2_000, "Bonjour")), captions.lines)
        assertEquals("Captions from Wikimedia Commons", captions.credit)
        assertEquals("Speech.ogg.", server.takeRequest().url.queryParameter("apprefix"))
        assertEquals("TimedText:Speech.ogg.fr.srt", server.takeRequest().url.queryParameter("title"))
    }

    @Test
    fun `archive captions sit beside the audio file`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"files":[{"name":"disc1/talk.mp3"},{"name":"other.srt"},
            {"name":"disc1/talk.asr.srt"},{"name":"disc1/talk.en.vtt"}]}""").build())
        server.enqueue(MockResponse.Builder().body("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n").build())
        val captions = finder.internetArchive("item1/disc1/talk.mp3")!!
        assertEquals("Hello", captions.lines.single().text)
        assertEquals("Captions from the Internet Archive", captions.credit)
        assertEquals("/metadata/item1", server.takeRequest().url.encodedPath)
        assertEquals("/download/item1/disc1/talk.en.vtt", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `no captions is null`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"files":[{"name":"talk.mp3"}]}""").build())
        assertNull(finder.internetArchive("item1/talk.mp3"))
        server.enqueue(MockResponse.Builder().code(500).build())
        assertNull(finder.wikimedia("File:X.ogg"))
    }
}
